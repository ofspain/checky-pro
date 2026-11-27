<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T47 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `09-mutation-run.txt` |

**STATUS: COMPLETE.** Signed off by the owner on 2026-11-27 in two parts — first the ratifications and the
two Medium fixes, then, once Kimi's Phase 8 arrived, Kimi's I9 and the self-review's F5.

An earlier version of this file was marked PARTIAL, because Phase 8 had not run and a `Consumes` line
pointing at a file that does not exist is the kind of false claim this task has spent several phases
correcting in other people's comments. Phase 8 has since landed and is resolved in full below.

---

## The run after every accepted change

```
baseline whole suite: 0 failed of 2011, exit 0
baseline failing cases (excluded from every attribution below): []

regressions: 14/14 killed
canaries:    ['TR16', 'TR17'] - a SURVIVING canary means the instrument is inert: none survived
survivors:   ['TR15']
broken:      none
disagreements: ['TR14']
```

**14/14, up from 13/14, and the only survivor is the row written to survive.** `TR15` (useEffect →
useLayoutEffect) has no observable effect, and a kill there would mean a case had started pinning the hook
rather than the behaviour. Both canaries die. `TR14` disagrees in the same safe direction as before —
predicted `inherited`, killed `own`, because this task's own suite imports the path constant.

## Kimi's Phase 8 — disposition

Kimi re-ran the suite, `tsc`, `oxlint` **and the mutation harness itself** on the Phase 7 tree, and its
numbers match Phase 7's row for row: 2010/2010, 13/14 killed, TR10 and TR16 surviving, TR14 disagreeing in
the safe direction, 0 broken. An independent reviewer reproducing a harness rather than reading its output
is the strongest form this gate has had.

| Finding | Severity | Disposition |
|---|---|---|
| **I1** `replace: true` is genuinely unmeasured | Medium | **ACCEPTED — already fixed.** Identical to F1. Kimi recommended "assert `historyAction === 'REPLACE'`, or stronger, that Back does not land on the form"; the implemented case does **both**, and each half was measured separately. |
| **I2** the identical-output case never asserts its probes differ | Medium | **ACCEPTED — already fixed.** Identical to F2. |
| **I3** the T45 suite was modified despite the freeze | High (process) | **RATIFIED by the owner.** Kimi offered an alternative — move the fixes elsewhere, or test the navigation another way. Rejected on inspection: the thirteen cases interrogate the *form*, so there is nowhere else for the fix to live, and a shared helper is FU-T43-2, which this task is scoped out of. |
| **I4** the blast radius spans five files | High (process) | **RATIFIED.** Kimi recommended updating the brief's *Files to Modify* or splitting the work so closed-task files are changed by their own maintenance tasks. The first half is done — Phase 6's notes and this log carry the real list. The second is a **process proposal beyond this task**, recorded as **FU-T47-2**. |
| **I5** the I9 guard uses a reasoned exclusion | Positive | Noted. |
| **I6** the acknowledgement ignores the server `message` | Positive | Noted. |
| **I7** the storage assertion is no longer vacuous | Positive | Noted, with F6's caveat that the react-router key name is a private dependency. |
| **I8** the `innerHTML` comparison is deliberate but brittle | Minor | **ACCEPTED as written — no change.** Kimi's own recommendation is to keep the equality and accept the brittleness as the price of the stronger "copy AND layout" claim. Agreed, and it matches F8. |
| **I9** the `type` helper differs from the registration suites' pattern | Minor | **ACCEPTED and FIXED, and it was worth more than "Minor"** — see below. |
| **I10** the AC11 no-props regex is literal | Minor | **ACCEPTED as written — no change.** Kimi notes it is the same shape the project uses elsewhere for export signatures, so the brittleness is not new. |

**One citation correction.** I9 cites `registrationAccepts…test.tsx:129-138` for the prototype-setter helper;
those lines are the end of `renderScreen` and the input accessors. The helper is at **154-164**. The finding
is substantively right and the line number is wrong — checked, because several Kimi findings in this project
have been accurate in substance and wrong in citation, and one was wrong in both.

### I9, and the hole underneath it

Kimi called this Minor and optional. It was neither, for a reason Kimi did not reach.

The finding is correct: the sibling suite writes through `Object.getOwnPropertyDescriptor(HTMLInputElement
.prototype, 'value')?.set` with the descriptor **asserted**, and its comment records why — its own phase 7
F7 and phase 8 I11, where `setter?.call` silently did nothing and *"the failure would have surfaced as an
empty request body three cases away from the cause."* This task assigned `input.value` directly, which works
here only because no case types twice into one field. So T47 re-introduced the weaker shape a closed task
had already paid to learn about.

**Then the fix was measured, and it was not enough.** With the setter call neutralised — a helper that types
nothing at all — **all eighteen cases still passed.** The transport is mocked, so an empty form still draws a
202, still navigates, and every identical-output, focus, storage and cold-arrival case stays green. The suite
asserted **no request body anywhere**, so the helper was entirely unmeasured; the asserted descriptor catches
a *missing* setter and never one that is simply never called.

Copying the sibling's helper without copying its body assertions would have left the same hole with
better-looking code in it. So the AC4 case now asserts that what was typed is what left the tab, and the
probe confirms it discriminates: a no-op helper fails with `{ email: '', password: '' }` — the exact
empty-request-body failure the sibling's comment describes.

This is the third time in this task that an instrument looked sound and measured nothing: T42's instance spy
(inherited as trap 2), trap 1's probes guarded only by a comment (F2/TR16/I2), and now this.

---


### D-T47-6 · RATIFIED · the T45 suite was modified although the freeze forbade it
`features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx`

The frozen brief's *Files NOT to Modify* named this file ("the T44/T45 suites — R1 is closed"). Thirteen of
its cases crashed once the screen began navigating. **Ratified by the owner at this gate.** No code change
follows from the ratification: the work was already done in Phase 6 and is now authorized rather than
pending. Every edit is one line per case plus the `holdTransportInFlight` helper, and no R1 claim moved —
the advisory rows, the bound interpolation, the code-point counting and the forbidden-shape scans are
untouched.

### D-T47-7 · RATIFIED · the blast radius was five files, not one
`publicRouteSetIsExactlyTheDeclaredSet`, `routesResolveUnderAppPrefixOnly`,
`unauthenticatedRoutesRedirectToSignIn`, plus the two registration suites. Each is a membership or count pin
that attaching a screen changes by construction. **Ratified.** No further change.

### F1 · ACCEPTED and FIXED · `replace: true` was unmeasured
**The change:** one case in the named suite, `REPLACES the form in history, so Back cannot resubmit the
address (AC3)`.

It asserts **both halves**, and the second is the one worth having:

```tsx
expect(router?.state.historyAction, 'the acknowledgement was PUSHED, so Back returns to the form')
  .toBe('REPLACE');

await act(async () => { await router?.navigate(-1); });

expect(container.querySelector('form'), 'Back reached the registration form, which could resubmit')
  .toBeNull();
```

A flag assertion alone pins today's spelling of the intent; the consequence assertion pins the intent. What
it forbids is a visitor pressing Back and landing on a form that would post the same address a second time —
**OQ-T43-11's shape reached through the browser's own control rather than a double click.**

**Measured, and each half separately.** `TR10` now reports `KILLED/own`, killed by exactly this case. But
the flag assertion fires FIRST, so on its own that run left the consequence assertion **unexecuted and
therefore unmeasured** — the trap this project has recorded repeatedly. So it was measured apart: with
`replace` dropped *and* the flag assertion neutralised, the consequence assertion fails on its own, with the
form in the failure message (`expected <form novalidate …> to be null`). Both halves discriminate.

### F2 · ACCEPTED and FIXED · the suite never asserted its two probes differ
**The change:** two lines in the AC2 byte-identical case.

```tsx
expect(ALREADY_REGISTERED.body, 'the two probe replies are identical, so "identical" measures nothing')
  .not.toBe(NEW_ADDRESS.body);
```

Trap 1 says identical mocks make an identical-output claim vacuous. The suite answered it by giving the two
replies different bodies — and nothing checked they stayed different. **`TR16` now reports `KILLED/own`**,
killed by that case. A canary that dies is the canary working.

### F3, F4 · RESOLVED BY RATIFICATION
These were the process findings that became D-T47-6 and D-T47-7 above. Nothing to apply.

### F5–F9 · RECORDED, NOT FIXED AT THIS GATE
The owner scoped this gate to TR10, TR16 and the two ratifications. The remaining findings stand as written
in Phase 7.

**F5 deserves one line of emphasis, because it is a false comment in a file this task modified.**
`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:287` says "Six of the eight have no element yet". It
was true when written, became five when task 43 attached `register`, and is four now. No assertion can catch
it, this project has shipped false comments past two reviews before (T42, found in phase 12), and the
correction is a single number. **Recommended for the remainder of this gate**, which has to reopen anyway
for Phase 8.

F6 (AC9 coupled to react-router's private key name), F7 (the AC2 case unmounts by hand), F8 (`textContent`
equality is whitespace-brittle) and F9 / **OQ-T47-7** (the heading repeats the sentence's opening) are
carried unchanged.

---

## Final state

| | |
|---|---|
| Suite | **2011 / 2011** |
| `lint` / `typecheck` | 0 / 0 |
| Harness | **14/14 regressions**, 2/2 canaries dead, 1 expected survivor, 0 broken, 1 safe-direction disagreement |
| Phase 7 | F1 ✅ F2 ✅ F3/F4 ratified · F5 ✅ · F6, F7, F8, F9 accepted as written |
| Phase 8 | I1 ✅ I2 ✅ I3/I4 ratified · I9 ✅ · I5, I6, I7 positive · I8, I10 accepted as written |

## Open items carried forward

| ID | Item |
|---|---|
| **OQ-T47-4** | Should the acknowledgement carry a sign-in link? Not needed for R2. |
| **OQ-T47-7** | The heading repeats the sentence's first four words. Deliberate — focus lands on the heading, so the instruction must be complete inside it — but a copy call the owner may overrule. |
| **FU-T47-2** | Kimi's I4 proposal: have closed-task count and membership pins changed by their own maintenance tasks rather than incidentally. A process change, beyond this task. |
| **FU-T43-2** | Still no shared test-util module. I9 is the fourth time a helper has been copied between suites, and the first time the copy carried a hole. Recorded a fifth time. |

**Phase 9 complete.** Next is **Phase 10 — Test Generation**.
