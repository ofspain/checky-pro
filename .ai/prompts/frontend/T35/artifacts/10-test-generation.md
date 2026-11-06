<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T35 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T35 — Sign-out clears memory |
| **Consumes** | `artifacts/04-freeze-task.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

No production code changed in this phase. The prompt's JUnit / Testcontainers / fixed-`Clock` conventions
belong to the Spring template it shares; this service tests with **Vitest 5.0.5 + jsdom**, and
`agents.md`'s testing rules are followed as the existing suites apply them.

## The audit, and what it found

Phase 10's job is to read the **frozen** criteria clause by clause rather than re-read the cases. Fourth task
running that this finds something, and this time one of the findings is that a criterion was **false**.

### The material one — AC11's third clause

> "T35 adds no route, no catalogue key, no network call, no new storage key, **and modifies no file but the
> two it creates and the one additive export.**"

**That is not what happened.** T35 modified four files outside the two it created:

| File | Why | Authorized by |
|---|---|---|
| `auth/returnTarget.ts` | the `clearReturnTarget` export | frozen brief, F3 |
| `auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` | T23's single-caller pin widened to three | phase 6, on T23-D2's own threat model |
| `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the new export's contract, in its own module's suite | phase 9, Kimi I2 |
| `auth/signOut.ts`, `…test.tsx` | created | frozen brief |

**Corrected clause:** *T35 adds no route, no catalogue key, no network call and no new storage key, and
modifies exactly the four files above, each for a reason recorded in the phase that made the change.*

A changed-file set cannot honestly be pinned from inside a test — it would be a git diff asserted at runtime,
green on a dirty tree and red after a rebase. So what is pinned instead is **the rule those edits had to
satisfy**: an export added to another task's module must be held to a contract in *that* module's suite. That
is the check which would have caught the omission when it happened, and it is now a case.

### Four clauses that were claimed and unmeasured

| Clause | The gap | Closed by |
|---|---|---|
| **AC7** "the list holds `registerServiceWorker.ts`'s **two** PWA entries" | the exclusion excused a **file**, and excusing a file excuses every declaration that ever appears in it. A third mutable added there would have been waved through in silence. | the count is pinned per entry, and **SO19** is the third mutable arriving |
| **AC2** "both leave **the same state**" | that is a comparison; the case asserted a list of nulls, which is satisfied by two calls that differ in some way neither assertion looks at | the whole observable state is captured after one call and compared after two |
| **AC12** "for **any** storage condition" | only a store that *refuses* was tested. A store that is **absent** is a different failure: the expression throws before any method is called, which some privacy modes produce | a case with `sessionStorage` stubbed to `undefined` |
| **AC9** "with a message **naming task 39**" | nothing checked that it does. A pinned hazard whose message loses its addressee is a green test beside a real defect with nobody told — the exact failure the case exists to prevent | the hazard block's own source is asserted to name task 39 and OQ-T35-3. If the message is the deliverable, pin the message (T32's lesson) |

## Manifest — 35 cases

| Criterion | Case | Verifies |
|---|---|---|
| **AC1** | leaves no token held | R19 cl. 1 |
| | **clears FIRST, proven by a later step that throws** | R19 cl. 1, R22 — the order (found by SO03) |
| | when a later step throws, the rest are abandoned — residue, never a token | R19, phase 9's correction of I9 |
| | source puts the clear before the other three | R19 cl. 1 |
| | drops a token **adopted** from a sibling | R19 cl. 1, L18 |
| **AC2** | harmless twice, and the second call leaves the state the first did | R19, R22 |
| | harmless in a cold tab that never signed in | R22 — the state task 39 calls from |
| **AC3** | no pending notice | R19, L4 |
| | no return target | R19, L1 |
| | none of the three authorize items, from a **real** mid-flight sign-in | R19, L16 |
| **AC4** | returns `undefined`, state clear on the next line | R19, R57 |
| | source defers nothing — no async, await, timer, microtask | R57 |
| **AC5** | zero parameters (and `signOut(undefined)` is a **compile** error) | L4 |
| **AC6** | four removals on the four keys, no read, write or `clear()`; six console channels silent | L3, L16 |
| | CONTROL: the watch sees a removal, read, write and clear | precision |
| | CONTROL: the console watch sees all six channels | precision |
| **AC7** | mutable state only where sign-out clears it or the list excuses it, **with the count pinned** | R19, L3 |
| | CONTROL: three planted shapes are caught (`let`, `new Map()`, array) | precision |
| **AC8** | the inventory names an owner and a primitive per piece | R19 |
| | spells no whole key of its own | — |
| | accounts for every `themistra.` literal, **in both directions** | L16 |
| | no module **builds** a key, so the literal scan cannot be side-stepped | L16 (Kimi I5) |
| | CONTROL: a key outside the inventory is caught | precision |
| **AC9** | **KNOWN HAZARD**: a signed-out tab still adopts a sibling renewal | R19, L18 — **OQ-T35-3** |
| **AC9b** | the hazard case names task 39 and OQ-T35-3 | the criterion's own wording |
| **AC10** | every primitive call is inside the measured allowlist | R19 |
| | every permitted caller really calls its primitive — no stale entry | precision (Kimi I8) |
| | CONTROL: a stray call in a non-allowlisted file is caught | precision |
| | sign-out calls all four and reaches no store itself | L16, L3 |
| **AC11** | no route, no network call, no catalogue key | L11, L14, L6 |
| | **the export added to T24's module is held to a contract in T24's suite** | the corrected clause |
| | no module-level mutable of its own | L3 |
| **AC12** | token cleared when every storage operation is refused | L3 |
| | token cleared when `sessionStorage` is **absent** | L3 |
| | CONTROL: the denied store really does throw | precision |

Plus four cases in **T24's** suite for `clearReturnTarget`: removes exactly that key and reads nothing;
idempotent and does not look first; never throws on a refused store; and the consuming reader still behaves
as it did beside it.

## Gates

| Gate | Result |
|---|---|
| T35 cases | **35 passed**, exit 0 |
| T24's suite | **188 passed** |
| whole suite | **1613 passed / 1 failed of 1614** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation | **18/18 regressions killed · 1/1 canary · 0 survivors · 0 broken** |

Every mutant's failure count is recorded in the run. The coarse ones are verified narrowly rather than
trusted: **SO12** (306 failures) fails exactly AC2's two cases, **SO17** (9) exactly AC10's one, **SO18** (2)
exactly AC1's one.

**Phase 10 complete.**
