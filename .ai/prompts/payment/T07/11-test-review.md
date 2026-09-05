<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T07 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T07 — Merchant-scoped reads |
| **Spec section** | Invoices |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 7):**
> **Merchant-scoped reads.** Add `GET /api/v1/invoices` and `GET /api/v1/invoices/{invoiceUuid}` filtered to the caller's `sub`, returning `404` cross-merchant (R28).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R28`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `shouldScopeInvoiceAndReceiptReadsToOwningMerchant`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the tests (Phase 10). Do the tests actually verify the specification? Do NOT rewrite. Look for: missing cases, weak/absent assertions, false positives, flakiness, duplicate tests, and coverage gaps against the acceptance criteria and named tests in the header.

Return recommendations only — each as **Gap · Why it matters · Suggested test.**
---

## Guardrails (apply to every phase)
- Work ONLY on **T07**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/11-test-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
