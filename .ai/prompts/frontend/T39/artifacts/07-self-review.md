<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review) -->

# frontend · T39 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T39 — Sign-out propagates to every tab |
| **Consumes** | `artifacts/06-implementation.md`, the implementation |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

Thirteen findings, no fixes — phase 9 decides. Five of them are pinned by measurements recorded
here rather than by argument, and two of those measurements are surviving mutants.

## Verification performed

| Command | Result |
|---|---|
| `vitest run` | **1719 passed / 1 failed of 1720** — the known `oxlint` host case |
| `vitest run src/api/signOutPropagatesToAllTabs.test.ts` | **21 passed (21)** |
| `vitest run src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx` | **91 passed (91)** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `07-mutation-harness.py` | *see **The harness** below* |

---

## Findings

### F1 — `signOut.ts`'s header still tells its reader this hazard cannot be closed, eight lines above the line that closes it
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOut.ts:42-50` — *"**A HAZARD THIS FUNCTION CANNOT CLOSE (task 39 owns it, OQ-T35-3).** … Task 39's propagation narrows the window without closing it, because a sibling whose renewal resolves before it handles the broadcast still posts a success and the channel orders nothing. It is not fixable from here: … the alternative — a latch that makes `adopt` refuse — means ADDING a piece of state in the task whose whole statement is removing it."* The latch is called at `signOut.ts:73`.
· **Assessment:** Both arguments were answered, and the answers are *measured*: the first reasons about when the sibling **posts**, while what decides this tab's token is when this tab **handles** — and only that is ordered; the second is true of task 35 and does not transfer, because L18 calls task 39's subject cross-tab *coherence*. Those answers live in `crossTabSignOut.ts`'s header and in this suite. A reader of `signOut.ts` — the one file the latch is called from — has no reason to open either, and is told the fix is impossible and that the mechanism actually used was considered and rejected. This is the shape T37 recorded as FU-T37-2 (`routes.tsx:5`), one degree worse: that comment is wrong about a *consequence*, this one is wrong about whether the task that fixed it could.
· **Recommendation:** Replace the paragraph: keep the hazard's description, mark the two arguments as superseded with the reason each failed, and point at the latch. The T36 phase-9 rule applies with more force to a comment than to a test — an instrument that has become **false** may be edited in a closed task's file, and T39 already edits this one.

### F2 — The latch leaks between cases in task 35's suite; the reset is imported there and never called
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx:29` imports `markSignedIn`, and the file has **no call to it** — only `hasSignedOut` is used (`:845`, `:891`, `:934`, `:938`, `:952`, `:956`). Neither hook (`:359`, `:369`) touches the latch, and `renewalRig`'s `close()` calls `clearAccessToken()`, which does not release it.
· **Measured:** a probe appended to the end of that file — `expect(hasSignedOut()).toBe(false)` — **fails**: `1 failed | 91 passed (92)`. Three AC9 cases and AC9d leave the latch set.
· **Assessment:** Nothing is wrong today, because every later case that needs an adopt calls `retainAccessToken` first and so releases the latch itself. That is luck, not design: a case added to this file that posts a `renewal:succeeded` without retaining first would be refused, and would fail for a reason belonging to a case that ran before it. The unused import is evidence the reset was intended and never wired — and `oxlint src` and `tsc --noEmit` both pass it, so no gate will ever say so.
· **Recommendation:** Call `markSignedIn()` in that suite's `afterEach` (`:369`), beside the existing teardown, and keep `renewalRig`'s `close()` as it is. The new suite already does exactly this (`signOutPropagatesToAllTabs.test.ts:73`, `:79`), which is why it has no equivalent leak.

### F3 — AC11's negative case cannot tell "refused" from "never delivered"
· **Severity:** **Major**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:260-274` posts twelve non-sign-out payloads and asserts `heard` is `[]`. Nothing in the case establishes that the channel was live.
· **Measured, both directions:**
  - with the listener made deaf (`channel.addEventListener('message', onMessage)` → `void onMessage;`), **six** cases die — and AC11's first case **passes**, as do AC10's first case and AC4's;
  - with the parser made always-true (`isSignOut` → `return true`), **AC11's first case is the only case in the whole 1720-test suite that fails**.
· **Assessment:** So the case is load-bearing in one direction and vacuous in the other. The deaf mutant is killed overall, which is why this is not a surviving mutant — but it is killed by *other* cases, and AC11's own claim ("only a sign-out is acted on") is satisfied by a channel that delivers nothing at all. This is T37's lesson — *a control must measure its own claim* — applied to a negative assertion: the twelve payloads are the strongest part of this suite and the weakest-held.
· **Recommendation:** End the same case with a real sign-out on the same channel and assert it **is** heard, so the twelve refusals are measured against a delivery that worked. AC10's first case wants the same treatment for a different reason: it asserts no store was touched, and a listener that never fired touches no store either.

### F4 — Two source scans read comments, and one of them is one word away from failing a correct implementation
· **Severity:** **Minor/Major**
· **Evidence:** `signOutClearsMemoryAndEndsSasSession.test.tsx:2016-2024` (AC9d) reads `endSession.ts` **unstripped** and orders `signOut()`, `broadcastSignOut()` and `location.assign` by `indexOf`. `signOutPropagatesToAllTabs.test.ts:132-138` (AC2) greps `signOut.ts` unstripped for `/broadcastSignOut/`; that file's prose already contains "broadcast" twice (`signOut.ts:13`, `:47`).
· **Measured:** `endSession.ts`'s comment at `:112` currently reads `` because `assign` may unload the page ``. Rewriting those two words as `` because `location.assign` may unload the page `` — a comment-only edit, correct prose about correct code — moves `atAssign` to **225** and `atBroadcast` to **935**, so the order assertion fails. The production file is unchanged and the test goes red.
· **Assessment:** The new suite solved this for itself, with a control and a written rationale (`signOutPropagatesToAllTabs.test.ts:50-69`: *"a scan that reads comments measures what a module SAYS instead of what it DOES"*). AC9d was written in the same session and did not inherit it. In a codebase where the comments are deliberately long because the reasoning is the deliverable, a positional scan over unstripped source is a tripwire aimed at prose.
· **Recommendation:** Strip comments before the positional scan in AC9d and before AC2's grep. The stripper exists; phase 10 should decide whether it moves to a shared helper or is copied with a pointer.

### F5 — `broadcastSignOut()` is documented fire-and-forget and is not: a throw lands between the clear and the hand-over
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/crossTabSignOut.ts:95` — *"Tells the app's other tabs that this one signed out. **Fire-and-forget.**"* The body's `try` has only a `finally` (`:111-115`), so `postMessage` throwing propagates. `endSession.ts:121-122` calls it between `signOut()` and `globalThis.location.assign(...)`.
· **Assessment:** Unreachable today in both ways it could fire: the no-argument path constructs a fresh channel, so the DOMException the module's own header names (`:99`, *"`postMessage` on an already-closed channel throws"*) cannot happen, and `{ type: string }` is always structured-cloneable. But the consequence if it ever did is the worst available: this tab has cleared its token, the siblings have not been told, and the SAS session is never ended because `assign` is not reached — a visitor who clicked sign out stays signed in at the authorization server (R19).
· **Recommendation:** Either make the advertised contract true — wrap the post in a `try`/`catch` that swallows, which is what fire-and-forget means — or delete the words. The first is better: a sign-out that cannot complete its own hand-over because a *notification* failed is the wrong failure ordering. The harness carries this as **XT03**.

### F6 — OQ-T39-7's comment understates the residual it names
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/crossTabRenewal.ts:111-115` — *"`use()` below still RETURNS a sibling's token to a local `renew()` caller in a signed-out tab. **Nothing holds it — it is never adopted** — but a caller awaiting `renew()` receives it."*
· **Assessment:** "Nothing holds it" is true only of the listener. A caller that receives the token and does the obvious thing with it — hand it to `retainAccessToken` — both re-authenticates the tab **and releases the latch** (`accessToken.ts:88`), so every subsequent sibling success is adopted too. The residual is therefore not "one token escapes the guard" but "the guarded path can clear the guard". That is a different size of thing to hand to whoever reads it next, and the comment is the handoff.
· **Recommendation:** Say so in the comment. No code change: `shareRenewalAcrossTabs` has no production caller and `accessToken.renew()` always rejects with `RENEWAL_NOT_BUILT`, so the path is doubly unreachable — which phase 2 measured and phase 6 recorded as D4.

### F7 — `quiet()`'s doc comment denies exactly what `quiet()` does
· **Severity:** **Minor**
· **Evidence:** `frontend/src/api/signOutPropagatesToAllTabs.test.ts:43-46` — *"Waits for `received` to stop growing, with delivery observed rather than guessed at."* There is no `received` anywhere in the file, nothing is observed, and the body is a fixed 25 × 2 ms spin, which is a guess.
· **Assessment:** The sentence appears to have been written for a different helper and survived. It matters more than a stale comment usually would, because it is the sentence a reader would rely on when deciding whether a negative case needs its own positive control — which is F3. Three functions below it, task 35's `settle()` does observe delivery first, and explains why.
· **Recommendation:** Either make it true (resolve on an observer endpoint's `message`, as `settle` does) or state the fixed bound plainly and point at F3's controls as what compensates for it.

### F8 — `renewalRig` and `settle` are declared at column zero inside a `describe` callback
· **Severity:** **Minor**
· **Evidence:** `signOutClearsMemoryAndEndsSasSession.test.tsx:778-816`. They sit inside `describe('signOutClearsMemoryAndEndsSasSession', …)` but are indented as though at module scope — the only unindented block in a 2 000-line file.
· **Assessment:** Correct and scoped as intended; they were hoisted out of AC9's block mid-build because AC9c is a sibling `describe` and could not see them. The indentation did not follow. `oxlint src` and `tsc --noEmit` both pass, so nothing will flag it.
· **Recommendation:** Indent them, or move them to module scope where their indentation would be honest. Cosmetic; phase 9's call.

### F9 — The module's one-mutable count is pinned twice, in two files
· **Severity:** **Informational**
· **Evidence:** `signOutPropagatesToAllTabs.test.ts:369-370` (`toHaveLength(1)`) and `signOutClearsMemoryAndEndsSasSession.test.tsx:228-237` (`declarations: 1`).
· **Assessment:** Deliberate, and the two claims differ: AC16 says the module holds one piece of state, task 35's entry says that one piece is excused from being cleared. Both must change together if the module ever legitimately grows a second. Noted so the next reader knows the second site exists.
· **Recommendation:** None. It is the same shape as OQ-T32-4 — duplication that is cheaper than the abstraction until it is not.

### F10 — The three AC9 refusal cases always pay `settle()`'s full bound
· **Severity:** **Informational**
· **Evidence:** `settle()` (`:808-816`) polls until `accessToken.current()` is non-null or 50 attempts elapse. A refusal keeps it null, so each case spends the whole 100 ms; the hazard case AC9c exits early.
· **Assessment:** The inverse of what the original hazard case paid, and it is the right trade: `await delivered` is unbounded and happens first, so a refusal case cannot pass because nothing was delivered — the false-PASS shape F2 of Kimi's phase 11 found in task 35. ~300 ms for the file.
· **Recommendation:** None.

### F11 — Nothing installs the listener in production
· **Severity:** **Blocker** (process, not code)
· **Evidence:** `signOutPropagatesToAllTabs.test.ts:9-12`. `endSession()` has no caller (OQ-T36-5) and no module owns app-level listeners.
· **Assessment:** The ninth consecutive task in this position, recorded as D1. "Signing out in one tab signs out all open tabs" is verified as a mechanism, not as a journey, and no test in this task can change that. Every piece is built and wired to each other; nothing is wired to the application.
· **Recommendation:** None for T39. It closes when the sign-out affordance is built.

### F12 — Nothing can falsify `close()`'s idempotency, so two assertions about it are claims rather than measurements
· **Severity:** **Minor**
· **Evidence:** `signOutPropagatesToAllTabs.test.ts:308` (`listener.close(); listener.close();  // idempotent`) and `:219` (*"close() is not idempotent on the degraded listener"*). **Mutant XT18 removes the guard (`if (closed) return;`) and SURVIVES.**
· **Assessment:** It survives because the guard protects nothing observable: `removeEventListener` with the same handler twice is a no-op by specification, and `BroadcastChannel.close()` on a closed channel is also a no-op. So `closed` is defensive code whose removal no test can see, and the two assertions that mention idempotency are really asserting "the second call does not throw", which is true of the unguarded version too. Keeping the guard is still right — it documents the intent and survives a future `close()` that does something observable — but the suite should say what it is measuring.
· **Recommendation:** Keep the guard; reword the two assertions to the claim they actually hold ("a second close() does not throw"), or give `close()` an observable effect to count. Phase 10's call, not phase 9's — it is a test-wording change, not a defect.

### F13 — The guard's placement inside the renewal listener is argued at length and measured by nothing
· **Severity:** **Minor/Major**
· **Evidence:** `crossTabRenewal.ts:107-109` — *"The guard sits HERE and not higher on purpose. The outcome is still counted and its waiters are still woken above, because this task changes what a signed-out tab ADOPTS — not how renewal is counted or how its waiters settle, which are R21's contract and `siblingTabsShareOneRenewal`'s."* **Mutant XT27 moves the guard above the count and the wake, and SURVIVES.**
· **Assessment:** The reasoning is right and the position is right; what is missing is any case that would notice the wrong one. The only suite that counts outcomes and settles waiters — `siblingTabsShareOneRenewal` — never signs out, so `hasSignedOut()` is false throughout it and the guard is inert there whatever its position. T39's own cases observe the *token*, which is unchanged by the move. So the paragraph is a claim about behaviour nothing holds: a later edit could hoist the guard, break R21's counting for signed-out tabs, and pass 1 720 tests.
· **Assessment (bound):** the behaviour it would break is reachable only by a signed-out tab receiving a renewal outcome, which is precisely the state T39 created — so this is a gap T39 opened, not an inherited one.
· **Recommendation:** One case in task 35's suite: a signed-out tab receives `renewal:succeeded`, and `outcomeCount` still increments and a pending waiter still settles while the token stays null. Phase 10. It is the only one of these findings that would make a surviving mutant die.

---

## What the implementation gets right

- **S1 — The premise was checked before it was obeyed.** The hazard was handed over with a reason it could not be closed, and phases 1–2 measured the reason to be wrong. The task would have shipped a narrower fix, and a comment claiming it was the best possible one.
- **S2 — The release sits at the single assignment point.** `retainAccessToken` is, by `accessToken.ts:82`'s own words, the only place a non-null token is assigned, so a second sign-in path added later cannot skip the reset. Putting it in `CallbackScreen` would have read more naturally and been wrong.
- **S3 — Two channels, argued from the degradation and not from tidiness.** `crossTabRenewal.ts:78-86` listens only where Web Locks exist; inheriting that would have made "a sibling tab stays signed in" the no-Web-Locks behaviour.
- **S4 — AC9 asserts both orderings.** Post-after-sign-out and post-before-sign-out, because the second is the one task 35 called unclosable and the first alone would not have answered it.
- **S5 — The receiver structurally cannot echo.** The broadcast lives in `endSession()`, not in `signOut()`, so there is no sender id and no self-filter to get wrong — and AC2 measures the structural claim with a third endpoint rather than trusting it.
- **S6 — The comment-strip control.** `signOutPropagatesToAllTabs.test.ts:328-338` asserts the strip left the real declarations behind, so an over-eager strip cannot empty the module and pass every scan at once. F4 is that this control has a sibling that needs it.

---

## The harness

`artifacts/07-mutation-harness.py`. Seventh copy of task 32's machinery (OQ-T32-4), with one addition
this task needed:

**Narrow verification is built in.** Every killed mutant prints the **names** of the cases that
failed, from the three files this task owns or grew. T37 found four separate disagreements between a
kill count and the named failures, and found each of them by writing a second script afterwards.

**And the reason it had to be built in.** This suite contains **AC9c**, which asserts a defect as it
is (OQ-T39-6). A mutant that happened to close that hazard would turn AC9c red, and a harness
counting failures would score a **fix** as a regression caught. So each row's named failures are
checked against AC9c, and a row whose kill rests on it alone prints `MISATTRIBUTED` and is counted
as lost. Phase 6 asked whether AC9c should be excluded from the numerator or made a canary; the
answer is neither — nothing is excluded, and the check is on the *attribution*, which is the thing
that could actually go wrong.

Two rows are kept whatever their verdict, because a survivor is the finding:

- **XT18** removes `close()`'s idempotency guard. A double `removeEventListener` and a double
  `close()` are both harmless by specification, so if nothing fails, AC12's and AC6's two
  "is idempotent" assertions are **unfalsifiable** rather than wrong.
- **XT27** moves the renewal guard above the outcome count and the waiter wake. That placement is
  argued for at length in `crossTabRenewal.ts:107-109`; if nothing fails, the paragraph is a claim
  no case can falsify, because the only suite that counts outcomes never signs out.

### Results

```
regressions: 28/30 killed
survivors:   XT18 close() loses its idempotency guard
             XT27 the guard moves above the count and the waiter wake
broken:      none
```

Baseline: **1 failed of 1720** (the known `oxlint` host case), **0 failed of 165** across the three
own files. **The harness exits 1, deliberately** — the two survivors are findings **F12** and **F13**,
not defects to be papered over before phase 8 reads them. Both were written as keep-whatever-the-verdict
rows, and both survived, which is the answer to the question phase 6 handed this phase.

**The attribution check never fired.** Two rows do fail AC9c — XT23 (the latch is never released) and
XT26 (the guard inverted) — and in both cases AC9c fails *alongside* a named regression case, so neither
kill rests on it. That is the outcome the check was built for: it had to be possible for it to fire.

### Two rows measured something other than their own description, and that is recorded in the harness

- **XT04** — *"signOut() broadcasts, so every receiving tab echoes"* — inserted the call and **not the
  import**: `ReferenceError: broadcastSignOut is not defined` at `signOut.ts:74`, **46 failures, 45 of
  them a crash cascade**. The intended case fired anyway, because a source scan matches an identifier
  whether or not it resolves — so the row would have scored as killed while measuring a module that
  cannot run. T37's SL25 was wrong in exactly this way. Corrected, it kills with **two** named cases, and
  the second is the one that matters: AC9d counts **two** messages on the real channel for one sign-out,
  so the echo is measured at runtime and not only by a scan.
- **XT29** — *"the broadcast runs BEFORE the local sign-out"* — inserted before removing, which left two
  occurrences of the line its second edit anchors on, so `apply` refused and the row printed **BROKEN**.
  BROKEN is not a kill, and the explicit broken-row arithmetic is the only reason the first run's score
  was not overstated by one. **Preflight now replays each mutant's edits in memory**, so an anchor that is
  unique in the file and ambiguous mid-mutant is caught before a single verdict prints. The four earlier
  copies of this harness check anchors against the unmutated file only.

### What the kills say about the suite

- **XT03 kills 22 cases.** F5 is not theoretical: a throwing broadcast takes the whole end-session
  hand-over down with it, including *"hands over rather than skipping, because skipping is the worse
  failure"*.
- **XT12 kills 18** across three suites — the latch persisted to `sessionStorage` is caught by T22's
  inventory, T22's namespace accounting, T35's clearing cases and the new suite's own store scan. The
  wrong fix is the best-covered mistake in the task.
- **XT01's seven kills do not include AC11 or AC10**, which is F3 stated as a measurement rather than
  as a worry.
- **XT13 and XT15 are each killed by exactly one case**, AC11's twelve-payload list — the case F3 says
  cannot tell a refusal from a silence. It is simultaneously the most load-bearing and the least
  self-verifying case in the file.
- **XT02 is killed by AC9d alone.** Every case in the new suite injects a channel, so a broadcast that
  silently stops working on the real channel is invisible to all 21 of them. The one case that posts
  through `endSession()` is the only thing holding the production path.

---

## Summary

Thirteen findings, no fixes. The two that would change production code are **F1** (a comment that now
tells its reader this task could not do what it did) and **F5** (a broadcast documented fire-and-forget
that can strand a sign-out between the clear and the hand-over). The two that would change what the
suite *measures* are **F2** (the latch leaks between cases, and the reset is imported and never called)
and **F3** (AC11's twelve-payload list cannot tell a refusal from a silence — and it is the only case in
1 720 that kills the always-true parser). **F13** is the one finding whose fix would make a surviving
mutant die.

Nothing here disputes the design. The mechanism works, the hazard task 35 called unclosable is closed,
and both orderings are asserted. What the review found is four places where the *reasoning* is recorded
in prose that no case holds — which, in a task whose deliverable is largely an argument, is the failure
mode to look for.

## Carried to phase 9

| | |
|---|---|
| **F1** | the superseded paragraph in `signOut.ts` |
| **F5** | `broadcastSignOut()`'s advertised contract |
| **F6** | OQ-T39-7's understated residual |
| **F7** | `quiet()`'s doc comment |
| **F8** | the unindented helpers |
| **F2** | the latch leak — one line in an `afterEach` |
| **F3**, **F4**, **F12**, **F13** | test strengthening, which is phase 10's |

## Open questions, unchanged

**OQ-T39-6** (pinned by AC9c) · **OQ-T39-7** (`use()` returns a sibling token to a `renew()` caller,
and F6 says it is worse than its comment claims) · **OQ-T36-5** (nothing calls `endSession()`) ·
**OQ-T32-4** (the seventh harness copy) · **FU-T37-2** (`routes.tsx:5`).

---

## Phase 8

Independent review — **Kimi 2.7**. Stop here.
