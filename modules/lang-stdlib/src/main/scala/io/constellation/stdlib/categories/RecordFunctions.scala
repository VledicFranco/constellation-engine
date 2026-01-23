package io.constellation.stdlib.categories

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.semantic.*
import io.constellation.lang.semantic.SemanticType.*

/** Row-polymorphic record functions for the standard library.
  *
  * These functions demonstrate row polymorphism by accepting records with
  * "at least" certain fields. Extra fields are allowed and ignored.
  *
  * Usage in constellation-lang:
  * {{{
  * in user: { name: String, age: Int, email: String }
  *
  * # GetName works because user has at least a 'name' field
  * name = GetName(user)
  *
  * # GetAge works because user has at least an 'age' field
  * age = GetAge(user)
  * }}}
  */
trait RecordFunctions {

  // Input/Output case classes for modules
  case class NameInput(name: String)
  case class NameOutput(result: String)
  case class AgeInput(age: Long)
  case class AgeOutput(result: Long)
  case class IdInput(id: Long)
  case class IdOutput(result: Long)
  case class ValueInput(value: String)
  case class ValueOutput(result: String)

  // Counter for generating unique row variables
  private var rowVarId = 1000 // Start at 1000 to avoid conflicts with type checker
  private def nextRowVar(): RowVar = {
    rowVarId += 1
    RowVar(rowVarId)
  }

  // Row-polymorphic signatures

  /** GetName: ∀ρ. { name: String | ρ } -> String
    * Extract the name field from any record that has one.
    */
  lazy val getNameSignature: FunctionSignature = {
    val rv = nextRowVar()
    FunctionSignature(
      name = "GetName",
      params = List(("record", SOpenRecord(Map("name" -> SString), rv))),
      returns = SString,
      moduleName = "stdlib.record.get-name",
      namespace = Some("stdlib.record"),
      rowVars = List(rv)
    )
  }

  /** GetAge: ∀ρ. { age: Int | ρ } -> Int
    * Extract the age field from any record that has one.
    */
  lazy val getAgeSignature: FunctionSignature = {
    val rv = nextRowVar()
    FunctionSignature(
      name = "GetAge",
      params = List(("record", SOpenRecord(Map("age" -> SInt), rv))),
      returns = SInt,
      moduleName = "stdlib.record.get-age",
      namespace = Some("stdlib.record"),
      rowVars = List(rv)
    )
  }

  /** GetId: ∀ρ. { id: Int | ρ } -> Int
    * Extract the id field from any record that has one.
    */
  lazy val getIdSignature: FunctionSignature = {
    val rv = nextRowVar()
    FunctionSignature(
      name = "GetId",
      params = List(("record", SOpenRecord(Map("id" -> SInt), rv))),
      returns = SInt,
      moduleName = "stdlib.record.get-id",
      namespace = Some("stdlib.record"),
      rowVars = List(rv)
    )
  }

  /** GetValue: ∀ρ. { value: String | ρ } -> String
    * Extract the value field from any record that has one.
    */
  lazy val getValueSignature: FunctionSignature = {
    val rv = nextRowVar()
    FunctionSignature(
      name = "GetValue",
      params = List(("record", SOpenRecord(Map("value" -> SString), rv))),
      returns = SString,
      moduleName = "stdlib.record.get-value",
      namespace = Some("stdlib.record"),
      rowVars = List(rv)
    )
  }

  def recordSignatures: List[FunctionSignature] = List(
    getNameSignature,
    getAgeSignature,
    getIdSignature,
    getValueSignature
  )

  // Module implementations

  /** GetName module - extracts 'name' field from a record */
  val getNameModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.record.get-name", "Extract name field from record", 1, 0)
    .implementationPure[NameInput, NameOutput](input => NameOutput(input.name))
    .build

  /** GetAge module - extracts 'age' field from a record */
  val getAgeModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.record.get-age", "Extract age field from record", 1, 0)
    .implementationPure[AgeInput, AgeOutput](input => AgeOutput(input.age))
    .build

  /** GetId module - extracts 'id' field from a record */
  val getIdModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.record.get-id", "Extract id field from record", 1, 0)
    .implementationPure[IdInput, IdOutput](input => IdOutput(input.id))
    .build

  /** GetValue module - extracts 'value' field from a record */
  val getValueModule: Module.Uninitialized = ModuleBuilder
    .metadata("stdlib.record.get-value", "Extract value field from record", 1, 0)
    .implementationPure[ValueInput, ValueOutput](input => ValueOutput(input.value))
    .build

  def recordModules: Map[String, Module.Uninitialized] = Map(
    "stdlib.record.get-name" -> getNameModule,
    "stdlib.record.get-age" -> getAgeModule,
    "stdlib.record.get-id" -> getIdModule,
    "stdlib.record.get-value" -> getValueModule
  )
}
