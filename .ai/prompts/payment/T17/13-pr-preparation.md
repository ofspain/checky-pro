<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T17 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T17 — Receipt issuance — FINALIZED only |
| **Spec section** | Attestation, receipts, and ledger |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/12-specification-verification.md` |
| **Produces** | `artifacts/13-pr-preparation.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 17):**
> **Receipt issuance — FINALIZED only.** Implement `ReceiptService`: guard that the payment is `FINALIZED` (L2, R17, R18), call `/attest` (R19), and honour a `BLOCKED` outcome by moving to `HELD` and emitting nothing (R20).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R17`, `R18`, `R19`, `R20`
- **Scoped LOCKED decisions:** `L2`
- **Named tests (`package.md` §8):** `shouldIssueReceiptOnlyFromFinalizedState`, `shouldNeverIssueReceiptBeforeFinalized`, `shouldRequestKmsSignatureViaCryptoAttestEndpoint`, `shouldNotIssueReceiptWhenAttestReturnsBlocked`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the verification (Phase 12) — proceed only if it is **PASS**. Prepare the task for merge. Produce, in the artifact: **Commit title**, **Commit message** (imperative; end with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer), **Files changed**, **Summary**, **Testing performed**, and **Specification references** (task number + the requirement and LOCKED-decision IDs from the header). No code. Branch off `main`; `main` stays deployable.
---

## Guardrails (apply to every phase)
- Work ONLY on **T17**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/13-pr-preparation.md`. Do this phase's work, write the one artifact, then STOP and wait.
