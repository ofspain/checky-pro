<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T30 · Phase 6 — Implementation

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T30 — On-demand tx-hash lookup endpoint (R29, L16) |
| **Spec section** | Phase 2 prerequisite (added 2026-10-10, discovered authoring `spec/intelligence-service`) |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 30):**
> **On-demand tx-hash lookup endpoint (R29, L16).** Implement `GET /internal/v1/transactions/{chain}/{txHash}`, reusing `ChainAdapter.getTx` + the existing `QuorumEvaluator` with no new verification logic. Add a contract test confirming the response shape matches §4c exactly and a unit test confirming 2-of-3 disagreement still yields `HELD`, never an auto-resolved value. CODEOWNERS review required (agents.md). **Not yet built** — this task exists only as a spec entry; task 29's own "Bump spec status" line does not cover it, since it was added after that bump. `package.md`'s own status line discloses this explicitly.

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R29`
- **Scoped LOCKED decisions:** `L16`
- **Named tests (`package.md` §8):** none pre-mapped — derive from the acceptance criteria + `package.md` §8
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4) and the plan (Phase 5). Implement ONLY this task's scope, following the plan and `agents.md` conventions exactly.

Rules: production-ready code only — no TODO, no placeholder methods, no pseudocode. Touch only the files the plan authorizes. Money as `BigDecimal`/`NUMERIC`; outbox for publishes; idempotent consumers; validated `@ConfigurationProperties`; no secrets in code. Do NOT write tests here (that is Phase 10) unless the task itself is test-only.

Then write the artifact as **implementation notes**: what changed, how each change maps to the plan and to the acceptance criteria, and any deviation forced by reality (flag it, don't hide it).
---

## Guardrails (apply to every phase)
- Work ONLY on **T30**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/06-implementation-notes.md`. Do this phase's work, write the one artifact, then STOP and wait.
