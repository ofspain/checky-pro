# frontend · T18 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5, as approving principal engineer |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Disposition of Kimi's phase 11 test review

**Five of ten accepted, two rejected by measurement, three needed no action** (Kimi marked 9 and 10 as
requiring none, and 6 is a process note carried below). Recorded here because phase 11 has no resolution phase.

| # | Gap | Disposition |
|---|---|---|
| 1 | the ordering test could pass if `getItem` throws | **REJECTED — premise measured false** |
| 2 | the denial stub is global, not key-specific | **ACCEPTED** |
| 3 | no success case at the production token shape | **ACCEPTED** |
| 4 | the OQ-2 tripwire checks only the state's survival | **ACCEPTED** |
| 5 | no test for a literal percent-encoded state | **ACCEPTED — the substantive one** |
| 6 | the suite total is prose, not CI-enforced | **ACCEPTED as a note**, below |
| 7 | no runtime assertion that the read uses the right key | **REJECTED — premise measured false** |
| 8 | `arrivalWith`'s `code` parameter is unused | **ACCEPTED** |
| 9, 10 | — | no action, by Kimi's own assessment |

### Gap 5 — accepted, and the open question decided

Kimi raised the case and explicitly left the behaviour open: "the test should pin whether that is considered a
match or mismatch."

**It is a match, deliberately.** `URLSearchParams` decodes `state=xyz%20` to `'xyz '`, and T17 trims the
**supplied** value because a padded query is a client-side artefact (T17's F3). The **stored** value is still
never trimmed — that asymmetry is T18-D3. Three cases now pin both directions: a percent-encoded trailing space
in the URL still matches a clean stored value, and the same padding in the *stored* value does not.

Mutant: removing T17's trim fails **5** cases.

### Gaps 1 and 7 — rejected, both measured

**Gap 7** claims a regression reading `AUTHORIZE_STORAGE_KEYS.nonce` instead of `.state` would pass the source
scan undetected and compare the callback against the stored nonce. **That mutant fails 13 cases** — the
comparison-path case already asserts `state` is read *and* that `nonce` and `codeVerifier` are not.

**Gap 1** assumes that if `getItem` throws, the spy records no call, so a reordered read could hide behind the
ordering assertion. **A spy records a call even when the implementation throws** — measured directly. So
`expect(read).not.toContain(state)` would fail for a read moved above the error check, which is exactly what
the assertion is for.

### Gap 6 — the process note, with the guard that already exists

Fair, and carried. The defence in place is that **every phase asserts an exact total** rather than "one known
failure": 634/635 at T17, 688 at phase 7, 693 at phase 10, **696** now. A new failure changes the number.
Recommendation for whoever sets up CI: assert the expected total there, and confirm that
`lintRejectsHandWrittenBackendFetch` **AC4** fails identically — `oxlint`'s output may differ from this host's.
Note both of Kimi's T17 reviews and its T18 phase 8 misattributed that failure to `pnpm` on `PATH`, which is
**AC2** of the same file and passes under the `npx` invocation.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R8** — "IF the callback `state` is missing **or does not match**, THEN discard, do not complete the exchange, return the visitor to the sign-in start with neutral copy" | **yes, both halves now** | `CallbackScreen.tsx:67-70` (missing, T17), `:81-96` (mismatch, T18) | 134 cases | none | none |
| R8 · "does not match" | yes | `CallbackScreen.tsx:95` — `stored !== state` | 9 mismatch shapes × 3 assertions | no | none |
| R8 · "SHALL NOT complete the exchange" | yes | the seam at `:107` is unreached | spy asserts `[]` on every discard | no | none |
| R8 · "return … to the sign-in start" | yes | `discard()` at `:74-79` | pathname, `REPLACE`, empty search | no | none |
| R8 · "with neutral copy" | yes | `signIn.discarded`, reused unchanged | outcome compared **byte-for-byte** with T17's missing-state outcome | no | none |

**R8 is now fully implemented across tasks 17 and 18.** `tasks.md` splits it deliberately, and both tasks verify
the same named test, which is why T18 extended T17's file rather than adding one.

### Locked decisions

| Decision | Honoured? | Evidence |
|---|---|---|
| **L4** | yes | the mismatch outcome equals the missing-state outcome on text, pathname, search and history action; an authorization server's own `error_description` is asserted never to render; T18 added no string |
| **L9** | yes | no absolute address in the changed file; verified in a real browser on the single origin |
| **L11** | yes | one `main`, one `h1`, one live region on the landed screen, asserted on the mismatch path too |
| **L16** | yes | one key read, guarded; `nonce` and `code_verifier` asserted unread on every path; nothing written, removed or cleared |
| **T18-D1** | yes | inline in the existing effect; no new module |
| **T18-D2** | yes | `:95` — `stored === null \|\| stored === '' \|\| stored !== state`; no `==` anywhere, scanned |
| **T18-D3** | yes | `===` only; six-pattern source scan with controls; mutants N4, N5, N6 killed |
| **T18-D4** | yes | the read split by whether the comparison is reached, not by what was found |
| **T18-D5** | yes | no `typeof` guard; `setItem` coerces to `DOMString`, so none could fire |
| **T18-D6** | yes | AC5 over **two** unrelated matching values, after one proved insufficient |
| **T18-D7** | yes | the test writes the stored value; prefix/suffix use short literals; the production shape comes from the real generator, not a hardcoded 43-character string |
| **T18-D8** | yes | the corrected L4 timing wording |
| **T18-D9** | yes | the frozen replacement comment; a case asserts no task number remains |
| **T18-D10** | yes | the comparison landed before the assertions |
| **T18-D11** | **deviated — openly** | see below |
| T17's **D-7, D-10, D-12** | yes | items survive; the notice reuses `'discarded'`; the error check precedes the comparison, now asserted |

### The one deviation

**T18-D11 predicted "exactly one existing case is expected to change" and eight did.** All eight drive the
success path, which since T18 requires the URL and storage to agree — the task working correctly, not a defect
in it. The decision named the wrong count because it reasoned about which assertion would be edited rather than
which arrivals change meaning. Recorded in phase 6 when it happened rather than reconciled after the fact.

### Acceptance criteria

AC1–AC13 and AC15–AC17 are asserted in `callbackWithMismatchedStateIsDiscarded.test.tsx` — **134 cases, all
passing**. **AC14** (whole-suite regression) is discharged by running the suite.

---

## The four questions

**1 · Is the task fully complete?** Yes. R8's "does not match" half is implemented, and with T17 the requirement
is now covered end to end. The comparison is exact, reads the one stored item it needs, and leaves everything
else to the tasks that own it.

**2 · Does it satisfy every acceptance criterion?** Yes, with every criterion that could pass vacuously carrying
a control. The most important of those was wrong when phase 7 ended and is right now: AC5 could be satisfied by
a comparison against a constant every fixture shared, which survived 125 of 126 cases until phase 9.

**3 · Does it violate any LOCKED decision?** No. One of this task's own decisions, T18-D11, made a prediction
that proved wrong; that is a defective prediction, not a violated constraint, and it is recorded where it
happened.

**4 · Remaining risks.**

- **OQ-2 — read without consume.** Until task 23 deletes the items, the same callback URL compares equal every
  time. Defused in practice: an authorization code is single-use at the token endpoint, and until task 20 the
  exchange does nothing. **Now a failing-by-design tripwire** that task 23 inherits.
- **N2 and N3 are equivalent mutants.** The explicit `null`/`''` tests cannot be distinguished from the
  inequality by any test, given T17's guard. Kept deliberately, documented in the code, and the coupling they
  defend is covered by three cases that assert the property rather than the path.
- **One environmental suite failure persists** — see Gap 6 above. Gates nothing, but it should be confirmed on
  CI rather than inherited as folklore.
- **The copy is unreviewed (OQ-3).** T18 added no string. Low.
- **The suite file is 1,034 lines and 134 cases**, shared by two tasks and due to be touched again by task 23.
  Large, but organised in 21 named blocks. Low.

---

## Verdict

**PASS** — R8 is now implemented in full across tasks 17 and 18, asserted by 134 non-vacuous cases with 17
mutants killed across phases 6, 9, 10 and 11, demonstrated against the live stack with the same authorization
code and a one-character state change producing opposite outcomes, and violating no locked decision.

---

## Open questions

**OQ-2** (deletion is task 23's) · **OQ-3** (unreviewed copy) · **Gap 6** (CI-enforce the suite total and
confirm the environmental failure). All **gate nothing**.

**Carried, gating nothing ·** T16's OQ-1; T12's OQ-1; `design.md` §4c's stale heading and its claim that auth
T19 is "not built"; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to R57; T17's OQ-5, the
generated headers listing another task's named test.

---

## Next

Phase 13 — PR preparation.
