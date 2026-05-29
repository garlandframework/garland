# Generate Project Commands

Analyse this project and generate MTO test generation command files tailored to it.
The output replaces manual authoring of gen commands — every class name, factory method, field constraint, and import must reflect the actual project, not a template.

**Usage:** `/gen-project-commands`

No arguments. Run once from the project root after `/setup` has generated the test infrastructure.

---

## Output structure

Generate **one set of four files per domain** found in the project, each domain in its own subdirectory. A domain is any named subpackage under the tests root (e.g. `users`, `orders`, `payments`).

```
.claude/commands/
  users/
    gen-endpoint-test.md     → /users/gen-endpoint-test
    gen-flow-test.md         → /users/gen-flow-test
    gen-component-test.md    → /users/gen-component-test
    gen-e2e-test.md          → /users/gen-e2e-test

  orders/
    gen-endpoint-test.md     → /orders/gen-endpoint-test
    gen-flow-test.md         → /orders/gen-flow-test
    gen-component-test.md    → /orders/gen-component-test
    gen-e2e-test.md          → /orders/gen-e2e-test
```

Each file contains only what is relevant to its domain. Do not mix factories, mappers, or constraints from different domains in the same file.

Re-running `/gen-project-commands` overwrites all existing domain directories and regenerates them from the current project state.

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
- Identify all domain subpackages (`endpoint`, `flow`, `component`, e2e class location)
- List every domain found (e.g. `users`, `orders`, `payments`)

### Cross-domain dependencies
For each domain, inspect its request/DTO classes and factory methods for fields that reference another domain's types or IDs.

Examples of cross-domain dependencies to detect:
- `OrderRequest.userId` — orders depends on users (needs a real user UUID)
- `PaymentRequest.orderId` — payments depends on orders
- A factory method that calls another domain's request factory internally

For each dependency found, record:
- Which domain depends on which
- Which specific factory method and DTO field create the dependency
- What the dependent domain needs to call to satisfy it (e.g. `TestUserRequests.createUser()` → returns `UserDto.uuid`)

---

## Step 2 — Generate command files

Write one set of four files per domain to `.claude/commands/<domain>/`. File names are always `gen-endpoint-test.md`, `gen-flow-test.md`, `gen-component-test.md`, `gen-e2e-test.md` — the domain is encoded in the directory, not the filename.

Each file must follow the structure below, filled with real project data for that domain only.

Every section marked `<!-- from analysis -->` must be populated from what you read in Step 1.
Never leave placeholder text, never invent class names, never copy from the demo project.

### Cross-domain section (add to every file where a dependency was detected)

Add this section after the test data factories section, omit it entirely if no cross-domain dependency exists:

```
## Cross-domain dependencies

<!-- from analysis: only include domains this domain actually depends on -->

### <DependencyDomain> (required for <field> field)
To satisfy <RequestClass>.<field>, create a <DependencyDomain> resource first:

    <FactoryMethod>()              // e.g. TestUserRequests.createUser()
    <DataFactory>.default<Type>()  // e.g. TestUsers.defaultUser()

Typical setup pattern:
    <DependencyDto> dep = Pipeline.given(<FactoryMethod>())
            .then(httpClient.makeCall(<status>, <DependencyDto>.class))
            .execute();
    // use dep.<getId>() when building the request for this domain
```

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

After writing all files, confirm:
- One set of four files was generated per domain found
- No file contains classes or factories from a different domain
- Every class name in the generated commands exists in the project
- Every method signature in request/data factories matches the actual code
- Every field in the constraints table has a corresponding annotation in the source
- Cross-domain sections appear only where a real dependency was detected — not speculatively
- Cross-domain factory methods referenced exist in the project
- No placeholder text remains in any file
- All files reference `llm.md` in the MTO repo root for universal framework rules
