package io.constellation.stream.connector.sse

/** A parsed Server-Sent Event.
  *
  * @param data
  *   The event data (joined with newlines for multi-line data fields)
  * @param eventType
  *   The event type (from `event:` field), if specified
  * @param id
  *   The event ID (from `id:` field), if specified
  * @param retry
  *   The reconnection time in milliseconds (from `retry:` field), if specified
  */
final case class SseEvent(
    data: String,
    eventType: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Int] = None
)
