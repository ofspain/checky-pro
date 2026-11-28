<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T51 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | every prior artifact; `spec/frontend/{requirements,design,tasks}.md`, `agents.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2137 / 2137  (41 files)  ·  lint 0  ·  typecheck 0  ·  named suite 51 cases
harness: 6/6 regressions · canary dead · 0 survivors · 0 broken · 0 disagreements
3 files changed over the task
```

---

## Requirement traceability

| Requirement | Implemented? | Evidence | Test? | Deviation? |
|---|---|---|---|---|
| **R5**, *"SHALL show the same acknowledgement **regardless of outcome**"* (`requirements.md:18-20`) | **Yes — and it was NOT before this task.** `ResendVerificationScreen.tsx` now passes `parseAs: 'stream'`, so `openapi-fetch` no longer parses a body this screen never reads | **15 arms**, each comparing `innerHTML` to a captured, guarded baseline; focus and the live region compared as whole lists; the whole location per arm | **Yes** — 4 ratified at Phase 4, **1 retired at Phase 7** |
| **R5**'s first two clauses | T50's, undisturbed | 51-case suite green | — |

**R5's third sentence remains stale** (`package.md` Q12(a)): backend R6's own text was the wrong half.
Recorded, not edited.

## LOCKED decisions

| ID | Verdict |
|---|---|
| **L4** *"Copy, redirects, styling, and timing are identical…"* | **HELD, and this task is what made it true.** Copy and **styling** by `innerHTML` equality across 15 arms — a class that varied by outcome is as much a channel as a word. **Redirects** by the whole location per arm. **Timing** by the tree-wide timer scan whose owner is now named here (D-T51-3 retired). **Plus two channels `innerHTML` cannot carry** — focus and the live region — which is D-T51-2. |
| **L11** | **HELD** — focus and the region are now proven identical across every outcome, which makes them enumeration-safe and not merely present. |
| **L6** generated client only | **HELD.** `parseAs` is an option *of* the generated client; the scan forbidding a bare `fetch(` is untouched. |
| **L16, L2/L5, L12, L14, D7** | **HELD**, all T50's instruments still green; T51 adds no key, no route, no render site. |
| **L4's carve-out** | Does not reach a public form. |

## Acceptance criteria

All thirteen **PASS**. The three worth naming:

| AC | |
|---|---|
| **AC2/AC3** | 15 arms, **and the baseline is captured then guarded three ways** — longer than 100 chars, text equal to `ACK_TEXT`, no `<form` — because a uniformly broken screen satisfies every comparison otherwise. |
| **AC7/AC8** | The two channels serialised markup cannot see. **TR24 kills them, which means they detect the defect independently of the 15 arms.** |
| **AC10** | **Ten of fifteen arms**, and the number is now a case naming the five that genuinely cannot carry server words. |

## Deviations

| ID | Status |
|---|---|
| **D-T51-1** | **Ratified at Phase 4.** Kimi's TR26 could not die; replaced. |
| **D-T51-2** | **Ratified at Phase 4**, and vindicated by measurement: two of TR24's five kills. |
| **D-T51-3** | **RETIRED at Phase 7**, not ratified. The clause was already held tree-wide; mine was a weaker duplicate, and I had reached the gate by correcting one false claim with another. |
| **D-T51-4** | **Ratified at Phase 4**, then **strengthened** when I found the pathname-only form unkillable. |

**Phase 9 had nothing to ratify — the second clean gate running.**

---

## Comment audit

### One false comment, and it was the task's own premise

`ResendVerificationScreen.tsx` said:

> **The only way this screen could leak which of those happened is by looking**, so it does not: the settle
> handler takes no argument.

**T51 disproved exactly that.** The screen leaked **without looking** — `openapi-fetch` parsed the body on its
own behalf, so a reply that did not parse took the reject arm and rendered a different screen, and *which
reply parses* is a property of the body the server chose to send.

This is the most load-bearing comment in the file: it is the **rationale for the source scan**, and it
asserted a completeness that was false. Corrected to record what happened — *not looking was necessary and
not sufficient, because a dependency can arrive through a library the source never names.*

**Everything else is accurate**, including `VerifyScreen.tsx`'s comment, which Phase 9 already corrected with
its measured table.

## Kimi's Phase 11 — disposition

**Nine findings, all positive or observational. No gaps, no corrections.** Every number reproduced: 51/51,
2137/2137, 6/6, 0 survivors, 0 broken, 0 disagreements. **F4 independently accepted the temporal argument**
for declining I10 and the cross-arm half of I11; **F7** correctly characterises the wordless-arm case as a
structural pin with TR30 as its behavioural complement.

**Again no false ratification claim** — and again I will not credit the generator fix, because **Kimi's header
still reads `Consumes: artifacts/10-test-generation.md`** where the prompt reads `09-review-resolution.md,
10-test-generation.md`, and D-T51-3 was *retired* rather than left outstanding, so there was nothing to
misreport. **FU-T49-1 remains shipped and unproven, three tasks running.**

## Principal engineer's answers

**1 · Complete?** Yes. R5's uniformity clause is implemented and held across fifteen outcomes, and the defect
it fixes was shipped by T50 and invisible to 2116 tests.

**2 · Every AC?** Yes, all thirteen.

**3 · Any LOCKED violation?** No — and **L4 is now satisfied where it previously was not**, which is the
substance of the task rather than a side effect.

**4 · Remaining risks.**

| Risk | Reading |
|---|---|
| **OQ-T51-6 — a measured bug on another screen** | `VerifyScreen` tells a visitor whose verification **succeeded** that their link is invalid. **Now scheduled as task 175**, with the measurement written into that screen's source so it cannot be lost. Kimi's F8 agrees it is the highest-value follow-up. |
| **A fourth screen can reintroduce this** | Three screens now need to remember `parseAs`. **There is no scan.** Task 175's `OQ-T175-4` asks whether there should be, and I think there should. |
| **OQ-T51-7** | The body stream is unconsumed. |
| **OQ-T51-8** | **The error inverts** — an edge interstitial now says *"Check your email"*. The posture L4 already chose, and the owner's to revisit. |
| **TR26 is non-isolating** | Recorded twice (Phase 7 F2, Kimi I6). The differing-email case states a claim it does not uniquely hold. |
| **FU-T43-2** | `teardownCurrent` second copy, `type` fourth. |

---

**PASS** — R5's remaining clause is implemented and measured across fifteen outcomes, all thirteen ACs hold at
2137/2137 with 6/6 mutants killed, no LOCKED decision is violated, four deviations were ratified before
implementation and a fifth was retired by measurement, and the one false comment found was the task's own
premise.
