# auth · T26 — API-key CRUD controller

Complete 14-phase prompt workflow for **Task 26** of the `auth-service` spec package.
Run the phases in order; each consumes the previous phase's artifact and writes exactly one of its own.

**Task (verbatim, `spec/auth-service/tasks.md` task 26):**
> **API-key CRUD controller.** Add `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}`. Ensure responses never include the secret.

**Spec section:** API keys

**Scoped IDs** — requirements: `R30`, `R34`, `R35` · locked: `L7` · tests: `shouldCreateApiKeyAndShowPlaintextExactlyOnce`, `shouldListAndRevokeOwnApiKeys`

## Phases

| # | Prompt | Phase | Responsible agent | Input | Output artifact | When to stop |
|---|---|---|---|---|---|---|
| 0 | [`00-repository-understanding.md`](00-repository-understanding.md) | Repository Understanding | Claude Sonnet | `—` | `artifacts/00-repository-understanding.md` | after artifact written |
| 1 | [`01-specification-extraction.md`](01-specification-extraction.md) | Specification Extraction | Claude Sonnet | `00-repository-understanding.md` | `artifacts/01-specification-extraction.md` | after artifact written |
| 2 | [`02-task-implementation-brief.md`](02-task-implementation-brief.md) | Task Implementation Brief | Claude Sonnet | `01-specification-extraction.md` | `artifacts/02-task-implementation-brief.md` | after artifact written |
| 3 | [`03-design-challenge.md`](03-design-challenge.md) | Design Challenge | Kimi 2.7 | `02-task-implementation-brief.md` | `artifacts/03-design-challenge.md` | after artifact written |
| 4 | [`04-freeze-task.md`](04-freeze-task.md) | Freeze Task Brief | Human Approval | `03-design-challenge.md` | `artifacts/04-frozen-task-brief.md` | after human sign-off |
| 5 | [`05-implementation-plan.md`](05-implementation-plan.md) | Implementation Plan | Claude Sonnet | `04-frozen-task-brief.md` | `artifacts/05-implementation-plan.md` | after artifact written |
| 6 | [`06-implementation.md`](06-implementation.md) | Implementation | Claude Sonnet | `05-implementation-plan.md` | `artifacts/06-implementation-notes.md` | after artifact written |
| 7 | [`07-self-review.md`](07-self-review.md) | Self Review | Claude Sonnet | `06-implementation-notes.md` | `artifacts/07-self-review.md` | after artifact written |
| 8 | [`08-independent-review.md`](08-independent-review.md) | Independent Code Review | Kimi 2.7 | `07-self-review.md` | `artifacts/08-independent-review.md` | after artifact written |
| 9 | [`09-review-resolution.md`](09-review-resolution.md) | Review Resolution | Human Approval | `08-independent-review.md` | `artifacts/09-review-resolution.md` | after human sign-off |
| 10 | [`10-test-generation.md`](10-test-generation.md) | Test Generation | Claude Sonnet | `09-review-resolution.md` | `artifacts/10-test-generation.md` | after artifact written |
| 11 | [`11-test-review.md`](11-test-review.md) | Test Review | Kimi 2.7 | `10-test-generation.md` | `artifacts/11-test-review.md` | after artifact written |
| 12 | [`12-specification-verification.md`](12-specification-verification.md) | Specification Verification | Claude Sonnet | `11-test-review.md` | `artifacts/12-specification-verification.md` | after artifact written |
| 13 | [`13-pr-preparation.md`](13-pr-preparation.md) | PR / Commit Preparation | Claude Sonnet | `12-specification-verification.md` | `artifacts/13-pr-preparation.md` | after artifact written |

- **Purpose:** turn task 26 of `auth-service` into merged, spec-verified code with a full audit trail.
- **Inputs:** the `spec/auth-service/` package (+ `agents.md`) and, per phase, the prior artifact under `artifacts/`.
- **Outputs:** the 14 artifacts under `artifacts/`, plus the implementation + tests in `services/auth/`.
- **Expected final artifact:** `artifacts/13-pr-preparation.md` (a merge-ready commit package), gated on a Phase 12 **PASS**.
- **When to stop:** each phase stops after its single artifact; the human gates (Phases 4 and 9) stop until sign-off. Do not skip an earlier artifact.

See [`../../../WORKFLOW.md`](../../../WORKFLOW.md) for the full pipeline and [`../README.md`](../README.md) for this service's task index.
