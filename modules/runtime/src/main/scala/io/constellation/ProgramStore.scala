package io.constellation

import cats.effect.IO

/** Persistent store for compiled program images.
  *
  * Supports storage, retrieval, aliasing (human-readable names), and a syntactic index that maps
  * (syntacticHash, registryHash) pairs to structural hashes for cache-hit detection.
  */
trait ProgramStore {

  /** Store a program image. Returns the structural hash as the key. */
  def store(image: ProgramImage): IO[String]

  /** Create or update a human-readable alias for a structural hash. */
  def alias(name: String, structuralHash: String): IO[Unit]

  /** Resolve a human-readable alias to a structural hash. */
  def resolve(name: String): IO[Option[String]]

  /** List all known aliases. */
  def listAliases: IO[Map[String, String]]

  /** Retrieve a program image by structural hash. */
  def get(structuralHash: String): IO[Option[ProgramImage]]

  /** Retrieve a program image by alias name. */
  def getByName(name: String): IO[Option[ProgramImage]]

  /** Index a syntactic hash to a structural hash for cache lookups.
    *
    * @param syntacticHash
    *   Hash of the source text
    * @param registryHash
    *   Hash of the function registry at compile time
    * @param structuralHash
    *   The structural hash of the resulting program
    */
  def indexSyntactic(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]

  /** Look up a structural hash by syntactic hash and registry hash. */
  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]]

  /** List all stored program images. */
  def listImages: IO[List[ProgramImage]]

  /** Remove a program image by structural hash. Returns true if found. */
  def remove(structuralHash: String): IO[Boolean]
}
