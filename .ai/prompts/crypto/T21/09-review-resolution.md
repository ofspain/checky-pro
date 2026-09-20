<!-- MODEL: Human Approval — Phase 9 (Review Resolution). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T21 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T21 — Attest endpoint |
| **Spec section** | Screening, attestation, key custody |
| **Model** | Human Approval |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 21):**
> **Attest endpoint.** Implement `AttestController` + `AttestationService` for `POST /internal/v1/attest`: gate on `AGREED` quorum + met finality + `CLEARED` screening, then sign; `BLOCKED` on sanctioned hit (R21); `409` when quorum/finality unmet (R23); persist every outcome to `attestations` (R20). Structure the sign path to be Nitro-Enclave-portable (no host-only assumptions, L11).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R20`, `R21`, `R23`
- **Scoped LOCKED decisions:** `L11`
- **Named tests (`package.md` §8):** `shouldReturnKmsSignatureFromAttestForValidDigest`, `shouldReturnBlockedFromAttestOnSanctionedCounterparty`, `shouldRejectAttestWhenQuorumOrFinalityNotMet`
- **Contracts:** `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`, `contracts/events/chain/tx-finalized.v1.schema.json`
- **Standing rules:** `spec/crypto-service/agents.md` is authoritative — never restate or violate it.

---

**Human Approval gate.** Consume the self-review (Phase 7) and independent review (Phase 8). A human decides which comments are ACCEPTED. Then apply ONLY the accepted comments.

Rules: do not refactor, do not optimize, do not change public APIs, do not rename classes. Write the artifact as a **resolution log**: each comment → accepted/rejected (+ reason) → the exact change made. If nothing is accepted, say so. Do not advance without human sign-off.
---

## Guardrails (apply to every phase)
- Work ONLY on **T21**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/09-review-resolution.md`. This is a **Human Approval gate** — a person makes the decision. The model only assembles the material for review; it does not advance the pipeline itself.
