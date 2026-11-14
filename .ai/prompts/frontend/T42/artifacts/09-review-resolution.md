<!-- MODEL: Human Approval gate — Phase 9 (Review Resolution). -->

# frontend · T42 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/08-independent-review.md` (18 findings) · `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` · `07-mutation-run.txt` re-run at 22 rows |
| **Suite** | **1880 passed, 1 failed** (environmental) — 1880 → 1881 cases |
| **Harness** | twelfth copy, 22 rows · **20/20 regressions killed**, canary killed, TE21 unfalsifiable as predicted, **0 BROKEN** |

Every citation was checked against source before disposition. **Sixteen of the eighteen are accurate.
Two — both filed Major — do not survive their own citation**, and the paragraph they quote is the one
that refutes them.

---

## The two that do not survive, and the real drift behind them

**I2** and **I3** say the layout violates the brief's module boundary by importing from `auth/` and `api/`.
The sentence they quote — *"must not import from `auth/`"* — appears in the brief **inside the bullet that
supersedes it**:

> **Module boundaries (amended — owner, OQ-T42-2):** … and **exactly two auth modules** … *The
> pre-amendment line said "must not import from `auth/`"; that becomes two named exceptions and nothing
> else, enforced by the layout's own source sweep.* — `02-task-implementation-brief.md:175-181`

So the finding quotes the brief's own epitaph for the constraint as though it were the constraint. **I1**
compounds it with *"ensure `04-freeze-task.md` (if it exists) reflects the resolved OQ-T42-2"* — it exists,
it is 18 KB of D1–D11, and **D9 is titled *"The layout's import list is three auth modules and the scan says
so"***, with a table giving each module and its reason. The record-keeping I1 and I2 ask for was done at the
approval gate, before the code was written.

**And yet there is a drift here — a different one.** The amended line says **two** auth modules and *"must
not call `signOut()` directly"*. D9 and D11 raised that to **three**, `signOut` among them, because
`api/crossTabSignOut.ts:130-133` prescribes that a receiving tab must sign out **without** broadcasting:
*"Signing out here must NOT broadcast, or two tabs would ping-pong"*. So the contradiction is **brief vs
freeze**, not brief vs source — and a freeze superseding a brief is the ordering doing its job, which is why
the brief is left exactly as frozen. What enforces the boundary is neither document: it is the AC13 import
allowlist at `unauthenticatedRoutesRedirectToSignIn.test.tsx:769-772`, which fails on a ninth import, and
its paired control, which fails if any forbidden pattern stops matching its own probe.

**This is the pattern from T174's F12 and T39's premise, a third time: the finding was wrong as put, and
checking it was still the most valuable thing in the gate.** Had I accepted I2 and "reconciled" the brief, I
would have edited a frozen artifact to match the code — which is the one direction this pipeline never goes.

---

## Disposition

| | finding | disposition |
|---|---|---|
| **I1** | OQ-T42-2 resolved here, contrary to phase 3's recommendation | **CONFIRMATION, recommendation already satisfied.** The owner closed it 2026-11-14; the brief carries twelve `(owner, OQ-T42-2)` marks and `04-freeze-task.md` carries D1–D11. No action. |
| **I2** | layout violates the brief's module boundary | **DECLINED — wrong as cited.** Quotes the superseded sentence from inside the bullet that supersedes it. Real drift recorded above. |
| **I3** | `api/` was not in the allowed set either | **DECLINED — same citation, same answer.** `listenForSignOut` is named in the amended bullet and in D9. |
| **I4** | `onClick={endSession}` is a latent parameter-mismatch hazard | **ACCEPTED — code.** See below. |
| **I5** | no StrictMode case for the layout effect | **ACCEPTED — phase 10.** Already named as a gap in self-review F8's list. Case shape below. |
| **I6** | no focus-ring assertion for the sign-out control | **PART-ACCEPTED.** The ring is already measured twice; the computed-style recommendation is not measurable here. The one measurable half goes to phase 10. See below. |
| **I7** | `Banner` has no accessible name | **ACCEPTED — documentation half.** `Banner.tsx` now records that the omission is deliberate and what would change it. Declined the `aria-label` / app name. |
| **I8** | the AC7 case mixes two concerns | **ACCEPTED — tests.** The OQ-T36-5 pin now has its own case, named after the question. |
| **I9** | `Banner`/`Screen` column alignment coincidental | **ACCEPTED — documented now, pinned in phase 10.** Option 1 (test-only guard), not the shared constant. |
| **I10** | the one-way latch is fragile | **ACCEPTED — documentation.** The comment now says why nothing clears it. |
| **I11** | ten paths render only a sign-out button | **CONFIRMATION** of self-review F8. Owner note, no action. |
| **I12** | the BROKEN reporting fix matters | **CONFIRMATION** of self-review F1. |
| **I13** | the three disagreements are definition collisions | **CONFIRMATION** of self-review F5. Expectations unchanged. |
| **I14** | the BROKEN heuristic's blind spot | **CONFIRMATION** of self-review F2. Carried to the thirteenth copy. |
| **I15** | the focus guard is in the task that created the risk | **CONFIRMATION.** Citations verified: `homeRoute…:422-454`, `unauthenticated…:690-701`. |
| **I16** | the pathless nesting keeps the screen-count pins honest | **CONFIRMATION.** `SCREENS_ATTACHED_SO_FAR` is untouched at `routesResolveUnderAppPrefixOnly.test.tsx:18`. |
| **I17** | `Banner`'s contract is well documented | **CONFIRMATION.** |
| **I18** | the whole-suite count matches | **CONFIRMATION.** Independently reproduced: 1880 of 1881 after this phase. |

---

## I4 — accepted, and the fix had to come with its own instrument

`src/app/AuthenticatedLayout.tsx:83` is now `onClick={() => endSession()}`.

The hazard both reviews found is real and **invisible to every behavioural instrument in the repository**.
`endSession(): void` drops the `MouseEvent` React hands a bare reference, so today nothing differs. `tsc`
cannot object either, now or later: a function with an **optional** first parameter is still assignable to
`() => void`, so `endSession(hint?: string)` would start receiving a click event with the type-checker
silent. A spy cannot see it because the behavioural AC13 case deliberately does **not** mock `endSession` —
it asserts on the real navigation.

So the guard is a **shape** assertion, in the AC13-by-source case, and it arrived with the row that proves it
is awake:

- `unauthenticatedRoutesRedirectToSignIn.test.tsx` — `.toMatch(/onClick=\{\(\) => endSession\(\)\}/)`, with
  its reasoning in the case, including why it is not in the behavioural case next door.
- **TE22** — the handler reverted to the bare reference. **Killed, with exactly one new failure in the whole
  suite**: *"AC13 reaches for none of the pieces endSession composes, by source"*. A mutant that isolates the
  hole it records, and a fix that is not a comment.
- Every other row that replaces that line — TE09, TE13, TE16, TE17 — moved onto the closure form, so each
  changes only the thing it names. TE16 is now `() => signOut()` against `() => endSession()`: one
  identifier apart.

Kimi's alternative — *"split the assertion into 'calls `endSession`' and 'calls nothing else'"* — was not
needed: AC13 already has both halves, behavioural and source, and the shape pin is a third thing neither of
them could see.

## I6 — part-accepted, and the recommendation as written would measure nothing

The ring is already measured **twice**, in the primitive layer where it belongs:

- `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:502-508` — `it.each(FOCUSABLE)` over
  `ActionButton`, `TextLink`, `FocusHolder`, asserting `focus-visible:outline-2` **and**
  `focus-visible:outline-focus`, the second because without the colour the ring is the browser default.
- the same suite's built-CSS case (`:881-886`) — the two classes must survive the real Tailwind build, which
  is the only place a ring can be lost between source and bundle.

**The recommended computed-style assertion cannot work here.** jsdom loads no stylesheet, so
`getComputedStyle(button).outline` is the empty string for every element in every case in this repository; an
assertion that it is non-zero would fail, and one that it is "set" would be vacuous. T173 made the same call
and wrote the class pin instead.

What the finding *does* identify that nothing measures is the **layout's** half: a wrapper that clips the
ring. `Banner` carries no `overflow-`, `clip-` or `contain-` utility today and nothing says it may not grow
one. That is measurable by source — **phase 10**.

## Carried to phase 10 (test generation), with shapes

1. **I5 — StrictMode.** `arrive()` already takes `{ strict: true }`. Mount the layout under it, post a
   sibling sign-out, assert the tab redirects **exactly once** and that `listenForSignOut`'s channel count
   ends at zero. Subscribe → cleanup → subscribe is the expected sequence; today that is an argument, not a
   measurement.
2. **I6's measurable half.** `Banner`'s class literal declares no `overflow-`, `clip-` or `contain-`
   utility, so the focus ring the primitive layer pins cannot be clipped by the chrome around it.
3. **I9 — the pair.** One case reading both primitive sources and asserting they carry the same column
   utilities. Not a shared constant: that is a design-system change needing the owner, and rule 1 keeps
   layout decisions inside each primitive.
4. The two gaps self-review F8 named that no finding touched: **what a signed-in visitor on a public path
   sees** when a sibling signs out (the listener lives inside the authenticated layout, so: nothing), and
   **which return target applies** on this path into `RedirectToSignIn`.

## Owner notes

- **I11** — ten of the eleven authenticated paths now show a sign-out button over a blank document until
  their owning tasks run. Intended (the chrome belongs to the layer), strictly better than ten empty
  documents, and the first state in which a visitor can reach a page whose only content signs them out.
- **I7** — if the chrome should carry the application's name, that is copy, a catalogue key and a decision
  D1 deliberately did not make. The task that adds navigation to the banner is the place for it.
- **The brief/freeze drift above.** Nothing to do in code. If the convention should be that a post-freeze
  brief gets an amendment note pointing at the superseding decision, that is a `.ai/` process change and
  belongs with the owner, not with this task. What makes it worth raising at all is that the brief's own
  amendment note — the sentence that quotes and retires the old constraint — is what a reader skimming for
  constraints picked up and filed as Major, twice in one review.
