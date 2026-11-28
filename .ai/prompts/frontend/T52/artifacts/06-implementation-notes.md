<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T52 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

```
suite 2167 / 2167  (43 files, +19 cases, +1 suite)  ·  lint 0  ·  typecheck 0
production change: one screen, one catalogue section, one route constant + element
```

---

## 1 · AC13 — all nineteen cases failed before the screen existed, and the suite **loaded**

```
Tests  19 failed (19)
  15 × TypeError: 'set value' called on an object that is not a valid instance of HTMLInputElement
   3 × ENOENT … src/features/account/PasswordResetRequestScreen.tsx
   1 × AssertionError: the route resolved to nothing: expected '' to contain 'Reset your password…'
```

The plan's step order was **changed before running** (D-T52-4): it had the suite before the catalogue, which
would have made every failure a **type error** on `en.passwordResetRequest` — the "fails to load proves
nothing" trap the plan itself had written down one page earlier. The catalogue went first, so these are
measurements.

## 2 · The freeze said **six** files. It was **ten**

| | |
|---|---|
| Named by the freeze | `routes.tsx`, `catalogue.ts`, T175's scan, `screensRenderThroughDesignSystemPrimitivesOnly`, `publicRouteSetIsExactlyTheDeclaredSet`, `loginChallengeRunsInsideSasFlow…` |
| **Found by measurement** | **`routesResolveUnderAppPrefixOnly`**, `unauthenticatedRoutesRedirectToSignIn`, `homeRouteRendersLandingScreenAndManagesFocus`, `registrationAcceptsTwelveAndOneTwentyEightCodePoints` |

### The same number lives in four files

| Number | Owner | Mirrored in |
|---|---|---|
| render sites, `11 → 12` | `screensRenderThrough…` | `unauthenticatedRoutes…`, `registrationAccepts…` |
| the union, `24 → 25` | `screensRenderThrough…` | `unauthenticatedRoutes…`, `homeRoute…`, `registrationAccepts…` |

**Phase 4 enumerated each file's pins by case title, believing it had finally applied D-T49-3.** That method
cannot find a number that is **copied into a file naming no list of its own** — a second witness is invisible
to an inventory of first witnesses. **The lesson needs its next refinement: grep the NUMBER, not the list.**

### And `routesResolveUnderAppPrefixOnly` was in my own "Files NOT to Modify", with a reason

The TIB said it *"**hard-codes** the public spellings at `:242`, so it is an **independent guard**"*. True — and
not the whole file. `SCREENS_ATTACHED_SO_FAR` sits in the same module and pins **which routes carry an
element**. Right about one pin, blind to another beside it, **one task after writing that exact lesson down.**

## 3 · Four predictions missed, each at the step that falsified it

| Prediction | What happened |
|---|---|
| The credential scan fires at the **route** step | It fired at the **screen** step. It reads production **source**, so the `<Form>` trips it the moment the file exists — no route needed |
| T175's scan fires **both** cases | **Only the census.** My stated diagnostic was **inverted**: a streaming call changes the census alone; one omitting `parseAs` fails both. The row recorded in the scan now says so |
| `:266` (the size pin) fires at the screen step | It fires when the **list** is edited, not when a file appears — the barrel-import equality is what notices a new screen |
| Step 1 is green | Green on four runs — but **red once**, on a **pre-existing flake** (below) |

**One prediction held exactly and was worth making:** the lowercase-tag count stayed at twelve, because this
screen renders no bare element. A red there would have meant one got in.

**And step 6's held:** the tree-wide timer scan needed no new entry, so **AC8** is satisfied by the screen
having no timer at all.

## 4 · FU-T52-1 — a pre-existing flake, and the harness has to know

`loginChallengeRunsInsideSasFlowAndResumesAtCallback` → *"CONTROL: a counter installed after the click sees
nothing at all"* failed **once in five whole-suite runs** and passes in isolation. At HEAD, without my change,
the suite is green. The mechanism is visible: it asserts after **exactly two `turn()`s**, so under load the
handover has not been observed yet and `toHaveLength(1)` reads `0`.

**It matters beyond tidiness:** the mutation harness asserts `EXPECTED_BASELINE_FAILURES = 0`, so a flake can
abort a run or be misattributed to a row. **Recorded, not fixed** — it is not this task's file.

## 5 · Three of my own cases were wrong, in ways worth keeping

### Two source scans read my own **prose** as code

The forbidden-read scan matched the header sentence *"`response.ok` **IS DELIBERATELY NOT CONSULTED**"*, and
the bare-element scan matched an `<h1>` in a paragraph about the document outline. Both now strip comments —
**T173's lesson from the other direction**, since that suite needed a control to prove *a comment naming
`<div>` is not a tag*.

### A leak detector whose needle was the haystack's own copy — **a false positive, twice**

Two arms were given the contract's own example sentence, `"Check your email"`, which is also
`acknowledgedHeading`. The leak case then reported a leak that was **the catalogue doing its job**.

**A false positive is the worse direction for a security assertion**, because the obvious response is to
loosen it. So a **guard** was added rather than just a fix: every arm's needle is asserted absent from
`ACK_TEXT`, naming the arm, and **one shared `needle()`** is used by the guard and the assertion so they
cannot drift.

## 6 · Deviations

| | |
|---|---|
| **D-T52-4** | Step order swapped — **catalogue before the suite**, so the pre-screen failures are measurements and not type errors |
| **D-T52-5** | Files to modify is **ten**, not six (§2) |
| **D-T52-6** | The arm-needle **collision guard**, added after the same mistake twice (§5) |
| **D-T52-7** | Both source scans strip comments (§5) |

**Nothing in the frozen screen changed**: the state machine, the `parseAs: 'stream'` call, the single heading,
the focus/region split and the seven catalogue keys all shipped exactly as Phase 4 fixed them.

## 7 · Open items

**FU-T52-1** (the flake, above), **OQ-T52-3** (nothing links to the screen — a known demo-path hole),
**FU-T43-2** (the fourth copy of the transport helper), **OQ-T43-9**.

---

**Phase 6 complete. Next is Phase 7 — Self-Review and the mutation harness.**
