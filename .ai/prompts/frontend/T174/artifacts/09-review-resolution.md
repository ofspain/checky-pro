<!-- MODEL: Human Approval gate — Phase 9 (Review Resolution). -->

# frontend · T174 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Consumes** | `artifacts/08-independent-review.md` (15 findings) · `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` · `09-mutation-run.txt` |
| **Suite** | **1861 passed, 1 failed** (environmental) — 1858 → 1862 cases |
| **Harness** | **19/19 regressions killed**, canary killed, RE18 unfalsifiable as predicted, **0 BROKEN** |

Every citation in the review was checked against source before disposition. **All fifteen were accurate** —
the first phase-8 gate in this task's history with no false premise. I2 is Kimi correcting its own phase-3
error, which phase 6 had already caught independently.

---

## Disposition

| | finding | disposition |
|---|---|---|
| **I1** | focus target well argued | **CONFIRMATION.** Records the resolution of phase 3's F1/F2. No action. |
| **I2** | *"my phase-3 claim about `SCREENS_ATTACHED_SO_FAR` was wrong"* | **ACCEPTED.** Already corrected at `routesResolveUnderAppPrefixOnly.test.tsx:18-28`, where the comment names task 16. Verified. |
| **I3** | end-to-end proof lives in task 27's suite | **CONFIRMATION** of self-review F5. No action — see note below on why F5's own recommendation was declined. |
| **I4** | AC1 does not assert element order | **ACCEPTED — code.** See below. |
| **I5** | `import.meta.url` works here, contradicting T41 | **ACCEPTED, AND IT GOES FURTHER THAN THE FINDING.** See below. |
| **I6** | the five disagreements are definition collisions | **CONFIRMATION** of self-review F1. Expectations unchanged. |
| **I7** | RE18 correctly unfalsifiable | **CONFIRMATION** of self-review F2. |
| **I8** | AC8's behavioural claim unmeasured | **CONFIRMATION** of self-review F3 / OQ-T174-5. Carried, not closed. |
| **I9** | nothing stops other screens taking focus | **ACCEPTED — code.** See below. |
| **I10–I15** | route shape, imports, catalogue, `Heading` type, harness machinery, suite count | **CONFIRMATIONS.** All six verified; no action. |

---

## The three that needed code

### I4 — the order of the landmark's children was unmeasured

**Accurate.** AC1 asserted one `<main>`, one `<h1>`, and the heading inside the landmark. None of that
fails for `<Screen><Text/><Heading/></Screen>` — a screen that explains itself before it names itself.

The precedent is inherited rather than invented: `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:829` pins
*"one landmark holding a level-one heading, a paragraph and the way back, in that order"* for the signed-out
screen. The new case asserts `['H1', 'P']` as the landmark's children, and **RE20** — which swaps the two
lines — is what shows it is not decoration. RE20 also kills in `identityErrors…` and `sasRefusal…`, whose
success-text assertions concatenate heading and explanation, so the order has two independent witnesses.

### I9 — the hole this task opened, closed in this task's own file

**Accurate, and the most valuable finding in the gate.** Before T174, a stray `.focus()` added to any screen
was inert, because no `<h1>` was focusable. D1 made every heading focusable at once — so the same edit now
silently moves a screen-reader user's focus on mount, and nothing anywhere asserted where focus lands for
the other three screens.

This is T40's lesson recurring: *a task can open a hole and must close it.* Four cases now mount
`SignInScreen`, `SignedOutScreen` and `CallbackScreen` and assert `document.activeElement` is `<body>`, each
with a guard that the screen actually rendered a heading, so the assertion cannot pass over an empty mount.

**The guard lives in T174's suite, not as three edits to three closed tasks' files.** The risk is this
task's creation; the instrument belongs with it, and a future reader finds the reason beside the decision
that caused it.

**RE21 plants exactly that regression** — and needed three edits to do so honestly. `SignInScreen` imports
no `useEffect`, and its `<Heading>` carries no ref, so a one-line effect would either break `tsc` or call
`focus()` on `null` and survive for a reason unrelated to the guard. A mutant must measure its own
description. It is killed by the named case.

### I5 — a false claim, carried in source, measured out

The finding notes that this suite reads files with `fileURLToPath(import.meta.url)` while T41 recorded that
the pattern does not work, and suggests preferring `expect.getState().testPath` for consistency.

**Both patterns are correct, and the recorded reason for preferring one was false.** T41's phase 10 wrote
that *"`import.meta.url` is **not** a `file:` URL under vitest's jsdom environment, so `readFile` rejects
it"*, quoting the error *"The URL must be of scheme file"*. Probed directly under this exact config:

| | measured |
|---|---|
| `import.meta.url` | `file:///Users/…/<name>.test.ts` — **it is a `file:` URL** |
| `readFile(import.meta.url)` | `ENOENT: no such file or directory, open 'file:///Users/…'` |
| `readFile(fileURLToPath(import.meta.url))` | **OK** |

The real cause was passing a URL string where a **path** was expected; `readFile` read `file:///…`
literally. The quoted error message never occurs. That false reason had been carried forward as a comment in
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:69`, where it would have told the next reader that
a working pattern is broken. It is corrected in place, with the measurement, rather than deleted — a wrong
explanation that produced a working convention is worth more as a correction than as a silence.

This is the third claim committed to this codebase that measurement has overturned, after `routes.tsx:5`'s
module-scope TDZ claim and T29's premise about account-state words. **No code changed**: `testPath` works
and stays.

---

## Declined, with reasons

- **Self-review F5's own recommendation** — that the named suite assert the T27-D1 case exists by name, as
  AC9 names the build gate. Declined on inspection: AC9 names the build gate because that case enforces
  something *no assertion in this file can reach* (`tsc`). The T27-D1 case is different — it proves the
  arrival, which this suite does not claim, and the suite's header already names it in prose. An assertion
  that a string exists in another file would fail on a rename without telling the reader what was lost. The
  existing citation is in the right register.
- **I5's consistency suggestion** (switch to `testPath`). Declined: both work, and the file that would be
  changed is this task's. Changing it would propagate a convention whose stated reason is false.
- **I8's dependency suggestion.** Declined for this task — adding `@testing-library/user-event` is an
  owner's call, and **OQ-T174-5** is where it is recorded.

---

## State after this phase

- **1862 cases**, 1861 passing; the single failure is the documented `oxlint@1.87.0` summary-line case.
- **19/19 regressions killed**, 1 canary killed, RE18 unfalsifiable as predicted, 0 BROKEN.
- **Five disagreements unchanged and deliberately not re-labelled** (self-review F1, confirmed by I6).
- **Open:** OQ-T174-3 (`phase1ScreensPassAccessibilityChecks` unclaimed) · OQ-T174-5 (no real Tab keypress).
  OQ-T174-1, -2 and -4 are closed, and **OQ-T41-2 closed with -4**.
