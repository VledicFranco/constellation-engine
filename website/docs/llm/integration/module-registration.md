---
title: "Module Registration Reference"
sidebar_position: 1
description: "Complete guide to runtime module registration, custom stdlib patterns, and organizing large module sets."
---

# Module Registration Reference

Complete reference for registering modules with the Constellation runtime, organizing custom standard libraries, and managing large module sets.

:::info Quick Navigation
- **[Registration Overview](#registration-overview)** - Core concepts and workflow
- **[Single Module Registration](#single-module-registration)** - Register individual modules
- **[Batch Registration](#batch-registration-patterns)** - Register multiple modules efficiently
- **[Organizing Module Libraries](#organizing-modules-into-libraries)** - Structure for large module sets
- **[Custom Stdlib Patterns](#custom-stdlib-patterns)** - Build domain-specific standard libraries
- **[Dynamic Module Loading](#dynamic-module-loading)** - Load modules at runtime
- **[Module Versioning](#module-versioning-and-updates)** - Version management strategies
- **[Namespace Management](#namespace-management)** - Organize modules with prefixes
- **[Module Discovery](#module-discovery-and-introspection)** - Query registered modules
- **[Best Practices](#best-practices)** - Patterns for production deployments
:::

## Registration Overview

### Core Concepts

**Module Registration** is the process of making modules available to the Constellation runtime so they can be called from constellation-lang pipelines.

```scala
import cats.effect.IO
import cats.implicits._
import io.constellation._

// 1. Create a Constellation instance
val constellation = ConstellationImpl.init

// 2. Register modules
constellation.flatMap { c =>
  c.setModule(myModule)
}

// 3. Use modules in pipelines
```

**Key Registration Interfaces:**

- **`Constellation.setModule(module: Module.Uninitialized): IO[Unit]`** - Register a single module
- **`ModuleRegistry`** - Internal registry managing name → module mappings
- **`Module.Uninitialized`** - Module template ready for registration
- **`Module.Initialized`** - Module with runtime context after initialization

### Registration Workflow

```
┌─────────────────────┐
│ Define Module       │
│ (ModuleBuilder)     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Build Module        │
│ (.build)            │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Register Module     │
│ (setModule)         │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ Use in Pipelines    │
│ (constellation-lang)│
└─────────────────────┘
```

**What Happens During Registration:**

1. **Name Validation** - Ensures module name is unique
2. **Spec Storage** - Stores module spec in registry
3. **Factory Storage** - Stores uninitialized module for later initialization
4. **Ready for Use** - Module can now be referenced in DAG compilation

## Single Module Registration

### Basic Registration

Register a single module with the runtime:

```scala
import cats.effect.IO
import io.constellation._

case class TextInput(text: String)
case class TextOutput(result: String)

val uppercaseModule = ModuleBuilder
  .metadata("Uppercase", "Convert text to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build

// Register the module
val program: IO[Unit] = for {
  constellation <- ConstellationImpl.init
  _             <- constellation.setModule(uppercaseModule)
} yield ()
```

### Registering with IO-Based Modules

For modules that perform side effects:

```scala
case class ApiInput(endpoint: String)
case class ApiOutput(data: String)

val apiCallModule = ModuleBuilder
  .metadata("ApiCall", "Make HTTP API call", 1, 0)
  .implementation[ApiInput, ApiOutput] { input =>
    IO {
      // Perform HTTP request
      val response = scala.io.Source.fromURL(input.endpoint).mkString
      ApiOutput(response)
    }
  }
  .build

// Registration is the same
constellation.setModule(apiCallModule)
```

### Registering Modules with Context

Modules can include definition context metadata:

```scala
import io.circe.Json
import io.circe.syntax._

val contextualModule = ModuleBuilder
  .metadata("Contextual", "Module with context", 1, 0)
  .definitionContext(Map(
    "author" -> "John Doe".asJson,
    "source" -> "custom-library".asJson,
    "license" -> "MIT".asJson
  ))
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build

constellation.setModule(contextualModule)
```

### Error Handling During Registration

Registration can fail if module names conflict:

```scala
val safeRegistration: IO[Unit] = for {
  constellation <- ConstellationImpl.init

  // Check if module already exists
  existing <- constellation.getModuleByName("Uppercase")

  _ <- existing match {
    case Some(_) =>
      IO.println("Module already registered, skipping")
    case None =>
      constellation.setModule(uppercaseModule)
  }
} yield ()
```

## Batch Registration Patterns

### Using `traverse` for Multiple Modules

The idiomatic way to register multiple modules in Cats Effect:

```scala
import cats.effect.IO
import cats.implicits._  // Required for .traverse
import io.constellation._

val modules: List[Module.Uninitialized] = List(
  uppercaseModule,
  lowercaseModule,
  trimModule
)

val program: IO[Unit] = for {
  constellation <- ConstellationImpl.init
  _             <- modules.traverse(constellation.setModule)
} yield ()
```

**Why `traverse`:**
- Transforms `List[Module]` → `List[IO[Unit]]` → `IO[List[Unit]]`
- Executes registrations sequentially
- Short-circuits on first error
- Type-safe and composable

### Registration with Error Accumulation

Continue registering modules even if some fail:

```scala
import cats.effect.IO
import cats.implicits._

def registerAllOrReport(
  constellation: Constellation,
  modules: List[Module.Uninitialized]
): IO[List[Either[Throwable, Unit]]] = {
  modules.traverse { module =>
    constellation.setModule(module).attempt
  }
}

// Usage
val results = for {
  c       <- ConstellationImpl.init
  results <- registerAllOrReport(c, modules)
  _       <- IO.println(s"Registered ${results.count(_.isRight)} of ${modules.length} modules")
} yield results
```

### Parallel Registration (Advanced)

Register independent modules in parallel for faster startup:

```scala
import cats.effect.IO
import cats.implicits._

val parallelRegistration: IO[Unit] = for {
  constellation <- ConstellationImpl.init

  // Register modules in parallel
  _ <- modules.parTraverse(constellation.setModule)
} yield ()
```

**⚠️ Caution:**
- Only use if module initialization is expensive and modules are independent
- Module registry must be thread-safe (default implementation is)
- Consider using bounded parallelism: `parTraverseN(8)(constellation.setModule)`

### Conditional Registration

Register modules based on runtime conditions:

```scala
import cats.effect.IO

def registerConditionally(
  constellation: Constellation,
  modules: List[Module.Uninitialized],
  enableFeatures: Set[String]
): IO[Unit] = {
  val filtered = modules.filter { module =>
    module.spec.metadata.tags.exists(enableFeatures.contains)
  }

  filtered.traverse(constellation.setModule).void
}

// Usage
val program = for {
  c <- ConstellationImpl.init
  _ <- registerConditionally(
    c,
    allModules,
    Set("text", "math")  // Only register text and math modules
  )
} yield ()
```

## Organizing Modules into Libraries

### Category-Based Organization

Split modules into logical categories using traits (stdlib pattern):

```scala
package io.mycompany.modules

import io.constellation._

// modules/TextFunctions.scala
trait TextFunctions {
  // Module definitions
  val uppercaseModule: Module.Uninitialized = ...
  val lowercaseModule: Module.Uninitialized = ...
  val trimModule: Module.Uninitialized = ...

  // Collect modules into a map
  def textModules: Map[String, Module.Uninitialized] = Map(
    uppercaseModule.spec.name -> uppercaseModule,
    lowercaseModule.spec.name -> lowercaseModule,
    trimModule.spec.name      -> trimModule
  )
}

// modules/MathFunctions.scala
trait MathFunctions {
  val addModule: Module.Uninitialized = ...
  val subtractModule: Module.Uninitialized = ...

  def mathModules: Map[String, Module.Uninitialized] = Map(
    addModule.spec.name      -> addModule,
    subtractModule.spec.name -> subtractModule
  )
}

// MyLib.scala
object MyLib extends TextFunctions with MathFunctions {
  def allModules: Map[String, Module.Uninitialized] =
    textModules ++ mathModules
}
```

**Benefits:**
- Clear separation of concerns
- Easy to add/remove categories
- Each category can be tested independently
- IDE-friendly organization

### Object-Based Organization

Group related modules in companion objects:

```scala
package io.mycompany.modules

import io.constellation._

object TextModules {
  case class TextInput(text: String)
  case class TextOutput(result: String)

  val uppercase: Module.Uninitialized = ModuleBuilder
    .metadata("Uppercase", "Convert to uppercase", 1, 0)
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
    .build

  val lowercase: Module.Uninitialized = ModuleBuilder
    .metadata("Lowercase", "Convert to lowercase", 1, 0)
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toLowerCase))
    .build

  val all: List[Module.Uninitialized] = List(uppercase, lowercase)
}

object DataModules {
  val sumList: Module.Uninitialized = ...
  val average: Module.Uninitialized = ...

  val all: List[Module.Uninitialized] = List(sumList, average)
}

// Usage
val allModules = TextModules.all ++ DataModules.all
```

### Multi-Package Organization

For large libraries, split into packages:

```
src/main/scala/io/mycompany/
├── MyLib.scala                    (top-level facade)
└── modules/
    ├── text/
    │   ├── TextModules.scala
    │   └── TextSignatures.scala
    ├── data/
    │   ├── DataModules.scala
    │   └── DataSignatures.scala
    └── network/
        ├── HttpModules.scala
        └── HttpSignatures.scala
```

**MyLib.scala:**
```scala
package io.mycompany

import io.constellation._
import io.mycompany.modules.text.TextModules
import io.mycompany.modules.data.DataModules
import io.mycompany.modules.network.HttpModules

object MyLib {
  def allModules: Map[String, Module.Uninitialized] =
    TextModules.all ++ DataModules.all ++ HttpModules.all

  def registerAll(constellation: Constellation): IO[Unit] =
    allModules.values.toList.traverse(constellation.setModule).void
}
```

## Custom Stdlib Patterns

### Creating a Domain-Specific Standard Library

Build a custom stdlib for a specific domain (e.g., e-commerce, finance, ML):

```scala
package io.mycompany.ecommerce

import cats.effect.IO
import cats.implicits._
import io.constellation._
import io.constellation.lang.LangCompilerBuilder
import io.constellation.lang.semantic._
import io.constellation.stdlib.StdLib

// 1. Define modules
trait ProductModules {
  case class ProductInput(sku: String)
  case class ProductOutput(name: String, price: Double)

  val getProduct: Module.Uninitialized = ModuleBuilder
    .metadata("GetProduct", "Fetch product by SKU", 1, 0)
    .tags("ecommerce", "product")
    .implementation[ProductInput, ProductOutput] { input =>
      IO {
        // Database lookup
        ProductOutput("Widget", 29.99)
      }
    }
    .build

  def productModules: Map[String, Module.Uninitialized] = Map(
    getProduct.spec.name -> getProduct
  )
}

trait OrderModules {
  case class OrderInput(userId: String, items: List[String])
  case class OrderOutput(orderId: String, total: Double)

  val createOrder: Module.Uninitialized = ModuleBuilder
    .metadata("CreateOrder", "Create new order", 1, 0)
    .tags("ecommerce", "order")
    .implementation[OrderInput, OrderOutput] { input =>
      IO {
        OrderOutput("ORD-123", 99.99)
      }
    }
    .build

  def orderModules: Map[String, Module.Uninitialized] = Map(
    createOrder.spec.name -> createOrder
  )
}

// 2. Define function signatures for type checking
trait EcommerceSignatures {
  val getProductSig = FunctionSignature(
    name = "GetProduct",
    params = List("sku" -> SemanticType.SString),
    returns = SemanticType.SRecord(Map(
      "name" -> SemanticType.SString,
      "price" -> SemanticType.SFloat
    )),
    moduleName = "GetProduct"
  )

  val createOrderSig = FunctionSignature(
    name = "CreateOrder",
    params = List(
      "userId" -> SemanticType.SString,
      "items" -> SemanticType.SList(SemanticType.SString)
    ),
    returns = SemanticType.SRecord(Map(
      "orderId" -> SemanticType.SString,
      "total" -> SemanticType.SFloat
    )),
    moduleName = "CreateOrder"
  )

  def ecommerceSignatures: List[FunctionSignature] = List(
    getProductSig,
    createOrderSig
  )
}

// 3. Combine into a custom stdlib
object EcommerceStdLib
  extends ProductModules
  with OrderModules
  with EcommerceSignatures {

  def allModules: Map[String, Module.Uninitialized] =
    productModules ++ orderModules

  def allSignatures: List[FunctionSignature] =
    ecommerceSignatures

  // Register with compiler builder
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder =
    allSignatures.foldLeft(builder)((b, sig) => b.withFunction(sig))

  // Create a compiler with both base stdlib and ecommerce stdlib
  def compiler: LangCompiler = {
    val combinedModules = StdLib.allModules ++ allModules
    val builder = registerAll(StdLib.registerAll(LangCompilerBuilder()))
      .withModules(combinedModules)
    builder.build
  }
}
```

**Usage:**
```scala
val program = for {
  // Use combined compiler
  compiler <- IO.pure(EcommerceStdLib.compiler)

  // Compile pipeline using both base and ecommerce functions
  result <- compiler.compile("""
    in sku: String

    product = GetProduct(sku)

    # Can also use base stdlib functions
    formatted = concat("Product: ", product.name)

    out formatted
  """, "product-lookup")
} yield result
```

### Extending Existing Stdlibs

Add domain modules while preserving the base stdlib:

```scala
object ExtendedStdLib {
  // Your domain modules
  val customModules: Map[String, Module.Uninitialized] = Map(
    "CustomModule" -> myCustomModule
  )

  // Combine with base stdlib
  def allModules: Map[String, Module.Uninitialized] =
    StdLib.allModules ++ customModules

  // Register all modules with Constellation
  def registerAll(constellation: Constellation): IO[Unit] =
    allModules.values.toList.traverse(constellation.setModule).void
}
```

### Plugin-Based Architecture

Support loading modules from plugins:

```scala
trait ModulePlugin {
  def name: String
  def modules: Map[String, Module.Uninitialized]
  def signatures: List[FunctionSignature]
}

class PluggableStdLib(plugins: List[ModulePlugin]) {
  def allModules: Map[String, Module.Uninitialized] =
    plugins.flatMap(_.modules).toMap

  def allSignatures: List[FunctionSignature] =
    plugins.flatMap(_.signatures)

  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder =
    allSignatures.foldLeft(builder)((b, sig) => b.withFunction(sig))
}

// Plugin implementation
object TextProcessingPlugin extends ModulePlugin {
  def name = "text-processing"

  def modules = Map(
    "Uppercase" -> uppercaseModule,
    "Lowercase" -> lowercaseModule
  )

  def signatures = List(uppercaseSig, lowercaseSig)
}

// Usage
val stdLib = new PluggableStdLib(List(
  TextProcessingPlugin,
  DataProcessingPlugin,
  NetworkPlugin
))
```

## Dynamic Module Loading

### Loading Modules at Runtime

Load and register modules based on configuration:

```scala
import cats.effect.IO
import io.circe.parser._

case class ModuleConfig(
  name: String,
  enabled: Boolean,
  tags: List[String]
)

def loadModulesFromConfig(configJson: String): IO[List[Module.Uninitialized]] = {
  for {
    config <- IO.fromEither(decode[List[ModuleConfig]](configJson))

    // Map config to actual module instances
    modules = config.filter(_.enabled).flatMap { cfg =>
      ModuleRegistry.lookupByName(cfg.name)
    }
  } yield modules
}

val program = for {
  config      <- IO(scala.io.Source.fromFile("modules.json").mkString)
  modules     <- loadModulesFromConfig(config)
  constellation <- ConstellationImpl.init
  _           <- modules.traverse(constellation.setModule)
} yield ()
```

### Lazy Module Registration

Register modules on-demand when first referenced:

```scala
import cats.effect.Ref

class LazyModuleRegistry(
  constellation: Constellation,
  moduleFactory: String => Option[Module.Uninitialized]
) {
  private val registered = Ref.unsafe[IO, Set[String]](Set.empty)

  def ensureRegistered(moduleName: String): IO[Unit] = {
    registered.get.flatMap { reg =>
      if (reg.contains(moduleName)) {
        IO.unit
      } else {
        moduleFactory(moduleName) match {
          case Some(module) =>
            for {
              _ <- constellation.setModule(module)
              _ <- registered.update(_ + moduleName)
            } yield ()
          case None =>
            IO.raiseError(new Exception(s"Unknown module: $moduleName"))
        }
      }
    }
  }
}
```

### Hot Module Reloading

Support updating modules without restarting:

```scala
class ReloadableModuleRegistry(constellation: Constellation) {
  def reloadModule(
    name: String,
    newModule: Module.Uninitialized
  ): IO[Unit] = {
    // Note: Constellation doesn't support un-registration
    // so this replaces the module implementation
    constellation.setModule(newModule)
  }

  def reloadFromSource(
    name: String,
    sourceCode: String
  ): IO[Unit] = {
    for {
      // Compile new module from source
      module <- compileModuleSource(sourceCode)

      // Register (replaces existing)
      _ <- constellation.setModule(module)

      _ <- IO.println(s"Reloaded module: $name")
    } yield ()
  }
}
```

## Module Versioning and Updates

### Semantic Versioning

Use semantic versioning in module metadata:

```scala
val moduleV1 = ModuleBuilder
  .metadata("MyModule", "Description", majorVersion = 1, minorVersion = 0)
  .implementationPure[Input, Output] { input => ... }
  .build

val moduleV2 = ModuleBuilder
  .metadata("MyModule", "Description", majorVersion = 2, minorVersion = 0)
  .implementationPure[Input, Output] { input =>
    // Breaking change - new implementation
    ...
  }
  .build
```

### Side-by-Side Versioning

Support multiple versions simultaneously with naming:

```scala
val moduleV1 = ModuleBuilder
  .metadata("MyModule_v1", "Description v1", 1, 0)
  .implementationPure[InputV1, OutputV1] { ... }
  .build

val moduleV2 = ModuleBuilder
  .metadata("MyModule_v2", "Description v2", 2, 0)
  .implementationPure[InputV2, OutputV2] { ... }
  .build

// Register both
constellation.setModule(moduleV1)
constellation.setModule(moduleV2)
```

**In constellation-lang:**
```constellation
# Use specific version
result_v1 = MyModule_v1(input)
result_v2 = MyModule_v2(input)
```

### Version Aliasing

Point "latest" alias to current version:

```scala
object ModuleVersions {
  val v1: Module.Uninitialized = ...
  val v2: Module.Uninitialized = ...

  // Current production version
  val latest: Module.Uninitialized = v2

  def registerAll(constellation: Constellation): IO[Unit] = {
    List(
      v1,
      v2,
      latest.spec.copy(metadata = latest.spec.metadata.copy(name = "MyModule"))
    ).traverse(constellation.setModule).void
  }
}
```

### Deprecation Strategy

Mark deprecated modules with tags and context:

```scala
val deprecatedModule = ModuleBuilder
  .metadata("OldModule", "DEPRECATED: Use NewModule instead", 1, 0)
  .tags("deprecated", "legacy")
  .definitionContext(Map(
    "deprecated" -> true.asJson,
    "deprecatedSince" -> "2024-01-01".asJson,
    "replacement" -> "NewModule".asJson
  ))
  .implementationPure[Input, Output] { ... }
  .build
```

## Namespace Management

### Prefixed Naming

Use dot-notation for namespacing:

```scala
trait MathModules {
  val add = ModuleBuilder
    .metadata("stdlib.math.add", "Add two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut] { ... }
    .build

  val multiply = ModuleBuilder
    .metadata("stdlib.math.multiply", "Multiply two integers", 1, 0)
    .tags("stdlib", "math")
    .implementationPure[TwoInts, IntOut] { ... }
    .build
}

trait StringModules {
  val concat = ModuleBuilder
    .metadata("stdlib.string.concat", "Concatenate strings", 1, 0)
    .tags("stdlib", "string")
    .implementationPure[TwoStrings, StringOut] { ... }
    .build
}
```

**Benefits:**
- Clear ownership and category
- Prevents name collisions
- IDE autocomplete friendly
- Easy to filter by prefix

### Organization-Based Namespacing

Prefix modules with organization or team name:

```scala
// Team-specific modules
val orderModule = ModuleBuilder
  .metadata("acme.sales.CreateOrder", "Create sales order", 1, 0)
  .tags("acme", "sales")
  .implementationPure[OrderInput, OrderOutput] { ... }
  .build

val inventoryModule = ModuleBuilder
  .metadata("acme.inventory.CheckStock", "Check inventory", 1, 0)
  .tags("acme", "inventory")
  .implementationPure[StockInput, StockOutput] { ... }
  .build
```

### Namespace Helpers

Build utilities for consistent naming:

```scala
object NamespaceHelper {
  def namespaced(category: String, name: String): String =
    s"mycompany.$category.$name"

  def buildModule[I <: Product, O <: Product](
    category: String,
    name: String,
    description: String
  )(impl: I => O): ModuleBuilderInit = {
    ModuleBuilder.metadata(
      name = namespaced(category, name),
      description = description,
      majorVersion = 1,
      minorVersion = 0
    ).tags("mycompany", category)
  }
}

// Usage
val uppercase = NamespaceHelper
  .buildModule[TextInput, TextOutput]("text", "Uppercase", "Convert to uppercase")
  .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
  .build
// Results in module named: "mycompany.text.Uppercase"
```

### Namespace Filtering

Register only modules matching a namespace:

```scala
def registerNamespace(
  constellation: Constellation,
  modules: Map[String, Module.Uninitialized],
  namespace: String
): IO[Unit] = {
  val filtered = modules.filter { case (name, _) =>
    name.startsWith(s"$namespace.")
  }
  filtered.values.toList.traverse(constellation.setModule).void
}

// Register only "stdlib.math.*" modules
registerNamespace(constellation, allModules, "stdlib.math")
```

## Module Discovery and Introspection

### Listing Registered Modules

Query all registered modules:

```scala
val program = for {
  constellation <- ConstellationImpl.init
  _             <- allModules.traverse(constellation.setModule)

  // List all registered modules
  specs <- constellation.getModules

  _ <- IO.println(s"Registered ${specs.length} modules:")
  _ <- specs.traverse { spec =>
    IO.println(s"  - ${spec.name}: ${spec.description}")
  }
} yield ()
```

### Module Lookup by Name

Check if a specific module is registered:

```scala
val lookupModule = for {
  constellation <- ConstellationImpl.init

  // Try to get a specific module
  maybeModule <- constellation.getModuleByName("Uppercase")

  _ <- maybeModule match {
    case Some(module) =>
      IO.println(s"Found: ${module.spec.metadata.name}")
    case None =>
      IO.println("Module not found")
  }
} yield ()
```

### Query Modules by Tags

Filter modules by tags:

```scala
def findModulesByTag(
  constellation: Constellation,
  tag: String
): IO[List[ModuleNodeSpec]] = {
  constellation.getModules.map { specs =>
    specs.filter(_.metadata.tags.contains(tag))
  }
}

// Usage
val textModules = for {
  c <- ConstellationImpl.init
  _ <- registerAll(c)
  modules <- findModulesByTag(c, "text")
  _ <- IO.println(s"Found ${modules.length} text modules")
} yield modules
```

### Build Module Directory

Generate documentation from registered modules:

```scala
case class ModuleInfo(
  name: String,
  description: String,
  version: String,
  tags: List[String]
)

def buildModuleDirectory(
  constellation: Constellation
): IO[List[ModuleInfo]] = {
  constellation.getModules.map { specs =>
    specs.map { spec =>
      ModuleInfo(
        name = spec.metadata.name,
        description = spec.metadata.description,
        version = s"${spec.metadata.majorVersion}.${spec.metadata.minorVersion}",
        tags = spec.metadata.tags
      )
    }
  }
}

// Generate JSON catalog
val catalog = for {
  c <- ConstellationImpl.init
  _ <- registerAll(c)
  dir <- buildModuleDirectory(c)
  json = dir.asJson.spaces2
  _ <- IO(scala.io.File("module-catalog.json").write(json))
} yield ()
```

### Health Check Modules

Verify all required modules are registered:

```scala
def checkRequiredModules(
  constellation: Constellation,
  required: Set[String]
): IO[Either[List[String], Unit]] = {
  for {
    specs <- constellation.getModules
    registered = specs.map(_.metadata.name).toSet
    missing = required -- registered
  } yield {
    if (missing.isEmpty) Right(())
    else Left(missing.toList)
  }
}

// Usage
val healthCheck = for {
  c <- ConstellationImpl.init
  _ <- registerAll(c)

  result <- checkRequiredModules(c, Set(
    "Uppercase",
    "Lowercase",
    "GetProduct"
  ))

  _ <- result match {
    case Right(_) =>
      IO.println("✓ All required modules registered")
    case Left(missing) =>
      IO.raiseError(new Exception(s"Missing modules: ${missing.mkString(", ")}"))
  }
} yield ()
```

## Best Practices

### Registration at Startup

Register all modules during application initialization:

```scala
object MyApplication extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      // Initialize Constellation
      constellation <- ConstellationImpl.init

      // Register all modules upfront
      _ <- MyLib.registerAll(constellation)

      // Verify registration
      specs <- constellation.getModules
      _ <- IO.println(s"Registered ${specs.length} modules")

      // Start server
      _ <- ConstellationServer.builder(constellation, compiler).run
    } yield ExitCode.Success
  }
}
```

### Module Organization Checklist

For production deployments:

✅ **Group by Category** - Use traits or objects to organize by domain
✅ **Use Namespacing** - Prefix module names with category/org
✅ **Version Explicitly** - Include version in metadata
✅ **Tag Appropriately** - Add tags for filtering and discovery
✅ **Document Thoroughly** - Write clear descriptions
✅ **Test Registration** - Verify all modules register successfully
✅ **Health Checks** - Check required modules at startup

### Performance Considerations

**Registration Performance:**
- Registration is fast (metadata storage only)
- No need to optimize unless registering 1000s of modules
- Consider lazy registration only if module initialization is expensive

**Best Practices:**
```scala
// ✅ Good: Register all at startup
modules.traverse(constellation.setModule)

// ❌ Avoid: Registering on every request
def handleRequest(moduleName: String) = {
  constellation.setModule(getModule(moduleName))  // Don't do this
}

// ✅ Good: Register once, use many times
val app = for {
  c <- ConstellationImpl.init
  _ <- modules.traverse(c.setModule)  // Once at startup

  // Use registered modules in requests
  _ <- handleRequests(c)
} yield ()
```

### Error Handling Best Practices

Handle registration errors gracefully:

```scala
def registerWithErrorHandling(
  constellation: Constellation,
  modules: List[Module.Uninitialized]
): IO[Unit] = {
  modules.zipWithIndex.traverse { case (module, idx) =>
    constellation.setModule(module).handleErrorWith { error =>
      IO.println(s"Failed to register module ${idx + 1}: ${module.spec.metadata.name}") *>
      IO.println(s"  Error: ${error.getMessage}") *>
      IO.raiseError(error)  // Re-raise to fail fast
    }
  }.void
}
```

### Testing Module Registration

Unit test your module registration:

```scala
import munit.CatsEffectSuite

class ModuleRegistrationTest extends CatsEffectSuite {
  test("all modules register successfully") {
    for {
      constellation <- ConstellationImpl.init
      _             <- MyLib.allModules.values.toList.traverse(constellation.setModule)
      specs         <- constellation.getModules

      // Verify count
      _ = assertEquals(specs.length, MyLib.allModules.size)

      // Verify all present
      names = specs.map(_.metadata.name).toSet
      _ = assert(names.contains("Uppercase"))
      _ = assert(names.contains("Lowercase"))
    } yield ()
  }

  test("can retrieve registered module by name") {
    for {
      constellation <- ConstellationImpl.init
      _             <- constellation.setModule(MyLib.uppercaseModule)
      maybeModule   <- constellation.getModuleByName("Uppercase")

      _ = assert(maybeModule.isDefined)
      _ = assertEquals(maybeModule.get.spec.metadata.name, "Uppercase")
    } yield ()
  }
}
```

### Monitoring Module Usage

Track which modules are actually used:

```scala
class MonitoredModuleRegistry(constellation: Constellation) {
  private val usageCounts = Ref.unsafe[IO, Map[String, Long]](Map.empty)

  def recordUsage(moduleName: String): IO[Unit] =
    usageCounts.update { counts =>
      counts.updated(moduleName, counts.getOrElse(moduleName, 0L) + 1)
    }

  def getUsageStats: IO[Map[String, Long]] =
    usageCounts.get

  def getUnusedModules: IO[List[String]] = {
    for {
      registered <- constellation.getModules
      usage      <- usageCounts.get

      unused = registered
        .map(_.metadata.name)
        .filterNot(usage.contains)
    } yield unused
  }
}
```

### Module Registry Patterns Summary

| Pattern | Use Case | Pros | Cons |
|---------|----------|------|------|
| **Single Registration** | Small apps, single module | Simple, direct | Doesn't scale |
| **Batch with `traverse`** | Medium apps (10-100 modules) | Type-safe, composable | Sequential |
| **Parallel Registration** | Large apps (100+ modules) | Fast startup | Requires thread safety |
| **Lazy Loading** | On-demand modules | Memory efficient | Complex, race conditions |
| **Plugin Architecture** | Extensible systems | Highly modular | Requires plugin API |
| **Namespace Prefixing** | Large teams | Prevents collisions | Verbose names |
| **Version Aliasing** | Multi-version support | Gradual migration | Complexity |

## Complete Examples

### Minimal Example

```scala
import cats.effect.{IO, IOApp, ExitCode}
import cats.implicits._
import io.constellation._

object MinimalApp extends IOApp {
  case class TextInput(text: String)
  case class TextOutput(result: String)

  val uppercaseModule = ModuleBuilder
    .metadata("Uppercase", "Convert to uppercase", 1, 0)
    .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
    .build

  def run(args: List[String]): IO[ExitCode] = {
    for {
      constellation <- ConstellationImpl.init
      _             <- constellation.setModule(uppercaseModule)
      specs         <- constellation.getModules
      _             <- IO.println(s"Registered ${specs.length} module(s)")
    } yield ExitCode.Success
  }
}
```

### Production Example

```scala
import cats.effect.{IO, IOApp, ExitCode, Resource}
import cats.implicits._
import io.constellation._
import io.constellation.lang.LangCompilerBuilder
import io.constellation.stdlib.StdLib

// 1. Define custom modules
object MyCompanyModules {
  // Product modules
  val getProduct = ModuleBuilder
    .metadata("myco.product.Get", "Fetch product by SKU", 1, 0)
    .tags("myco", "product")
    .implementation[ProductInput, ProductOutput] { ... }
    .build

  // Order modules
  val createOrder = ModuleBuilder
    .metadata("myco.order.Create", "Create new order", 1, 0)
    .tags("myco", "order")
    .implementation[OrderInput, OrderOutput] { ... }
    .build

  val all: List[Module.Uninitialized] = List(
    getProduct,
    createOrder
  )
}

// 2. Application setup
object MyApplication extends IOApp {
  def setupConstellation: IO[Constellation] = {
    for {
      // Initialize
      constellation <- ConstellationImpl.init

      // Register stdlib
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      // Register custom modules
      _ <- MyCompanyModules.all.traverse(constellation.setModule)

      // Verify registration
      specs <- constellation.getModules
      _ <- IO.println(s"✓ Registered ${specs.length} modules")

      // Health check
      _ <- checkRequiredModules(constellation, Set(
        "myco.product.Get",
        "myco.order.Create"
      )).flatMap {
        case Right(_) => IO.println("✓ All required modules present")
        case Left(missing) => IO.raiseError(new Exception(s"Missing: ${missing.mkString(", ")}"))
      }
    } yield constellation
  }

  def checkRequiredModules(
    constellation: Constellation,
    required: Set[String]
  ): IO[Either[List[String], Unit]] = {
    constellation.getModules.map { specs =>
      val registered = specs.map(_.metadata.name).toSet
      val missing = required -- registered
      if (missing.isEmpty) Right(()) else Left(missing.toList)
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    setupConstellation.flatMap { constellation =>
      // Run server or application logic
      IO.println("Application ready") *> IO.pure(ExitCode.Success)
    }
  }
}
```

### Multi-Environment Example

```scala
object EnvironmentAwareRegistry {
  sealed trait Environment
  case object Development extends Environment
  case object Staging extends Environment
  case object Production extends Environment

  def getEnvironment: IO[Environment] = {
    IO(System.getenv("APP_ENV")).map {
      case "production" => Production
      case "staging" => Staging
      case _ => Development
    }
  }

  def modulesForEnvironment(env: Environment): List[Module.Uninitialized] = {
    val baseModules = StdLib.allModules.values.toList

    val envSpecific = env match {
      case Development =>
        // Include debug modules in dev
        baseModules ++ DebugModules.all

      case Staging =>
        // Include monitoring in staging
        baseModules ++ MonitoringModules.all

      case Production =>
        // Production-optimized modules only
        baseModules.filterNot(_.spec.metadata.tags.contains("debug"))
    }

    envSpecific
  }

  def setup: IO[Constellation] = {
    for {
      env           <- getEnvironment
      modules       = modulesForEnvironment(env)
      constellation <- ConstellationImpl.init
      _             <- modules.traverse(constellation.setModule)
      _             <- IO.println(s"Registered ${modules.length} modules for $env")
    } yield constellation
  }
}
```

## Troubleshooting

### Common Issues

**Problem: Module not found in pipeline execution**
```
Error: Unknown module 'MyModule'
```

**Solution:** Verify module is registered before compilation:
```scala
for {
  c <- ConstellationImpl.init
  _ <- c.setModule(myModule)  // Must happen before compile

  // Now compile pipeline
  result <- compiler.compile(source, "my-dag")
} yield result
```

---

**Problem: Name collision during registration**
```
Error: Module 'Uppercase' already registered
```

**Solution:** Use unique names or namespacing:
```scala
// Option 1: Rename module
val uppercaseV2 = ModuleBuilder
  .metadata("UppercaseV2", "Description", 2, 0)
  .implementationPure[In, Out] { ... }
  .build

// Option 2: Use namespace
val uppercaseText = ModuleBuilder
  .metadata("text.Uppercase", "Description", 1, 0)
  .implementationPure[In, Out] { ... }
  .build
```

---

**Problem: Module registered but types don't match**
```
Error: Type mismatch for module 'MyModule'
```

**Solution:** Ensure FunctionSignature matches ModuleBuilder types:
```scala
// Module
case class MyInput(x: Long)
case class MyOutput(y: Long)
val module = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { ... }
  .build

// Signature MUST match
val signature = FunctionSignature(
  name = "MyModule",
  params = List("x" -> SemanticType.SInt),  // Must match MyInput.x
  returns = SemanticType.SInt,               // Must match MyOutput.y
  moduleName = "MyModule"
)
```

---

**Problem: Forgot to import `cats.implicits._`**
```
Error: value traverse is not a member of List[Module.Uninitialized]
```

**Solution:** Add the import:
```scala
import cats.implicits._  // Required for .traverse

modules.traverse(constellation.setModule)
```

## See Also

- **[ModuleBuilder Reference](../patterns/module-development.md)** - Building modules
- **[Type System Reference](../reference/type-syntax.md)** - Type signatures
- **[HTTP API Reference](../reference/http-api.md)** - Exposing modules via API
- **[Error Codes](../reference/error-codes.md)** - Registration error codes
