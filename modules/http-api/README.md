# Constellation HTTP API

HTTP server module for the Constellation Engine, providing a REST API for compiling constellation-lang programs, managing DAGs and modules, and executing computational pipelines.

## Features

- **Compilation API**: Compile constellation-lang source code to executable DAGs
- **Execution API**: Execute compiled DAGs with JSON inputs
- **DAG Management**: List, retrieve, and manage computational DAGs
- **Module Management**: List available modules and their specifications
- **Health Monitoring**: Simple health check endpoint

## Getting Started

### Running the Demo Server

The module includes a demo server with the standard library pre-loaded:

```scala
import io.constellation.http.examples.DemoServer

// Run with sbt
sbt "httpApi/runMain io.constellation.http.examples.DemoServer"
```

The server will start on `http://localhost:8080`

### Creating a Custom Server

```scala
import cats.effect.{IO, IOApp}
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer
import io.constellation.stdlib.StdLib

object MyServer extends IOApp.Simple {
  def run: IO[Unit] = {
    for {
      // Initialize the constellation engine
      constellation <- ConstellationImpl.init

      // Create a compiler (with or without stdlib)
      compiler = StdLib.compiler

      // Start the HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .run
    } yield ()
  }
}
```

## API Endpoints

### Health Check
```bash
GET /health
```

Returns server status.

**Response:**
```json
{
  "status": "ok"
}
```

### Compile Program
```bash
POST /compile
Content-Type: application/json
```

Compile constellation-lang source code into a DAG.

**Request:**
```json
{
  "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
  "dagName": "addition-dag"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "dagName": "addition-dag",
  "errors": []
}
```

**Error Response (400):**
```json
{
  "success": false,
  "dagName": null,
  "errors": ["Undefined variable: x"]
}
```

### Execute DAG
```bash
POST /execute
Content-Type: application/json
```

Execute a compiled DAG with inputs.

**Request:**
```json
{
  "dagName": "addition-dag",
  "inputs": {
    "a": 10,
    "b": 20
  }
}
```

**Response:**
```json
{
  "success": true,
  "outputs": {},
  "error": null
}
```

### List DAGs
```bash
GET /dags
```

List all available DAGs.

**Response:**
```json
{
  "dags": {
    "addition-dag": {
      "name": "addition-dag",
      "description": "",
      "tags": [],
      "majorVersion": 0,
      "minorVersion": 1
    }
  }
}
```

### Get DAG
```bash
GET /dags/:dagName
```

Get a specific DAG by name.

**Response (200):**
```json
{
  "name": "addition-dag",
  "metadata": {
    "name": "addition-dag",
    "description": "",
    "tags": [],
    "majorVersion": 0,
    "minorVersion": 1
  }
}
```

**Response (404):**
```json
{
  "error": "DagNotFound",
  "message": "DAG 'non-existent' not found"
}
```

### List Modules
```bash
GET /modules
```

List all available modules.

**Response:**
```json
{
  "modules": [
    {
      "name": "PlusOne",
      "description": "Adds one to the input",
      "version": "0.1",
      "inputs": {
        "n": "CInt"
      },
      "outputs": {
        "nPlusOne": "CInt"
      }
    }
  ]
}
```

## Example Usage

### Using curl

```bash
# Health check
curl http://localhost:8080/health

# Compile a program
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
    "dagName": "addition-dag"
  }'

# List DAGs
curl http://localhost:8080/dags

# Get a specific DAG
curl http://localhost:8080/dags/addition-dag

# List modules
curl http://localhost:8080/modules
```

### Using httpie

```bash
# Compile a program
http POST localhost:8080/compile \
  source="in x: Int\nout x" \
  dagName="simple-dag"

# List DAGs
http GET localhost:8080/dags
```

## Configuration

The server can be configured using the builder pattern:

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withHost("127.0.0.1")    // Default: "0.0.0.0"
  .withPort(9000)            // Default: 8080
  .build
```

## Dependencies

- **http4s-ember-server**: HTTP server
- **http4s-dsl**: DSL for defining routes
- **http4s-circe**: JSON encoding/decoding
- **circe**: JSON library
- **logback**: Logging

## Testing

Run the test suite:

```bash
sbt httpApi/test
```

The tests cover:
- Health check endpoint
- Program compilation (success and error cases)
- DAG listing and retrieval
- Module listing
- Error handling

## Future Enhancements

- Full DAG execution with JSON to CValue conversion
- Streaming execution results
- WebSocket support for real-time execution monitoring
- Authentication and authorization
- Rate limiting
- Metrics and observability endpoints
- OpenAPI/Swagger documentation
