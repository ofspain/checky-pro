<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). Approving principal engineer. -->

# frontend · T44 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | every prior artifact |
| **Sources of truth** | `spec/frontend/requirements.md` (R1), `spec/frontend/design.md`, `spec/frontend/tasks.md` (task 44), `contracts/api/auth.yaml` |
| **Harness, four clean runs** | phase 9 ×2, phase 10, phase 12 — **32/32 killed · 0 broken · 2 canaries killed · 1 expected survivor · 0 disagreements**, and the phase 10 and phase 12 runs are **identical row for row** |

## R1, clause by clause

> *"WHEN a visitor submits the registration form, THEN the SPA SHALL check that the password is 12 to 128
> Unicode code points as advisory guidance only, and SHALL submit to `POST /accounts` through the generated
> client. The server is the authority."*

| clause | holds? | evidence |
|---|---|---|
| *WHEN a visitor **submits***  | yes | `marks nothing before the first submit, because the check is a submit-time one` (T43) — typing three characters marks nothing |
| *12 … code points* | yes, **task 43's** | rows at 11, 12, 13, 12-astral, 7-emoji, empty |
| *…to **128** code points* | **yes, and this is task 44** | rows at 128 ASCII, 128 astral, 129 · `T44 AC2` · the control's upper-bound pins · mutants TR29/TR30/TR31/TR33 |
| *Unicode **code points*** | yes, **at both ends and in both directions** | 12 astral proves the false accept, 128 astral proves the false **reject** — 128 code points are 256 UTF-16 units |
| *as advisory guidance **only*** | yes | every row in the table reaches the transport, including 129; nothing is disabled |
| *SHALL submit to `POST /accounts` through the generated client* | yes | T43's AC5, with TR07 (a hand-written `fetch`) killed |
| *The server is the authority* | yes | no client-side block, no truncation — **AC4 is this clause's new instrument**: a `maxLength` would have let the browser overrule the server before the handler ran |

## Traceability matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R1** (upper bound) | yes — **by T43**, `RegisterScreen.tsx:60` evaluates `length <= ADVISORY_MAXIMUM_CODE_POINTS`; `passwordLength.ts` exports 128 | unchanged by this task | **3 rows + 2 cases + extended control**, 5 mutants | none | none |
| **R1** (code points, upper end) | yes | `countCodePoints` = `[...value].length` | `T44 AC2`: `value.length === 256` while the count is 128 | none | none |
| **R1** (advisory, not blocking) | yes | the 129 row is marked **and sent** | table `it.each` | none | none |
| **R1** (no client-side cap) | yes — **new in this task** | no primitive carries `maxLength`/`minLength` | scan over `PRIMITIVE_FILES` + rendered pin + a control each | none | none |
| **L12** (phase additivity) | yes | rows added, nothing restructured | the whole suite stayed green throughout | none | none |
| **L11** (catalogue-only copy) | yes | the `register` group is still six keys | T43's AC6 scan | none | none |
| **L6 / L4 / L3 / L2 / L14** | yes | no new call, branch, store, credential surface or route | T43's instruments, all still green | none | none |
| **task 45** (`…rejects…`) | not this task | — | the 11 and 129 rows it needs now exist | by design | none |
| **task 46** (`passwordLengthCountsCodePoints`) | not this task | — | the counter is untouched | by design | none |

**`design.md` is silent on password length** — it carries no mention of code points, 128, `maxLength` or the
word *advisory*. R1 and the contract are therefore the only authorities for this task, which is why phase 12
read both rather than only the brief.

## Two things the sources say that the brief did not

**1. The task statement says "128 **characters**"; R1 says "code points".** `tasks.md:78` reads *"A
128-character password is accepted client-side as advisory"*. Those two words agree only because the astral
row exists: 128 emoji are **128 characters, 128 code points and 256 UTF-16 units**, and an implementation
that counted units would accept the 128 ASCII row while rejecting the 128-character emoji one. The row the
freeze added beyond T43's prediction is precisely what makes the task statement's looser word true.

**2. The contract caps `email` at 254 and `password` at nothing** (`contracts/api/auth.yaml:604-609`). Two
consequences, both deliberate and both recorded here rather than silently:

- The uncapped `password` is why R1's check is *advisory by construction*: there is no contract bound for a
  client to enforce, so a client that blocked would be inventing a rule.
- **This task's new scan now forbids on `EmailField` an attribute the contract has a number for.** No
  requirement asks the client to enforce 254, and silently truncating an address is worse than a `400` —
  but a later task that wants client-side email capping must now change this scan deliberately, which is
  exactly the signal the scan exists to produce. Logged as **OQ-T44-1** for the owner, not as a deviation.

## The approving principal engineer's answers

**(1) Is the task fully complete?** Yes. The named test
`registrationAcceptsTwelveAndOneTwentyEightCodePoints` is complete for the first time: task 43 owned the 12,
task 44 owns the 128, and the file header no longer describes its own name as half a promise.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC5, each mapped in the phase 10
manifest to the test that holds it and the mutant that proves the test is alive. The claim that matters
most is AC4's, and it is the one the whole task turned on: **TR32 and TR35 each die in exactly the two
structural pins and nowhere else**, which means no behavioural case in a 1946-case suite can see a length
cap on a field. Phase 3 asserted the opposite; the freeze measured it; the harness settled it.

**(3) Does it violate any LOCKED decision?** No. L12 most of all: not one production file changed, the
table was extended rather than restructured, and the counter and screen were read but never written.

**(4) Remaining risks.**

| | |
|---|---|
| **FU-T42-1** | Now **diagnosed** rather than merely sighted: load-sensitive, reproducing only with the local Docker stack running (9 clean runs quiet, 3 flaky of 7 under load), always in the sign-in cases that count hand-overs and storage writes. What is still unknown is whether the fault is a real race in the code those cases cover or an over-tight assertion in the cases. **Open, with a question attached.** |
| **Standing rule produced** | The harness runs on a quiet machine. Both phase 9 refusals were the guard working, not the harness failing. |
| **TR24** | Survives, as the freeze predicted: jsdom performs no interactive constraint validation, so `noValidate` has no observable effect here. It would be the first mutant to re-run under a real browser. |
| **Two diagonal AC4 combinations** | `EmailField`+`maxLength` and `PasswordField`+`minLength` have no mutant of their own. Both axes are proven live by TR32 and TR35 through the same loop and the same regex pair, so the residual risk is a per-file typo rather than a dead instrument. |
| **OQ-T44-1** | the `EmailField` 254 question above. |
| **`/app/verify` is a blank page** | Found while rehearsing the demo, not by this task: the route is declared with no element until task 48, so a verification link renders nothing instead of falling through to the catch-all. Outside this task's scope; recorded so task 48 meets it. |

## Counts, stated exactly

| | |
|---|---|
| named suite | **45** cases (T43's 40 + 3 rows + 2 cases; phase 10's probe is assertions **inside** the existing AC4 case, not a new case) |
| `screensRenderThroughDesignSystemPrimitivesOnly` | **65** cases (+1 scan, +1 control) |
| whole suite | **1945 / 1946**, the known environmental lint-script failure |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |

*(An earlier message of mine said the named suite was 46. It is 45 — the eleventh case of the pair went to
the design-system file.)*

---

**PASS** — R1's upper bound is now falsifiable at both ends and in both counting directions, every AC is
held by a test whose liveness a mutant proves, and the one behaviour no test could see was found by
measurement and pinned by two instruments that did not exist this morning.
