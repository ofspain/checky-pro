# notification · T17 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T17 — End-to-end `auth.email.requested(verify)` redelivery/idempotency proof (scoped per Phase 0's user decision) |
| **Consumes** | All prior T17 artifacts (Phases 0–11, including the Phase 11 addendum) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R1** — `auth.email.requested(verify_email)` results in a verification email | Yes | `VerifyEmailRedeliveryIntegrationTest.java:140-156` — real Kafka production through the real, unspied chain | `verifyEmailRedeliveryProducesExactlyOneEmailAndNoDuplicateDeliveryLogRows` | No | No |
| **R7** — a consumed event's stable key is recorded | Yes | `:142-145` (`processedEventExists`) | Same test | No | No |
| **R8** — redelivery produces no second delivery | Yes | `:181-192` | Same test | No | No |
| **L1** — idempotent by event key, `processed_events` written in the same transaction as the delivery-log append | Yes (proven as an outcome, not by inspecting the transaction boundary directly) | `:142-192` — the real chain's own existing transactional guarantee (T04) is exercised, not re-implemented or re-verified at the transaction level by this task | Same test | No | No — this task proves the *outcome* L1 guarantees (no double-send), not the transaction mechanics themselves, which `IdempotencyGuardIntegrationTest` (T04) already owns |
| **L3** — dispute-grade delivery log, every attempt recorded, correlatable to the source event key | Yes | `:157-178` — two real `delivery_log` rows, both `SENT`, both correlated to the computed `eventKey` via `source_event_key`, each with the correct real recipient | Same test | No | No |
| **AC1** — real Kafka event → exactly one captured email via real, unspied wiring | Yes | `:146-156` | Same test | No | No |
| **AC2** — redelivery produces no second email | Yes | `:181-192` | Same test | No | No |
| **AC3** — real `delivery_log` row(s) with outcome `SENT` (revised by Phase 4 Finding #4 to two rows, one per channel) | Yes | `:157-178` | Same test | No | No, already disclosed at Phase 4 |
| **AC4** — payment scenario explicitly out of scope | Yes | Class Javadoc (`:35-48`) | N/A — nothing to test | No | No, disclosed at every phase since Phase 0 |

## Answers

**(1) Is the task fully complete?** Yes, for the scope the user chose at Phase 0. The one file the
frozen brief named exists, with no production code touched. The task went through adversarial
review (Kimi Phases 8, 11) plus this session's own self-review (Phase 7) and test-generation audit
(Phase 10), with every finding fixed, correctly disposed with a stated reason, or explicitly
documented as already-correct/already-disclosed. The real deliverable — proving the two
already-separately-tested halves of the chain (`AuthEventConsumerIntegrationTest`'s Kafka wiring,
`DeliveryOrchestratorIntegrationTest`'s delivery chain) actually work together, unspied, end to
end — is proven, not merely asserted: the test was run fresh dozens of times across every phase,
consistently green except for one, now fully root-caused, cold-start/backlog anomaly (Phase 9) that
was never a correctness defect.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC4, see matrix above. AC3 is
the criterion most strengthened across this task's own phases: Phase 6 proved two `SENT` rows
existed at all; Phase 9 added `source_event_key` correlation and the EMAIL row's recipient; Phase 10
added the symmetric IN_APP recipient check; Phase 11 added the captured email's own `to()` field.
Each strengthening was verified against real, cited source before being applied, not assumed.

**(3) Does it violate any LOCKED decision?** No. L1 and L3 both hold, per the matrix. No production
code was touched by this task (test-source only), so no other LOCKED decision in this service's
`agents.md` is at risk.

**(4) Remaining risks?**
- **The `payments.receipt.issued` scenario remains entirely untested** — identical in kind to T07's
  own already-accepted blocker, disclosed at Phase 0 by explicit user decision, not silently
  dropped. Resolves once `spec/payment-service/package.md` reaches `READY FOR IMPL` and a real
  `PaymentEventConsumer` exists for a future task to glue in the same way this task glued the
  auth/email chain.
- **No test proves the second Kafka message was actually consumed and deduped, specifically** (Kimi
  Phase 11 Gap #4) — the unchanged counts after redelivery are the correct-level proof for AC2's own
  wording ("no second delivery"), and a stronger "was it specifically seen and deduped" claim was
  correctly assessed as not required and not added.
- **Captured email content (subject/body) and the `inapp_notifications` row are not directly
  asserted here** — intentional, bounded scope; `TemplateRendererIntegrationTest` and
  `InAppChannelIntegrationTest` (T13) already own that coverage respectively, confirmed at Phase 7
  and reconfirmed at Phase 11.
- **Shared local Kafka broker state across repeated local reruns can produce a harmless,
  fully-explained backlog-processing exception** (confirmed at Phase 9: a Jackson
  `JsonParseException` from an old, deliberately-malformed message left by
  `AuthEventConsumerTest`'s own existing poison-message test) — environmental, not reproducible in a
  real CI run (fresh broker per build), already proven recoverable by existing consumer behavior.

## Verdict

**PASS** — T17 fully satisfies R1, R7, R8, L1, L3 and every acceptance criterion (AC1–AC4) within
the scope the user chose at Phase 0. The real gap no existing test closed — the full,
Kafka-to-captured-email, unspied production chain — is now proven, with redelivery producing no
duplicate email or `delivery_log` row. The `payments.receipt.issued` half remains deliberately,
visibly deferred, not silently absorbed. The full suite is green at 369 tests, 0 failures, 0 errors.
