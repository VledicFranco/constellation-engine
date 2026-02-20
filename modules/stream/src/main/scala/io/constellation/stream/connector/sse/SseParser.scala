package io.constellation.stream.connector.sse

import cats.effect.IO

import fs2.{Pipe, Pull, Stream}

/** Pure SSE (Server-Sent Events) protocol parser.
  *
  * Parses a stream of bytes (UTF-8 text/event-stream) into SseEvent instances according to the W3C
  * Server-Sent Events specification.
  *
  * Protocol rules:
  *   - Lines starting with `:` are comments (ignored)
  *   - Blank lines dispatch the accumulated event
  *   - `data:` fields accumulate (joined with `\n` for multi-line data)
  *   - `event:`, `id:`, `retry:` fields set their respective values
  *   - Unknown field names are ignored
  */
object SseParser {

  /** Parse a byte stream into SSE events. */
  def parse: Pipe[IO, Byte, SseEvent] =
    _.through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .through(parseLines)

  /** Parse a stream of text lines into SSE events. */
  def parseLines: Pipe[IO, String, SseEvent] = { input =>
    def go(
        s: Stream[IO, String],
        dataLines: List[String],
        eventType: Option[String],
        id: Option[String],
        retry: Option[Int]
    ): Pull[IO, SseEvent, Unit] =
      s.pull.uncons1.flatMap {
        case None =>
          // End of stream — emit any pending event
          if dataLines.nonEmpty then {
            val event = SseEvent(dataLines.reverse.mkString("\n"), eventType, id, retry)
            Pull.output1(event)
          } else Pull.done

        case Some((line, rest)) =>
          if line.isEmpty then {
            // Blank line: dispatch event if we have data
            if dataLines.nonEmpty then {
              val event = SseEvent(dataLines.reverse.mkString("\n"), eventType, id, retry)
              Pull.output1(event) >> go(rest, Nil, None, None, None)
            } else {
              go(rest, Nil, None, None, None)
            }
          } else if line.startsWith(":") then {
            // Comment line — ignore
            go(rest, dataLines, eventType, id, retry)
          } else {
            // Parse field:value
            val (field, value) = line.indexOf(':') match {
              case -1 => (line, "")
              case idx =>
                val f = line.substring(0, idx)
                val v = line.substring(idx + 1)
                // Strip single leading space from value per spec
                (f, if v.startsWith(" ") then v.substring(1) else v)
            }

            field match {
              case "data"  => go(rest, value :: dataLines, eventType, id, retry)
              case "event" => go(rest, dataLines, Some(value), id, retry)
              case "id"    => go(rest, dataLines, eventType, Some(value), retry)
              case "retry" =>
                val retryVal = value.trim.toIntOption
                go(rest, dataLines, eventType, id, retryVal.orElse(retry))
              case _ =>
                // Unknown field — ignore per spec
                go(rest, dataLines, eventType, id, retry)
            }
          }
      }

    go(input, Nil, None, None, None).stream
  }
}
