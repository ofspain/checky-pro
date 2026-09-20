# crypto · T19 · Phase 2 — Task Implementation Brief

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
  `screening_results.outcome`'s own three-value CHECK constraint exactly (no fourth value, no renaming).
- `screening.ScreeningClient` — interface, one method:
  `ScreeningOutcome screen(String chain, String address, String txHash)` (`txHash` nullable, matching
  the DDL's own nullable `tx_hash` column — a counterparty can be screened before a specific transaction
  exists). Javadoc states the contract every implementation must honor: **every call persists exactly
  one `ScreeningResult` row before returning**, regardless of outcome — this is the interface's own
  contract, not something a future caller must remember to do, so the audit trail (L12) exists no matter
  which implementation is wired in.
- `screening.FailClosedScreeningClient` — the stub `@Component` implementing `ScreeningClient`. Performs
  no network I/O of any kind. Unconditionally builds and saves a `ScreeningResult` with
  `outcome = ERROR`, `provider = "fail-closed-stub"`, `raw_response = null`, then returns `ERROR`. Never
  returns `CLEARED` for any input, by construction — there is no code path in this class that produces
  that value.
- `screening.ScreeningResult` — JPA entity mapping `chain.screening_results` (already shipped by `V1`)
  exactly: `chain`, `address`, `txHash` (nullable), `outcome` (converted via a `DbConverter` mirroring
  `observation.FactType`'s own enum-to-`VARCHAR` pattern), `provider`, `rawResponse` (nullable
  `@JdbcTypeCode(SqlTypes.JSON)` `String`, mirrors `Observation.rawResponse`'s mapping), `screenedAt`.
  No setters; `protected` no-arg constructor for JPA; public static `create(...)` factory with
  `Objects.requireNonNull` on every non-nullable reference parameter, matching
  `TokenAllowlist`/`Observation`'s established shape.
- `screening.ScreeningResultRepository` — package-private `JpaRepository<ScreeningResult, Long>`, no
  finder methods beyond what `JpaRepository` already provides (nothing in this task's own scope needs
  to query a prior result back).
- New Flyway migration `V9__crypto_app_screening_results_grant.sql` — grants `crypto_app` `INSERT,
  SELECT` on `chain.screening_results`, mirroring `V5`'s reasoning for `token_allowlist`: each screening
  attempt is a new, append-only row, never revised in place; no `UPDATE`, no `DELETE`.
- `screening.ScreeningModuleBoundaryTest` — new source-scan test for the new `screening/` package,
  matching `WatchModuleBoundaryTest`/`ReorgModuleBoundaryTest`'s established one-per-package style;
  since `screening/` needs no other feature module's entity (only `common`/JDK/JPA/Jackson types), every
  other feature-module prefix is fully forbidden — the tightest boundary test in the codebase after
  `ReorgModuleBoundaryTest`.

**Out:**
- Wiring `ScreeningClient` into any caller. No `AttestationService`, `AttestController`, or any HTTP
  endpoint exists yet (task 21). This task produces a callable, self-contained, tested component with no
  consumer in this codebase yet.
- The real vendor adapter (Chainalysis/TRM/Elliptic) — blocked on Q2, explicitly deferred by the task
  statement itself ("Wire the real vendor once Q2 is answered").
- The compliance-queue mechanism R21 mentions ("place the item in the compliance queue") — that is part
  of task 21's `AttestationService` gate logic, not this task's screening-primitive scope. This task only
  guarantees the audit-trail row (`ScreeningResult`) that a compliance queue could later be built from;
  it does not build a queue.
- Any `chain.*` event emission — no event schema in `design.md` §4c's `EventTopics` mapping covers
  screening, and none is required by R21 (R21's own response shape is a direct HTTP `{outcome, reason}`
  from `/attest`, not an emitted event).
- Any change to `contracts/api/crypto-internal.yaml` — the `/attest` request/response shape it defines is
  task 21's to implement against.
- Address format validation — `token.AddressValidator` already owns EIP-55/Base58 checksum validation;
  `ScreeningClient.screen` accepts whatever address string a future caller passes and does not
  re-validate its format.

## Business Rules

- **R21.** A sanctioned/OFAC hit under screening must produce `BLOCKED`, a compliance-queue placement,
  and no signature. *(This task builds the screening primitive and its persisted audit trail; the
  `BLOCKED` HTTP response and compliance-queue placement are task 21's to enact.)*

## Locked Decisions

- **L12.** Screening gates attestation and fails closed: an unreachable/unimplemented vendor must never
  be treated as `CLEARED`. Enacted here by `FailClosedScreeningClient` having no code path that returns
  `CLEARED`.

## Dependencies

`common.ClockConfig`'s `Clock` bean (for `screenedAt`); `observation.FactType.DbConverter` (pattern
precedent only — a converter with the identical shape is written fresh in `screening/`, not shared
across modules, per `agents.md`'s "each module owns its entities" rule); Jackson (`ObjectMapper`) is
**not** a dependency of the stub — it persists `raw_response = null` since it has no real response body
to serialize.

## Inputs

`(chain, address, txHash)` — three plain `String`s passed by a future caller; no request DTO exists in
this task's scope (no controller).

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
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/FailClosedScreeningClientTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultRepositoryIntegrationTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningModuleBoundaryTest.java`

## Files to Modify

None. This task adds a self-contained new module with no existing caller to rewire.

## Files NOT to Modify

- `V1`-`V8` migrations — frozen; `V1`'s `screening_results` DDL is used exactly as shipped, no column
  added or changed.
- `watch/`, `token/`, `quorum/`, `finality/`, `adapter/`, `provider/`, `observation/`, `reorg/`,
  `events/` — called by nothing in this task and call nothing of this task's; untouched.
- Any file under `spec/`.
- `contracts/`.

## Acceptance Criteria

- **AC1 (R21/L12).** `ScreeningOutcome` has exactly the three values `CLEARED`, `BLOCKED`, `ERROR`,
  matching the DDL's CHECK constraint.
- **AC2 (L12).** `FailClosedScreeningClient.screen(...)` returns `ERROR` for every input tested,
  including varied `chain`/`address` values and both a present and a `null` `txHash` — never `CLEARED`.
- **AC3 (L12/R21, audit trail).** Every call to `FailClosedScreeningClient.screen(...)` results in
  exactly one new `ScreeningResult` persisted, with `outcome = ERROR`, before the method returns.
- **AC4 (schema fidelity).** `ScreeningResult.create(...)` rejects `null` for every non-nullable
  parameter (`chain`, `address`, `outcome`, `provider`, `screenedAt`); `txHash` and `rawResponse` accept
  `null` (matching the DDL's own nullability).
- **AC5 (persistence/grant correctness).** An integration test, connected as the real `crypto_app` role
  (not the Flyway owner role), successfully inserts and reads back a `ScreeningResult` row after `V9`
  applies.
- **AC6 (module boundary).** `screening/` imports no other feature module's entity or repository type;
  a source-scan test enforces this with every other feature-module prefix fully forbidden.
- **AC7 (no network I/O).** `FailClosedScreeningClient` makes no HTTP/RPC call — verified by the class
  containing no such dependency at all (a structural fact, not something a runtime test can meaningfully
  assert beyond "no such client field/import exists").

## Required Tests

- `FailClosedScreeningClientTest`: always-`ERROR` behavior (AC2) across varied inputs including a `null`
  `txHash`; persists exactly one `ScreeningResult` per call (AC3), asserted via a mocked
  `ScreeningResultRepository` capturing the saved entity's fields.
- `ScreeningResultTest`: entity factory null-checks (AC4); accepts `null` `txHash`/`rawResponse`.
- `ScreeningResultRepositoryIntegrationTest` (Testcontainers, real `crypto_app` role): round-trip save
  and read of a fully-populated row and of a row with `null` `txHash`/`rawResponse` (AC5).
- `ScreeningModuleBoundaryTest` (AC6).
- Named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty` (R21): **not achievable in full
  within this task's scope** — no `/attest` endpoint exists yet. Documented in Phase 10's test manifest
  as partially satisfied at the component level (this task proves the fail-closed floor the eventual
  `BLOCKED` behavior will be built on); full ownership belongs to task 21.

## Constraints

- **Performance:** none — a single synchronous DB insert per call, no polling, no scheduling.
- **Security:** no new endpoint, no new secret, no new scope. `raw_response` must never itself be used to
  smuggle a monetary value as a bare JSON number if a future real vendor payload includes one — not
  applicable to the stub (`raw_response = null`), noted for the real-vendor task.
- **Thread-safety:** `FailClosedScreeningClient` is stateless (a `@Component` singleton bean); no shared
  mutable state.
- **Transaction:** `screen(...)`'s single repository `save` is already transactional via Spring Data; no
  outer `@Transactional` needed since there is exactly one write and no companion outbox/event write in
  this task's scope (unlike `ReorgDetector`/`TxLifecyclePublisher`, which coordinate a save alongside a
  separate outbox publish).
- **Module boundaries:** `screening/` is a genuinely new top-level package with no legitimate dependency
  on any other feature module — the tightest boundary test in the codebase, matching
  `ReorgModuleBoundaryTest`'s precedent for a package with no cross-module needs at all.
- **Null handling:** `txHash` and `rawResponse` are the DDL's only nullable columns; every other field is
  `NOT NULL` and factory-guarded.

## Open Questions

No blockers. Q2 (vendor choice) and Q3 (fail-open/closed confirmation) are both explicitly out of this
task's scope per the task statement itself and per L12's already-LOCKED fail-closed default (Phase 1).
