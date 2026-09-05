<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# notification · T10 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T10 — Secret-safe rendering & logging |
| **Spec section** | Preferences, rendering, delivery |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` (write it here; exactly one artifact) |

**Task statement (verbatim from `spec/notification-service/tasks.md`, task 10):**
> **Secret-safe rendering & logging.** Add `SecretSafeLogging` redaction and assert no token/secret/full-key leaks into bodies or logs (L4, R15).

**Spec package:** `spec/notification-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

- **Scoped requirement IDs:** `R15`
- **Scoped LOCKED decisions:** `L4`
- **Named tests (`package.md` §8):** `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`
- **Contracts:** `contracts/events/`, `contracts/events/*`, `contracts/events/notifications/*`
- **Standing rules:** `spec/notification-service/agents.md` is authoritative — never restate or violate it.

---

Consume the tests (Phase 10). Do the tests actually verify the specification? Do NOT rewrite. Look for: missing cases, weak/absent assertions, false positives, flakiness, duplicate tests, and coverage gaps against the acceptance criteria and named tests in the header.

Return recommendations only — each as **Gap · Why it matters · Suggested test.**
---

## Guardrails (apply to every phase)
- Work ONLY on **T10**. Ignore every other task in the package.
- No unrelated refactoring. No speculative improvements. No scope beyond this task.
- Obey every LOCKED decision and `agents.md`. If one looks wrong, STOP and log it under Open Questions — never deviate silently.
- Never modify the specification files under `spec/`.
- Reference documents; do not paste whole specs into the artifact.
- Produce exactly one artifact: `artifacts/11-test-review.md`. Do this phase's work, write the one artifact, then STOP and wait.
