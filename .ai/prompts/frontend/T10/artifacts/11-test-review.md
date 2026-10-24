# frontend · T10 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies `endSessionNavigationBypassesServiceWorker`. Cites R57.

---

## Method

Reviewed the Phase 10 test manifest for `endSessionNavigationBypassesServiceWorker` (14 cases, 7 mutation breaks) against the frozen T10 acceptance criteria (AC1–AC7) and the Phase 8 findings. No code was rewritten.

The manifest already covers the Phase 8 findings: the end-session URL shape is explicit and backend-derived; `/app/signed-out` is verified as a shell route; the ID token is asserted never to reach Cache Storage; and the cache-key assertion (M7) addresses the Phase 3 concern about a full-URL cache key leaking the token.

---

## Gaps

### 1. No test for a hash/fragment on the end-session URL
**Gap ·** AC4 covers query strings, trailing slashes, and encoded redirect URIs, but not a URL fragment such as `/connect/logout#section`.  
**Why it matters ·** Browsers do not send fragments to the server, but a worker decision that accidentally read `url.hash` could change classification. The current implementation uses `url.pathname`, so the test should assert fragments are ignored.  
**Suggested test ·** Add one AC4 variant: `/connect/logout?id_token_hint=...#fragment` is still declined.

### 2. No test for the signed-out target with a hash
**Gap ·** AC3 tests `/app/signed-out?state=s1` but not `/app/signed-out#section`.  
**Why it matters ·** The shell matcher uses `url.pathname`, so a fragment should not affect the shell answer. Asserting this pins the behavior.  
**Suggested test ·** Add one AC3 row: `/app/signed-out#reset` resolves to the shell.

### 3. No test for repeated `/app/signed-out` navigation
**Gap ·** AC6 repeats the end-session navigation to prove statelessness, but there is no equivalent repetition for the shell answer.  
**Why it matters ·** A bug where the shell answer is stateful (e.g., keyed by a nonce in the query) might only show on repetition.  
**Suggested test ·** Add a case that navigates to `/app/signed-out?state=s1` twice and asserts the shell is returned both times with only one cache `match` call (or two, depending on intended caching), and no cache `put`.

### 4. No test that the cache key for the shell answer does not include the `state` query value
**Gap ·** M7 catches a harness that keys the cache by full URL, but the assertion is on the constant `SHELL` key. It does not explicitly check that the `state=s1` query parameter is absent from the key.  
**Why it matters ·** If the shell were keyed by `/app/index.html?state=s1`, the test would still pass because `cache.match` is called with `SHELL`, but a real Workbox setup might construct a different key.  
**Suggested test ·** Extend AC5 to assert that the key passed to `cache.match` is exactly `SHELL` and contains neither `?` nor `state`.

### 5. No negative test for a path that looks like the end session but is not
**Gap ·** There is no case asserting that `/connect/logout-extra` or `/connect/logoutx` is *not* denylisted. T09 covers segment boundaries generally, but T10 does not re-pin it for the end-session path specifically.  
**Why it matters ·** A regression that changed the segment rule for just `/connect/logout` could break R19 without affecting other paths.  
**Suggested test ·** Add one AC4-style negative row: `/connect/logoutx` is neither denylisted nor shell.

### 6. No test that the `id_token_hint` value does not appear in any failure message
**Gap ·** The fake token is synthetic, but Vitest assertion diffs could still print it on failure.  
**Why it matters ·** The task constraint says "No token in a snapshot or a failure message." There is no explicit guard against the token leaking into a test failure output.  
**Suggested test ·** Optional and meta: not a functional test, but a one-off check that the source does not contain a realistic JWT. The current `fake.…` literal satisfies this by inspection.

### 7. No test for non-navigate `POST` or `GET` to `/connect/logout`
**Gap ·** T09 covers non-navigation modes for the denylist generally, but T10 does not re-pin that an API-style `GET /connect/logout` is outside this module's scope.  
**Why it matters ·** R19 is a navigation; task 11 owns API NetworkOnly. A future change could make the predicate apply to non-navigations and this file would not notice.  
**Suggested test ·** Add one AC6-style assertion: `isDenylistedNavigation(new URL('/connect/logout?...'), 'cors')` is `false`.

### 8. No integration test with a real 302 redirect chain
**Gap ·** The two-step flow is simulated by two harness calls. jsdom has no `FetchEvent`, so a real redirect cannot be exercised here.  
**Why it matters ·** This is an inherent limitation of the unit-test environment. It is recorded in the file header, but the task's scope ends before integration.  
**Suggested test ·** No test can be added in T10; record as a task 12 / Playwright integration requirement.

---

## Non-gaps (acknowledged, no change needed)

- **14/14 cases pass** and **7/7 mutants caught**.
- **Every AC has dedicated cases**, including the six URL-shape variants for AC4.
- **M1 (dropped `/connect/logout`)** is caught 12 times, 8 of them T10's, confirming the phase 9 mitigation.
- **M2/M3 (`url.href` / `url.search`)** are caught by 38 cases each — the most important mutants for preventing the ID token from influencing the cache decision.
- **M7 (full-URL cache key)** is caught by AC5's key assertion.
- **AC2** asserts the cache is never touched across all six URL shapes.
- **AC5** asserts the only cache key is the shell path and that no key contains `?` or the token.
- **AC7** asserts both `/connect/logout` in `agents.md` and `signed-out` as a public `/app` route.
- **No production file touched** — `navigationDenylist.ts`, T09's test, and T01–T08 are unchanged.
- **Verified against the running platform** — the local stack smoke test signs out through end-session successfully.

---

## Summary

The T10 test suite is focused and effective: 14 cases cover every acceptance criterion, and the mutation pass confirms the critical defenses against dropping `/connect/logout` and against letting the query string influence the decision. The remaining gaps are small edge cases (hashes, repeated shell answer, non-navigation modes, a segment-boundary negative) and the inherent unit-test limitation that the real 302 redirect chain cannot be exercised here. Adding the edge-case rows would make the suite complete, but it already provides a strong regression guard for R19's worker boundary.
