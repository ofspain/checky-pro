# frontend · T16 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md`, the implementation |
| **Produces** | `artifacts/10-test-generation.md` |

---

## The suite

**31 cases** in `src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx`, plus the
narrowed route guard in T02's file and the justified vendor URL in T13's.

| | |
|---|---|
| Whole suite | **560 pass / 562 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch`, two **different** environmental faults: AC4 because this host's `oxlint@1.87.0` prints no summary line, AC2 because bare `pnpm` is not on PATH. Pre-existing, unrelated. |
| `tsc --noEmit` / `oxlint` | clean |
| Suite delta | 531 → 562, **+31** |
| Mutants | **22 run, 21 caught** |

---

## Mutation testing

| # | Mutant | Result | Killed by |
|---|---|---|---|
| M1 | the synchronous double-submit guard removed | CAUGHT | the two-clicks-in-one-task case |
| M2 | a real `disabled` attribute | CAUGHT | 2 cases |
| M3 | redirects on arrival instead of on the visitor choosing | CAUGHT | 6 cases — **R6's "chooses"** |
| M4 | a password field on the sign-in screen | CAUGHT | 6 cases — **the task's whole point** |
| M5 | the heading hardcoded instead of from the catalogue | CAUGHT | the inverted catalogue scan |
| M6 | `href =` instead of `assign` | CAUGHT | 10 cases |
| M7 | the failure swallowed, nothing announced | CAUGHT | 3 cases |
| M8 | the rejection escapes the handler | **SURVIVED → now CAUGHT** | the process-channel guard |
| M9 | the in-flight ref not reset on failure | CAUGHT | the real-retry case |
| M10 | the explanation no longer associated with the action | CAUGHT | the `aria-describedby` case |
| M11 | the live region loses `role` and `aria-live` | CAUGHT | 5 cases |
| M12 | a second interactive control | CAUGHT | 17 cases |
| M13 | the in-flight state never announced | CAUGHT | 1 case |
| M14 | a fourth `sessionStorage` key, outside L16 | CAUGHT | 5 cases |
| M15 | `APP_BASENAME` hoisted to module scope | CAUGHT (collection failure) | see below |
| M16 | the screen attached to no route | CAUGHT | 5 cases, incl. T02's narrowed guard |
| M17 | the i18n provider removed from the entry point | CAUGHT (collection failure) | 3 cases |
| M18 | the catalogue copy drifts | **SURVIVED — equivalent by design** | see below |
| M19 | the credential matcher always reports clean | CAUGHT | the decoy |
| M20 | the hardcoded-copy scan iterates nothing | **SURVIVED → now CAUGHT** | the non-empty precondition |
| M21 | the copy starts promising a password step | CAUGHT | the new copy-property guard |
| M22 | the failure copy discloses account state | CAUGHT | the same guard |

### M15 — the circular import, confirmed fatal, and my prediction corrected

Phase 8 found that T16 closes a cycle (`app/routes → auth/SignInScreen → auth/authorizeRequest → app/routes`)
which survives only because `APP_BASENAME` is read inside a function. M15 hoists that read to module scope.

**Result, measured two ways.** In the suite: the whole file set collapses, 562 collected tests down to 442, with
`shellBuildsAndMounts`, `routesResolveUnderAppPrefixOnly` and `authorizeRequestsExactlyTheDeclaredScopes` all
failing to load. In a real browser against the running stack: `<div id="root">` is **empty**. A white screen.

**My phase-8 prediction was wrong in its detail**, and the truth is worse. I predicted
`ReferenceError: Cannot access 'APP_BASENAME' before initialization`. The actual error is:

```
TypeError: Cannot read properties of undefined (reading 'endsWith')
  ❯ src/auth/authorizeRequest.ts:59
```

Vite's transform yields `undefined` for the not-yet-initialised binding rather than throwing on the dead zone. So
the failure names `.endsWith` and points at the line that *reads* the value, with no mention of a cycle at all —
a harder diagnosis than the one I predicted, which is the opposite of reassuring.

**On where the guard lives, honestly.** The source-scan case that pins this property is in the sign-in test file,
which imports the cycle — so when the cycle actually breaks, that file fails to load and the guard never runs.
Detection still happens, loudly, because the suite collapses. But the guard's real value is narrower than it
looks: it catches a hoist that does *not* crash at load — someone writing `APP_BASENAME?.endsWith?.('/')`, which
would silently produce a wrong `redirect_uri` and an exact-match rejection at the server. That is the case worth
having it for, and it is stated here rather than left implied.

### M18 — survived, and should

Changing `heading: 'Sign in'` to `'Sign in now'` passes all 31 cases. Every case compares the screen to the
catalogue, so the catalogue is compared with itself.

**This is correct, not a gap.** The wording is OQ-1, the owner's, and `agents.md:96` puts it in the catalogue
precisely so it can change in one file without touching a component or a test. A test pinning the exact words
would make the owner's own copy change a test failure.

What *is* pinnable is the set of properties the wording must have, and those come from requirements rather than
from taste. Added in this phase: the sign-in copy may name no credential type (`password`, `passcode`, `pin`,
`username`, `email address`) per R6 and L2, and may disclose no account state (`account exists`, `no such`,
`not found`, `locked`, `suspended`, `incorrect`) per L4. **M21** and **M22** prove that guard has reach — a copy
change to "Enter your password on the next page." and one to "That account was not found or is locked." are both
caught.

So M18 is an equivalent mutant with respect to every property anyone has specified, and the properties nobody had
specified are now specified.

### M8 — a guard that was itself wrong

M8 makes the handler rethrow *after* updating the screen. Every case stayed green: the screen is correct, and
`void startSignIn()` turns the throw into an unhandled rejection.

I added a listener for `unhandledrejection` on the window — and **M8 still survived**, because the guard was on
the wrong channel. Measured with a throwaway probe: in this environment an unhandled rejection surfaces as Node's
`process.on('unhandledRejection')`, and jsdom dispatches no window event at all. Re-aimed at `process`, and M8 is
caught.

Worth recording because the guard was the thing that needed testing. A guard asserting "no bad event arrived" on
a channel where no event ever arrives is the purest form of the vacuity trap this project keeps meeting — it
passes before the bug, during the bug, and after it.

### M20 — the same trap, one layer down

The hardcoded-copy scan looped over `Object.values(en.signIn)`. Emptying that list made the scan iterate nothing,
and the suite stayed green. The loop now asserts its input is non-empty before scanning, and M20 is caught.

### A harness defect worth naming

My first scoring run called M8 and M15 survivors because it only counted *failures*, and a mutant that stops
files loading produces **fewer total tests**, not more failures. M15 reported "2 failed" — the two environmental
ones — while 120 tests never ran at all. The harness now compares the collected total to the baseline and marks
those `[COLLECTION FAILED]`. Without that, the single most dangerous mutant in this task would have been filed as
a coverage gap I had already fixed.

---

## AC coverage

| AC | Covered by | Mutants |
|---|---|---|
| AC1 — no password input, no credential input at all | DOM query over six shapes, plus a decoy proving the matchers find one | **M4**, M19 |
| AC2 — choosing sign-in starts the flow | `assign` spy, URL parsed, compared parameter-for-parameter with a directly built request | M6, M16 |
| AC3 — nothing happens until the visitor acts | spies on mount | **M3** |
| AC4 — the three L16 items, and no fourth | key-set equality, challenge recomputed from the stored verifier | M14 |
| AC5 — catalogue-only copy | rendered text equals catalogue values; source scanned for those values; copy properties pinned | M5, M20, M21, M22 |
| AC6 — keyboard operable, no focus loss | real button, `aria-disabled` not `disabled`, focus retained, two-click guard | M1, M2, M9 |
| AC7 — structure and live region | one `h1`, `main`, `role=status` from first paint, `aria-describedby` | M10, M11, M13 |
| AC8 — fails closed, retryable, no error text, no escaping rejection | mocked rejection, real retry, process-channel guard | M7, M8, M9 |
| AC9 — T01 and T02 intact | both run; T02's guard narrowed with a counting predicate | M16 |
| AC10 — nothing else modified | `git diff --stat` | — |
| AC11 — measured delta | 531 → 562 | — |
| AC12 — non-vacuity | decoys for the credential matcher, the copy scan and the route predicate | M19, M20 |
| AC13 — no Storage or console of its own | runtime spies, source scan | M14 |
| AC14 — reachable at `/app/sign-in` | real mount through `mountShell`; and in a real browser | M16, M17 |
| AC15 — the cycle stays safe | source scan plus entry-order import | **M15** |

---

## Coverage, honestly

**What is now beyond doubt, because it was observed rather than asserted:** the screen renders in a real browser,
the button reaches the live auth service, SAS answers with its own login page, that page has the password field,
and the three L16 items survive the real redirect. R6 is demonstrated end to end.

**What this suite still cannot tell you:**

- **The in-flight announcement is rendered, not heard.** In a real browser the document is replaced; whether a
  screen reader ever speaks it depends on timing nothing here models. It earns its place for the slow or blocked
  navigation, which is exactly when it would be perceived.
- **No assistive technology has been run against this screen.** `role`, `aria-live`, `aria-describedby` and focus
  behaviour are asserted structurally. That is not the same as a screen reader doing the right thing, and L11 is
  ultimately a claim about the latter.
- **Contrast is browser default**, because there is no styling. True today, and it stops being true the moment a
  task introduces a design system.
- **The cycle guard lives inside the cycle.** When the cycle breaks fatally, the guard dies with the file. It
  catches the quieter variant; the loud one is caught by the suite collapsing.
- **M18 is unkilled by design** — the wording is the owner's, and only its properties are pinned.
- **The AC14 case leaks a router.** `mountShell` returns the `Root`, not the router, so the history subscription
  outlives the unmount. Accepted with its reason in the file; jsdom is torn down per file.
- **The two environmental failures are two different faults**, and I spent several phases calling both "the pnpm
  one". Only AC2 is.

---

## Next

Phase 11 — test review. **Self-produced** at the owner's instruction for this task.
