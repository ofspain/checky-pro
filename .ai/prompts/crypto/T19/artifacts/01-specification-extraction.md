# crypto · T19 · Phase 1 — Specification Extraction

## Business Rules

- **R21.** IF the counterparty address for an attest request is a sanctioned/OFAC hit under screening,
  THEN the system SHALL return `{ outcome: "BLOCKED", reason }`, place the item in the compliance queue,
  and SHALL NOT produce a signature. *(This task builds the screening capability and persistence R21
  depends on; wiring R21's actual `BLOCKED` HTTP response into `/attest` is task 21's scope, not this
  one — this task's own deliverable is the `ScreeningClient` interface, its fail-closed stub, and
  `ScreeningResult` persistence that task 21 will consume.)*

## Locked Decisions

- **L12. Screening gates attestation, fail-closed** (`design.md` §4a): before signing, the counterparty
  address is screened (Chainalysis/TRM/Elliptic per Q2); an OFAC/sanctioned hit → `BLOCKED`, compliance
  queue, no signature. If the screening API is unreachable, attest **fails closed** (no signature) unless
  the author overrides via Q3. *(The compliance-queue/`BLOCKED`-response half of this decision is
  enacted by task 21's `AttestationService`; this task's own responsibility is that the interface and
  stub make fail-closed the only possible outcome of an unreachable/unimplemented vendor — never a
  silent pass-through to `CLEARED`.)*

## Files involved

**Existing, to read/extend:**
- `spec/crypto-service/design.md` §4c `V1__chain_baseline.sql` (lines 86-97) — `chain.screening_results`
  DDL, already shipped, frozen, not to be altered by any new migration.
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — contains the already-created
  `screening_results` table (chain, address, tx_hash, outcome, provider, raw_response, screened_at).
  Do not edit (Flyway migrations are immutable once merged).
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` — confirms
  `crypto_app` has **no** grant on `screening_results` today; a new migration must add one.
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlist.java` /
  `TokenAllowlistRepository.java` — closest existing precedent for a simple entity + repository pair
  with a signed/versioned append-only shape.
- `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java` — precedent for a
  `JSONB` raw-response column (`@JdbcTypeCode(SqlTypes.JSON)` over a pre-serialized `String`), directly
  analogous to `screening_results.raw_response`.
- `services/crypto/src/main/resources/db/migration/V5__crypto_app_token_allowlist_grant.sql` — precedent
  for a standalone, single-purpose grant migration with a comment explaining why `V2` didn't already
  cover the table.
- `spec/crypto-service/agents.md` — authoritative standing rules (package-by-feature layout, Flyway
  DDL-only/immutable migrations, fixed `Clock`, no real vendor calls in tests/CI, RFC 9457 errors).

**New, expected by the spec's own package map (`design.md` §6):**
- `screening/ScreeningClient.java` — interface.
- `screening/ScreeningResult.java` — JPA entity mapping `chain.screening_results`.
- `screening/ScreeningResultRepository.java` — `JpaRepository`.
- A fail-closed stub implementation of `ScreeningClient` (name not dictated by the spec; a Phase 2/5
  design choice, e.g. `FailClosedScreeningClient` or `StubScreeningClient`).
- A new Flyway migration granting `crypto_app` the privileges it needs on `chain.screening_results`
  (`V9__...` — next free version number after `V8`).

## Dependencies

- `chain.screening_results` table (already exists, per `V1`) — columns `chain VARCHAR(32)`,
  `address VARCHAR(128)`, `tx_hash VARCHAR(128)` nullable, `outcome VARCHAR(16)` (CHECK
  `CLEARED|BLOCKED|ERROR`), `provider VARCHAR(64)`, `raw_response JSONB`,
  `screened_at TIMESTAMPTZ DEFAULT now()`.
- `common.ClockConfig`'s injectable `Clock` bean — required for `screened_at` in tests (`agents.md`: no
  `java.util.Date`, no unfixed wall-clock reads in tests).
- No dependency on `events.OutboxPublisher` — no event is emitted by this task (R21's `chain.tx.*`
  events are all scoped to earlier tasks; screening produces no new `chain.*` topic per `EventTopics`'s
  own mapping in `design.md` §4c, which lists no screening-related topic).
- No dependency on `attest/` (does not exist yet — task 20/21) or `KmsSigner` (task 20).
- `contracts/api/crypto-internal.yaml` — the `/attest` response shapes (`BLOCKED`) are defined here but
  are task 21's to implement against; this task does not touch the contract file.

## Acceptance Criteria

Mapped from the task statement, `agents.md` §9's checklist line ("Attest refuses to sign unless quorum +
finality (+ screening) passed (L10, L12)" — the screening-availability half of that precondition is what
this task must make true), and L12's own text:

1. **AC1 (R21/L12).** `ScreeningClient` is a plain interface with a method to screen a
   `(chain, address)` pair (exact signature is a Phase 2 design choice) and return a screening
   outcome — `CLEARED`, `BLOCKED`, or `ERROR` — matching `screening_results.outcome`'s own three-value
   check constraint.
2. **AC2 (L12).** The stub implementation of `ScreeningClient` is **fail-closed**: since no real vendor
   is wired (Q2 unanswered), it must never return `CLEARED` for any input — it returns/produces
   `ERROR` (or an equivalent non-clearing outcome) unconditionally, so a caller relying on it can never
   mistake "vendor not wired yet" for "counterparty confirmed clean."
3. **AC3 (L12/R21).** Every screening attempt (stub or, later, real vendor) is persisted as a
   `ScreeningResult` row — `chain`, `address`, `tx_hash` (nullable per the DDL), `outcome`, `provider`,
   `raw_response`, `screened_at` — via `ScreeningResultRepository`, before/regardless of what the caller
   does with the outcome, forming the audit trail L12 and the compliance-queue placement (task 21) will
   depend on.
4. **AC4 (`agents.md` schema rule).** No screening code introduces a floating-point or JSON-number money
   value — not directly applicable here since `screening_results` carries no monetary column, but flagged
   because `raw_response` is free-form `JSONB` and must not itself become a place where a later caller
   stuffs an amount as a bare number if one is ever included in a vendor payload.
5. **AC5 (persistence/grant correctness).** `crypto_app` can `INSERT` (and `SELECT`, mirroring every
   other table's grant shape) on `chain.screening_results` after this task's migration — verified by an
   integration test actually writing through the real role, not just asserting SQL text.
6. **AC6 (module boundary, `agents.md`).** `screening/` does not import another feature module's entity
   type across module boundaries (mirrors `L15`'s general rule); if `ScreeningResult`/`ScreeningClient`
   have no legitimate reason to depend on `watch`/`token`/`quorum`/etc., a module-boundary test should
   assert that, per the `WatchModuleBoundaryTest`/`ReorgModuleBoundaryTest` precedent.
7. **AC7 (no real vendor call, ever, in this task).** The stub must not perform any network I/O — it is
   pure/local, consistent with `agents.md`'s "real RPC providers are never called in tests or CI" and
   this task's own explicit deferral of the vendor to a later task once Q2 is answered.

## Tests required

- **Named test (`package.md` §8):** `shouldReturnBlockedFromAttestOnSanctionedCounterparty` → R21. This
  test's literal name refers to the `/attest` endpoint's behavior, which does not exist until task 21.
  For this task, the equivalent, in-scope proof is that the fail-closed stub can never itself produce a
  `CLEARED` result — the task 21 test will compose this task's `ScreeningClient` with the not-yet-built
  `AttestationService` to prove the full `BLOCKED` HTTP outcome. This task should note the named test as
  **partially satisfied at the component level only**, not claim full ownership of it.
- **Boundary/unit tests implied by scope:**
  - `ScreeningClient`'s stub always returns a non-`CLEARED` outcome, for any input (including edge cases:
    empty/malformed address — though address *validation* itself is `token.AddressValidator`'s job,
    not this task's, so only screening-specific behavior should be tested here).
  - `ScreeningResult` entity: construction/factory null-checks, matching the `TokenAllowlist`/`Observation`
    convention.
  - `ScreeningResultRepository` integration test (Testcontainers): a saved row round-trips with all
    columns, uses the fixed `Clock` for `screened_at`, and the new grant migration actually permits the
    write under the real `crypto_app` role (not the Flyway/owner role).
  - A module-boundary test for `screening/`, if Phase 2/5 design concludes one is warranted (see AC6).

## Open Questions

- **Q2 (`package.md` §11) — screening vendor.** Explicitly unanswered; this task's own task statement
  defers it ("Wire the real vendor once Q2 is answered"). Not a blocker for this task — the fail-closed
  stub is the entire deliverable.
- **Q3 (`package.md` §11) — fail-open vs. fail-closed on screening outage.** `design.md` §4a-L12 already
  states the LOCKED default (fail-closed) "unless the author overrides via Q3." No override exists
  anywhere in the current codebase (confirmed in Phase 0 — `attest/` doesn't exist yet, and no
  screening-related code exists yet). Not a blocker for this task: fail-closed is what the stub already
  must do, per L12, with or without Q3 ever being formally answered.
- **Genuine blocker for a later task, not this one:** the exact `ScreeningClient` method signature and
  return-type shape (a sealed result type vs. a checked exception vs. an enum outcome) is left to
  Phase 2/5 design, since `design.md` §4c contains no verbatim interface text for `ScreeningClient` (only
  `O4` in §4b marks it as an OPEN decision the implementer proposes) — not a blocker, just explicitly not
  pre-decided by the spec.
