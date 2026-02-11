package io.constellation.provider

import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.provider.v1.{provider => pb}

/** Validation result for a single module declaration. */
sealed trait ModuleValidationResult
object ModuleValidationResult {
  final case class Accepted(qualifiedName: String) extends ModuleValidationResult
  final case class Rejected(moduleName: String, reason: String) extends ModuleValidationResult
}

/** Validates a RegisterRequest against current registry state and ownership rules. */
object SchemaValidator {

  /** Validate a registration request.
    *
    * @param request
    *   The incoming registration request
    * @param functionRegistry
    *   Current compile-time function registry (for name conflict detection)
    * @param namespaceOwners
    *   Map of namespace -> active provider connection ID
    * @param connectionId
    *   The connection ID of the requesting provider
    * @param reservedNamespaces
    *   Set of reserved namespace prefixes (e.g., "stdlib")
    * @return
    *   Per-module validation results
    */
  def validate(
      request: pb.RegisterRequest,
      functionRegistry: FunctionRegistry,
      namespaceOwners: Map[String, String],
      connectionId: String,
      reservedNamespaces: Set[String]
  ): List[ModuleValidationResult] = {
    val namespace = request.namespace

    // Validate namespace syntax
    validateNamespace(namespace) match {
      case Some(error) =>
        // Reject all modules if namespace is invalid
        request.modules.toList.map(m => ModuleValidationResult.Rejected(m.name, error))

      case None =>
        // Validate each module
        request.modules.toList.map { decl =>
          validateModule(decl, namespace, functionRegistry, namespaceOwners, connectionId,
            reservedNamespaces)
        }
    }
  }

  /** Validate that a namespace is a valid constellation-lang namespace. */
  def validateNamespace(namespace: String): Option[String] =
    if namespace.isEmpty then Some("Namespace cannot be empty")
    else {
      val parts = namespace.split('.')
      val invalidPart = parts.find(part =>
        part.isEmpty || !part.head.isLetter || !part.forall(c => c.isLetterOrDigit || c == '_')
      )
      invalidPart.map(p =>
        s"Invalid namespace part '$p': must start with a letter and contain only alphanumeric characters and underscores"
      )
    }

  private def validateModule(
      decl: pb.ModuleDeclaration,
      namespace: String,
      functionRegistry: FunctionRegistry,
      namespaceOwners: Map[String, String],
      connectionId: String,
      reservedNamespaces: Set[String]
  ): ModuleValidationResult = {
    val qualifiedName = s"$namespace.${decl.name}"

    // Check reserved namespaces
    val isReserved = reservedNamespaces.exists(reserved =>
      namespace == reserved || namespace.startsWith(s"$reserved.")
    )
    if isReserved then
      return ModuleValidationResult.Rejected(decl.name, s"Namespace '$namespace' is reserved")

    // Check module name validity
    if decl.name.isEmpty || !decl.name.head.isLetter then
      return ModuleValidationResult.Rejected(decl.name, "Module name must start with a letter")

    // Check name conflicts
    functionRegistry.lookupQualified(qualifiedName) match {
      case Some(existing) =>
        // Same namespace — check ownership
        namespaceOwners.get(namespace) match {
          case Some(owner) if owner == connectionId =>
            // Same provider — treat as replace (upgrade)
            ()
          case Some(_) =>
            // Different provider owns this namespace
            return ModuleValidationResult.Rejected(
              decl.name,
              s"Namespace '$namespace' is owned by another provider"
            )
          case None =>
            // No owner tracked yet — shouldn't happen if existing function has this namespace
            // Treat as conflict
            return ModuleValidationResult.Rejected(
              decl.name,
              s"Module '$qualifiedName' already exists"
            )
        }
      case None =>
        // No conflict — check if another provider owns the namespace
        namespaceOwners.get(namespace) match {
          case Some(owner) if owner != connectionId =>
            return ModuleValidationResult.Rejected(
              decl.name,
              s"Namespace '$namespace' is owned by another provider"
            )
          case _ => ()
        }
    }

    // Validate type schemas
    val inputResult = decl.inputSchema match {
      case Some(schema) => TypeSchemaConverter.toCType(schema)
      case None         => Left("Missing input_schema")
    }
    val outputResult = decl.outputSchema match {
      case Some(schema) => TypeSchemaConverter.toCType(schema)
      case None         => Left("Missing output_schema")
    }

    (inputResult, outputResult) match {
      case (Left(err), _) =>
        ModuleValidationResult.Rejected(decl.name, s"Invalid input schema: $err")
      case (_, Left(err)) =>
        ModuleValidationResult.Rejected(decl.name, s"Invalid output schema: $err")
      case (Right(_), Right(_)) =>
        ModuleValidationResult.Accepted(qualifiedName)
    }
  }
}
