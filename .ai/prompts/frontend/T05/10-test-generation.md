<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T05 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T05 — Single-flight 401 |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 5):**
> **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies `parallel401sTriggerExactlyOneRenewal`. Cites R21.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R21`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `parallel401sTriggerExactlyOneRenewal`, `siblingTabsShareOneRenewal`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4) and the resolved implementation (Phase 9). Generate ONLY tests. Cover every acceptance criterion, every boundary, and every state transition; implement each named test listed in the header. Follow `agents.md` testing conventions — unit tests use plain JUnit with a fixed `Clock`; integration tests use Testcontainers (Postgres + Kafka); contract tests validate against the referenced contracts.

Do NOT change production code. Write the artifact as a **test manifest** mapping each test to the acceptance criterion / requirement ID it verifies.
---

## Guardrails (apply to every phase)
- Work ONLY on **T05**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/10-test-generation.md`. Do this phase's work, write the one artifact, then STOP and wait.
