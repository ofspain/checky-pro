# payment · T08 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7, `mvn -pl services/payment test`: 177 tests run,
0 failures — includes `SchemaBaselineMigrationIntegrationTest`, confirming Docker was available in
that environment). 7 of 8 gaps were straightforward test-coverage additions. Gap 1/2's own
resolution surfaced a genuine, previously-undetected **production bug**, fixed directly (no Human
Approval gate needed — self-evident correctness defect, same established precedent as T06/T08
Phase 7/9).

## The production bug found while resolving Gaps 1/2

Writing the Testcontainers-backed integration test Kimi's Gap 1 asked for required actually
seeding and running `InvoiceRepository.updateStateIfCurrentState` over a real `payment_app`
connection — something no existing test did (every `InvoiceService`/`InvoiceRepository` test to
this point mocks the repository). Re-reading every grant migration against the real query
surfaced the gap *before* even running the new test (Docker is unavailable this session, so it
could not be run locally either): **`V3__payment_app_invoice_and_outbox_grants.sql` granted only
`INSERT, SELECT` on `payments.invoices`, and no later migration ever added `UPDATE`.**
`updateStateIfCurrentState` (Phase 9's own atomic conditional update — the fix for the Phase 8
lost-update race) issues a real `UPDATE payments.invoices ...` as `payment_app`. Without the
grant, this call would fail with a real Postgres permission-denied error the first time it ever
ran against a live database — meaning the Phase 9 fix for the race condition would itself have
been non-functional in production. `SchemaBaselineMigrationIntegrationTest`'s own
`grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore` test was, until this task, actively
asserting the *absence* of this grant — correct until this task's own code needed otherwise.

**FIXED:**
- Added `V6__payment_app_invoice_update_grant.sql` (`GRANT UPDATE ON payments.invoices TO
  payment_app`), with a Javadoc-style comment explaining why and how this was found.
- `SchemaBaselineMigrationIntegrationTest`: removed `invoices` from the exactly-INSERT-SELECT
  `GRANTED_TABLES` list; added a dedicated `invoicesHasExactlyInsertSelectAndUpdatePrivilegesNoMore`
  test (mirrors the `outbox`/`shedlock` precedent exactly); updated the Flyway-history version
  assertion to `"1".."6"`.
- Could not re-run `SchemaBaselineMigrationIntegrationTest` against a real database this session
  (Docker unavailable) — same disclosed, carried-forward gap as T06/T07/T08 Phase 7/8/9; expected
  to be empirically confirmed next time Kimi's own Docker-enabled environment runs it (the same
  pattern that confirmed T07's and this task's own earlier grant fixes).

## Gap 1 — Native candidate query never executed against a real database

**FIXED** (see above) — added `InvoiceRepositoryIntegrationTest.java`, a new Testcontainers-backed
test mirroring `WatchRepositoryIntegrationTest`'s (crypto-service) own narrow-context pattern:
connects as `payment_app`, seeds invoices via the repository and payment rows via a raw admin
connection (no `Payment` JPA entity exists yet), and asserts
`findExpiredCandidatesWithoutInFlightPayment` returns exactly the truly-eligible invoice set
across all 4 non-terminal payment states, a terminal-payment invoice (eligible), a not-yet-expired
invoice, and an already-`EXPIRED` invoice. Cannot be run locally this session (Docker unavailable)
— disclosed in the class's own Javadoc.

## Gap 2 — Structural proof was too narrow

**FIXED**, both ways Kimi suggested: extended the existing reflection-based structural test
(`shouldNotExpireInvoiceWithPaymentInFlight`) to also assert `state = 'OPEN'`, `expires_at <=
:now`, and `NOT EXISTS` are present in the real `@Query` value; *and* added the integration test
from Gap 1 as the authoritative behavioral proof, per Kimi's own "better, replace the structural
test with the integration test" — both are kept (the structural test is a fast, no-Docker-required
regression guard; the integration test is the real proof once Docker is available).

## Gap 3 — No test that `sweep()` is `@Scheduled`

**FIXED.** Added `sweepMethodIsAnnotatedScheduled` (`InvoiceExpirySweepJobTest`) — reflection-based,
asserts the annotation is present and its `cron` attribute resolves to the expected property
placeholder.

## Gap 4 — No test that `expireInvoice` is `@Transactional`

**FIXED.** Added `expireInvoiceMethodIsAnnotatedTransactional` (`InvoiceServiceTest`) —
reflection-based (not source-text, which `InvoiceExpirySweepJobTest`'s own
`sweepMethodIsNotAnnotatedTransactional` already found can false-positive on Javadoc prose that
mentions `@Transactional`).

## Gap 5 — `unregisterWatch` DELETE test doesn't assert the URI path

**FIXED.** Added `shouldSendDeleteRequestToTheWatchIdSpecificUriOnUnregisterWatch` — captures and
asserts the real request path equals `/internal/v1/watches/{watchId}`.

## Gap 6 — No test that `unregisterWatch` sends the bearer token

**FIXED.** Added `shouldSendBearerTokenOnUnregisterWatch`, mirroring `registerWatch`'s own
`shouldSendBearerTokenFromProvider`.

## Gap 7 — No test verifying `updatedAt` is part of the conditional update

**FIXED**, both ways: added `updateStateIfCurrentStateQuerySetsUpdatedAt` (structural, reflection
on the real `@Query` value) *and* the integration test from Gap 1
(`updateStateIfCurrentStateActuallyUpdatesStateAndUpdatedAtForARealOpenInvoice`) asserts the real
`updated_at` column value advances.

## Gap 8 — Residual race is only documented, not tested

**FIXED.** Extended `shouldNotThrowWhenInvoiceWasNoLongerOpenWhenExpiryWasAttempted` with a
`ch.qos.logback` `ListAppender` (mirrors `HeldFactAlerterTest`'s own identical pattern,
crypto-service) to assert the disclosed residual race is actually logged at `WARN` with the
invoice's own UUID and the expected message fragment, not silently swallowed.

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'`
  — **162/162 passing** (up from 157; the two Testcontainers-backed classes cannot run locally
  this session, same disclosed Docker-unavailable gap carried forward).
- Mutation verification (each via `Edit`, confirmed red, reverted via `Edit`):

| Mutation | Test(s) that caught it |
|---|---|
| `expireInvoice`'s `@Transactional` removed | `expireInvoiceMethodIsAnnotatedTransactional` |
| `sweep()`'s `@Scheduled` cron replaced with a literal | `sweepMethodIsAnnotatedScheduled` |
| `updateStateIfCurrentState`'s `i.updatedAt = :now` dropped | `updateStateIfCurrentStateQuerySetsUpdatedAt` |
| `unregisterWatch`'s URI dropped the `{watchId}` segment | `shouldSendDeleteRequestToTheWatchIdSpecificUriOnUnregisterWatch` |
| `processOne`'s residual-race WARN message text changed | `shouldNotThrowWhenInvoiceWasNoLongerOpenWhenExpiryWasAttempted` |

(The V6 grant itself, and the new `InvoiceRepositoryIntegrationTest`, cannot be mutation-verified
locally this session — Docker unavailable; both are new real-database-dependent checks, not
regressions of existing mocked behavior.)

## Net result

All 8 Kimi findings resolved. One additional, more significant finding (the missing `UPDATE` grant
on `invoices`) was self-identified while doing the work Gap 1 asked for and fixed immediately,
with a new migration, an updated schema-grant test, and the disclosure of why no prior test could
have caught it. Proceeding to Phase 12.
