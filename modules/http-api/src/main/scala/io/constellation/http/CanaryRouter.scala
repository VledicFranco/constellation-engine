package io.constellation.http

import cats.effect.{IO, Ref}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/** Configuration for a canary deployment. */
case class CanaryConfig(
    initialWeight: Double = 0.05,
    promotionSteps: List[Double] = List(0.10, 0.25, 0.50, 1.0),
    observationWindow: FiniteDuration = FiniteDuration(5, "minutes"),
    errorThreshold: Double = 0.05,
    latencyThresholdMs: Option[Long] = None,
    minRequests: Int = 10,
    autoPromote: Boolean = true
)

/** Status of a canary deployment. */
enum CanaryStatus {
  case Observing, Promoting, RolledBack, Complete
}

/** Per-version metrics collected during a canary deployment. */
case class VersionMetrics(
    requests: Long = 0,
    successes: Long = 0,
    failures: Long = 0,
    latencies: Vector[Double] = Vector.empty
) {
  def errorRate: Double = if requests == 0 then 0.0 else failures.toDouble / requests

  def avgLatencyMs: Double = if latencies.isEmpty then 0.0 else latencies.sum / latencies.size

  def p99LatencyMs: Double =
    if latencies.isEmpty then 0.0
    else {
      val sorted = latencies.sorted
      val idx    = math.ceil(sorted.size * 0.99).toInt - 1
      sorted(math.max(0, math.min(idx, sorted.size - 1)))
    }

  def record(success: Boolean, latencyMs: Double): VersionMetrics =
    copy(
      requests = requests + 1,
      successes = if success then successes + 1 else successes,
      failures = if success then failures else failures + 1,
      latencies = latencies :+ latencyMs
    )
}

/** Metrics for both old and new versions in a canary deployment. */
case class CanaryMetrics(
    oldVersion: VersionMetrics = VersionMetrics(),
    newVersion: VersionMetrics = VersionMetrics()
)

/** State of an active canary deployment. */
case class CanaryState(
    pipelineName: String,
    oldVersion: PipelineVersion,
    newVersion: PipelineVersion,
    config: CanaryConfig,
    currentWeight: Double,
    currentStep: Int,
    status: CanaryStatus,
    startedAt: Instant,
    stepStartedAt: Instant,
    metrics: CanaryMetrics
)

/** Router that manages canary deployments for pipelines.
  *
  * Provides weighted traffic splitting between old and new pipeline versions, collects per-version
  * metrics, and supports auto-promotion/auto-rollback based on error rate and latency thresholds.
  */
trait CanaryRouter {

  /** Start a canary deployment. Returns Left(error) if one is already active for this pipeline. */
  def startCanary(
      name: String,
      oldVersion: PipelineVersion,
      newVersion: PipelineVersion,
      config: CanaryConfig
  ): IO[Either[String, CanaryState]]

  /** Select which structural hash to use for execution. Returns None if no canary is active. */
  def selectVersion(name: String): IO[Option[String]]

  /** Record an execution result and evaluate promotion/rollback criteria. */
  def recordResult(
      name: String,
      structuralHash: String,
      success: Boolean,
      latencyMs: Double
  ): IO[Option[CanaryState]]

  /** Get the current canary state for a pipeline. */
  def getState(name: String): IO[Option[CanaryState]]

  /** Manually promote to the next step. */
  def promote(name: String): IO[Option[CanaryState]]

  /** Manually rollback (abort the canary, revert to old version). */
  def rollback(name: String): IO[Option[CanaryState]]

  /** Abort the canary deployment (same as rollback). */
  def abort(name: String): IO[Option[CanaryState]]
}

object CanaryRouter {

  /** Create a new in-memory canary router backed by `Ref`. */
  def init: IO[CanaryRouter] =
    for {
      stateRef <- Ref.of[IO, Map[String, CanaryState]](Map.empty)
    } yield new CanaryRouter {

      def startCanary(
          name: String,
          oldVersion: PipelineVersion,
          newVersion: PipelineVersion,
          config: CanaryConfig
      ): IO[Either[String, CanaryState]] =
        IO.realTimeInstant.flatMap { now =>
          stateRef.modify { states =>
            states.get(name) match {
              case Some(existing) if existing.status == CanaryStatus.Observing =>
                (states, Left(s"Canary deployment already active for pipeline '$name'"))
              case _ =>
                val state = CanaryState(
                  pipelineName = name,
                  oldVersion = oldVersion,
                  newVersion = newVersion,
                  config = config,
                  currentWeight = config.initialWeight,
                  currentStep = 0,
                  status = CanaryStatus.Observing,
                  startedAt = now,
                  stepStartedAt = now,
                  metrics = CanaryMetrics()
                )
                (states.updated(name, state), Right(state))
            }
          }
        }

      def selectVersion(name: String): IO[Option[String]] =
        stateRef.get.map { states =>
          states.get(name).flatMap { state =>
            if state.status != CanaryStatus.Observing then None
            else {
              val rnd = Random.nextDouble()
              if rnd < state.currentWeight then Some(state.newVersion.structuralHash)
              else Some(state.oldVersion.structuralHash)
            }
          }
        }

      def recordResult(
          name: String,
          structuralHash: String,
          success: Boolean,
          latencyMs: Double
      ): IO[Option[CanaryState]] =
        IO.realTimeInstant.flatMap { now =>
          stateRef.modify { states =>
            states.get(name) match {
              case None => (states, None)
              case Some(state) if state.status != CanaryStatus.Observing =>
                (states, Some(state))
              case Some(state) =>
                val isNewVersion = structuralHash == state.newVersion.structuralHash
                val updatedMetrics =
                  if isNewVersion then
                    state.metrics.copy(newVersion = state.metrics.newVersion.record(success, latencyMs))
                  else
                    state.metrics.copy(oldVersion = state.metrics.oldVersion.record(success, latencyMs))

                val updatedState = state.copy(metrics = updatedMetrics)

                // Evaluate auto-promotion/rollback
                val evaluated = evaluateCanary(updatedState, now)
                (states.updated(name, evaluated), Some(evaluated))
            }
          }
        }

      def getState(name: String): IO[Option[CanaryState]] =
        stateRef.get.map(_.get(name))

      def promote(name: String): IO[Option[CanaryState]] =
        IO.realTimeInstant.flatMap { now =>
          stateRef.modify { states =>
            states.get(name) match {
              case None => (states, None)
              case Some(state) if state.status != CanaryStatus.Observing =>
                (states, Some(state))
              case Some(state) =>
                val advanced = advanceStep(state, now)
                (states.updated(name, advanced), Some(advanced))
            }
          }
        }

      def rollback(name: String): IO[Option[CanaryState]] =
        stateRef.modify { states =>
          states.get(name) match {
            case None => (states, None)
            case Some(state) =>
              val rolledBack = state.copy(status = CanaryStatus.RolledBack)
              (states.updated(name, rolledBack), Some(rolledBack))
          }
        }

      def abort(name: String): IO[Option[CanaryState]] =
        rollback(name)

      /** Evaluate whether to auto-promote or auto-rollback based on the canary config. */
      private def evaluateCanary(state: CanaryState, now: Instant): CanaryState = {
        if !state.config.autoPromote then return state

        val newMetrics = state.metrics.newVersion

        // Not enough requests yet
        if newMetrics.requests < state.config.minRequests then return state

        // Observation window hasn't elapsed
        val elapsed = java.time.Duration.between(state.stepStartedAt, now)
        val windowMillis = state.config.observationWindow.toMillis
        if elapsed.toMillis < windowMillis then return state

        // Check error threshold
        if newMetrics.errorRate > state.config.errorThreshold then
          return state.copy(status = CanaryStatus.RolledBack)

        // Check latency threshold
        state.config.latencyThresholdMs match {
          case Some(threshold) if newMetrics.p99LatencyMs > threshold =>
            return state.copy(status = CanaryStatus.RolledBack)
          case _ => // OK
        }

        // Healthy — advance to next step
        advanceStep(state, now)
      }

      /** Advance to the next promotion step, or mark as Complete if this was the final step. */
      private def advanceStep(state: CanaryState, now: Instant): CanaryState = {
        val nextStep = state.currentStep + 1
        if nextStep >= state.config.promotionSteps.size then {
          // Final step completed — canary is done
          state.copy(
            currentWeight = 1.0,
            currentStep = nextStep,
            status = CanaryStatus.Complete
          )
        } else {
          val nextWeight = state.config.promotionSteps(nextStep)
          state.copy(
            currentWeight = nextWeight,
            currentStep = nextStep,
            status = CanaryStatus.Observing,
            stepStartedAt = now,
            metrics = state.metrics.copy(newVersion = VersionMetrics())
          )
        }
      }
    }
}
