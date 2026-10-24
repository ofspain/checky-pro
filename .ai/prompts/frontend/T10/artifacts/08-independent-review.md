# frontend · T10 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Current `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/frontend/agents.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies `endSessionNavigationBypassesServiceWorker`. Cites R57.

---

## Review scope

Reviewed the Phase 6 T10 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No production code was changed; the review covers only the new test file.

The test file correctly demonstrates that T09's policy covers the R19 end-session flow, that the ID token never reaches Cache Storage in the harness, and that `/app/signed-out` is answered by the shell. The self-review found seven issues, all low or informational. This review corroborates them and adds two fresh observations.

---

## New findings

### 1. The harness-duplication warning is one-way
**Issue ·** The test file header clearly warns that `simulateNavigationFallback` is duplicated from T09 and must be kept in step. However, T09's test file does not carry the reciprocal warning, and the frozen brief forbids editing T09's test. A maintainer who opens T09's copy first has no indication that T10 depends on it.  
**Severity ·** Low — test-only, but the asymmetry undermines the duplication strategy.  
**Evidence ·** `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts:24-27` (the warning); frozen brief §Files NOT to Modify: `sasNavigationBypassesServiceWorker.test.ts`.  
**Recommendation ·** Accept the risk and record it in the Phase 13 summary. If the gate later allows a shared test-only helper (the Open Question 2 alternative), replace both copies at that time. Do not edit T09's file under L12.

### 2. `repoRoot` is still computed at module load, but the error message is now clear
**Issue ·** `repoRoot` is derived from `expect.getState().testPath` at module load, which couples the file to Vitest's import order and its depth in the tree. The new `repoFile` helper mitigates this with a good failure message, but the computation itself remains implicit.  
**Severity ·** Low — stable in the current setup, and the error message makes a moved file fail loudly.  
**Evidence ·** Test file lines 30-42.  
**Recommendation ·** No change required for T10. If a future task opens either T09 or T10 for other reasons, consider moving the repo-root computation into the tests that need it (e.g., using `import.meta.dirname` with a fallback).

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| 1 | AC6 overstates cross-tab coverage | **Confirmed.** The case proves the decision is stateless, which is valuable, but it does not prove cross-tab cache isolation. The name and artifacts should not claim R22 cross-tab coverage. |
| 2 | Harness returning `undefined` for unknown paths could hide a dropped denylist entry | **Confirmed, mitigated.** AC1/AC4 include explicit `isDenylistedNavigation(...) === true` assertions, and AC7 checks membership. The mutation pass confirms M1 is caught. |
| 3 | No production footprint, so cannot constrain real worker | **Confirmed.** This is the declared shape of the task. The Phase 13 summary must make clear that task 12 owns the real Workbox wiring. |
| 4 | `agents.md` parse now in a fourth file | **Confirmed, acceptable.** Extracting a shared spec-reading helper would require editing frozen files. The duplication is a known cost. |
| 5 | Two harness copies, warning only in T10 | **Confirmed and expanded in finding 1 above.** |
| 6 | The `400` variant tests a URL, not a response | **Confirmed.** The case asserts the navigation is declined; caching an error response cannot be proven without responses. |
| 7 | Importing `../app/routes` couples the test to the router | **Confirmed, acceptable.** It is a typed, side-effect-free import and is more robust than source parsing. |

---

## Non-gaps (acknowledged, no change needed)

- **URL shape is backend-derived**, not invented; `id_token_hint`, `post_logout_redirect_uri`, and `state` are used.
- **Fake ID token** is clearly synthetic (`fake.` prefix, no valid signature).
- **AC2** asserts the cache is never touched and that no key contains `id_token_hint`.
- **AC3/AC5** assert `/app/signed-out` and `/app/signed-out?state=s1` are answered by the shell.
- **AC4** covers six end-session URL shapes, including trailing slash and foreign-origin redirect URI.
- **AC5** runs both navigations against one cache and asserts the only key is the shell path, with no `?` or token.
- **AC7** asserts `/connect/logout` is in the denylist and in `agents.md`, and that `signed-out` is a public `/app` route.
- **No production file touched** — `navigationDenylist.ts`, T09's test, and T01–T08 files are unchanged.
- **No worker registered or emitted** — task 12 still owns that.

---

## Summary

The T10 implementation is a clean, test-only verification that T09's denylist correctly handles the OIDC end-session flow. The only fresh finding is the one-way harness-duplication warning, which is an accepted consequence of L12. The more important limitations — no production footprint and no real worker wiring — are inherent to the task and already documented for task 12. No Phase 9 code change is required.
