<!-- MODEL: Human Approval — Phase 9 (Review Resolution). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# auth · T02 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T02 — Resolve Q1 (TOTP encryption) |
| **Spec section** | Foundation |
| **Model** | Human Approval |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 2):**
> **Resolve Q1 (TOTP encryption).** Get an author decision on seed encryption, update this spec and `auth-decisions.md` with the chosen approach before writing `mfa/` code.

**Spec package:** `spec/auth-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R22`
- **Scoped LOCKED decisions:** `L6`, `L13`
- **Named tests (`package.md` §8):** none — no named §8 test maps to this task
- **Contracts:** `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, `contracts/events/auth/email-requested.v1.schema.json`, `contracts/events/auth/security-audit.v1.schema.json`
- **Standing rules:** `spec/auth-service/agents.md` is authoritative — never restate or violate it.

---

**Human Approval gate.** Consume the self-review (Phase 7) and independent review (Phase 8). A human decides which comments are ACCEPTED. Then apply ONLY the accepted comments.

Rules: do not refactor, do not optimize, do not change public APIs, do not rename classes. Write the artifact as a **resolution log**: each comment → accepted/rejected (+ reason) → the exact change made. If nothing is accepted, say so. Do not advance without human sign-off.
---

## Guardrails (apply to every phase)
- Work ONLY on **T02**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/09-review-resolution.md`. This is a **Human Approval gate** — a person makes the decision. The model only assembles the material for review; it does not advance the pipeline itself.
