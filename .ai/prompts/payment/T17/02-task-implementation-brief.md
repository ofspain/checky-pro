<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T17 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T17 — Receipt issuance — FINALIZED only |
| **Spec section** | Attestation, receipts, and ledger |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 17):**
> **Receipt issuance — FINALIZED only.** Implement `ReceiptService`: guard that the payment is `FINALIZED` (L2, R17, R18), call `/attest` (R19), and honour a `BLOCKED` outcome by moving to `HELD` and emitting nothing (R20).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R17`, `R18`, `R19`, `R20`
- **Scoped LOCKED decisions:** `L2`
- **Named tests (`package.md` §8):** `shouldIssueReceiptOnlyFromFinalizedState`, `shouldNeverIssueReceiptBeforeFinalized`, `shouldRequestKmsSignatureViaCryptoAttestEndpoint`, `shouldNotIssueReceiptWhenAttestReturnsBlocked`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the Phase 1 extraction. You are preparing work for a senior engineer. Do NOT design, write code, or suggest improvements. Convert the extraction into a concise **Task Implementation Brief (TIB)** — this becomes the ONLY specification the implementation and review phases use.

Use EXACTLY these sections, nothing else:
`Task` · `Purpose` · `Scope` (In / Out) · `Business Rules` (by requirement ID, one line each) · `Locked Decisions` (by ID) · `Dependencies` · `Inputs` · `Outputs` · `State Changes` (or None) · `Files to Create` · `Files to Modify` · `Files NOT to Modify` · `Acceptance Criteria` (by ID) · `Required Tests` · `Constraints` (performance, security, thread-safety, transaction, module boundaries, null handling) · `Open Questions` (blockers only; else "No blockers").

Keep it under three pages. Do not invent requirements. Do not restate unrelated spec parts.
---

## Guardrails (apply to every phase)
- Work ONLY on **T17**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/02-task-implementation-brief.md`. Do this phase's work, write the one artifact, then STOP and wait.
