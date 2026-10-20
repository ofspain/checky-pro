<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). Hand-written, not produced by .ai/generate.py: the open-issues sweep ran as direct fixes outside the 14-phase pipeline, so this is its only review prompt. The generator never deletes files, so regenerating leaves this folder alone. -->

# open-issues sweep · 2026-10-19/20 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Services** | `auth-service`, `crypto-service`, `notification-service`, `payment-service` |
| **Work** | The "fix all open issues" sweep: 24 commits of direct fixes, each with its own tests and deliberate-break checks |
| **Diff** | `git diff 8d8236da..57060d2f` (137 files, +4149 / −253). Per commit: `git log --reverse 8d8236da..57060d2f` |
| **Branch** | `spec/service-specs-and-ai-framework` |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` (stands in for Phases 6 and 7: one entry per fix, with its commit, issue, tests and deliberate breaks) |
| **Produces** | `artifacts/08-independent-review.md` (write it here; exactly one artifact) |

**Why this exists.** The author approved a direct sweep of every open defect and follow-up across the four services, with one combined independent review at the end in place of a per-task pipeline. This is that review.

**Spec packages:** `spec/<service>/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`, for `auth-service`, `crypto-service`, `notification-service` and `payment-service`. `spec/frontend/design.md` received one dated note (link routes).

- **Standing rules:** each `spec/<service>/agents.md` is authoritative for its service.
- **Contracts touched:** `contracts/api/payments.yaml` (receipt read, commit `07f4347e`).
- **Spec edits in the sweep** are dated notes only; the §4c verbatim blocks are unchanged. Check that this held.
- **Out of scope by the author's decision:** payer notification (FU-T07-1), the R26 on-chain anchor, infrastructure alerting and IAM. Do not report these as missing; do report anything in the diff that makes them harder later.

---

Review the diff and the self-review with fresh, adversarial eyes. You are reviewing a finished pull request. Do NOT rewrite it.

Hunt for:
- logic bugs, missing edge cases, and wrong assumptions in each fix
- time, ordering and concurrency bugs, especially:
  - `ParkedEventExpiryJob` against park-and-replay
  - the crypto Watcher tracking-only mode and its restart reload
  - Kafka partition-key ordering (G2)
- security regressions, especially the FU-2 port:
  - any handler whose `@PreAuthorize` is weaker than its URL rule
  - any way to open a public endpoint that `ControllerRulesArchitectureTest` would miss
  - any `@PreAuthorize` denial that can still become a 500
- data-safety problems:
  - PII, tokens or raw payloads reaching logs or durable rows
  - database grants wider than the code needs (V17, V18)
- tests that pass without proving their claim: vacuous canaries, fixtures the rule never sees, assertions a mutation would survive. For each fix, the self-review lists the deliberate breaks that were run. Look for a break that was *not* run and would survive.
- spec or `agents.md` notes that claim more than the code and tests show
- any LOCKED-decision deviation in `spec/<service>/design.md` §4a

Before you report a finding, check the cited file and line in the source at `57060d2f`. Several past findings were wrong or already fixed, so cite the file and line for every finding.

Return, per finding: **Issue · Evidence (file:line, commit) · Recommendation · Confidence.** Findings only. Rank them most severe first, and say which commit each one belongs to.

---

## Guardrails
- Review ONLY the 24 commits in `8d8236da..57060d2f`. Pre-existing code is in scope only where a sweep commit relies on it or changes how it behaves.
- No rewrites, no speculative improvements, no new scope.
- Never modify files under `spec/` or any source file.
- Reference documents; do not paste whole specs or files into the artifact.
- Produce exactly one artifact: `artifacts/08-independent-review.md`. Write it, then STOP and wait.
