<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# notification · T02 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T02 — Schema V1 |
| **Spec section** | Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/notification-service/tasks.md`, task 2):**
> **Schema V1.** Add `V1__notifications_baseline.sql` (design §4c) and seed the launch templates; run `mvn -pl services/notification flyway:migrate` against local Docker Compose Postgres. Grant the service DB role INSERT+SELECT-only on `delivery_log`.

**Spec package:** `spec/notification-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** none cited inline — derive them in Phase 1 from `requirements.md`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** none pre-mapped — derive from the acceptance criteria + `package.md` §8
- **Contracts:** `contracts/events/`, `contracts/events/*`, `contracts/events/notifications/*`
- **Standing rules:** `spec/notification-service/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6). Self-review the diff against the frozen brief and `agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types, enumeration-safety/secret-handling, readability, complexity.

Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** Findings only — fixes are applied in Phase 9.
---

## Guardrails (apply to every phase)
- Work ONLY on **T02**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/07-self-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
