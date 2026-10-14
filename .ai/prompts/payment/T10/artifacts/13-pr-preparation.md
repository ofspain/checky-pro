# payment · T10 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T10: verification record (VerificationRecord)`

## Commit message

```
payment-service T10: verification record (VerificationRecord)

Adds VerificationRecord, the append-only audit trail for every payment
state movement: a plain Long paymentId (no Payment entity exists yet,
task 11's own scope), fromState/toState as PaymentState enums, the
deterministic triggerEventKey, an optional confirmations count, and a
detail JSONB payload (the first JSONB-mapped column in this codebase,
via Hibernate 6's native @JdbcTypeCode(SqlTypes.JSON), no new
dependency).

VerificationRecordService offers record(...) for forward transitions
and reversals alike (mechanically identical - a state pair plus an
optional key/count/detail) and a dedicated recordIgnored(...) for R14's
own stale-event case. recordIgnored stores fromState = null (a design
decision made directly during Phase 3/4, not the originally-planned
fromState == toState self-transition) - toState alone already carries
the unchanged current state, so the explicit null is the unambiguous
"no real transition occurred" signal the nullable from_state column was
clearly designed to support. Neither method ever references
PaymentStateMachine (T09) - this service trusts its caller completely,
with no second enforcement of L1.

Kimi's Phase 8 independent review found a real defect: the detail map
was stored and returned by reference, so a caller mutating it after the
call (or mutating what getDetail() returned) could silently corrupt the
entity's own persisted state. Fixed with a defensive copy on both input
and output. Fixing this surfaced, and caught before shipping, a second
problem in the regression test written for it: the first version used
Map.of(...) as its test input, which is already immutable on its own and
would have passed even with zero protection from VerificationRecord -
rewritten with a genuinely mutable map, re-verified.

ArchitectureTest's FEATURE_MODULES gains "verification"; all 3 existing
cross-module-entity-import canaries (T09) re-run clean.

A new VerificationRecordRepositoryIntegrationTest proves the JSONB
mapping round-trips against a real payment_app connection, seeding a
real invoice+payment row first (the FK the Phase 5 plan's own
placeholder IDs would have violated, caught while writing the test).
Cannot run locally this session - Docker unavailable, same disclosed
carried-forward gap as every prior Testcontainers-backed test.

Spec: spec/payment-service/tasks.md task 10. Requirements: R11, R12,
R14. Locked decisions: L1, L5, L7, L11, L12.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordRepository.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java`
- `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordServiceTest.java`
- `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordRepositoryIntegrationTest.java`

**Modified**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T10/artifacts/00-*.md` through `12-*.md`.

## Summary

Every payment state movement this service will ever make — forward transitions, reorg
reversals, and deliberately ignored stale events alike — now has a durable, append-only, JSONB-
annotated audit row it can be recorded into. No live caller exists yet; task 11 is what will
actually drive real `chain.tx.*` events through this mechanism for the first time.

## Testing performed

- `mvn -pl services/payment -am compile` / `test-compile` — clean throughout.
- 15 new tests added this task (13 in `VerificationRecordServiceTest`, 2 in the new
  Testcontainers-backed `VerificationRecordRepositoryIntegrationTest`).
- **196/196 non-Docker-dependent tests passing**, up from 183 before this task.
- Mutation tests across Phases 10/11 on: both reversal/transition shapes, the `recordIgnored`
  `fromState = null` design, the append-only guard (widened to all 3 production files), both
  `NOT NULL`-backed null checks (and explicit proof `fromState` is correctly *not* required), the
  `detail` map's defensive copy (both the input and output side, the latter catching a real bug
  in its own first-draft test), and the `repository.save` invocation itself — each caught its
  introduced defect, each reverted clean (via `Edit`, never `git checkout --`).
- `VerificationRecordRepositoryIntegrationTest` (this task) and `InvoiceRepositoryIntegrationTest`
  (T08, confirmed broken) were not run locally this session — Docker unavailable throughout.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 10 — "Verification record."
- **Requirement IDs:** R11, R12, R14.
- **LOCKED decisions:** L1, L5, L7, L11, L12.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T10.**
