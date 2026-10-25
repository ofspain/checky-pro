# frontend · T16 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/11-test-review.md`, the working tree |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim):**
> **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

---

## Verdict

**PASS.** And for the first time in this pipeline, the central requirement is not merely asserted — it is
observed, in a real browser, against the running platform.

| | |
|---|---|
| Whole suite | **560 pass / 562 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch`, **two different** pre-existing environmental faults: AC4 because this host's `oxlint@1.87.0` prints no summary line; AC2 because bare `pnpm` is not on PATH (`npx -y pnpm@12.14.0` is the working recipe). |
| `tsc --noEmit` / `oxlint` | clean |
| Suite delta | 531 → 562, **+31**, matching the case count |
| Mutants | **24 defined, 23 scored, 22 caught** (M24 malformed and excluded; M18 equivalent by design) |
| Files changed | 7 |

### R6, demonstrated

Headless Chrome, driven over the DevTools protocol against the live stack (eleven containers, all four services
ready):

| Step | Observed |
|---|---|
| `/app/sign-in` renders | `<main><h1>Sign in</h1><p>…</p><button type="button" aria-disabled="false">Continue to sign in</button><div role="status" aria-live="polite"></div></main>` |
| `sessionStorage` before the click | `[]` |
| Click → | `/oauth2/authorize?response_type=code&client_id=checky-spa&redirect_uri=…%2Fapp%2Fcallback&scope=openid+profile+email&state=…` |
| → | `/login`, title **"Please sign in"** |
| `input[type=password]` on that page | **present** |
| `sessionStorage` after the redirect | the three L16 items, verifier 43 chars |

**There is a password field in this flow. It is on the server's page.** The SPA has none. That is R6 and L2
together, shown rather than claimed, with a screenshot in `artifacts/signin-screen.png`.

---

## Acceptance criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 — no password input, no credential input at all | PASS | six DOM shapes, decoy control · M4, M19 |
| AC2 — choosing sign-in starts the flow | PASS | `assign` spy; URL compared parameter-for-parameter with a directly built request · M6, M16 |
| AC3 — nothing until the visitor acts | PASS | mount spies; confirmed in a real browser · **M3** |
| AC4 — the three L16 items, no fourth | PASS | key-set equality; challenge recomputed from the stored verifier · M14 |
| AC5 — catalogue-only copy | PASS | rendered text equals catalogue values; source scanned for those values; copy properties pinned · M5, M20, M21, M22 |
| AC6 — keyboard operable, no focus loss | PASS | real button, `aria-disabled`/`aria-busy` not `disabled`, focus retained, two-click guard · M1, M2, M9, M23 |
| AC7 — structure and live region | PASS | one `h1`, one `main`, one status region, `aria-describedby` · M10, M11, M13 |
| AC8 — fails closed, retryable, no error text, no escaping rejection | PASS | real retry; process-channel guard · M7, M8, M9 |
| AC9 — T01 and T02 intact | PASS | both green; T02's guard narrowed with a counting predicate · M16 |
| AC10 — nothing else modified | PASS | 7 files; `pkce.ts` and `authorizeRequest.ts` untouched |
| AC11 — measured delta | PASS | +31 |
| AC12 — non-vacuity | PASS | decoys for the credential matcher, the copy scan, the route predicate · M19, M20 |
| AC13 — no Storage or console of its own | PASS | runtime spies, source scan · M14 |
| AC14 — reachable at `/app/sign-in` | PASS | real mount; and a real browser · M16, M17 |
| AC15 — the cycle stays safe | PASS | source scan + entry-order import · **M15** |

**All ten locked decisions discharged.** D-1 the synchronous ref (M1), D-2 focus not moved, D-3 the inverted copy
scan (M5, M20), D-4 T15 authoritative for the shape, D-5 `state`/`nonce` discarded, D-6 the silent error path on
the record, D-7 the counting predicate, D-8 the in-flight announcement (M13), D-9 T02 narrowed (M16), D-10 no
styling and no new dependency.

---

## Files

| File | Change |
|---|---|
| `src/auth/SignInScreen.tsx` | **new**, 72 lines |
| `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` | **new**, 31 cases |
| `src/app/mountShell.tsx` | the `<I18nextProvider>` |
| `src/app/routes.tsx` | `element` on `sign-in`, and the cycle named |
| `src/i18n/catalogue.ts` | five `signIn.*` keys |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | T02's absence assertion narrowed |
| `src/app/bundleContainsNoSecretEnvKeys.test.ts` | T13's vendor-URL list, justified in place |

**Two earlier tasks' tests were changed, both declared.** Phase 2 predicted T02's; it did not predict T13's, which
caught react-i18next's error-documentation URL entering the bundle because `useTranslation` became reachable. That
guard did its job two tasks after it was written.

---

## What this task taught, recorded for the ones after it

1. **`act()` does not await anything that leaves the microtask queue.** A real `crypto.subtle` call settles
   later, so assertions raced the navigation and one case's async work was attributed to the next. Every screen
   task that awaits real crypto, a timer or a network boundary will meet this.
2. **A guard can watch the wrong channel.** The `unhandledrejection` listener I added was on `window`; in this
   environment rejections surface on Node's `process`. It passed before, during and after the bug.
3. **A mutation harness that counts failures misses the worst mutants.** One that stops files loading shows
   *fewer* tests, not more failures. M15 reported two failures while 120 tests never ran.
4. **A review finding applied without a guard is unwatched.** `aria-busy` shipped from a review fix and M23
   removed it with the suite still green.
5. **The self-comparison trap, for the sixth time** (T12 AC7, T13 AC8, T14 AC1, T15 S1/S2 and M10/M12, T16 M18).
   Here it was benign — the catalogue *should* be the source of truth for wording — but it forced the useful
   question of which properties the wording must have.

---

## Carried forward

- **OQ-1 — the five sign-in strings are the owner's.** Neutral, no credential type named, no account state
  disclosed, and those two properties are now enforced by a test. Rewording is one file and breaks nothing.
- **The `<main>` landmark will need revisiting** when a second screen lands and the shell gains chrome.
- **No assistive technology has been run against this screen.** The ARIA is structural.
- **The AC14 case leaks a router**, because `mountShell` returns the `Root`. Recorded with its reason.
- Unchanged and still open: T12's OQ-1 (a refresh cannot promote a waiting worker); `design.md` §4c heading still
  says "PROPOSED, pending sign-off (Q6)" though Q6 closed; §4c calls auth T19 "not built" while L5 records it as
  built; `package.md:172` maps `serviceWorkerUpdateWaitsForReload` to R57, which does not describe it;
  `package.md:321-323` forced-update-for-rollback unowned; `segmentPrefixPattern` duplicated between
  `navigationDenylist.ts` and `apiRequestPolicy.ts`; `apiRequestPolicy.ts:46` lists `/app` though it 404s; T01
  asserts against a development bundle; the two environmental lint failures need `pnpm` on PATH and an oxlint
  summary line.

---

## Next

Phase 13 — PR preparation.
