package io.constellation.stream.connector.sse

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import fs2.Stream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SseParserTest extends AnyFlatSpec with Matchers {

  private def parseLines(lines: String*): List[SseEvent] =
    Stream.emits(lines).through(SseParser.parseLines).compile.toList.unsafeRunSync()

  "SseParser" should "parse a single event" in {
    val events = parseLines("data: hello", "")
    events should have size 1
    events.head.data shouldBe "hello"
    events.head.eventType shouldBe None
    events.head.id shouldBe None
    events.head.retry shouldBe None
  }

  it should "parse multi-line data" in {
    val events = parseLines("data: line1", "data: line2", "data: line3", "")
    events should have size 1
    events.head.data shouldBe "line1\nline2\nline3"
  }

  it should "parse event with type, id, and retry" in {
    val events = parseLines(
      "event: update",
      "id: 42",
      "retry: 3000",
      "data: payload",
      ""
    )
    events should have size 1
    events.head.data shouldBe "payload"
    events.head.eventType shouldBe Some("update")
    events.head.id shouldBe Some("42")
    events.head.retry shouldBe Some(3000)
  }

  it should "ignore comment lines" in {
    val events = parseLines(
      ": this is a comment",
      "data: actual data",
      ": another comment",
      ""
    )
    events should have size 1
    events.head.data shouldBe "actual data"
  }

  it should "handle empty data field" in {
    val events = parseLines("data:", "")
    events should have size 1
    events.head.data shouldBe ""
  }

  it should "parse multiple events" in {
    val events = parseLines(
      "data: first",
      "",
      "data: second",
      "",
      "data: third",
      ""
    )
    events should have size 3
    events.map(_.data) shouldBe List("first", "second", "third")
  }

  it should "skip blank lines with no accumulated data" in {
    val events = parseLines("", "", "data: hello", "")
    events should have size 1
    events.head.data shouldBe "hello"
  }

  it should "strip single leading space from value" in {
    val events = parseLines("data:  two spaces", "")
    events should have size 1
    events.head.data shouldBe " two spaces" // first space stripped, second kept
  }

  it should "handle field with no colon" in {
    val events = parseLines("data", "")
    events should have size 1
    events.head.data shouldBe ""
  }

  it should "ignore unknown fields" in {
    val events = parseLines("custom: value", "data: hello", "")
    events should have size 1
    events.head.data shouldBe "hello"
  }

  it should "ignore non-numeric retry values" in {
    val events = parseLines("retry: not-a-number", "data: hello", "")
    events should have size 1
    events.head.retry shouldBe None
  }

  it should "emit pending event at end of stream" in {
    val events = parseLines("data: no-trailing-blank")
    events should have size 1
    events.head.data shouldBe "no-trailing-blank"
  }

  it should "parse bytes through full pipeline" in {
    val text   = "data: from bytes\n\n"
    val bytes  = text.getBytes("UTF-8")
    val events = Stream.emits(bytes).through(SseParser.parse).compile.toList.unsafeRunSync()

    events should have size 1
    events.head.data shouldBe "from bytes"
  }

  it should "reset event fields between events" in {
    val events = parseLines(
      "event: type1",
      "id: id1",
      "data: first",
      "",
      "data: second",
      ""
    )
    events should have size 2
    events(0).eventType shouldBe Some("type1")
    events(0).id shouldBe Some("id1")
    events(1).eventType shouldBe None
    events(1).id shouldBe None
  }
}
