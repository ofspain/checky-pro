STATUS: FROZEN

# crypto · T19 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

| # | Finding | Disposition | Rationale |
|---|---|---|---|
| 1 | Stub returns `ERROR`, not `BLOCKED` — ambiguous for task 21 | **ACCEPTED, MODIFIED** | Stub keeps returning `ERROR` (returning `BLOCKED` would falsely claim a sanctioned hit was actually detected, when no vendor ever ran). New Locked Decision added instead: any `ScreeningOutcome` other than `CLEARED` — i.e. `BLOCKED` or `ERROR` — is fail-closed for callers; task 21 must gate signing on `outcome == CLEARED` alone, never special-case `ERROR` as retryable. |
| 2 | Interface can't enforce its "persist one row" contract | **REJECTED** | A decorator/template-wrapper mechanism is premature abstraction — exactly one implementation exists in this task, and the codebase's own standing discipline (`agents.md`, this pipeline's guardrails) rejects designing for a hypothetical second implementation. The Javadoc contract plus this task's own tests stand as sufficient enforcement for now; revisit if/when a second implementation is ever added. |
| 3 | No `reason` field despite R21's `{outcome, reason}` shape | **DOCUMENT-ONLY** | `V1`'s `screening_results` DDL is frozen and carries no `reason` column; adding one is a schema change out of this task's declared scope. Moot for this task specifically, since the stub never returns `BLOCKED` (only `CLEARED`/`ERROR` are reachable, and only `ERROR` is ever actually produced) — `reason`-sourcing for a real `BLOCKED` result is deferred to whichever task wires the real vendor. |
| 4 | Undefined behavior when persistence itself fails | **ACCEPTED** | Documented as a Locked Decision: `screen(...)` does not catch a persistence failure — it propagates uncaught. Callers must treat any exception from `screen(...)` as fail-closed (no signature), exactly as they must treat `ERROR`/`BLOCKED`. This is a documentation-only decision; no catch-and-swallow logic is added (swallowing a DB failure here would itself risk masking real infrastructure problems). |
| 5 | No address-format validation in `ScreeningClient` | **ACCEPTED** | Documented as already out of scope (`token.AddressValidator` owns format validation). New required test: a malformed address is persisted and screened as-is, proving the component does not silently reject it. |
| 6 | No test locks `ScreeningOutcome`'s 3 values against the DDL | **ACCEPTED** | New required test: `ScreeningOutcomeTest` asserts `values()` is exactly `{CLEARED, BLOCKED, ERROR}`. |
| 7 | Stub's `provider` string is a private, undocumented literal | **ACCEPTED** | `FailClosedScreeningClient` exposes `public static final String PROVIDER_NAME = "fail-closed-stub"`; a test asserts the persisted `ScreeningResult.provider()` equals it. |
| 8 | No logging in the stub | **ACCEPTED** | `FailClosedScreeningClient.screen(...)` logs a `warn` stating the fail-closed stub is active and no real vendor screened the address. No dedicated log-capture test — proportionate to a single log statement with no branching logic to lock in. |

## Task

Add a `screening` module: a `ScreeningClient` interface, a fail-closed stub implementation of it (no
real vendor — Q2 is unanswered), and `ScreeningResult` persistence recording every screening attempt.

## Purpose

Give the platform the counterparty-screening capability L12/R21 require before any attestation signs —
without yet depending on a chosen OFAC/sanctions vendor. Task 21's `AttestationService` will call this
interface as one of its three gates (quorum + finality + screening); until Q2 is answered, calling it
must never be mistaken for "counterparty cleared."

## Scope

**In:**
- `screening.ScreeningOutcome` — enum `{ CLEARED, BLOCKED, ERROR }`, matching
  `screening_results.outcome`'s own three-value CHECK constraint exactly.
- `screening.ScreeningClient` — interface, one method:
  `ScreeningOutcome screen(String chain, String address, String txHash)` (`txHash` nullable). Javadoc
  states: every call persists exactly one `ScreeningResult` row before returning, regardless of outcome;
  an exception from this method (including a persistence failure) is not caught internally and must be
  treated by the caller as fail-closed, identically to an `ERROR` return.
- `screening.FailClosedScreeningClient` — the stub `@Component` implementing `ScreeningClient`. Performs
  no network I/O. Unconditionally builds and saves a `ScreeningResult` with `outcome = ERROR`,
  `provider = PROVIDER_NAME` (a public constant, `"fail-closed-stub"`), `raw_response = null`, logs a
  `warn` that the fail-closed stub is active, then returns `ERROR`. Never returns `CLEARED` or `BLOCKED`
  for any input — there is no code path in this class producing either.
- `screening.ScreeningResult` — JPA entity mapping `chain.screening_results` (already shipped by `V1`)
  exactly: `chain`, `address`, `txHash` (nullable), `outcome` (converted via a `DbConverter` mirroring
  `observation.FactType`'s own enum-to-`VARCHAR` pattern), `provider`, `rawResponse` (nullable
  `@JdbcTypeCode(SqlTypes.JSON)` `String`), `screenedAt`. No setters; `protected` no-arg constructor;
  public static `create(...)` factory with `Objects.requireNonNull` on every non-nullable reference
  parameter.
- `screening.ScreeningResultRepository` — package-private `JpaRepository<ScreeningResult, Long>`.
- New Flyway migration `V9__crypto_app_screening_results_grant.sql` — grants `crypto_app` `INSERT,
  SELECT` on `chain.screening_results` (append-only, mirroring `V5`'s `token_allowlist` grant). No
  `UPDATE`, no `DELETE`.
- `screening.ScreeningModuleBoundaryTest` — new source-scan test; every other feature-module prefix is
  fully forbidden (mirrors `ReorgModuleBoundaryTest`'s precedent for a module needing no cross-module
  entity at all).

**Out:**
- Wiring `ScreeningClient` into any caller — no `AttestationService`/`AttestController`/HTTP endpoint
  exists yet (task 21).
- The real vendor adapter (Chainalysis/TRM/Elliptic) — blocked on Q2, explicitly deferred by the task
  statement.
- The compliance-queue mechanism R21 mentions — task 21's `AttestationService` gate logic, not this
  task's. This task only guarantees the audit-trail row a compliance queue could later be built from.
- Any `chain.*` event emission — no event schema covers screening; R21's own response shape is a direct
  HTTP body from `/attest`, not an emitted event.
- Any change to `contracts/api/crypto-internal.yaml`.
- Address format validation (`token.AddressValidator`'s job) — `ScreeningClient` accepts any string and
  does not re-validate format; a required test proves this explicitly (Phase 3 Finding #5).
- A `reason` field/column — `V1`'s DDL is frozen and has none; moot since the stub never returns
  `BLOCKED` (Phase 3 Finding #3, document-only).
- A persistence-enforcement decorator/wrapper — premature abstraction with a single implementation
  (Phase 3 Finding #2, rejected).

## Business Rules

- **R21.** A sanctioned/OFAC hit under screening must produce `BLOCKED`, a compliance-queue placement,
  and no signature. *(This task builds the screening primitive and its persisted audit trail; the
  `BLOCKED` HTTP response and compliance-queue placement are task 21's to enact.)*

## Locked Decisions

- **L12.** Screening gates attestation and fails closed: an unreachable/unimplemented vendor must never
  be treated as `CLEARED`. Enacted here by `FailClosedScreeningClient` having no code path that returns
  `CLEARED`.
- **L12-T19a (new, this task's own addition, Phase 4).** Any `ScreeningOutcome` other than `CLEARED` —
  i.e. `BLOCKED` or `ERROR` — is fail-closed for callers: only `CLEARED` may lead to a signature. `ERROR`
  must never be treated as retryable or transient by a caller.
- **L12-T19b (new, this task's own addition, Phase 4).** An exception thrown by `ScreeningClient.screen(...)`
  (including a failed `ScreeningResult` persistence) is not caught internally and must be treated by the
  caller as fail-closed, identically to an `ERROR` return.

## Dependencies

`common.ClockConfig`'s `Clock` bean (for `screenedAt`); `observation.FactType.DbConverter` (pattern
precedent only — a converter with the identical shape is written fresh in `screening/`).

## Inputs

`(chain, address, txHash)` — three plain `String`s passed by a future caller; no request DTO exists in
this task's scope.

## Outputs

- A `ScreeningOutcome` return value from `ScreeningClient.screen(...)`.
- Exactly one new `chain.screening_results` row per call.

## State Changes

- `INSERT` into `chain.screening_results` (new grant, `V9`).
- No `UPDATE`, no `DELETE`, ever, on this table.
- No change to any other table.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningOutcome.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningClient.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/FailClosedScreeningClient.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResult.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResultRepository.java`
- `services/crypto/src/main/resources/db/migration/V9__crypto_app_screening_results_grant.sql`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningOutcomeTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/FailClosedScreeningClientTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultRepositoryIntegrationTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningModuleBoundaryTest.java`

## Files to Modify

None. This task adds a self-contained new module with no existing caller to rewire.

## Files NOT to Modify

- `V1`-`V8` migrations — frozen; `V1`'s `screening_results` DDL is used exactly as shipped.
- `watch/`, `token/`, `quorum/`, `finality/`, `adapter/`, `provider/`, `observation/`, `reorg/`,
  `events/` — untouched.
- Any file under `spec/`.
- `contracts/`.

## Acceptance Criteria

- **AC1 (R21/L12).** `ScreeningOutcome` has exactly the three values `CLEARED`, `BLOCKED`, `ERROR`,
  matching the DDL's CHECK constraint (Phase 3 Finding #6).
- **AC2 (L12).** `FailClosedScreeningClient.screen(...)` returns `ERROR` for every input tested,
  including varied `chain`/`address` values, a present and a `null` `txHash`, and a malformed address
  string — never `CLEARED` or `BLOCKED` (Phase 3 Finding #5).
- **AC3 (L12/R21, audit trail).** Every call to `FailClosedScreeningClient.screen(...)` results in
  exactly one new `ScreeningResult` persisted, with `outcome = ERROR` and `provider = PROVIDER_NAME`,
  before the method returns (Phase 3 Finding #7).
- **AC4 (schema fidelity).** `ScreeningResult.create(...)` rejects `null` for every non-nullable
  parameter; `txHash` and `rawResponse` accept `null`.
- **AC5 (persistence/grant correctness).** An integration test, connected as the real `crypto_app` role,
  successfully inserts and reads back a `ScreeningResult` row after `V9` applies.
- **AC6 (module boundary).** `screening/` imports no other feature module's entity or repository type.
- **AC7 (no network I/O).** `FailClosedScreeningClient` makes no HTTP/RPC call.
- **AC8 (observability, Phase 3 Finding #8).** `FailClosedScreeningClient.screen(...)` logs a `warn`
  that the fail-closed stub is active.

## Required Tests

- `ScreeningOutcomeTest`: asserts the enum's exact 3-value set (AC1).
- `FailClosedScreeningClientTest`: always-`ERROR` behavior across varied inputs including `null` `txHash`
  and a malformed address (AC2); persists exactly one `ScreeningResult` per call with the expected
  `provider` constant (AC3).
- `ScreeningResultTest`: entity factory null-checks (AC4).
- `ScreeningResultRepositoryIntegrationTest` (Testcontainers, real `crypto_app` role): round-trip save
  and read of a fully-populated row and of a row with `null` `txHash`/`rawResponse` (AC5).
- `ScreeningModuleBoundaryTest` (AC6).
- Named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (R21): **not achievable in full
  within this task's scope** — no `/attest` endpoint exists yet. Partially satisfied at the component
  level (this task proves the fail-closed floor); full ownership belongs to task 21.

## Constraints

- **Performance:** none — a single synchronous DB insert per call.
- **Security:** no new endpoint, no new secret, no new scope.
- **Thread-safety:** `FailClosedScreeningClient` is stateless; no shared mutable state.
- **Transaction:** `screen(...)`'s single repository `save` is already transactional via Spring Data; no
  outer `@Transactional` needed (exactly one write, no companion outbox/event write).
- **Module boundaries:** `screening/` has no legitimate dependency on any other feature module.
- **Null handling:** `txHash` and `rawResponse` are the DDL's only nullable columns; every other field is
  `NOT NULL` and factory-guarded.
- **Fail-closed semantics (new, Phase 4):** only `outcome == CLEARED` may lead to a signature downstream;
  `ERROR`, `BLOCKED`, and any exception from `screen(...)` are all fail-closed, non-retryable outcomes.

## Open Questions

No blockers. Q2 (vendor choice) and Q3 (fail-open/closed confirmation) remain explicitly out of this
task's scope per the task statement and L12's already-LOCKED fail-closed default. The `reason`-field gap
(Phase 3 Finding #3) is documented above as deferred to the real-vendor task, not a blocker here.
