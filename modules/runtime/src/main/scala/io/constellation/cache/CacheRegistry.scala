package io.constellation.cache

import cats.effect.{IO, Ref}
import cats.implicits._

/** Registry for managing multiple cache backends.
  *
  * Allows configuring different backends for different use cases:
  * - `memory` - In-process cache for development
  * - `caffeine` - Caffeine-based cache for production single instance
  * - `redis` - Distributed cache for multi-instance deployments
  *
  * ==Usage==
  *
  * {{{
  * val registry = CacheRegistry.create.unsafeRunSync()
  *
  * // Register backends
  * registry.register("memory", InMemoryCacheBackend()).unsafeRunSync()
  * registry.register("redis", redisCacheBackend).unsafeRunSync()
  *
  * // Get backend by name
  * val cache = registry.get("redis").unsafeRunSync()
  *
  * // Use default backend
  * val default = registry.default.unsafeRunSync()
  * }}}
  */
trait CacheRegistry {

  /** Register a cache backend with a name. */
  def register(name: String, backend: CacheBackend): IO[Unit]

  /** Get a cache backend by name. */
  def get(name: String): IO[Option[CacheBackend]]

  /** Get the default cache backend.
    * Returns the first registered backend, or InMemoryCacheBackend if none.
    */
  def default: IO[CacheBackend]

  /** Set the default backend by name. */
  def setDefault(name: String): IO[Boolean]

  /** List all registered backend names. */
  def list: IO[List[String]]

  /** Get statistics for all backends. */
  def allStats: IO[Map[String, CacheStats]]

  /** Clear all backends. */
  def clearAll: IO[Unit]

  /** Unregister a backend. */
  def unregister(name: String): IO[Boolean]
}

/** Default implementation using Cats Effect Ref for thread-safe state. */
private[cache] class CacheRegistryImpl(
    backendsRef: Ref[IO, Map[String, CacheBackend]],
    defaultNameRef: Ref[IO, Option[String]]
) extends CacheRegistry {

  private val fallbackBackend = new InMemoryCacheBackend()

  override def register(name: String, backend: CacheBackend): IO[Unit] =
    for {
      _ <- backendsRef.update(_ + (name -> backend))
      // Set as default if this is the first backend
      _ <- defaultNameRef.update {
        case None    => Some(name)
        case default => default
      }
    } yield ()

  override def get(name: String): IO[Option[CacheBackend]] =
    backendsRef.get.map(_.get(name))

  override def default: IO[CacheBackend] =
    for {
      defaultName <- defaultNameRef.get
      backends <- backendsRef.get
      result = defaultName
        .flatMap(backends.get)
        .getOrElse(fallbackBackend)
    } yield result

  override def setDefault(name: String): IO[Boolean] =
    for {
      backends <- backendsRef.get
      exists = backends.contains(name)
      _ <- if (exists) defaultNameRef.set(Some(name)) else IO.unit
    } yield exists

  override def list: IO[List[String]] =
    backendsRef.get.map(_.keys.toList.sorted)

  override def allStats: IO[Map[String, CacheStats]] =
    for {
      backends <- backendsRef.get
      stats <- backends.toList.traverse { case (name, backend) =>
        backend.stats.map(name -> _)
      }
    } yield stats.toMap

  override def clearAll: IO[Unit] =
    for {
      backends <- backendsRef.get
      _ <- backends.values.toList.traverse_(_.clear)
    } yield ()

  override def unregister(name: String): IO[Boolean] =
    backendsRef.modify { backends =>
      val existed = backends.contains(name)
      (backends - name, existed)
    }
}

object CacheRegistry {

  /** Create an empty cache registry. */
  def create: IO[CacheRegistry] =
    for {
      backendsRef <- Ref.of[IO, Map[String, CacheBackend]](Map.empty)
      defaultRef <- Ref.of[IO, Option[String]](None)
    } yield new CacheRegistryImpl(backendsRef, defaultRef)

  /** Create a registry with pre-configured backends. */
  def withBackends(backends: (String, CacheBackend)*): IO[CacheRegistry] =
    for {
      registry <- create
      _ <- backends.toList.traverse_ { case (name, backend) =>
        registry.register(name, backend)
      }
    } yield registry

  /** Create a registry with a default in-memory backend. */
  def withMemory: IO[CacheRegistry] =
    withBackends("memory" -> InMemoryCacheBackend())

  /** Create a registry with a sized in-memory backend. */
  def withMemory(maxSize: Int): IO[CacheRegistry] =
    withBackends("memory" -> InMemoryCacheBackend.withMaxSize(maxSize))
}
