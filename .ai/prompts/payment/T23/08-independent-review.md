<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# payment · T23 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T23 — RFC 9457 errors |
| **Spec section** | Exports, contracts, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/payment-service/tasks.md`, task 23):**
> **RFC 9457 errors.** Add `ApiExceptionHandler` with a stable `type` catalogue; no stack traces or internal detail on 4xx (R29).

**Spec package:** `spec/payment-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R29`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `shouldReturnProblemJsonWithNoInternalDetailOn4xx`
- **Contracts:** `contracts/api/payments.yaml`, `contracts/events/payments/`, `contracts/events/payments/receipt-issued.v1.schema.json`
- **Standing rules:** `spec/payment-service/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6) and the self-review (Phase 7) — with fresh, adversarial eyes. You are reviewing a pull request; the implementation is complete. Do NOT rewrite.

Hunt for: logic bugs, missing edge cases, time/ordering bugs, race conditions, incorrect state transitions, wrong assumptions, performance issues, security issues, and any LOCKED-decision or spec deviation.

Return, per finding: **Issue · Evidence · Recommendation · Confidence.** Findings only.
---

## Guardrails (apply to every phase)
- Work ONLY on **T23**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/08-independent-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
