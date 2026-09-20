<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T17 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T17 — Receipt issuance — FINALIZED only |
| **Spec section** | Attestation, receipts, and ledger |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 17):**
> **Receipt issuance — FINALIZED only.** Implement `ReceiptService`: guard that the payment is `FINALIZED` (L2, R17, R18), call `/attest` (R19), and honour a `BLOCKED` outcome by moving to `HELD` and emitting nothing (R20).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R17`, `R18`, `R19`, `R20`
- **Scoped LOCKED decisions:** `L2`
- **Named tests (`package.md` §8):** `shouldIssueReceiptOnlyFromFinalizedState`, `shouldNeverIssueReceiptBeforeFinalized`, `shouldRequestKmsSignatureViaCryptoAttestEndpoint`, `shouldNotIssueReceiptWhenAttestReturnsBlocked`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4) and the resolved implementation (Phase 9). Generate ONLY tests. Cover every acceptance criterion, every boundary, and every state transition; implement each named test listed in the header. Follow `agents.md` testing conventions — unit tests use plain JUnit with a fixed `Clock`; integration tests use Testcontainers (Postgres + Kafka); contract tests validate against the referenced contracts.

Do NOT change production code. Write the artifact as a **test manifest** mapping each test to the acceptance criterion / requirement ID it verifies.
---

## Guardrails (apply to every phase)
- Work ONLY on **T17**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/10-test-generation.md`. Do this phase's work, write the one artifact, then STOP and wait.
