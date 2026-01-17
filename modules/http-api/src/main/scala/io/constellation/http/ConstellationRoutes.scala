package io.constellation.http

import cats.effect.IO
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityCodec._
import io.constellation.{Constellation, CValue}
import io.constellation.http.ApiModels._
import io.constellation.lang.LangCompiler
import io.circe.syntax._
import io.circe.Json

/** HTTP routes for the Constellation Engine API */
class ConstellationRoutes(
  constellation: Constellation,
  compiler: LangCompiler
) {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Compile constellation-lang source code
    case req @ POST -> Root / "compile" =>
      for {
        compileReq <- req.as[CompileRequest]
        result = compiler.compile(compileReq.source, compileReq.dagName)
        response <- result match {
          case Right(compiled) =>
            constellation.setDag(compileReq.dagName, compiled.dagSpec).flatMap { _ =>
              Ok(CompileResponse(
                success = true,
                dagName = Some(compileReq.dagName)
              ))
            }
          case Left(errors) =>
            BadRequest(CompileResponse(
              success = false,
              errors = errors.map(_.message)
            ))
        }
      } yield response

    // Execute a DAG with inputs
    case req @ POST -> Root / "execute" =>
      (for {
        execReq <- req.as[ExecuteRequest]
        dagOpt <- constellation.getDag(execReq.dagName)

        response <- dagOpt match {
          case Some(dagSpec) =>
            for {
              // Convert inputs: JSON → CValue
              convertResult <- ExecutionHelper.convertInputs(execReq.inputs, dagSpec).attempt
              response <- convertResult match {
                case Left(error) =>
                  BadRequest(ExecuteResponse(
                    success = false,
                    error = Some(s"Input error: ${error.getMessage}")
                  ))

                case Right(cValueInputs) =>
                  for {
                    // Execute DAG
                    execResult <- constellation.runDag(execReq.dagName, cValueInputs).attempt
                    response <- execResult match {
                      case Left(error) =>
                        InternalServerError(ExecuteResponse(
                          success = false,
                          error = Some(s"Execution failed: ${error.getMessage}")
                        ))

                      case Right(state) =>
                        for {
                          // Extract outputs: CValue → JSON
                          outputResult <- ExecutionHelper.extractOutputs(state).attempt
                          response <- outputResult match {
                            case Left(error) =>
                              InternalServerError(ExecuteResponse(
                                success = false,
                                error = Some(s"Output error: ${error.getMessage}")
                              ))

                            case Right(outputs) =>
                              Ok(ExecuteResponse(
                                success = true,
                                outputs = outputs,
                                error = None
                              ))
                          }
                        } yield response
                    }
                  } yield response
              }
            } yield response

          case None =>
            NotFound(ErrorResponse(
              error = "DagNotFound",
              message = s"DAG '${execReq.dagName}' not found"
            ))
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(ExecuteResponse(
          success = false,
          error = Some(s"Unexpected error: ${error.getMessage}")
        ))
      }

    // List all available DAGs
    case GET -> Root / "dags" =>
      for {
        dags <- constellation.listDags
        response <- Ok(DagListResponse(dags))
      } yield response

    // Get a specific DAG by name
    case GET -> Root / "dags" / dagName =>
      for {
        dagOpt <- constellation.getDag(dagName)
        response <- dagOpt match {
          case Some(dagSpec) =>
            Ok(DagResponse(
              name = dagName,
              metadata = dagSpec.metadata
            ))
          case None =>
            NotFound(ErrorResponse(
              error = "DagNotFound",
              message = s"DAG '$dagName' not found"
            ))
        }
      } yield response

    // List all available modules
    case GET -> Root / "modules" =>
      for {
        modules <- constellation.getModules
        moduleInfos = modules.map { spec =>
          ModuleInfo(
            name = spec.name,
            description = spec.description,
            version = s"${spec.majorVersion}.${spec.minorVersion}",
            inputs = spec.consumes.map { case (k, v) => k -> v.toString },
            outputs = spec.produces.map { case (k, v) => k -> v.toString }
          )
        }
        response <- Ok(ModuleListResponse(moduleInfos))
      } yield response

    // Health check endpoint
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))
  }
}

object ConstellationRoutes {
  def apply(constellation: Constellation, compiler: LangCompiler): ConstellationRoutes =
    new ConstellationRoutes(constellation, compiler)
}
