# Generate Project Commands

Analyse this project and generate all four MTO test generation command files tailored to it.
The output replaces manual authoring of gen commands — every class name, factory method, field constraint, and import must reflect the actual project, not a template.

**Usage:** `/gen-project-commands`

No arguments. Run once from the project root after `/setup` has generated the test infrastructure.

---

## Step 1 — Analyse the project

Before generating anything, read the following. Do not guess — read the actual files.

### Test infrastructure
- `BaseTest` — what clients are declared (`httpClient`, `dbClient`, `kafkaClient`, `mongoClient`), what their types are, what retry configs are used
- Request factory classes (`TestXxxRequests`) — every method signature, URL, HTTP method, expected status
- Test data factory classes (`TestXxx`, `TestAddresses`, `TestCars`, etc.) — every static method and builder pattern
- Test mapper class (`XxxTestMapper`) — every method and its direction (e.g. `UserDto → UserEntity`, `UserEntity → UserCreatedEvent`)
- `Connections` or equivalent — base URLs, topic names, database names
- Error DTO classes — the exact class names and factory methods used for 400, 404, 409 responses

### Source code
- Controllers — every endpoint: HTTP method, path, request body type, response body type, path variables
- Request / DTO classes with validation annotations — extract every `@NotBlank`, `@NotNull`, `@Size(max=N)`, `@Positive`, `@Min`, `@Max`, `@Valid` annotation per field, including nested objects and collections
- Entity classes — field names and types (for DB assertion context)
- Event classes — field names (for Kafka assertion context)
- Document classes — field names (for MongoDB assertion context)
- Kafka topic names — from `@KafkaListener` annotations or event publisher classes

### Project layout
- Identify the test module root package
- Identify existing subpackages (`endpoint`, `flow`, `component`, e2e class location)
- Identify the domain name (e.g. `users`, `orders`)

---

## Step 2 — Generate four command files

Write all four files to `.claude/commands/` in the project. Each file must follow the structure below, filled with real project data.

Every section marked `<!-- from analysis -->` must be populated from what you read in Step 1.
Never leave placeholder text, never invent class names, never copy from the demo project.

---

### File 1: `gen-endpoint-test.md`

```
# Generate Endpoint Test

Generate or extend a TestNG endpoint test class for this project.

**Usage:** `/gen-endpoint-test <description of what to generate>`

Examples:
<!-- from analysis: 2-3 examples using real class names from this project -->

Universal framework rules are in `llm.md` in the MTO repo root.
The rules below are specific to this project.

---

## Project layout

<!-- from analysis: real package paths and test class locations -->

## Clients (from BaseTest)

| Field | Type | Purpose |
|---|---|---|
<!-- from analysis: real client fields and types from BaseTest -->

## Request factories

<!-- from analysis: every method in the real TestXxxRequests class with full signature -->

## Test data factories

<!-- from analysis: every factory method and builder in real TestXxx factory classes -->

## Mappers (pipeline steps)

<!-- from analysis: every pipeline-compatible method in XxxTestMapper -->

## Error response DTOs

<!-- from analysis: real error DTO class names, factory methods, and when to use each -->

## Imports reference

<!-- from analysis: full import list with real package names -->

## Field constraints

| Field | Constraint | Blank test value | Null test value | Size test value |
|---|---|---|---|---|
<!-- from analysis: every validated field from request/DTO classes with real constraint values -->
<!-- for nested objects show path: address.street, cars[0].plateNumber -->
<!-- for optional fields note they are optional -->

---

Now generate the tests described in the argument: **$ARGUMENTS**
```

---

### File 2: `gen-flow-test.md`

```
# Generate Flow Test

Generate a TestNG flow test class for this project.
Flow tests verify state transitions and consistency across a sequence of endpoint calls within a single service.

**Usage:** `/gen-flow-test <description of what to generate>`

Examples:
<!-- from analysis: 2-3 examples using real domain name -->

Universal framework rules are in `llm.md` in the MTO repo root.
The rules below are specific to this project.

---

## Project layout

<!-- from analysis: real package paths -->

## Package

<!-- from analysis: real flow test package, e.g. org.example.tests.orders.flow -->

## Test class name convention

<!-- from analysis: e.g. OrderFlowTest -->

## Available request factories

<!-- from analysis: every method in TestXxxRequests -->

## Imports reference

<!-- from analysis: full import list with real package names -->

---

Now generate the flow tests described in the argument: **$ARGUMENTS**
```

---

### File 3: `gen-component-test.md`

```
# Generate Component Test

Generate a TestNG component test class for this project.
Component tests verify a vertical slice from a specific entry point without requiring the full stack.

**Usage:** `/gen-component-test <description of what to generate>`

Examples:
<!-- from analysis: 2-3 examples using real slice names -->

Universal framework rules are in `llm.md` in the MTO repo root.
The rules below are specific to this project.

---

## Project layout

<!-- from analysis: real package paths -->

## Package

<!-- from analysis: real component test package -->

## Two component slices

### Slice 1 — HTTP entry point
<!-- from analysis:
     - real test class name (e.g. OrderApiToKafkaTest)
     - real pipeline example using actual request factory, mapper steps, event class
     - which Kafka topic the service publishes to
-->

### Slice 2 — Event entry point
<!-- from analysis:
     - real test class name (e.g. KafkaToOrderProjectionTest)
     - real event class name
     - real TestEvents factory method
     - real projection document class
     - real mapper method for event → projection doc
     - which MongoDB collection
-->

## Test data factories

<!-- from analysis: TestEvents factory and any other event-building utilities -->

## Imports reference

<!-- from analysis: full import list with real package names -->

---

Now generate the component tests described in the argument: **$ARGUMENTS**
```

---

### File 4: `gen-e2e-test.md`

```
# Generate End-to-End Test

Generate or extend a TestNG end-to-end test class for this project.
E2e tests verify the full cross-system chain across all infrastructure systems.

**Usage:** `/gen-e2e-test <description of what to generate>`

Examples:
<!-- from analysis: 2-3 examples using real domain name -->

Universal framework rules are in `llm.md` in the MTO repo root.
The rules below are specific to this project.

---

## Project layout

<!-- from analysis: real package paths, real e2e class name and location -->

## System topology

<!-- from analysis: describe the actual chain, e.g.:
     HTTP → user-service → Postgres → Kafka (user.created) → projection-service → MongoDB
     Only include systems actually present in this project -->

## Clients (from BaseTest)

| Field | Type | Purpose |
|---|---|---|
<!-- from analysis: real client fields with Kafka topics listed -->

## Mapper steps (pipeline-compatible)

<!-- from analysis: every pipeline-compatible step in XxxTestMapper, direction noted -->

## Direct mapper calls

<!-- from analysis: INSTANCE.toXxx() methods used for pre-computing state before destructive operations -->

## Request factories

<!-- from analysis: relevant request factory methods for e2e flows -->

## Test data factories

<!-- from analysis: relevant test data factory methods -->

## Imports reference

<!-- from analysis: full import list with real package names -->

---

Now generate the e2e tests described in the argument: **$ARGUMENTS**
```

---

## Step 3 — Verify

After writing all four files, confirm:
- Every class name in the generated commands exists in the project
- Every method signature in request/data factories matches the actual code
- Every field in the constraints table has a corresponding annotation in the source
- No placeholder text remains in any file
- All four files reference `llm.md` in the MTO repo root for universal framework rules
