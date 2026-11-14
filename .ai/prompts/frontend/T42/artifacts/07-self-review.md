<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). Findings only; fixes are applied in Phase 9. -->

# frontend · T42 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` · `07-mutation-harness.py` · `07-mutation-run.txt` |
| **Harness** | twelfth copy · **19/19 regressions killed**, 1 canary killed, 1 unfalsifiable as predicted, **0 BROKEN** (second run) |
| **Suite** | 1880 cases, 1 failing — the documented environmental one |

---

## The harness

Twenty-one rows: nineteen regressions covering AC1–AC15, one canary (TE20) and one row predicted
unfalsifiable (TE21). Every row's full output is in `07-mutation-run.txt`.

**It ran twice, and the first run is the finding.** `c56e69fc` records it: *16/19 killed (3 BROKEN, not
counted as killed)*. Three rows mutated nothing measurable — and three criteria were therefore unmeasured
while the table underneath said two of them disagreed about attribution and the third was *as expected*.
F1 is that, in full. What follows describes the harness as it now stands.

**Three machinery changes, all caused by this copy's own rows:**

1. **A JSX mutant has to leave JSX behind.** TE02 and TE04 anchored on the *opening tag*, so `<div …>` was
   left closed by `</header>` and the inserted `<section>` was never closed. `Banner.tsx` stopped parsing,
   every file importing the barrel failed to **load**, and 530 of 1880 tests ran. Both now anchor the whole
   element — open tag, children and close tag — and both were replayed against `tsc` before the run.
2. **A cast cannot name an import that does not exist.** TE02's first replacement said
   `ref as React.Ref<HTMLDivElement>`; `Banner.tsx` imports `{ forwardRef, type ReactNode }` and no `React`
   namespace. Even with balanced tags that row would have been killed by the build gate in
   `lintRejectsHandWrittenBackendFetch.test.ts` — a file this task does not own — so the row would have
   reported `inherited` for a *type* error rather than `own` for a *missing landmark*. The mutant now grows
   the import by `type Ref` so the only thing it changes is the tag.
3. **A mutant on a list that feeds `it.each` must rename, not delete.** `PRIMITIVES` generates one case per
   primitive (`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:494`), so TE12's deletion deleted a
   **test**: the total fell to 1879 and the BROKEN guard — correctly watching for a drop — reported a load
   failure that had not happened. TE12 now renames `'Banner'` to `'Bannerr'`, which keeps the list's length,
   so all nine generated cases still run and the pin is measured by **what it names** rather than how long
   it is. Measured alone, the rename fails 9 of that file's 54 cases and loses none. In the whole-suite run
   it fails 12, and one of them is the end-to-end build gate — `'Bannerr'` leaves both
   `Record<(typeof PRIMITIVES)[number], …>` literals with an excess key, and the only case in the repository
   that runs a real `pnpm run build` is the only thing that can see a type error. Attribution by **case
   name** rather than by file is what let that kill be reported at all (T174's F3).

---

## Findings

### F1 — Three rows measured nothing, and the expectation table said one of them was as expected

**Severity:** Major (instrument) · **Evidence:** `c56e69fc:07-mutation-run.txt`, the table under
`broken: [TE02, TE04, TE12]`; `07-mutation-harness.py`, the `outcomes[...]` assignment in `main`.

The kill count was honest: *16/19 killed (3 BROKEN, not counted as killed)*. The table beneath it was not.
`score()` returns `(None, True, None, False)` for a BROKEN row, and that line read the tuple as "not a
survivor, not own" and printed **KILLED/inherited**. So:

- TE02 and TE04 were listed as disagreements — *"killed only OUTSIDE this task's files"* — a statement about
  attribution in a run where **1350 tests never ran**.
- **TE12 printed `expected inherited got KILLED/inherited as expected`.** The table agreed with a prediction
  about a run in which the case being predicted never executed.

This is the exact sin the guard inside `score()` exists to prevent — *a BROKEN mutant is not a kill* — one
level up, in the reporting layer that reads the guard's own return value. Eleven copies carried it; only this
one produced a BROKEN row to expose it. The table now prints `BROKEN` and flags it
*"DISAGREES - nothing ran, so this criterion is UNMEASURED"*, and the anchor-missed branch, which previously
recorded no outcome at all, records `BROKEN` too.

The three criteria that were unmeasured for the length of that run: **AC2's landmark half** (the chrome is a
`<header>`, not a `<div>`), **AC9's ancestry half** (the `banner` role survives only outside
`article`/`aside`/`main`/`nav`/`section`), and **AC7's primitive-list half** (the pinned list names `Banner`).
All three are measured in the second run, each by the case that carries the claim:
TE02 dies on *"AC9 is a `<header>` banner outside every landmark that would demote it"* among nine others,
TE04 dies on that same case and on *"gives each primitive exactly one element to render"* and nothing else —
three failures total, which is a mutant isolating the hole it records — and TE12 dies on *"AC7 names the
layout and the ninth primitive in the lists that pin them"*.

### F2 — The BROKEN heuristic cannot distinguish "nothing loaded" from "a case was deleted"

**Severity:** Major (process, carried forward) · **Evidence:** TE12's first outcome,
`1879 of 1880 ran → 1 tests never ran: a file did not LOAD`.

The heuristic is `total < base_total`, and it is the right signal — a file that cannot load contributes no
tests. But a mutant that shrinks a **parameterised list** removes generated cases, which produces the same
signal for a completely different reason, and the printed diagnosis was then false in its own words. One
test did not run; no file failed to load.

A refinement would be to compare the set of case names rather than the count, which would separate "cases
vanished" from "cases vanished *and* a whole file's worth went with them". I did not make it: `run()` already
collects names, but at baseline it collects only *failing* names, so the comparison does not exist yet and
building it is machinery work this task did not commission. **Recorded for the thirteenth copy**, with the
cheap mitigation already applied: a row that edits a list feeding `it.each` renames instead of deleting.

### F3 — `onClick={endSession}` is clean today and would break silently if `endSession` grew a parameter

**Severity:** Minor · **Evidence:** `src/app/AuthenticatedLayout.tsx:71`; `src/auth/endSession.ts:108`
(`export function endSession(): void`); `src/app/ui/ActionButton.tsx:18` (`onClick?: () => void`).

React calls a DOM `onClick` with a `MouseEvent`. `endSession()` declares no parameters, so the argument is
dropped and nothing happens — and `ActionButton` declares `onClick?: () => void`, which is what makes the
reference type-check. But a function with an **optional** first parameter is still assignable to `() => void`,
so if a later task changed the signature to `endSession(hint?: string)`, this call site would start handing
it a `MouseEvent` and `tsc` would say nothing. The hazard is latent, not live.

**Recommendation (phase 9):** `onClick={() => endSession()}` costs one closure and removes the class of bug
entirely. Against it: TE16 and TE17 both anchor on this line, and the wrapper makes the handover a line of
*this* file's logic rather than a bare reference — which is arguably worse for AC13, whose claim is that
there is exactly one hand-over and it goes through `endSession`. I lean to leaving it and recording the
hazard in `endSession.ts`'s own comment instead, as a note to whoever changes that signature.

### F4 — `Banner` and `Screen` share a content column by coincidence, and two pins agree by coincidence

**Severity:** Minor · **Evidence:** `src/app/ui/Banner.tsx:29` (`mx-auto … max-w-prose`); `src/app/ui/Screen.tsx`;
`REQUIRED_UTILITIES.Banner = ['mx-auto', 'max-w-prose']` in the Only suite.

The chrome lines up with the screen below it because both literals happen to contain the same two classes.
Nothing asserts they are the *same* column: change `Screen` to `max-w-4xl` and the banner stays at
`max-w-prose`, every case passes, and the sign-out button sits visibly off the content edge. T173's rule is
*pin the tokens a primitive owns, not its exact class string* — and each primitive does own its own width,
so neither pin is wrong. What is missing is a pin on the **relationship**.

**Recommendation (phase 10, not 9):** one case asserting that `Banner` and `Screen` carry the same column
classes. Not a shared constant — that would put a value in a module the design system has no home for, and
rule 1 keeps layout decisions inside each primitive.

### F5 — The own/inherited axis collides with itself again, and the expectations stay as written

**Severity:** Minor (reporting, not strength) · **Evidence:** the disagreements list.

All three disagreements in the second run — TE11, TE12, TE20 — revert or extend a list inside
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`, which this task **grew**, so it is in `OWN_TESTS`
and the kill reports `own` against a predicted `inherited`. T174 recorded the same collision for five rows
and the reason is unchanged: for a task whose work is largely growing other tasks' instruments, file-level
attribution cannot express *"task 174's logic reading task 42's data"*.

The predictions are not being re-labelled. *An expectation rewritten to match an outcome is how a harness
stops measuring anything.* The honest reading is printed in the run and repeated here.

### F6 — A latent order-dependent flake in T16's suite, sighted and not diagnosed

**Severity:** Major (test reliability) · **Evidence:** phase 6 notes, FU-T42-1;
`src/auth/signInStartsAuthorizationCodeFlowWithPkce.test.tsx`.

During phase 6 two cases in that file — *"re-arms the attempt"* (`assign` called 0 times) and *"writes exactly
the three items T15 writes"* (`setItem` called 6 times, not 3) — failed in one whole-suite run and passed in
the next on an identical tree. The file passes 31/31 in isolation. It has not recurred in either harness run.

This matters to the harness specifically: attribution is by **case name**, and the baseline-failing set is
captured from one run. A case that fails intermittently would be absent from the baseline and then counted as
a *new* failure — a kill credited to whichever mutant happened to be in the tree. No kill in this run is
suspect (every one names a case whose connection to its mutant is legible), but the mechanism is real.

**Recommendation:** carry FU-T42-1 as a sighting, not a diagnosis, and do not let phase 9 "fix" it blind.

### F7 — `siblingSignedOut` is a one-way latch, which is correct only because the redirect unmounts the layout

**Severity:** Informational · **Evidence:** `src/app/AuthenticatedLayout.tsx:53,66`.

Nothing resets the flag. That is safe today: `RedirectToSignIn` navigates away, the layout unmounts, and the
cleanup closes the channel. It would stop being safe the moment something re-mounted this layout without a
remount of the tree — a sign-in-in-place flow, say — because the latch would still be set. The comment at
`:38-44` explains *why* the flag exists and not *why it never clears*.

### F8 — Ten of the eleven authenticated paths now render a sign-out button over a blank document

**Severity:** Informational (intended, worth a reviewer's eye) · **Evidence:** `src/app/routes.tsx`, the
`AUTHENTICATED_ROUTE_PATHS.map` returning `{ path }` with no element.

This is what the task commissioned — the chrome belongs to the layer, not to each screen — and it is strictly
better than ten empty documents. It is also the first state in which a visitor can reach a page whose entire
content is a control that signs them out. Tasks 43 onward fill them. Naming it here so phase 8 can disagree
deliberately rather than discover it.

---

## What the harness does NOT measure

- **StrictMode.** No case mounts the layout under `{ strict: true }`, so the double-invoked effect — subscribe,
  clean up, subscribe — is unmeasured. One live channel is the expected outcome and `close()` is idempotent,
  but that is an argument, not a measurement. **Phase 10 gap.**
- **What a signed-in visitor sees on a public path.** The listener lives inside the authenticated layout, so a
  tab parked on `/sign-in` with a session does not react to a sibling's sign-out. No criterion claims it does;
  nothing forbids it either. **Phase 10 gap, and arguably an open question for the owner.**
- **The return target after a cross-tab sign-out.** D11 left the destination, the return target and
  replace-vs-push with `RedirectToSignIn`, and no case in this task asserts which of them applies on *this*
  path into it. **Phase 10 gap.**
- **A real second tab.** TE18 and TE19 drive `listenForSignOut`'s channel directly. jsdom has one
  `BroadcastChannel` realm; two browsing contexts are not available here and nothing claims otherwise.
- **Whether the chrome looks right.** AC7's utility pins hold tokens and the content column; no case renders
  pixels. Unchanged from T173, and F4 is the one relationship that pin cannot see.
