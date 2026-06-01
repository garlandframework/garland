# MTO — LLM Rules

Rules for generating tests with the Modular Test Orchestrator framework.
Copy the relevant sections into your project's `CLAUDE.md` or skill file.

---

## Core rule: Pipeline is the only way to write a test

Every test must use `Pipeline.given(...).then(...).execute()`.
Never call clients, steps, or assertions directly outside a pipeline.

```java
@Test
public void createItem_persistedInDb() throws Exception {
    Pipeline.given(request)
            .then(httpClient.makeCall(201, ItemDto.class))
            .then(Verify.matching(expected))
            .then(mapper.toEntity())
            .then(dbClient.findById())
            .execute();
}
```

`Pipeline.given(input)` binds the input and starts the chain.
Each `.then(StepFunction<O, NO>)` transforms the value flowing through.
`.execute()` runs all steps eagerly and sequentially.
The output type changes with each step — the compiler enforces the chain is valid.

---

## StepFunction

```java
@FunctionalInterface
(I input, PipelineContext ctx) -> O
```

Steps can transform data (return value) and share state via `ctx`.
Method references that match this signature can be passed directly to `.then(...)`.

---

## HttpTestClient

```java
// Assert status, deserialize body to R
httpClient.makeCall(int expectedStatus, Class<R> responseType)

// Same but for generic types (List<T>, Page<T>, etc.) — use TypeReference to preserve type at runtime
httpClient.makeCall(int expectedStatus, new TypeReference<List<ItemDto>>() {})

// Assert status, headers, and body match expected response object
httpClient.makeCall(HttpCallResponse<R> expected)

// Same with temporal tolerance — for GET responses that include server-generated timestamps
httpClient.makeCall(HttpCallResponse<R> expected, Duration temporalTolerance)

// Poll until status + body match (for eventual consistency)
httpClient.pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig)

// Same with temporal tolerance — for polling GET responses that include server-generated timestamps
httpClient.pollingCall(int expectedStatus, R expectedDto, RetryConfig retryConfig, Duration temporalTolerance)
```

Input to `makeCall` steps is always `HttpCallRequest<T>`:
```java
new HttpCallRequest<>(url, "POST", List.of(), bodyDto)
new HttpCallRequest<>(url, "GET",  List.of(), null)
```

---

## DbTestClient (Postgres / Hibernate)

```java
dbClient.findById()                          // fetch by @Id, assert exists, assert matches input
dbClient.findById(Duration temporalTolerance) // same, with tolerance for timestamp fields
dbClient.findByFields()                      // fetch by all non-null fields, assert exists, assert matches
dbClient.existsById()                        // assert entity exists, passthrough input
dbClient.notExistsById()                     // assert entity does NOT exist
dbClient.persist(expected)                   // persist DbRequest, assert result matches expected
dbClient.delete()                            // delete DbRequest, assert no longer exists
```

Input must be the entity class registered with `HibernateWrapper`.
`findById` and `findByFields` both assert the fetched entity matches the input — no separate `Verify` needed.

### Hibernate naming strategy

The test `HibernateWrapper` does **not** apply Spring Boot's `SpringPhysicalNamingStrategy`. Java camelCase field names are mapped to column names as-is — `productName` maps to column `productName`, not `product_name`. Entity mirrors must carry explicit `@Column(name = "snake_case_name")` for any field whose production column name differs from the Java field name:

```java
@Column(name = "product_name") private String productName;
@Column(name = "user_id")      private UUID userId;
@Column(name = "created_at")   private LocalDateTime createdAt;
```

Only remove constraint attributes (`nullable`, `length`, `unique`) — keep the `name` attribute.

---

## KafkaTestClient

```java
kafkaClient.consumeMatching(Class<T> type)                              // poll until matching message, assert equals input
kafkaClient.consumeMatching(Class<T> type, Duration temporalTolerance)  // same, with tolerance for timestamp fields
kafkaClient.consume(Class<T> type)                                      // poll and return next message of type T
kafkaClient.consume(Class<T> type, T expected)                          // poll and assert equals expected
kafkaClient.consume(Class<T> type, T expected, Duration temporalTolerance) // same, with tolerance
kafkaClient.publish()                                                   // publish KafkaMessage<T> to configured topic
```

`consumeMatching` is the standard choice for verifying that a write triggered an event.
Call `kafkaClient.warmup()` in `@BeforeSuite` before any consume calls.

### Topic routing

`publish()` always sends to **`topics.get(0)`** — the first topic registered in `KafkaConfig`. When a project has multiple Kafka domains (e.g. `user.created` and `order.placed`), declare a **separate `KafkaTestClient`** per domain so that `publish()` and `consumeMatching()` operate on the right topic:

```java
// user-domain client — user.created is first
kafkaClient = new KafkaTestClient(
        KafkaConfig.builder()
                .bootstrapServers(...)
                .topic("user.created")
                .topic("user.updated")
                .topic("user.deleted")
                .groupId(UUID.randomUUID().toString())
                .build(),
        RetryConfig.of(5, Duration.ofSeconds(2))
);

// order-domain client — order.placed is first
orderKafkaClient = new KafkaTestClient(
        KafkaConfig.builder()
                .bootstrapServers(...)
                .topic("order.placed")
                .topic("order.cancelled")
                .groupId(UUID.randomUUID().toString())
                .build(),
        RetryConfig.of(5, Duration.ofSeconds(2))
);
```

Call `warmup()` on every client in `@BeforeSuite`, and `close()` each in `@AfterSuite`.

---

## MongoTestClient

```java
mongoClient.findById()                           // fetch by @Id, assert exists, assert matches input
mongoClient.findById(Duration temporalTolerance)  // same, with tolerance for timestamp fields
mongoClient.findByFields()                        // fetch by all non-null fields, assert exists, assert matches
mongoClient.existsById()                          // assert document exists, passthrough input
mongoClient.notExistsById()                       // assert document does NOT exist
mongoClient.persist(expected)                     // persist MongoRequest, assert result matches expected
mongoClient.delete()                              // delete MongoRequest, assert no longer exists
```

Input must be the document class registered with `MongoWrapper`.

### Collection registration

`MongoWrapper` uses explicit `.collection(Class, "name")` registration — it does **not** scan for `@Document` annotations. Test mirror document classes must **not** carry `@Document`; the collection mapping belongs in the `MongoWrapper` construction in `BaseTest`:

```java
mongo = new MongoWrapper(
        MongoConfig.builder()
                .connectionString(...)
                .database(...)
                .collection(UserProjectionDoc.class, "users")
                .collection(OrderProjectionDoc.class, "order_projections")
                .build()
);
```

---

## Temporal tolerance

All comparison steps (`findById`, `consumeMatching`, `makeCall(HttpCallResponse)`, etc.) have overloads that accept a `Duration temporalTolerance`. When temporal tolerance is set, the comparator accepts any timestamp within `±tolerance` of the expected value for `Instant`, `LocalDateTime`, `ZonedDateTime`, and `OffsetDateTime` fields. All other fields are compared exactly.

Two situations that require temporal tolerance:

### Storage precision truncation

Some stores reduce timestamp precision when persisting:
- MongoDB: truncates `Instant` nanoseconds to milliseconds
- Postgres: truncates nanoseconds to microseconds (less common in practice)

When the expected document was built from an event or DTO that carries full nanosecond precision, the round-trip through the store will fail exact comparison. Use `Duration.ofMillis(1)`:

```java
Pipeline.given(expectedDoc)
        .then(mongoClient.findById(Duration.ofMillis(1)))
        .execute();
```

Always use the tolerance overload for `mongoClient.findById()` when the expected document contains a timestamp field that was stored in MongoDB.

### SLA window for service-generated timestamps

When the service sets a timestamp field to `Instant.now()` internally (e.g. `eventTimestamp`, `createdAt`, `processedAt`), the test cannot know the exact value. The correct approach is:

1. Capture `Instant testStart = Instant.now()` at the start of the test
2. Set the expected timestamp field to `testStart`
3. Use a tolerance equal to the maximum acceptable processing delay

```java
Instant testStart = Instant.now();

OrderPlacedEvent expected = new OrderPlacedEvent(orderId, ..., testStart);
Pipeline.given(expected)
        .then(orderKafkaClient.consumeMatching(OrderPlacedEvent.class, Duration.ofMinutes(2)))
        .execute();
```

This simultaneously asserts that the timestamp exists (not null) and was set within the SLA window. If the event arrives with `eventTimestamp` more than 2 minutes from `testStart`, the assertion fails — which is the desired behavior when the SLA is violated.

**Do not use `null` for timestamp fields when you can use temporal tolerance.** Null skips the field entirely; tolerance-aware comparison still verifies the field is present and within bounds.

---

## Verify (inline assertions)

```java
Verify.matching(expected)          // recursive field-by-field equality, ignores nulls in expected
Verify.equalTo(expected)           // strict equality
Verify.containsAll(Collection<T>)  // list contains all expected items, order-insensitive
```

Use `Verify.matching` when the actual object may have server-generated fields (id, timestamps) that are absent from the expected object.
Use `Verify.equalTo` for exact equality where no extra fields are allowed.
Use `Verify.containsAll` for GET-all endpoints where the list may contain items from other tests.

`Verify.matching` uses `ignoringExpectedNullFields` — any field set to `null` in the expected object is skipped during comparison. Use this to assert partial state without pinning dynamic or irrelevant fields.

---

## Test levels and package structure

Organise tests into three levels, each in its own subpackage:

| Level | Package | What it tests |
|---|---|---|
| **Endpoint** | `…domain.endpoint` | Single endpoint in isolation — happy path + all negative cases |
| **Flow** | `…domain.flow` | Multi-step sequences within one service — state transitions, consistency across operations |
| **Component** | `…domain.component` | Vertical slice from a specific entry point — e.g. HTTP→DB→Kafka or Kafka→MongoDB |
| **End-to-end** | `…domain` (or `…domain.e2e`) | Full cross-system chain |

Rules per level:
- **Endpoint**: every endpoint gets its own test class; covers all status codes, validation errors, not-found
- **Flow**: one class per domain; no validation/error body tests; one pipeline per operation, state carried via local variables
- **Component**: tests can start from any entry point (HTTP, Kafka, DB); each slice covers only its own boundary — stop at the output boundary, do not assert the next system's state; use dedicated test data factories that don't depend on other layers (e.g. build Kafka events directly, not via HTTP)
- **End-to-end**: full system chain across all systems; one pipeline per logical operation; pre-compute expected state before destructive operations; no duplicate assertions already covered by lower levels

## Test class structure

```java
public class XxxApiTest extends BaseTest {

    @Test
    public void methodName_condition_expectedOutcome() throws Exception {
        Pipeline.given(...)
                .then(...)
                .execute();
    }
}
```

- Extend the project's `BaseTest` (provides clients and lifecycle hooks)
- All tests declare `throws Exception`
- No `try/catch` — let failures propagate

---

## Naming convention

**Endpoint and component tests** — `methodUnderTest_condition_expectedOutcome`:

| Example | Meaning |
|---|---|
| `createItem_persistedInDb` | happy path, verifies DB |
| `createItem_nullName_returns400` | null required field → 400 |
| `createItem_blankName_returns400` | blank required field → 400 |
| `createItem_nameTooLong_returns400` | size exceeded → 400 |
| `deleteItem_notFound` | resource does not exist → 404 |

**Flow and e2e tests** — `verbNoun_thenVerbNoun_outcome` (reads as a sequence of operations):

| Example | Meaning |
|---|---|
| `createThenGet_userRetrievable` | create followed by GET returns the same data |
| `createThenUpdate_thenGet_returnsUpdatedData` | update is reflected in subsequent GET |
| `createThenDelete_thenGet_returns404` | deleted resource is no longer retrievable |
| `createUser_fullSystemFlow` | create triggers the full cross-system chain |
| `updateUser_fullSystemFlow` | update propagates to all systems |

Use `@Test(description = "...")` for all tests. Write the description as a user story — what the system does, not what the test does: `"Creating an item persists it in the database and publishes an event"`, not `"Test create item"`.



---

## Happy path rule

The primary happy path test must verify the end state, not just the HTTP response.

- Write endpoint → chain to `dbClient.findById()` (or `mongoClient.findById()` for read models)
- Write that publishes an event → chain to `kafkaClient.consumeMatching(...)`
- Full flow → chain HTTP → DB → Kafka → MongoDB in one pipeline

A test that only checks the HTTP response body is redundant when `findById` already performs a round-trip assertion. Only add a response-only test if it covers something the DB test cannot (response shape, headers, status nuance).

---

## Negative test rules

### Required fields (`@NotBlank` or equivalent)
Generate **two** tests per field — blank (`""`) and null (`null`). Both must return 400.

### Size limits (`@Size(max = N)` or equivalent)
Generate **one** test per field at exactly `N + 1` characters. Must return 400.

### Not-found
For every endpoint that accepts a resource id in the path, generate one test with a random UUID. Must return 404.

### Always deserialize and assert the error response body
Never use `Void.class` for error responses. Define project-level error DTOs that mirror your API's error shape, deserialize into them, and assert with `Verify.matching`.

Use `null` for fields you do not want to assert — `Verify.matching` skips them via `ignoringExpectedNullFields`. This lets you assert the field that matters (e.g. which field failed validation) without pinning dynamic content (e.g. a message containing the resource id).

Two common error shapes and the assertion levels they support:

**Validation errors (400)** — typically `{ status, errors: [{field, message}] }`:
```java
// Assert field name only — default when message is a framework string
Verify.matching(yourErrorDto)   // where errors[0].field = "name", message = null

// Assert field name + message — only when message is custom and API-contract
Verify.matching(yourErrorDto)   // where errors[0].field = "name", message = "Name is required"
```

**Other errors (404, 409, etc.)** — typically `{ status, message }`:
```java
// Assert status only — default when message contains dynamic content (e.g. resource id)
Verify.matching(yourErrorDto)   // where status = 404, message = null

// Assert status + message — when message is a fixed, meaningful string
Verify.matching(yourErrorDto)   // where status = 404, message = "Item not found"
```

Define these DTOs in your project and document the specific class names and factory methods in your project-level skill file.

---

## Component tests

Component tests verify a vertical slice of the system starting from a specific entry point — not always from HTTP. Each slice covers only the boundary of the service under test and stops at its output. The next system's state is out of scope.

Two common slice patterns:

### Slice 1 — HTTP entry point (write service)

Verify that an HTTP operation both persists state **and** publishes the expected event. Use a single pipeline that chains HTTP → DB → Kafka in order.

```java
@Test(description = "Creating an item via HTTP persists it in the database and publishes a matching event to Kafka")
public void createItem_persistedInDb_andPublishesKafkaEvent() throws Exception {
    Pipeline.given(TestItemRequests.createItem())
            .then(httpClient.makeCall(201, ItemDto.class))
            .then(mapper.toEntity())
            .then(dbClient.findById())
            .then(mapper.entityToCreatedEvent())
            .then(kafkaClient.consumeMatching(ItemCreatedEvent.class))
            .execute();
}
```

Stop after `kafkaClient.consumeMatching` — do not assert the read model (MongoDB, Elasticsearch, etc.). That belongs to Slice 2.

### Slice 2 — Event entry point (read/projection service)

Verify that publishing an event causes the downstream service to project it into the read store. Use **two separate pipelines**: one to publish the event, one to assert the projection.

```java
@Test(description = "An ItemCreated event published directly is projected into the read store by the projection service")
public void itemCreatedEvent_projectedToReadStore() throws Exception {
    ItemCreatedEvent event = TestEvents.defaultItemCreatedEvent();

    Pipeline.given(new KafkaMessage<>(event.itemId().toString(), event))
            .then(kafkaClient.publish())
            .execute();

    ItemProjectionDoc expected = mapper.toProjectionDoc(event);
    Pipeline.given(expected)
            .then(mongoClient.findById())
            .execute();
}
```

Do not call HTTP to produce the event — build it directly from a dedicated test data factory (`TestEvents` or equivalent). This removes the write service as a dependency and lets the projection service team own the test independently.

### Test data factory independence

The event factory for Slice 2 must build events from scratch (datafaker, fixed values, etc.) without relying on any other service or HTTP layer. If the factory calls HTTP to create a resource first, you have inadvertently reintroduced a cross-service dependency.

### Sequential execution

Component tests that share a messaging topic with other test levels must run sequentially to prevent event contamination. Configure `thread-count="1"` or `parallel="false"` in your test runner config, or annotate the class with the equivalent single-threaded annotation.

### Rules

- **Slice 1 stops at its output boundary** — if the service publishes to Kafka, stop there; do not assert the read model
- **Slice 2 starts at the event** — never derive the event from an HTTP call; use an independent factory
- **Two pipelines in event-entry slices** — publish is one pipeline, assertion is a separate pipeline
- **No validation or error body tests** — those belong in endpoint tests
- **Each slice is owned by the team that owns the boundary** — Slice 1 by the write-service team, Slice 2 by the projection/read-service team

---

## End-to-end tests

E2e tests verify the full cross-system chain from the HTTP entry point through all downstream systems. The number of pipelines depends on the operation — not all e2e tests fit in a single pipeline.

### Create — single pipeline walks the full chain

Each step's output feeds the next, so the whole chain is one pipeline:

```java
@Test(description = "Creating an item triggers the full system flow: persisted in DB, event published to Kafka, projected to read store")
public void createItem_fullSystemFlow() throws Exception {
    Pipeline.given(TestItemRequests.createItem())
            .then(httpClient.makeCall(201, ItemDto.class))
            .then(mapper.toEntity())
            .then(dbClient.findById())
            .then(mapper.entityToCreatedEvent())
            .then(kafkaClient.consumeMatching(ItemCreatedEvent.class))
            .then(mapper.toProjectionDoc())
            .then(mongoClient.findById())
            .execute();
}
```

### Update — two pipelines: setup then assert

The setup (create) is a separate pipeline. Its result is captured and reused in the update pipeline:

```java
@Test(description = "Updating an item propagates to DB, Kafka, and the read store")
public void updateItem_fullSystemFlow() throws Exception {
    ItemDto created = Pipeline.given(TestItemRequests.createItem())
            .then(httpClient.makeCall(201, ItemDto.class))
            .execute();

    Pipeline.given(TestItemRequests.updateItem(created.getId(), TestItems.defaultItem()))
            .then(httpClient.makeCall(200, ItemDto.class))
            .then(mapper.toEntity())
            .then(dbClient.findById())
            .then(mapper.entityToUpdatedEvent())
            .then(kafkaClient.consumeMatching(ItemUpdatedEvent.class))
            .then(mapper.toUpdatedProjectionDoc())
            .then(mongoClient.findById())
            .execute();
}
```

### Delete — one pipeline per assertion

Delete assertions are independent (DB absence, Kafka event, read store absence) and cannot be chained linearly. Each becomes its own pipeline. Pre-compute the expected entity and doc **before** the delete — the live data is gone after it:

```java
@Test(description = "Deleting an item removes it from DB, publishes an event to Kafka, and removes the projection from the read store")
public void deleteItem_fullSystemFlow() throws Exception {
    ItemDto created = Pipeline.given(TestItemRequests.createItem())
            .then(httpClient.makeCall(201, ItemDto.class))
            .execute();

    // Pre-compute before the delete — live data will be gone after
    ItemEntity expectedEntity = mapper.INSTANCE.toEntity(created);
    ItemProjectionDoc expectedDoc = mapper.INSTANCE.toProjectionDoc(
            mapper.INSTANCE.toCreatedEvent(created));

    Pipeline.given(TestItemRequests.deleteItem(created.getId()))
            .then(httpClient.makeCall(204, Void.class))
            .execute();

    Pipeline.given(expectedEntity)
            .then(dbClient.notExistsById())
            .execute();

    Pipeline.given(new ItemDeletedEvent(created.getId(), null))
            .then(kafkaClient.consumeMatching(ItemDeletedEvent.class))
            .execute();

    Pipeline.given(expectedDoc)
            .then(mongoClient.notExistsById())
            .execute();
}
```

For server-generated timestamps (e.g. `eventTimestamp`) prefer temporal tolerance over `null` — see the **Temporal tolerance** section. `null` skips the field entirely; tolerance-aware comparison still verifies the field is present and within an acceptable window. Use `null` only for truly non-deterministic fields that cannot be bounded.

### Rules

- **Assert all systems** — an e2e test that skips Kafka or the read store is a component test
- **One pipeline per logical operation** — do not chain create→delete in one pipeline
- **Pre-compute expected state before destructive operations** — capture the entity and projection doc before calling delete
- **No validation or error tests** — those belong in endpoint tests

---

## Cross-domain foreign key fields

When a domain's request DTO references an entity from another domain (e.g. `OrderRequest.userId`), a `PLACEHOLDER_<DOMAIN>_ID` constant is generated for use in test factories. This placeholder is **only valid for validation (400) tests** where the service rejects the request at the bean-validation layer before reaching the database.

For any test that expects a successful (2xx) response and persists data, the referenced entity must exist. Create it first in a setup pipeline and use its real ID:

```java
// wrong for happy-path tests — service rejects with 404 if userId doesn't exist
Pipeline.given(TestOrderRequests.placeOrder(TestOrders.defaultOrder()))  // uses PLACEHOLDER_USER_ID
        .then(httpClient.makeCall(201, OrderDto.class))
        .execute();

// correct — create the user first, use the real UUID
UserDto user = Pipeline.given(TestUserRequests.createUser())
        .then(httpClient.makeCall(201, UserDto.class))
        .execute();
Pipeline.given(TestOrderRequests.placeOrder(TestOrders.builder().userId(user.getUuid()).build()))
        .then(httpClient.makeCall(201, OrderDto.class))
        .execute();
```

---

## Retry and eventual consistency

Clients are constructed with a `RetryConfig` that controls how many times a step is retried before failing. For async flows (Kafka consumers, read models populated asynchronously), use a higher retry count and longer interval:

```java
RetryConfig.of(10, Duration.ofSeconds(2))  // 10 attempts, 2s apart
```

`pollingCall` on `HttpTestClient` is for cases where the HTTP response itself is eventually consistent.
