<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T40 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T40 — Public route set is exact |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **47/47 passed** |
| `python3 -I .ai/prompts/frontend/T40/artifacts/07-mutation-harness.py` | **16/16 regressions killed, 1 unfalsifiable scored apart (RS04), 0 broken, 0 disagreements** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1742/1743 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |

No production or test code was changed during this review.

---

## Findings

### F1 — `GUARD_FORBIDDEN` misses several auth-layer actions the gate must not take
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:311-355` lists 37 forbidden patterns. It includes `retainAccessToken`, `clearAccessToken`, `retainIdToken`, and `takeIdToken`, but it does NOT include:
  - `accessToken.adopt` (also sets the token, via `retainAccessToken`)
  - `accessToken.renew` (an action on the session)
  - `markSignedOut` / `markSignedIn` (cross-tab sign-out latch writers)
  - `broadcastSignOut` / `listenForSignOut` (cross-tab sign-out propagation)
  - `signOut` / `endSession` (the clearing and hand-over primitives)
· **Assessment:** The gate is meant to be a pure read of `accessToken.current()`. A mutant that called `accessToken.adopt('x')` inside the render would not be caught by the source sweep, because the regex only forbids the standalone writer functions, not method calls on the `accessToken` object. Similarly, a mutant that called `markSignedOut()` would set the cross-tab latch as a side effect of rendering.
· **Recommendation:** Add the missing patterns to `GUARD_FORBIDDEN`, with paired probes. Specifically: `/\badopt\b/`, `/\brenew\b/`, `/\bmarkSignedOut\b/`, `/\bmarkSignedIn\b/`, `/\bbroadcastSignOut\b/`, `/\blistenForSignOut\b/`, `/\bsignOut\s*\(/`, `/\bendSession\s*\(/`. Add controls proving each fires.

### F2 — `GUARD_FORBIDDEN` has no duplicate-pattern check
· **Severity:** **Major**
· **Evidence:** T37's AC7 control added `expect(new Set(FORBIDDEN_REACHES.map(([pattern]) => pattern.source)).toHaveLength(FORBIDDEN_REACHES.length)` to catch duplicated rows. T40's AC7 control (`frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:1020-1027`) only checks that each pattern fires on its probe.
· **Assessment:** A duplicated pattern would leave the length unchanged while one surface was swept twice and another not at all. The control would still pass because each duplicated pattern fires on its probe.
· **Recommendation:** Add the same duplicate-pattern assertion to T40's AC7 control.

### F3 — `RequireSession` still has no explicit return-type annotation
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:32` — `export function RequireSession() {`. Phase 5's plan published `RequireSession(): ReactElement | null`, and the self-review's F8 noted this.
· **Assessment:** Phase 10 did not add the annotation. Inference is sound, but the contract is not visible in source. This is the one thing a reader of a route guard most wants to know.
· **Recommendation:** Add `import type { ReactElement } from 'react';` and annotate the return type as `ReactElement | null`.

### F4 — No test verifies an authenticated path with a query string or trailing slash is still blocked
· **Severity:** **Minor/Major**
· **Evidence:** AC2 tests each authenticated path in its normal form. AC1 tests trailing slash and query string for `verify` only.
· **Assessment:** React Router normalizes trailing slashes, and the gate reads only `accessToken.current()`, so a query string should not affect the decision. But this is an assumption that a regression could break (e.g., if someone added route-reading logic).
· **Recommendation:** Add two boundary cases: `/app/home?foo=bar` is blocked without a session, and `/app/home/` (normalized to `home`) is blocked without a session. These complement the public-path boundary cases.

### F5 — No test verifies the gate under `StrictMode` WITH a session
· **Severity:** **Minor**
· **Evidence:** AC2 has a StrictMode case without a session. AC4 tests with a session but not under StrictMode.
· **Assessment:** The gate is a pure read, so StrictMode should not matter, but a test that combines the two conditions would pin it.
· **Recommendation:** Add a StrictMode variant of AC4.

### F6 — The gate is still dormant in production
· **Severity:** **Blocker** (process)
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` notes that no authenticated screen exists yet (task 42, blocked on 173/174) and `endSession()` has no caller.
· **Assessment:** The gate mechanism is built and tested, but no production route has a real screen behind it. R28's first clause is verified only by probes.
· **Recommendation:** None for T40. The handoff pins will make it visible when task 42 attaches a screen.

### F7 — `GUARD_FORBIDDEN` does not forbid `document.title`, `window.name`, or `history.state`
· **Severity:** **Minor**
· **Evidence:** T37's `FORBIDDEN_REACHES` includes these surfaces. T40's `GUARD_FORBIDDEN` does not.
· **Assessment:** The gate renders nothing, so leaking via these surfaces would be intentional misbehavior rather than incidental. The risk is lower than for a screen, but the surfaces are still ways a render could leave state behind.
· **Recommendation:** Add `/document\.title/`, `/window\.name/`, and `/history\.state/` to `GUARD_FORBIDDEN`, with controls, for parity with T37's privacy sweep.

### F8 — The `withProbes` instrument strips `Component` and `errorElement` from copied routes
· **Severity:** **Minor**
· **Evidence:** `withProbes` copies only `path`, `element`, `children`. It asserts no fourth key exists.
· **Assessment:** This is correct and deliberate. The assertion means a future route with `Component` or `errorElement` would fail the instrument rather than being silently dropped. Good.
· **Recommendation:** None.

### F9 — The `historyAction` assertion is a React Router internal
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:940` asserts `router.state.historyAction` is `'POP'`.
· **Assessment:** This is an effective way to detect an unintended navigation. If React Router changes the API, the test would need updating. The risk is acceptable.
· **Recommendation:** None.

### F10 — The `GUARD_FORBIDDEN` scan does not forbid dynamic `import()` or `require()` from `app/`
· **Severity:** **Minor**
· **Evidence:** AC6's layering scan uses `/from ['"]\.\.\/app\//`.
· **Assessment:** A dynamic `import('../app/routes')` would bypass the static-import regex. This is unlikely but possible.
· **Recommendation:** Add `/import\s*\(\s*['"]\.\.\/app\//` and `/require\s*\(\s*['"]\.\.\/app\//` to the layering scan, or accept it as covered by the static-import rule.

### F11 — No test verifies the gate does not render for the catch-all WITH a session
· **Severity:** **Minor**
· **Evidence:** The catch-all is public and is tested without a session.
· **Assessment:** The gate is not applied to the catch-all, so session should not matter. A test would be redundant.
· **Recommendation:** None.

### F12 — The `Probe` component introduces text content, which is fine for the control but means AC8's exact emptiness depends on the probe not rendering
· **Severity:** **Minor**
· **Evidence:** `Probe` renders `<i data-probe={path}>{`probe:${path}`}</i>`. The AC2 case expects `container.innerHTML === ''`.
· **Assessment:** This is correct: the probe only renders when the gate opens. The emptiness assertion is precise.
· **Recommendation:** None.

### F13 — The file header still carries T37/T40 shared-ownership explanation
· **Severity:** **Informational**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:357-362`.
· **Assessment:** The header now correctly notes the gate exists. The shared-ownership explanation is still relevant because `tasks.md` names the same test for both tasks.
· **Recommendation:** None.

### F14 — The `routes.tsx` import-cycle comment still does not mention `RequireSession`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/routes.tsx:2-5`.
· **Assessment:** `RequireSession` does not create a cycle, but the comment is incomplete.
· **Recommendation:** Optional: update the comment to mention that `RequireSession` is also imported but does not participate in the cycle.

---

## What the test suite gets right

- **S1 — The AC3 control is the strongest part of the suite.** The pass-through gate makes AC2 falsifiable; without it, AC2 would measure the absence of screens rather than the presence of a guard.
- **S2 — The `withProbes` instrument asserts its own baseline.** It checks that exactly one pathless gate is found and that the right number of probes are attached, derived from the exported arrays.
- **S3 — Every mutant is killed by a named case in this task's own files, with no disagreements.** The harness improvement from phase 7 (expectation field, `own` vs `inherited-only`) is working.
- **S4 — AC7's 37-pattern sweep has paired probes for every forbidden surface, applying T37's lesson.** (But see F1 and F2 for missing patterns and duplicate check.)
- **S5 — AC13 correctly closes the pathless-element hole** that the screen-count pins cannot see.
- **S6 — AC10 uses `historyAction` to detect unintended navigation rather than relying on `textContent`.**

---

## Summary

T40's Phase 10 is strong, with the AC3 control and the guard-identity cases being the standout work. The most important remaining gaps are **F1** (`GUARD_FORBIDDEN` misses auth-layer method calls and cross-tab primitives), **F2** (no duplicate-pattern check in T40's AC7 control), and **F3** (`RequireSession` still lacks an explicit return type). **F4** and **F5** are smaller boundary/StrictMode additions. The suite and gates are independently reproducible, and the one whole-suite failure is the known environmental `oxlint` host case.

**Phase 11 complete.** Fourteen findings returned; no code or contract changed.
