package io.constellation.docgen

import java.nio.file.{Files, Path, Paths}

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer
import scala.quoted.*
import scala.tasty.inspector.*

import io.constellation.docgen.model.*

/** Extracts type information from TASTy files using Scala 3 TASTy Inspector */
@experimental
object TastyExtractor:

  /** Extract types from all TASTy files in the given classpath */
  def extract(classpath: List[String], targetPackages: List[String]): List[TypeInfo] =
    val inspector = new TypeExtractorInspector(targetPackages)

    // Find all .tasty files
    val tastyFiles = classpath.flatMap { cp =>
      val path = Paths.get(cp)
      if Files.isDirectory(path) then findTastyFiles(path)
      else Nil
    }

    if tastyFiles.nonEmpty then TastyInspector.inspectTastyFiles(tastyFiles)(inspector)

    inspector.extractedTypes.toList

  private def findTastyFiles(dir: Path): List[String] =
    import scala.jdk.CollectionConverters.*
    Files
      .walk(dir)
      .filter(p => p.toString.endsWith(".tasty"))
      .map(_.toString)
      .toList
      .asScala
      .toList

/** Inspector that extracts type information from TASTy */
@experimental
private class TypeExtractorInspector(targetPackages: List[String]) extends Inspector:
  val extractedTypes: ListBuffer[TypeInfo] = ListBuffer.empty

  override def inspect(using Quotes)(tastys: List[Tasty[quotes.type]]): Unit =
    import quotes.reflect.*

    tastys.foreach { tasty =>
      val tree = tasty.ast
      extractFromTree(tree)
    }

  private def extractFromTree(using Quotes)(tree: quotes.reflect.Tree): Unit =
    import quotes.reflect.*

    tree match
      case PackageClause(pid, stats) =>
        stats.foreach(extractFromTree)

      case ClassDef(name, constr, parents, selfOpt, body) if shouldExtract(tree.symbol) =>
        val sym      = tree.symbol
        val pkg      = sym.owner.fullName
        val scaladoc = sym.docstring

        if sym.flags.is(Flags.Case) && sym.flags.is(Flags.Module) then
          // Case object - skip, handled as enum case
          ()
        else if sym.flags.is(Flags.Module) then
          // Object
          val methods = extractMethods(sym)
          val fields  = extractFields(sym)
          extractedTypes += ObjectInfo(name, pkg, scaladoc, methods, fields)
        else if sym.flags.is(Flags.Trait) then
          // Trait
          val typeParams  = extractTypeParams(sym)
          val parentNames = parents.collect { case t: TypeTree => t.tpe.typeSymbol.name }
          val methods     = extractMethods(sym)
          extractedTypes += TraitInfo(name, pkg, scaladoc, typeParams, parentNames, methods)
        else if sym.flags.is(Flags.Enum) then
          // Enum
          val cases = extractEnumCases(sym)
          extractedTypes += EnumInfo(name, pkg, scaladoc, cases)
        else
          // Class
          val typeParams  = extractTypeParams(sym)
          val parentNames = parents.collect { case t: TypeTree => t.tpe.typeSymbol.name }
          val fields      = extractFields(sym)
          val methods     = extractMethods(sym)
          val isCaseClass = sym.flags.is(Flags.Case)
          extractedTypes += ClassInfo(
            name,
            pkg,
            scaladoc,
            typeParams,
            parentNames,
            fields,
            methods,
            isCaseClass
          )

      case _ =>
        // Skip other tree types
        ()

  private def shouldExtract(using Quotes)(sym: quotes.reflect.Symbol): Boolean =
    val fullName = sym.fullName
    targetPackages.exists(pkg => fullName.startsWith(pkg))

  private def extractTypeParams(using Quotes)(sym: quotes.reflect.Symbol): List[String] =
    import quotes.reflect.*
    sym.primaryConstructor.paramSymss.flatten
      .filter(_.isTypeParam)
      .map(_.name)

  private def extractFields(using Quotes)(sym: quotes.reflect.Symbol): List[FieldInfo] =
    import quotes.reflect.*
    sym.caseFields.map { field =>
      FieldInfo(
        name = field.name,
        typeName = formatType(field.info),
        scaladoc = field.docstring
      )
    }

  private def extractMethods(using Quotes)(sym: quotes.reflect.Symbol): List[MethodInfo] =
    import quotes.reflect.*
    sym.methodMembers
      .filter(m =>
        m.flags.is(Flags.Method) && !m.flags.is(Flags.Private) && !m.flags.is(Flags.Protected)
      )
      .filter(m => !m.name.startsWith("$") && m.name != "<init>")
      .map { method =>
        val typeParams = method.paramSymss.flatten.filter(_.isTypeParam).map(_.name)
        val params = method.paramSymss.flatten
          .filterNot(_.isTypeParam)
          .map(p => ParamInfo(p.name, formatType(p.info)))

        // Get return type by stripping method type layers
        val returnType = method.info match
          case MethodType(_, _, res) => formatType(res)
          case PolyType(_, _, res)   => formatType(res)
          case other                 => formatType(other)

        MethodInfo(
          name = method.name,
          typeParams = typeParams,
          params = params,
          returnType = returnType,
          scaladoc = method.docstring
        )
      }

  private def formatType(using Quotes)(tpe: quotes.reflect.TypeRepr): String =
    import quotes.reflect.*
    tpe match
      case AppliedType(tycon, args) =>
        val base    = tycon.typeSymbol.name
        val argsStr = args.map(formatType).mkString(", ")
        s"$base[$argsStr]"
      case MethodType(_, _, res) => formatType(res)
      case PolyType(_, _, res)   => formatType(res)
      case other                 => other.typeSymbol.name

  private def extractEnumCases(using Quotes)(sym: quotes.reflect.Symbol): List[EnumCaseInfo] =
    import quotes.reflect.*
    sym.children
      .filter(_.flags.is(Flags.Case))
      .map { caseSym =>
        val params = caseSym.primaryConstructor.paramSymss.flatten
          .filterNot(_.isTypeParam)
          .map(p => ParamInfo(p.name, formatType(p.info)))
        EnumCaseInfo(caseSym.name, params)
      }
