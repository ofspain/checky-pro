# crypto · T19 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Add crypto-service screening client and fail-closed stub (T19)
```

## Commit message

```
Add crypto-service screening client and fail-closed stub (T19)

Introduce the screening module L12/R21 require before any attestation
signs: a ScreeningClient interface, a fail-closed stub implementation
(FailClosedScreeningClient), and ScreeningResult persistence recording
every screening attempt against chain.screening_results (shipped by
V1 but never granted or used until now).

No real vendor is wired in - Q2 (Chainalysis/TRM/Elliptic) is still
unanswered - so the stub never returns CLEARED or BLOCKED for any
input; it always returns ERROR, since claiming a sanctioned hit was
actually detected would be exactly as false as claiming one was ruled
out when no real screening ever ran. Two new task-scoped Locked
Decisions make this unambiguous for task 21's future AttestationService
caller: only CLEARED may ever lead to a signature (BLOCKED and ERROR
are both fail-closed, non-retryable), and an exception from screen(...)
- including a failed persistence - propagates uncaught and must be
treated identically to an ERROR return.

Every screen(...) call persists exactly one ScreeningResult row before
returning, forming the audit trail a future compliance queue (task 21)
can be built from. New migration V9 grants crypto_app INSERT/SELECT
on screening_results - the sole V1 baseline table with no grant at
all until this task.

Ripple: ChainBaselineMigrationIntegrationTest's hardcoded Flyway
version list and ungranted-tables assertion needed updating for V9,
the same category of unavoidable cross-cutting update T17 and T18
both hit with this identical file.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningOutcome.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResult.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningResultRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/ScreeningClient.java`
- `services/crypto/src/main/java/com/themistra/crypto/screening/FailClosedScreeningClient.java`
- `services/crypto/src/main/resources/db/migration/V9__crypto_app_screening_results_grant.sql`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningOutcomeTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/FailClosedScreeningClientTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningResultRepositoryIntegrationTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/screening/ScreeningModuleBoundaryTest.java`

**Modified:**
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  Flyway-version-list assertion extended to include `"9"`; `UNGRANTED_TABLES` emptied now that
  `screening_results` (the sole remaining ungranted `V1` baseline table) is deliberately granted.

12 files changed (11 created, 1 modified), +793/-3 lines. New migration `V9` (additive grant only — no
new table, column, or DDL change; `V1`'s `screening_results` table is used exactly as shipped).

## Summary

Gives the platform the counterparty-screening primitive L12/R21 require before any attestation signs,
without depending on a chosen OFAC/sanctions vendor (Q2, still unanswered). `ScreeningClient` is a small,
self-contained interface with exactly one implementation today, `FailClosedScreeningClient`, which
performs no network I/O and can never return `CLEARED` or `BLOCKED` — only `ERROR`, since no real
screening ever ran. Every call persists a `ScreeningResult` audit-trail row via the new `V9` grant on
`chain.screening_results`, `V1`'s own frozen DDL used exactly as shipped since T02.

This task has no consumer yet — no `AttestController`/`AttestationService`/`/attest` endpoint exists
until task 21 — so it ships as a fully tested, self-contained module with a documented contract
(persist-then-return; fail-closed for any non-`CLEARED` outcome; exceptions propagate uncaught) that
task 21's future caller will build against. The named test `shouldReturnBlockedFromAttestOnSanctionedCounterparty`
is therefore only partially satisfied here — this task proves the fail-closed floor; full ownership
belongs to task 21.

Both review phases (Phase 8 independent review, Phase 11 test review) ran to completion with findings
folded in via human-approved dispositions: 6 of 10 Phase 9 findings accepted and fixed (null-check
ordering, Javadoc completeness, log content, a mischaracterized code comment, an exhaustive module-
boundary test, a multi-call persistence test), 3 rejected as inconsistent with this codebase's own
established precedent (`ReorgDetector`'s unguarded constructor, no entity anywhere blank-string-validates
its `String` fields); 6 of 8 Phase 11 gaps accepted and folded into the suite (a converter unit test, an
exception-propagation test, a denied-delete row-still-exists assertion, a DB-level CHECK-constraint
test, a `CLEARED` round-trip, an entity-immutability guard), 2 rejected (one re-litigating an
already-settled Phase 4 disposition on log-capture testing, one re-raising the same already-rejected
blank-string-validation ask).

## Testing performed

- `mvn -pl services/crypto test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=ScreeningOutcomeTest,ScreeningResultTest,FailClosedScreeningClientTest,ScreeningModuleBoundaryTest,ScreeningResultRepositoryIntegrationTest` — 28/28 pass.
- `mvn -pl services/crypto test -Dtest=ChainBaselineMigrationIntegrationTest` — 10/10 pass (ripple fix verified).
- `mvn -pl services/crypto -am test` (full module regression) — 640 tests, 6 failures, all pre-existing
  and unrelated to this task (disclosed since T18: `ObservationRepositoryIntegrationTest` ×2,
  `ProviderHealthRepositoryIntegrationTest` ×1, `QuorumDecisionRepositoryIntegrationTest` ×1,
  `TokenAllowlistRepositoryIntegrationTest` ×2 — a JSON-spacing assertion, three
  DB-permission-vs-Hibernate-exception-wrapping mismatches, and one order-dependent failure confirmed,
  by isolated re-run, to reproduce with zero involvement of this task's own code). Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`: `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 19 ("Screening client").
- **Requirements:** R21 (screening capability + audit trail this task delivers; the `BLOCKED` HTTP
  response and compliance-queue placement remain task 21's scope).
- **LOCKED decisions:** L12 (screening gates attestation, fail-closed). Two new task-scoped Locked
  Decisions this task itself introduced at Phase 4: L12-T19a (only `CLEARED` may lead to a signature)
  and L12-T19b (an exception from `screen(...)` propagates uncaught, fail-closed).
