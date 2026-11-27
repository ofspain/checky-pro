<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T48 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `09-mutation-run.txt` |
| **Status** | **COMPLETE** — owner-approved 2026-11-28: ratify both deviations, apply F1 and F2. |

```
baseline whole suite: 0 failed of 2043, exit 0

regressions: 17/17 killed
canaries:    ['TR18', 'TR19'] - none survived
survivors:   ['TR20']
broken:      none
disagreements: none
```

**17/17, up from 15/17, with no disagreements and the only survivor the row written to survive.**

---

## Kimi's Phase 8 — disposition

Kimi re-ran the suite, `tsc`, `oxlint` **and the harness**, and reproduced Phase 7 row for row. Its first
harness attempt was **refused by the baseline guard**, reporting two failures in
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` that passed 53/53 when that file ran alone —
the FU-T42-1 flake. That is worth recording as a result in its own right: **the guard did the job it exists
for**, on an independent machine, rather than letting a flaky baseline be attributed to a mutant.

| Finding | Severity | Disposition |
|---|---|---|
| **I1** TR05 survives; the guard's semantics are claimed but unmeasured | Medium | **ACCEPTED — FIXED.** Identical to F1. |
| **I2** TR10 survives; `response.ok` chosen but not held | Medium | **ACCEPTED — FIXED.** Identical to F2. |
| **I3** five test files, one forbidden (**D-T48-1**) | High (process) | **RATIFIED by the owner.** Kimi's third option — "refactor so that closed-task files are not edited incidentally" — is the right long-term answer and is **not available inside this task**: the pins read another file's bytes, so they cannot pass unless the file holding them is edited or `RENDER_SITES` never grows, and the latter makes AC10 unenforceable. Carried as **FU-T48-1**. |
| **I4** the `+`/`%`/`/` test reinterpreted (**D-T48-2**) | Medium (scope) | **RATIFIED.** Kimi offered restoring a literal case "if the owner insists on the original wording"; declined, because the characters cannot occur — `generateRawToken` is `Base64.getUrlEncoder().withoutPadding()` — and a case aimed at an impossible input measures nothing. The latent-trap case is what carries the intent. |
| **I5** R-T48-1 is a documented accepted risk | Observation | Noted; unchanged. |
| **I6** `idle` collapses three situations, pinned for the handoff | Positive | Noted. |
| **I7** the source scan deliberately omits `useNavigate` | Positive | Noted. |
| **I8** focus and verification are separate effects | Positive | Noted. |
| **I9** the token-alphabet trap is well-pinned | Positive | Noted. |
| **I10** the guard-order scan is literal and brittle | Minor | **ACCEPTED, and answered by I1's own fix.** See below. |
| **I11** TR20 remains unfalsifiable | Observation | Noted. |

## F1 / I1 — the fix the screen's own comment had been asking for

`VerifyScreen.tsx`'s header states the guard means *"a request has been made, not this effect has run."*
Nothing measured that, because **every existing case started in its final state** — either with a token or
without one — and the difference only shows on a screen that goes from one to the other **without
remounting**. That is why TR05 survived all 2042 cases.

The new case navigates to the same path with a different query string, which keeps the route matched so
React Router re-renders the mounted screen with new `params`. Measured with TR05 applied by hand before
trusting the harness: it fails with *"the guard latched on the tokenless arrival, so the real token was never
sent"*, 1 of 27.

## F2 / I2 — one assertion, and the direction is why it matters

The case that mocks a `200` asserted only what must **not** appear. It now also asserts the success sentence
**does**. With `=== 204`, a server answering `200` would leave the visitor on `idle` — which after task 49
*is* the uniform failure message — for a verification that actually succeeded. Measured with TR10 applied:
fails with *"a 200 did not reach the success state"*, 1 of 27.

## I10 — accepted, and the fix for I1 is its answer

Kimi rated the source-order scan acceptable but brittle, and suggested *"asserting a behavioural property
directly (e.g., the TR05 case from I1)"*. That case now exists, so **both are kept and the comment says
why**: they fail for different reasons. The scan fails when the **order** changes; the new case fails when
the guard's **meaning** does; the StrictMode case fails when the guard disappears. Three cases, three
distinct failure modes, one of which no behavioural test can reach.

## Final state

| | |
|---|---|
| Suite | **2043 / 2043**, 39 files |
| `lint` / `typecheck` | 0 / 0 |
| Harness | **17/17 regressions**, 2/2 canaries dead, 1 declared survivor, 0 broken, **no disagreements** |
| Phase 7 | F1 ✅ F2 ✅ F3/F4 ratified · F5–F8 recorded |
| Phase 8 | I1 ✅ I2 ✅ I3/I4 ratified · I10 ✅ · I5–I9, I11 noted |

## Carried forward

| ID | |
|---|---|
| **FU-T48-1** | Kimi's I3 proposal: stop closed-task files being edited incidentally for count and union pins. Two consecutive tasks have broken the same *Files NOT to Modify* line. A process change, beyond this task — and related to **FU-T47-2**. |
| **R-T48-1** | The gateway hazard; owner-decided, documented in the screen. |
| **OQ-T48-3** | Resolved by design: no token ⇒ no request, pinned by AC12. |
| **OQ-T48-4** | `auth.yaml` declares no failure response for `verifyEmail`. **Blocks task 49.** |
| **OQ-T48-5** | The heading still reads "Verify email" after verification. Copy; owner's call. |

**Phase 9 complete. Next is Phase 10 — Test Generation.**
