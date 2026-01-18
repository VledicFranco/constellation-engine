# Core Features

Constellation must make machine learning practicioners develop and deploy their inference path pipelines as fast as possible, the objective is reach experimentation in record time.

Forget time to market, we aim for minimal time to experiment!



## Time to Experiment
* (constellation-lang) StdLib with a full toolchain for machine learning pipelines.
* (constellation-modules) Libraries for Scala modules (bigquery, bigtable, embedding storage, ANN, etc.)
* Hotdeploy static modules.
* Test your modules with replayed logged values.

## Expressiveness
* Orchestration Algebra (conditional execution, retry semantics and failure tolerance, constellation-lang level caching, contellation-lang level batch management)
* Data Manipulation Algebra (type algebra with performance in mind)

## Performance
* Concurrency by default, everything that can fire does fire, DAGs give safety and orchestration performance.
* Compile to metals, with performant ML and data manipulation algorithms.
* Data Structures made for massive prossesing.
* Fine control over batching.
* Native batching
* Native caching

## Reliability
* Graph based tracing. Requests sampler that logs DAG data signatures.
* 