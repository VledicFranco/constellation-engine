package io.constellation.provider.sdk

import cats.effect.IO
import cats.implicits.*

// ===== Canary Result =====

enum CanaryResult:
  case Promoted
  case RolledBack(reason: String)
  case PartialFailure(promoted: List[String], failed: List[String])

// ===== CanaryCoordinator =====

/** Coordinates sequential canary rollout across instances.
  *
  * For each instance: replace modules → wait observation window → check health.
  * If unhealthy: rollback all previously upgraded instances to old modules.
  */
class CanaryCoordinator(
    connections: Map[String, InstanceConnection],
    config: CanaryConfig
) {

  /** Roll out new modules sequentially across all instances.
    *
    * Returns Promoted if all instances pass health check,
    * RolledBack if any instance fails (with rollback of previously upgraded instances).
    */
  def rollout(
      oldModules: List[ModuleDefinition],
      newModules: List[ModuleDefinition]
  ): IO[CanaryResult] = {
    val instances = connections.toList.sortBy(_._1)

    if instances.isEmpty then IO.pure(CanaryResult.Promoted)
    else rolloutSequential(instances, oldModules, newModules, upgraded = List.empty)
  }

  private def rolloutSequential(
      remaining: List[(String, InstanceConnection)],
      oldModules: List[ModuleDefinition],
      newModules: List[ModuleDefinition],
      upgraded: List[(String, InstanceConnection)]
  ): IO[CanaryResult] =
    remaining match {
      case Nil =>
        IO.pure(CanaryResult.Promoted)

      case (instanceId, conn) :: rest =>
        for {
          _       <- conn.replaceModules(newModules)
          _       <- IO.sleep(config.observationWindow)
          healthy <- conn.isHealthy
          result <- if healthy then
            rolloutSequential(rest, oldModules, newModules, upgraded :+ (instanceId -> conn))
          else {
            // Rollback all previously upgraded instances
            val allToRollback = upgraded :+ (instanceId -> conn)
            allToRollback.traverse_ { case (_, c) => c.replaceModules(oldModules) }.as(
              CanaryResult.RolledBack(s"Instance $instanceId failed health check after canary deployment")
            )
          }
        } yield result
    }
}
