<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). Findings only; fixes are applied in Phase 9. -->

# frontend · T47 · Phase 7 — Self-Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

Findings only — **nothing here was fixed.** Fixes are Phase 9's.

---

## The mutation harness — 17 rows, the first clean baseline in seventeen copies

```
baseline whole suite: 0 failed of 2010, exit 0
baseline failing cases (excluded from every attribution below): []

regressions: 13/14 killed
canaries:    ['TR16', 'TR17'] - a SURVIVING canary means the instrument is inert: ['TR16']
unfalsifiable: ['TR15']
survivors:   ['TR10', 'TR15', 'TR16']
broken:      none
disagreements: ['TR14']
```

**`KNOWN_ENVIRONMENTAL_FAILURES` is empty, and that is new.** All sixteen previous copies excused
`lintRejectsHandWrittenBackendFetch.test.ts`, whose assertion parsed an oxlint summary line the tool had
stopped printing. FU-T47-1 fixed it before this phase, so there is nothing excused — and therefore nothing
an excused file can hide. T173 recorded that a `KNOWN_ENVIRONMENTAL_FAILURES` keyed by FILE had concealed a
real kill in the repository's only end-to-end build gate; that hazard no longer has a file to live in.

### THE ROW THAT SETTLES THE PHASE 4 DECISION — TR11

`TR11` makes the screen read the response and navigate only on a 202 — the exact branch R2 and L4 forbid.
It was killed by **four** cases:

| Case | File |
|---|---|
| `renders the same success state for a 409 conflict…` | **a re-aimed AC11 row** in task 43's suite |
| `renders the same success state for a 500 with no body at all` | **a re-aimed AC11 row** in task 43's suite |
| `is identical even for a status the contract never declares (AC2, trap 1)` | this task's named suite |
| `reads neither the body nor the status, and sets no timer` | task 43's source scan |

**Two of the four kills are rows the alternative plan would have deleted.** Phase 3 presented removing them
as updating comments; had they gone, this mutant would have been caught by one behavioural case instead of
three. The owner's Phase 4 amendment — re-aim, never delete — is the reason the instrument survived the
change, and this is the measurement that says so rather than the argument that predicted it.

Worth noting precisely: the **202 row did not die**, and should not have. A mutant that navigates on 202
behaves identically for 202. Only the 409 and 500 rows discriminate, which means the `it.each`'s value is
concentrated in its two off-contract rows — the ones whose reason for existing is hardest to see.

### TR17 — phase 6's own decision, inverted and measured

`holdTransportInFlight` is phase 6's answer to 35 crashing cases. TR17 makes it answer immediately, which is
the decision undone. It killed **14 cases** across both registration suites. The decision is load-bearing;
had TR17 survived, those cases would not have been measuring the form at all.

### TR14 — the one disagreement, and it is in the safe direction

Predicted `inherited`, measured `KILLED/own`. Changing the path's spelling was expected to be caught only by
the declared-set pins that have guarded `PUBLIC_ROUTE_PATHS` since task 16. It is caught by those **and** by
this task's own suite, which imports the constant and asserts the landing pathname. The prediction was too
modest; nothing is wrong. Recorded because a disagreement in either direction is worth printing, and because
this project has twice had a kill count and its named failures disagree.

## Findings

Severity: **High** = a claim this task makes is not held, or the freeze was exceeded without ratification.
**Medium** = a real gap in what is measured. **Low** = readability, coupling, or a comment that is now false.

### F1 · Medium · `replace: true` is unmeasured
`features/account/RegisterScreen.tsx:96` — `navigate(\`/${REGISTER_SENT_PATH}\`, { replace: true })`.

The plan gave this a substantive reason: Back from the acknowledgement must not return to a filled form
whose resubmission posts the same address again, so pushing would turn the browser's Back button into a
retry control nobody designed. **No assertion anywhere holds it.** Measured: a grep for `historyAction`,
`'REPLACE'` and `replace: true` across every registration suite returns nothing.

The harness row TR10 predicts this survives, and it was written to predict it.

**Recommendation (Phase 9/10):** one case asserting `router.state.historyAction` is `'REPLACE'` after the
submit, or — stronger, because it measures the consequence rather than the flag — that navigating back
from the acknowledgement does not land on the form.

### F2 · Medium · the named suite never asserts that its two probes differ
`registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:46-50`.

Trap 1 says identical mocks make an identical-output claim vacuous, and the suite answers it by giving
`NEW_ADDRESS` and `ALREADY_REGISTERED` different `message` bodies. **Nothing checks that they are still
different.** Edit one to match the other and every identical-output case still passes, having measured that
the same input gives the same output. The defence is a convention in a comment, not an instrument.

Harness row TR16 is the canary for exactly this and predicts a survivor.

**Recommendation:** assert `NEW_ADDRESS.body !== ALREADY_REGISTERED.body` inside the AC2 case, so the probe
and the claim cannot drift apart.

### F3 · High · the freeze was exceeded, and it needs ratification
`features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx`.

The frozen brief's *Files NOT to Modify* names this file ("the T44/T45 suites — R1 is closed"). Thirteen of
its cases crashed once the screen began navigating, because each submits and then interrogates the password
field. They were fixed with one line per case. No R1 claim moved.

This is reported as High not because the change is risky but because **it is outside what a human approved**,
and the Phase 9 gate is where that is ratified or reversed.

### F4 · High · the blast radius was five files; the freeze named one
`publicRouteSetIsExactlyTheDeclaredSet`, `routesResolveUnderAppPrefixOnly`,
`unauthenticatedRoutesRedirectToSignIn`, plus the two registration suites.

Each is a membership or count pin that attaching a screen changes by construction. Three separate files
pin the same declared-union number as source text, which is worth recording on its own: a count can be
guarded in three places and still be invisible to a survey that greps for behaviour.

### F5 · Low · a comment in a touched file was already false, and this task makes it falser
`auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:287` — "Six of the eight have no element yet."

It was true when written. Task 43 attached `register`, making it five; nobody corrected it. This task
attaches `register/sent`, making it four. The case's own title ("renders eight of them") is still right,
because `PUBLIC_ROUTE_PATHS` still holds nine and `callback` is still excluded.

Recorded rather than fixed here: Phase 7 reports, Phase 9 applies. Flagged because this project has shipped
false comments past two reviews before (T42), and because a stale number in a passing case is exactly the
kind no assertion can catch.

### F6 · Low · AC9 is coupled to a react-router internal
`registrationAcknowledgement…test.tsx` — the storage case filters out the literal key
`'remix-router-transitions'`, which `restoreAppliedTransitions` writes inside `createMemoryRouter().initialize()`.

If react-router renames it, the filter stops excluding and the case fails. That is the *loud* direction and
therefore acceptable, but it is a test coupled to a library's private key name, and it should be read as
such rather than as a claim about the screen. The alternative — asserting a call count — is what the first
version did, and it was worse: it made the exception invisible.

### F7 · Low · the AC2 case unmounts by hand
The identical-markup cases tear down and re-mount inside the case body, duplicating `afterEach`. It is
needed (two full journeys in one case, and the second must start clean) but it is the least readable code in
the suite and the place a leak would hide.

### F8 · Low · `textContent` equality is whitespace-brittle
`expect(container.textContent).toBe(\`${en.registerSent.heading}${en.register.success}\`)` is also the
assertion that proves nothing ELSE renders, which is why it is written as equality rather than `toContain`.
The trade-off is deliberate: a future primitive that inserts a separator breaks it. Noted so the next reader
changes it knowingly rather than relaxing it to `toContain` and losing the only "and nothing else" claim.

### F9 · Low · the heading repeats the sentence's first four words
`i18n/catalogue.ts` — `registerSent.heading` is 'Check your email' and `register.success` begins with the
same four words. Deliberate, with the rationale written beside it: focus lands on the heading, so it is the
first and sometimes only thing a screen-reader user hears before acting, and the instruction has to be
complete inside it. **It remains a copy decision the owner may overrule**, so it is carried as **OQ-T47-7**
rather than presented as settled.

### Not findings, checked and sound

- **Every claim in `RegisterSentScreen`'s header is measured.** The source scan in the named suite forbids
  `useLocation`, `useSearchParams`, `useParams`, `sessionStorage`, `localStorage`, `fetch`, `.status`,
  `data`, `error`, `setTimeout`, `setInterval` and `props` — so "reads no router state, no storage, takes no
  props" is enforced rather than asserted in prose.
- **The public/no-redirect claim for the new path is inherited automatically.**
  `unauthenticatedRoutesRedirectToSignIn` iterates `PUBLIC_ROUTE_PATHS`, so `register/sent` joined both the
  matching and the no-redirect cases without an edit. This is what a membership-driven pin is for.
- **`PUBLIC_ROUTE_PATHS` really is unchanged (AC10).** Replacing the literal with a constant of the same
  value left every membership and order pin passing untouched, including two `toHaveLength(9)` assertions.
- **Module boundaries hold.** `features/account/` imports nothing from `auth/`; the new screen imports only
  React, i18next and the primitive barrel.
- **No money, no transactions, no concurrency, no secrets.** The screen has no inputs at all, which is the
  property AC8's cold-arrival case exists to pin.

---

## Severity summary

| ID | Severity | Issue | Confirmed by |
|---|---|---|---|
| F3 | High | the T45 suite was modified though the freeze forbade it | — (process) |
| F4 | High | blast radius was five files, not one | the run |
| F1 | Medium | `replace: true` is unmeasured | **TR10 survived** |
| F2 | Medium | the suite never asserts its two probes differ | **TR16 survived** |
| F5 | Low | a stale comment, already false before this task | read |
| F6 | Low | AC9 is coupled to a react-router internal key | read |
| F7 | Low | the AC2 case unmounts by hand | read |
| F8 | Low | `textContent` equality is whitespace-brittle | read |
| F9 | Low | the heading repeats the sentence's opening — OQ-T47-7 | read |

**The two Medium findings were each predicted by a harness row written to predict them, and each row
survived.** That is the harness doing its job: F1 and F2 are not suspicions, they are measurements.

## Open questions raised

| ID | Question |
|---|---|
| **OQ-T47-7** | Is the heading repeating the sentence's first four words acceptable copy? The rationale is accessibility; the call is the owner's. |

**Phase 7 complete.** Next is **Phase 8 — Independent Review (Kimi 2.7)**. STOP HERE.
