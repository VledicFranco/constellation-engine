package io.constellation.provider

import io.constellation.CType
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}

/** Factory for creating FunctionSignature from validated module declarations. */
object ExternalFunctionSignature {

  /** Create a FunctionSignature from a validated module declaration.
    *
    * @param name
    *   Short module name (e.g., "analyze")
    * @param namespace
    *   Provider namespace (e.g., "ml.sentiment")
    * @param inputType
    *   Validated CType for module inputs
    * @param outputType
    *   Validated CType for module outputs
    * @return
    *   A FunctionSignature ready for registration in the FunctionRegistry
    */
  def create(
      name: String,
      namespace: String,
      inputType: CType,
      outputType: CType
  ): FunctionSignature = {
    val params = inputType match {
      case CType.CProduct(structure) =>
        structure.toList.map { case (fieldName, fieldType) =>
          fieldName -> SemanticType.fromCType(fieldType)
        }
      case other =>
        List("input" -> SemanticType.fromCType(other))
    }

    val returns = SemanticType.fromCType(outputType)

    FunctionSignature(
      name = name,
      params = params,
      returns = returns,
      moduleName = s"$namespace.$name",
      namespace = Some(namespace)
    )
  }
}
