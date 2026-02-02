package io.constellation.lang.compiler

import cats.effect.IO
import cats.syntax.all.*
import io.constellation.{CType, CValue}
import io.constellation.cache.{CacheBackend, CacheKeyGenerator, CacheRegistry, InMemoryCacheBackend}
import io.constellation.execution.*
import io.constellation.lang.ast.{
  BackoffStrategy as ASTBackoffStrategy,
  ErrorStrategy as ASTErrorStrategy
}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.*
import java.util.UUID

/** Executes module operations with options from the DAG compiler.
  *
  * This is the integration layer between:
  *   - `IRModuleCallOptions` from the compiler
  *   - Runtime execution infrastructure (ModuleExecutor, CacheRegistry, LimiterRegistry,
  *     GlobalScheduler, etc.)
  *
  * ==Usage==
  *
  * {{{
  * val executor = ModuleOptionsExecutor.create.unsafeRunSync()
  *
  * val result = executor.executeWithOptions(
  *   operation = module.run(runtime),
  *   moduleId = moduleId,
  *   moduleName = "MyModule",
  *   options = irOptions,
  *   outputType = CType.CString,
  *   getFallbackValue = Some(() => IO.pure("default"))
  * )
  * }}}
  *
  * ==Priority Scheduling==
  *
  * When a GlobalScheduler is configured (via `withScheduler` or environment config), tasks with
  * `priority` options are submitted to the scheduler for global ordering. High-priority tasks from
  * any execution will run before low-priority tasks.
  */
class ModuleOptionsExecutor private (
    cacheRegistry: CacheRegistry,
    limiterRegistry: LimiterRegistry,
    scheduler: GlobalScheduler = GlobalScheduler.unbounded
) {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromClass[IO](classOf[ModuleOptionsExecutor])

  /** Execute an operation with the specified module call options.
    *
    * @param operation
    *   The IO operation to execute
    * @param moduleId
    *   The module UUID (for cache/limiter keys)
    * @param moduleName
    *   The module name (for logging)
    * @param options
    *   The module call options from the compiler
    * @param outputType
    *   The expected output type (for error strategy zero values)
    * @param inputs
    *   The input values passed to the module (for cache key generation)
    * @param getFallbackValue
    *   Optional function to get fallback value (evaluated lazily)
    * @return
    *   The result of execution with all options applied
    */
  def executeWithOptions[A](
      operation: IO[A],
      moduleId: UUID,
      moduleName: String,
      options: IRModuleCallOptions,
      outputType: CType,
      inputs: Map[String, CValue] = Map.empty,
      getFallbackValue: Option[() => IO[Any]] = None
  ): IO[Any] =
    if options.isEmpty then {
      // Fast path: no options, just run the operation
      operation
    } else {
      // Apply options in order (outer to inner):
      // 1. Lazy evaluation (if enabled, wrap in LazyValue)
      // 2. Rate control (throttle + concurrency)
      // 3. Caching
      // 4. Retry with timeout and fallback
      // 5. Error strategy
      // 6. Priority scheduling

      var wrapped: IO[Any] = operation.widen[Any]

      // Apply error strategy (innermost, catches errors from operation)
      options.onError.foreach { strategy =>
        val errorStrategy = convertErrorStrategy(strategy)
        wrapped = ErrorStrategyExecutor.execute(wrapped, errorStrategy, outputType, moduleName)
      }

      // Apply retry, timeout, and fallback
      wrapped = applyRetryTimeoutFallback(wrapped, moduleName, options, getFallbackValue)

      // Apply caching
      options.cacheMs.foreach { ttlMs =>
        wrapped =
          applyCaching(wrapped, moduleId, moduleName, inputs, ttlMs.millis, options.cacheBackend)
      }

      // Apply rate control (throttle + concurrency)
      wrapped = applyRateControl(wrapped, moduleName, options)

      // Apply priority scheduling
      options.priority.foreach { priority =>
        wrapped = applyPriority(wrapped, priority)
      }

      // Apply lazy evaluation (outermost)
      if options.lazyEval.getOrElse(false) then {
        wrapped = applyLazy(wrapped)
      }

      wrapped
    }

  /** Apply retry, timeout, and fallback options. */
  private def applyRetryTimeoutFallback[A](
      operation: IO[A],
      moduleName: String,
      options: IRModuleCallOptions,
      getFallbackValue: Option[() => IO[Any]]
  ): IO[Any] = {
    val hasRetry    = options.retry.exists(_ > 0)
    val hasTimeout  = options.timeoutMs.isDefined
    val hasFallback = getFallbackValue.isDefined

    if !hasRetry && !hasTimeout && !hasFallback then {
      operation
    } else {
      // Build execution options
      val execOptions = ExecutionOptions[Any](
        retry = options.retry,
        timeout = options.timeoutMs.map(_.millis),
        delay = options.delayMs.map(_.millis),
        backoff = options.backoff.map(convertBackoffStrategy),
        maxDelay = Some(30.seconds),
        fallback = getFallbackValue.map(f => f()),
        onRetry = Some((attempt, error) =>
          logger.warn(error)(s"[$moduleName] retry $attempt: ${error.getMessage}")
        ),
        onFallback =
          Some(error => logger.warn(error)(s"[$moduleName] using fallback: ${error.getMessage}"))
      )

      ModuleExecutor.execute(operation.widen[Any], execOptions)
    }
  }

  /** Apply caching option. */
  private def applyCaching[A](
      operation: IO[A],
      moduleId: UUID,
      moduleName: String,
      inputs: Map[String, CValue],
      ttl: FiniteDuration,
      backendName: Option[String]
  ): IO[Any] = {
    // Generate cache key including input values to prevent wrong results
    val inputHash = if inputs.isEmpty then {
      "no-inputs"
    } else {
      import io.constellation.ContentHash
      val inputsRepr = inputs.toList
        .sortBy(_._1)
        .map { case (k, v) => s"$k:${v.toString}" }
        .mkString(",")
      ContentHash.computeSHA256(inputsRepr.getBytes("UTF-8"))
    }
    val cacheKey = s"module:$moduleName:$moduleId:$inputHash"

    for {
      backend <- backendName match {
        case Some(name) =>
          cacheRegistry.get(name).flatMap {
            case Some(b) => IO.pure(b)
            case None    =>
              // Create and register a new backend if not found
              val newBackend = new InMemoryCacheBackend(maxSize = Some(1000))
              cacheRegistry.register(name, newBackend).as(newBackend)
          }
        case None =>
          cacheRegistry.default
      }
      result <- backend.getOrCompute[Any](cacheKey, ttl)(operation.widen[Any])
    } yield result
  }

  /** Apply rate control (throttle and/or concurrency). */
  private def applyRateControl[A](
      operation: IO[A],
      moduleName: String,
      options: IRModuleCallOptions
  ): IO[Any] = {
    val hasThrottle    = options.throttleCount.isDefined && options.throttlePerMs.isDefined
    val hasConcurrency = options.concurrency.isDefined

    if !hasThrottle && !hasConcurrency then {
      operation
    } else {
      val rateLimit = for {
        count <- options.throttleCount
        perMs <- options.throttlePerMs
      } yield RateLimit(count, perMs.millis)

      val rateControlOptions = RateControlOptions(
        throttle = rateLimit,
        concurrency = options.concurrency
      )

      RateControlExecutor.executeWithRateControl(
        operation.widen[Any],
        moduleName,
        rateControlOptions,
        limiterRegistry
      )
    }
  }

  /** Apply priority scheduling.
    *
    * Submits the operation to the global scheduler with the given priority. High-priority tasks (>=
    * 75) are executed before low-priority tasks (< 25) when the system is under load.
    *
    * @param operation
    *   The IO operation to schedule
    * @param priority
    *   Priority value (0-100, higher = more important)
    * @return
    *   The scheduled operation
    */
  private def applyPriority[A](operation: IO[A], priority: Int): IO[Any] = {
    // Clamp priority to valid range
    val clampedPriority = math.max(0, math.min(100, priority))

    // Submit to global scheduler - this provides cross-execution priority ordering
    // when using a bounded scheduler, or passes through immediately with unbounded
    scheduler.submit(clampedPriority, operation.widen[Any])
  }

  /** Apply lazy evaluation. */
  private def applyLazy[A](operation: IO[A]): IO[Any] =
    // Wrap in LazyValue for deferred execution
    LazyValue(operation.widen[Any]).flatMap(_.force)

  /** Convert AST backoff strategy to runtime backoff strategy. */
  private def convertBackoffStrategy(strategy: ASTBackoffStrategy): BackoffStrategy =
    strategy match {
      case ASTBackoffStrategy.Fixed       => BackoffStrategy.Fixed
      case ASTBackoffStrategy.Linear      => BackoffStrategy.Linear
      case ASTBackoffStrategy.Exponential => BackoffStrategy.Exponential
    }

  /** Convert AST error strategy to runtime error strategy. */
  private def convertErrorStrategy(strategy: ASTErrorStrategy): ErrorStrategy =
    strategy match {
      case ASTErrorStrategy.Propagate => ErrorStrategy.Propagate
      case ASTErrorStrategy.Skip      => ErrorStrategy.Skip
      case ASTErrorStrategy.Log       => ErrorStrategy.Log
      case ASTErrorStrategy.Wrap      => ErrorStrategy.Wrap
    }

  /** Get the cache registry for external access. */
  def getCacheRegistry: CacheRegistry = cacheRegistry

  /** Get the limiter registry for external access. */
  def getLimiterRegistry: LimiterRegistry = limiterRegistry

  /** Get the scheduler for external access. */
  def getScheduler: GlobalScheduler = scheduler
}

object ModuleOptionsExecutor {

  /** Create a new module options executor with default registries and unbounded scheduler. */
  def create: IO[ModuleOptionsExecutor] =
    for {
      cacheRegistry   <- CacheRegistry.create
      limiterRegistry <- LimiterRegistry.create
    } yield new ModuleOptionsExecutor(cacheRegistry, limiterRegistry)

  /** Create a module options executor with custom registries (unbounded scheduler). */
  def withRegistries(
      cacheRegistry: CacheRegistry,
      limiterRegistry: LimiterRegistry
  ): ModuleOptionsExecutor =
    new ModuleOptionsExecutor(cacheRegistry, limiterRegistry)

  /** Create a module options executor with a custom scheduler.
    *
    * Use this when you want priority-based scheduling across all executions.
    *
    * @param scheduler
    *   The global scheduler for priority ordering
    * @return
    *   IO that creates the executor
    */
  def createWithScheduler(scheduler: GlobalScheduler): IO[ModuleOptionsExecutor] =
    for {
      cacheRegistry   <- CacheRegistry.create
      limiterRegistry <- LimiterRegistry.create
    } yield new ModuleOptionsExecutor(cacheRegistry, limiterRegistry, scheduler)

  /** Create a module options executor with custom registries and scheduler.
    *
    * @param cacheRegistry
    *   Cache registry for caching options
    * @param limiterRegistry
    *   Limiter registry for rate limiting options
    * @param scheduler
    *   Global scheduler for priority scheduling
    * @return
    *   The executor instance
    */
  def withAll(
      cacheRegistry: CacheRegistry,
      limiterRegistry: LimiterRegistry,
      scheduler: GlobalScheduler
  ): ModuleOptionsExecutor =
    new ModuleOptionsExecutor(cacheRegistry, limiterRegistry, scheduler)
}
