package io.constellation.lsp

import io.constellation.CType

object TypeFormatter {
  /** Convert CType to readable string representation */
  def formatCType(ctype: CType): String = ctype match {
    case CType.CString => "String"
    case CType.CInt => "Int"
    case CType.CFloat => "Float"
    case CType.CBoolean => "Boolean"
    case CType.CList(elem) => s"List<${formatCType(elem)}>"
    case CType.CMap(key, value) => s"Map<${formatCType(key)}, ${formatCType(value)}>"
    case CType.CProduct(fields) =>
      val fieldStrs = fields.toList.sortBy(_._1).map { case (name, typ) =>
        s"$name: ${formatCType(typ)}"
      }
      s"{ ${fieldStrs.mkString(", ")} }"
    case CType.CUnion(fields) =>
      val fieldStrs = fields.toList.sortBy(_._1).map { case (name, typ) =>
        s"$name: ${formatCType(typ)}"
      }
      s"(${fieldStrs.mkString(" | ")})"
    case CType.COptional(inner) =>
      s"Optional<${formatCType(inner)}>"
  }

  /** Format function signature from ModuleNodeSpec */
  def formatSignature(
    moduleName: String,
    consumes: Map[String, CType],
    produces: Map[String, CType]
  ): String = {
    // Format parameters
    val paramStrs = consumes.toList.sortBy(_._1).map { case (name, ctype) =>
      s"$name: ${formatCType(ctype)}"
    }
    val paramsStr = paramStrs.mkString(", ")

    // Format return type - unwrap single-field records for cleaner display
    val returnStr = if (produces.size == 1) {
      formatCType(produces.values.head)
    } else {
      formatCType(CType.CProduct(produces))
    }

    s"$moduleName($paramsStr) -> $returnStr"
  }

  /** Format parameter documentation */
  def formatParameters(consumes: Map[String, CType]): String = {
    if (consumes.isEmpty) {
      "No parameters"
    } else {
      val paramLines = consumes.toList.sortBy(_._1).map { case (name, ctype) =>
        s"- **$name**: `${formatCType(ctype)}`"
      }
      paramLines.mkString("\n")
    }
  }

  /** Format return type documentation - unwrap single-field records for cleaner display */
  def formatReturns(produces: Map[String, CType]): String = {
    if (produces.size == 1) {
      s"`${formatCType(produces.values.head)}`"
    } else {
      val fieldLines = produces.toList.sortBy(_._1).map { case (name, ctype) =>
        s"- **$name**: `${formatCType(ctype)}`"
      }
      fieldLines.mkString("\n")
    }
  }
}
