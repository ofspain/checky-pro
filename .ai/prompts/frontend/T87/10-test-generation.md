<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T87 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T87 — adminUnlockAccount |
| **Spec section** | Phase 1 — Auth & account: admin (role matrix) |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 87):**
> **adminUnlockAccount.** Verifies `adminOperation_adminUnlockAccount_matchesRoleMatrix`. Cites R27.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R27`
- **Scoped LOCKED decisions:** none cited inline — derive them in Phase 1 from `design.md` §4a
- **Named tests (`package.md` §8):** `adminOperation_adminGetAccount_matchesRoleMatrix`, `adminOperation_adminDeleteAccount_matchesRoleMatrix`, `adminOperation_adminActivateAccount_matchesRoleMatrix`, `adminOperation_adminSuspendAccount_matchesRoleMatrix`, `adminOperation_adminReinstateAccount_matchesRoleMatrix`, `adminOperation_adminUnlockAccount_matchesRoleMatrix`, `adminOperation_getEffectiveRoles_matchesRoleMatrix`, `adminOperation_assignRole_matchesRoleMatrix`, `adminOperation_removeRole_matchesRoleMatrix`, `adminOperation_assignRoleTemplate_matchesRoleMatrix`, `adminOperation_removeRoleTemplate_matchesRoleMatrix`, `adminOperation_createRole_matchesRoleMatrix`, `adminOperation_listRoles_matchesRoleMatrix`, `adminOperation_createRoleTemplate_matchesRoleMatrix`, `adminOperation_listRoleTemplates_matchesRoleMatrix`, `adminOperation_listAuditEvents_matchesRoleMatrix`, `adminAreaHiddenFromOtherRoles`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4) and the resolved implementation (Phase 9). Generate ONLY tests. Cover every acceptance criterion, every boundary, and every state transition; implement each named test listed in the header. Follow `agents.md` testing conventions — unit tests use plain JUnit with a fixed `Clock`; integration tests use Testcontainers (Postgres + Kafka); contract tests validate against the referenced contracts.

Do NOT change production code. Write the artifact as a **test manifest** mapping each test to the acceptance criterion / requirement ID it verifies.
---

## Guardrails (apply to every phase)
- Work ONLY on **T87**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/10-test-generation.md`. Do this phase's work, write the one artifact, then STOP and wait.
