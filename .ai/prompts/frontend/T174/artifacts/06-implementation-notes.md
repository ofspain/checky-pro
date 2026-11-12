<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# frontend · T174 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` · **Task** T174 — Home screen |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |
| **Suite** | **1857 passed, 1 failed** — the documented environmental case only |
| **Named suite** | 24 cases, AC1–AC10 |

---

## What was built

| file | |
|---|---|
| `src/app/HomeScreen.tsx` | **new** — two catalogue strings and one focus effect |
| `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | **new** — 24 cases |
| `src/app/ui/Heading.tsx` | `tabIndex={-1}` inside; props type untouched |
| `src/app/routes.tsx` | the first element behind the gate |
| `src/i18n/catalogue.ts` | the `home` group |
| five instrument files | grown — see below |

The component is deliberately thin. Having somewhere to land is the deliverable; task 42 nests the shell
around it (L12), and every line that would have anticipated task 42 is a line task 42 would delete.

---

## The five instruments that fired, where the freeze named three

The freeze predicted edits to three files. Five failed. **The two it did not predict are the two worth
reading.**

### 1. `loginChallenge…`'s T27-D1 pin — DISCHARGED, and it proves the freeze deferred something it needn't have

For eleven tasks this case asserted that a completed sign-in *renders nothing, announces nothing and strands
focus on `<body>`*. It was a pin on an unmet obligation, and its failure message carried an instruction:

> *"If this failed because a home screen now renders: delete this pin and assert the real focus target."*

It now asserts the real focus target. What matters beyond the discharge is **what that case already was**: it
drives a real callback, with a real code and state, through a real token exchange, on the real router, and
lands on `/app/home`. T41 recorded **OQ-T41-2** — that the post-sign-in landing could only be proved by
composing two suites, deferred to task 42. **This task's own freeze deferred it a second time, as D8 /
OQ-T174-4, for the same stated reason.**

Both deferrals were wrong. The composition was never required, because the end-to-end case already existed
and had been failing-by-design the whole time, waiting for a screen. Nothing was mocked into place to make it
pass; the three new assertions read what the completed flow now does. **OQ-T174-4 is closed, and OQ-T41-2
with it.** The lesson is narrow and worth keeping: *a deferral is a claim about what cannot be measured yet,
and it deserves the same evidence as any other claim.* Neither deferral was checked against the suites.

### 2. `failedRenewal…`'s AC11 — grown to state the real claim

T33 pinned that **no** authenticated route carries an element, with a note that a screen landing there is
task 42's and *"this case is still what will stop it from landing unnoticed."* It did stop it. The
attribution was one task out — the home screen is 174's — but the instrument worked exactly as designed.

The edit that mattered is what **not** to do: loosening it to "zero or more" would have made it green forever
and measured nothing. It now names the exact set, `[HOME_PATH]`, so it fails again the next time a screen is
attached. "No authenticated route renders anything" was never the requirement; it was a stand-in for R22 that
held for free while the table was empty.

### 3–5. The three the freeze predicted
`routesResolveUnderAppPrefixOnly` (`SCREENS_ATTACHED_SO_FAR` → four, **attributed to task 16**, correcting
phase 3's F12), `publicRouteSetIsExactlyTheDeclaredSet` (**D5's measurement: it DID move** — the AC9 element
list and both its titles), and `screensRenderThroughDesignSystemPrimitivesOnly` (`RENDER_SITES` → six, the
size pin → six, the union → fourteen, and the catalogue key set).

---

## Two mistakes made in this phase, recorded as made

### I checked the blunting risk in one file and missed it in another

Phase 5 named the risk explicitly — *"`screensRender…:515` takes the first `[tabindex]`"* — and verified it
safe. It did not occur to me to ask **which other queries select on `[tabindex]`**, although a grep run
before the edit had already printed the answer:
`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:173` selects
`'button, a, input, select, textarea, [tabindex]'` and asserts there is exactly one interactive control. The
heading became the second. **The evidence was in front of me and I read it as a list rather than a question.**

The fix is a narrowing, not a concession: `[tabindex]` matched any value including `-1`, and `-1` means the
*opposite* of interactive — focusable by code, never by Tab. The selector now requires a non-negative value,
so a stray `tabindex="0"` on a div, which is what the clause actually guards against, still fails it.

### The instrument that grew had a hole in the clause doing the growing

`screensRender…` cross-checks with `for (const file of bearing)` — every file **the sweep found** must be in
a pinned list. The sweep finds files by lowercase JSX tag. `HomeScreen.tsx` renders entirely through
primitives, which is the suite's whole point, so it contains none, and **when it was added the sweep said
nothing.** A screen could be written perfectly and never enter `RENDER_SITES`; the instrument that exists to
pin render sites was structurally incapable of noticing a correct one. It measured the files least likely to
be missing.

Closed with a second derivation that never touches tags: every production file outside `app/ui/` that imports
the barrel is a render site, since the barrel is where primitives come from. Asserted in **both directions**,
with a control — an equality between two lists passes when both are empty, and T173 shipped exactly that
shape of bug once.

---

## Deviations from the frozen brief

| | deviation | why |
|---|---|---|
| **D-1** | **AC6's import list omits `react`.** Implemented as `react`, `react-i18next`, `./ui`. | The frozen AC was literally unachievable: the same brief mandates a focus effect, and `useEffect`/`useRef` come from nowhere else. The prohibition AC6 exists for — no `auth/`, no storage, no network — is unchanged, and is asserted separately, pattern by pattern, so the widening buys no silence. |
| **D-2** | **Two more instruments than the freeze listed** (`loginChallenge…`, `failedRenewal…`). | Both are "must admit new state", the permitted reason. Neither was loosened. |
| **D-3** | **`src/app/ui/Screen.tsx` modified**, though the freeze put all other `app/ui/` files off limits. | One comment, no code: it read *"THREE CONSUMERS: the sign-in, callback and signed-out screens"*, which this task made false. Leaving a knowingly false statement in a frozen file is worse than the deviation, but it is a deviation and is recorded as one. |
| **D-4** | **Two case titles in `publicRouteSetIsExactlyTheDeclaredSet` renamed** ("three screens" → "four"). | Both titles asserted the number in words. A false title is worse than a renamed one; no file cites either title (checked by grep across `src/` and the artifacts). |
| **D-5** | **F15's prediction was wrong, and the freeze recorded it as a prediction to be measured.** | It predicted no T41 test would break. Six failed. Five were real instrument growth; one was the environmental case. The freeze's instruction — *"the only honest form of it is a green run named in the notes"* — is why this is a line in a table rather than a surprise in phase 8. |

---

## Open questions after this phase

- **OQ-T174-4 — CLOSED.** The end-to-end case exists and now asserts the landing. **OQ-T41-2 closes with it.**
- **OQ-T174-3** — `phase1ScreensPassAccessibilityChecks` still commissioned by no task. Unclaimed.
- **OQ-T174-5** — AC8 is structural, not behavioural; no Tab keypress is measurable without a new dependency.
  Named inside the suite's own header so a reader cannot mistake the green for more than it is.
- **The environmental failure is unchanged**: `lintRejectsHandWrittenBackendFetch` AC4 expects a summary line
  this host's `oxlint@1.87.0` does not print. Verified it is that case and that message, not a new one. The
  build gate in the same file **passed**, so AC9's `tsc`-level enforcement has a real run behind it.
