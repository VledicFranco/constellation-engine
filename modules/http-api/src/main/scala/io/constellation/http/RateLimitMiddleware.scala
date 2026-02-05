package io.constellation.http

import scala.concurrent.duration.*

import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.execution.{RateLimit, TokenBucketRateLimiter}
import io.constellation.http.ApiModels.ErrorResponse

import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.`Retry-After`
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.typelevel.ci.*

/** Configuration for per-IP HTTP rate limiting.
  *
  * Presence as `Some(...)` in `Config` means enabled (opt-in via `.withRateLimit()`).
  *
  * Environment variables:
  *   - `CONSTELLATION_RATE_LIMIT_RPM` — requests per minute (default 100)
  *   - `CONSTELLATION_RATE_LIMIT_BURST` — burst size (default 20)
  *
  * @param requestsPerMinute
  *   Sustained request rate per client IP
  * @param burst
  *   Maximum burst size (token bucket capacity)
  * @param exemptPaths
  *   Path prefixes that bypass rate limiting
  */
case class RateLimitConfig(
    requestsPerMinute: Int = 100,
    burst: Int = 20,
    keyRequestsPerMinute: Int = 200,
    keyBurst: Int = 40,
    exemptPaths: Set[String] = Set("/health", "/health/live", "/health/ready", "/metrics")
) {

  /** Validate the configuration. */
  def validate: Either[String, RateLimitConfig] =
    if requestsPerMinute <= 0 then
      Left(s"requestsPerMinute must be positive, got: $requestsPerMinute")
    else if burst <= 0 then Left(s"burst must be positive, got: $burst")
    else if keyRequestsPerMinute <= 0 then
      Left(s"keyRequestsPerMinute must be positive, got: $keyRequestsPerMinute")
    else if keyBurst <= 0 then Left(s"keyBurst must be positive, got: $keyBurst")
    else Right(this)
}

object RateLimitConfig {

  /** Create configuration from environment variables. */
  def fromEnv: RateLimitConfig = {
    val rpm = sys.env
      .get("CONSTELLATION_RATE_LIMIT_RPM")
      .flatMap(_.toIntOption)
      .getOrElse(100)

    val burst = sys.env
      .get("CONSTELLATION_RATE_LIMIT_BURST")
      .flatMap(_.toIntOption)
      .getOrElse(20)

    RateLimitConfig(requestsPerMinute = rpm, burst = burst)
  }

  /** Default configuration. */
  val default: RateLimitConfig = RateLimitConfig()
}

/** Per-IP and per-API-key token-bucket rate limiter for HTTP routes.
  *
  * Uses `tryAcquire` (non-blocking). When a client exceeds the limit a 429 response is returned
  * immediately with a `Retry-After` header.
  *
  * Rate limiting is applied in two layers:
  *   1. Per-IP: every client is rate-limited by source IP 2. Per-API-key: authenticated clients are
  *      also rate-limited by their key
  *
  * A request must pass both checks. This prevents a single API key from monopolizing the IP's
  * budget and vice versa.
  */
object RateLimitMiddleware {

  /** Wrap routes with per-IP rate limiting.
    *
    * Returns `IO` because the shared per-IP bucket map requires a `Ref` allocation.
    *
    * @param config
    *   Rate limit parameters
    * @param routes
    *   The routes to protect
    * @return
    *   Rate-limited routes (effectful due to Ref creation)
    */
  def apply(config: RateLimitConfig)(routes: HttpRoutes[IO]): IO[HttpRoutes[IO]] =
    for {
      buckets <- Ref.of[IO, Map[String, TokenBucketRateLimiter]](Map.empty)
    } yield withBuckets(config, buckets)(routes)

  /** Wrap routes using a pre-created bucket Ref.
    *
    * This is a pure function — no IO allocation. Useful when the Ref is created during server setup
    * (e.g. inside `Resource.eval`) and reused in a pure callback.
    */
  def withBuckets(
      config: RateLimitConfig,
      buckets: Ref[IO, Map[String, TokenBucketRateLimiter]]
  )(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    val ipRate  = RateLimit.perMinute(config.requestsPerMinute)
    val keyRate = RateLimit.perMinute(config.keyRequestsPerMinute)

    Kleisli { (req: Request[IO]) =>
      val path = req.uri.path.renderString

      // Check exempt paths (prefix match)
      val exempt = config.exemptPaths.exists(p => path == p || path.startsWith(p + "/"))

      if exempt then routes(req)
      else {
        val ip     = extractClientIp(req)
        val apiKey = extractBearerToken(req)

        // Check IP-based rate limit
        val ipCheck = getOrCreateBucket(buckets, s"ip:$ip", ipRate, config.burst)
          .flatMap(_.tryAcquire)

        // Check per-API-key rate limit (if authenticated)
        val keyCheck = apiKey match {
          case Some(key) =>
            val keyId = s"key:${HashedApiKey.hashKey(key).take(8).map("%02x".format(_)).mkString}"
            getOrCreateBucket(buckets, keyId, keyRate, config.keyBurst)
              .flatMap(_.tryAcquire)
          case None => IO.pure(true)
        }

        OptionT
          .liftF(ipCheck.flatMap { ipOk =>
            if !ipOk then IO.pure(false)
            else keyCheck
          })
          .flatMap { acquired =>
            if acquired then routes(req)
            else {
              val retryAfterSeconds = 60L / config.requestsPerMinute.toLong
              val response = Response[IO](Status.TooManyRequests)
                .putHeaders(`Retry-After`.unsafeFromLong(retryAfterSeconds.max(1L)))
                .withEntity(
                  ErrorResponse(
                    error = "RateLimitExceeded",
                    message = "Too many requests, please try again later"
                  )
                )
              OptionT.some[IO](response)
            }
          }
      }
    }
  }

  /** Extract client IP from remote address.
    *
    * Uses `remoteAddr` directly rather than trusting `X-Forwarded-For`, which can be spoofed by any
    * client to bypass rate limiting. In production deployments behind a reverse proxy, the proxy
    * should set a trusted header and the server should be configured accordingly.
    */
  private[http] def extractClientIp(req: Request[IO]): String =
    req.remoteAddr
      .map(_.toInetAddress.getHostAddress)
      .getOrElse("unknown")

  /** Extract Bearer token from Authorization header (for per-key rate limiting). */
  private def extractBearerToken(req: Request[IO]): Option[String] =
    req.headers.get[org.http4s.headers.Authorization].flatMap { auth =>
      auth.credentials match {
        case org.http4s.Credentials.Token(scheme, token) if scheme == ci"Bearer" =>
          Some(token)
        case _ => None
      }
    }

  /** Get an existing bucket for the IP or create a new one. */
  private def getOrCreateBucket(
      ref: Ref[IO, Map[String, TokenBucketRateLimiter]],
      ip: String,
      rate: RateLimit,
      burst: Int
  ): IO[TokenBucketRateLimiter] =
    ref.get.flatMap { buckets =>
      buckets.get(ip) match {
        case Some(limiter) => IO.pure(limiter)
        case None =>
          TokenBucketRateLimiter.withInitialTokens(rate, burst.toDouble).flatMap { limiter =>
            ref.modify { current =>
              // Double-check after creation to avoid race
              current.get(ip) match {
                case Some(existing) => (current, existing)
                case None           => (current + (ip -> limiter), limiter)
              }
            }
          }
      }
    }
}
