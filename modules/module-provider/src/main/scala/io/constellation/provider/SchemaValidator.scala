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

    // Validate executor URL first
    validateExecutorUrl(request.executorUrl) match {
      case Some(error) =>
        request.modules.toList.map(m => ModuleValidationResult.Rejected(m.name, error))

      case None =>
        // Validate namespace syntax
        validateNamespace(namespace) match {
          case Some(error) =>
            request.modules.toList.map(m => ModuleValidationResult.Rejected(m.name, error))

          case None =>
            request.modules.toList.map { decl =>
              validateModule(decl, namespace, functionRegistry, namespaceOwners, connectionId,
                reservedNamespaces)
            }
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

  /** Validate that an executor URL is a valid host:port format. */
  def validateExecutorUrl(url: String): Option[String] = {
    val trimmed = url.trim
    if trimmed.isEmpty then Some("executor_url cannot be empty")
    else if trimmed.contains("://") then
      Some(s"executor_url must be host:port format, not a URL with scheme: $trimmed")
    else {
      val lastColon = trimmed.lastIndexOf(':')
      if lastColon > 0 then {
        val portStr = trimmed.substring(lastColon + 1)
        portStr.toIntOption match {
          case Some(port) if port > 0 && port <= 65535 => None
          case Some(_) => Some(s"Port out of range in executor_url: $trimmed")
          case None    => Some(s"Invalid port in executor_url: $trimmed")
        }
      }
      else None // Just a hostname, port defaults to 9090
    }
  }

  /** Validate that a module name contains only valid characters. */
  private[provider] def validateModuleName(name: String): Option[String] =
    if name.isEmpty then Some("Module name cannot be empty")
    else if !name.head.isLetter then Some("Module name must start with a letter")
    else if !name.forall(c => c.isLetterOrDigit || c == '_') then
      Some("Module name must contain only alphanumeric characters and underscores")
    else None

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
      ModuleValidationResult.Rejected(decl.name, s"Namespace '$namespace' is reserved")
    else {
      // Check module name validity
      validateModuleName(decl.name) match {
        case Some(error) =>
          ModuleValidationResult.Rejected(decl.name, error)

        case None =>
          // Check name conflicts
          val namespaceConflict = functionRegistry.lookupQualified(qualifiedName) match {
            case Some(_) =>
              namespaceOwners.get(namespace) match {
                case Some(owner) if owner == connectionId => None // Same provider — allow upgrade
                case Some(_) => Some(s"Namespace '$namespace' is owned by another provider")
                case None    => Some(s"Module '$qualifiedName' already exists")
              }
            case None =>
              namespaceOwners.get(namespace) match {
                case Some(owner) if owner != connectionId =>
                  Some(s"Namespace '$namespace' is owned by another provider")
                case _ => None
              }
          }

          namespaceConflict match {
            case Some(reason) =>
              ModuleValidationResult.Rejected(decl.name, reason)

            case None =>
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
    }
  }
}
