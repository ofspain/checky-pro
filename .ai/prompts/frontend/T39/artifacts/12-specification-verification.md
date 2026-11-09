<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T39 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | every prior artifact, the implementation, the tests |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task statement** (`spec/frontend/tasks.md`, task 39): *"Sign-out propagates to every tab. Signing out in
one tab signs out all open tabs. Verifies `signOutPropagatesToAllTabs`. Cites R22, L18."*

## The two scoped clauses, verbatim

> **R22** *(requirements.md:72-75)* — "IF renewal fails or the session is no longer valid, THEN the SPA
> SHALL transition to clean re-auth, SHALL NOT show a half-authenticated screen, and SHALL show neutral
> copy. **Sign-out SHALL propagate to every open tab.**"

> **L18** *(design.md:63-64)* — "**[ALL] Single-flight renewal and cross-tab coherence.** At most one
> renewal runs per tab. Tabs share renewal **and sign-out** through BroadcastChannel or a Web Locks
> leader (R21, R22)."

R22's first three clauses are tasks 33/35's; the fourth is this task's, and it was the **last unowned
clause in the requirement**. L18's renewal half was built in task 21; the sign-out half is this task's.

---

## Gates

| | |
|---|---|
| `vitest run` | **1724 passed / 1 failed of 1725** — the known `oxlint` host case |
| `signOutPropagatesToAllTabs.test.ts` · task 35's · task 21's | **25** · **91** · **23** |
| `tsc --noEmit` · `oxlint src` | **exit 0** · **exit 0** |
| `07-mutation-harness.py` | **33 mutants · 32/32 regressions killed · 0 survivors · 0 broken · 1 unfalsifiable, scored apart** |

---

## Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R22** · sign-out propagates to every open tab | **yes** | `api/crossTabSignOut.ts:115` `broadcastSignOut`, `:150` `listenForSignOut`; `auth/endSession.ts:121` broadcasts | `signOutPropagatesToAllTabs.test.ts:119`, `:132` (three tabs, because "every" cannot be measured with one) | **nothing installs the listener** | **D1** |
| **R22** · no half-authenticated tab after sign-out | **yes** | `auth/signOut.ts:83` latch; `api/crossTabRenewal.ts:125` refusal | T35 suite: three AC9 refusal cases, both orderings | — | — |
| **R19** · the SAS session ends once, in the acting tab | **yes** | `auth/endSession.ts:121-122` — broadcast, then one `assign` | `:188`, `:198`; T35's "performs one navigation and no more" | — | **D7** (source scan, not end-to-end) |
| **R21** · renewal counting and settling unchanged | **yes** | guard placed below the count and the wake, `api/crossTabRenewal.ts:125` | `siblingTabsShareOneRenewal.test.ts` — the phase-10 case | — | — |
| **L18** · BroadcastChannel, not a Web Locks leader | **yes** | `:115`, `:150` depend on `BroadcastChannel` alone | `:251` (locks removed entirely), `:266` | — | — |
| **L16** · nothing is stored | **yes** | no store reference exists in the module | `:299` (+ a positive control), `:324`; T22's inventory and namespace cases | — | — |
| **L3** · a broadcast is a surface | **yes** | the message is `{ type }` and nothing else | `:206`, `:224`, `:241` | — | — |
| **L2** · layering, `api/` imports nothing from `auth/` | **yes** | the local action is a parameter, `:150` | `:547`, `:558` | — | — |

### Frozen acceptance criteria (phase 4, AC1–AC17)

| AC | Verified by | |
|---|---|---|
| **AC1** delivered to a sibling, which clears its own token | `:119` · `:132` | ✅ |
| **AC2** the receiver does not re-broadcast, counted by a third endpoint | `:148` · `:170` | ✅ |
| **AC3** the receiver does not navigate; SAS ends once | `:188` · `:198` | ✅ |
| **AC4** exactly `{ type: 'session:signed-out' }`, on the raw payload | `:206` · `:224` · `:241` | ✅ |
| **AC5** works with no Web Locks at all | `:251` · `:266` | ✅ |
| **AC6** `BroadcastChannel` undefined → no-ops that do not throw | `:274` | ✅ |
| **AC7** OQ-T35-3 closed, **in both orderings** | T35: *refuses a success posted AFTER* · *…BEFORE* · *…from a SIBLING TAB* | ✅ |
| **AC8** the latch resets on a real sign-in | `auth/accessToken.ts:89`; T35: *releases the latch on a real sign-in* | ✅ |
| **AC9** `markSignedOut()` runs FIRST, and is set even when a later step throws | `auth/signOut.ts:83`; T35: *sets the latch even when the very next step throws* | ✅ ¹ |
| **AC10** nothing stored on any path | `:299` · `:324` | ✅ |
| **AC11** only `type === 'session:signed-out'`; extra properties ignored, with the reason | `:334` · `:365` | ✅ |
| **AC12** `close()` removes the listener | `:502` · `:520` | ✅ |
| **AC13** `crossTabSignOut.ts` imports nothing from `auth/` | `:547` · `:558` | ✅ |
| **AC14** T35's AC9 replaced, AC9b deleted, the old hazard message gone | T35: *the retired hazard wording is gone from this file* | ✅ |
| **AC15** excused in `NOT_SESSION_STATE`, `declarations: 1`, latch named as the inverse of session state | T35 suite `:225-237` + the excusal case | ✅ |
| **AC16** no `BroadcastChannel` at module scope | `:567` | ✅ |
| **AC17** the stale-adopt race pinned as a failing expectation | T35: AC9c | ✅ |

¹ **Partial, and named rather than claimed.** "`markSignedOut()` runs FIRST" and "the latch is set even
when a later step throws" are both verified. The clause "the five existing steps keep their order" is
verified only for the **clear's** position (T35's *"clears FIRST, proven by a later step that throws"*);
the relative order of the remaining three is not pinned by any case, and T39 did not change it. Stated
because an unqualified ✅ there would be a claim no case holds — the exact defect F13 was.

### Criteria added after the freeze

The frozen brief is frozen, so criteria that came out of the review gates are numbered **beyond** it.

| AC | Source | Verified by |
|---|---|---|
| **AC18** the channels this module creates are the channels it closes | Kimi phase 11 **F3** | `:403` · `:433`, mutant **XT33** |
| **AC19** the sign-out and renewal listeners coexist on one tab | Kimi phase 11 **F13** | `:465` |
| the channel **name** pinned by literal | Kimi phase 8 **I8** (accepted half) | `:224` |
| `markSignedIn` in `PERMITTED_CALLERS` | Kimi phase 8 **I9** | T35's stray-caller sweep |
| a signed-out tab's outcome is counted and its waiters settle | self-review **F13** / Kimi **I14** | `siblingTabsShareOneRenewal.test.ts`, mutant **XT27** |

**A numbering defect found in this phase and corrected.** The two new blocks were first written as
`AC14` and `AC15` — numbers the frozen brief already assigns to *"T35's AC9 is replaced"* and *"excused in
`NOT_SESSION_STATE`"*. Two different criteria would have shared each number, in a matrix whose only job is
to make that impossible. Renumbered to **AC18/AC19**.

And the renumbering immediately proved its own point about citations. The sentence here first read *"the
describe strings are the same length, so every line citation above is unchanged"* — true of the rename and
false of the commit, because the rename shipped with a seven-line comment explaining why the numbers skip.
Eight citations below `:365` were off by seven. Re-derived from the file, not from the claim.

---

## The approving principal engineer's answers

### 1. Is the task fully complete?

**The mechanism is complete. The journey is not reachable, and that is not this task's doing.**

Everything the task statement names is built and verified: a sign-out in one tab is delivered to every
sibling, the receiver clears locally without echoing, the SAS session ends once in the acting tab, and a
signed-out tab refuses a sibling's renewal. What is missing is a caller: `endSession()` has no production
caller (**OQ-T36-5**) and no module owns app-level listeners, so every case installs the listener itself.

**T39 is the ninth consecutive task in this position.** The gap is a single unassigned piece of wiring
shared by nine tasks, not nine separate omissions, and no test in this task can close it.

### 2. Does it satisfy every acceptance criterion?

**Yes — seventeen frozen, with one partial stated explicitly (AC9's footnote), plus five added by the
review gates.** The suite is 25 cases in the named file, 91 in task 35's, 23 in task 21's, and the
mutation harness measures them.

### 3. Does it violate any LOCKED decision?

**No.** L16 (nothing stored) is verified at runtime and by source, in two suites. L3 (surfaces) is
verified on the raw payload. L18 is satisfied by the mechanism it names — and the **second channel** is
the decision worth defending here: `crossTabRenewal.ts` builds its channel only where Web Locks exist, and
L18 permits that for renewal because the cost is a duplicate request. For sign-out the cost would be a
sibling tab left signed in, which R22 forbids outright. L18 says "BroadcastChannel **or** a Web Locks
leader"; taking the first for sign-out and leaving renewal on the second is inside the decision, not a
deviation from it.

### 4. Remaining risks

| | |
|---|---|
| **R1 · the dormant listener** | **D1 / OQ-T36-5.** The highest-value risk and the only one a user would notice. Nine tasks deep. |
| **R2 · OQ-T39-6, the stale-adopt race** | Pinned by AC9c as a *failing-when-fixed* expectation. Needs a generation stamp on the message, which means changing `crossTabRenewal`'s closed union — declined, with reasons, in phase 4's D1. Unreachable today: `renew()` always rejects with `RENEWAL_NOT_BUILT`. |
| **R3 · OQ-T39-7, the guarded path can clear its own guard** | Now a *measured fact* in task 21's suite rather than a sentence in a comment. Doubly unreachable: no production caller, and no genuine success can be produced. |
| **R4 · FU-T39-1, the shared scan's two holes** | **The widest-reaching risk in this task, and it is not about this task.** `MUTABLE_AT_MODULE_SCOPE` anchors on `^const` and on `new Set\s*\(`, so **fourteen** exported module-scope object/array/Set literals are invisible to it — including `auth/accessToken.ts:46`, the session object — and `pwa/registerServiceWorker.ts` is excused with a **pinned `declarations: 2`** while holding **three**. T39 widened its own instrument and left the shared one, because changing it alters what four closed tasks are measured against and would turn that pinned count false. |
| **R5 · XT18, unfalsifiable** | `close()`'s idempotency guard has no observable effect to remove. Scored apart rather than counted either way. |
| **R6 · the lenient parser** | A deliberate asymmetry (phase 4, rejecting Kimi F8's strict half): acting on a slightly-unknown shape costs a redundant local clear, refusing one costs a live session. Pinned by a case that *acts* on `{ type, reason }`. |

### Deviations

| | |
|---|---|
| **D1** | Nothing installs the listener; every case installs it directly. Ninth consecutive dormant task. |
| **D2** | Three closed tasks' production files modified (`crossTabRenewal.ts`, `accessToken.ts`, `endSession.ts`). |
| **D3** | Five instruments in four suites grown, as task 35's handoff instructed. |
| **D4** | The hazard closed is latent, not live — no production caller, and `renew()` always rejects. |
| **D5** | **OQ-T39-6 not closed**, pinned by AC9c. |
| **D6** | The parser is deliberately lenient about extra properties. |
| **D7** | AC3's "ended once" is proved by source scan, not end-to-end, because D1 makes a journey unreachable. |
| **D8** | Five criteria added after the freeze, numbered beyond it (AC18, AC19 and three instrument additions). |
| **D9** | `broadcastSignOut()` swallows on the production path and **not** on the injected one — an argued asymmetry, narrower than Kimi I5's recommendation. |
| **D10** | FU-T39-1 recorded, not fixed. |

### Spec drift observed, not touched

- `package.md:123` still names a pre-revision test name (carried from earlier tasks).
- `design.md:138` still reads "PROPOSED … pending Q6" for a decision since resolved.

`spec/` is never modified by this pipeline; both are recorded for the owner.

---

## Verdict

**PASS** — every clause of R22's propagation requirement and L18's sign-out half is implemented and
measured, with the one incomplete thing (no production caller) an inherited, unassigned piece of wiring
that nine consecutive tasks share and no test in this task can close.
