# Modular Test Orchestrator

A Java library for writing integration tests as composable, type-safe pipelines. Each test step is a function `(I input, PipelineContext ctx) -> O`; steps chain together with compile-time type enforcement.

Modules: `core`, `http`, `postgres`, `kafka`, `mongodb`.  
Requires Java 21 and Maven.

---

## Core concept

A `Pipeline` is an immutable step chain. Each `.then(step)` call returns a new pipeline with an updated output type — the previous instance is never modified. Steps run eagerly and sequentially; the output of each step becomes the input of the next.

```java
UserDto created = Pipeline.given(buildCreateUserRequest())
        .then(httpClient.makeCall(201, UserDto.class))
        .execute();
```

`Step<I, O>` is a `@FunctionalInterface`: `(I input, PipelineContext ctx) -> O`. Pass method references, lambdas, or use `Step.lift(fn)` to adapt a plain `Function<I, O>`:

```java
static Step<UserDto, UserEntity> toEntity() {
    return Step.lift(INSTANCE::toEntity);
}
```

`PipelineContext` is a shared map that flows through the entire execution. Prefer returning data through step output types; use the context only for cross-cutting state that would otherwise need threading through several intermediate steps.

---

## Modules

### HTTP — `org.modulartestorchestrator:http`

`HttpTestClient` executes requests, asserts status codes, deserializes responses, and optionally matches the body — all in a single step.

```java
HttpTestClient http = new HttpTestClient();

// assert status + body match (null fields in expected are ignored)
UserDto result = Pipeline.given(createUserRequest())
        .then(http.makeCall(HttpCallResponse.of(201, expectedDto)))
        .execute();

// assert status only, return deserialized body
ErrorDto error = Pipeline.given(badRequest())
        .then(http.makeCall(400, ErrorDto.class))
        .execute();
```

**Auth headers are stored in the client instance.** `withBearer`, `withHeader`, and `withApiKey` each return a new instance — the original is not modified. Never reassign the shared reference:

```java
// correct
HttpTestClient authed = http.withBearer(token);

// wrong — breaks every other reference to http
http = http.withBearer(token);
```

**`pollingCall`** retries until the response body matches — use for read-model endpoints populated by async consumers:

```java
Pipeline.given(getProjectionRequest())
        .then(http.pollingCall(200, expectedProjection,
                RetryConfig.of(10, Duration.ofSeconds(1))))
        .execute();
```

**Generic response types** use `TypeReference` to avoid erasure:

```java
List<UserDto> users = Pipeline.given(listUsersRequest())
        .then(http.makeCall(200, new TypeReference<List<UserDto>>() {}))
        .execute();
```

---

### PostgreSQL — `org.modulartestorchestrator:postgres`

`DbTestClient` queries and asserts rows via Hibernate. All entity classes must be registered in `DbConfig` before construction — Hibernate validates the schema at startup.

```java
DbConfig config = DbConfig.builder()
        .url("jdbc:postgresql://localhost:5432/mydb")
        .username("test").password("test")
        .entity(UserEntity.class)
        .build();

DbTestClient db = new DbTestClient(
        new HibernateWrapper(config),
        RetryConfig.of(5, Duration.ofSeconds(1))
);
```

```java
// find by @Id field, assert matches (null fields ignored)
db.findById().apply(expectedEntity, ctx);

// find by all non-null fields — throws if more than one row matches
db.findByFields().apply(expectedEntity, ctx);

// count rows matching non-null fields
long count = db.countByFields().apply(criteria, ctx);

// assert row exists / does not exist by @Id
db.existsById().apply(entity, ctx);
db.notExistsById().apply(entity, ctx);

// insert and assert stored result
db.persist(expected).apply(DbRequest.persist(entity), ctx);
```

Use `findById(Duration.ofMillis(1))` when timestamps are truncated by the JDBC driver.

---

### MongoDB — `org.modulartestorchestrator:mongodb`

`MongoTestClient` mirrors the `DbTestClient` API. Document classes must be mapped to collection names in `MongoConfig`.

```java
MongoConfig config = MongoConfig.builder()
        .connectionString("mongodb://localhost:27017")
        .database("testdb")
        .collection(UserProjection.class, "user_projections")
        .build();

MongoTestClient mongo = new MongoTestClient(
        new MongoWrapper(config),
        RetryConfig.of(5, Duration.ofSeconds(1))
);
```

MongoDB stores `Instant` with millisecond precision. If your documents contain nanosecond-precision timestamps, use:

```java
mongo.findById(Duration.ofMillis(1)).apply(expected, ctx);
```

---

### Kafka — `org.modulartestorchestrator:kafka`

`KafkaTestClient` consumes, deserializes, and asserts messages. **Call `warmup()` before any consume call** — it seeks all partitions to the current end so the test does not pick up events from previous runs. Call it again between test sections.

```java
KafkaConfig config = KafkaConfig.builder()
        .bootstrapServers("localhost:9092")
        .topic("user-events")
        .groupId(UUID.randomUUID().toString())  // always random — prevents offset reuse
        .build();

KafkaTestClient kafka = new KafkaTestClient(config,
        RetryConfig.of(10, Duration.ofSeconds(1)));

kafka.warmup(); // call before consuming
```

```java
// consume next record and assert it matches expected
kafka.consume(UserCreatedEvent.class, expectedEvent).apply(input, ctx);

// when other events may arrive before the expected one:
// consumeMatching retries until a record matches, tolerating interleaved messages
kafka.consumeMatching(UserCreatedEvent.class).apply(expectedEvent, ctx);

// publish
Pipeline.given(KafkaMessage.of(event))
        .then(kafka.publish())
        .execute();
```

---

## Fan-out: asserting multiple systems at once

`Verify.allOf` runs all branches against the same input, collects every failure, then throws a single combined `AssertionError`. Chaining `.then()` calls stops at the first failure; `allOf` always reports all failing branches together.

```java
Pipeline.given(createUserRequest())
        .then(http.makeCall(201, UserDto.class))
        .then(Verify.allOf(
                db.findById(),
                kafka.consumeMatching(UserCreatedEvent.class),
                mongo.findById()
        ))
        .execute();
```

---

## Retry

`Retry.of(step, config)` wraps any `Step`. Each failed attempt is logged as a warning; after all attempts are exhausted the last throwable is rethrown as-is.

```java
RetryConfig config = RetryConfig.of(10, Duration.ofSeconds(1));
// or no delay:
RetryConfig config = RetryConfig.attempts(5);
```

All test clients accept a `RetryConfig` in their constructor that applies to all operations. Individual operations can override it inline.

---

## Build

```bash
mvn clean install          # build + test
mvn clean install -DskipTests
mvn test -pl core          # run core unit tests only
```

The `release` profile (`mvn package -Prelease`) produces sources JARs, Javadoc JARs, and GPG-signed artifacts for Maven Central.
