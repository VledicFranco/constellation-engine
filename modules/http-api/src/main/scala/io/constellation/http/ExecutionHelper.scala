package io.constellation.http

import cats.effect.IO
import cats.implicits.*

import io.constellation.{CType, CValue, DagSpec, JsonCValueConverter, Runtime}

import io.circe.Json

/** Helper functions for converting between JSON and CValue when executing DAGs.
  *
  * Handles:
  *   - Converting JSON inputs to CValue inputs using DAG input schema
  *   - Extracting CValue outputs from Runtime.State and converting to JSON
  */
object ExecutionHelper {

  /** Convert JSON inputs to CValue inputs using the DAG's input schema.
    *
    * Maps input names from topLevelDataNodes to provided JSON values, then converts each JSON value
    * to CValue using the expected type.
    *
    * @param inputs
    *   Map of input names to JSON values
    * @param dagSpec
    *   The DAG specification containing input schema
    * @return
    *   IO containing Map of input names to CValue, or raises error if conversion fails
    */
  def convertInputs(
      inputs: Map[String, Json],
      dagSpec: DagSpec
  ): IO[Map[String, CValue]] =
    dagSpec.userInputDataNodes.toList
      .traverse { case (uuid, dataSpec) =>
        // Use the data node's name as the canonical input name
        // Note: nicknames may contain module parameter names (like "a", "b") which should not be used
        val inputName = dataSpec.name

        // Find matching JSON input
        inputs.get(inputName) match {
          case Some(json) =>
            // Convert with type info
            JsonCValueConverter.jsonToCValue(json, dataSpec.cType, inputName) match {
              case Right(cValue) =>
                IO.pure(inputName -> cValue)
              case Left(error) =>
                IO.raiseError(new RuntimeException(s"Input '$inputName': $error"))
            }
          case None =>
            IO.raiseError(new RuntimeException(s"Missing required input: '$inputName'"))
        }
      }
      .map(_.toMap)

  /** Convert JSON inputs to CValue inputs leniently — skip missing inputs instead of failing.
    *
    * Present inputs are converted using the expected type (type mismatches still raise errors).
    * Missing inputs are silently skipped so the runtime can produce a Suspended status.
    *
    * @param inputs
    *   Map of input names to JSON values
    * @param dagSpec
    *   The DAG specification containing input schema
    * @return
    *   IO containing Map of provided input names to CValue
    */
  def convertInputsLenient(
      inputs: Map[String, Json],
      dagSpec: DagSpec
  ): IO[Map[String, CValue]] =
    dagSpec.userInputDataNodes.toList
      .traverse { case (uuid, dataSpec) =>
        val inputName = dataSpec.name
        inputs.get(inputName) match {
          case Some(json) =>
            JsonCValueConverter.jsonToCValue(json, dataSpec.cType, inputName) match {
              case Right(cValue) =>
                IO.pure(Some(inputName -> cValue))
              case Left(error) =>
                IO.raiseError(new RuntimeException(s"Input '$inputName': $error"))
            }
          case None =>
            IO.pure(None)
        }
      }
      .map(_.flatten.toMap)

  /** Build a map of missing input names to their expected type strings.
    *
    * Cross-references the provided input names against `dagSpec.userInputDataNodes` to identify
    * which inputs were not provided, and returns their names mapped to `cType.toString`.
    *
    * @param providedInputNames
    *   Names of inputs that were actually provided
    * @param dagSpec
    *   The DAG specification containing input schema
    * @return
    *   Map of missing input names to type strings (e.g. "CInt", "CString")
    */
  def buildMissingInputsMap(
      providedInputNames: Set[String],
      dagSpec: DagSpec
  ): Map[String, String] =
    dagSpec.userInputDataNodes.values
      .filterNot(spec => providedInputNames.contains(spec.name))
      .map(spec => spec.name -> spec.cType.toString)
      .toMap

  /** Extract outputs from Runtime.State and convert to JSON.
    *
    * If the DAG has explicit output declarations (declaredOutputs), only those outputs are
    * returned. Otherwise, falls back to returning all bottom-level data nodes (legacy behavior).
    *
    * @param state
    *   The runtime state after DAG execution
    * @return
    *   IO containing Map of output names to JSON values
    */
  def extractOutputs(state: Runtime.State): IO[Map[String, Json]] = {
    val declaredOutputs = state.dag.declaredOutputs

    if declaredOutputs.nonEmpty then {
      // New behavior: filter to only declared outputs
      extractDeclaredOutputs(state, declaredOutputs)
    } else {
      // Legacy behavior: return all bottom-level data nodes
      extractAllOutputs(state)
    }
  }

  /** Extract only the declared outputs from the runtime state */
  private def extractDeclaredOutputs(
      state: Runtime.State,
      declaredOutputs: List[String]
  ): IO[Map[String, Json]] = {
    val outputBindings = state.dag.outputBindings

    // Use outputBindings to look up data by UUID instead of by name
    declaredOutputs
      .traverse { outputName =>
        outputBindings.get(outputName) match {
          case Some(dataNodeUuid) =>
            state.data.get(dataNodeUuid) match {
              case Some(evalCvalue) =>
                val json = JsonCValueConverter.cValueToJson(evalCvalue.value)
                IO.pure(outputName -> json)
              case None =>
                IO.raiseError(
                  new RuntimeException(
                    s"Data node for output '$outputName' (UUID: $dataNodeUuid) not found in runtime state."
                  )
                )
            }
          case None =>
            IO.raiseError(
              new RuntimeException(
                s"Output binding for '$outputName' not found. Available bindings: ${outputBindings.keys
                    .mkString(", ")}"
              )
            )
        }
      }
      .map(_.toMap)
  }

  /** Extract all bottom-level data nodes (legacy behavior) */
  private def extractAllOutputs(state: Runtime.State): IO[Map[String, Json]] =
    state.dag.bottomLevelDataNodes.toList
      .traverse { case (uuid, dataSpec) =>
        // Get CValue from state
        state.data.get(uuid) match {
          case Some(evalCValue) =>
            val cValue = evalCValue.value

            // Convert to JSON
            val json = JsonCValueConverter.cValueToJson(cValue)

            // Use data node name as key (or first nickname if available)
            val outputName = dataSpec.nicknames.values.headOption.getOrElse(dataSpec.name)
            IO.pure(outputName -> json)

          case None =>
            IO.raiseError(
              new RuntimeException(
                s"Output data node '${dataSpec.name}' (UUID: $uuid) not found in runtime state. " +
                  "This indicates an incomplete DAG execution."
              )
            )
        }
      }
      .map(_.toMap)
}
