# frontend · T17 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5, as approving principal engineer |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Disposition of Kimi's phase 11 test review

**Six of eight accepted and applied** (commit `edeaf363`). Recorded here because phase 11 is a
review phase with no resolution phase of its own.

| # | Gap | Disposition | Mutant |
|---|---|---|---|
| 1 | discard URL not asserted free of the callback query | **ACCEPTED** | M13 **killed** |
| 2 | trim untested on the way *through* | **ACCEPTED** | M12 **killed** |
| 3 | `error` + `code` together uncovered | **ACCEPTED**, behaviour locked as **D-12** | M11 **killed** |
| 4 | catalogue keys not asserted non-empty | **ACCEPTED** | M15 **killed** |
| 5 | no StrictMode accessibility case | **REJECTED** — decorative | — |
| 6 | storage boundary unpinned on the success path | **ACCEPTED** | M14 **killed** |
| 7 | no negative control for the indentation scan | **ACCEPTED** — the best of the eight | M16 **killed** |
| 8 | "no mutant removes `.catch(discard)`" | **REJECTED** — factually wrong | — |

**Gap 7 was the most valuable finding of either review.** Phase 10 recorded that the indentation scan is the
only thing in the suite that can catch the TDZ class, because Vitest's module resolution does not reproduce
Vite's failure. Kimi drew the correct conclusion that I had missed: a scan in that position with no negative
control is a *silent hole*, not merely a weak test. The scan is now extracted as `moduleScopeUses` and fed a
synthetic source containing a module-scope use, a commented use and an indented use.

**D-12 (new, locked).** A callback carrying **both** `error` and `code` is discarded. RFC 6749 §4.1.2/§4.1.2.1
has the server send one or the other, so a response carrying both did not come from this flow intact; it is an
injected or mangled link. Previously any non-empty `code` was exchanged regardless of an accompanying `error`.
Kimi left the choice open and asked only that it be locked; this is the conservative reading and it costs one
branch. `CallbackScreen.tsx:54-57`.

**Gap 5 rejected** because no mutant can kill it. React renders one tree under StrictMode regardless of effect
structure, so the case would assert a property of React rather than of this screen — decorative by the standard
this task has applied throughout, and the suite already asserts the element counts under a normal render.

**Gap 8 rejected as factually wrong.** M1 in `09-review-resolution.md:126` is precisely that mutant — the
`.catch` removed, **killed** at 1 failed / 55 of 56. Kimi consumed only the phase 10 manifest, which lists
M8–M10 and summarises M1–M7 as "remain killed" without restating them. Phase 11 also repeats the stale suite
figure (608/609) and, as in phase 8, misattributes the one environmental failure to `pnpm` on `PATH`; it is
AC4's absent oxlint summary line, and AC2 — the `pnpm` one — passes under this invocation.

---

## Traceability matrix

### Requirement

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R8** — "IF the callback `state` is missing **or does not match**, THEN the SPA SHALL discard the attempt, SHALL NOT complete the exchange, and SHALL return the visitor to the sign-in start with neutral copy" | **Half** — by design | `CallbackScreen.tsx:62-65` | yes | **the mismatch half is task 18's**, by `tasks.md` task 18; `CallbackScreen.tsx:67` marks the seam | none |
| R8 · "discard the attempt" | yes | `CallbackScreen.tsx:47-52` | 25 cases | no | none |
| R8 · "SHALL NOT complete the exchange" | yes | `codeExchange.ts:18-21` not called; `CallbackScreen.tsx:73` is the only call site | 12 cases incl. a positive control | no | none |
| R8 · "return the visitor to the sign-in start" | yes | `CallbackScreen.tsx:51` | pathname, `REPLACE`, empty search | no | none |
| R8 · "with neutral copy" | yes | `catalogue.ts` `signIn.discarded` | identical across 6 shapes; word-list guard | no | none |

**R8's "or does not match" is not a gap in T17.** `tasks.md` splits R8 across task 17 (missing) and task 18
(mismatched), and both verify the same named test `callbackWithMismatchedStateIsDiscarded`, which is why T18
extends this file rather than adding one. The split is the specification's, not mine.

### Locked decisions

| Decision | Honoured? | Evidence |
|---|---|---|
| **L4** — enumeration-safe copy, no carve-out | yes | copy identical across all 6 discard shapes, compared shape-to-shape; word list forbids cause/credential/account state, with a reach control; the server's own `error_description` is asserted never to render |
| **L9** — single origin | yes | no absolute address in any of the three new files (AC15 scan); verified in a real browser — `/login` is same-origin on `localhost:5173` |
| **L11** — accessibility, no default English 404 | yes | one `main`, one `h1`, one `role="status"` with `aria-live="polite"` on both screens (`CallbackScreen.tsx:77-80`); the discard lands on a declared route, never the catch-all |
| **L16** — the only sessionStorage carve-out | yes | T17 writes nothing and reads nothing; `setItem`/`removeItem`/`clear` spies on the discard path, `getItem` key assertions on the success path |
| **D-1, D-2** — the seam, `objectContaining` | yes | `codeExchange.ts:13-16`; every seam assertion uses `objectContaining` |
| **D-3** — notice isolation, single-read | yes | unconditional `takeSignInNotice()` in `afterEach`; drain asserted as a unit |
| **D-4** — missing `code` is a discard | yes | `CallbackScreen.tsx:62` |
| **D-5** — leading-slash target, measured | yes | `CallbackScreen.tsx:51`; exact-pathname assertion |
| **D-6** — the success path is a deliberate no-op until task 20 | yes | `codeExchange.ts:18-21`; observed in a real browser holding on "Checking your sign-in…" |
| **D-7** — a discard does **not** delete the stored items | yes | AC8 case; still present after a *failed exchange* too |
| **D-8** — the StrictMode latch is asserted | yes | 3 StrictMode cases |
| **D-9** — source scans on all three new files | yes | 16 cases, each pattern with its own control |
| **D-10** — `SignInNotice` stays a union | yes | `signInNotice.ts:13` |
| **D-11** — whole-suite regression obligation | yes | 634/635, measured at every phase |
| **D-12** — `error` + `code` is a discard | yes | `CallbackScreen.tsx:54-57` |

### Acceptance criteria

AC1–AC10, AC12, AC14–AC17 are asserted in `callbackWithMismatchedStateIsDiscarded.test.tsx` — **73 cases, all
passing**. AC11 (T01/T02/T13/T16 green) and AC13 (no new dependency, measured delta) are regression obligations
discharged by running the suite, recorded as such rather than faked with an assertion. `package.json` is
unchanged, so AC13's no-new-dependency half is structural.

---

## The four questions

**1 · Is the task fully complete?** Yes, for the half of R8 that `tasks.md` assigns to task 17. The missing-state
discard is implemented, the exchange is demonstrably not reached, the visitor is returned to the sign-in start
with neutral copy, and the mismatch half is marked at the exact line task 18 will fill.

**2 · Does it satisfy every acceptance criterion?** Yes. AC1–AC10 and AC12–AC17 are asserted; AC11 and AC13 are
discharged by the suite run. AC12 was uncovered at phase 7 and closed at phase 10. Every criterion that could be
satisfied vacuously has a control: the seam has a positive case, each source-scan pattern has its own violation,
the copy word list is proved to have reach, the identical-copy assertion compares shapes to each other rather
than to the catalogue, and the indentation scan is fed a module-scope use.

**3 · Does it violate any LOCKED decision?** No. All of L4, L9, L11, L16 and D-1 through D-12 are honoured, with
evidence above. One decision was *extended* — D-12 — and it is recorded rather than silent.

**4 · Remaining risks.**

- **The seam is empty, so a deployed T17 cannot complete a sign-in.** This is D-6, deliberate and documented. A
  callback with a valid state and code holds on "Checking your sign-in…" until task 20 lands. Verified in a real
  browser so it is a known state rather than a surprise. **Mitigated by being stated; it is the owner's to
  sequence.**
- **`.catch(discard)` is this task's guess at task 20's failure handling.** It is strictly better than dropping
  the rejection, and a comment says task 20 may route it somewhere more specific. Low.
- **One environmental suite failure persists** — `lintRejectsHandWrittenBackendFetch` AC4, this host's
  `oxlint@1.87.0` printing no summary line. Pre-existing, unrelated, and not T17's to fix. It does mean the
  suite is never literally green on this machine, which has now caused two review phases to misreport which
  case fails. Worth a host fix at some point; **gates nothing**.
- **The copy is unreviewed (OQ-3).** Properties enforced by test, wording the owner's, in one file. Low.
- **Task 18 inherits a file of 606 lines and 73 cases.** Large, but organised in 15 named blocks and explicitly
  designed for T18 to extend. Low.

---

## Verdict

**PASS** — R8's missing-state half is implemented, asserted by 73 non-vacuous cases with 16 mutants killed across
phases 9–11, verified end to end in a real browser against the live stack, and violates no locked decision.

---

## Open questions

**OQ-3** — the callback copy is unreviewed, the owner's to reword in one file. **Gates nothing.**

**OQ-5** — T17's generated prompt header lists `stateSurvivesRedirectAndIsConsumedOnce`, which is task 23's by
name; not implemented here because D-7 locked deletion as task 23's and AC8 pins the opposite. **Gates nothing**,
but `.ai/generate.py` will reproduce the same inherited-test noise in other task headers.

**Carried, gating nothing ·** T16's OQ-1 (the five sign-in strings); T12's OQ-1 (a refresh cannot promote a
waiting worker); `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its claim that auth T19
is "not built" while L5 records it as built; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to
R57, which does not describe it; `segmentPrefixPattern` duplicated between `navigationDenylist.ts` and
`apiRequestPolicy.ts`; `apiRequestPolicy.ts:46` listing `/app` though it 404s.

---

## Next

Phase 13 — PR preparation.
