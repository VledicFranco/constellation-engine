package io.constellation.http

/** API role for authorization.
  *
  * Determines what HTTP methods a client is allowed to use:
  *   - ReadOnly: GET only
  *   - Execute: GET + POST
  *   - Admin: all methods
  */
enum ApiRole:
  case Admin, Execute, ReadOnly

  /** Check whether this role permits the given HTTP method. */
  def permits(method: String): Boolean = this match
    case ApiRole.Admin    => true
    case ApiRole.Execute  => method == "GET" || method == "POST"
    case ApiRole.ReadOnly => method == "GET"

/** Configuration for static API-key authentication.
  *
  * When `apiKeys` is non-empty, every request that does not match a public path
  * must carry an `Authorization: Bearer <key>` header whose key appears in the map.
  *
  * Environment variable:
  *   - `CONSTELLATION_API_KEYS` — comma-separated `key:Role` pairs,
  *     e.g. `key1:Admin,key2:Execute`
  *
  * @param apiKeys
  *   Map from API key string to the role it grants
  * @param publicPaths
  *   Path prefixes that bypass authentication (prefix match)
  */
case class AuthConfig(
    apiKeys: Map[String, ApiRole] = Map.empty,
    publicPaths: Set[String] = Set("/health", "/health/live", "/health/ready", "/metrics")
) {

  /** Authentication is only active when at least one key is configured. */
  def isEnabled: Boolean = apiKeys.nonEmpty

  /** Validate the configuration. */
  def validate: Either[String, AuthConfig] =
    if apiKeys.exists((k, _) => k.isBlank) then
      Left("API keys must not be blank")
    else Right(this)
}

object AuthConfig {

  /** Parse role from string (case-insensitive). */
  private def parseRole(s: String): Option[ApiRole] = s.trim.toLowerCase match
    case "admin"    => Some(ApiRole.Admin)
    case "execute"  => Some(ApiRole.Execute)
    case "readonly" => Some(ApiRole.ReadOnly)
    case _          => None

  /** Create configuration from environment variables.
    *
    * `CONSTELLATION_API_KEYS=key1:Admin,key2:Execute`
    */
  def fromEnv: AuthConfig = {
    val keys = sys.env.get("CONSTELLATION_API_KEYS").map { raw =>
      raw.split(",").flatMap { entry =>
        entry.split(":", 2) match
          case Array(k, r) => parseRole(r).map(k.trim -> _)
          case _           => None
      }.toMap
    }.getOrElse(Map.empty)

    AuthConfig(apiKeys = keys)
  }

  /** Default configuration (no authentication). */
  val default: AuthConfig = AuthConfig()
}
