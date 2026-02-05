package io.constellation.http

import io.circe.Json

/** Formats metrics data into Prometheus exposition text format.
  *
  * Produces output conforming to the Prometheus text-based format:
  * https://prometheus.io/docs/instrumenting/exposition_formats/
  */
object PrometheusFormatter {

  /** Format a metrics JSON object into Prometheus exposition text format.
    *
    * @param metricsJson
    *   The metrics JSON object (same structure as the /metrics JSON response)
    * @return
    *   Prometheus exposition format text
    */
  def format(metricsJson: Json): String = {
    val sb     = new StringBuilder
    val cursor = metricsJson.hcursor

    // Server metrics
    cursor.downField("server").downField("uptime_seconds").as[Long].foreach { v =>
      sb.append("# HELP constellation_server_uptime_seconds Server uptime in seconds.\n")
      sb.append("# TYPE constellation_server_uptime_seconds gauge\n")
      sb.append(s"constellation_server_uptime_seconds $v\n")
    }

    cursor.downField("server").downField("requests_total").as[Long].foreach { v =>
      sb.append("# HELP constellation_server_requests_total Total HTTP requests served.\n")
      sb.append("# TYPE constellation_server_requests_total counter\n")
      sb.append(s"constellation_server_requests_total $v\n")
    }

    // Cache metrics
    val cache = cursor.downField("cache")
    if !cache.focus.exists(_.isNull) then {
      cache.downField("hits").as[Long].foreach { v =>
        sb.append("# HELP constellation_cache_hits_total Total cache hits.\n")
        sb.append("# TYPE constellation_cache_hits_total counter\n")
        sb.append(s"constellation_cache_hits_total $v\n")
      }

      cache.downField("misses").as[Long].foreach { v =>
        sb.append("# HELP constellation_cache_misses_total Total cache misses.\n")
        sb.append("# TYPE constellation_cache_misses_total counter\n")
        sb.append(s"constellation_cache_misses_total $v\n")
      }

      cache.downField("hitRate").as[Double].foreach { v =>
        sb.append("# HELP constellation_cache_hit_rate Cache hit rate.\n")
        sb.append("# TYPE constellation_cache_hit_rate gauge\n")
        sb.append(s"constellation_cache_hit_rate $v\n")
      }

      cache.downField("evictions").as[Long].foreach { v =>
        sb.append("# HELP constellation_cache_evictions_total Total cache evictions.\n")
        sb.append("# TYPE constellation_cache_evictions_total counter\n")
        sb.append(s"constellation_cache_evictions_total $v\n")
      }

      cache.downField("entries").as[Int].foreach { v =>
        sb.append("# HELP constellation_cache_entries Current number of cache entries.\n")
        sb.append("# TYPE constellation_cache_entries gauge\n")
        sb.append(s"constellation_cache_entries $v\n")
      }
    }

    // Scheduler metrics
    val sched = cursor.downField("scheduler")
    if !sched.focus.exists(_.isNull) then {
      sched.downField("enabled").as[Boolean].foreach { enabled =>
        if enabled then {
          sched.downField("activeCount").as[Int].foreach { v =>
            sb.append(
              "# HELP constellation_scheduler_active_count Currently active scheduled tasks.\n"
            )
            sb.append("# TYPE constellation_scheduler_active_count gauge\n")
            sb.append(s"constellation_scheduler_active_count $v\n")
          }

          sched.downField("queuedCount").as[Int].foreach { v =>
            sb.append(
              "# HELP constellation_scheduler_queued_count Tasks waiting in scheduler queue.\n"
            )
            sb.append("# TYPE constellation_scheduler_queued_count gauge\n")
            sb.append(s"constellation_scheduler_queued_count $v\n")
          }

          sched.downField("totalSubmitted").as[Long].foreach { v =>
            sb.append(
              "# HELP constellation_scheduler_submitted_total Total tasks submitted to scheduler.\n"
            )
            sb.append("# TYPE constellation_scheduler_submitted_total counter\n")
            sb.append(s"constellation_scheduler_submitted_total $v\n")
          }

          sched.downField("totalCompleted").as[Long].foreach { v =>
            sb.append(
              "# HELP constellation_scheduler_completed_total Total tasks completed by scheduler.\n"
            )
            sb.append("# TYPE constellation_scheduler_completed_total counter\n")
            sb.append(s"constellation_scheduler_completed_total $v\n")
          }

          sched.downField("starvationPromotions").as[Long].foreach { v =>
            sb.append(
              "# HELP constellation_scheduler_starvation_promotions_total Total starvation-based priority promotions.\n"
            )
            sb.append("# TYPE constellation_scheduler_starvation_promotions_total counter\n")
            sb.append(s"constellation_scheduler_starvation_promotions_total $v\n")
          }
        }
      }
    }

    sb.toString
  }
}
