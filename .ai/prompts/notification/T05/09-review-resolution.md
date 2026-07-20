<!-- MODEL: Human Approval — Phase 9 (Review Resolution). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# notification · T05 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T05 — Contact projection (Q1/O1) |
| **Spec section** | Consumers & idempotency |
| **Model** | Human Approval |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/notification-service/tasks.md`, task 5):**
> **Contact projection (Q1/O1).** Consume `auth.user.lifecycle` and read email from `auth.email.requested` to populate `contact_projection` (blocker for email delivery). Confirm approach against Q1 before finalizing.

**Spec package:** `spec/notification-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** none cited inline — derive them in Phase 1 from `requirements.md`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** none pre-mapped — derive from the acceptance criteria + `package.md` §8
- **Contracts:** `contracts/events/`, `contracts/events/*`, `contracts/events/notifications/*`
- **Standing rules:** `spec/notification-service/agents.md` is authoritative — never restate or violate it.

---

**Human Approval gate.** Consume the self-review (Phase 7) and independent review (Phase 8). A human decides which comments are ACCEPTED. Then apply ONLY the accepted comments.

Rules: do not refactor, do not optimize, do not change public APIs, do not rename classes. Write the artifact as a **resolution log**: each comment → accepted/rejected (+ reason) → the exact change made. If nothing is accepted, say so. Do not advance without human sign-off.
---

## Guardrails (apply to every phase)
- Work ONLY on **T05**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/09-review-resolution.md`. This is a **Human Approval gate** — a person makes the decision. The model only assembles the material for review; it does not advance the pipeline itself.
