<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T41 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T41 — Unauthenticated redirect |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

All commands run independently of the self-review, on the same tree.

| Command | Result |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1745/1746 passed** — 1 known environmental failure in `lintRejectsHandWrittenBackendFetch.test.ts` |
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx src/app/routesResolveUnderAppPrefixOnly.test.tsx` | **59/59 passed** |
| `python3 -I .ai/prompts/frontend/T41/artifacts/07-mutation-harness.py` | **14/19 regressions killed, 1 unfalsifiable scored apart (RD09), 0 broken, 0 disagreements** |

The independent run matches the self-review's numbers exactly. No production or test code was changed during this review.

---

## Findings

### I1 — The callback fixture change claimed in F9 is real and is the right fix
· **Severity:** **Minor/Major** (process)
· **Evidence:** `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx:115-116` and `frontend/src/auth/callbackValidatesNonce.test.tsx:124` now resolve `{ idToken, accessToken: 'a-token-the-exchange-returned' }`.
· **Assessment:** I checked both files. The fixtures previously resolved only `idToken`, so after T41 the callback screen's retained access token left `/app/home` blocked and the tests' "not sign-in" assertions collapsed. The fix makes the fixtures faithful to `CallbackScreen.tsx:170`, which retains the access token returned by the exchange. No assertion in either suite is weakened; the seam is now more like production.
· **Recommendation:** Accept as-is. Phase 10 should not need to touch these files.

### I2 — `RedirectToSignIn` writes to `sessionStorage` during render, which is a deliberate React rules deviation
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/RedirectToSignIn.tsx:67` calls `rememberReturnTarget(arrival)` before returning.
· **Assessment:** The self-review (F8) and the component header both acknowledge this. The write is idempotent in outcome and the value is derived only from `useLocation().pathname`. Under StrictMode the component renders twice and writes twice with the same value. In concurrent React, renders can in principle be discarded; because the value is arrival-derived, a discarded render and a retried render write the same value, so no observable state corruption occurs. It is still a side effect in render, and the team has chosen it over `useLayoutEffect` to keep the write before the navigation decision and to avoid adding a hook surface to the source scan.
· **Recommendation:** Keep it, but make the deviation explicit in the component header (it already is) and ensure Phase 10's tests assert stored **value**, never write count. No code change needed.

### I3 — RD09 is behaviorally unfalsifiable, but a source scan is not behavior
· **Severity:** **Minor/Major**
· **Evidence:** The harness scores RD09 as `unfalsifiable` because skipping `rememberReturnTarget` for `/no-such-route` produces the same outcome as calling it and letting the allowlist refuse.
· **Assessment:** The self-review is correct that no **behavioral** assertion can separate the two. However, the design rule "no branch, the allowlist is the only filter" is a **source** rule, not a behavioral one. A source scan that forbids an `if` (or ternary) on `arrival` before the `rememberReturnTarget` call would catch RD09. Calling it "unfalsifiable" is true for the harness as written, but it is not true for the task's overall test strategy.
· **Recommendation:** Phase 10 should add a source scan in `unauthenticatedRoutesRedirectToSignIn.test.tsx` that asserts `RedirectToSignIn.tsx` has no branch on the arrival path before calling `rememberReturnTarget`. Move RD09 from "unfalsifiable" to "phase 10" in the harness notes, or keep the behavioral unfalsifiability note and add a source-scan row.

### I4 — The catch-all test in `routesResolveUnderAppPrefixOnly` does not prove the sign-in screen arrived
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:154-155` asserts `shellText` is not empty and does not match `/404|Not Found/i`.
· **Assessment:** This is the self-review's F6, and it is correct. "Not empty and not a 404" is satisfied by any screen or even a stray DOM node. The named suite in Phase 10 should assert the sign-in heading for all three arrivals, which is also what AC9's indistinguishability claim requires.
· **Recommendation:** Phase 10 adds an assertion that the sign-in heading renders for arrivals (a), (b), and (c).

### I5 — No test proves the index route is narrow
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx:162-178` tests `/app/` and `/app` redirect. It does not test that `/app/sign-in` does **not** match the index route.
· **Assessment:** The public-path tests and the redirect-to-sign-in tests together imply this: if `/app/sign-in` matched the index route, it would redirect to itself in a loop, and the test suite would fail. So it is covered indirectly. But there is no explicit narrowness pin.
· **Recommendation:** Optional for Phase 10: add one case that `/app/sign-in` renders the sign-in screen (not a redirect loop) and stays at `/app/sign-in` with `historyAction === 'POP'`.

### I6 — `RedirectToSignIn`'s basename stripping is defensive and its necessity depends on a measurement
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RedirectToSignIn.tsx:58-60` strips `APP_BASENAME` from `useLocation().pathname` before handing it to `rememberReturnTarget`.
· **Assessment:** The comment says `useLocation().pathname` was measured at phase 4 to carry the `/app` prefix. React Router documentation says `useLocation().pathname` is basename-relative. If the measurement is correct, the stripping is load-bearing; if the docs are correct, the stripping is dead code but harmless. Because `APP_BASENAME` is `'/app'` and the allowlist holds `/account`, the wrong path (`/app/account`) would be silently refused and the deep link lost. The defensive strip prevents that loss if the measurement holds.
· **Recommendation:** Keep the defensive code. Phase 10 should add a regression test that arriving at `/app/account` stores `/account` (not `/app/account`) in `RETURN_TARGET_KEY`, which is the only way to pin the measurement against a future React Router change.

### I7 — No source scan exists yet for `RedirectToSignIn.tsx`
· **Severity:** **Major/Blocker** (expected; phase 10 closes it)
· **Evidence:** No test file reads `RedirectToSignIn.tsx` source. The harness rows RD05 (hardcoded literal) and the RD06/RD07/RD08 return-target family cannot be killed without either reading storage or reading source.
· **Assessment:** This is the self-review's F1 in different clothing. The five survivors are exactly the five things a named suite must measure: the destination spelling, the return target storage, and the authenticated-visitor arm.
· **Recommendation:** Phase 10's named suite must include:
  - a source scan forbidding the literal `'/sign-in'` (kills RD05);
  - assertions on `sessionStorage.getItem(RETURN_TARGET_KEY)` after arrival at `/app/account` (kills RD06/RD07/RD08);
  - an arrival at `/app/no-such-route` with a held token that stays blank and at the original path (kills RD02).

### I8 — The `GUARD_FORBIDDEN` update in T40 is complete and correct
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:1146-1150` now checks length and duplicate patterns.
· **Assessment:** The phase-11 finding F2 (duplicate-pattern check) and F1 (missing auth-layer actions) were addressed. The scan grew from 37 to 50 rows and now includes `adopt`, `renew`, `markSignedOut`, `markSignedIn`, `broadcastSignOut`, `listenForSignOut`, `signOut`, and `endSession`. The duplicate check is present.
· **Recommendation:** None. This is confirmation that T41's phase 6 did not leave T40's instruments in a half-fixed state.

### I9 — The `RequireSession` return type is now explicit
· **Severity:** **Minor**
· **Evidence:** `frontend/src/auth/RequireSession.tsx:40` and `frontend/src/auth/RedirectToSignIn.tsx:45` both declare `ReactElement | null`.
· **Assessment:** This closes the phase-11 F3 finding from T40 and the phase-3 F3 finding from T41.
· **Recommendation:** None.

### I10 — `routes.tsx`'s comment references "phase 8, I13" before phase 8 existed
· **Severity:** **Informational**
· **Evidence:** `frontend/src/app/routes.tsx:6`.
· **Assessment:** The comment anticipates this review and claims the three screen imports and the two auth imports do not participate in the cycle. I verified this: `RedirectToSignIn` and `RequireSession` read `SIGN_IN_PATH` and `APP_BASENAME` only inside component functions, so the cycle claim is true. The forward reference is harmless but slightly odd.
· **Recommendation:** Optional: reword the comment to remove the phase-8 citation now that phase 8 has confirmed it.

### I11 — The mutation harness's expectation field works as intended
· **Severity:** **Informational**
· **Evidence:** All twenty rows matched their predicted outcome; no `INHERITED-ONLY` or disagreement rows.
· **Assessment:** The self-review's claim that every kill is `own` is independently confirmed. The five survivors are all predicted as `phase 10` or `unfalsifiable`. This is a clean phase-7 state.
· **Recommendation:** None.

### I12 — The `withProbes` derivation correctly handles the index route
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:197-225`.
· **Assessment:** The derivation now allows `index`/`element` for the index route and asserts no other keys are silently dropped. It counts pathless leaves separately from gates. This addresses the self-review's F2.
· **Recommendation:** None.

### I13 — Module boundaries are respected
· **Severity:** **Minor**
· **Evidence:** `RedirectToSignIn.tsx` imports `APP_BASENAME` and `SIGN_IN_PATH` from `../app/routes`; `RequireSession.tsx` imports only from `./accessToken` and `./RedirectToSignIn`.
· **Assessment:** No `auth/` module imports screens or non-constant app modules. The cycle documented in `routes.tsx` is preserved.
· **Recommendation:** None.

### I14 — The whole-suite failure is environmental and unrelated
· **Severity:** **Informational**
· **Evidence:** `lintRejectsHandWrittenBackendFetch.test.ts` fails because this host's `oxlint@1.87.0` prints no summary line.
· **Assessment:** Same failure as every prior task in this session. It is not caused by T41.
· **Recommendation:** None.

---

## Summary

The self-review's findings are accurate and the implementation is in a clean phase-7 state. My independent run reproduced the same numbers: 14/19 regressions killed, 1 unfalsifiable, 5 survivors, 0 disagreements. The most important additions are **I3** (RD09 is behaviorally unfalsifiable but not source-unfalsifiable; a source scan should enforce the no-branch rule), **I4** (the catch-all test is too loose about what screen arrived), and **I6** (the basename strip is defensive and needs a regression test that pins the stored value). The callback fixture change claimed in F9 is real and correct. Phase 10's priority order remains: return target storage first, then the authenticated-visitor arm, then the source scan for the destination literal.

**Phase 8 complete.** Fourteen findings returned; no code or contract changed.
