<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T25 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T25 — ArchUnit/module boundaries |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 25):**
> **ArchUnit/module boundaries.** Enforce no cross-module entity imports (L15), the KMS-signer package ban (L11/R22), and the internal-scope requirement on watch/attest endpoints (R27).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R22`, `R27`
- **Scoped LOCKED decisions:** `L11`, `L15`
- **Named tests (`package.md` §8):** `shouldOnlyAllowAttestPathToInvokeKmsSign`, `shouldRequireInternalScopeForWatchAndAttestEndpoints`, `shouldPreventCrossModuleEntityImports`
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

---

Consume the Phase 2 TIB. You are an adversarial reviewer. Do NOT redesign and do NOT implement. Challenge the brief before it is frozen:
- Hidden or unstated assumptions.
- Ambiguous or untestable business rules.
- Missing edge cases and failure modes.
- Any conflict with a LOCKED decision or `agents.md`.
- Unstated dependencies, ordering hazards, or contract mismatches.

For each finding, return: **Issue · Severity · Evidence · Recommended brief amendment.** Output findings only — the human folds accepted ones into the brief in Phase 4.
---

## Guardrails (apply to every phase)
- Work ONLY on **T25**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/03-design-challenge.md`. Do this phase's work, write the one artifact, then STOP and wait.
