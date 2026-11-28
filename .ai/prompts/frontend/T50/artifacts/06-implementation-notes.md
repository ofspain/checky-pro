<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T50 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

```
2113 / 2113  (41 files)  ·  lint 0  ·  typecheck 0  ·  named suite 27 cases
every touched file also green when run ALONE
```

---

## What changed

**Created (2)** — `features/account/ResendVerificationScreen.tsx` (+104) ·
`features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` (27 cases).

**Modified (10)** — `i18n/catalogue.ts` (seven flat keys) · `app/routes.tsx` (import + element on the existing
constant) · and **eight pins**, listed under *Blast radius* below.

The screen is the frozen design with one measured correction, and `submit()` is three lines:

```tsx
function submit(): void {
  setStatus('submitting');
  void createPublicAuthApi()
    .POST('/accounts/resend-verification', { body: { email } })
    .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
}
```

## Mapping to the frozen ACs

| AC | Evidence |
|---|---|
| **AC1, AC8** | The form renders on the already-declared path; a case asserts `PUBLIC_ROUTE_PATHS` contains it **before** rendering, and another asserts the pathname is **unchanged** after acknowledgement — if anyone adds a `/sent` sibling, that fails before the ADR is missed. |
| **AC2, AC3, AC11** | Nothing is signed in anywhere in the suite; the route is asserted to be a **direct child of the root** from the table itself, because a behavioural case cannot tell a public route from a gate that let it through. |
| **AC4, AC5, AC6** | One call, `POST`, body compared by **equality** to `{email}`. AC5 has its own case that **seeds the storage a session uses** and still demands a bare request — every other case would pass with the authenticated factory. |
| **AC7** | Acknowledgement on resolve, form removed (`form`, `input`, `button` all asserted gone), nothing shown in flight, and acknowledgement on a `500` too. |
| **AC9** | Seven literal strings, no raw key in the DOM, a transport that replies `{"message":"SERVER SUPPLIED SENTENCE"}` and a `toBe(ACK_TEXT)` equality proving it is not rendered, plus the source scan. |
| **AC10** | Region mounted **empty**, filled while submitting; focus moves to the heading on acknowledgement; **focus deliberately untouched on arrival**, asserted separately so the first case cannot pass for the wrong reason; implicit submit via the form's own event; the field's label is matched by `for`/`id`. |
| **AC12**, **D-T50-2** | No lowercase tag, no `className`/`style`, and **one `h1` in both states**. |
| **AC13** | T49's handoff **replaced** — see below. |
| **AC14** | Eight pins updated. |
| **AC16** | The `unsent` line with the form still mounted, the acknowledgement asserted **absent**, and a second case that submits again mid-flight to prove the line clears. |

## Deviations

### D-T50-2 — one `Heading`, not two (**forced by measurement**)

Ratified in the freeze on the strength of the measurement, and the case that holds it says why it exists. The
phase 3 structure had a second `Heading` under the screen's own in the acknowledged branch; `Heading` renders
an `<h1>`, **all seven pre-existing screens render exactly one**, and the design-system suite pins `h1` to
length 1 for three of them with the note *"the document outline L11 requires has one top level"*. Changing the
heading's words instead also **improves** OQ-T50-4's mechanism: focus lands on a heading whose text is now the
acknowledgement.

### The named suite was written in Phase 6, not Phase 10

Flagged in advance in the plan; unchanged since T43.

## Blast radius — **ten files, not seven, and the three extra are second-order**

Phase 2's probe measured what **attaching** the screen breaks. Three further pins fire from what **repairing**
it breaks:

| | |
|---|---|
| `homeRouteRendersLandingScreenAndManagesFocus` (T174) | reads the design-system suite's **source** and asserts the union says `23` |
| `unauthenticatedRoutesRedirectToSignIn` (T42) | the same, for `toHaveLength(10)` **and** the union |
| `registrationAcceptsTwelveAndOneTwentyEightCodePoints` (T43) | the same three numbers again |

**None of them could fire during the probe**, because those numbers move only once the new file is added to
`RENDER_SITES` — which a probe of the *change* never does. **A probe measures the blast radius of the change,
not of the fix.** The lesson is not that the probe was wrong; it is that a probe of a production change cannot
see the pins that a pin-update trips, and the brief should say which it measured.

Four files now hold the union count. **It moved to 24 while the sweep still finds twelve** — the third time
that has happened and the second in a row, so it is the pattern rather than an exception: a screen composing
primitives only is a *declared* render site that bears no lowercase tag. The case name still says twelve and
that is **not stale** — the twelve is a statement about the **sweep**. A task bumping both together would
destroy the distinction those comments exist to protect.

## Three places the implementation corrected itself

1. **The storage case was wrong as first written.** It forbade *every* `getItem`, and failed. Measured rather
   than guessed: the single read is `remix-router-transitions`, **react-router's own** scroll bookkeeping.
   Loosening it to writes-only would have been the easy fix and would have **stopped measuring reads
   entirely**; instead the allowed key is named, so the day this screen reads a session value, it fails. The
   derived-list comparison then needed its own non-vacuity control, which it has.
2. **`RESEND_VERIFICATION_PATH` was used before it was imported** in `publicRouteSetIsExactlyTheDeclaredSet` —
   caught by `tsc`, not by a test, which is the gate the plan front-loaded it for.
3. **T49's handoff case is replaced by a stronger claim than "the form renders."** What matters about that
   link is not that its destination exists but that the destination is **what the link said it was**, so the
   case now pins `h1` text to `en.verify.failureAction`:

   ```ts
   expect(container.querySelector('h1')?.textContent).toBe(en.verify.failureAction);
   ```

   Two catalogue groups, two features, one sentence — held in neither screen, because **neither screen can
   see the other**. Change either wording alone and it fails.

## One flake, recorded as unidentified

The **first** of four whole-suite runs reported `1 failed | 2112 passed`; the three after it were
`2113/2113`. **Its name was not captured** — my own grep pattern dropped the detail block — so it is recorded
as *unidentified* rather than attributed to the known load-sensitive one (FU-T42-1), which would be a guess
dressed as a finding. The harness asserts its own baseline, so if it recurs during a mutation run it surfaces
as a disagreement; **and the rule is to read the NAMED failures, not the count.**

**Phase 6 complete. Next is Phase 7 — Self Review.**
