<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review). Findings only; no code changed. -->

# frontend · T43 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Reviewed: the whole phase-6 diff (12 files, 4 new primitives, 1 new feature directory, 6 grown
instruments, 34 new cases) against `04-freeze-task.md` (D1–D17), `spec/frontend/agents.md`, R1, R2, L2, L4,
L6, L11, L12 and the three rules in `app/ui/index.ts`. **Nothing was rewritten.** The harness
(`07-mutation-harness.py`) was built and run after this review; its surprises are appended under
*Measured after the review*.

---

## F1 — `app/ui/index.ts`'s header is now false, and the plan said to grow it

· **Severity:** **Major** (a knowingly false comment in the file that governs the layer)
· **Evidence:** `frontend/src/app/ui/index.ts:7-10` — *"No screen in this application renders a dialog,
dropdown, popover, menu, tooltip, tab set, **form field** or table — the composite controls such a library
exists to make accessible — so installing one now would ship unused code."* Four primitives added by this
task render exactly that. `git diff HEAD~1 -- src/app/ui/index.ts` shows **only the four export lines
changed**; `05-implementation-plan.md`'s Files-to-modify row for this file says *"grow the header's count of
eight/nine and say **why a field composes two elements** (D3)"*, and that half was not done.
· **Assessment:** This paragraph is the one a future task reads before deciding whether to install Radix —
`index.ts:9` names *"MFA enrollment, R11-R13"* as the likely first consumer. It now gives that reader a
false premise. Rule 2's enumeration (`index.ts:17-19`) is also incomplete: it lists `role`, `aria-live`,
`tabIndex`, `type="button"` and the heading level, and does **not** list `type="email"`, `type="password"`,
`type="submit"`, the `htmlFor`/`id` pair or `noValidate` — five contracts this task set inside primitives on
the strength of that rule.
· **Recommendation:** Phase 9. Correct the form-field clause (the layer now has four field primitives and
the Radix question is *narrower*, not answered), extend rule 2's list with the five new inside-set
contracts, and record D3's two-element exception where rule 2 lives rather than only in `PasswordField`'s
header. **This finding must not survive phase 9** — T42's phase 12 found two comments its own brief had
named as falsified, shipped by phase 6 and walked past by two reviews.

## F2 — The submit promise has no rejection handler

· **Severity:** **Major** (correctness, unmeasured and unmeasurable by the harness)
· **Evidence:** `frontend/src/features/account/RegisterScreen.tsx:48-50`:
`void createPublicAuthApi().POST('/accounts', { body: { email, password } }).then(() => setStatus('sent'));`
· **Assessment:** `openapi-fetch` resolves with `{ error }` for an HTTP failure — which is why AC11's 409 and
500 rows pass — but it **rejects** when the transport itself throws, which is what an offline browser, a DNS
failure or a blocked request produces. On that path: the live region stays on *"Creating your account…"*
indefinitely, and the rejection escapes as an unhandled promise rejection. No case covers it, and **no
mutant can**: there is nothing to delete.
· **Recommendation:** Phase 9 decides between (a) attaching a rejection handler that leaves the in-flight
copy in place, so nothing escapes and nothing lies, or (b) a failure state — which is **task 45's** by D7.
What it must not do is treat a rejection as success: that would report an account created when the request
never left the tab. Whichever is chosen needs a case with a throwing transport.

## F3 — `SubmitButton`'s header claims a pair pin that does not exist

· **Severity:** **Major** (a false claim about an instrument, in the file the instrument covers)
· **Evidence:** `frontend/src/app/ui/SubmitButton.tsx:19-21` — *"Both files are pinned to the same token
list by the design-system suite, which is the pair pin task 42 learned to ask for: two literals that happen
to agree are not the same claim as two that must."* `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`'s
`REQUIRED_UTILITIES` holds `ActionButton: ['bg-accent', 'text-text-inverse', 'text-step-0']` and
`SubmitButton: ['bg-accent', 'text-text-inverse', 'text-step-0']` as **two independent rows**. Nothing
asserts they are equal.
· **Assessment:** This is `Banner`'s `max-w-prose` coincidence exactly (T42 phase 8 I9), in a file whose
comment claims the lesson was applied. Change `ActionButton`'s accent and the two controls stop matching
with every case green.
· **Recommendation:** Phase 9: add the pair assertion — `REQUIRED_UTILITIES.SubmitButton` deep-equals
`REQUIRED_UTILITIES.ActionButton`, and the two files' class strings agree on the three tokens — or delete
the sentence. The pin is cheap and is what the comment already promises.

## F4 — AC10's storage case has no control, so it could be vacuous

· **Severity:** **Minor/Major** (instrument)
· **Evidence:** `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:500-516`
spies on `globalThis.localStorage.setItem` and `globalThis.sessionStorage.setItem` and asserts
`not.toHaveBeenCalled()`.
· **Assessment:** A spy installed on the wrong object, or one that failed to intercept, reports *exactly the
same thing* as a journey that writes nothing. The `Object.keys(...)` assertions beside it are real evidence
but weaker: they would miss a write-then-remove. Every spy mistake in this package has had this shape
(`loginChallenge…`'s *"CONTROL: a counter installed after the click sees nothing at all"*).
· **Recommendation:** Phase 9/10: after the journey assertions, write one key through each storage and
assert the spy saw it.

## F5 — D16's empty-string clause is unmeasured

· **Severity:** **Minor**
· **Evidence:** `04-freeze-task.md` D16: *"`'a'.repeat(12)` and `'👍'.repeat(12)` … are both accepted. **The
empty string must not crash the count.**"* The table in the named suite has no empty row, and
`passwordLength.ts:16` claims *"an empty string is 0, a lone surrogate counts as the one code point the
iterator yields"* with nothing asserting either.
· **Assessment:** Task 46 owns `passwordLengthCountsCodePoints` and the general claim, but the freeze froze
this clause for **this** task, and a submit with both fields empty is the most ordinary accidental input
there is.
· **Recommendation:** Phase 10: one case — an empty password submits, `countCodePoints('')` is 0, the field
is marked out of range, and the request still goes.

## F6 — Nothing prevents a second submit while the first is in flight

· **Severity:** **Minor** (undecided by the freeze, so a finding rather than a defect)
· **Evidence:** `RegisterScreen.tsx:43-51` — `submit()` sets `status` to `'submitting'` and nothing reads it
back; `SubmitButton` carries no `aria-disabled` and nothing holds a ref to it.
· **Assessment:** Two clicks send two `POST /accounts`. The server is the authority and answers 202 either
way, so the visitor sees one outcome — but two verification emails is a visible consequence. R2 forbids
client-side *branching and delay*, which an in-flight guard arguably is not; D1 and D7 say nothing about it.
· **Recommendation:** Carry to phase 8 as a question for the owner rather than fixing it here. If it is
taken, the shape is `ActionButton`'s: `aria-disabled` plus a ref, never the `disabled` attribute
(`ActionButton.tsx:5-11` explains why).

## F7 — Two smaller things, recorded so they are not re-found

· **Severity:** **Minor**
· `registrationAccepts…test.tsx:120-128` — the typing helper's `setter?.call(...)` is silently optional. If
`HTMLInputElement.prototype`'s `value` descriptor ever lacked a setter, the helper would no-op and the
failure would surface as an empty request body three cases away. One assertion that the descriptor exists
would localize it.
· `app/ui/Form.tsx:8-10` claims jsdom *"logs 'Not implemented' instead"* of submitting. Plausible but
**unverified** — the harness's D13 row (`preventDefault` removed) is what can say, and the claim should be
corrected or confirmed from that output.
· `app/ui/PasswordField.tsx:15-18` — two lines break the file's wrap discipline (one short, one ~120 chars),
left over from an edit. Cosmetic, but this layer's headers are read as documentation.

## F8 — `EmailField` accepts no `aria-invalid`, and that is correct

· **Severity:** **Positive / pre-empting a review finding**
· **Evidence:** `EmailField.tsx` takes `{ label, value, onChange }`; `PasswordField.tsx` adds
`'aria-invalid'?: boolean`.
· **Assessment:** The asymmetry is deliberate: R1's advisory bound is on the **password**. The spec says
nothing about validating an address in the client, the server is the authority on both, and `noValidate`
(D-2 in phase 6's notes) exists precisely so the browser does not invent a rejection of its own. A field
primitive that accepted a mark nothing computes would be a prop with no caller.
· **Recommendation:** None. Named here so phase 8 does not read it as an oversight.

---

## Checked and clean

| | |
|---|---|
| **Boundary conditions** | 12 and 13 in range, 11 and 7-emoji out; 12 astral code points = 24 UTF-16 units asserted **through the DOM's own value**, not only through the counter |
| **Null-safety** | `countCodePoints` is total and never throws (R1 is advisory — a count that could throw would be a gate) |
| **Module boundaries** | the screen imports nothing from `auth/` (asserted); it reaches the network only through `createPublicAuthApi`; no deep import of a primitive |
| **Enumeration-safety** | one copy for both outcomes (R2); the success sentence is asserted not to contain *already/exists/taken* |
| **Secret handling** | no storage write; the password reaches no element but its own field; `type="password"` pinned on that field |
| **Idempotency** | the request carries exactly what was typed and is sent once per submit (F6 is about two submits, not one) |
| **Transaction boundaries / money types / thread-safety** | no counterpart in a browser bundle |
| **`agents.md`** | no spec file touched; no new route path; no state library; flat catalogue keys |

---

## Measured after the review — the thirteenth harness copy

`python3 -I .ai/prompts/frontend/T43/artifacts/07-mutation-harness.py` → **26 rows, 22/24 regressions
killed, 0 BROKEN, 1 canary killed, 3 survivors, 1 disagreement.** Full output in
`artifacts/07-mutation-run.txt`; baseline 1 failed of **1931**, the documented host `oxlint` case, with
attribution subtracting that case **by name**.

| row | expected | got | |
|---|---|---|---|
| TR01–TR08, TR10–TR20, TR22, TR23 | `own` | **KILLED/own** | every one killed inside this task's files |
| **TR09** copy hardcoded in English | survive | **SURVIVED** | the hole: hardcoded English is byte-for-byte the catalogue's, so no copy assertion can tell a lookup from a literal |
| **TR21** focus moved on mount | survive | **SURVIVED** | D5 has no case; AC9 focuses each control deliberately, so a screen that stole focus on arrival passes every row |
| **TR24** `noValidate` removed | `unfalsifiable` | **SURVIVED** | as predicted, and the limit is jsdom's: it performs no interactive constraint validation |
| **TR25** canary (an 11-code-point row claiming to be in range) | killed | **KILLED/own** | the length table's assertions are awake, not merely generated |
| **TR26** the send rejects | survive | **KILLED/own**, 13 cases | **the disagreement, and the mutant is what was wrong** |

### TR23 confirms phase 6's deviation was the right call

The freeze named the advisory check **unfalsifiable**. With `aria-invalid` consumed, replacing the whole
check with `setAdvisory('in-range')` is caught by every out-of-range row in the table. Had the check been
left with no consumer, this row would have survived and the task would have shipped dead code with a green
harness — which is what D16/D17 were predicting without naming it as a cost.

### TR26 — a mutant that dies can still measure nothing (T36's lesson, again)

TR26 replaced the whole send with a rejecting promise, so **thirteen** cases failed — five of them
*"sends … to the server either way"*, which fail because **no request was sent at all**, not because one
rejected. The row measured a *missing send*, not a *rejected* one: it did not isolate the hole it records.

What this says about **F2** is precise, and it is not what F2 claimed:

- **The suite can see the symptom.** A send that never resolves leaves the live region on
  *"Creating your account…"*, and thirteen cases notice.
- **F2's defect stands on inspection and is unchanged**: production attaches no rejection handler, so an
  offline submit leaves an unhandled rejection and a visitor stuck on the in-flight copy forever.
- **F2's sentence *"no mutant can"* was wrong in its reasoning.** The reachable mutation is in the
  **transport**, which is test-controlled, so a source row cannot isolate it. The instrument that can is a
  case with a throwing transport — **phase 10 owes it**, and TR26 is withdrawn as evidence for F2 rather
  than counted as having measured it.

### What the three survivors oblige

| | |
|---|---|
| **TR09** | phase 10: a source assertion that the screen reaches `t()` for all five strings — the only thing that distinguishes a lookup from an identical literal |
| **TR21** | phase 10: D5's case — mount the screen and assert `document.activeElement` is still `<body>`, the shape task 174 wrote for the opposite claim |
| **TR26** | phase 10: a throwing-transport case, after phase 9 decides F2's fix |
