<!-- MODEL: Human Approval — Phase 9 (Review Resolution). PARTIAL: Phase 8 has not run. -->

# frontend · T47 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/07-self-review.md` · **`artifacts/08-independent-review.md` DOES NOT YET EXIST** |
| **Produces** | `artifacts/09-review-resolution.md`, `09-mutation-run.txt` |

**STATUS: PARTIAL.** This phase formally consumes Phase 8, the independent review, which is Kimi 2.7's and
has not run. What is resolved here is **Phase 7's findings only**, on the owner's decision of 2026-11-27:
ratify both deviations, fix TR10 and TR16. **This gate reopens when Kimi's Phase 8 lands**, and the log
below is extended rather than replaced. Nothing in the pipeline advances past Phase 8 on the strength of
this document.

Recorded this way because the alternative — writing a complete-looking Phase 9 with a `Consumes` line
pointing at a file that does not exist — is the kind of false claim this task has spent three phases
correcting in other people's comments.

---

## The run after the fixes

```
baseline whole suite: 0 failed of 2011, exit 0
baseline failing cases (excluded from every attribution below): []

regressions: 14/14 killed
canaries:    ['TR16', 'TR17'] - a SURVIVING canary means the instrument is inert: none survived
unfalsifiable: ['TR15']
survivors:   ['TR15']
broken:      none
disagreements: ['TR14']
```

**14/14, up from 13/14, and the only survivor is the row written to survive.** `TR15` (useEffect →
useLayoutEffect) has no observable effect and a kill there would mean a case had started pinning the hook
rather than the behaviour. Both canaries now die. `TR14` disagrees in the same safe direction as in Phase 7
— predicted `inherited`, killed `own` — because this task's own suite imports the path constant.

## Resolution log

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

## State of the task

| | |
|---|---|
| Suite | **2011 / 2011** |
| `lint` / `typecheck` | 0 / 0 |
| Harness | **14/14 regressions**, 2/2 canaries dead, 1 expected survivor, 0 broken |
| Phase 7 findings | F1 fixed, F2 fixed, F3+F4 ratified, F5–F9 recorded |
| Open for the owner | **OQ-T47-4** (sign-in link?), **OQ-T47-7** (the heading's repeated opening), **F5** (a one-number comment fix, recommended) |

**Phase 9 is PARTIAL and the pipeline is parked at Phase 8 — Independent Review (Kimi 2.7).**
