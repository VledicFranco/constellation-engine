package io.constellation.http

import org.slf4j.LoggerFactory

/** Configuration for Cross-Origin Resource Sharing (CORS).
  *
  * When `allowedOrigins` is non-empty the CORS middleware is applied. An empty set means CORS is
  * disabled and adds zero overhead.
  *
  * Environment variable:
  *   - `CONSTELLATION_CORS_ORIGINS` — comma-separated origin URLs, e.g.
  *     `https://app.example.com,https://admin.example.com` Use `*` to allow all origins
  *     (development only).
  *
  * @param allowedOrigins
  *   Set of allowed origin URLs. Use `Set("*")` for wildcard.
  * @param allowedMethods
  *   HTTP methods allowed in CORS requests
  * @param allowedHeaders
  *   Headers the client may send in CORS requests
  * @param allowCredentials
  *   Whether `Access-Control-Allow-Credentials` is set
  * @param maxAge
  *   `Access-Control-Max-Age` in seconds for preflight cache
  */
case class CorsConfig(
    allowedOrigins: Set[String] = Set.empty,
    allowedMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "OPTIONS"),
    allowedHeaders: Set[String] = Set("Content-Type", "Authorization"),
    allowCredentials: Boolean = false,
    maxAge: Long = 3600L
) {

  /** CORS is only active when at least one origin is configured. */
  def isEnabled: Boolean = allowedOrigins.nonEmpty

  /** Whether the wildcard `"*"` origin is configured. */
  def isWildcard: Boolean = allowedOrigins.contains("*")

  /** Validate the configuration. */
  def validate: Either[String, CorsConfig] =
    if maxAge < 0 then Left(s"maxAge must be non-negative, got: $maxAge")
    else if allowCredentials && isWildcard then
      Left("Cannot combine allowCredentials=true with wildcard origin '*'")
    else Right(this)
}

object CorsConfig {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Validate a CORS origin URL.
    *
    * @param origin
    *   The origin URL to validate
    * @return
    *   Either an error message or the validated origin
    */
  private[http] def validateOrigin(origin: String): Either[String, String] =
    if origin == "*" then {
      Right(origin) // Wildcard is valid
    } else {
      try {
        val url    = new java.net.URL(origin)
        val scheme = url.getProtocol.toLowerCase

        // Require HTTPS unless localhost
        val isLocalhost = url.getHost.toLowerCase.matches("localhost|127\\.0\\.0\\.1|\\[::1\\]")

        if scheme == "https" || (scheme == "http" && isLocalhost) then {
          Right(origin)
        } else if scheme == "http" then {
          Left(s"HTTP not allowed for non-localhost origins (use HTTPS): $origin")
        } else {
          Left(s"Invalid scheme '$scheme' (expected http/https): $origin")
        }
      } catch {
        case e: java.net.MalformedURLException =>
          Left(s"Malformed URL: ${e.getMessage}")
      }
    }

  /** Create configuration from environment variables.
    *
    * `CONSTELLATION_CORS_ORIGINS=https://app.example.com,https://admin.example.com`
    *
    * Invalid origins are logged as warnings and skipped. Only origin counts are logged — specific
    * domains are not exposed in logs.
    */
  def fromEnv: CorsConfig = {
    val origins = sys.env
      .get("CONSTELLATION_CORS_ORIGINS")
      .map { raw =>
        raw
          .split(',')
          .zipWithIndex
          .flatMap { case (origin, idx) =>
            val trimmed = origin.trim
            if trimmed.isEmpty then {
              None
            } else {
              validateOrigin(trimmed) match {
                case Right(validOrigin) =>
                  Some(validOrigin)
                case Left(error) =>
                  // Log error category without exposing the origin value
                  logger.warn("CORS origin #{} rejected: validation failed", idx + 1)
                  None
              }
            }
          }
          .toSet
      }
      .getOrElse(Set.empty)

    if origins.nonEmpty then {
      val wildcardNote = if origins.contains("*") then " (wildcard)" else ""
      logger.info("Loaded {} CORS origin(s){}", origins.size, wildcardNote)
    }

    CorsConfig(allowedOrigins = origins)
  }

  /** Default configuration (no CORS). */
  val default: CorsConfig = CorsConfig()
}
