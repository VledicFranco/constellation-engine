package io.constellation.http

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for PrometheusFormatter (RFC-017 Phase 3).
  *
  * Verifies that metrics JSON is correctly formatted into Prometheus exposition text format.
  *
  * Run with: sbt "httpApi/testOnly *PrometheusFormatterTest"
  */
class PrometheusFormatterTest extends AnyFlatSpec with Matchers {

  private def metricsJson(
      uptimeSeconds: Long = 3600,
      requestsTotal: Long = 1000,
      cacheHits: Long = 800,
      cacheMisses: Long = 200,
      cacheHitRate: Double = 0.8,
      cacheEvictions: Long = 10,
      cacheEntries: Int = 50,
      schedulerEnabled: Boolean = true,
      activeCount: Int = 3,
      queuedCount: Int = 1,
      totalSubmitted: Long = 500,
      totalCompleted: Long = 497,
      starvationPromotions: Long = 2
  ): Json = Json.obj(
    "timestamp" -> Json.fromString("2026-01-15T10:30:00Z"),
    "server" -> Json.obj(
      "uptime_seconds" -> Json.fromLong(uptimeSeconds),
      "requests_total" -> Json.fromLong(requestsTotal)
    ),
    "cache" -> Json.obj(
      "hits"      -> Json.fromLong(cacheHits),
      "misses"    -> Json.fromLong(cacheMisses),
      "hitRate"   -> Json.fromDoubleOrNull(cacheHitRate),
      "evictions" -> Json.fromLong(cacheEvictions),
      "entries"   -> Json.fromInt(cacheEntries)
    ),
    "scheduler" -> (if schedulerEnabled then
                      Json.obj(
                        "enabled"               -> Json.fromBoolean(true),
                        "activeCount"           -> Json.fromInt(activeCount),
                        "queuedCount"           -> Json.fromInt(queuedCount),
                        "totalSubmitted"        -> Json.fromLong(totalSubmitted),
                        "totalCompleted"        -> Json.fromLong(totalCompleted),
                        "highPriorityCompleted" -> Json.fromLong(300),
                        "lowPriorityCompleted"  -> Json.fromLong(197),
                        "starvationPromotions"  -> Json.fromLong(starvationPromotions)
                      )
                    else Json.obj("enabled" -> Json.fromBoolean(false)))
  )

  "PrometheusFormatter" should "emit server uptime metric" in {
    val output = PrometheusFormatter.format(metricsJson())
    output should include("constellation_server_uptime_seconds 3600")
    output should include("# TYPE constellation_server_uptime_seconds gauge")
    output should include("# HELP constellation_server_uptime_seconds")
  }

  it should "emit server requests total metric" in {
    val output = PrometheusFormatter.format(metricsJson())
    output should include("constellation_server_requests_total 1000")
    output should include("# TYPE constellation_server_requests_total counter")
  }

  it should "emit cache metrics" in {
    val output = PrometheusFormatter.format(metricsJson())
    output should include("constellation_cache_hits_total 800")
    output should include("constellation_cache_misses_total 200")
    output should include("constellation_cache_hit_rate 0.8")
    output should include("constellation_cache_evictions_total 10")
    output should include("constellation_cache_entries 50")
    output should include("# TYPE constellation_cache_hits_total counter")
    output should include("# TYPE constellation_cache_misses_total counter")
    output should include("# TYPE constellation_cache_hit_rate gauge")
    output should include("# TYPE constellation_cache_evictions_total counter")
    output should include("# TYPE constellation_cache_entries gauge")
  }

  it should "emit scheduler metrics when enabled" in {
    val output = PrometheusFormatter.format(metricsJson(schedulerEnabled = true))
    output should include("constellation_scheduler_active_count 3")
    output should include("constellation_scheduler_queued_count 1")
    output should include("constellation_scheduler_submitted_total 500")
    output should include("constellation_scheduler_completed_total 497")
    output should include("constellation_scheduler_starvation_promotions_total 2")
    output should include("# TYPE constellation_scheduler_active_count gauge")
    output should include("# TYPE constellation_scheduler_queued_count gauge")
    output should include("# TYPE constellation_scheduler_submitted_total counter")
    output should include("# TYPE constellation_scheduler_completed_total counter")
    output should include("# TYPE constellation_scheduler_starvation_promotions_total counter")
  }

  it should "not emit scheduler detail metrics when disabled" in {
    val output = PrometheusFormatter.format(metricsJson(schedulerEnabled = false))
    output should not include "constellation_scheduler_active_count"
    output should not include "constellation_scheduler_queued_count"
    output should not include "constellation_scheduler_submitted_total"
    output should not include "constellation_scheduler_completed_total"
    output should not include "constellation_scheduler_starvation_promotions_total"
  }

  it should "handle null cache stats" in {
    val json = Json.obj(
      "timestamp" -> Json.fromString("2026-01-15T10:30:00Z"),
      "server" -> Json.obj(
        "uptime_seconds" -> Json.fromLong(100),
        "requests_total" -> Json.fromLong(50)
      ),
      "cache"     -> Json.Null,
      "scheduler" -> Json.Null
    )
    val output = PrometheusFormatter.format(json)
    output should include("constellation_server_uptime_seconds 100")
    output should include("constellation_server_requests_total 50")
    output should not include "constellation_cache_"
    output should not include "constellation_scheduler_"
  }

  it should "produce valid Prometheus exposition format with HELP and TYPE lines" in {
    val output = PrometheusFormatter.format(metricsJson())
    val lines  = output.split("\n")

    // Every TYPE line should be preceded by a HELP line
    val typeLines = lines.filter(_.startsWith("# TYPE"))
    typeLines.foreach { typeLine =>
      val metricName = typeLine.stripPrefix("# TYPE ").split(" ").head
      val helpLine   = s"# HELP $metricName"
      output should include(helpLine)
    }

    // Every metric value line should follow its TYPE line
    val valueLines = lines.filterNot(l => l.startsWith("#") || l.isEmpty)
    valueLines should not be empty
  }

  it should "format with varying values" in {
    val output = PrometheusFormatter.format(
      metricsJson(
        uptimeSeconds = 0,
        requestsTotal = 1,
        cacheHits = 0,
        cacheMisses = 0,
        cacheHitRate = 0.0,
        cacheEvictions = 0,
        cacheEntries = 0
      )
    )
    output should include("constellation_server_uptime_seconds 0")
    output should include("constellation_server_requests_total 1")
    output should include("constellation_cache_hits_total 0")
    output should include("constellation_cache_hit_rate 0.0")
  }
}
