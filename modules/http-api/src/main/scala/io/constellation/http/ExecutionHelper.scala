package io.constellation.http

import cats.effect.IO
import cats.implicits._
import io.circe.Json
import io.constellation.{CValue, DagSpec, JsonCValueConverter, Runtime}

/** Helper functions for converting between JSON and CValue when executing DAGs.
  *
  * Handles:
  * - Converting JSON inputs to CValue inputs using DAG input schema
  * - Extracting CValue outputs from Runtime.State and converting to JSON
  */
object ExecutionHelper {

  /** Convert JSON inputs to CValue inputs using the DAG's input schema.
    *
    * Maps input names from topLevelDataNodes to provided JSON values,
    * then converts each JSON value to CValue using the expected type.
    *
    * @param inputs Map of input names to JSON values
    * @param dagSpec The DAG specification containing input schema
    * @return IO containing Map of input names to CValue, or raises error if conversion fails
    */
  def convertInputs(
    inputs: Map[String, Json],
    dagSpec: DagSpec
  ): IO[Map[String, CValue]] = {
    dagSpec.topLevelDataNodes.toList.traverse { case (uuid, dataSpec) =>
      // Get input name from nicknames (use first nickname as the canonical name)
      val inputName = dataSpec.nicknames.values.headOption.getOrElse(dataSpec.name)

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
    }.map(_.toMap)
  }

  /** Extract outputs from Runtime.State and convert to JSON.
    *
    * Identifies output data nodes using bottomLevelDataNodes,
    * retrieves their CValue from the state, and converts to JSON.
    *
    * @param state The runtime state after DAG execution
    * @return IO containing Map of output names to JSON values
    */
  def extractOutputs(state: Runtime.State): IO[Map[String, Json]] = {
    state.dag.bottomLevelDataNodes.toList.traverse { case (uuid, dataSpec) =>
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
          IO.raiseError(new RuntimeException(
            s"Output data node '${dataSpec.name}' (UUID: $uuid) not found in runtime state. " +
            "This indicates an incomplete DAG execution."
          ))
      }
    }.map(_.toMap)
  }
}
