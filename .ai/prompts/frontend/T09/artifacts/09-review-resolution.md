# frontend · T09 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

## Dispositions

### Phase 8 finding 1 — `repoRoot` computed at module load — **FIXED**
The disposition the owner already chose for the same finding in T08 applies here: keep the pattern (it is T01's, T05's
and T08's convention, and `import.meta.url` is a non-file URL under jsdom, which is why the pattern exists at all),
and add the existence check that makes a wrong root fail legibly.

`sasNavigationBypassesServiceWorker.test.ts` now resolves every on-disk path through a `repoFile()` helper that
checks the target exists and, when it does not, throws a sentence naming the computed root and pointing at the
`dirname()` count above it. Kimi's alternative — `import.meta.dirname` — is what the Vite transform makes
unavailable in this environment, and `process.cwd()` is what varies by runner invocation; both were rejected on that
basis, not on preference. The two reads affected are the `agents.md` drift guard (AC7) and the module source scans
(AC8, AC9).

Suite re-run after the change: **55/55** in `src/pwa`, `tsc --noEmit` clean.

### Self-review 1 / Phase 8 corroboration — precache could still answer a denylisted navigation — **CARRIED FORWARD to task 12**
Confirmed by both reviews as the task's hardest limit, and not fixable here: a Workbox precache route matches by URL
and registers ahead of the navigation fallback, so a denylisted URL in a future precache manifest would be answered
before this module is consulted, with all 55 cases still green. **Task 12 must import `NAVIGATION_DENYLIST` rather
than restate the list, and assert its generated precache manifest contains no URL matching it.** Recorded in Phase
13 and in the project memory so it reaches task 12's brief.

### Self-review 2 — no production consumer yet — **CARRIED FORWARD to task 12**
Inherent to the ordering decided at the gate (D1): tasks 9–11 are policy, task 12 registers. Task 12's test should
fail if the worker's navigation route is not built from these exports.

### Self-review 3, 4, 7 and Phase 8 corroborations — **ACCEPTED, no change**
Sub-paths of `/connect/logout` being denylisted (uniform segment matching, safe direction, flagged as beyond R57);
`/connect/*` otherwise unguarded (faithful to R57, which lists only `/connect/logout`); the duplicated `mode` check
(keeps each exported predicate independently correct). All three are recorded, none is a defect.

### Self-review 5 — AC9's scan keeps trailing comments — **ACCEPTED, no change**
Over-strict in the safe direction: a banned token in a trailing comment would fail the case rather than pass a real
violation. No current line is affected.

### Self-review 6 — the drift guard depends on one sentence in `agents.md` — **ACCEPTED, no change**
Intended loud failure, and the same property T05's and T08's drift guards have. Rewording that sentence in `spec/`
fails this test with `expect(start).toBeGreaterThan(-1)`, which is the signal, not a bug.

## Net change in this phase

`frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts` only: one `existsSync` import, one `repoFile()` helper,
three call sites, and the comment above `repoRoot` rewritten to say why the pattern is used and what the helper
protects. No production file changed; no acceptance criterion changed.
