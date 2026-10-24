# frontend · T12 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> **Prompt-to-reload update policy.** The worker never calls `skipWaiting` or `clients.claim`, and a new build waits for a user reload. Verifies `serviceWorkerUpdateWaitsForReload`. Cites L17, `package.md` §10.

---

## Method

Reviewed the Phase 10 test manifest for `serviceWorkerUpdateWaitsForReload` (57 cases, 17 mutation breaks) against the frozen T12 acceptance criteria (AC1–AC11) and the Phase 8 findings. No code was rewritten.

The manifest already resolves the Phase 8 findings: S1 is fixed and protected by M1; the S7 guard is in place and tested by M3/M9; AC7's precache matcher is now exercised against synthetic denylisted entries (the M3 lesson); and the M3 mutant showed the original AC7 case was vacuous and was replaced with a tested guard.

---

## Gaps

### 1. The forbidden-call source scan still does not include `src/main.tsx`
**Gap ·** AC1's source scan checks `src/pwa/serviceWorker.ts`, `src/pwa/registerServiceWorker.ts`, and `vite.config.ts`. It does not include `src/main.tsx`, the file that actually triggers registration and could plausibly contain a `skipWaiting`/`clients.claim` call or a reload-promoting message.  
**Why it matters ·** The emitted-bundle scan will catch it, but only after a full build with a worse error message. The Phase 7 self-review (S6) already flagged this.  
**Suggested test ·** Add `src/main.tsx` to the `it.each` table in the source scan, or add a separate assertion that `main.tsx` contains no forbidden call.

### 2. No test for two sequential updates
**Gap ·** The lifecycle cases assert that one update is reported exactly once and that late subscribers see an already-waiting worker. There is no case where a second update arrives after the first is waiting, and the listener is notified a second time.  
**Why it matters ·** A bug where the `statechange` listener is removed after the first update, or where `registration.waiting` is not re-checked, would be missed.  
**Suggested test ·** Add a case: push one update, assert listener called once; push a second update (replacing the waiting worker), assert listener called a second time.

### 3. No test for `updatefound` with a null `installing` worker
**Gap ·** `trackWaiting` returns silently if `registration.installing` is null when `updatefound` fires. The Phase 7 self-review (S4) noted this defensive gap. The manifest does not mention a test that exercises it.  
**Why it matters ·** If a browser ever fires `updatefound` before setting `installing`, the update would be lost with no record.  
**Suggested test ·** Add a case that dispatches `updatefound` on a `FakeRegistration` whose `installing` is null, then sets `installing` and advances state on the next tick, asserting the listener is still called.

### 4. No test for failed-registration memoization
**Gap ·** The Phase 7 self-review (S2) noted that a rejected registration is memoised forever. M10 tests idempotence of the happy path, but not the failure-memo behaviour.  
**Why it matters ·** The intended behaviour (one attempt per page load, no retry) is undocumented and unpinned. A future change that cleared the memo and retried would pass all current cases.  
**Suggested test ·** Add a case: registration rejects, then `registerServiceWorker()` is called again; assert `container.register` was called exactly once. Document the choice in the module comment.

### 5. No test that the hook works inside a React effect
**Gap ·** `onUpdateWaiting` returns an unsubscribe function, which is the shape expected by `useEffect`. There is no case that exercises subscribe → effect cleanup → no further notifications.  
**Why it matters ·** A subtle bug where the unsubscribe removes the wrong listener, or where a listener added during registration is not cleaned up, could be missed.  
**Suggested test ·** Add a case that mimics React: subscribe, call the returned cleanup, then push an update, and assert the cleaned-up listener is not called while another listener still is.

### 6. No test for the service worker file itself not being precached
**Gap ·** AC7 asserts the precache manifest contains only `/app/*` URLs, no denylisted URL, and the shell. It does not assert that `/app/serviceWorker.js` is absent from the manifest.  
**Why it matters ·** The worker script should never be cached by itself; if the plugin ever emitted it into the precache manifest, the worker would try to cache itself.  
**Suggested test ·** Add an assertion that no manifest entry's resolved pathname is `/app/serviceWorker.js`.

### 7. No test for `onUpdateWaiting` when registration resolves to `undefined`
**Gap ·** `onUpdateWaiting` calls `registerServiceWorker()` and checks `registration?.waiting`. If registration is `undefined`, it does nothing. This path is not explicitly tested.  
**Why it matters ·** A regression that threw when `registration` was `undefined` would break the mount path on jsdom or insecure origins.  
**Suggested test ·** Add a case: `navigator.serviceWorker` is absent, `onUpdateWaiting` is called, and the listener is never called and the function returns an unsubscribe that is safe to call.

### 8. No mutant for AC9 (no user-facing strings)
**Gap ·** The manifest acknowledges AC9 has no mutant because introducing a string is not a mutation of existing behaviour.  
**Why it matters ·** A prohibited behaviour is best guarded by both an assertion and a mutation that would violate it. A mutant that adds a hardcoded English string to the registration module would be caught by the current assertion, but no such mutant is run.  
**Suggested test ·** Optional: add a mutation break that introduces a hardcoded string literal in the module and assert the source-scan case fails.

---

## Non-gaps (acknowledged, no change needed)

- **57/57 cases pass** and **17/17 mutants caught**.
- **M3 finding addressed:** AC7's precache matcher is now a named function tested with synthetic denylisted entries.
- **AC1** is guarded by source scan, emitted worker scan, and app-chunk scan.
- **AC2** is guarded by plugin config assertions and M6/M12.
- **AC3** is guarded by 8 lifecycle cases; M1/M2 protect notification correctness.
- **AC4** is guarded by one-registration-site, one-`__WB_MANIFEST`, one-`filename`, and idempotence tests.
- **AC5** is guarded by explicit scope assertion and M9.
- **AC6** is guarded by denylist identity, shell URL identity, `NetworkOnly` default, and route ordering; M4–M8.
- **AC7** is guarded by real + synthetic precache matcher; M3, M16, M17.
- **AC8** is guarded by source hygiene and precache-only-cacheable assertions; M12.
- **AC9** is guarded by `i18next`/`t(` absence assertions.
- **AC10** is guarded by drift guards over `agents.md:91` and `package.md:320`; M14/M15.
- **AC11** baseline is 371/373 with the two known environmental T07 failures.
- **No Playwright test for real browser lifecycle** is an inherent jsdom limitation; the task scope ends before the prompt UI.

---

## Summary

The T12 test suite is excellent: 57 cases, 17 mutation breaks caught, and a strong set of build-time assertions. The main remaining gaps are the missing `main.tsx` source scan (already flagged in Phase 7), a few lifecycle edge cases (sequential updates, null `installing`, failure memoization, React-effect-shaped unsubscribe), and a self-precache guard. Adding these would make the suite a complete regression guard for the worker registration and update policy.
