package io.constellation.impl

import java.util.UUID

import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.{DagSpec, Module, ModuleNodeSpec, ModuleRegistry}

/** Optimized ModuleRegistry implementation with pre-computed name index.
  *
  * ==Performance Optimization==
  *
  * Instead of performing string operations on every lookup, name variants are pre-computed at
  * registration time. This eliminates:
  *   - String splitting on lookups
  *   - Array allocations on lookups
  *   - Secondary map lookups for stripped names
  *
  * ==Name Resolution==
  *
  * Modules can be looked up by:
  *   - Full name (e.g., "mydag.Uppercase")
  *   - Short name (e.g., "Uppercase") if unambiguous
  *
  * When multiple modules share the same short name (e.g., "dag1.Transform" and "dag2.Transform"),
  * the first registered module wins for short name lookup. Full names always work correctly.
  *
  * ==Thread Safety==
  *
  * Uses Cats Effect Ref for thread-safe atomic updates.
  */
class ModuleRegistryImpl(
    modulesRef: Ref[IO, Map[String, Module.Uninitialized]],
    nameIndexRef: Ref[IO, Map[String, String]]
) extends ModuleRegistry {

  override def listModules: IO[List[ModuleNodeSpec]] =
    modulesRef.get.map(_.values.map(_.spec).toList)

  override def register(name: String, node: Module.Uninitialized): IO[Unit] =
    for {
      _ <- modulesRef.update(_ + (name -> node))
      _ <- updateNameIndex(name)
    } yield ()

  /** Register multiple modules efficiently. Useful for batch registration at startup.
    */
  def registerAll(modules: List[(String, Module.Uninitialized)]): IO[Unit] =
    for {
      _ <- modulesRef.update(_ ++ modules.toMap)
      _ <- modules.traverse_ { case (name, _) => updateNameIndex(name) }
    } yield ()

  /** Fast module lookup using pre-computed index. Tries exact match first, then stripped name
    * (without prefix).
    */
  override def get(name: String): IO[Option[Module.Uninitialized]] =
    for {
      index   <- nameIndexRef.get
      modules <- modulesRef.get
      // Try exact match first via index
      result = index.get(name).flatMap(modules.get).orElse {
        // Fallback: try stripped name (query might have prefix not in index)
        if name.contains(".") then {
          val stripped = name.split('.').last
          index.get(stripped).flatMap(modules.get)
        } else None
      }
    } yield result

  override def initModules(dagSpec: DagSpec): IO[Map[UUID, Module.Uninitialized]] =
    for {
      index   <- nameIndexRef.get
      modules <- modulesRef.get
      loaded = dagSpec.modules.toList.flatMap { case (uuid, spec) =>
        // Try exact match first, then stripped name
        val found = index.get(spec.name).flatMap(modules.get).orElse {
          if spec.name.contains(".") then {
            val stripped = spec.name.split('.').last
            index.get(stripped).flatMap(modules.get)
          } else None
        }
        found.map(uuid -> _)
      }.toMap
    } yield loaded

  /** Pre-compute and index all possible name variants. Called once at registration, not at every
    * lookup.
    */
  private def updateNameIndex(canonicalName: String): IO[Unit] =
    nameIndexRef.update { index =>
      // Always index the full name
      val withFullName = index + (canonicalName -> canonicalName)

      // Index short name (without dag prefix) if applicable
      // "mydag.Uppercase" → "Uppercase"
      if canonicalName.contains(".") then {
        val shortName = canonicalName.split('.').last

        withFullName.get(shortName) match {
          case None =>
            // No conflict, add short name
            withFullName + (shortName -> canonicalName)

          case Some(existing) if existing == canonicalName =>
            // Same module, already indexed
            withFullName

          case Some(_) =>
            // Conflict: multiple modules could match this short name
            // First registration wins - don't overwrite
            // Users should use fully qualified names to avoid ambiguity
            withFullName
        }
      } else {
        withFullName
      }
    }

  /** Check if a module is registered (by any name variant). */
  def contains(name: String): IO[Boolean] =
    nameIndexRef.get.map(_.contains(name))

  /** Get number of registered modules. */
  def size: IO[Int] =
    modulesRef.get.map(_.size)

  /** Get number of indexed names (may be > size due to short names). */
  def indexSize: IO[Int] =
    nameIndexRef.get.map(_.size)

  /** Clear all modules (for testing). */
  def clear: IO[Unit] =
    for {
      _ <- modulesRef.set(Map.empty)
      _ <- nameIndexRef.set(Map.empty)
    } yield ()
}

object ModuleRegistryImpl {

  def init: IO[ModuleRegistry] =
    for {
      modulesRef   <- Ref.of[IO, Map[String, Module.Uninitialized]](Map.empty)
      nameIndexRef <- Ref.of[IO, Map[String, String]](Map.empty)
    } yield new ModuleRegistryImpl(modulesRef, nameIndexRef)

  /** Create with pre-populated modules (for testing). */
  def withModules(modules: List[(String, Module.Uninitialized)]): IO[ModuleRegistryImpl] =
    for {
      modulesRef   <- Ref.of[IO, Map[String, Module.Uninitialized]](Map.empty)
      nameIndexRef <- Ref.of[IO, Map[String, String]](Map.empty)
      registry = new ModuleRegistryImpl(modulesRef, nameIndexRef)
      _ <- registry.registerAll(modules)
    } yield registry
}
