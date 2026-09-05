<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T11 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T11 — Token allowlist + validator |
| **Spec section** | Token, address, finality |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 11):**
> **Token allowlist + validator.** Seed the signed, versioned allowlist (per-chain official USDT/USDC contracts) via migration/config. Implement `TokenValidator` — identity by `<chain, contractAddress>` only; non-allowlisted → `UNKNOWN_TOKEN` surfaced loudly (L7, R13/R14).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R13`, `R14`
- **Scoped LOCKED decisions:** `L7`
- **Named tests (`package.md` §8):** `shouldIdentifyTokenByContractAddressNotSymbol`, `shouldSurfaceUnknownTokenForNonAllowlistedContract`
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

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
- Work ONLY on **T11**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/01-specification-extraction.md`. Do this phase's work, write the one artifact, then STOP and wait.
