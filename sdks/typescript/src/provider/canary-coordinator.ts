/**
 * Coordinates sequential canary rollout across instances.
 *
 * For each instance: replace modules -> wait observation window -> check health.
 * If unhealthy: rollback all previously upgraded instances to old modules.
 *
 * Mirrors the Scala CanaryCoordinator class.
 */

import type { CanaryConfig } from "../types/config.js";
import type { CanaryResult } from "../types/canary.js";
import type { ModuleDefinition } from "../types/module-definition.js";
import type { InstanceConnection } from "./instance-connection.js";

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export class CanaryCoordinator {
  private readonly connections: Map<string, InstanceConnection>;
  private readonly config: CanaryConfig;

  constructor(connections: Map<string, InstanceConnection>, config: CanaryConfig) {
    this.connections = connections;
    this.config = config;
  }

  /** Roll out new modules sequentially across all instances. */
  async rollout(
    oldModules: ModuleDefinition[],
    newModules: ModuleDefinition[],
  ): Promise<CanaryResult> {
    const instances = [...this.connections.entries()].sort(([a], [b]) => a.localeCompare(b));

    if (instances.length === 0) {
      return { status: "Promoted" };
    }

    return this.rolloutSequential(instances, oldModules, newModules, []);
  }

  private async rolloutSequential(
    remaining: Array<[string, InstanceConnection]>,
    oldModules: ModuleDefinition[],
    newModules: ModuleDefinition[],
    upgraded: Array<[string, InstanceConnection]>,
  ): Promise<CanaryResult> {
    if (remaining.length === 0) {
      return { status: "Promoted" };
    }

    const [[instanceId, conn], ...rest] = remaining;

    conn.replaceModules(newModules);
    await sleep(this.config.observationWindowMs);
    const healthy = conn.isHealthy;

    if (healthy) {
      return this.rolloutSequential(rest, oldModules, newModules, [
        ...upgraded,
        [instanceId, conn],
      ]);
    } else {
      // Rollback all previously upgraded instances + this one
      const allToRollback = [...upgraded, [instanceId, conn]] as Array<
        [string, InstanceConnection]
      >;
      for (const [, c] of allToRollback) {
        c.replaceModules(oldModules);
      }
      return {
        status: "RolledBack",
        reason: `Instance ${instanceId} failed health check after canary deployment`,
      };
    }
  }
}
