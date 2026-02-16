package io.constellation.cli

/** String utilities for safe display formatting. */
object StringUtils:

  /** Display length constants. */
  object Display:
    val HashPreviewLength      = 12
    val IdPreviewLength        = 8
    val TimestampPreviewLength = 19 // "2026-02-08T00:00:00"

  /** Safely truncate a string with ellipsis.
    *
    * @param s
    *   The string to truncate
    * @param maxLen
    *   Maximum length before truncation
    * @return
    *   The truncated string, or original if short enough
    */
  def truncate(s: String, maxLen: Int): String =
    if maxLen <= 0 then ""
    else if s.length <= maxLen then s
    else if maxLen <= 3 then s.take(maxLen)
    else s.take(maxLen - 3) + "..."

  /** Safely truncate and display a hash for preview.
    *
    * @param hash
    *   The hash string to truncate
    * @return
    *   Truncated hash with ellipsis (e.g., "7a3b8c9d1234...")
    */
  def hashPreview(hash: String): String =
    if hash.length <= Display.HashPreviewLength then hash
    else hash.take(Display.HashPreviewLength) + "..."

  /** Safely truncate and display a UUID/ID for preview.
    *
    * @param id
    *   The ID string to truncate
    * @return
    *   Truncated ID with ellipsis (e.g., "550e8400...")
    */
  def idPreview(id: String): String =
    if id.length <= Display.IdPreviewLength then id
    else id.take(Display.IdPreviewLength) + "..."

  /** Safely truncate and display a timestamp for preview. Removes timezone information for compact
    * display.
    *
    * @param timestamp
    *   The ISO timestamp string
    * @return
    *   Truncated timestamp (e.g., "2026-02-08T00:00:00")
    */
  def timestampPreview(timestamp: String): String =
    timestamp.take(Display.TimestampPreviewLength)

  /** Sanitize an error message by redacting sensitive patterns.
    *
    * Redacts:
    *   - Bearer tokens
    *   - API keys starting with sk-
    *   - Authorization headers
    *   - Password patterns
    *
    * @param message
    *   The error message to sanitize
    * @return
    *   Sanitized message with sensitive data redacted
    */
  def sanitizeError(message: String): String =
    message
      .replaceAll("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.]+", "Bearer [REDACTED]")
      .replaceAll("(?i)sk-[A-Za-z0-9]+", "[REDACTED]")
      .replaceAll("(?i)Authorization:\\s*[^\\s,;]+", "Authorization: [REDACTED]")
      .replaceAll("(?i)password[\"']?\\s*[=:]\\s*[\"']?[^\\s,;\"']+", "password=[REDACTED]")
      .replaceAll("(?i)token[\"']?\\s*[=:]\\s*[\"']?[^\\s,;\"']+", "token=[REDACTED]")
      .replaceAll("://[^/@\\s]+:[^/@\\s]+@", "://[REDACTED]@")
