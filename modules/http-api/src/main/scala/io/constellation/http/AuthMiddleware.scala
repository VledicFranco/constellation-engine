package io.constellation.http

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.http4s.headers.Authorization
import org.http4s.Credentials
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.ci.*
import io.constellation.http.ApiModels.ErrorResponse

/** Middleware that enforces static API-key authentication.
  *
  * Extracts the `Authorization: Bearer <token>` header, verifies it against
  * hashed keys in `AuthConfig.hashedKeys`, and checks that the associated
  * `ApiRole` permits the HTTP method. Public paths (prefix match) bypass
  * authentication entirely.
  */
object AuthMiddleware {

  /** Wrap routes with authentication.
    *
    * @param config
    *   Authentication configuration
    * @param routes
    *   The routes to protect
    * @return
    *   Authenticated routes
    */
  def apply(config: AuthConfig)(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    if !config.isEnabled then routes
    else {
      Kleisli { (req: Request[IO]) =>
        val path = req.uri.path.renderString

        // Public paths bypass auth (prefix match)
        val isPublic = config.publicPaths.exists(p => path == p || path.startsWith(p + "/"))

        if isPublic then routes(req)
        else {
          extractBearerToken(req) match {
            case None =>
              OptionT.liftF(errorResponse(Status.Unauthorized,
                "Unauthorized", "Missing or invalid Authorization header. Expected: Bearer <api-key>"))

            case Some(token) =>
              config.verifyKey(token) match {
                case None =>
                  OptionT.liftF(errorResponse(Status.Unauthorized,
                    "Unauthorized", "Invalid API key"))

                case Some(role) =>
                  val method = req.method.name
                  if role.permits(method) then routes(req)
                  else OptionT.liftF(errorResponse(Status.Forbidden,
                    "Forbidden", s"Role '${role}' does not permit ${method} requests"))
              }
          }
        }
      }
    }
  }

  private def errorResponse(status: Status, error: String, message: String): IO[Response[IO]] =
    IO.pure(Response[IO](status).withEntity(ErrorResponse(error = error, message = message)))

  /** Extract Bearer token from the Authorization header. */
  private def extractBearerToken(req: Request[IO]): Option[String] =
    req.headers.get[Authorization].flatMap { auth =>
      auth.credentials match {
        case Credentials.Token(scheme, token) if scheme == ci"Bearer" =>
          Some(token)
        case _ => None
      }
    }
}
