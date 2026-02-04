# Constellation Engine - Example Application

This is a **complete example application** that demonstrates how to use the Constellation Engine library to build a custom data processing pipeline with HTTP API. It serves as both a tutorial and a template for building your own applications.

## What This Example Demonstrates

As a library user, you'll learn how to:

1. ✅ **Define custom domain-specific modules** using `ModuleBuilder`
2. ✅ **Register modules** with the Constellation engine
3. ✅ **Compose processing pipelines** using constellation-lang DSL
4. ✅ **Expose your pipelines** via HTTP API
5. ✅ **Run and test** your application

## Dependencies

To use Constellation Engine as a library in your own project, add the dependencies from [Maven Central](https://central.sonatype.com/):

```scala
val constellationVersion = "0.3.1"

libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-runtime"       % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-compiler" % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-stdlib"   % constellationVersion,
  "io.github.vledicfranco" %% "constellation-http-api"      % constellationVersion
)
```

## Quick Start

### 1. Start the Application

```bash
sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
```

You should see:
```
🚀 Initializing Constellation Engine...
📦 Registering custom modules...

✅ Available custom modules:
   • Uppercase (v1.0)
   • Lowercase (v1.0)
   • Trim (v1.0)
   • Replace (v1.0)
   • WordCount (v1.0)
   • TextLength (v1.0)
   • Contains (v1.0)
   • SplitLines (v1.0)
   • Split (v1.0)
   • SumList (v1.0)
   • Average (v1.0)
   • Max (v1.0)
   • Min (v1.0)
   • FilterGreaterThan (v1.0)
   • MultiplyEach (v1.0)
   • Range (v1.0)
   • FormatNumber (v1.0)

🌐 Starting HTTP API server on port 8080...
```

### 2. List Available Modules

```bash
curl http://localhost:8080/modules | jq
```

### 3. Compile a Pipeline

```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\ncleaned = Trim(text)\nuppercased = Uppercase(cleaned)\nout uppercased",
    "dagName": "text-cleanup"
  }'
```

### 4. List Compiled DAGs

```bash
curl http://localhost:8080/dags | jq
```

## Custom Modules Provided

### Text Processing Modules

| Module | Description | Inputs | Outputs |
|--------|-------------|--------|---------|
| `Uppercase` | Convert to uppercase | text: String | result: String |
| `Lowercase` | Convert to lowercase | text: String | result: String |
| `Trim` | Remove whitespace | text: String | result: String |
| `Replace` | Replace substring | text, find, replace: String | result: String |
| `WordCount` | Count words | text: String | count: Int |
| `TextLength` | Get text length | text: String | length: Int |
| `Contains` | Check substring | text, substring: String | contains: Boolean |
| `SplitLines` | Split by newlines | text: String | lines: List\<String\> |
| `Split` | Split by delimiter | text, delimiter: String | parts: List\<String\> |

### Data Processing Modules

| Module | Description | Inputs | Outputs |
|--------|-------------|--------|---------|
| `SumList` | Sum numbers | numbers: List\<Int\> | total: Int |
| `Average` | Calculate average | numbers: List\<Int\> | average: Float |
| `Max` | Find maximum | numbers: List\<Int\> | max: Int |
| `Min` | Find minimum | numbers: List\<Int\> | min: Int |
| `FilterGreaterThan` | Filter by threshold | numbers: List\<Int\>, threshold: Int | filtered: List\<Int\> |
| `MultiplyEach` | Multiply each number | numbers: List\<Int\>, multiplier: Int | result: List\<Int\> |
| `Range` | Generate range | start, end: Int | numbers: List\<Int\> |
| `FormatNumber` | Format with commas | number: Int | formatted: String |

## Example Pipelines

### Example 1: Text Processing

```constellation
# Input: raw text from user
in rawText: String

# Clean and transform
cleaned = Trim(rawText)
uppercased = Uppercase(cleaned)

# Analyze
stats = WordCount(uppercased)

# Output
out uppercased
out stats
```

**Compile:**
```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in rawText: String\ncleaned = Trim(rawText)\nuppercased = Uppercase(cleaned)\nstats = WordCount(uppercased)\nout uppercased\nout stats",
    "dagName": "text-pipeline"
  }'
```

### Example 2: Data Analysis

```constellation
# Input: list of numbers
in numbers: List<Int>

# Calculate statistics
total = SumList(numbers)
avg = Average(numbers)
maximum = Max(numbers)
minimum = Min(numbers)

# Output all stats
out total
out avg
out maximum
out minimum
```

### Example 3: Filter and Transform

```constellation
# Inputs
in numbers: List<Int>
in threshold: Int
in multiplier: Int

# Filter numbers greater than threshold
filtered = FilterGreaterThan(numbers, threshold)

# Multiply each number
transformed = MultiplyEach(filtered, multiplier)

# Calculate sum
result = SumList(transformed)

# Outputs
out result
out filtered
out transformed
```

### Example 4: Text Search

```constellation
# Inputs
in document: String
in searchTerm: String

# Prepare text
trimmed = Trim(document)
lowercased = Lowercase(trimmed)

# Search
found = Contains(lowercased, searchTerm)

# Analyze
wordCount = WordCount(trimmed)
length = TextLength(trimmed)

# Outputs
out found
out wordCount
out length
```

## How to Build Your Own Application

### Step 1: Define Your Custom Modules

Create a Scala object with your domain-specific modules:

```scala
package com.mycompany.myapp.modules

import io.constellation._

object MyModules {

  // Example: Simple transformation module
  case class Input(value: String)
  case class Output(result: String)

  val myModule: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "MyModule",
      description = "Does something useful",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("my-domain")
    .implementationPure[Input, Output] { input =>
      Output(input.value.toUpperCase)
    }
    .build

  val all: List[Module.Uninitialized] = List(myModule)
}
```

### Step 2: Create Your Application

```scala
package com.mycompany.myapp

import cats.effect.{IO, IOApp}
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer
import com.mycompany.myapp.modules.MyModules

object MyApp extends IOApp.Simple {

  def run: IO[Unit] = {
    for {
      // Initialize engine
      constellation <- ConstellationImpl.init

      // Register your modules
      _ <- MyModules.all.traverse(constellation.setModule)

      // Create compiler
      compiler = LangCompiler.empty

      // Start HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withPort(8080)
        .run
    } yield ()
  }
}
```

### Step 3: Define Your Pipelines

Create `.cst` files with your constellation-lang pipelines:

```constellation
# my-pipeline.cst
in input: String
processed = MyModule(input)
out processed
```

### Step 4: Run and Test

```bash
# Start your app
sbt "myApp/run"

# Compile pipeline
curl -X POST http://localhost:8080/compile \
  -d '{"source": "...", "dagName": "my-pipeline"}'

# Execute pipeline
curl -X POST http://localhost:8080/execute \
  -d '{"dagName": "my-pipeline", "inputs": {...}}'
```

## Module Builder API Tips

### Pure Functions (Recommended for Most Cases)

```scala
val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[Input, Output] { input =>
    // Pure transformation
    Output(input.value.process())
  }
  .build
```

### IO-Based Functions (For Side Effects)

```scala
val asyncModule = ModuleBuilder
  .metadata("AsyncModule", "Description", 1, 0)
  .implementation[Input, Output] { input =>
    IO {
      // Can perform IO operations
      val result = callExternalAPI(input)
      Output(result)
    }
  }
  .build
```

### Configuration Options

```scala
val configuredModule = ModuleBuilder
  .metadata("ConfiguredModule", "Description", 1, 0)
  .tags("tag1", "tag2")
  .inputsTimeout(5.seconds)
  .moduleTimeout(10.seconds)
  .implementationPure[Input, Output](transform)
  .build
```

## Constellation-Lang Syntax

```constellation
# Define inputs
in myInput: String
in myNumber: Int
in myList: List<String>

# Call modules (module names match Scala module names)
result1 = MyModule(myInput)
result2 = AnotherModule(myNumber)

# Multiple inputs
combined = CombineModule(result1, result2)

# Output results
out combined
out result1
```

## Project Structure

```
modules/example-app/
├── src/main/scala/io/constellation/examples/app/
│   ├── modules/
│   │   ├── TextModules.scala      # Text processing modules
│   │   └── DataModules.scala      # Data processing modules
│   └── TextProcessingApp.scala     # Main application
├── examples/
│   ├── text-processing.cst         # Example pipeline 1
│   ├── data-analysis.cst           # Example pipeline 2
│   ├── filter-and-transform.cst    # Example pipeline 3
│   └── text-search.cst             # Example pipeline 4
├── build.sbt
└── README.md
```

## Next Steps

1. **Explore the code**: Read through `TextModules.scala` and `DataModules.scala` to see how modules are defined
2. **Try the examples**: Run the example pipelines in the `examples/` directory
3. **Build your own**: Create your own modules and pipelines for your domain
4. **Extend**: Add more features like persistence, authentication, or monitoring

## FAQ

**Q: How do I handle errors in my modules?**
A: Use `IO.raiseError` in IO-based implementations, or throw exceptions in pure implementations. The engine will handle them appropriately.

**Q: Can I use external libraries in my modules?**
A: Yes! Add dependencies to your `build.sbt` and use them in your module implementations.

**Q: How do I test my modules?**
A: Use ScalaTest to unit test your module logic separately from the engine integration.

**Q: Can I use the standard library functions?**
A: Yes! Instead of `LangCompiler.empty`, use `StdLib.compiler` to get all standard library functions.

**Q: How do I deploy this?**
A: Package your application with `sbt assembly` or create a Docker image. The HTTP server can run standalone.

## Support

For more information:
- Check the main Constellation Engine documentation
- Review the API documentation for `ModuleBuilder`
- Look at the source code for this example app
