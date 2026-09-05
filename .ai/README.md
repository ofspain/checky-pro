# `.ai/` — Themistra spec-driven engineering framework

This directory is the AI-native engineering workspace for the repository. It converts each
specification package under `spec/<service>/` into a per-task, 14-phase prompt workflow so any
engineer or agent can implement a task independently, consistently, and with a full audit trail.

It contains **no application code** and never edits the specs — only the framework.

## What's here

- **[`WORKFLOW.md`](WORKFLOW.md)** — the 14-phase pipeline, artifact chain, model responsibilities,
  directory layout, and cost guidance. Read this first.
- **[`generate.py`](generate.py)** — the generator. Re-run it whenever a spec package changes:
  `python3 .ai/generate.py` (or `--check` to print the discovered inventory without writing).
- **`prompts/<service>/T##/`** — the prompt set for one task: `00-…` through `13-…`, a `README.md`,
  and an `artifacts/` output directory.

## Services (118 tasks total)

- [`auth`](prompts/auth/) — 40 tasks (spec: `spec/auth-service/`)
- [`crypto`](prompts/crypto/) — 29 tasks (spec: `spec/crypto-service/`)
- [`notification`](prompts/notification/) — 20 tasks (spec: `spec/notification-service/`)
- [`payment`](prompts/payment/) — 29 tasks (spec: `spec/payment-service/`)

## How to use it

**New engineer or agent:**
1. Pick a task. Open `.ai/prompts/<service>/T##/README.md`.
2. Run the phases `00 → 13` in order, each with the model named in its header comment.
3. Each phase consumes the previous artifact and writes exactly one new one under `artifacts/`.
4. Stop at the Human Approval gates (Phases 4 and 9) until a person signs off.
5. Ship only after Phase 12 returns **PASS**; Phase 13 produces the commit package.

**Ground rules** (see each service's `spec/<service>/agents.md`): one task per workflow, obey LOCKED
decisions, no unrelated refactoring, reference specs rather than copying them, `main` stays deployable.

Prompts assume Claude Code has repository access. When a spec changes, **regenerate** rather than
hand-editing individual prompts — that keeps the scoped requirement/test IDs in every header accurate.
