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
      for {
        execReq <- req.as[ExecuteRequest]
        // Convert JSON inputs to CValues
        dagOpt <- constellation.getDag(execReq.dagName)
        response <- dagOpt match {
          case Some(dagSpec) =>
            // For now, return a simple success response
            // In a full implementation, we'd need to convert JSON to CValue,
            // run the DAG, and convert results back to JSON
            Ok(ExecuteResponse(
              success = true,
              outputs = Map.empty,
              error = None
            ))
          case None =>
            NotFound(ErrorResponse(
              error = "DagNotFound",
              message = s"DAG '${execReq.dagName}' not found"
            ))
        }
      } yield response

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
