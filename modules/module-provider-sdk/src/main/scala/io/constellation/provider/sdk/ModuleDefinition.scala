package io.constellation.provider.sdk

import cats.effect.IO

import io.constellation.provider.TypeSchemaConverter
import io.constellation.provider.v1.provider as pb
import io.constellation.{CType, CValue}

/** Describes a module that a provider offers for execution.
  *
  * @param name
  *   Short module name (e.g., "analyze")
  * @param inputType
  *   CType for the module's input
  * @param outputType
  *   CType for the module's output
  * @param version
  *   Semantic version string (informational)
  * @param description
  *   Human-readable description
  * @param handler
  *   The function that executes this module
  */
final case class ModuleDefinition(
    name: String,
    inputType: CType,
    outputType: CType,
    version: String,
    description: String,
    handler: CValue => IO[CValue]
) {

  /** Convert to a protobuf ModuleDeclaration for registration. */
  def toDeclaration: pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(TypeSchemaConverter.toTypeSchema(inputType)),
      outputSchema = Some(TypeSchemaConverter.toTypeSchema(outputType)),
      version = version,
      description = description
    )

  /** Produce the fully qualified module name within a namespace. */
  def qualifiedName(namespace: String): String = s"$namespace.$name"
}
