<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T28 · Phase 4 — Freeze Task Brief

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T28 — Threat-model closure |
| **Spec section** | Final verification |
| **Model** | Human Approval |
| **Consumes** | `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 28):**
> **Threat-model closure.** Verify each `SECURITY-THREAT-MODEL.md` row (#1–#6) has a corresponding passing test; confirm no non-attest path can reach `kms:Sign` and no single-provider fact is ever emitted.

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** none cited inline — derive them in Phase 1 from `requirements.md`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** none pre-mapped — derive from the acceptance criteria + `package.md` §8
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

---

**Human Approval gate.** Consume the Phase 2 TIB and the Phase 3 challenge. A human (not the model) decides. The model's job is only to assemble the decision packet:
- Fold each ACCEPTED Phase 3 amendment into the brief; list each REJECTED one with a reason.
- Confirm every Open Question is resolved or explicitly deferred with an owner.
- Confirm scope, Files-to-Create/Modify/NOT-Modify, and acceptance criteria are final.

Write the **frozen brief** to the artifact and mark it `STATUS: FROZEN`. Downstream phases may not renegotiate it. If the human does not approve, stop — do not advance.
---

## Guardrails (apply to every phase)
- Work ONLY on **T28**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/04-frozen-task-brief.md`. This is a **Human Approval gate** — a person makes the decision. The model only assembles the material for review; it does not advance the pipeline itself.
