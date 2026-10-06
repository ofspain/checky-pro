STATUS: FROZEN

# notification · T19 · Phase 4 — Frozen Task Brief

## Process note: Phase 3 ran before Phase 2 existed

Kimi's Phase 3 review (`03-design-challenge.md`, commit `b4b5e81`) consumed the Phase 1 extraction
only. It was committed before my Phase 2 brief (`216f90d`) existed, so Phase 3 did not challenge
the Phase 2 decisions. Its own header records this: "Consumes `01-specification-extraction.md`."
Disclosed here rather than hidden. Its findings are dispositioned below against Phase 1 and
verified against source. Where it conflicts with Phase 2, Phase 4 decides.

## Phase 3 findings — dispositions

All findings verified directly against source before disposition. Two claims needed precision.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Connect as `notification_app` to prove the grant, not the migration owner | — | **ACCEPTED** | Matches Phase 2's own constraint. The DML proof uses `notification_app` credentials, and the assertion checks SQLState `42501`. |
| 2 | Test class belongs in `com.themistra.notification.delivery` | — | **ACCEPTED** | Same package gives access to the package-private `DeliveryLogRepository`, `DeliveryOrchestrator`, and `RetryScheduler`. |
| 3 | Drive the retry deterministically with `ControllableEmailTransport`, then replay | — | **ACCEPTED, with one amendment** | The replay driver changes from Phase 2's `DeliveryOrchestrator.replay(...)` to `RetryScheduler.processOne(DeliveryRetry)`. Verified: `processOne` is package-private (`RetryScheduler.java:69`), so the same-package test can call it. It is the production retry path on a real `delivery_retry` row, which is stronger evidence than a direct orchestrator call. Still never `sweep()`, which stays ShedLock-guarded. |
| 4 | Suppress with a non-SECURITY category, PAYMENT / `invoice.created` | — | **ACCEPTED, with a precision correction** | Verified: `invoice.created` is mapped to PAYMENT (`DeliveryOrchestrator.java:101`), so the PAYMENT path is real. Correction: `PreferenceResolver.resolve` hard-codes only `SECURITY:EMAIL` to `true` (`:61-63`). `SECURITY:IN_APP` is governed by stored rows and `DEFAULTS`, so Kimi's "SECURITY channels" wording overstates it. The conclusion (don't suppress a SECURITY email) still holds. |
| 5 | `template_version` is non-null only on rendered rows | — | **ACCEPTED** | Verified: `SUPPRESSED` rows are saved with `null` template version (`DeliveryOrchestrator.java:199`) and the no-recipient FAILED row likewise (`:204`). AC3 is scoped to rendered rows, as Phase 1 already disclosed. |
| 6 | AC5 by static inspection plus AC1 | — | **ACCEPTED** | Static search of `src/main` for mutation paths is recorded in the implementation notes, and AC1 covers the runtime grant. |
| 7 | One test class, multiple focused methods | — | **ACCEPTED** | One class, four methods, one per AC group. Method names below are adopted. |
| 8 | No production code changes | — | **ACCEPTED** | Confirmed: test source only. |

## Decisions (resolved)

- **Class name**: `com.themistra.notification.delivery.DeliveryLogDisputeGradeIntegrationTest`.
  This supersedes the Phase 2 name `DeliveryLogDisputeIntegrationTest`. The "grade" suffix matches
  the L3 requirement wording.
- **Retry driver**: `RetryScheduler.processOne(DeliveryRetry)` on a real row, replacing Phase 2's
  `replay(...)`. Never `sweep()`.
- **Suppression**: PAYMENT category, `invoice.created`, EMAIL disabled via a real
  `channel_preferences` row. The SECURITY floor is not involved.
- **Test methods**: `deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole` (AC1),
  `retryChainAppendsMultipleRowsForTheSameSourceEventKey` (AC2, AC3),
  `suppressedChannelLeavesASuppressedDeliveryLogRow` (AC4),
  `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` (AC5, static inspection recorded).

## Task, Scope, Acceptance Criteria, Constraints

Unchanged from Phases 1 and 2 except where a decision above amends them. No production code changes.
Every acceptance criterion is AC1–AC5 as written in Phase 1.

## Files to Create

- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryLogDisputeGradeIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

Everything under `src/main`, every migration, every existing test, `spec/`, auth/crypto Dockerfiles.

## Open Questions

No blockers. The Phase 2 naming and driver are superseded by the decisions above. Implementation
starts from this frozen brief.
