<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# frontend · T174 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T174 — Home screen |
| **Spec section** | Phase 1 — Foundation: presentation layer (added 2026-10-29) |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 174):**
> **Home screen.** `/app/home` renders a landing screen and moves focus to it on arrival, so a completed sign-in lands somewhere rather than on a blank document. Verifies `homeRouteRendersLandingScreenAndManagesFocus`. Cites R28, L11, L1. Execute before 42.

**Spec package:** `spec/frontend/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R28`
- **Scoped LOCKED decisions:** `L1`, `L11`
- **Named tests (`package.md` §8):** `unauthenticatedRoutesRedirectToSignIn`, `publicRouteSetIsExactlyTheDeclaredSet`, `screensRenderThroughDesignSystemPrimitivesOnly`, `homeRouteRendersLandingScreenAndManagesFocus`, `oidcRoundTripEndToEnd`, `phase1ScreensPassAccessibilityChecks`
- **Contracts:** `contracts/api/*.yaml`, `contracts/api/auth.yaml`, `contracts/api/notifications.yaml`, `contracts/api/payments.yaml`, `contracts/api/token-claims.md`
- **Standing rules:** `spec/frontend/agents.md` is authoritative — never restate or violate it.

---

Consume the implementation (Phase 6) and the self-review (Phase 7) — with fresh, adversarial eyes. You are reviewing a pull request; the implementation is complete. Do NOT rewrite.

Hunt for: logic bugs, missing edge cases, time/ordering bugs, race conditions, incorrect state transitions, wrong assumptions, performance issues, security issues, and any LOCKED-decision or spec deviation.

Return, per finding: **Issue · Evidence · Recommendation · Confidence.** Findings only.
---

## Guardrails (apply to every phase)
- Work ONLY on **T174**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/08-independent-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
