# Garland

A Java framework for writing integration tests as composable, type-safe pipelines.

Each test step is a typed function `(I input, PipelineContext ctx) -> O`. Steps chain together with compile-time type enforcement — if the chain doesn't connect, it doesn't compile.

```java
UserDto expected = TestUsers.defaultUser();

Pipeline.given(TestUserRequests.createUser(expected))
        .then(httpClient.makeCall(201, UserDto.class))
        .then(Verify.matching(expected))
        .then(trackUser())
        .then(Verify.allOf(
                UserTestMapper.toEntity().andThen(postgresClient.findByFields()),
                UserTestMapper.toCreatedEvent().andThen(kafkaClient.consumeMatching(UserCreatedEvent.class)),
                UserTestMapper.dtoToCreatedProjectionDoc().andThen(mongoClient.findByFields())
        ))
        .execute();
```

**[Demo project](https://github.com/garlandframework/garland-demo)** — a full working example with two Spring Boot microservices, Kafka, Postgres, and MongoDB.

---

## Why Garland

Most integration test suites grow into unmaintainable collections of HTTP utilities, custom assertions, and shared mutable state. Garland replaces that with a single composable primitive.

**Type safety at compile time.** Step chains are generically typed — `Pipeline<I,O>` tracks the output type at each `.then()`. If two steps don't connect, the code doesn't compile. No runtime surprises from passing the wrong object to the wrong assertion.

**One pattern everywhere.** HTTP call, DB lookup, Kafka consume, MongoDB assert — all expressed as `Step<I,O>`. No separate assertion libraries to learn, no ad-hoc helper methods to maintain. A developer who has read one test understands all of them.

**Multi-system failures reported together.** `Verify.allOf()` runs all branches against the same input and collects every failure before throwing. Sequential assertions stop at the first failure and hide the rest. With `allOf`, one test run tells you everything that is broken.

**Built-in retry for async systems.** Every test client accepts a `RetryConfig` that applies automatically to all operations. Testing a Kafka consumer or a read-model projection that is populated asynchronously requires no manual polling loops — the client retries until the expected state appears or the attempt limit is reached. Individual operations can override the default when a tighter or looser tolerance is needed.

**Designed for AI generation.** Garland pipelines are linear, explicit, and structurally uniform — exactly the shape that LLMs generate reliably. No implicit DSL, no fluent builder ambiguity, no magic annotations. Claude can generate a full component test from a description and get it right on the first attempt. The output is also easy to review — one glance at the chain tells you exactly what the test does and in what order.

---

## Add to your project

```xml
<!-- HTTP -->
<dependency>
    <groupId>dev.garlandframework</groupId>
    <artifactId>garland-http</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Postgres -->
<dependency>
    <groupId>dev.garlandframework</groupId>
    <artifactId>garland-postgres</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>dev.garlandframework</groupId>
    <artifactId>garland-kafka</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- MongoDB -->
<dependency>
    <groupId>dev.garlandframework</groupId>
    <artifactId>garland-mongodb</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- TestNG base class and logger (optional) -->
<dependency>
    <groupId>dev.garlandframework</groupId>
    <artifactId>garland-testng</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or use the BOM to manage versions centrally:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.garlandframework</groupId>
            <artifactId>garland-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Requires Java 21.

---

## Core concept

A `Pipeline` is an immutable step chain. Each `.then(step)` call returns a new pipeline with an updated output type — the previous instance is never modified. Steps run eagerly and sequentially; the output of each step becomes the input of the next.

```java
UserDto created = Pipeline.given(buildCreateUserRequest())
        .then(httpClient.makeCall(201, UserDto.class))
        .execute();
```

Each step is typed `Step<I, O>`. The output type of one step must match the input type of the next — enforced at compile time. A full E2E chain makes the data flow explicit:

```
HttpCallRequest  →  [httpClient.makeCall]  →  UserDto
UserDto          →  [toEntity()]           →  UserEntity
UserEntity       →  [db.findById()]        →  UserEntity   (asserted)

UserDto          →  [toCreatedEvent()]     →  UserCreatedEvent
UserCreatedEvent →  [kafka.consumeMatching]→  UserCreatedEvent  (asserted)

UserDto          →  [toProjection()]       →  UserProjectionDoc
UserProjectionDoc→  [mongo.findById()]     →  UserProjectionDoc (asserted)
```

The mapper steps (`toEntity()`, `toCreatedEvent()`, `toProjection()`) are plain `Step<A, B>` implementations — typically thin wrappers around a mapper class. They connect the HTTP response type to whatever the downstream client expects.

`Step<I, O>` is a `@FunctionalInterface`: `(I input, PipelineContext ctx) -> O`. Pass method references, lambdas, or use `Step.lift(fn)` to adapt a plain `Function<I, O>`:

```java
static Step<UserDto, UserEntity> toEntity() {
    return Step.lift(INSTANCE::toEntity);
}
```

`PipelineContext` is a shared map that flows through the entire execution. Prefer returning data through step output types; use the context only for cross-cutting state that would otherwise need threading through several intermediate steps.

---

## Modules

### HTTP — `garland-http`

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

**Auth headers are stored in the client instance.** `withBearer`, `withHeader`, and `withApiKey` each return a new instance — the original is not modified:

```java
HttpTestClient authed = http.withBearer(token);
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

### PostgreSQL — `garland-postgres`

`PostgresTestClient` queries and asserts rows via Hibernate. All entity classes must be registered in `PostgresConfig` before construction.

```java
PostgresConfig config = PostgresConfig.builder()
        .url("jdbc:postgresql://localhost:5432/mydb")
        .username("test").password("test")
        .entity(UserEntity.class)
        .build();

PostgresTestClient db = new PostgresTestClient(
        new PostgresWrapper(config),
        RetryConfig.of(5, Duration.ofSeconds(1))
);
```

```java
// find by @Id field, assert matches (null fields ignored)
.then(db.findById())

// find by all non-null fields — throws if more than one row matches
.then(db.findByFields())

// count rows matching non-null fields
.then(db.countByFields())

// assert absence
.then(db.notExistsById())
```

---

### MongoDB — `garland-mongodb`

`MongoTestClient` mirrors the Postgres API. Document classes are mapped to collection names in `MongoConfig`.

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

MongoDB stores `Instant` with millisecond precision. Use `findById(Duration.ofMillis(1))` to absorb truncation in timestamp comparisons.

---

### Kafka — `garland-kafka`

`KafkaTestClient` consumes, deserializes, and asserts messages. Call `warmup()` before any consume call — it seeks all partitions to the current end so the test does not pick up events from previous runs.

```java
KafkaConfig config = KafkaConfig.builder()
        .bootstrapServers("localhost:9092")
        .topic("user-events")
        .groupId(UUID.randomUUID().toString())
        .build();

KafkaTestClient kafka = new KafkaTestClient(config,
        RetryConfig.of(10, Duration.ofSeconds(1)));

kafka.warmup();
```

```java
// consume next record matching expected
.then(kafka.consumeMatching(UserCreatedEvent.class))

// publish
Pipeline.given(KafkaMessage.of(event))
        .then(kafka.publish())
        .execute();
```

---

## Fan-out: asserting multiple systems at once

`Verify.allOf` runs all branches against the same input, collects every failure, then throws a single combined `AssertionError`. Sequential `.then()` stops at the first failure; `allOf` always reports all failing branches together.

```java
Pipeline.given(createUserRequest())
        .then(http.makeCall(201, UserDto.class))
        .then(Verify.allOf(
                UserTestMapper.toEntity().andThen(db.findById()),
                UserTestMapper.toCreatedEvent().andThen(kafka.consumeMatching(UserCreatedEvent.class)),
                UserTestMapper.toProjection().andThen(mongo.findById())
        ))
        .execute();
```

---

## Retry

`RetryConfig` controls retry behaviour for all test clients:

```java
RetryConfig.of(10, Duration.ofSeconds(1));  // 10 attempts, 1s delay
RetryConfig.attempts(5);                    // 5 attempts, no delay
```

All test clients accept a `RetryConfig` in their constructor that applies to all operations. Individual operations can override it inline.

---

## Build

```bash
mvn clean install          # build + test
mvn clean install -DskipTests
mvn test -pl garland-core  # run core unit tests only
```

The `release` profile (`mvn package -Prelease`) produces sources JARs, Javadoc JARs, and GPG-signed artifacts for Maven Central.
