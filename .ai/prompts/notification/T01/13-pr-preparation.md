<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# notification · T01 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T01 — Service skeleton & POM |
| **Spec section** | Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/12-specification-verification.md` |
| **Produces** | `artifacts/13-pr-preparation.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/notification-service/tasks.md`, task 1):**
> **Service skeleton & POM.** Add `services/notification` to the root `<modules>`. Create `pom.xml` mirroring `services/auth` (web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka, actuator, prometheus, testcontainers, archunit, awaitility) plus the chosen email-transport client (per O2/Q2). No producer/outbox dependency at launch unless O5 is taken.

**Spec package:** `spec/notification-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** none cited inline — derive them in Phase 1 from `requirements.md`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** none pre-mapped — derive from the acceptance criteria + `package.md` §8
- **Contracts:** `contracts/events/`, `contracts/events/*`, `contracts/events/notifications/*`
- **Standing rules:** `spec/notification-service/agents.md` is authoritative — never restate or violate it.

---

Consume the verification (Phase 12) — proceed only if it is **PASS**. Prepare the task for merge. Produce, in the artifact: **Commit title**, **Commit message** (imperative; end with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer), **Files changed**, **Summary**, **Testing performed**, and **Specification references** (task number + the requirement and LOCKED-decision IDs from the header). No code. Branch off `main`; `main` stays deployable.
---

## Guardrails (apply to every phase)
- Work ONLY on **T01**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/13-pr-preparation.md`. Do this phase's work, write the one artifact, then STOP and wait.
