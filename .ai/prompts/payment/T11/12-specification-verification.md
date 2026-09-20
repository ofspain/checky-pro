<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T11 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T11 — Idempotent consumers |
| **Spec section** | State machine & consumers |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/12-specification-verification.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 11):**
> **Idempotent consumers.** Add `ChainEventConsumer` with `processed_events` dedupe on `chain:txhash:eventtype` inside the applying transaction (R13, L5). Handle `chain.tx.seen` → `SEEN` + emit `payment.seen` (R7, R8); `chain.tx.confirmed` → `CONFIRMING` + record confirmations (R9); `chain.tx.finalized` → `FINALIZED` + emit `payment.finalized` (R10).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R7`, `R8`, `R9`, `R10`, `R13`
- **Scoped LOCKED decisions:** `L5`
- **Named tests (`package.md` §8):** `shouldTransitionToSeenOnChainTxSeen`, `shouldEmitPaymentSeenOnFirstSeen`, `shouldTransitionToConfirmingOnChainTxConfirmed`, `shouldTransitionToFinalizedOnChainTxFinalized`, `shouldDedupeDuplicateChainEventsByEventKey`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume all prior artifacts. Compare the final implementation and tests against `requirements.md`, `design.md`, and `tasks.md` for THIS task. Produce a **traceability matrix** with columns: `Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation?`.

Then, as the approving principal engineer, answer: (1) Is the task fully complete? (2) Does it satisfy every acceptance criterion? (3) Does it violate any LOCKED decision? (4) Remaining risks? End with a single verdict line: **PASS** or **FAIL**, with a one-line reason.
---

## Guardrails (apply to every phase)
- Work ONLY on **T11**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/12-specification-verification.md`. Do this phase's work, write the one artifact, then STOP and wait.
