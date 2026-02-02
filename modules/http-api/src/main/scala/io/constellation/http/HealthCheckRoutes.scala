package io.constellation.http

import cats.effect.IO
import cats.implicits.*
import io.circe.Json
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.constellation.execution.{ConstellationLifecycle, LifecycleState}
import io.constellation.lang.CachingLangCompiler
import io.constellation.lang.LangCompiler
import io.constellation.execution.GlobalScheduler

import java.time.Instant

/** A named readiness check that returns `true` when ready. */
case class ReadinessCheck(name: String, check: IO[Boolean])

/** Configuration for health check endpoints.
  *
  * `/health/live` and `/health/ready` are always available (no config needed). `/health/detail` is
  * opt-in via `enableDetailEndpoint`.
  *
  * @param enableDetailEndpoint
  *   Whether the `/health/detail` endpoint is exposed
  * @param detailRequiresAuth
  *   If true, `/health/detail` is NOT in public paths and requires auth
  * @param customReadinessChecks
  *   Additional checks for the readiness probe
  */
case class HealthCheckConfig(
    enableDetailEndpoint: Boolean = false,
    detailRequiresAuth: Boolean = true,
    customReadinessChecks: List[ReadinessCheck] = Nil
)

object HealthCheckConfig {

  /** Default configuration. */
  val default: HealthCheckConfig = HealthCheckConfig()
}

/** HTTP routes for health checks.
  *
  *   - `GET /health/live` — always 200 `{"status":"alive"}`. Liveness probe.
  *   - `GET /health/ready` — 200 if lifecycle Running + custom checks pass, 503 otherwise.
  *     Readiness probe.
  *   - `GET /health/detail` — full diagnostics (opt-in). Auth-gated by default.
  */
object HealthCheckRoutes {

  def routes(
      config: HealthCheckConfig,
      lifecycle: Option[ConstellationLifecycle] = None,
      compiler: Option[LangCompiler] = None,
      scheduler: Option[GlobalScheduler] = None
  ): HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Liveness probe — always returns 200
    case GET -> Root / "health" / "live" =>
      Ok(Json.obj("status" -> Json.fromString("alive")))

    // Readiness probe — checks lifecycle state and custom checks
    case GET -> Root / "health" / "ready" =>
      val lifecycleReady: IO[Boolean] = lifecycle match {
        case Some(lc) => lc.state.map(_ == LifecycleState.Running)
        case None     => IO.pure(true)
      }

      val customReady: IO[Boolean] =
        if config.customReadinessChecks.isEmpty then IO.pure(true)
        else config.customReadinessChecks.traverse(_.check).map(_.forall(identity))

      for {
        lcReady <- lifecycleReady
        crReady <- customReady
        ready = lcReady && crReady
        resp <-
          if ready then Ok(Json.obj("status" -> Json.fromString("ready")))
          else ServiceUnavailable(Json.obj("status" -> Json.fromString("not_ready")))
      } yield resp

    // Detail endpoint — full diagnostics, opt-in
    case GET -> Root / "health" / "detail" if config.enableDetailEndpoint =>
      val lifecycleJson: IO[Json] = lifecycle match {
        case Some(lc) =>
          lc.state.map { state =>
            Json.obj("state" -> Json.fromString(state.toString))
          }
        case None =>
          IO.pure(Json.obj("state" -> Json.fromString("unknown")))
      }

      val cacheJson: IO[Json] = compiler match {
        case Some(c: CachingLangCompiler) =>
          IO.pure {
            val s = c.cacheStats
            Json.obj(
              "hits"      -> Json.fromLong(s.hits),
              "misses"    -> Json.fromLong(s.misses),
              "hitRate"   -> Json.fromDoubleOrNull(s.hitRate),
              "evictions" -> Json.fromLong(s.evictions),
              "entries"   -> Json.fromInt(s.entries)
            )
          }
        case _ => IO.pure(Json.Null)
      }

      val schedulerJson: IO[Json] = scheduler match {
        case Some(s) =>
          s.stats.map { stats =>
            Json.obj(
              "activeCount"    -> Json.fromInt(stats.activeCount),
              "queuedCount"    -> Json.fromInt(stats.queuedCount),
              "totalSubmitted" -> Json.fromLong(stats.totalSubmitted),
              "totalCompleted" -> Json.fromLong(stats.totalCompleted)
            )
          }
        case None => IO.pure(Json.Null)
      }

      val customChecksJson: IO[Json] =
        if config.customReadinessChecks.isEmpty then IO.pure(Json.Null)
        else {
          config.customReadinessChecks
            .traverse { rc =>
              rc.check.map(ok => rc.name -> Json.fromBoolean(ok))
            }
            .map(pairs => Json.obj(pairs*))
        }

      for {
        lc      <- lifecycleJson
        cache   <- cacheJson
        sched   <- schedulerJson
        customs <- customChecksJson
        resp <- Ok(
          Json.obj(
            "timestamp"       -> Json.fromString(Instant.now().toString),
            "lifecycle"       -> lc,
            "cache"           -> cache,
            "scheduler"       -> sched,
            "readinessChecks" -> customs
          )
        )
      } yield resp
  }
}
