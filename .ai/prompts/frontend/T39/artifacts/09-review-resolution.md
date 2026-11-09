<!-- MODEL: Human Approval — Phase 9 (Review Resolution) -->

# frontend · T39 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/08-independent-review.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Sixteen findings from Kimi 2.7, thirteen from the self-review. Phase 8 **independently confirmed every
major self-review finding** (I1↔F1, I2↔F2, I3↔F3, I4↔F4, I5↔F5, I6↔F6, I7↔F7, I14↔F12/F13, I16↔F11),
which is worth saying because it is the first time in this package that a Kimi gate and a self-review
converged on the same list without either prompting the other.

Six findings were new. **Three of them I decided on by measurement, and one of those measurements
reversed my decision** — see I10.

## Resolution log

| | Finding | Verdict | Where |
|---|---|---|---|
| **I1 / F1** | `signOut.ts` still says the hazard cannot be closed | **ACCEPTED** | applied, below |
| **I5 / F5** | `broadcastSignOut()` documented fire-and-forget and isn't | **ACCEPTED** | applied, below |
| **I6 / F6** | OQ-T39-7's residual understated | **ACCEPTED** | applied, below |
| **I12** | a throwing `onSignOut` | **ACCEPTED as documentation · consequence REJECTED by measurement** | applied, below |
| **I10** | nothing asserts `SIGN_OUT_TYPE`'s shape | **REJECTED, then ACCEPTED when the rejection's own mutant survived** | phase 10 + FU-T39-1 |
| **I8** | the channel name and type are not pinned | **SPLIT: type half REJECTED, channel half ACCEPTED** | phase 10 |
| **I9** | nothing scans for unauthorized `markSignedIn()` callers | **ACCEPTED, severity raised** | phase 10 |
| **I11** | `endSession.ts`'s latch sentence misleads | **REJECTED** | — |
| **I2 / F2 · I15** | the latch leaks between cases | **ACCEPTED** (I15's second site declined) | phase 10 |
| **I3 / F3** | AC11 cannot tell refused from never delivered | **ACCEPTED** | phase 10 |
| **I4 / F4** | two scans read unstripped comments | **ACCEPTED** | phase 10 |
| **I7 / F7** | `quiet()`'s doc comment | **ACCEPTED** | phase 10 |
| **I14 / F13** | XT27's placement is unfalsifiable | **ACCEPTED** | phase 10 |
| **I14 / F12** | XT18's idempotency is unfalsifiable | **ACCEPTED as rewording** | phase 10 |
| **F8** | the unindented helpers | **ACCEPTED** | phase 10 |
| **I13** | AC6's double-close | **NOT A FINDING** — Kimi's own assessment is "the test does assert this. No gap." | — |
| **I16 / F11 · F9 · F10** | dormancy · the duplicated pin · `settle`'s bound | **NO ACTION**, each recorded as already understood | — |

**Phase 9 touches production code and production comments only.** Everything in a test file is phase
10's, which is why eleven accepted findings are listed as applied there: this phase's prompt applies the
accepted comments, and the next one's forbids changing production code. The split keeps each diff
reviewable as one kind of thing.

---

## Applied

### I1 / F1 — `signOut.ts:42-50`

The paragraph is now the record of a **closed** hazard, and it **keeps both of task 35's arguments
against the fix**, each with the reason it failed:

> *"Propagation narrows the window without closing it…"* — true about the POST and irrelevant: what
> decides this tab's token is when **this** tab handles, and that is ordered.
>
> *"A latch means ADDING a piece of state in the task whose whole statement is removing it."* — true of
> task 35 and it does not transfer: L18 calls task 39's subject cross-tab **coherence**.

Kept rather than deleted, because a comment that confidently forecloses a fix is read as a reason not to
try — and this one nearly worked. The last line records that it was caught by its own task's self-review,
not by a reader.

### I5 / F5 — `crossTabSignOut.ts`, the production path only

`postMessage` on the no-argument path is now wrapped in `catch` and dropped, with the reason in the
header: the failure being prevented is a *notification* failure stranding a sign-out between the clear
and `location.assign`, which would leave the visitor signed in at SAS (R19) because a broadcast failed.

**The injected path is deliberately NOT caught**, which is a decision and not an omission: that path is a
test seam, its channel is in the caller's hand, and swallowing there would hide a caller's bug in the one
place the caller could fix it. Kimi's I5 recommended catching unconditionally; the asymmetry is the half
of it I did not take.

### I6 / F6 — `crossTabRenewal.ts`'s residual comment

*"Nothing holds it — it is never adopted"* was true only of the listener. A caller that hands the returned
token to `retainAccessToken` **releases the latch**, so the residual is not "one token escapes the guard"
but "the guarded path can clear its own guard". Corrected, with the double unreachability stated beside
it so nobody reads it as live.

### I12 — accepted as documentation, **consequence rejected by measurement**

Kimi recommended wrapping `onSignOut()` and gave the reason: *"the listener would crash and future
sign-out broadcasts would be ignored for that tab."* **Measured:** a callback that throws on every
delivery, two broadcasts → **two uncaught errors**, so the handler was still attached for the second. A
DOM listener is not detached by throwing. Nothing is wrapped, because a wrap would buy only a different
route to the same uncaught error; the behaviour is documented instead, with the measurement.

---

## Rejected

### I11 — `endSession.ts:105`

> *"A latch would be module-level mutable state - the one thing this module must not hold - traded for a
> second navigation that cannot happen."*

That paragraph's subject is an **idempotence** latch for a second `endSession()` call, not a session
latch, and the sentence is still true: `endSession.ts` holds no module-scope state. The worry is that a
reader infers no latch exists anywhere — but the task-39 comment **fifteen lines below** names the latch,
says `signOut()` sets it, and says which module it lives in. Adding a cross-reference to an unrelated
latch inside a paragraph about idempotence would cost more clarity than it buys.

### I8, the type half

> *"No test asserts the constant's value… Add an assertion that `SIGN_OUT_TYPE` equals
> `'session:signed-out'`."*

It is asserted **three times already**, each by literal rather than by the constant:
`signOutPropagatesToAllTabs.test.ts:174` (`toBe('{"type":"session:signed-out"}')`, the whole message as
JSON), and `signOutClearsMemoryAndEndsSasSession.test.tsx:886` and `:1988`. A rename of the constant's
value fails all three.

**The channel-name half stands**, and is sharper than Kimi put it: `grep` finds
`'themistra.session.signout'` in **exactly one place in the repository** — the production declaration.
Every test reads the constant, so a rename passes everything. That matters for the same reason the parser
is lenient: two tabs running different app versions must agree on the channel, or a sign-out reaches
nobody. Phase 10.

---

## The rejection that was wrong

### I10 — asked for a scan asserting `SIGN_OUT_TYPE` is a string and not an object

I rejected it, with what looked like strong evidence: task 35's `declarations: 1` entry already catches
an object constant, and that is not a deduction — **it is how the object form was caught while phase 6
was being written**, and phase 6 records the measurement.

Then I built **XT32** to pin the rejection, as T37 pinned its own rejected I12. XT32 turns
`SIGN_OUT_TYPE` into an exported object constant. **It survived all 1 720 tests.**

The reason is one anchor character. The scan is

```
MUTABLE_AT_MODULE_SCOPE =
  /^(?:let|var)\s|^const\s+\w+\s*(?::[^=\n]*)?=\s*(?:new\s+(?:Map|Set|WeakMap|WeakSet)\s*\(|\[|\{)/m
```

and it requires **`^const`**. Phase 6's object form was a bare `const` and was caught. XT32's is
`export const`, and **nothing in the repository sees it**. T39's own AC16 has the same hole from the other
side: it counts `^(?:let|var)\s` and would not see an exported object either.

So **I10 is accepted**, with a rationale neither review had: not *"no test asserts the shape"* but
*"both scans anchor on a form that `export const` escapes"*. The fix is in T39's own AC16 — phase 10 —
and XT32 stays in the harness as a **currently-surviving row phase 10 must kill**.

**The commit that carried this phase's production fixes says "I10 rejected" in its message. That line is
wrong, and this artifact is the correction.** The rejection was reversed by the mutant written to defend
it, which is the whole reason that mutant existed.

### What else the same hole hides — FU-T39-1

`MUTABLE_AT_MODULE_SCOPE` is task 35's, and it is shared by task 22's accounting and task 36's
`endSession.ts` and `idToken.ts` cases. Measured across production source, it sees **two** declarations
and misses **fourteen**:

| caught (`^const`) | missed (`^export const`) |
|---|---|
| `profile/readProfile.ts:61` · `profile/readAccount.ts:74` — both excused by name | `app/routes.tsx:27`, `:40`, `:56` · **`auth/accessToken.ts:46`** (the session object itself) · `auth/signInNotice.ts:26` · `auth/authorizeRequest.ts:29`, `:35` · `profile/accountStatus.ts:31` · `pwa/apiRequestPolicy.ts:24`, `:46` · **`api/client.ts:6`** (`new Set([…])`), `:25` · `pwa/navigationDenylist.ts:17` · `i18n/catalogue.ts:2` |

So a lookup table is excused when it is `const` and invisible when it is `export const`, and the case
whose message is *"a module holds mutable state that sign-out neither clears nor excuses"* has never
examined fourteen of them.

**Not fixed here, and deliberately not.** Widening the regex changes what fourteen files in four closed
tasks are measured against, and it would almost certainly turn several suites red at once — which is a
decision about those tasks, not about T39. T39's own instrument is T39's to fix; the shared one is
**FU-T39-1** for the owner. The T36 phase-9 rule is what draws the line: an inherited instrument may be
edited when it has become **false** or must grow to **admit new state**, and not when it is merely
incomplete for something another task owns.

---

## Harness changes

| | |
|---|---|
| **XT03** | re-aimed. Before the fix it killed **22** cases by throwing into `endSession()`; with the swallow it is killed by **one** — AC9d, because no message reaches the channel. The row now measures that the swallow did not swallow the feature. |
| **XT31** | new. The pre-fix state reconstructed in full — a post that throws **and** no catch to drop it. **Killed, 22 cases**, which is F5's hazard measured rather than argued. |
| **XT32** | new. The measurement that was built to reject I10 and reversed it instead. **SURVIVES.** Phase 10 must kill it. |

Running total: **32 mutants, 30 killed, 2 survivors (XT18, XT27) + XT32 open**. The harness exits 1, and
the three open rows are findings F12, F13 and I10 rather than defects to paper over.

## Open questions

**OQ-T39-6** (AC9c) · **OQ-T39-7** (now correctly sized, I6) · **FU-T39-1** (the `^const` anchor, above)
· **OQ-T36-5** (nothing calls `endSession()`) · **OQ-T32-4** (the seventh harness copy) ·
**FU-T37-2** (`routes.tsx:5`).

## Phase 10

Test generation. Eleven accepted findings land there, and three harness rows are waiting to be killed.
