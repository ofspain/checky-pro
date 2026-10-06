<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T85 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T85 — adminSuspendAccount |
| **Spec section** | Phase 1 — Auth & account: admin (role matrix) |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/12-specification-verification.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 85):**
> **adminSuspendAccount.** Verifies `adminOperation_adminSuspendAccount_matchesRoleMatrix`. Cites R27.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R27`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `adminOperation_adminGetAccount_matchesRoleMatrix`, `adminOperation_adminDeleteAccount_matchesRoleMatrix`, `adminOperation_adminActivateAccount_matchesRoleMatrix`, `adminOperation_adminSuspendAccount_matchesRoleMatrix`, `adminOperation_adminReinstateAccount_matchesRoleMatrix`, `adminOperation_adminUnlockAccount_matchesRoleMatrix`, `adminOperation_getEffectiveRoles_matchesRoleMatrix`, `adminOperation_assignRole_matchesRoleMatrix`, `adminOperation_removeRole_matchesRoleMatrix`, `adminOperation_assignRoleTemplate_matchesRoleMatrix`, `adminOperation_removeRoleTemplate_matchesRoleMatrix`, `adminOperation_createRole_matchesRoleMatrix`, `adminOperation_listRoles_matchesRoleMatrix`, `adminOperation_createRoleTemplate_matchesRoleMatrix`, `adminOperation_listRoleTemplates_matchesRoleMatrix`, `adminOperation_listAuditEvents_matchesRoleMatrix`, `adminAreaHiddenFromOtherRoles`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume all prior artifacts. Compare the final implementation and tests against `requirements.md`, `design.md`, and `tasks.md` for THIS task. Produce a **traceability matrix** with columns: `Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation?`.

Then, as the approving principal engineer, answer: (1) Is the task fully complete? (2) Does it satisfy every acceptance criterion? (3) Does it violate any LOCKED decision? (4) Remaining risks? End with a single verdict line: **PASS** or **FAIL**, with a one-line reason.
---

## Guardrails (apply to every phase)
- Work ONLY on **T85**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/12-specification-verification.md`. Do this phase's work, write the one artifact, then STOP and wait.
