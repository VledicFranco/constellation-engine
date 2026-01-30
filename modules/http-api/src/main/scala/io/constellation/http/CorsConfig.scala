package io.constellation.http

/** Configuration for Cross-Origin Resource Sharing (CORS).
  *
  * When `allowedOrigins` is non-empty the CORS middleware is applied.
  * An empty set means CORS is disabled and adds zero overhead.
  *
  * Environment variable:
  *   - `CONSTELLATION_CORS_ORIGINS` — comma-separated origin URLs,
  *     e.g. `https://app.example.com,https://admin.example.com`
  *     Use `*` to allow all origins (development only).
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

  /** Create configuration from environment variables.
    *
    * `CONSTELLATION_CORS_ORIGINS=https://app.example.com,https://admin.example.com`
    */
  def fromEnv: CorsConfig = {
    val origins = sys.env.get("CONSTELLATION_CORS_ORIGINS").map { raw =>
      raw.split(',').map(_.trim).filter(_.nonEmpty).toSet
    }.getOrElse(Set.empty)

    CorsConfig(allowedOrigins = origins)
  }

  /** Default configuration (no CORS). */
  val default: CorsConfig = CorsConfig()
}
