<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T175 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T175 — A reply the client cannot parse is not a verification failure |
| **Spec section** | Phase 1 — Auth & account: registration and reset |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 175):**
> **A reply the client cannot parse is not a verification failure.** `VerifyScreen` reaches its success or failure state by what the server answered, not by whether the reply body happened to parse, and `RegisterScreen` does not strand a visitor on an unparseable reply. Verifies `verifyFromLinkShowsSuccessState`, `verifyFailureShowsOneUniformMessage`. Cites R3, R4, L4. Execute after 51.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R3`, `R4`
- **Scoped LOCKED decisions:** `L4`
- **Named tests (`package.md` §8):** `verifyFromLinkShowsSuccessState`, `verifyFailureShowsOneUniformMessage`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/notifications.yaml`, `contracts/api/payments.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6). Self-review the diff against the frozen brief and `agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types, enumeration-safety/secret-handling, readability, complexity.

Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** Findings only — fixes are applied in Phase 9.
---

## Guardrails (apply to every phase)
- Work ONLY on **T175**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/07-self-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
