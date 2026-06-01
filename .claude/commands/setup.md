# Setup

Interactive setup that scans your source code, creates adapted test mirrors, and generates
the full test infrastructure for your project. Run once per domain.

**Usage:**
```
/setup [--domain <name>] [--http <path>] [--db <path>] [--kafka <path>] [--mongodb <path>] [--env <name:url>] [--update]
```

All flags are optional. If omitted, the setup asks interactively.

**Examples:**
```
/setup
/setup --domain users --http /path/to/dto --db /path/to/entity
/setup --domain orders --http /path/to/dto --db /path/to/entity --kafka /path/to/event --mongodb /path/to/document
/setup --domain users --kafka /path/to/events --update
```

**CRITICAL: Do not write any files until the user confirms the mapping report in Step 3.**

---

## Step 1 — Collect configuration

Ask only for what was not provided as flags.

**Domain** — if `--domain` not provided:
> "Which domain do you want to set up? (e.g. users, orders) — or 'all' to extract everything.
> Warning: 'all' on a large system may produce noise. A domain name is recommended."

**Protocols** — if no protocol paths were provided, ask:
> "Which protocols does this domain use? Answer with any combination of: http, db, kafka, mongodb"

**Paths** — for each declared protocol that has no path flag, ask individually:
- `http` → "Path to request/response DTO classes (comma-separated for multiple repos)"
- `db` → "Path to entity classes"
- `kafka` → "Path to event classes"
- `mongodb` → "Path to document classes"

Multiple paths per protocol are supported. Accept comma-separated or space-separated.

**Environments** — if `--env` not provided:
> "Which environments do you want to support? Provide name and base URL for each service.
> Example: local:http://localhost:8080, dev:http://service.dev.internal, qa:http://service.qa.internal
> Press enter to use local only."

---

## Step 2 — Scan

Read each provided path recursively. Find all Java classes.

**Domain filter:** keep only classes whose simple name or package segment contains the domain keyword (case-insensitive). If domain=all, keep everything and warn:
> "Found N classes total across all protocols. Consider re-running with --domain to scope the setup."

For each protocol output:

```
Scanning with domain filter: "<domain>"

HTTP    found N classes → ClassName, ClassName, ...
        filtered out   → ClassName, ClassName, ...   (if any)

DB      found N classes → ClassName, ClassName, ...
        filtered out   → ClassName, ClassName, ...

KAFKA   found N classes → ClassName, ClassName, ...
        filtered out   → ClassName, ClassName, ...

MONGODB found N classes → ClassName, ClassName, ...
        filtered out   → ClassName, ClassName, ...

Proceed to mapping? [yes]
```

Wait for user confirmation before proceeding.

---

## Step 3 — Map

Attempt to map extracted classes across protocols into domain chains:
```
HTTP (request DTO + response DTO) → DB (entity) → Kafka (events) → MongoDB (document)
```

Not all protocols are required in the chain. Map what is available.

### Confidence rules

**Confident (✓)** — report and auto-proceed, do not ask:
- Class names share the domain keyword consistently across all layers
- Field overlap confirmed (majority of fields present across layers)
- Structural relationships clear (@OneToOne, @OneToMany match the naming)
- Sub-entity or sub-DTO pattern unambiguous (referenced only from parent, never standalone)

**Uncertain (⚠)** — stop, show candidates, require decision:
- Class name matches partially or inconsistently across layers
- Multiple candidates exist for the same mapping slot
- Field overlap below 60%
- Sub-entity/sub-DTO vs standalone domain is ambiguous

For each uncertain item:
```
⚠ UNCERTAIN — ClassName (protocol)
Candidates:
  A) <description>  [XX%]
  B) <description>  [XX%]
Suggested: A — <reason>
Decision? [A / B / ignore]
```

Wait for user input on each uncertain item before continuing.

**Not mapped (✗)** — list at end of report with options:
```
✗ NOT MAPPED
  ClassName — no match found across other protocols
  Options: [ignore / provide target manually]
```

### Cross-domain reference detection

After mapping, scan all included classes for fields that reference another domain:
- UUID or Long field whose name contains a foreign domain keyword (e.g. `userId`, `orderId`, `productId`)
- Field whose type is a class from outside the current domain

For each detected reference group:
```
⚡ CROSS-DOMAIN REFERENCES DETECTED

  The following classes reference the <other> domain:

  ClassName.fieldName (UUID) → likely references <OtherDomain>Entity.id
  ClassName.fieldName (UUID) → likely references <OtherDomain>Entity.id

  Suggestion: run /setup --domain <other> after this setup so cross-domain
  references resolve correctly in generated factories and mappers.

  Connect <other> domain now? [yes / no / later]
```

If user says yes: note the cross-domain context. During generation, foreign key fields are
kept as UUID in mirrors, and factories use a fixed placeholder UUID constant for that field.

### Full report format

```
MAPPING REPORT — domain: <domain>
────────────────────────────────────────────────────────

✓ CONFIDENT
  [confirmed chains]

⚠ UNCERTAIN
  [each uncertain item — wait for decision before continuing]

✗ NOT MAPPED
  [list with ignore option]

⚡ CROSS-DOMAIN REFERENCES
  [grouped by target domain with connect option]

────────────────────────────────────────────────────────
Review complete. Proceed with confirmed mappings? [confirm / change]
```

**Do not proceed to Step 4 until user types confirm.**

---

## Step 4 — Adapt

Apply the following standard test adaptations. These are always applied — do not ask.

**All mirrored DTOs (request + response):**
- Remove all validation annotations: `@NotBlank`, `@NotNull`, `@Size`, `@Valid`, `@Positive`,
  `@Min`, `@Max`, `@Pattern`, `@Email` and any other `javax.validation` / `jakarta.validation` annotations
- Keep all fields including optional ones
- Add fields present in response DTO but absent from request DTO (id, createdAt, modifiedAt etc.)
  — these are needed for assertion

**Event mirrors:**
- Add `@JsonIgnoreProperties(ignoreUnknown = true)` at class level
- Replace all primitive types with boxed equivalents (int → Integer, long → Long)
- Make all fields nullable

**Entity mirrors:**
- Keep `@Id` field and its `@GeneratedValue` if present
- Remove JPA lifecycle annotations (`@PrePersist`, `@PreUpdate`, `@EntityListeners` etc.)
- Remove `@Column` constraint attributes (`nullable=false`, `length`, `unique`) — test entities are permissive
- **Preserve `@Column(name = "...")` name mappings** — the test `HibernateWrapper` does not apply Spring Boot's naming strategy, so camelCase fields that map to snake_case columns (`productName` → `product_name`) require an explicit `name` attribute
- Keep `@OneToOne`, `@OneToMany`, `@ManyToOne` for structural context

**Document mirrors:**
- **Remove `@Document`** — collection names are registered via `MongoWrapper.collection(Class, "name")` in `BaseTest`, not via annotation scanning (the test module typically does not have Spring Data MongoDB on the classpath)
- Remove index annotations (`@Indexed`, `@CompoundIndex` etc.)

**Cross-domain foreign key fields:**
- Keep as UUID with no reference to the other domain's class
- Factories use a static `PLACEHOLDER_<DOMAIN>_ID = UUID.fromString("...")` constant
- **The placeholder is only valid for validation (400) tests** where the service rejects at the annotation layer. Any test that expects a successful (2xx) response and persists data must create the referenced entity first and use its real UUID — services validate FK existence at the database or service layer

---

## Step 5 — Generate

Determine the test module output path:
- If a `tests/` or `*-tests/` module exists, write to its `src/test/java` tree
- If tests are embedded in a service module, write to that module's `src/test/java`
- If ambiguous, ask: "Where should the generated test infrastructure be written? (provide path)"

Infer the base package from existing test classes, or ask if none exist.

Generate the following files:

**Mirrored classes:**
- `dto/XxxDto.java` — response DTO mirror
- `entity/XxxEntity.java` — entity mirror (+ sub-entities)
- `event/XxxCreatedEvent.java`, `XxxUpdatedEvent.java`, `XxxDeletedEvent.java` — one per event
- `document/XxxProjectionDoc.java` — document mirror

**Factory classes:**
- `factory/TestXxx.java` — builds test DTOs; datafaker for random valid values; static `defaultXxx()`;
  `builder()` with per-field overrides; `requiredFieldsOnlyXxx()` if optional fields exist
- `factory/TestEvents.java` — one static `defaultXxxCreatedEvent()` per event class; datafaker values
- Sub-factories for nested types (`TestAddresses`, `TestCars` etc.) if sub-DTOs exist

**Mapper:**
- `mapper/XxxTestMapper.java` — MapStruct `@Mapper(componentModel = "spring")`:
  - DTO → Entity
  - Entity → CreatedEvent
  - CreatedEvent → ProjectionDoc
  - Pipeline-compatible static bridge methods returning `StepFunction`

**Infrastructure** (only if not already present — do not overwrite existing BaseTest or Connections):
- `infrastructure/BaseTest.java` — declares clients for declared protocols; `@BeforeSuite` wires
  them from Connections; `@AfterSuite` closes them; `kafkaClient.warmup()` if Kafka declared.
  **When adding Kafka for a second (or Nth) domain to an existing BaseTest**: do not add the new domain's topics to the existing `kafkaClient`. Instead, declare a separate `<domain>KafkaClient` field with that domain's topics listed first — `publish()` always sends to the first registered topic, so mixing topics from multiple domains in one client will route publishes to the wrong topic.
- `infrastructure/Connections.java` — one constant per URL/topic/credential; multi-environment
  switch on `System.getProperty("env", "local")`:

```java
public static final String USER_SERVICE_URL = switch (ENV) {
    case "local" -> "http://localhost:8080";
    case "dev"   -> "http://service.dev.internal";
    default      -> throw new IllegalArgumentException("Unknown env: " + ENV);
};
```

Output:
```
Generating...
  ✓ dto/XxxDto.java
  ✓ entity/XxxEntity.java
  ...

Done.
Next: /gen-project-commands  — generates test generation command files
      /setup --domain <other>  — set up another domain
```

---

## Update flow

When `--update` is provided, only the specified protocol paths are re-scanned and re-generated.
All other protocols and previously generated files are left untouched.

Re-run mapping only for the updated protocol classes. If cross-domain references were previously
resolved, carry them forward automatically — do not ask again.
