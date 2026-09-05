<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T17 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T17 — Seen/confirmed/finalized emission |
| **Spec section** | Watchers, reorg, events |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 17):**
> **Seen/confirmed/finalized emission.** On quorum-agreed first sighting emit `chain.tx.seen` (R8); on confirmations gained emit `chain.tx.confirmed` with the count (R9); at finality emit `chain.tx.finalized` and never before (R10). Every event carries `chain:txhash:eventtype` (L5, R12).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R8`, `R9`, `R10`, `R12`
- **Scoped LOCKED decisions:** `L5`
- **Named tests (`package.md` §8):** `shouldEmitChainTxSeenOnQuorumAgreedFirstSighting`, `shouldEmitChainTxConfirmedWithConfirmationCount`, `shouldEmitChainTxFinalizedOnlyAtPerChainFinality`, `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent`
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6). Self-review the diff against the frozen brief and `agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types, enumeration-safety/secret-handling, readability, complexity.

Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** Findings only — fixes are applied in Phase 9.
---

## Guardrails (apply to every phase)
- Work ONLY on **T17**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/07-self-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
