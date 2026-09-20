<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# auth · T31 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T31 — Rate limiting |
| **Spec section** | Rate limiting, contracts, and hardening |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 31):**
> **Rate limiting.** Implement per-account buckets for the paths in R41. Add `mvn` dependency if a library is chosen. Add 429 tests.

**Spec package:** `spec/auth-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R41`, `R42`
- **Scoped LOCKED decisions:** none — no LOCKED decision constrains this task
- **Named tests (`package.md` §8):** `shouldReturn429WhenPerAccountRateLimitExceeded`
- **Contracts:** `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, `contracts/events/auth/email-requested.v1.schema.json`, `contracts/events/auth/security-audit.v1.schema.json`
- **Standing rules:** `spec/auth-service/agents.md` is authoritative — never restate or violate it.

---

Consume the Phase 0 artifact. We are implementing ONLY this task. From `requirements.md`, `design.md`, `package.md`, and `tasks.md`, extract everything needed to implement it — and nothing about any other task.

Return, in the artifact, these sections only:
- **Business Rules** — each applicable requirement, by ID, one sentence.
- **Locked Decisions** — every LOCKED decision that constrains this task, by ID.
- **Files involved** — existing files to read/extend, and new files the spec expects.
- **Dependencies** — classes, services, repositories, entities, config keys, contracts.
- **Acceptance Criteria** — mapped to requirement IDs.
- **Tests required** — the named tests from `package.md` §8 plus boundary tests implied.
- **Open Questions** — only genuine blockers (cite `package.md` §11 where relevant).

Do not design. Do not implement. Reference the header's scoped IDs as your starting set and widen only if the task clearly requires it.
---

## Guardrails (apply to every phase)
- Work ONLY on **T31**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/01-specification-extraction.md`. Do this phase's work, write the one artifact, then STOP and wait.
