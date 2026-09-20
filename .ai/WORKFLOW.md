# Themistra AI Engineering Workflow

Specification-driven development: every implementation task flows through a fixed 14-phase
pipeline, each phase producing exactly one artifact that the next phase consumes. No phase
skips an earlier artifact. The specs in `spec/<service>/` are the source of truth; this
framework turns each `tasks.md` task into merged, spec-verified code with a full audit trail.

## Inventory (generated)

| Spec package | Short name | Tasks | Prompts |
|---|---|---|---|
| `auth-service` | `auth` | 40 | `.ai/prompts/auth/` |
| `crypto-service` | `crypto` | 29 | `.ai/prompts/crypto/` |
| `notification-service` | `notification` | 20 | `.ai/prompts/notification/` |
| `payment-service` | `payment` | 29 | `.ai/prompts/payment/` |

**Total: 118 tasks × 14 phase prompts.** Regenerate with `python3 .ai/generate.py`.

## The pipeline

| # | Phase | Responsible agent | Produces |
|---|---|---|---|
| 0 | Repository Understanding | Claude Sonnet | `artifacts/00-repository-understanding.md` |
| 1 | Specification Extraction | Claude Sonnet | `artifacts/01-specification-extraction.md` |
| 2 | Task Implementation Brief | Claude Sonnet | `artifacts/02-task-implementation-brief.md` |
| 3 | Design Challenge | Kimi 2.7 | `artifacts/03-design-challenge.md` |
| 4 | Freeze Task Brief | Human Approval | `artifacts/04-frozen-task-brief.md` |
| 5 | Implementation Plan | Claude Sonnet | `artifacts/05-implementation-plan.md` |
| 6 | Implementation | Claude Sonnet | `artifacts/06-implementation-notes.md` |
| 7 | Self Review | Claude Sonnet | `artifacts/07-self-review.md` |
| 8 | Independent Code Review | Kimi 2.7 | `artifacts/08-independent-review.md` |
| 9 | Review Resolution | Human Approval | `artifacts/09-review-resolution.md` |
| 10 | Test Generation | Claude Sonnet | `artifacts/10-test-generation.md` |
| 11 | Test Review | Kimi 2.7 | `artifacts/11-test-review.md` |
| 12 | Specification Verification | Claude Sonnet | `artifacts/12-specification-verification.md` |
| 13 | PR / Commit Preparation | Claude Sonnet | `artifacts/13-pr-preparation.md` |

Phases 4 and 9 are **Human Approval** gates — a person decides; the model only assembles the
review packet. Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs.

## Artifact chain

Each task folder (`.ai/prompts/<service>/T##/`) carries the 14 prompts plus an `artifacts/`
directory. Phase _n_ reads `artifacts/(n-1)-…` and writes `artifacts/n-…`. The implementation
(Phase 6) and tests (Phase 10) additionally land in `services/<service>/`. The terminal
artifact is `artifacts/13-pr-preparation.md`, gated on a Phase 12 **PASS**.

## Directory layout

```
.ai/
  generate.py          # this framework's generator (re-runnable)
  WORKFLOW.md          # this file
  README.md            # how to use the system
  prompts/
    <service>/
      README.md        # task index for the service
      T01/ … T##/
        00-repository-understanding.md  … 13-pr-preparation.md
        README.md      # task purpose, phase table, when-to-stop
        artifacts/     # the 14 produced artifacts land here
```

## How an engineer executes a task

1. Open `.ai/prompts/<service>/T##/` and read its `README.md`.
2. Run `00 → 13` in order. Use the model named in each prompt's `<!-- MODEL: … -->` header.
3. Do not advance past a phase until its one artifact exists. Stop at the human gates (4, 9)
   until you have sign-off.
4. Phase 12 must return **PASS** before Phase 13 prepares the commit.
5. Branch off `main`; keep `main` deployable.

## Common mistakes

- **Widening scope.** A prompt is scoped to one task. Do not fix neighbouring code or refactor.
- **Skipping the freeze (Phase 4).** Implementing against an unfrozen brief invites churn.
- **Silently deviating from a LOCKED decision or `agents.md`.** Stop and log it instead.
- **Writing tests in Phase 6 or code in Phase 10.** Keep the phases separate.
- **Copying whole specs into artifacts.** Reference by path; keep artifacts lean.
- **Running review phases (3, 8, 11) on the default model.** Use Kimi 2.7 for adversarial eyes.

## Cost optimization

- Sonnet handles construction phases; reserve Opus/Fable for genuine architecture only.
- Kimi 2.7 for the three review phases keeps a second, independent perspective cheap.
- Human gates cost no tokens — use them to kill bad direction early.
- Prompts reference documents instead of pasting them, so context stays small per phase.
- Regenerate prompts (don't hand-edit) when a spec changes, so scoped IDs stay accurate.
