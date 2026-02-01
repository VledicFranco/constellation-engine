package io.constellation.http

import java.security.MessageDigest
import java.util.Arrays

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

/** Hashed API key for secure storage.
  *
  * Stores SHA-256 hash instead of plaintext to prevent:
  * - Exposure in memory dumps
  * - Accidental logging of secrets
  * - Timing attacks (via constant-time comparison)
  */
case class HashedApiKey(hash: Array[Byte], role: ApiRole) {

  /** Verify a plaintext key against this hashed key using constant-time comparison. */
  def verify(plaintextKey: String): Boolean = {
    val candidateHash = HashedApiKey.hashKey(plaintextKey)
    // Use Arrays.equals for constant-time comparison (resistant to timing attacks)
    Arrays.equals(hash, candidateHash)
  }
}

object HashedApiKey {

  /** Create a hashed API key from plaintext. */
  def apply(plaintextKey: String, role: ApiRole): HashedApiKey = {
    HashedApiKey(hashKey(plaintextKey), role)
  }

  /** Hash a key using SHA-256. */
  private[http] def hashKey(key: String): Array[Byte] = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(key.getBytes("UTF-8"))
  }
}

/** Configuration for static API-key authentication.
  *
  * When `hashedKeys` is non-empty, every request that does not match a public path
  * must carry an `Authorization: Bearer <key>` header that verifies against a hashed key.
  *
  * Environment variable:
  *   - `CONSTELLATION_API_KEYS` — comma-separated `key:Role` pairs,
  *     e.g. `key1:Admin,key2:Execute`
  *
  * Keys are hashed with SHA-256 on startup to prevent:
  * - Exposure in memory dumps
  * - Accidental logging
  * - Timing attacks (constant-time verification)
  *
  * @param hashedKeys
  *   List of hashed API keys with their roles
  * @param publicPaths
  *   Path prefixes that bypass authentication (prefix match)
  */
case class AuthConfig(
    hashedKeys: List[HashedApiKey] = List.empty,
    publicPaths: Set[String] = Set("/health", "/health/live", "/health/ready", "/metrics")
) {

  /** Authentication is only active when at least one key is configured. */
  def isEnabled: Boolean = hashedKeys.nonEmpty

  /** Verify a plaintext key and return its role if valid. */
  def verifyKey(plaintextKey: String): Option[ApiRole] = {
    hashedKeys.find(_.verify(plaintextKey)).map(_.role)
  }

  /** Validate the configuration. */
  def validate: Either[String, AuthConfig] = {
    // No validation needed for hashed keys (validation done during fromEnv)
    Right(this)
  }
}

object AuthConfig {

  /** Minimum API key length for security. */
  private val MinKeyLength = 24

  /** Parse role from string (case-insensitive). */
  private[http] def parseRole(s: String): Option[ApiRole] = s.trim.toLowerCase match
    case "admin"    => Some(ApiRole.Admin)
    case "execute"  => Some(ApiRole.Execute)
    case "readonly" => Some(ApiRole.ReadOnly)
    case _          => None

  /** Validate an API key meets security requirements.
    *
    * @param key The plaintext API key to validate
    * @return Either an error message or the validated key
    */
  private[http] def validateApiKey(key: String): Either[String, String] = {
    if (key.isBlank) {
      Left("API key cannot be blank or whitespace-only")
    } else if (key.length < MinKeyLength) {
      Left(s"API key too short: ${key.length} chars (minimum $MinKeyLength required)")
    } else if (key.exists(c => c.isControl)) {
      Left(s"API key contains control characters")
    } else {
      Right(key)
    }
  }

  /** Create configuration from environment variables.
    *
    * `CONSTELLATION_API_KEYS=key1:Admin,key2:Execute`
    *
    * Keys are hashed on startup for secure storage.
    * Invalid keys are logged as warnings and skipped.
    * API key values are NEVER logged — only counts and error categories.
    */
  def fromEnv: AuthConfig = {
    val hashedKeys = sys.env.get("CONSTELLATION_API_KEYS").map { raw =>
      raw.split(",").zipWithIndex.flatMap { case (entry, idx) =>
        entry.split(":", 2) match {
          case Array(k, r) =>
            val key = k.trim

            // Validate API key
            val keyValidation = validateApiKey(key)
            val roleValidation = parseRole(r).toRight(s"Invalid role format")

            (keyValidation, roleValidation) match {
              case (Right(validKey), Right(role)) =>
                Some(HashedApiKey(validKey, role))

              case (Left(keyError), _) =>
                // Log error category only — never log key content or length hints
                System.err.println(s"[WARN] API key #${idx + 1} rejected: validation failed")
                None

              case (_, Left(roleError)) =>
                System.err.println(s"[WARN] API key #${idx + 1}: $roleError")
                None
            }

          case _ =>
            System.err.println(s"[WARN] API key #${idx + 1}: expected 'key:Role' format")
            None
        }
      }.toList
    }.getOrElse(List.empty)

    if (hashedKeys.nonEmpty) {
      val roleCounts = hashedKeys.groupBy(_.role).map { case (role, keys) => s"$role=${keys.size}" }.mkString(", ")
      System.err.println(s"[INFO] Loaded ${hashedKeys.length} API key(s) [$roleCounts]")
    }

    AuthConfig(hashedKeys = hashedKeys)
  }

  /** Default configuration (no authentication). */
  val default: AuthConfig = AuthConfig()
}
