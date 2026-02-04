package io.constellation.http

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import io.constellation.{CValue, Constellation, ExecutionOptions, DataSignature, LoadedPipeline, Module, ModuleNodeSpec, PipelineStore, SuspensionHandle, SuspensionStore}
import io.constellation.execution.{GlobalScheduler, TokenBucketRateLimiter}
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.Path
import scala.concurrent.duration.*

/** HTTP server for the Constellation Engine API
  *
  * Provides a REST API for compiling constellation-lang programs, managing DAGs and modules, and
  * executing computational pipelines.
  *
  * Example usage:
  * {{{
  * import cats.effect.IO
  * import cats.effect.unsafe.implicits.global
  * import io.constellation.impl.ConstellationImpl
  * import io.constellation.lang.LangCompiler
  *
  * val constellation = ConstellationImpl.create.unsafeRunSync()
  * val compiler = LangCompiler.empty
  *
  * // Minimal (no hardening — same as before):
  * ConstellationServer
  *   .builder(constellation, compiler)
  *   .withPort(8080)
  *   .build
  *   .use(_ => IO.never)
  *   .unsafeRunSync()
  *
  * // Fully hardened:
  * ConstellationServer
  *   .builder(constellation, compiler)
  *   .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  *   .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  *   .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  *   .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  *   .run
  * }}}
  */
object ConstellationServer {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.http.ConstellationServer")

  /** Default port, can be overridden via CONSTELLATION_PORT environment variable */
  val DefaultPort: Int = sys.env.get("CONSTELLATION_PORT").flatMap(_.toIntOption).getOrElse(8080)

  /** Scheduler configuration from environment variables */
  object SchedulerConfig {

    /** Whether bounded scheduler is enabled (CONSTELLATION_SCHEDULER_ENABLED) */
    val enabled: Boolean = sys.env
      .get("CONSTELLATION_SCHEDULER_ENABLED")
      .map(_.toLowerCase)
      .exists(v => v == "true" || v == "1" || v == "yes")

    /** Maximum concurrency for bounded scheduler (CONSTELLATION_SCHEDULER_MAX_CONCURRENCY) */
    val maxConcurrency: Int = sys.env
      .get("CONSTELLATION_SCHEDULER_MAX_CONCURRENCY")
      .flatMap(_.toIntOption)
      .getOrElse(16)

    /** Starvation timeout for priority boost (CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT) */
    val starvationTimeout: FiniteDuration = sys.env
      .get("CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT")
      .flatMap(parseDuration)
      .getOrElse(30.seconds)

    private def parseDuration(s: String): Option[FiniteDuration] = {
      val pattern = """(\d+)(s|ms|m|min|h)""".r
      s.toLowerCase match {
        case pattern(num, "s")   => num.toIntOption.map(_.seconds)
        case pattern(num, "ms")  => num.toIntOption.map(_.milliseconds)
        case pattern(num, "m")   => num.toIntOption.map(_.minutes)
        case pattern(num, "min") => num.toIntOption.map(_.minutes)
        case pattern(num, "h")   => num.toIntOption.map(_.hours)
        case _                   => s.toIntOption.map(_.seconds) // Default to seconds
      }
    }
  }

  /** Create a scheduler resource based on environment configuration.
    *
    * @return
    *   Resource that manages scheduler lifecycle, or unbounded if disabled
    */
  def schedulerResource: Resource[IO, GlobalScheduler] =
    if SchedulerConfig.enabled then {
      Resource.eval(
        logger.info(
          s"Creating bounded scheduler: maxConcurrency=${SchedulerConfig.maxConcurrency}, " +
            s"starvationTimeout=${SchedulerConfig.starvationTimeout}"
        )
      ) *>
        GlobalScheduler.bounded(
          maxConcurrency = SchedulerConfig.maxConcurrency,
          starvationTimeout = SchedulerConfig.starvationTimeout
        )
    } else {
      Resource.eval(logger.info("Using unbounded scheduler (default)")) *>
        Resource.pure(GlobalScheduler.unbounded)
    }

  /** Configuration for the HTTP server */
  case class Config(
      host: String = "0.0.0.0",
      port: Int = DefaultPort,
      dashboardConfig: Option[DashboardConfig] = None,
      authConfig: Option[AuthConfig] = None,
      corsConfig: Option[CorsConfig] = None,
      rateLimitConfig: Option[RateLimitConfig] = None,
      healthCheckConfig: HealthCheckConfig = HealthCheckConfig.default,
      pipelineLoaderConfig: Option[PipelineLoaderConfig] = None,
      persistentStorePath: Option[Path] = None
  )

  /** Builder for creating a Constellation HTTP server */
  class ServerBuilder(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry = FunctionRegistry.empty,
      config: Config = Config()
  ) {

    /** Set the host address */
    def withHost(host: String): ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry, config.copy(host = host))

    /** Set the port number */
    def withPort(port: Int): ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry, config.copy(port = port))

    /** Set the function registry for namespace endpoints */
    def withFunctionRegistry(registry: FunctionRegistry): ServerBuilder =
      new ServerBuilder(constellation, compiler, registry, config)

    /** Enable dashboard with default configuration */
    def withDashboard: ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(dashboardConfig = Some(DashboardConfig.fromEnv))
      )

    /** Enable dashboard with custom configuration */
    def withDashboard(dashboardConfig: DashboardConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(dashboardConfig = Some(dashboardConfig))
      )

    /** Enable dashboard with CST directory */
    def withDashboard(cstDirectory: Path): ServerBuilder =
      withDashboard(DashboardConfig.fromEnv.copy(cstDirectory = Some(cstDirectory)))

    /** Enable API key authentication */
    def withAuth(authConfig: AuthConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(authConfig = Some(authConfig))
      )

    /** Enable CORS */
    def withCors(corsConfig: CorsConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(corsConfig = Some(corsConfig))
      )

    /** Enable rate limiting */
    def withRateLimit(rateLimitConfig: RateLimitConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(rateLimitConfig = Some(rateLimitConfig))
      )

    /** Configure health check endpoints */
    def withHealthChecks(healthCheckConfig: HealthCheckConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(healthCheckConfig = healthCheckConfig)
      )

    /** Configure startup pipeline loader to pre-load `.cst` files from a directory */
    def withPipelineLoader(pipelineLoaderConfig: PipelineLoaderConfig): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(pipelineLoaderConfig = Some(pipelineLoaderConfig))
      )

    /** Enable persistent filesystem-backed pipeline store.
      *
      * Wraps the constellation's existing PipelineStore with a filesystem-backed layer. Pipeline
      * images, aliases, and syntactic indices are persisted to disk so they survive server restarts.
      *
      * @param directory
      *   Root directory for the persistent store (e.g. `.constellation-store`)
      */
    def withPersistentPipelineStore(directory: Path): ServerBuilder =
      new ServerBuilder(
        constellation,
        compiler,
        functionRegistry,
        config.copy(persistentStorePath = Some(directory))
      )

    /** Build the HTTP server resource.
      *
      * Validates all configuration at startup. Fails fast with clear errors if any config is
      * invalid.
      */
    def build: Resource[IO, Server] = {
      // Validate all configs at startup (fail fast)
      val validationErrors = List(
        config.authConfig.flatMap(_.validate.left.toOption.map(e => s"AuthConfig: $e")),
        config.corsConfig.flatMap(_.validate.left.toOption.map(e => s"CorsConfig: $e")),
        config.rateLimitConfig.flatMap(_.validate.left.toOption.map(e => s"RateLimitConfig: $e"))
      ).flatten

      if validationErrors.nonEmpty then {
        val msg = validationErrors.mkString("Configuration validation failed:\n  - ", "\n  - ", "")
        return Resource.eval(IO.raiseError(new IllegalArgumentException(msg)))
      }

      val healthRoutes =
        HealthCheckRoutes.routes(config.healthCheckConfig, compiler = Some(compiler))
      val lspHandler = LspWebSocketHandler(constellation, compiler)

      val host = Host.fromString(config.host).getOrElse(host"0.0.0.0")
      val port = Port.fromInt(config.port).getOrElse(port"8080")

      // Optionally create dashboard routes
      val dashboardRoutesIO: IO[Option[DashboardRoutes]] = config.dashboardConfig match {
        case Some(dashConfig) if dashConfig.enableDashboard =>
          DashboardRoutes.withDefaultStorage(constellation, compiler, dashConfig).map(Some(_))
        case _ =>
          IO.pure(None)
      }

      // Pre-allocate rate limiter bucket map (effectful) so middleware can be applied purely
      val rateLimitBucketsIO
          : IO[Option[(RateLimitConfig, Ref[IO, Map[String, TokenBucketRateLimiter]])]] =
        config.rateLimitConfig match {
          case Some(rlConfig) =>
            Ref
              .of[IO, Map[String, TokenBucketRateLimiter]](Map.empty)
              .map(ref => Some((rlConfig, ref)))
          case None =>
            IO.pure(None)
        }

      for {
        dashboardRoutesOpt <- Resource.eval(dashboardRoutesIO)
        rateLimitState     <- Resource.eval(rateLimitBucketsIO)

        // Optionally wrap the constellation with a persistent pipeline store
        effectiveConstellation <- Resource.eval(
          config.persistentStorePath match {
            case Some(storePath) =>
              FileSystemPipelineStore.init(storePath, constellation.PipelineStore).map { fsStore =>
                wrapWithStore(constellation, fsStore)
              }
            case None =>
              IO.pure(constellation)
          }
        )

        // Create pipeline version store, file path map, and canary router
        versionStore <- Resource.eval(PipelineVersionStore.init)
        filePathRef  <- Resource.eval(Ref.of[IO, Map[String, java.nio.file.Path]](Map.empty))
        canaryRouter <- Resource.eval(CanaryRouter.init)

        // Load pipelines from directory if configured (before server starts listening)
        _ <- Resource.eval(
          config.pipelineLoaderConfig match {
            case Some(plConfig) =>
              PipelineLoader.load(plConfig, effectiveConstellation, compiler).flatMap { result =>
                for {
                  // Populate file path map from loader results
                  _ <- filePathRef.update(_ ++ result.filePaths)
                  // Record v1 in version store for each loaded pipeline
                  _ <- result.filePaths.toList.traverse_ { case (alias, _) =>
                    effectiveConstellation.PipelineStore.resolve(alias).flatMap {
                      case Some(hash) => versionStore.recordVersion(alias, hash, None).void
                      case None       => IO.unit
                    }
                  }
                  _ <- logger.info(
                    s"PipelineLoader: startup complete — loaded=${result.loaded}, " +
                      s"failed=${result.failed}, skipped=${result.skipped}"
                  )
                } yield ()
              }
            case None => IO.unit
          }
        )

        // Create HTTP routes with version store and canary router wired in
        httpRoutes = new ConstellationRoutes(
          effectiveConstellation,
          compiler,
          functionRegistry,
          scheduler = None,
          lifecycle = None,
          versionStore = Some(versionStore),
          filePathMap = Some(filePathRef),
          canaryRouter = Some(canaryRouter)
        ).routes

        server <- EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withIdleTimeout(scala.concurrent.duration.Duration.Inf) // Disable WebSocket idle timeout
          .withHttpWebSocketApp { wsb =>
            // Combine core routes + health routes + optional dashboard routes + WebSocket routes
            val coreRoutes = httpRoutes <+> healthRoutes <+> lspHandler.routes(wsb)
            val withDashboard = dashboardRoutesOpt match {
              case Some(dashboardRoutes) => dashboardRoutes.routes <+> coreRoutes
              case None                  => coreRoutes
            }

            // Apply middleware layers (inner to outer): Auth → Rate Limit → CORS
            // When all disabled (default), routes pass through with zero wrapping.
            val withAuth = config.authConfig match {
              case Some(ac) if ac.isEnabled => AuthMiddleware(ac)(withDashboard)
              case _                        => withDashboard
            }

            val withRateLimit = rateLimitState match {
              case Some((rlConfig, buckets)) =>
                RateLimitMiddleware.withBuckets(rlConfig, buckets)(withAuth)
              case None => withAuth
            }

            val withCors = config.corsConfig match {
              case Some(cc) if cc.isEnabled => CorsMiddleware(cc)(withRateLimit)
              case _                        => withRateLimit
            }

            withCors.orNotFound
          }
          .build
      } yield server
    }

    /** Run the server and return when it completes */
    def run: IO[Unit] =
      build.use { server =>
        val authSummary = config.authConfig
          .filter(_.isEnabled)
          .map(c => s"auth=${c.hashedKeys.size} keys")
          .getOrElse("auth=off")
        val corsSummary = config.corsConfig
          .filter(_.isEnabled)
          .map(c => s"cors=${c.allowedOrigins.size} origins")
          .getOrElse("cors=off")
        val rateSummary = config.rateLimitConfig
          .map(c => s"rateLimit=${c.requestsPerMinute}rpm")
          .getOrElse("rateLimit=off")
        val loaderSummary = config.pipelineLoaderConfig
          .map(c => s"pipelineDir=${c.directory}")
          .getOrElse("pipelineLoader=off")
        logger.info(
          s"Constellation HTTP API server started at http://${config.host}:${config.port} [$authSummary, $corsSummary, $rateSummary, $loaderSummary]"
        ) *>
          IO.never
      }
  }

  /** Wrap a Constellation with a different PipelineStore, delegating all other operations.
    *
    * Used by `withPersistentPipelineStore()` to transparently intercept store operations while
    * keeping the rest of the Constellation behavior unchanged.
    */
  private def wrapWithStore(delegate: Constellation, store: PipelineStore): Constellation =
    new Constellation {
      def getModules: IO[List[ModuleNodeSpec]] = delegate.getModules
      def getModuleByName(name: String): IO[Option[Module.Uninitialized]] = delegate.getModuleByName(name)
      def setModule(module: Module.Uninitialized): IO[Unit] = delegate.setModule(module)
      def PipelineStore: PipelineStore = store
      def run(loaded: LoadedPipeline, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
        delegate.run(loaded, inputs, options)
      def run(ref: String, inputs: Map[String, CValue], options: ExecutionOptions): IO[DataSignature] =
        delegate.run(ref, inputs, options)
      override def suspensionStore: Option[SuspensionStore] = delegate.suspensionStore
      def resumeFromStore(
          handle: SuspensionHandle,
          additionalInputs: Map[String, CValue],
          resolvedNodes: Map[String, CValue],
          options: ExecutionOptions
      ): IO[DataSignature] =
        delegate.resumeFromStore(handle, additionalInputs, resolvedNodes, options)
    }

  /** Create a new server builder
    *
    * @param constellation
    *   The constellation engine instance
    * @param compiler
    *   The constellation-lang compiler instance
    * @return
    *   A builder for configuring and starting the server
    */
  def builder(constellation: Constellation, compiler: LangCompiler): ServerBuilder =
    new ServerBuilder(constellation, compiler, compiler.functionRegistry)
}
