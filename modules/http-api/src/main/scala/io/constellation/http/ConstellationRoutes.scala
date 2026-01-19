package io.constellation.http

import cats.effect.IO
import cats.implicits.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import io.constellation.{CValue, Constellation}
import io.constellation.http.ApiModels.*
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.circe.syntax.*
import io.circe.Json

/** HTTP routes for the Constellation Engine API */
class ConstellationRoutes(
    constellation: Constellation,
    compiler: LangCompiler,
    functionRegistry: FunctionRegistry
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
              Ok(
                CompileResponse(
                  success = true,
                  dagName = Some(compileReq.dagName)
                )
              )
            }
          case Left(errors) =>
            BadRequest(
              CompileResponse(
                success = false,
                errors = errors.map(_.message)
              )
            )
        }
      } yield response

    // Execute a DAG with inputs
    case req @ POST -> Root / "execute" =>
      (for {
        execReq <- req.as[ExecuteRequest]
        dagOpt  <- constellation.getDag(execReq.dagName)

        response <- dagOpt match {
          case Some(dagSpec) =>
            for {
              // Convert inputs: JSON → CValue
              convertResult <- ExecutionHelper.convertInputs(execReq.inputs, dagSpec).attempt
              response <- convertResult match {
                case Left(error) =>
                  BadRequest(
                    ExecuteResponse(
                      success = false,
                      error = Some(s"Input error: ${error.getMessage}")
                    )
                  )

                case Right(cValueInputs) =>
                  for {
                    // Execute DAG
                    execResult <- constellation.runDag(execReq.dagName, cValueInputs).attempt
                    response <- execResult match {
                      case Left(error) =>
                        InternalServerError(
                          ExecuteResponse(
                            success = false,
                            error = Some(s"Execution failed: ${error.getMessage}")
                          )
                        )

                      case Right(state) =>
                        for {
                          // Extract outputs: CValue → JSON
                          outputResult <- ExecutionHelper.extractOutputs(state).attempt
                          response <- outputResult match {
                            case Left(error) =>
                              InternalServerError(
                                ExecuteResponse(
                                  success = false,
                                  error = Some(s"Output error: ${error.getMessage}")
                                )
                              )

                            case Right(outputs) =>
                              Ok(
                                ExecuteResponse(
                                  success = true,
                                  outputs = outputs,
                                  error = None
                                )
                              )
                          }
                        } yield response
                    }
                  } yield response
              }
            } yield response

          case None =>
            NotFound(
              ErrorResponse(
                error = "DagNotFound",
                message = s"DAG '${execReq.dagName}' not found"
              )
            )
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(
          ExecuteResponse(
            success = false,
            error = Some(s"Unexpected error: ${error.getMessage}")
          )
        )
      }

    // Compile and run a script in one step (without storing the DAG)
    case req @ POST -> Root / "run" =>
      (for {
        runReq <- req.as[RunRequest]

        // Compile the source
        compileResult = compiler.compile(runReq.source, "ephemeral")

        response <- compileResult match {
          case Left(errors) =>
            BadRequest(
              RunResponse(
                success = false,
                compilationErrors = errors.map(_.message)
              )
            )

          case Right(compiled) =>
            val dagSpec = compiled.dagSpec
            for {
              // Convert inputs: JSON → CValue
              convertResult <- ExecutionHelper.convertInputs(runReq.inputs, dagSpec).attempt
              response <- convertResult match {
                case Left(error) =>
                  BadRequest(
                    RunResponse(
                      success = false,
                      error = Some(s"Input error: ${error.getMessage}")
                    )
                  )

                case Right(cValueInputs) =>
                  for {
                    // Execute DAG with pre-resolved synthetic modules from compilation
                    execResult <- constellation
                      .runDagWithModules(dagSpec, cValueInputs, compiled.syntheticModules)
                      .attempt
                    response <- execResult match {
                      case Left(error) =>
                        InternalServerError(
                          RunResponse(
                            success = false,
                            error = Some(s"Execution failed: ${error.getMessage}")
                          )
                        )

                      case Right(state) =>
                        for {
                          // Extract outputs: CValue → JSON
                          outputResult <- ExecutionHelper.extractOutputs(state).attempt
                          response <- outputResult match {
                            case Left(error) =>
                              InternalServerError(
                                RunResponse(
                                  success = false,
                                  error = Some(s"Output error: ${error.getMessage}")
                                )
                              )

                            case Right(outputs) =>
                              Ok(
                                RunResponse(
                                  success = true,
                                  outputs = outputs
                                )
                              )
                          }
                        } yield response
                    }
                  } yield response
              }
            } yield response
        }
      } yield response).handleErrorWith { error =>
        InternalServerError(
          RunResponse(
            success = false,
            error = Some(s"Unexpected error: ${error.getMessage}")
          )
        )
      }

    // List all available DAGs
    case GET -> Root / "dags" =>
      for {
        dags     <- constellation.listDags
        response <- Ok(DagListResponse(dags))
      } yield response

    // Get a specific DAG by name
    case GET -> Root / "dags" / dagName =>
      for {
        dagOpt <- constellation.getDag(dagName)
        response <- dagOpt match {
          case Some(dagSpec) =>
            Ok(
              DagResponse(
                name = dagName,
                metadata = dagSpec.metadata
              )
            )
          case None =>
            NotFound(
              ErrorResponse(
                error = "DagNotFound",
                message = s"DAG '$dagName' not found"
              )
            )
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

    // List all available namespaces
    case GET -> Root / "namespaces" =>
      val namespaceList = functionRegistry.namespaces.toList.sorted
      Ok(NamespaceListResponse(namespaceList))

    // List functions in a specific namespace
    case GET -> Root / "namespaces" / namespace =>
      val functions = functionRegistry.all
        .filter(sig =>
          sig.namespace.exists(ns => ns == namespace || ns.startsWith(namespace + "."))
        )
        .map { sig =>
          FunctionInfo(
            name = sig.name,
            qualifiedName = sig.qualifiedName,
            params = sig.params.map { case (name, typ) => s"$name: ${typ.prettyPrint}" },
            returns = sig.returns.prettyPrint
          )
        }
      if functions.nonEmpty then {
        Ok(NamespaceFunctionsResponse(namespace, functions))
      } else {
        NotFound(
          ErrorResponse(
            error = "NamespaceNotFound",
            message = s"Namespace '$namespace' not found or has no functions"
          )
        )
      }

    // Health check endpoint
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))
  }
}

object ConstellationRoutes {
  def apply(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry
  ): ConstellationRoutes =
    new ConstellationRoutes(constellation, compiler, functionRegistry)
}
