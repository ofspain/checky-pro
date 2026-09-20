<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T06 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T06 — Invoice API + watch registration |
| **Spec section** | Invoices |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 6):**
> **Invoice API + watch registration.** Add `InvoiceController` `POST /api/v1/invoices`, register the watch via `CryptoClient` (design §4c), and emit `invoice.created` through the outbox in the same transaction (R1, R2). Move tracking to `CREATED`, then `WATCHING` on watch-ack (R6).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R1`, `R2`, `R6`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `shouldCreateInvoiceAndRegisterWatch`, `shouldEmitInvoiceCreatedInSameTransactionAsInsert`, `shouldTransitionCreatedToWatchingOnWatchAck`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6). Self-review the diff against the frozen brief and `agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types, enumeration-safety/secret-handling, readability, complexity.

Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** Findings only — fixes are applied in Phase 9.
---

## Guardrails (apply to every phase)
- Work ONLY on **T06**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/07-self-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
