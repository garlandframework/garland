# MTO Test Generation Rules

This file is read by LLMs generating tests with the Modular Test Orchestrator framework.
It contains rules that are non-obvious and that an LLM would get wrong without explicit guidance.
Project-specific rules (factories, mappers, client names) live in the gen-command files.

---

## 1. Core pipeline syntax

```java
// Basic form
Pipeline.given(input)
        .then(stepFunction)
        .execute();

// Capture result
UserDto created = Pipeline.given(request)
        .then(httpClient.makeCall(201, UserDto.class))
        .execute();

// Chain multiple steps
Pipeline.given(request)
        .then(httpClient.makeCall(201, UserDto.class))
        .then(UserTestMapper.toEntity())
        .then(postgresClient.findById())
        .execute();
```

`Step<I, O>` is `(I input, PipelineContext ctx) -> O`. Each step receives the previous step's output as its input. Type safety is enforced at compile time — if steps don't chain correctly, it won't compile.

`execute()` runs the chain and returns the final output. Checked exceptions are wrapped in `RuntimeException` automatically.

---

## 2. Fan-out verification — Verify.allOf()

Use `Verify.allOf()` whenever one HTTP response triggers multiple independent side-effects that all need to be verified.

```java
Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.makeCall(201, UserDto.class))
        .then(Verify.allOf(
                UserTestMapper.toEntity().andThen(postgresClient.findById()),
                UserTestMapper.toCreatedEvent().andThen(kafkaClient.consumeMatching(UserCreatedEvent.class)),
                UserTestMapper.dtoToCreatedProjectionDoc().andThen(mongoClient.findById())
        ))
        .execute();
```

**What allOf does:**
- Calls every branch with the same input (the previous step's output)
- Runs branches sequentially
- Collects **all** failures before throwing — one `AssertionError` reports every broken branch
- Returns the original input as passthrough, so you can chain steps after `allOf` if needed

**When to use allOf vs sequential chaining:**

| Situation | Use |
|---|---|
| Multiple systems receive the same trigger (create/update) | `Verify.allOf()` |
| Steps have a data dependency (output of step N feeds step N+1) | Sequential `.then()` chain |
| Post-delete verification of absence across systems | `Verify.allOf()` on the captured DTO |
| Single system assertion | Sequential `.then()` |

**Delete pattern** — the delete call returns `Void`, so fan out from the pre-delete DTO:

```java
UserDto created = Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.makeCall(201, UserDto.class))
        .execute();

Pipeline.given(TestUserRequests.deleteUser(created.getUuid()))
        .then(httpClient.makeCall(204, Void.class))
        .execute();

// Fan out from created — verify absence in all systems
Pipeline.given(created)
        .then(Verify.allOf(
                UserTestMapper.toEntity().andThen(postgresClient.notExistsById()),
                UserTestMapper.toDeletedEvent().andThen(kafkaClient.consumeMatching(UserDeletedEvent.class)),
                UserTestMapper.dtoToCreatedProjectionDoc().andThen(mongoClient.notExistsById())
        ))
        .execute();
```

---

## 3. Step.lift and mapper bridges

`Step.lift(Function<I, O> fn)` wraps a plain function into a `Step`. Use it when you need to compose a non-step mapping into a pipeline.

**Type inference warning:** If a mapper has overloaded methods (e.g. `toEntity(UserDto)`, `toEntity(AddressDto)`, `toEntity(CarDto)`), `lift(INSTANCE::toEntity)` will not compile — Java cannot resolve the overload. Always prefer the pre-defined static bridge methods on the mapper interface:

```java
// Correct — static bridge has an unambiguous type
UserTestMapper.toEntity()                  // Step<UserDto, UserEntity>
UserTestMapper.toCreatedEvent()            // Step<UserDto, UserCreatedEvent>
UserTestMapper.dtoToCreatedProjectionDoc() // Step<UserDto, UserProjectionDoc>

// Wrong — will fail to compile if toEntity() is overloaded
Step.lift(UserTestMapper.INSTANCE::toEntity)
```

Bridges are chained with `.andThen()` to build branch expressions for `allOf`:

```java
UserTestMapper.toPlacedEvent()
        .andThen(OrderTestMapper.toProjectionDoc())
        .andThen(mongoClient.findById())
```

---

## 4. Test DTO design — two families

Tests use **two separate DTO families**. Never use the same DTO class for both purposes.

### Happy-path DTOs
Match production DTO types exactly (`UUID`, `int`, `@Email String`, enums, etc.). Used for:
- All positive integration tests
- Pipeline data flowing through HTTP → DB → Kafka → MongoDB
- Object Mother / factory builders populate with Datafaker defaults

### Adversarial DTOs
Use `Object` fields (or wider types) for fields under test. Jackson serializes based on runtime type, so `Object carCount = 2.4` sends a JSON float even though the production field is `int`. Used for:
- Validation boundary tests (400 responses)
- Type violation tests (wrong type, null where required, over-length strings)

Field type mapping for adversarial DTOs:

| Production type | Adversarial type | Example test values |
|---|---|---|
| `int` / `long` | `Object` | `2.4`, `-999999`, `"abc"`, `null` |
| `@Email String` | `String` (no constraint) | `"not-an-email"`, `""` |
| `StatusEnum` | `Object` | `"INVALID_VALUE"`, `123` |
| `UUID` | `Object` or `String` | `"not-a-uuid"`, `""` |
| `@Size(max=N) String` | `String` (no constraint) | `"a".repeat(N+1)` |
| `@NotNull` field | nullable | `null` |

Adversarial builders have **no Datafaker defaults** — every invalid value is explicit. A test should be unambiguous about which field it is breaking.

---

## 5. Test data factory pattern (Object Mother)

Factories follow the Object Mother pattern. Naming convention: `Test{Entity}` (not `*Repository`, not `*TestData`, not a god class).

```java
// Full valid object with Datafaker values
TestUsers.defaultUser()

// Only required fields — optional fields null/empty
TestUsers.requiredFieldsOnlyUser()

// Pre-populated builder for field-level override
TestUsers.builder().name("Alice").build()
```

Rules:
- `defaultUser()` delegates to `builder().build()` — no duplication
- Factories are hierarchical: `TestUsers` internally calls `TestAddresses` and `TestCars`
- `TestUserRequests` is separate from `TestUsers` — it wraps `HttpCallRequest<UserDto>` and must not be conflated with the data factory

---

## 6. DB and MongoDB assertion rules

```java
// Asserts record/document exists AND matches expected — throws if absent or mismatched
postgresClient.findById()
mongoClient.findById()
mongoClient.findById(Duration)              // override default tolerance for this call

// Asserts exactly one match — throws if 0 or >1 results
postgresClient.findByFields()
mongoClient.findByFields()

// Returns the count of matching records/documents
postgresClient.countByFields()
mongoClient.countByFields()

// Asserts absence — throws if present
postgresClient.notExistsById()
mongoClient.notExistsById()
```

**`findByFields()` is strict** — it throws if more than one record matches. Use it only when you expect exactly one result. For verifying a count, always use `countByFields()` chained with `Verify.equalTo(NL)`:

```java
Car template = new Car(null, null, "Toyota", null); // only non-null fields used as filter

Pipeline.given(template)
        .then(db.countByFields())
        .then(Verify.equalTo(5L))
        .execute();
```

Never unwrap the count outside the pipeline and assert with a bare `assertThat`.

**`matchingNonNull` semantics** — `findById`, `findByFields`, `consumeMatching` all compare using `matchingNonNull`, which ignores `null` fields in the expected object. Set a field to `null` to skip asserting it. Set it to a value to assert it exactly. This is how partial matching works — pass an object with only the fields you care about non-null.

---

## 7. Temporal tolerance

Two separate situations require temporal tolerance:

### Storage precision truncation
Each storage technology truncates `Instant` precision: MongoDB to milliseconds, PostgreSQL to microseconds. Rather than annotating every assertion site, set a client-level default in `BaseTest` via `withTemporalTolerance()`:

```java
postgresClient    = new PostgresTestClient(postgres, retryConfig)
        .withTemporalTolerance(Duration.ofNanos(1000)); // absorbs Postgres µs truncation

mongoClient = new MongoTestClient(mongo, retryConfig)
        .withTemporalTolerance(Duration.ofMillis(1));   // absorbs MongoDB ms truncation

kafkaClient = new KafkaTestClient(config, retryConfig)
        .withTemporalTolerance(Duration.ofMillis(1));   // absorbs DB-echo truncation in events
```

With defaults set, `findById()` and `consumeMatching()` apply the tolerance automatically — no per-call annotation needed. Use the explicit `findById(Duration)` / `consumeMatching(Class, Duration)` overload only when a specific assertion needs a higher tolerance than the default.

### Service-generated timestamps
When a service sets a timestamp internally (e.g. `eventTimestamp = Instant.now()`), capture the test start time and pass it as the expected value with a tolerance equal to the maximum acceptable processing delay:

```java
Instant testStart = Instant.now();

OrderPlacedEvent expected = new OrderPlacedEvent(orderId, ..., testStart);
Pipeline.given(expected)
        .then(orderKafkaClient.consumeMatching(OrderPlacedEvent.class, Duration.ofMinutes(2)))
        .execute();
```

This asserts the timestamp is **present and within the SLA window** — if processing takes longer than 2 minutes, the test fails. Prefer this over passing `null` for timestamp fields: `null` skips the assertion entirely, which means a missing or corrupted timestamp would silently pass.

---

## 8. Pipeline = path through system graph

Each test pipeline represents one path through the system's data flow graph:
- **Nodes** = services, Kafka topics, databases
- **Edges** = data flows between them

A well-designed test suite covers every edge at least once:

| Test type | Path length | Example |
|---|---|---|
| Endpoint test | 1 edge: client → service | `POST /users` returns 201 with correct body |
| Component test | 2-3 edges: partial slice | HTTP → Postgres → Kafka (stops at Kafka) |
| E2E test | Full path | HTTP → Postgres → Kafka → MongoDB |

**One pipeline per logical operation.** A "create user then delete user" test uses two pipelines: one for create, one for delete. Do not chain unrelated operations in a single pipeline.

**State flows via local variables.** Capture the output of a setup pipeline into a typed local variable and feed it into the next pipeline. Never use `PipelineContext` to pass state between test methods or between unrelated pipelines.

---

## 10. Auth — token acquisition and per-call overrides

### How BaseTest wires auth

Token acquisition lives in `@BeforeSuite`. The pattern is always the same — unauthenticated client calls the login endpoint, gets a token, then the shared client is replaced with an authenticated version:

```java
httpClient = new HttpTestClient(RetryConfig.of(3, Duration.ofSeconds(2)));

TokenDto tokenDto = Pipeline.given(TestAuthRequests.login())
        .then(httpClient.makeCall(200, TokenDto.class))
        .execute();

httpClient = httpClient.withBearer(tokenDto.token());
```

All tests in the suite share the authenticated `httpClient`. You do not acquire a token per test or per class.

In projects using OAuth2 client credentials the pattern is identical — only the URL and request body of the login pipeline change.

### withBearer / withoutHeader / withApiKey

`HttpTestClient` is immutable. Every `with*` / `without*` call returns a **new instance** — the shared `httpClient` is never mutated:

```java
httpClient.withBearer("other-token")             // new instance, adds/replaces Authorization
httpClient.withoutHeader("Authorization")         // new instance, removes Authorization
httpClient.withApiKey("X-Api-Key", "key")         // new instance, adds header
```

Use these inline in a single `.then(...)` call for negative auth tests. Never reassign `httpClient` inside a test method — it is shared across the entire suite.

### Negative auth tests

```java
// No token → 401
Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.withoutHeader("Authorization")
                .makeCall(new HttpCallResponse<>(401, Map.of(), ErrorDto.withStatus(401))))
        .execute();

// Invalid JWT → 401
Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.withBearer("not-a-valid-jwt")
                .makeCall(new HttpCallResponse<>(401, Map.of(), ErrorDto.withStatus(401))))
        .execute();

// Wrong credentials on login endpoint → 401
Pipeline.given(TestAuthRequests.login("admin", "wrong-password"))
        .then(httpClient.makeCall(new HttpCallResponse<>(401, Map.of(), ErrorDto.withStatus(401))))
        .execute();
```

The login endpoint is excluded from token validation — using `httpClient` (which carries a bearer token) for login calls is harmless.

---

## 9. Anti-patterns

**Do not chain DB + Kafka sequentially when they are independent.** The old pattern `→ toEntity() → postgresClient.findById() → entityToEvent() → kafkaClient.consumeMatching()` forces sequential execution and hides the fact that these are parallel side-effects. Use `Verify.allOf()` instead.

**Do not use separate `Pipeline.given()` blocks for post-action assertions.** Three separate pipelines for DB / Kafka / Mongo checks after a create is harder to read and fails fast on the first failure. `Verify.allOf()` collects all failures in one report.

**Do not assert timestamps without tolerance.** An assertion on `Instant` without a tolerance is fragile — it will fail due to storage truncation or clock precision. Set default tolerances via `withTemporalTolerance()` in `BaseTest` so every `findById()` and `consumeMatching()` call inherits them. Use the explicit `Duration` overloads only for SLA-style assertions on service-generated timestamps.

**Do not use raw `assertThat` for values that come out of pipelines.** Keep assertions inside the pipeline using `Verify.matching`, `Verify.equalTo`, `Verify.containsAll`. Use `assertThat` only for assertions on data that lives entirely outside any pipeline (e.g. the `doesNotContain` list check after `Pipeline.execute()`).

**Do not use `Step.lift(INSTANCE::overloadedMethod)`.** If the mapper method has multiple overloads, type inference fails. Use the pre-defined static bridge on the mapper interface instead.

**Do not use `PLACEHOLDER_USER_ID` in happy-path tests that persist to the database.** The placeholder is only valid for validation (400) tests where the service rejects the request before hitting the DB. Happy-path tests must create the referenced entity in a setup pipeline first.

---

## 11. Test resource cleanup — API-only, never direct DB

Resources created by tests must be cleaned up through the application's own API — never by truncating or deleting records directly in the database.

**Why direct DB truncation is dangerous:** Other systems (Kafka, MongoDB projections, caches) are not notified of deletions made outside the application. Inconsistent state accumulates and causes spurious test failures that are not caused by bugs in the code under test.

**The pattern:** `BaseTest` provides step helpers that track created resource IDs. `@AfterMethod(alwaysRun = true)` calls the delete/cancel endpoint for each tracked resource after every test method, including on failure:

```java
UserDto user = Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.makeCall(201, UserDto.class))
        .then(trackUser())   // registers UUID; @AfterMethod deletes via API
        .execute();
```

Cleanup failures are caught as `Throwable` (not `Exception`) and logged as warnings — an `AssertionError` from a 404 (resource already deleted by the test itself) must not prevent remaining cleanup from running.

**Hybrid strategy:** Some resources have no DELETE endpoint (e.g. orders can only be cancelled). Use a separate tracker (e.g. `trackOrder()`) that sends the appropriate cancellation request. The principle is the same: all cleanup goes through the API.

**Always track, even if the test deletes the resource itself.** If the test fails before its own cleanup step, the resource would be stranded without the tracker.
