package io.constellation.cli

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.implicits.*

import io.circe.{Decoder, Json}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, Headers, Method, Request, Status, Uri}
import org.typelevel.ci.CIString

/** HTTP client wrapper for Constellation API. */
object HttpClient:

  /** Response from an API call. */
  sealed trait ApiResponse[+A]
  case class Success[A](value: A)                   extends ApiResponse[A]
  case class ApiError(status: Int, message: String) extends ApiResponse[Nothing]
  case class ConnectionError(message: String)       extends ApiResponse[Nothing]
  case class ParseError(message: String)            extends ApiResponse[Nothing]

  /** Create an HTTP client resource. */
  def client: Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .withTimeout(60.seconds)
      .withIdleConnectionTime(30.seconds)
      .build

  /** Make a GET request. */
  def get[A: Decoder](
      uri: Uri,
      token: Option[String] = None
  )(using client: Client[IO]): IO[ApiResponse[A]] =
    val headers = token
      .map(t => Headers(Header.Raw(CIString("Authorization"), s"Bearer $t")))
      .getOrElse(Headers.empty)
    val request = Request[IO](Method.GET, uri, headers = headers)

    client
      .run(request)
      .use { response =>
        handleResponse[A](response)
      }
      .handleError { e =>
        ConnectionError(
          StringUtils.sanitizeError(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
        )
      }

  /** Make a POST request with JSON body. */
  def post[A: Decoder](
      uri: Uri,
      body: Json,
      token: Option[String] = None
  )(using client: Client[IO]): IO[ApiResponse[A]] =
    val headers = token
      .map(t => Headers(Header.Raw(CIString("Authorization"), s"Bearer $t")))
      .getOrElse(Headers.empty)
    val request = Request[IO](Method.POST, uri, headers = headers).withEntity(body)

    client
      .run(request)
      .use { response =>
        handleResponse[A](response)
      }
      .handleError { e =>
        ConnectionError(
          StringUtils.sanitizeError(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
        )
      }

  /** Make a DELETE request. */
  def delete[A: Decoder](
      uri: Uri,
      token: Option[String] = None
  )(using client: Client[IO]): IO[ApiResponse[A]] =
    val headers = token
      .map(t => Headers(Header.Raw(CIString("Authorization"), s"Bearer $t")))
      .getOrElse(Headers.empty)
    val request = Request[IO](Method.DELETE, uri, headers = headers)

    client
      .run(request)
      .use { response =>
        handleResponse[A](response)
      }
      .handleError { e =>
        ConnectionError(
          StringUtils.sanitizeError(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
        )
      }

  /** Handle HTTP response. */
  private def handleResponse[A: Decoder](
      response: org.http4s.Response[IO]
  ): IO[ApiResponse[A]] =
    response.status match
      case Status.Ok | Status.Created =>
        response.as[Json].attempt.map {
          case Right(json) =>
            json.as[A] match
              case Right(value) => Success(value)
              case Left(err)    => ParseError(s"Failed to parse response: ${err.message}")
          case Left(err) =>
            ParseError(s"Failed to read response body: ${err.getMessage}")
        }
      case Status.BadRequest =>
        response.as[Json].attempt.map {
          case Right(json) =>
            val message = json.hcursor
              .downField("message")
              .as[String]
              .orElse(json.hcursor.downField("error").as[String])
              .getOrElse(json.noSpaces)
            ApiError(400, message)
          case Left(_) =>
            ApiError(400, "Bad request")
        }
      case Status.Unauthorized =>
        IO.pure(ApiError(401, "Authentication required"))
      case Status.Forbidden =>
        IO.pure(ApiError(403, "Access denied"))
      case Status.NotFound =>
        response.as[Json].attempt.map {
          case Right(json) =>
            val message = json.hcursor.downField("message").as[String].getOrElse("Not found")
            ApiError(404, message)
          case Left(_) =>
            ApiError(404, "Not found")
        }
      case Status.TooManyRequests =>
        IO.pure(ApiError(429, "Rate limit exceeded"))
      case Status.ServiceUnavailable =>
        IO.pure(ApiError(503, "Server is unavailable"))
      case status =>
        response.as[String].attempt.map {
          case Right(body) => ApiError(status.code, s"HTTP ${status.code}: $body")
          case Left(_)     => ApiError(status.code, s"HTTP ${status.code}")
        }
