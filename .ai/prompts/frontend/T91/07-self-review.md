<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T91 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T91 — assignRoleTemplate |
| **Spec section** | Phase 1 — Auth & account: admin (role matrix) |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 91):**
> **assignRoleTemplate.** Verifies `adminOperation_assignRoleTemplate_matchesRoleMatrix`. Cites R27.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R27`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `adminOperation_adminGetAccount_matchesRoleMatrix`, `adminOperation_adminDeleteAccount_matchesRoleMatrix`, `adminOperation_adminActivateAccount_matchesRoleMatrix`, `adminOperation_adminSuspendAccount_matchesRoleMatrix`, `adminOperation_adminReinstateAccount_matchesRoleMatrix`, `adminOperation_adminUnlockAccount_matchesRoleMatrix`, `adminOperation_getEffectiveRoles_matchesRoleMatrix`, `adminOperation_assignRole_matchesRoleMatrix`, `adminOperation_removeRole_matchesRoleMatrix`, `adminOperation_assignRoleTemplate_matchesRoleMatrix`, `adminOperation_removeRoleTemplate_matchesRoleMatrix`, `adminOperation_createRole_matchesRoleMatrix`, `adminOperation_listRoles_matchesRoleMatrix`, `adminOperation_createRoleTemplate_matchesRoleMatrix`, `adminOperation_listRoleTemplates_matchesRoleMatrix`, `adminOperation_listAuditEvents_matchesRoleMatrix`, `adminAreaHiddenFromOtherRoles`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6). Self-review the diff against the frozen brief and `agents.md`. Do NOT rewrite. Evaluate: correctness, boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types, enumeration-safety/secret-handling, readability, complexity.

Return, per finding: **Issue · Severity · Evidence (file:line) · Recommendation.** Findings only — fixes are applied in Phase 9.
---

## Guardrails (apply to every phase)
- Work ONLY on **T91**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/07-self-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
