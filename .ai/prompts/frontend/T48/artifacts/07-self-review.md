<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). -->

# frontend · T48 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

```
baseline whole suite: 0 failed of 2042, exit 0
baseline failing cases (excluded from every attribution below): []

regressions: 15/17 killed
canaries:    ['TR18', 'TR19'] - none survived
survivors:   ['TR05', 'TR10', 'TR20']
broken:      none
disagreements: ['TR05', 'TR10']
```

**Every survivor was declared before the run.** TR05 and TR10 were written into the harness docstring as
rows that might survive, with the reason; TR20 as unfalsifiable. That does not make them acceptable — two
are real gaps, below — but it does mean the suite's strength is not being reported as better than it is.

## The aiming is tight

Nine of the seventeen regressions die by **one or two cases**, which is what a well-aimed row looks like:

| Row | Cases | What caught it |
|---|---|---|
| TR14 StatusRegion mounted only on success | **1** | The announcement case — and it is the only thing in 2042 that can see it, because the **words are identical** in both versions and only the moment the region appears differs. |
| TR17 focus only when a token is present | **1** | "moves focus even when the link carried no token" — the case that exists because focus and verification are two effects. |
| TR11 a bearer on a `security: []` call | **1** | The no-Authorization case. |
| TR09 the response ignored | **1** | The *PINS A LIMIT* case, which turns out to do double duty: it pins the limit **and** is the only row that notices success being shown unconditionally. |
| TR07 an empty token posted | **1** | AC12's empty-token case. |
| TR03 the guard set after the request | **2** | The StrictMode case and the source-order case — both halves, as intended. |
| TR06 the wrong parameter name | 8 | |
| TR13 `VERIFY_PATH` respelled | 9 | |
| TR12 the attach branch removed | 17 | |

Both canaries died, so neither instrument is inert: the *PINS A LIMIT* case fails when its two arms are made
genuinely different (TR18), and the storage control fails when switched to an **instance** spy (TR19) — T42's
exact bug, now provably caught here.

---

## Findings

### F1 — TR05 survives, and the screen's own comment claims the property nothing measures
· **Severity: Medium.** · **Row:** TR05, `0 failed of 2042`.

Moving `handled.current = true` **above** the empty-token return changes nothing any case can see. The
consequence is that a tokenless arrival latches the guard, so if `params` later carried a real token to the
same mounted screen, no request would be sent.

What makes this more than a theoretical row is that **`VerifyScreen.tsx`'s header asserts the property
directly** — *"the guard exists to mean a request has been made, not this effect has run"* — and the
implementation plan singled it out as *"a decision, not a formality"*. So this is a comment making a claim
with no instrument behind it, which is precisely the defect Phase 12 has caught in three consecutive tasks.

**Recommendation:** a case that arrives with no token, navigates to the same route *with* one, and asserts a
request is then sent. That measures the semantics the comment claims.

### F2 — TR10 survives: nothing proves `response.ok` was the right choice
· **Severity: Medium.** · **Row:** TR10, `0 failed of 2042`.

Narrowing `response.ok` to `response.status === 204` is invisible, because **no case mocks a non-204 success**.
The contract declares only 204, so this is not a contract violation either way — but the code chose the
broader test deliberately and nothing holds the choice.

It matters in the direction that hurts: with `=== 204`, a server answering `200` would leave the visitor on
`idle` — which, after task 49, is the uniform **failure** message — for a verification that actually
succeeded.

**Recommendation:** the existing "renders nothing the server sent" case already mocks a `200`. Adding one
assertion there, that the success sentence *is* shown, kills TR10 and documents the decision, at the cost of
a single line.

### F3 — D-T48-1 needs ratification: five test files, one of them forbidden
· **Severity: High (process).**

Three files pin `RENDER_SITES`' length and the union **as source text**, and one is
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` — a T43 suite the freeze listed under *Files
NOT to Modify*. Numbers and assertion messages only; no claim changed. **Second consecutive task to break
that same line**, which argues the list should be derived from a probe rather than written from reading — and
even Phase 5's probe found only three of the five, because the other two become reachable only once
`RENDER_SITES` actually changes.

### F4 — D-T48-2: a required test was aimed at characters the server cannot issue
· **Severity: Medium (scope).**

The freeze required a round-trip case for a token containing `+`, `%` and `/`. `generateRawToken` is
`Base64.getUrlEncoder().withoutPadding()`, so the alphabet is `A-Za-z0-9-_`. Replaced by a real base64url
round trip and a latent-trap case pinning that `+` decodes to a **space**. Needs ratification as a scope
change.

### F5 — R-T48-1 is unchanged and remains the task's largest real-world risk
· **Severity: recorded, owner-decided.**

A JavaScript-executing email gateway burns the token before the human clicks; the account verifies and the
human is shown task 49's uniform failure, which R4 forbids task 49 from explaining. Owner chose
POST-on-arrival at Phase 4 because R3 is unambiguous. The mitigation's entry point is documented in the
screen.

### F6 — `idle` is one rendering for three situations, and only one of them is pinned
· **Severity: Low (by design).**

AC12 pins the no-token branch by its consequence (no request sent). The in-flight and failed branches are
not distinguished, deliberately, because R4 is task 49's. The *PINS A LIMIT* case asserts a rejected token
and a tokenless arrival render identically **and** that the rejected arm really sent a request, so the
comparison is not vacuous. It must fail when task 49 lands.

### F7 — the announcement is held by exactly one case
· **Severity: Low (observation).**

TR14 dies by one case and nothing else in 2042 can see it, because the mutant keeps the words and changes
only when the live region is mounted. That is correct aiming rather than fragility — but it means the
announcement property has a single point of failure, and anyone deleting that case should know it is the only
thing standing between this screen and a silent success state.

### F8 — TR20 survived as declared
· **Severity: none.** `useEffect` → `useLayoutEffect` has no observable effect; a kill would mean a case had
started pinning the hook rather than the behaviour.

---

## State

| | |
|---|---|
| Suite | **2042 / 2042**, 39 files |
| `lint` / `typecheck` | 0 / 0 |
| Harness | 15/17 regressions, 2/2 canaries dead, 3 declared survivors, **0 broken** |
| Per-file runs | all seven touched files green alone, no `Errors` |
| Needs ratification | **D-T48-1** (F3), **D-T48-2** (F4) |
| Recommended fixes | **F1** (a case for the guard's semantics), **F2** (one assertion on the 200 case) |

**Phase 7 complete. Next is Phase 8 — Independent Review (Kimi 2.7). STOP HERE.**
