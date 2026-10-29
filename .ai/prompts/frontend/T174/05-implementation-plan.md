<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T174 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Spec section** | Phase 1 — Foundation: presentation layer (added 2026-10-29) |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 174):**
> **Home screen.** `/app/home` renders a landing screen and moves focus to it on arrival, so a completed sign-in lands somewhere rather than on a blank document. Verifies `homeRouteRendersLandingScreenAndManagesFocus`. Cites R28, L11, L1. Execute before 42.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R28`
- **Scoped LOCKED decisions:** `L1`, `L11`
- **Named tests (`package.md` §8):** `unauthenticatedRoutesRedirectToSignIn`, `publicRouteSetIsExactlyTheDeclaredSet`, `screensRenderThroughDesignSystemPrimitivesOnly`, `homeRouteRendersLandingScreenAndManagesFocus`, `oidcRoundTripEndToEnd`, `phase1ScreensPassAccessibilityChecks`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/notifications.yaml`, `contracts/api/payments.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the frozen brief (Phase 4). Plan the implementation — **do NOT write code.**

Return: Files to create · Files to modify · Public methods (signatures) · Private methods · Entities used · Repositories used · Services used · Unit/integration tests required · **Execution order** (front-load schema/migration, then dao, service, api, tests).

Every planned file must trace to the frozen brief's Files sections. Do not add files the brief does not authorize.
---

## Guardrails (apply to every phase)
- Work ONLY on **T174**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/05-implementation-plan.md`. Do this phase's work, write the one artifact, then STOP and wait.
