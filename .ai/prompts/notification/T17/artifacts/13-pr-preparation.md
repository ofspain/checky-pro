# notification · T17 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T17: end-to-end verify-email redelivery proof`

## Commit message

```
notification-service T17: end-to-end verify-email redelivery proof

Scoped to the auth.email.requested(verify_email) scenario only, per an
explicit Phase 0 user decision: the task's own payments.receipt.issued
scenario is blocked on the exact same unbuilt services/payment that got
task 7 skipped (unchanged since 2026-09-28) - deferred, disclosed, not
silently dropped.

Adds VerifyEmailRedeliveryIntegrationTest, closing a real gap no existing
test covers: AuthEventConsumerIntegrationTest (T06) proves real Kafka-to-
consumer wiring but replaces the real NotificationDispatcher
(DeliveryOrchestrator) with a spy, so nothing downstream ever runs;
DeliveryOrchestratorIntegrationTest (T11) proves that downstream chain but
calls dispatch() directly, with no Kafka at all. Neither test glues the two
proven halves together. This one does: a real auth.email.requested event,
produced to the real Kafka broker, consumed through the real, unspied
production wiring, landing as exactly one captured email plus two real
delivery_log rows (EMAIL + IN_APP, both SENT) - confirming a design fact
Phase 1's own extraction had under-specified (verify_email dispatches to
both channels, not one). Redelivering the identical event produces no
second email and no additional delivery_log rows.

Strengthened across three review rounds, each verified against actual
source before any change, never taken on word: delivery_log.source_event_key
correlation and the EMAIL row's own recipient (Phase 9, after Kimi's
independent review), the symmetric IN_APP recipient check (Phase 10's own
audit), and the captured email's own to() field (Phase 11's test review).
One review-cited precedent (Kimi Phase 8 Finding 2/3, carried over correctly
from this task's own Phase 3 design challenge) was independently
re-confirmed accurate this time, unlike T16's own Phase 8 citation error.

A one-time, non-reproducing Kafka listener exception during repeated manual
local reruns was fully root-caused, not merely dismissed: a Jackson
JsonParseException from a historical malformed-JSON message left on the
shared, persistent local Kafka topic by AuthEventConsumerTest's own existing
poison-message test, consumed on catch-up by this test's brand-new
consumer group. The consumer recovered correctly, confirming its own
already-proven resilience - environmental, not a defect, and not
reproducible in a real CI run (fresh broker per build).

No production code changed. 369 tests total (368 T01-T16 unaffected + 1
new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Created**
- `services/notification/src/test/java/com/themistra/notification/delivery/VerifyEmailRedeliveryIntegrationTest.java`

**Modified**

None (the one new file was revised in place across Phases 6/7/9/10/11, all captured in its final
state above).

**Deleted**

None.

**Process artifacts**
- `.ai/prompts/notification/T17/artifacts/00-13-*.md` — full 14-phase pipeline record (this file
  completes it; Phase 2 was skipped this session, disclosed at Phase 4).

## Summary

Closes the real gap between two already-separately-tested halves of the `auth.email.requested
(verify_email)` chain: Kafka-to-consumer wiring (T06) and the delivery-orchestration chain (T11)
had each been proven in isolation, but never glued together end to end through the real, unspied
production wiring. This task's own test does that for the first time, and in doing so corrected an
under-specified assumption from its own Phase 1 extraction (one `delivery_log` row, not two) before
any code was written. The `payments.receipt.issued` half of the task's own literal wording remains
explicitly, visibly deferred — identical in kind to task 7's own already-accepted blocker — not
silently dropped to make the task look more complete than it is.

## Testing performed

- `mvn -pl services/notification test -Dtest=VerifyEmailRedeliveryIntegrationTest` — run repeatedly
  across every phase (dozens of times total); consistently `Tests run: 1, Failures: 0, Errors: 0`
  except one cold-start timeout (Phase 6, environment warm-up, immediately reproduced-clean on
  rerun) and one later backlog-processing log trace (Phase 9, root-caused, never a test failure).
- `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors, `BUILD SUCCESS`,
  run fresh at every phase, most recently at Phase 12.
- Every factual claim behind every strengthening (the real `NotificationMapping` two-template
  shape, `DeliveryLogRepository`'s own "no custom methods" design, the real `sourceEventKey`
  propagation path, the real IN_APP/EMAIL recipient conventions, `DeliveryOrchestratorIntegrationTest`'s
  own existing `to()`/recipient assertions) was independently re-verified via direct `grep`/file
  reads before being relied on — never accepted on a review's word alone.
- `git diff --stat 5d7b3f9^..HEAD -- services/auth services/crypto services/payment spec/` — empty;
  no sibling service or specification file touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 17 ("End-to-end integration test"), scoped
  to its `auth.email.requested(verify_email)` half per the Phase 0 user decision.
- **Requirements:** R1, R7, R8.
- **LOCKED decisions:** L1, L3.
- **Deferred, disclosed:** the task's own `payments.receipt.issued` scenario — blocked on the same
  unbuilt `services/payment` as task 7, unchanged since 2026-09-28.

## Known, deliberate gaps (not this task's scope)

- **`payments.receipt.issued` remains entirely untested** — `services/payment` still has zero
  code, `spec/payment-service` is still `DRAFT`, `contracts/events/payments/` still doesn't exist.
  Revisit together with task 7 once `spec/payment-service/package.md` reaches `READY FOR IMPL`.
- **No test proves the second Kafka message was specifically seen and deduped**, as opposed to the
  weaker (and AC2-sufficient) "the counts are unchanged" — matches
  `AuthEventConsumerIntegrationTest`'s own identical redelivery-assertion shape; correctly assessed
  by Kimi's own Phase 11 review as not required.
- **Captured email content (subject/body) and the `inapp_notifications` row are not directly
  asserted** — deliberate, bounded scope; owned by `TemplateRendererIntegrationTest` and
  `InAppChannelIntegrationTest` (T13) respectively.

## Reviewer notes

- **This task's own Phase 0 found the real blocker before any code was written**: the task's own
  literal wording names a `payments.receipt.issued` scenario blocked on the identical unbuilt
  producer that got task 7 skipped — surfaced immediately, with the user choosing to scope the task
  to its buildable half rather than skip it entirely (unlike task 7, which had no buildable half at
  all).
- **Kimi's Phase 8 independent review (6 findings)** correctly identified a real correlation gap
  (`source_event_key`, Finding 2) and a real recipient-correlation gap (Finding 3) — both verified
  against actual source and applied as real strengthenings, not left as documentation-only, per
  this pipeline's own precedent for cheap, clearly-correct fixes.
- **Kimi's Phase 11 test review (5 findings)** found one more real, symmetric gap (the captured
  email's own `to()` field, Finding 3) after this task's own Phase 10 audit had already closed the
  analogous IN_APP-recipient gap — each review round found something real the previous one missed,
  consistent with this pipeline's own layered-review design working as intended.
- **A process deviation was disclosed, not hidden**: Phase 2 (Task Implementation Brief) was
  skipped this session — Kimi's own Phase 3 commit combined Phase 2+3 content directly from Phase
  1's extraction. Rather than manufacture a redundant backfilled Phase 2 document, Phase 4 proceeded
  directly from Phase 1 + Phase 3, with the skip disclosed in Phase 4's own text.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T17
(Phase 2 skipped and disclosed, per Phase 4).**
