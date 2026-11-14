<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution). Fixes applied. -->

# frontend · T43 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/07-self-review.md` (F1–F8) · `artifacts/08-independent-review.md` (I1–I17) |
| **Produces** | `artifacts/09-review-resolution.md` |

Seventeen findings from Kimi, eight from the self-review. **Every citation was checked against source before
it was accepted or declined** — the standing rule, and it mattered twice here: once to confirm a finding the
plan itself had predicted (I1), and once to decline one whose premise does not hold in this repository (I4).

**State after the fixes:** suite **1932 passed, 1 failed of 1933** (the documented host `oxlint` case),
`tsc` exit 0, `oxlint src` exit 0. Two cases added, 1931 → 1933.

---

## The finding that mattered most: I1 was not a hole, it was a line the plan told me to write

`08-independent-review.md` I1: *"`RegisterScreen` is not added to T174's 'no OTHER screen takes focus'
guard … despite the brief listing this file under Files to Modify for T43."*

**Confirmed, and worse than Kimi put it.** `05-implementation-plan.md`'s Files-to-modify row reads:
`homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | *"the I9 guard loop → **four screens** (D5); the
union pin → 21"*. Phase 6 did the union pin and **not** the loop — one row, two halves, one done. Then
phase 7's harness reported TR21 (*"the screen focuses the first field on mount"*) as a **survivor** and my
own artifact recorded it as *"a hole phase 10 closes"*.

It was not a hole. **The instrument existed, the plan named it, and the line was simply unwritten** — and
the harness's survivor was the evidence, misread as a gap in the suite rather than a gap in the work. This
is T174's lesson with the sign reversed: there, *a gap recorded was not a gap closed*; here, **a gap
recorded was not even a gap — it was an omission wearing a gap's clothes.** Kimi found it by reading the
plan against the diff, which no amount of running tests would have done.

The loop is now four screens, and `RegisterScreen` is the first entry in it **with interactive controls** —
which is what makes the guard matter more than it did for three read-only screens: an autofocused email
field is the single most likely well-meant edit to this screen.

## The control that failed on its first run: F4 / I9

Both reviews said AC10's storage case could be vacuous because a spy that fails to intercept reports
exactly what a journey that writes nothing reports. The control was added — and **it failed**.

Measured with a throwaway probe: `vi.spyOn(globalThis.localStorage, 'setItem')` saw **0 of 1** writes;
`vi.spyOn(Storage.prototype, 'setItem')` saw **2 of 2**, and `mock.instances` tells the two storages apart.
**An instance spy on a jsdom `Storage` does not intercept at all.** So the assertion *"the registration
journey wrote to localStorage"* was asserting that a spy which never fires had not fired.

Two cases were corrected, not one:

| | |
|---|---|
| `registrationAccepts…test.tsx` | one `Storage.prototype` spy, attribution by `mock.instances`, and a control that writes through **both** storages and proves the spy sees each |
| `unauthenticatedRoutesRedirectToSignIn.test.tsx:918` (**T42's AC11**) | the same inert instance spy, **vacuous from the day it was written**. Its `fetch` half was always real. Corrected with its own control |

Editing T42's file is a deviation (**D-10** below). It is taken rather than deferred because the claim was
never true, and a false instrument is worse than a missing one: this one said *"the layout wrote storage on
render"* and could not have seen it.

---

## Accepted and fixed

| finding | fix |
|---|---|
| **F1 / I6** `app/ui/index.ts`'s header is false and rule 2's list is short | The form-field clause is **corrected, not deleted**: the conclusion survives and the reason narrowed — a text field is the one item on that list a headless library is not needed for, because its accessibility is `<label for>` + `id` rather than focus choreography. Rule 2 now names the five contracts this task set inside (`type="email"`, `type="password"`, `type="submit"`, the `htmlFor`/`id` pair, `noValidate`), says why a field renders two elements, and distinguishes a **contract** from a **state** a consumer computes (`aria-disabled`, `aria-invalid`) |
| **F2 / I7** the submit promise has no rejection handler | `.then(onFulfilled, onRejected)` → a rejected send sets `'idle'`. It is the one state that needs **no new word**: the live region empties, both fields stay filled, the control stays usable, and nothing claims an account was created. `'sent'` would lie; a failure **sentence** would be copy no owner approved, and D7 froze the key set at six. The silence is the remaining gap — **OQ-T43-9** |
| **F3 / I8** `SubmitButton` claims a pair pin that does not exist | The pin now exists: `REQUIRED_UTILITIES.SubmitButton` deep-equals `REQUIRED_UTILITIES.ActionButton`, **and** both files' class strings are compared on the nine shared utilities. A row both files satisfy is still two literals until the literals are compared |
| **F4 / I9** the storage spy has no control | Above. The control failed, the spy was wrong, two files fixed |
| **F7 / I11** the typing helper's optional setter | `expect(setter).toBeTypeOf('function')` before any case types |
| **F7 / I12** `Form`'s unverified jsdom claim | **Measured**: with `preventDefault()` removed, jsdom prints `Not implemented: HTMLFormElement's requestSubmit() method` — `requestSubmit`, not `submit`, because that is what a click on a submit control goes through. The sentence that was wrong was the next one: it said such a mistake would *"merely print in the suite"*. It does not — AC9 asserts `defaultPrevented`, so TR22 is killed. Comment corrected to the measurement |
| **I1** `RegisterScreen` missing from T174's focus guard | Above |

## Accepted, and owed to phase 10

| finding | why it waits |
|---|---|
| **F5 / I10** D16's empty-string clause is unmeasured | It is a **new case**, not a correction. Phase 10 |
| **I2** no case arrives at `/app/register` through the real route table | Confirmed: `routesResolveUnderAppPrefixOnly` uses `matchRoutes` (resolution, not rendering), and the named suite mounts the screen under a probe route. T41 and T42 both mount the real table. A new case — phase 10 |
| **TR09** (harness) copy hardcoded in English is indistinguishable from a lookup | A new source assertion — phase 10 |
| **F2 / I7**'s case | The fix is phase 9's; a **throwing-transport** case is phase 10's, and it is what retires TR26 |

## Declined, with reasons

### I4 — `createPublicAuthApi()` per submit · **declined**
Kimi's premise is *"inconsistent with `createAuthApi` being created once per tab"*. Checked: **nothing in
production constructs `createAuthApi` at all** — `auth/cleanReauth.ts:13-16` says so in its own words
(OQ-T33-2), and the one production client call in the repository, `auth/codeExchange.ts:114`, creates its
client **inside the function** exactly as this screen does. So the inconsistency is with a convention that
does not exist, and the precedent runs the other way. Moving construction to module scope would also move
it to **import** time for no measurable gain. Declined; the per-submit call is the established shape.

### I3 — focus the heading on arrival · **declined here, carried as OQ-T43-10**
D5 froze *no focus move on mount*, and Kimi itself says *"this is a design decision the owner should make"*.
It is not phase 9's to overturn a freeze. Recorded with the reason the asymmetry is defensible: `HomeScreen`
is an **arrival from a redirect** with no document load, so without a focus move a screen-reader user is
told nothing happened; `/app/register` is reached by a link or a typed URL, where the browser's own handling
applies. The guard I1 added now **enforces** D5 either way, so the question can be answered later without a
silent drift in between.

### I5 — a busy state on `SubmitButton` · **declined here, carried as OQ-T43-11**
It depends on **F6** (nothing prevents a second submit), which is an owner question and not a defect under
R1/R2. If F6 is taken, `SubmitButton` grows optional `aria-disabled`/`aria-busy` mirroring `ActionButton` —
never the `disabled` attribute, for the reason `ActionButton.tsx:5-11` gives.

### F6 — double submit · **carried to the owner**
Two clicks send two `POST /accounts`, so two verification emails. R2 forbids client-side *branching and
delay*, which an in-flight guard arguably is not; D1 and D7 are silent. Owner's call — **OQ-T43-11**.

### F8, I13–I17 — nothing to do
F8 (no `aria-invalid` on `EmailField`) was written to pre-empt a review finding and Kimi did not raise it.
I13–I17 are positive and confirm the two-element exception table, the length table's shape for task 44, the
response-agnostic AC11 rows, the corrected password-leakage claim, and the suite count.

---

## Deviations added in this phase

| | | why |
|---|---|---|
| **D-10** | **`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` modified** — T42's file, not in Files to Modify | Its AC11 storage assertion was **vacuous from the day it was written**, measured, not suspected. T42's own D-2 is the precedent for editing a closed task's instrument when it has become false; this one was never true |
| **D-11** | **A rejected send sets `'idle'`** | Not in the freeze, because the freeze did not consider the transport rejecting. The alternatives were a lie (`'sent'`) or unapproved copy (D7 froze six keys) |
| **D-12** | **`app/ui/index.ts`'s Radix paragraph rewritten rather than minimally patched** | The clause that was false was load-bearing for a future decision (*"the first screen that needs one … should start by reading this paragraph"*). Correcting the fact without correcting the **reason** would have left a reader with a true sentence and a wrong conclusion |
