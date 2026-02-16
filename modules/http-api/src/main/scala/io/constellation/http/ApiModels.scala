package io.constellation.http

import io.constellation.json.given
import io.constellation.{CValue, ComponentMetadata}

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}

/** API request and response models for the HTTP server */
object ApiModels {

  // Explicit encoders/decoders for ComponentMetadata
  given Encoder[ComponentMetadata] = deriveEncoder
  given Decoder[ComponentMetadata] = deriveDecoder

  /** Request to compile constellation-lang source code.
    *
    * Accepts either `name` (new API) or `dagName` (legacy, deprecated). If both are present, `name`
    * takes precedence. At least one must be provided.
    */
  case class CompileRequest(
      source: String,
      name: Option[String] = None,
      dagName: Option[String] = None
  ) {

    /** Resolve the effective pipeline name (new `name` field takes priority). */
    def effectiveName: Option[String] = name.orElse(dagName)
  }

  object CompileRequest {
    given Encoder[CompileRequest] = deriveEncoder
    given Decoder[CompileRequest] = Decoder.instance { c =>
      for {
        source  <- c.downField("source").as[String]
        name    <- c.downField("name").as[Option[String]]
        dagName <- c.downField("dagName").as[Option[String]]
      } yield CompileRequest(source, name, dagName)
    }
  }

  /** Response from compilation.
    *
    * Now includes `structuralHash` and `syntacticHash` for content-addressed lookups. The `dagName`
    * field is retained for backward compatibility.
    */
  case class CompileResponse(
      success: Boolean,
      structuralHash: Option[String] = None,
      syntacticHash: Option[String] = None,
      dagName: Option[String] = None,
      name: Option[String] = None,
      errors: List[String] = List.empty
  )

  object CompileResponse {
    given Encoder[CompileResponse] = deriveEncoder
    given Decoder[CompileResponse] = deriveDecoder
  }

  /** Request to execute a pipeline.
    *
    * Accepts `ref` (new API: name or "sha256:<hash>") or `dagName` (legacy). If both are present,
    * `ref` takes precedence.
    */
  case class ExecuteRequest(
      ref: Option[String] = None,
      dagName: Option[String] = None,
      inputs: Map[String, Json] = Map.empty
  ) {

    /** Resolve the effective reference (new `ref` field takes priority). */
    def effectiveRef: Option[String] = ref.orElse(dagName)
  }

  object ExecuteRequest {
    given Encoder[ExecuteRequest] = deriveEncoder
    given Decoder[ExecuteRequest] = Decoder.instance { c =>
      for {
        ref     <- c.downField("ref").as[Option[String]]
        dagName <- c.downField("dagName").as[Option[String]]
        inputs  <- c.downField("inputs").as[Map[String, Json]]
      } yield ExecuteRequest(ref, dagName, inputs)
    }
  }

  /** Response from DAG execution.
    *
    * When suspension-aware fields are populated: `status` is "completed", "suspended", or "failed";
    * `executionId` is a UUID string; `missingInputs` maps missing input names to type strings;
    * `pendingOutputs` lists outputs that were not computed; `resumptionCount` tracks resume count.
    * All new fields default to None for backward compatibility.
    */
  case class ExecuteResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      error: Option[String] = None,
      status: Option[String] = None,
      executionId: Option[String] = None,
      missingInputs: Option[Map[String, String]] = None,
      pendingOutputs: Option[List[String]] = None,
      resumptionCount: Option[Int] = None
  )

  object ExecuteResponse {
    given Encoder[ExecuteResponse] = deriveEncoder
    given Decoder[ExecuteResponse] = deriveDecoder
  }

  /** Request to compile and run a script in one step */
  case class RunRequest(
      source: String,
      inputs: Map[String, Json]
  )

  object RunRequest {
    given Encoder[RunRequest] = deriveEncoder
    given Decoder[RunRequest] = deriveDecoder
  }

  /** Response from compile-and-run.
    *
    * Now includes `structuralHash` so callers can reference the pipeline by hash. Suspension-aware
    * fields mirror those on [[ExecuteResponse]].
    */
  case class RunResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      structuralHash: Option[String] = None,
      compilationErrors: List[String] = List.empty,
      error: Option[String] = None,
      status: Option[String] = None,
      executionId: Option[String] = None,
      missingInputs: Option[Map[String, String]] = None,
      pendingOutputs: Option[List[String]] = None,
      resumptionCount: Option[Int] = None
  )

  object RunResponse {
    given Encoder[RunResponse] = deriveEncoder
    given Decoder[RunResponse] = deriveDecoder
  }

  /** Response listing available modules */
  case class ModuleListResponse(
      modules: List[ModuleInfo]
  )

  object ModuleListResponse {
    given Encoder[ModuleListResponse] = deriveEncoder
    given Decoder[ModuleListResponse] = deriveDecoder
  }

  /** Module information */
  case class ModuleInfo(
      name: String,
      description: String,
      version: String,
      inputs: Map[String, String],
      outputs: Map[String, String]
  )

  object ModuleInfo {
    given Encoder[ModuleInfo] = deriveEncoder
    given Decoder[ModuleInfo] = deriveDecoder
  }

  /** Error response with optional request ID for tracing */
  case class ErrorResponse(
      error: String,
      message: String,
      requestId: Option[String] = None
  )

  object ErrorResponse {
    given Encoder[ErrorResponse] = deriveEncoder
    given Decoder[ErrorResponse] = deriveDecoder
  }

  /** Response listing available namespaces */
  case class NamespaceListResponse(
      namespaces: List[String]
  )

  object NamespaceListResponse {
    given Encoder[NamespaceListResponse] = deriveEncoder
    given Decoder[NamespaceListResponse] = deriveDecoder
  }

  /** Function information for namespace listing */
  case class FunctionInfo(
      name: String,
      qualifiedName: String,
      params: List[String],
      returns: String
  )

  object FunctionInfo {
    given Encoder[FunctionInfo] = deriveEncoder
    given Decoder[FunctionInfo] = deriveDecoder
  }

  /** Response listing functions in a namespace */
  case class NamespaceFunctionsResponse(
      namespace: String,
      functions: List[FunctionInfo]
  )

  object NamespaceFunctionsResponse {
    given Encoder[NamespaceFunctionsResponse] = deriveEncoder
    given Decoder[NamespaceFunctionsResponse] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Module HTTP endpoint models (RFC-027)
  // ---------------------------------------------------------------------------

  /** Response from invoking a published module directly via HTTP.
    *
    * @param success
    *   Whether the invocation succeeded
    * @param outputs
    *   Module output values as JSON (keyed by output field name)
    * @param error
    *   Error message if invocation failed
    * @param module
    *   Name of the invoked module
    */
  case class ModuleInvokeResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      error: Option[String] = None,
      module: Option[String] = None
  )

  object ModuleInvokeResponse {
    given Encoder[ModuleInvokeResponse] = deriveEncoder
    given Decoder[ModuleInvokeResponse] = deriveDecoder
  }

  /** Information about a published module endpoint for discovery. */
  case class PublishedModuleInfo(
      name: String,
      description: String,
      version: String,
      tags: List[String],
      endpoint: String,
      inputs: Map[String, String],
      outputs: Map[String, String]
  )

  object PublishedModuleInfo {
    given Encoder[PublishedModuleInfo] = deriveEncoder
    given Decoder[PublishedModuleInfo] = deriveDecoder
  }

  /** Response listing all published module endpoints. */
  case class PublishedModulesResponse(
      modules: List[PublishedModuleInfo]
  )

  object PublishedModulesResponse {
    given Encoder[PublishedModulesResponse] = deriveEncoder
    given Decoder[PublishedModulesResponse] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Pipeline management models (Phase 5)
  // ---------------------------------------------------------------------------

  /** Summary of a stored pipeline image */
  case class PipelineSummary(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      moduleCount: Int,
      declaredOutputs: List[String]
  )

  object PipelineSummary {
    given Encoder[PipelineSummary] = deriveEncoder
    given Decoder[PipelineSummary] = deriveDecoder
  }

  /** Response listing stored pipelines */
  case class PipelineListResponse(
      pipelines: List[PipelineSummary]
  )

  object PipelineListResponse {
    given Encoder[PipelineListResponse] = deriveEncoder
    given Decoder[PipelineListResponse] = deriveDecoder
  }

  /** Detailed pipeline metadata response */
  case class PipelineDetailResponse(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      modules: List[ModuleInfo],
      declaredOutputs: List[String],
      inputSchema: Map[String, String],
      outputSchema: Map[String, String]
  )

  object PipelineDetailResponse {
    given Encoder[PipelineDetailResponse] = deriveEncoder
    given Decoder[PipelineDetailResponse] = deriveDecoder
  }

  /** Request to update an alias target */
  case class AliasRequest(
      structuralHash: String
  )

  object AliasRequest {
    given Encoder[AliasRequest] = deriveEncoder
    given Decoder[AliasRequest] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Suspension management models (Phase 2)
  // ---------------------------------------------------------------------------

  /** Request to resume a suspended execution.
    *
    * @param additionalInputs
    *   New input values to provide (keyed by variable name, JSON-encoded)
    * @param resolvedNodes
    *   Manually-resolved data node values (keyed by variable name, JSON-encoded)
    */
  case class ResumeRequest(
      additionalInputs: Option[Map[String, Json]] = None,
      resolvedNodes: Option[Map[String, Json]] = None
  )

  object ResumeRequest {
    given Encoder[ResumeRequest] = deriveEncoder
    given Decoder[ResumeRequest] = deriveDecoder
  }

  /** Summary of a suspended execution for listing.
    *
    * @param executionId
    *   UUID string of the execution
    * @param structuralHash
    *   Pipeline structural hash
    * @param resumptionCount
    *   How many times this execution has been resumed
    * @param missingInputs
    *   Map of missing input names to type strings (e.g. "CInt", "CString")
    * @param createdAt
    *   ISO-8601 timestamp when the suspension was created
    */
  case class ExecutionSummary(
      executionId: String,
      structuralHash: String,
      resumptionCount: Int,
      missingInputs: Map[String, String],
      createdAt: String
  )

  object ExecutionSummary {
    given Encoder[ExecutionSummary] = deriveEncoder
    given Decoder[ExecutionSummary] = deriveDecoder
  }

  /** Response listing suspended executions. */
  case class ExecutionListResponse(
      executions: List[ExecutionSummary]
  )

  object ExecutionListResponse {
    given Encoder[ExecutionListResponse] = deriveEncoder
    given Decoder[ExecutionListResponse] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Pipeline versioning models (Phase 4a)
  // ---------------------------------------------------------------------------

  /** Request to reload a named pipeline.
    *
    * If `source` is provided, the pipeline is recompiled from that source. If omitted, the server
    * attempts to re-read the original file (if a file path is known from startup loading).
    *
    * If `canary` is provided and the reload produces a changed pipeline, a canary deployment is
    * started to gradually shift traffic from the old version to the new one.
    */
  case class ReloadRequest(
      source: Option[String] = None,
      canary: Option[CanaryConfigRequest] = None
  )

  object ReloadRequest {
    given Encoder[ReloadRequest] = deriveEncoder
    given Decoder[ReloadRequest] = deriveDecoder
  }

  /** Response from a pipeline reload operation. */
  case class ReloadResponse(
      success: Boolean,
      previousHash: Option[String],
      newHash: String,
      name: String,
      changed: Boolean,
      version: Int,
      canary: Option[CanaryStateResponse] = None
  )

  object ReloadResponse {
    given Encoder[ReloadResponse] = deriveEncoder
    given Decoder[ReloadResponse] = deriveDecoder
  }

  /** Version info entry for a single pipeline version. */
  case class PipelineVersionInfo(
      version: Int,
      structuralHash: String,
      createdAt: String,
      active: Boolean
  )

  object PipelineVersionInfo {
    given Encoder[PipelineVersionInfo] = deriveEncoder
    given Decoder[PipelineVersionInfo] = deriveDecoder
  }

  /** Response listing all versions of a named pipeline. */
  case class PipelineVersionsResponse(
      name: String,
      versions: List[PipelineVersionInfo],
      activeVersion: Int
  )

  object PipelineVersionsResponse {
    given Encoder[PipelineVersionsResponse] = deriveEncoder
    given Decoder[PipelineVersionsResponse] = deriveDecoder
  }

  /** Response from a pipeline rollback operation. */
  case class RollbackResponse(
      success: Boolean,
      name: String,
      previousVersion: Int,
      activeVersion: Int,
      structuralHash: String
  )

  object RollbackResponse {
    given Encoder[RollbackResponse] = deriveEncoder
    given Decoder[RollbackResponse] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Canary release models (Phase 4b)
  // ---------------------------------------------------------------------------

  /** Canary configuration for a reload request.
    *
    * All fields are optional — omitted fields use the defaults from [[CanaryConfig]].
    * `observationWindow` is a duration string like "5m", "30s", "1h".
    */
  case class CanaryConfigRequest(
      initialWeight: Option[Double] = None,
      promotionSteps: Option[List[Double]] = None,
      observationWindow: Option[String] = None,
      errorThreshold: Option[Double] = None,
      latencyThresholdMs: Option[Long] = None,
      minRequests: Option[Int] = None,
      autoPromote: Option[Boolean] = None
  )

  object CanaryConfigRequest {
    given Encoder[CanaryConfigRequest] = deriveEncoder
    given Decoder[CanaryConfigRequest] = deriveDecoder
  }

  /** Version info within a canary state response. */
  case class CanaryVersionInfo(
      version: Int,
      structuralHash: String
  )

  object CanaryVersionInfo {
    given Encoder[CanaryVersionInfo] = deriveEncoder
    given Decoder[CanaryVersionInfo] = deriveDecoder
  }

  /** Per-version metrics in a canary state response. */
  case class VersionMetricsResponse(
      requests: Long,
      successes: Long,
      failures: Long,
      avgLatencyMs: Double,
      p99LatencyMs: Double
  )

  object VersionMetricsResponse {
    given Encoder[VersionMetricsResponse] = deriveEncoder
    given Decoder[VersionMetricsResponse] = deriveDecoder
  }

  /** Combined metrics for old and new versions. */
  case class CanaryMetricsResponse(
      oldVersion: VersionMetricsResponse,
      newVersion: VersionMetricsResponse
  )

  object CanaryMetricsResponse {
    given Encoder[CanaryMetricsResponse] = deriveEncoder
    given Decoder[CanaryMetricsResponse] = deriveDecoder
  }

  /** API response representing the current state of a canary deployment. */
  case class CanaryStateResponse(
      pipelineName: String,
      oldVersion: CanaryVersionInfo,
      newVersion: CanaryVersionInfo,
      currentWeight: Double,
      currentStep: Int,
      status: String,
      startedAt: String,
      metrics: CanaryMetricsResponse
  )

  object CanaryStateResponse {
    given Encoder[CanaryStateResponse] = deriveEncoder
    given Decoder[CanaryStateResponse] = deriveDecoder
  }
}
