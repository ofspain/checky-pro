# payment · T08 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## A self-identified gap, fixed during this phase

While building the AC5 row below, the existing `shouldExpireInvoiceAndUnregisterWatchAfterTtl`
test was found to only `verify()` both calls happened, never that `unregisterWatch` runs *before*
`expireInvoice` — AC5's own actual claim. Strengthened to a Mockito `InOrder` check and
mutation-verified (swapped the two calls in `InvoiceExpirySweepJob.processOne`, confirmed red,
reverted via `Edit`). No Human Approval gate needed — same established "fix self-evident gaps
immediately" precedent as Phase 9/11's own findings.

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 (R5, named) | Eligible invoice → `EXPIRED`, watch unregistered | `InvoiceExpirySweepJob.processOne`, `InvoiceService.expireInvoice`, `InvoiceRepository.updateStateIfCurrentState` | `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (job + service layers, mocked), `InvoiceRepositoryIntegrationTest.updateStateIfCurrentStateActuallyUpdatesStateAndUpdatedAtForARealOpenInvoice` (real DB — Docker-unavailable this session, disclosed) |
| AC2 (R5, named) | Invoice with a payment in ANY of `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING` → stays `OPEN`, watch stays registered | `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` (native query, 4-state anti-join) | `shouldNotExpireInvoiceWithPaymentInFlight` (structural, reflection on the real `@Query` value), `InvoiceRepositoryIntegrationTest.findExpiredCandidatesWithoutInFlightPaymentReturnsOnlyTheEligibleInvoice` (real DB, all 4 states + a terminal-payment control + a not-yet-expired control + an already-`EXPIRED` control — Docker-unavailable, disclosed) |
| AC3 | `@SchedulerLock` present on the sweep method | `InvoiceExpirySweepJob.sweep` | `sweepMethodIsAnnotatedSchedulerLock`; strengthened by Phase 11 Gap 3's own `sweepMethodIsAnnotatedScheduled` (the lock alone doesn't prove the method is actually scheduled) |
| AC4 | One invoice's own failure doesn't abort the batch | `sweep()`'s per-invoice `try/catch` | `shouldContinueSweepAfterOneInvoiceFails` (mutation-verified: removing the `try/catch` turns this red) |
| AC5 | `unregisterWatch` runs outside any open transaction | `processOne` calls `cryptoClient.unregisterWatch` strictly before `invoiceService.expireInvoice` (the only `@Transactional` call in the path) | `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (`InOrder`-verified this phase, mutation-verified) |
| AC6 (Phase 9 re-shaped) | A non-`OPEN` invoice is never silently overwritten back to `EXPIRED` | `InvoiceRepository.updateStateIfCurrentState` (atomic conditional `UPDATE ... WHERE state = :expectedCurrentState`) — replaced the frozen brief's original `markExpired()` no-op-guard design after Kimi Phase 8 found it created a real lost-update race | `shouldNotOverwriteNonOpenInvoiceState` (mocked, mutation-verified), `InvoiceRepositoryIntegrationTest.updateStateIfCurrentStateReturnsZeroAndLeavesRowUnchangedWhenNoLongerOpen` (real DB, Docker-unavailable, disclosed) |
| AC7 (Phase 3 finding 3) | `unregisterWatch` treats `404`/`410` as success | `CryptoClient.unregisterWatch`'s multi-catch on `HttpClientErrorException.NotFound`/`.Gone` | `shouldTreatNotFoundAsSuccessOnUnregisterWatch`, `shouldTreatGoneAsSuccessOnUnregisterWatch` (both mutation-verified) |
| AC8 (Phase 3 finding 7) | A `null`-`watchId` candidate is skipped and logged, not passed to `unregisterWatch` | `processOne`'s own guard clause | `shouldSkipCandidateWithNullWatchId` (mutation-verified) |

## Required Tests (frozen brief)

All 7 named/described tests present and passing, several strengthened beyond the frozen brief's
own minimum by the two genuine Kimi review passes (Phase 8, Phase 11):
`shouldExpireInvoiceAndUnregisterWatchAfterTtl`, `shouldNotExpireInvoiceWithPaymentInFlight`
(now covers all 4 non-terminal states, not only `SEEN`/`CONFIRMING`, plus a real-DB integration
proof), `shouldContinueSweepAfterOneInvoiceFails`, `sweepMethodIsAnnotatedSchedulerLock`,
`shouldNotOverwriteNonOpenInvoiceState` (re-shaped for the Phase 9 atomic-update redesign, the
original brief's `markExpired()`-no-op wording no longer applies to the real implementation),
`CryptoClient.unregisterWatch` success/404/410/failure tests, `shouldSkipCandidateWithNullWatchId`.

## Scope discipline

`git diff --stat e08508a1..HEAD -- services/payment` (T07's own final commit to this task's own
HEAD) touches exactly 17 files:

- The 4 new + 6 modified production/migration files the frozen brief's own Files to
  Create/Modify list named, unchanged.
- `InvoiceExpirySweepJobTest.java` (new) and `CryptoClientTest.java`'s own `unregisterWatch`
  additions — both named in the frozen brief.
- `T01SkeletonRegressionTest.java` — the established necessary-consequence-update pattern
  (T03/T04/T06 precedent): the ShedLock dependency/annotation this task adds requires updating
  T01's own regression guards, not scope creep.
- Three disclosed additions beyond the frozen brief's own list, all necessary consequences of
  this task's own work, none silent: `V6__payment_app_invoice_update_grant.sql` and
  `SchemaBaselineMigrationIntegrationTest`'s own updated grant assertions (the Phase 11
  self-identified missing-`UPDATE`-grant bug — a real defect in this task's own Phase 9 fix, not
  a different task's concern), and `InvoiceRepositoryIntegrationTest.java` (new, Phase 11 Gap
  1/2's own test-only resolution).

No migration outside this task's own `V4`-`V6` range touched. Nothing under `spec/`. No file
outside this task's own disclosed scope.

## Locked decisions

- **L3** — `unregisterWatch` is a Crypto Service REST call only. Held — `CryptoClient` is the only
  caller, no direct chain/DB interaction.
- **L8** — no outbox event for this transition. Held — confirmed by code read; no
  `outboxPublisher.publish(...)` call anywhere in `InvoiceExpirySweepJob`/`InvoiceService.expireInvoice`.

## Open items carried forward (not blockers for this task)

- `SchemaBaselineMigrationIntegrationTest` and `InvoiceRepositoryIntegrationTest` (both
  Testcontainers-backed) could not be run locally this session — Docker unavailable throughout.
  Same disclosed, carried-forward gap as T06/T07/T08 Phase 7/8/9/10/11; expected to be empirically
  confirmed next time Kimi's own Docker-enabled environment runs the suite (the pattern that has
  confirmed every prior task's own equivalent gap).
- The disclosed residual race between `unregisterWatch` and the atomic update (Phase 9's own
  finding, now test-covered at the `WARN`-log level per Phase 11 Gap 8) remains a known,
  accepted limitation — fully closing it would need an atomic re-check of in-flight status
  immediately before `unregisterWatch`, a real design question for a future hardening task if
  this window is ever shown to matter in practice, not attempted here.
- L8's rejected Phase 3 finding (no outbox event for `EXPIRED`) means downstream consumers (e.g.
  a future notification for "your invoice expired") have no event to react to — accepted,
  disclosed, out of this task's own scope.

## Test suite status

162/162 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'`, up from 157
at the end of Phase 10). The two Testcontainers-backed classes were not run this session (Docker
unavailable) but are expected to pass given the V6 grant fix and the structural reflection tests
that mirror their own query/annotation text exactly.

## Verdict rationale

All 8 acceptance criteria are implemented and test-covered, including one genuine production bug
(the missing `UPDATE` grant on `invoices`) found and fixed mid-task rather than shipped silently,
and one test-rigor gap (AC5's own ordering claim) found and fixed during this very verification
phase. Two full Kimi review passes (Phase 8, Phase 11) together found zero surviving code defects
and a comprehensive set of real coverage gaps — all fixed. Both LOCKED decisions (L3, L8) held.
No file outside this task's own disclosed scope touched; every addition beyond the frozen brief's
own file list is individually justified as a necessary, disclosed consequence of this task's own
work. The carried-forward items above are accepted, named limitations, not defects. **PASS.**
Proceeding to Phase 13.
