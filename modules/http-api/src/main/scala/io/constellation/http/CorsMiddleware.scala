package io.constellation.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.server.middleware.CORS
import org.typelevel.ci.CIString

import scala.concurrent.duration.*

/** Middleware that applies CORS headers to responses.
  *
  * Delegates to the http4s built-in `CORS` middleware, configured from `CorsConfig`.
  * When `allowedOrigins` is empty, no middleware is applied (zero overhead).
  */
object CorsMiddleware {

  /** Wrap routes with CORS middleware.
    *
    * @param config
    *   CORS configuration
    * @param routes
    *   The routes to wrap
    * @return
    *   CORS-enabled routes
    */
  def apply(config: CorsConfig)(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    if !config.isEnabled then routes
    else {
      val policy = CORS.policy
        .withAllowMethodsIn(
          config.allowedMethods.flatMap(m =>
            org.http4s.Method.fromString(m).toOption
          )
        )
        .withAllowHeadersIn(
          config.allowedHeaders.map(CIString(_))
        )
        .withMaxAge(config.maxAge.seconds)
        .withAllowCredentials(config.allowCredentials)

      val policyWithOrigins =
        if config.isWildcard then policy.withAllowOriginAll
        else policy.withAllowOriginHost { host =>
          val portSuffix = host.port.fold("")(p => s":$p")
          val origin = s"${host.scheme.value}://${host.host.value}$portSuffix"
          config.allowedOrigins.contains(origin)
        }

      policyWithOrigins.httpRoutes(routes)
    }
  }
}
