package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.implicits.*

/** Reconstructs synthetic module implementations from a [[DagSpec]].
  *
  * Currently only branch modules can be reconstructed because their behaviour is fully determined
  * by the DagSpec structure (number of condition/expression pairs and the output type). HOF
  * transforms (filter, map, etc.) contain closures that cannot be serialized or reconstructed.
  */
object SyntheticModuleFactory {

  /** Scan a DagSpec for branch modules and reconstruct their [[Module.Uninitialized]].
    *
    * A module is identified as a branch module when its name contains `"branch-"`.
    *
    * @return
    *   Map of module UUID to reconstructed Module.Uninitialized
    */
  def fromDagSpec(dagSpec: DagSpec): Map[UUID, Module.Uninitialized] =
    dagSpec.modules.collect {
      case (moduleId, spec) if isBranchModule(spec) =>
        val numCases    = countBranchCases(spec)
        val outputCType = spec.produces.getOrElse("out", CType.CString)
        moduleId -> createBranchModule(spec, numCases, outputCType)
    }

  /** Check whether a module spec represents a branch module. */
  private def isBranchModule(spec: ModuleNodeSpec): Boolean =
    spec.name.contains("branch-")

  /** Count the number of condition/expression pairs in a branch module.
    *
    * Branch modules have inputs named `cond0`, `expr0`, `cond1`, `expr1`, ..., `otherwise`.
    */
  private def countBranchCases(spec: ModuleNodeSpec): Int = {
    val condKeys = spec.consumes.keys.filter(_.startsWith("cond")).toList
    condKeys.size
  }

  /** Create a branch module implementation identical to the one in DagCompiler. */
  private def createBranchModule(
      spec: ModuleNodeSpec,
      numCases: Int,
      outputCType: CType
  ): Module.Uninitialized =
    Module.Uninitialized(
      spec = spec,
      init = (moduleId, dagSpec) =>
        for {
          consumesNs    <- Module.Namespace.consumes(moduleId, dagSpec)
          producesNs    <- Module.Namespace.produces(moduleId, dagSpec)
          condDeferreds <- (0 until numCases).toList.traverse(_ => cats.effect.Deferred[IO, Any])
          exprDeferreds <- (0 until numCases).toList.traverse(_ => cats.effect.Deferred[IO, Any])
          otherwiseDeferred <- cats.effect.Deferred[IO, Any]
          outDeferred       <- cats.effect.Deferred[IO, Any]
          condIds           <- (0 until numCases).toList.traverse(i => consumesNs.nameId(s"cond$i"))
          exprIds           <- (0 until numCases).toList.traverse(i => consumesNs.nameId(s"expr$i"))
          otherwiseId       <- consumesNs.nameId("otherwise")
          outId             <- producesNs.nameId("out")
        } yield {
          val dataMap = (condIds.zip(condDeferreds) ++ exprIds.zip(exprDeferreds) ++
            List((otherwiseId, otherwiseDeferred), (outId, outDeferred))).toMap
          Module.Runnable(
            id = moduleId,
            data = dataMap,
            run = runtime => {
              def evaluateBranches(idx: Int): IO[Any] =
                if idx >= numCases then {
                  runtime.getTableData(otherwiseId)
                } else {
                  for {
                    condValue <- runtime.getTableData(condIds(idx))
                    result <-
                      if condValue.asInstanceOf[Boolean] then {
                        runtime.getTableData(exprIds(idx))
                      } else {
                        evaluateBranches(idx + 1)
                      }
                  } yield result
                }
              for {
                result <- evaluateBranches(0)
                _      <- runtime.setTableData(outId, result)
                cValue = Runtime.anyToCValue(result, outputCType)
                _ <- runtime.setStateData(outId, cValue)
              } yield ()
            }
          )
        }
    )
}
