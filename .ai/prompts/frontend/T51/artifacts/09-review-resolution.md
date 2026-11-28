<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution). -->

# frontend · T51 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

```
suite 2133 / 2133   lint 0   typecheck 0
```

---

## Nothing to ratify — **the second clean gate in two tasks**

**All four deviations were ratified at Phase 4, before implementation**, and **one was retired during Phase 7
by measurement** (`D-T51-3`, below). No deviation reached this gate unratified.

That is now twice running, against T47, T48 and T49 each arriving here with deviations found mid-flight. The
cause is the same both times: **the deviations were put in writing before any code was changed, each with the
measurement that forced it.**

## Kimi's Phase 8 — thirteen findings, disposition

| | |
|---|---|
| **I1–I5, I7, I13** | **Accepted.** Every number reproduced independently: 47/47, 2133/2133, `tsc` 0, `oxlint` 0, harness 4/4 + canary, 0 survivors, 0 broken, 0 disagreements. |
| **I6** | **Accepted**, and it is Phase 7's own F2: TR26 is real and **non-isolating**. Kimi's phrasing is the one to keep — *"it should not be counted as a new isolated instrument."* |
| **I8, I9, I10, I11** | **Accepted as Phase 10 candidates**, and all four are Phase 7's own F7. **I9 is the one I will act on**: AC10 is held by **nine arms, not thirteen**, and a stated count beats an implied one. |
| **I12** | **Accepted, verified by measurement, and it is the finding of this phase** — below. |

**No finding was declined**, and none was wrong. Three tasks running, Kimi's Phase 8 numbers have matched
mine exactly; the value this time was in **I12's reasoning**, not in its arithmetic.

## I12 verified — and the other screen is worse

Kimi reasoned that `VerifyScreen` *"sets failure on non-ok; a non-JSON 200 would be treated as success (since
`response.ok` is true) but the promise would reject, so it would show failure instead."*

**I measured it, with a valid token:**

```
204, no body (the declared success)  -> "Your email has been verified."
200, valid JSON                      -> "Your email has been verified."
200, HTML body (an edge error page)  -> "This verification link is not valid."   ← WRONG
200, JSON truncated mid-document     -> "This verification link is not valid."   ← WRONG
```

**Kimi's reasoning was exactly right.** A visitor whose verification **succeeded** is told their link is not
valid, and R4's uniform failure then offers them a resend — **a link they do not need**, for an account that
is already verified.

**This is worse than the defect T51 fixed**, for three reasons: it is on the demo path the owner has
rehearsed; the remedy offered is useless; and the resend bug merely withheld reassurance, while this one
actively tells a visitor a true thing is false.

### It is NOT fixed here, deliberately

`tasks.md` gives T51 the resend acknowledgement. `VerifyScreen` is R3/R4, and a fix there has to decide
**what R4's *"fails for any reason"* should mean when the failure is the body rather than the token** — which
is a requirements question, not a one-line change. Widening T51 to cover it would be deciding that question
without a gate.

**What I did instead:** `VerifyScreen.tsx`'s own comment claimed *"the rejection arm below sees transport
failures only."* **That is false, and T51 is what falsified it.** The comment now carries the correction and
the measured table above, so the next reader inherits the finding rather than rediscovering it.
`RegisterScreen` has the same shape — it navigates on resolve, so a non-JSON body leaves the form hanging —
and is named in **OQ-T51-6** alongside it.

## D-T51-3, retired rather than ratified

Phase 4 ratified two timer patterns on the local scan. **Phase 7's harness retired them**: a tree-wide timer
scan with a pinned allow-list already owns the clause, mine was two patterns over one file, and I had reached
Phase 4 by **correcting one false claim with another**. The patterns are gone, the owner is named where they
were, and `TR27` is retagged `inherited`.

**Recording it as retired, not as ratified**, because the brief froze something that should not have been
built, and a gate that only ever confirms is not a gate.

## A probe column that measured nothing, again

The `VerifyScreen` probe printed `SUCCESS=` and `FAILURE=` booleans alongside the raw text. **Both read `n`
on every arm, including the two that plainly succeeded** — I had guessed catalogue key names
(`verify.successHeading`, `verify.failureHeading`) that do not exist, so both columns compared against
`'@@@'` and were vacuous from birth.

**The finding came entirely from the raw-text column I printed beside them.** Phase 2 of this task recorded
*a probe needs a control as much as a case does*; this is the same lesson in its other form — **a column that
reads the same on every row is not evidence, it is a constant.**

## Phase 10 plan

| From | Work |
|---|---|
| **I9** | Give every arm a distinctive `serverWords`, or state why four cannot — the count becomes thirteen or the limit is written down. |
| **I10** | Compare the live region **mid-flight** across arms, not only at rest. |
| **I11** | Compare the **request** across arms, so uniformity covers what is sent and not only what is rendered. |
| **I8** | A `3xx` arm **if a practical stub exists**; `fetch` follows redirects itself, so this may be unmeasurable here — and if it is, that gets written down instead of attempted silently. |

---

**Phase 9 complete — PASS. Next is Phase 10 — Test Generation.**
