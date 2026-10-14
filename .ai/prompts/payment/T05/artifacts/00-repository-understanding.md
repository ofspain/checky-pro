# payment · T05 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

`payment-service` (Java 21, Spring Boot) currently has: a Flyway-migrated `payments` schema
(9 tables, `V1__payments_baseline.sql`/`V2__payment_app_role_and_grants.sql`, immutable — T02);
a resource-server security layer validating JWTs against auth-service's JWKS, with
`PublicEndpoints` allowlisting only health/info/prometheus (T03); five validated
`@ConfigurationProperties` records under `common.config` (T03); and, as of T04, its first
`@Entity` (`OutboxEvent`), its outbound Kafka pipeline (`OutboxPublisher`/`OutboxRelay`/
`EventTopics`/`KafkaProducerConfig`), and a `ClockConfig` bean. No domain entity, controller, or
business-logic package exists yet — T05 is the first task to add one.

## 2. Existing code this task touches

- **New**: `invoice` package — does not exist. This task creates `Invoice`, `InvoiceState`,
  `InvoiceRepository`, `InvoiceService` only (per `tasks.md` task 5's own text — `dto/`,
  `event/`, `InvoiceController`, `InvoiceExpirySweepJob` are later tasks: 6 and 8).
- **Existing, to reuse as-is**: the real `invoices` table (`V1__payments_baseline.sql`) —
  `invoice_uuid UUID`, `merchant_account_uuid UUID`, `chain VARCHAR(32)`,
  `token_contract_address VARCHAR(128)`, `token_decimals SMALLINT`, `amount NUMERIC(78,0)`,
  `pay_to_address VARCHAR(128)`, `reference VARCHAR(140)`, `state VARCHAR(16)` (CHECK-constrained
  to `OPEN/PAID/UNDERPAID/OVERPAID/EXPIRED/HELD`), `watch_id UUID`, `expires_at TIMESTAMPTZ`,
  `created_at`/`updated_at`. `CHECK (amount > 0)` is already enforced at the DB layer.
- **Existing, no change**: `OutboxPublisher` (T04) exists and is available for a later task
  (T06) to call — T05 itself does not publish anything (no call site yet, confirmed by
  `tasks.md`'s own task 5/6 split).
- **`payment_app` role** currently has grants on `ledger_entries`/`verification_records` only
  (T02) — `invoices` has **no grant yet**. This task's own JPA repository will need
  `invoices` granted; per the established incremental-grant convention (T02's own note: "each
  table gets its own grant in whichever later task first needs it"), this likely means T05 must
  add a new migration (`V3__...sql`) granting `payment_app` on `invoices`, mirroring how T02
  scoped the first two grants. **Flagged as a design question for Phase 1/2**, not assumed here.

## 3. Established patterns to follow

- **Entities**: `OutboxEvent` (T04) is the only precedent in this service — package-private
  repository interface, protected no-arg constructor, static factory method, getters only, no
  setters. `crypto-service`'s own domain entities (e.g. `TokenAllowlist`) follow the identical
  shape and are a closer structural match for a non-outbox domain entity.
- **Money/chain identity (L6)**: confirmed by direct read of `crypto-service`'s own
  `AttestRequest`/`RegisterWatchRequest`/`ProviderProperties`/`FinalityProperties` — the
  established, repeated convention for a chain field is `@NotBlank @Pattern(regexp =
  "ETHEREUM|TRON")`, not a Java enum (`Chain.java` exists but is only used internally by
  `crypto-service`'s adapter layer, never as a bean-validated request field type). No
  `AddressValidator`/EIP-55/Base58Check validation exists anywhere yet — `crypto-service`'s own
  `TokenValidator.java` Javadoc explicitly defers that to its own later, separately-scheduled
  task (12), not yet built. No contract-address shape regex exists anywhere in the codebase
  today.
- **ArchUnit money-type enforcement**: **no precedent exists anywhere in this codebase for an
  ArchUnit rule that forbids `float`/`double` on money fields** — `auth-service`'s and
  `notification-service`'s own `ArchitectureTest` classes enforce module-boundary rules only,
  not money types; `crypto-service` has no generic `ArchitectureTest` at all (only
  `CrossModuleEntityArchitectureTest`/`KmsSignerArchitectureTest`, neither money-related). T05 is
  the first task in the entire codebase to need this specific rule — see [[surefire-archtest-field-bug]]
  below for the one mandatory structural constraint on how it must be written.
- **Known, critical gotcha for any new ArchUnit test (confirmed in memory and directly
  re-verified by reading both existing classes)**: a class containing even one `@ArchTest`-
  annotated field causes this repository's Maven Surefire (3.5.3) to report `Tests run: 0` for
  the **entire class**, silently swallowing every ordinary `@Test` method in it too —
  `auth-service`'s and `crypto-service`'s own existing `@ArchTest`-field-based architecture tests
  are themselves silently non-enforced by this mechanism (a separate, already-logged, out-of-
  scope issue). `notification-service`'s own `ArchitectureTest.java` (T16) is the **only
  confirmed-working shape**: plain `private static final ArchRule` fields (no `@ArchTest`, no
  `@AnalyzeClasses`), each exercised by a plain `@Test` method that calls `.check(classes)`
  directly. **This task's own ArchUnit rule MUST use that exact shape**, not the
  `auth-service`/`crypto-service` shape, or it will silently not run.
- **Validation convention**: every prior `@ConfigurationProperties` validation in this service
  (T03) uses `jakarta.validation` annotations (`@NotBlank`, `@Positive`, etc.) plus
  `BindValidationException` as the real failure type at the config layer. For a `@Service`-layer
  business-rule rejection (not config binding), `crypto-service`'s own precedent
  (`TokenValidator`) throws a plain unchecked exception (`IllegalArgumentException`) rather than
  a bean-validation annotation — but `payment-service`'s own R3/R4 text says the HTTP layer must
  return a `400` RFC 9457 problem, which is a later task's concern (T06, the controller) unless
  `InvoiceService.create` itself is expected to throw a typed exception a future
  `@ExceptionHandler` maps to 400. **Flagged as a Phase 1/2 question**, not assumed.

## 4. Testing conventions

- Unit tests: plain JUnit 5, no Spring context, fixed `Clock.fixed(...)` where time matters
  (established by every T01-T04 unit test in this service).
- ArchUnit tests: see the mandatory shape constraint above (plain fields + `@Test` canaries, no
  `@ArchTest`).
- No Testcontainers precedent yet for a repository-layer test in this service beyond
  `SchemaBaselineMigrationIntegrationTest` (T02, schema-shape only, no entity). If this task
  needs a Testcontainers-backed `InvoiceRepository` round-trip test, there is no existing
  `payment-service` precedent to mirror structurally — `crypto-service`'s own entity-repository
  integration tests would be the closer template.

## 5. Known gaps / unknowns (I do not know — not assumed)

1. **Does `invoices` need a new grant migration (`V3__...sql`) before `InvoiceRepository` can
   read/write it?** T02's own `payment_app` grants cover only `ledger_entries`/
   `verification_records`. Not resolved here.
2. **Does R3's "not a recognised `<chain, contractAddress>` shape" mean local format validation
   only (chain ∈ known set + a loose address-shape check), or a real cross-service allowlist
   lookup against `crypto-service`'s `TokenAllowlist`?** No contract (`crypto-internal.yaml`) has
   a token-allowlist-check endpoint, and `design.md`'s own Crypto Service contract section (Q7)
   lists only `/internal/v1/watches` and `/internal/v1/attest` — strongly suggests local-only
   validation, but not confirmed as a design decision anywhere in this task's own text.
3. **What exception type does `InvoiceService.create` throw on an R3/R4 violation**, and does
   that type already have (or need) an `@ExceptionHandler`/`@ControllerAdvice` mapping to the
   RFC 9457 `400`? No controller exists yet (T06), so no handler exists to check against.
4. **Is `expires_at` computed inside `InvoiceService.create` from
   `themistra.payment.invoice.default-ttl-minutes` (already configured, T03), or passed in?**
   Not stated in task 5's own text.

Phase 1 (Specification Extraction) resolves what the spec itself answers; anything still open
after that goes to Phase 3 (Kimi design challenge) as a named finding, not a silent assumption.
