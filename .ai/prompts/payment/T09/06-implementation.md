<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T09 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T09 — Payment state machine |
| **Spec section** | State machine & consumers |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 9):**
> **Payment state machine.** Implement `PaymentStateMachine` as pure logic enforcing exactly the transitions in design §4c/L1, including the two reorg reversals. Unit-test every permitted transition and assert illegal transitions throw (not silently no-op).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** none cited inline — derive them in Phase 1 from `requirements.md`
- **Scoped LOCKED decisions:** `L1`
- **Named tests (`package.md` §8):** `shouldPreventCrossModuleEntityImports`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4) and the plan (Phase 5). Implement ONLY this task's scope, following the plan and `agents.md` conventions exactly.

Rules: production-ready code only — no TODO, no placeholder methods, no pseudocode. Touch only the files the plan authorizes. Money as `BigDecimal`/`NUMERIC`; outbox for publishes; idempotent consumers; validated `@ConfigurationProperties`; no secrets in code. Do NOT write tests here (that is Phase 10) unless the task itself is test-only.

Then write the artifact as **implementation notes**: what changed, how each change maps to the plan and to the acceptance criteria, and any deviation forced by reality (flag it, don't hide it).
---

## Guardrails (apply to every phase)
- Work ONLY on **T09**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/06-implementation-notes.md`. Do this phase's work, write the one artifact, then STOP and wait.
