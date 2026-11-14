<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation Notes) -->

# frontend · T43 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/04-freeze-task.md` (D1–D17) · `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

| | |
|---|---|
| `app/ui/Form.tsx` | `<form noValidate>`, `preventDefault()` **inside** (D13), ref → `FORM` |
| `app/ui/EmailField.tsx` | `<label htmlFor>` + `<input type="email">`, `useId()` inside, ref → the **input** |
| `app/ui/PasswordField.tsx` | the same shape with `type="password"`, plus `aria-invalid?` |
| `app/ui/SubmitButton.tsx` | `<button type="submit">`, `ActionButton`'s token string |
| `features/account/passwordLength.ts` | `countCodePoints`, `ADVISORY_MINIMUM_CODE_POINTS` 12, `ADVISORY_MAXIMUM_CODE_POINTS` 128 |
| `features/account/RegisterScreen.tsx` | the screen — **the first file in `features/`**, 42 tasks after the design named the directory |
| `api/client.ts` | `createPublicAuthApi(rateLimit?)` (D4); `createAuthApi` untouched |
| `app/routes.tsx` | `REGISTER_PATH`; `RegisterScreen` attached in the existing public `.map()` |
| `i18n/catalogue.ts` | the `register` group, six keys (D7) |
| `app/ui/index.ts` | four exports, and a header that answers the form-field invitation and records D3's exception |
| **six instruments grown** | listed below — **one of them in a suite no document named** |
| `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **34 cases**, AC1–AC11 + D1/D7, the length cases as a **table** task 44 adds a row to |

**State:** whole suite **1930 passed, 1 failed of 1931** — the documented host `oxlint` case, unchanged —
`tsc` exit 0, `oxlint src` exit 0. Committed and pushed **before** any harness run (`revert()` is
`git checkout` plus `git clean` over `frontend/src/`).

## §4's prediction, measured

The freeze predicted that **D11's eleven rows would all fail on the first run**, plus the build gate, and
that the two exhaustive `Record`s keyed by the primitive union would fail **as a `tsc` error rather than a
test failure**.

**`tsc --noEmit` exited 0 with all four primitives written, the screen written and the route wired.** The
prediction was right about the mechanism and **inverted about the order**: both `Record`s are keyed by
`(typeof PRIMITIVES)[number]`, and `PRIMITIVES` is a **list inside the test file**. A new primitive is
invisible to the type until that list grows — so `tsc` was not the first instrument to care but the **last**,
and it is a consequence of growing the instrument rather than a cause of growing it. The moment four names
went into the list, both `Record`s failed to compile, exactly as the freeze described. The same shape T42
recorded for count pins ("a count pin over a list nobody has edited yet measures nothing") applies to a
**type** keyed by a hand-written list, which is new and worth carrying forward.

The suite's first run gave **five real failures in four files** (plus the environmental one), not eleven:
D11's eleven *rows* mostly live in one file, and a file reports one failing **case** however many rows are
stale inside it.

| predicted? | instrument | what it actually read |
|---|---|---|
| **yes** | `screensRender…`'s lowercase-tag sweep | `[…12 bearers]` vs the pinned 8 |
| **yes** | T173's catalogue key-set pin | 22 paths vs 16 |
| **yes** | `publicRouteSetIsExactlyTheDeclaredSet`'s AC9 walk | six attached paths vs five — and `register` lands **third**, because `walk` reads `PUBLIC_ROUTE_PATHS` in order |
| **yes** | `routesResolveUnderAppPrefixOnly`'s `SCREENS_ATTACHED_SO_FAR` | the same six, through a different derivation |
| **no — and nobody had named it** | **T16's credential-shape scan** in `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | three hits: `PasswordField.tsx`'s `type="password"`, `Form.tsx`'s `<form`, and `RegisterScreen.tsx`'s `<Form` |
| **no — fired on the second pass** | T42's AC7 count guard | it pins `/toHaveLength\(7\)/`, `/toHaveLength\(9\)/`, `/\.toBe\(16\)/` **in another file's source**, so it could only fail after `screensRender…` was edited |
| **no — and it was WRONG, not stale** | T173's "no primitive holds user-facing copy" | `PasswordField.tsx` was reported as spelling `register.passwordLabel`, because the catalogue's first **single-word** string is `'Password'` and the scan was a substring `toContain` |
| **no — and it did not fire at all** | T173's barrel-importer derivation | a **silent hole**: its regex matched `'./ui'` and `'../app/ui'` only, so the first screen two levels deep imported the barrel as `'../../app/ui'` and the equality passed while measuring one file fewer |
| **not in D11's list** | `FOCUSABLE` | three rows short: an `<input>` is focusable by default, so its ring is the easiest one to leave to the browser |

**The credential scan is the finding of this phase.** T16 wrote its `<form>` pattern *"broad ON PURPOSE, and
it will fire on a future search or preferences form (Kimi Gap 5). That is the intended friction: L2 puts ALL
credential collection on the server, so a production form is a thing somebody should have to justify in this
table rather than add quietly."* Task 43 is the task that paid it, **eleven tasks later**, and the payment is
a table of three named rows — by file, by shape and **by count** — plus a staleness case, plus a case
asserting the rows that are **not** there: no `one-time-code`, no `current-password`/`new-password`. L2 is
not relaxed by any of it, and the reason is in the table's own header: L2 governs interactive **login**
steps; account **creation** is R1, which names the registration form and sends the password to
`POST /accounts`. The SPA now collects a password exactly once, to create an account, and never to
authenticate one.

## Deviations

| | | why |
|---|---|---|
| **D-1** | **The screen consumes the advisory count**: out of range sets `aria-invalid` on the password field, and the request is sent anyway | D16/D17 expected removing the check to be **unobservable** (the `unfalsifiable` row). An advisory check with no consumer is not a check — it is dead code the harness would have proved dead. Nothing is blocked and nothing is disabled, so R1 is satisfied either way; the mark is measured by every row of the length table. **D17's unfalsifiable row is now falsifiable**, which phase 7's harness must re-aim rather than inherit |
| **D-2** | **`<form noValidate>`** | Not in the freeze. The browser's own bubble would reject `type="email"` input before the handler ran, which would make the SPA's advisory check unreachable for a malformed address and would show copy no catalogue owns. R1 puts the authority on the server |
| **D-3** | **`FOCUSABLE` grown 3 → 6**, an instrument D11's eleven rows did not list | D17 names a mutant that removes `SubmitButton`'s focus ring, so the obligation existed; the row to grow did not. Recorded so the count of "instruments nobody named" stays honest |
| **D-4** | **`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` modified** — not in Files to Modify | It had become **false**: `expect(found).toEqual([])` over all of production. Narrowed as little as possible (three rows, exact counts) and **strengthened** with two cases it did not have: a staleness check and a claim about the shapes the table may never excuse |
| **D-5** | **T173's copy scan loosened** from `toContain(sentence)` to a word-boundary regex, with a control | The old scan reported a file as spelling copy because the word was a **substring of its own component name**. The claim was never about substrings: a primitive must not *spell* a sentence it should take as children, and a sentence is spelled in a string literal or as JSX text. Loosening an instrument is the edit that most needs a control beside it, so the control asserts **both** directions |
| **D-6** | **T173's barrel-importer regex widened** to any depth | Not a failure — a hole. It passed while being blind to `features/account/`. Adding the render site is what surfaced it, because the scan's equality is against that list |
| **D-7** | **`PUBLIC_ROUTE_PATHS`' `'register'` literal replaced by `REGISTER_PATH`** | One spelling, the reason the other four constants exist. The declared set is unchanged, which AC8 asserts against a hand-written list |
| **D-8** | **Two claims in my own new suite were false, and measurement said so** | (a) the AC5 source mask forbade `['"`]/accounts`, which is the **correct** call through the generated client — a mask wrong in both directions, and one a later reader would have "fixed" by deleting the case; (b) AC10 asserted the password reaches **no attribute of any element**, and React 19 mirrors a controlled input's value into the `value` **content attribute**, so `form.outerHTML` contains it. The claim is now the one worth making: the password reaches nothing *else*, and the field that holds it is still `type="password"` |
| **D-9** | **`createPublicAuthApi`'s `rateLimit?` parameter has no caller** | Nothing in production constructs a `RateLimitNoticeState` — `AppShell` does not, and `RateLimitNotice` is a render site with no mount. Pre-existing (R23 is unwired platform-wide); the parameter is frozen by D4 and kept so the screen does not have to change shape when the notice is wired. **Named here so it is a known dormancy, not an oversight** |

## Open questions this phase opened

- **OQ-T43-7** — the two fields carry **no `autoComplete`**. A registration form would normally hint
  `username` and `new-password`, which is what tells a password manager to offer a generated password. The
  credential scan treats `new-password` as a login-credential shape with **no permitted row**, so adding the
  hints is a deliberate table entry and an owner's decision, not a quiet improvement.
- **OQ-T43-8** — **Enter-to-submit is unmeasurable here.** jsdom implements no implicit submission, so AC9
  asserts the structural cause (a `type="submit"` control inside the form) and measures the click path that
  shares the handler. The same `@testing-library/user-event` dependency OQ-T174-5 costs would close both.
- **OQ-T43-9** — the screen **reports success on a 500**. R2 and L4 forbid branching on the response, and
  the server answers 202 for a new address and for one already registered; a client that distinguished
  outcomes would rebuild the difference R2 removes. The consequence is pinned by an AC11 row rather than
  hidden, and task 45 owns the failure state.

Carried unchanged: **OQ-T43-6**, **OQ-T42-5** (and `/app/register` **is** such a public path), OQ-T42-3,
OQ-T33-2's authenticated half, OQ-T40-1, OQ-T173-4/5, OQ-T174-3/5, FU-T42-1, FU-T37-2, FU-T39-1, task 38's
Q15.

## What the freeze got right that mattered

- **D3 is the decision that saved the task.** The challenge missed that a labelled field is **two**
  elements; T173's *"exactly one element to render, which is its whole job"* case would have failed on both
  field files. The freeze named it, and the instrument now carries a **sized** exception table (three rows:
  the react-router delegate at 0, the two fields at 2) rather than a loosened rule.
- **D1 was right about the live region.** The `StatusRegion` is mounted empty from the first render and
  filled afterwards, and the suite asserts it is **the same node** — a region replaced between renders is a
  new one to the accessibility tree, and would be announced by nothing.
- **D4 earned its own file.** `createAuthApi` needs a `SessionBoundary`; the alternative to
  `createPublicAuthApi` was a screen hand-writing an inert implementation of a security interface.

## Not done, deliberately

No advisory, rejection, strength or hint copy (D7 — task 45). No `register/sent` screen and no navigation
(D1 — task 47). No 128-code-point case (task 44; the table takes it as a row). No focus move on mount (D5).
No state library (D9). No `autoComplete` (OQ-T43-7). No failure state (OQ-T43-9).
