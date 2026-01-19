package io.constellation.pool

import cats.effect.IO

/** Unified pool manager for runtime resources.
  *
  * Combines DeferredPool and RuntimeStatePool into a single interface for convenient access and
  * lifecycle management.
  *
  * ==Usage==
  *
  * {{{
  * // Create pool manager
  * val pool = RuntimePool.create().unsafeRunSync()
  *
  * // Use in Runtime.runPooled
  * Runtime.runPooled(dagSpec, inputs, modules, pool)
  * }}}
  *
  * ==Configuration==
  *
  * Pool sizes can be configured based on workload:
  *   - High throughput: Larger pools (more pre-allocation)
  *   - Low memory: Smaller pools (on-demand allocation)
  */
final class RuntimePool private (
    val deferredPool: DeferredPool,
    val statePool: RuntimeStatePool
) {

  /** Get combined metrics from both pools.
    */
  def getMetrics: RuntimePool.CombinedMetrics = RuntimePool.CombinedMetrics(
    deferred = deferredPool.getMetrics,
    state = statePool.getMetrics
  )

  /** Get current sizes of both pools.
    */
  def sizes: IO[(Int, Int)] = for {
    deferredSize <- deferredPool.size
    stateSize    <- statePool.size
  } yield (deferredSize, stateSize)
}

object RuntimePool {

  /** Configuration for pool sizes.
    */
  final case class Config(
      deferredInitialSize: Int = 100,
      deferredMaxSize: Int = 10000,
      stateInitialSize: Int = 10,
      stateMaxSize: Int = 50
  )

  object Config {
    val default: Config = Config()

    /** High throughput configuration with larger pools */
    val highThroughput: Config = Config(
      deferredInitialSize = 500,
      deferredMaxSize = 50000,
      stateInitialSize = 50,
      stateMaxSize = 200
    )

    /** Low memory configuration with minimal pooling */
    val lowMemory: Config = Config(
      deferredInitialSize = 10,
      deferredMaxSize = 100,
      stateInitialSize = 2,
      stateMaxSize = 10
    )

    /** No pooling (for testing or debugging) */
    val disabled: Config = Config(
      deferredInitialSize = 0,
      deferredMaxSize = 0,
      stateInitialSize = 0,
      stateMaxSize = 0
    )
  }

  /** Create a RuntimePool with default configuration.
    */
  def create(): IO[RuntimePool] = create(Config.default)

  /** Create a RuntimePool with custom configuration.
    */
  def create(config: Config): IO[RuntimePool] =
    for {
      deferredPool <- DeferredPool.create(config.deferredInitialSize, config.deferredMaxSize)
      statePool    <- RuntimeStatePool.create(config.stateInitialSize, config.stateMaxSize)
    } yield new RuntimePool(deferredPool, statePool)

  /** Create an empty pool (no pre-allocation, for testing).
    */
  def empty: IO[RuntimePool] =
    for {
      deferredPool <- DeferredPool.empty
      statePool    <- RuntimeStatePool.empty
    } yield new RuntimePool(deferredPool, statePool)

  /** Combined metrics from both pools.
    */
  final case class CombinedMetrics(
      deferred: DeferredPool.MetricsSnapshot,
      state: RuntimeStatePool.MetricsSnapshot
  ) {
    def overallHitRate: Double = {
      val totalHits     = deferred.hits + state.hits
      val totalAcquires = deferred.totalAcquires + state.totalAcquires
      if totalAcquires > 0 then totalHits.toDouble / totalAcquires else 0.0
    }
  }
}
