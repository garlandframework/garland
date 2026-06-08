# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Compile only
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -pl core -Dtest=ClassName

# Run Main directly (after build)
mvn exec:java -pl core -Dexec.mainClass=dev.garlandframework.Main
```

Java 21 is required. No external runtime dependencies beyond Maven.

## Architecture

This is a Maven multi-module project (`groupId: dev.garlandframework`). Currently one module: `core`.

### Core abstraction: Pipeline

`Pipeline<I, O>` is an immutable, type-safe step chain. Each call to `.then(Step<O, NO>)` returns a new `Pipeline<I, NO>` — the output type evolves with each step. Execution is eager and sequential via `.execute(input)`.

`Step<I, O>` is a `@FunctionalInterface` `(I input, PipelineContext ctx) -> O`. Steps can both transform data (return value) and share state sideways via the context.

`PipelineContext` is a stringly-typed `Map<String, Object>` bag that flows through all steps. Steps read and write to it by convention (e.g., `"method"`, `"url"`, `"body"`, `"response"`). There is no schema enforcement — callers must know what keys downstream steps expect.

### HTTP layer

`HttpSteps` provides four reusable `Step`-compatible methods meant to be composed in order:
1. `serialize` — serializes the input DTO to JSON and stores it in `ctx["body"]`
2. `call` — reads `ctx["method"]`, `ctx["url"]`, `ctx["body"]`; sends the request; stores the response in `ctx["response"]`
3. `validate` — asserts 2xx status
4. `deserialize` — reads `ctx["response"]` and unmarshals to a target type

HTTP config (`method`, `url`) must be placed into the context by a preceding step before `call` runs.

`HttpClientWrapper` is a thin wrapper around `java.net.http.HttpClient` — always sets `Content-Type: application/json`.

### Adding new step modules

New functional domains (DB, gRPC, messaging, etc.) follow the same pattern as `HttpSteps`: a plain class whose methods match `(Object input, PipelineContext ctx) -> Object` and can be passed as method references to `.then(...)`.
