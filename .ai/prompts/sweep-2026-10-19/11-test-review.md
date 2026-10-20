<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). Hand-written, not produced by .ai/generate.py: the open-issues sweep ran as direct fixes outside the 14-phase pipeline. The generator never deletes files, so regenerating leaves this folder alone. -->

# open-issues sweep · 2026-10-19/20 · Phase 11 — Test Review

| | |
|---|---|
| **Services** | `auth-service`, `crypto-service`, `notification-service`, `payment-service` |
| **Work** | The "fix all open issues" sweep, the resolution of the Phase 8 review, and F7's durable expired record: 28 commits |
| **Diff** | `git diff 8d8236da..6ed5c59f -- '*src/test*'` for the tests; drop the path filter for the code they guard. Per commit: `git log --reverse 8d8236da..6ed5c59f` |
| **Branch** | `spec/service-specs-and-ai-framework` |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` (the test manifest: fix → tests → what they verify → deliberate breaks caught). Context: `artifacts/07-self-review.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/11-test-review.md` (write it here; exactly one artifact) |

**Spec packages:** `spec/<service>/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`. Each `agents.md` is authoritative for its service.

**Out of scope by the author's decision:** payer notification (FU-T07-1), the R26 on-chain anchor, infrastructure alerting and IAM. Do not report missing tests for these.

---

Consume the manifest and read the tests themselves at `6ed5c59f`. Do the tests prove what the manifest says they prove? Do NOT rewrite them.

Look for:
- **Tests that would pass with the fix reverted.** Name the production line you would change and explain why no listed test would fail. The manifest's break counts are the author's claim; check them.
- **Weak or absent assertions:** checks on mocks or call counts where the behaviour should be checked; `isNotNull` or size-only checks; no negative assertion where the fix is a refusal.
- **False realism:** an "end-to-end" or "integration" test whose mocks, stand-ins or narrowed inputs hide the defect it is meant to catch. The manifest lists the author's own choices under "Known test-design choices"; judge whether each is sound.
- **Flakiness:** Awaitility timeouts, poll batching, shared containers or topics across test classes, ShedLock and scheduler interference, wall-clock use, ordering between test methods.
- **Missing cases** for each fix's boundary. Examples: the expiry cutoff exactly at `parked_at`; a re-park racing the delete; two expiry runs at once; a receipt UUID owned by a merchant who is suspended; a public `POST` endpoint in crypto; an expression in `KNOWN_ACCESS_EXPRESSIONS` that is itself too broad.
- **Guard tests that can be bypassed:** ArchUnit rules that key on strings or annotation names a future change could avoid; allowlists that a new file would silently join.
- **Duplicates,** and tests whose name says more than their body checks.
- **Rows under "Not covered by a test":** say whether each one is acceptable, or needs a test now.

Before you report a gap, open the cited test and the production code it guards at `6ed5c59f`. Several past findings were wrong or already fixed, so cite file:line for both.

Return, per gap: **Gap · Evidence (test file:line, production file:line) · Why it matters · Suggested test · Confidence.** Recommendations only. Rank them most severe first, and say which fix (manifest row) each one belongs to.

---

## Guardrails
- Review ONLY the tests for the 28 commits in `8d8236da..6ed5c59f`. Pre-existing tests are in scope only where a sweep fix relies on them as its guard.
- No rewrites, no speculative improvements, no new scope.
- Never modify files under `spec/`, any source file or any test file.
- Reference documents; do not paste whole files into the artifact.
- Produce exactly one artifact: `artifacts/11-test-review.md`. Write it, then STOP and wait.
