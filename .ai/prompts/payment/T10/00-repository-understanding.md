<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T10 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T10 — Verification record |
| **Spec section** | State machine & consumers |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 10):**
> **Verification record.** Add `VerificationRecord` (append-only) and record every transition, reversal, and *ignored stale event* with the trigger event key (R11, R12, R14).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R11`, `R12`, `R14`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `shouldWalkBackwardConfirmingToSeenOnReorg`, `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm`, `shouldIgnoreOutOfOrderOrStaleChainEvents`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

You are joining an existing engineering team. **Do NOT write code.** Read the repository and this service's spec package (only the four/five files listed above), then ground yourself in what already exists that this task will touch.

Produce, in the artifact:
1. **Architecture summary** of this service (modules, persistence, events/outbox, security).
2. **Existing code this task touches** — packages/classes/tables that already exist vs. are new.
3. **Established patterns** to follow — persistence (JPA/Flyway), outbox/idempotency, resource-server security, error handling, configuration.
4. **Testing conventions** (unit vs. Testcontainers, fixed `Clock`, ArchUnit).
5. **Known gaps / unknowns.** If something required by the task is missing, write "I do not know" — do not speculate.

Do not design and do not extract requirements yet — that is Phase 1.
---

## Guardrails (apply to every phase)
- Work ONLY on **T10**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/00-repository-understanding.md`. Do this phase's work, write the one artifact, then STOP and wait.
