# Coverage Report

Scan the project and generate a structured integration test coverage report.
This report measures coverage in terms meaningful for integration tests — not line coverage,
but endpoint coverage, scenario coverage, and system chain coverage.

**Usage:** `/coverage-report`

No arguments. Run from the project root at any time.

---

## Step 1 — Scan source code

Read every `@RestController` class in the source modules. For each controller extract:
- HTTP method (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`)
- Path (including path variables)
- Request body type (if any)
- Response body type
- Which domain it belongs to

Also read every Kafka publisher class (`@KafkaListener` or publisher classes) to identify:
- Which topics are published to per operation
- Which topics are consumed

---

## Step 2 — Scan test classes

Read every test class under the tests module. For each class determine:

**Location signals:**
- `*/endpoint/*` → endpoint test class
- `*/flow/*` → flow test class
- `*/component/*` → component test class
- `*EndToEndTest*` or directly under domain package → e2e test class

**Method-level signals — read every `@Test` method name and body:**

For endpoint tests, determine per endpoint:
- **Happy path** — method calls `httpClient.makeCall(2xx)` AND chains to `dbClient` or `mongoClient`
- **Validation (400)** — method name contains `returns400`, `blank`, `null`, `tooLong`, or calls `makeCall(400)`
- **Not-found (404)** — method name contains `notFound` or `returns404`, or calls `makeCall(404)`
- **Conflict (409)** — method name contains `conflict` or `returns409`, or calls `makeCall(409)`

For chain tests, determine:
- **HTTP→DB** — test chains `httpClient` → `dbClient`
- **HTTP→DB→Kafka** — test chains `httpClient` → `dbClient` → `kafkaClient`
- **Kafka→MongoDB** — test publishes via `kafkaClient.publish()` → asserts via `mongoClient`
- **Full chain** — test chains all four clients: `httpClient` → `dbClient` → `kafkaClient` → `mongoClient`

For flow tests, determine which operation sequences are covered from method names:
- `createThenGet`, `createThenUpdate`, `createThenDelete`, `fullCrudLifecycle`, etc.

---

## Step 3 — Generate the report

Output the report in this exact format:

```
Coverage Report — <project name>
Generated: <today's date>
════════════════════════════════════════════════════════════════

<DOMAIN NAME> DOMAIN
────────────────────────────────────────────────────────────────

Endpoint Coverage:
  <METHOD>  <path>
    ✓ happy path       (<TestClassName>.<methodName>)
    ✓ validation       (N tests — fields: <list of fields tested>)
    ✓ not-found
    ✗ conflict         ← not tested

  <METHOD>  <path>
    ✓ happy path
    ✗ validation       ← not tested
    ✗ not-found        ← not tested

Chain Coverage:
  HTTP → Postgres                   ✓ <TestClassName>
  HTTP → Postgres → Kafka           ✓ <TestClassName>
  Kafka → MongoDB                   ✓ <TestClassName>
  Full chain (all systems)          ✓ <TestClassName>

  ← or ✗ not covered for missing chains

Flow Coverage:
  ✓ <sequence name>   (<methodName>)
  ✗ <suggested gap>   ← not tested

════════════════════════════════════════════════════════════════

SUMMARY
────────────────────────────────────────────────────────────────
Domains:        <N> domains scanned

Endpoint tests:
  Happy path:   <X>/<total> endpoints
  Validation:   <X>/<total> endpoints with validated fields
  Not-found:    <X>/<total> endpoints with path variable id

Chain tests:
  HTTP→DB:              ✓/✗ per domain
  HTTP→DB→Kafka:        ✓/✗ per domain
  Kafka→MongoDB:        ✓/✗ per domain
  Full chain:           ✓/✗ per domain

════════════════════════════════════════════════════════════════

GAPS — what to generate next
────────────────────────────────────────────────────────────────
<list every ✗ item as a concrete gen command the user can run>

Examples:
  /orders/gen-endpoint-test CancelOrderApiTest — add not-found test
  /orders/gen-e2e-test OrderEndToEndTest — full chain not covered
  /users/gen-flow-test UserFlowTest — cancelThenGet sequence missing
```

---

## Step 4 — Rules

- Never guess coverage — only mark ✓ if you read the actual test method and confirmed it
- If a test class exists but you cannot determine what it covers — mark as `⚠ exists but unreadable`
- List every gap as a concrete, runnable gen command — not a vague suggestion
- If a domain has no test classes at all — mark entire domain as `✗ no tests`
- For validation coverage, list which fields are tested and which are missing based on the source constraints
- Do not report line coverage percentages — this report is scenario-based only
