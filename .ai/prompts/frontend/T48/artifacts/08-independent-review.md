<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T48 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | **26 / 26 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2042 / 2042 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T48/artifacts/07-mutation-harness.py` | **15/17 regressions killed**, 2 canaries killed, 1 unfalsifiable (TR20), 2 expected survivors (TR05, TR10), **0 broken**, **2 disagreements** (TR05, TR10) |

The whole-suite result matches the Phase 7 claim of a fully green 2042/2042 baseline.

**Harness run note:** The first invocation refused to run, reporting two unexpected baseline failures in `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` ("adds no second hand-over..." and "CONTROL: a counter installed after the click sees nothing at all"). The same file passed when run alone (53/53). A second harness invocation ran with a clean baseline and produced the results above. This is the FU-T42-1 intermittent-flake pattern documented in prior tasks.

---

## Findings

### I1 — TR05 survives: the guard's semantics are claimed but unmeasured
· **Severity:** **Medium**
· **Evidence:** `frontend/src/features/account/VerifyScreen.tsx:68-71` sets `handled.current = true` **after** the empty-token return. The screen's header comment explicitly states the guard means *"a request has been made"* and warns against latching on a tokenless arrival. Harness row TR05 survived (0 failed of 2042), and the self-review reports it as a declared survivor.
· **Assessment:** Confirmed independently. The current cases assert no request is sent when token is absent, and one request is sent when token is present, but no case moves from the first state to the second on the same mounted screen. A regression that moved `handled.current = true` above the empty-token check would make a later params change carrying a real token do nothing — exactly the bug the comment describes.
· **Recommendation:** Same as self-review F1: add a case that arrives with no token, then navigates to the same route with a token, and asserts the request is then sent. This measures the semantics the comment claims.

### I2 — TR10 survives: `response.ok` is chosen but not held
· **Severity:** **Medium**
· **Evidence:** `frontend/src/features/account/VerifyScreen.tsx:77-78` uses `response.ok` rather than `response.status === 204`. The case at `verifyFromLinkShowsSuccessState.test.tsx:271-283` mocks a 200 with a body and asserts the body is **not** rendered, but it does **not** assert the success text **is** rendered. Harness row TR10 survived (0 failed of 2042).
· **Assessment:** Confirmed. The contract declares only 204, so `response.ok` is defensible, but it is a deliberate choice with no instrument. A regression to `=== 204` would leave a 200 visitor on `idle` (task 49's future failure state) even though the server signalled success.
· **Recommendation:** Same as self-review F2: in the existing 200 mock case, add an assertion that `en.verify.success` is rendered. This documents and holds the `response.ok` choice.

### I3 — D-T48-1: five test files touched, one forbidden
· **Severity:** **High (process)**
· **Evidence:** The Phase 2 brief listed `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` under *Files NOT to Modify*. The self-review reports it was modified for count/union pins, the second consecutive task to do so (T47 was the first).
· **Assessment:** Confirmed by reading the self-review and the brief. The changes are numbers and assertion messages; no R1 claim moved. Still, it is outside the approved scope and needs ratification.
· **Recommendation:** Phase 9 should ratify or reverse the change, or refactor so that closed-task files are not edited incidentally.

### I4 — D-T48-2: the `+`, `%`, `/` round-trip requirement was reinterpreted
· **Severity:** **Medium (scope)**
· **Evidence:** The Phase 2 brief required a test for a token containing `+`, `%`, and `/`. The actual test file uses a base64url token (`[-_]`) and a latent-trap case showing `+` decodes to a space. The self-review explains the server issues URL-safe base64 and documents the deviation.
· **Assessment:** This is a reasoned scope change, but it is a change from the brief and should be ratified. The latent-trap case is a good substitute for the original requirement.
· **Recommendation:** Ratify in Phase 9, or restore a literal `+`, `%`, `/` case if the owner insists on the original wording.

### I5 — R-T48-1 is documented as an accepted product/security risk
· **Severity:** **Observation**
· **Evidence:** `VerifyScreen.tsx:44-54` records that POST-on-arrival allows JavaScript-executing gateways to consume the token before the human clicks.
· **Assessment:** The risk is real and recorded. The owner chose on-arrival at Phase 4 because R3's wording compels it. This is not a finding against the implementation; it is a documented decision.
· **Recommendation:** None.

### I6 — The `idle` state deliberately collapses three situations
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:232-250` ("PINS A LIMIT") asserts that a tokenless arrival and a rejected-token arrival render identically, while also asserting the rejected arm sent a request.
· **Assessment:** This is the right design for the handoff to task 49. The case is written to fail when task 49 lands, which prevents the uniform-failure work from being closed silently.
· **Recommendation:** None.

### I7 — The source scan deliberately omits `useNavigate`
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:391-403` explains why `useNavigate` is not in the forbidden list: task 49's neutral resend action may need it.
· **Assessment:** This avoids boxing in a future task, which is the opposite mistake T47's AC7 count pin made. Good forward-looking design.
· **Recommendation:** None.

### I8 — Focus and verification are correctly separate effects
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:63-65` focuses unconditionally; lines 67-80 verify conditionally. The test at `verifyFromLinkShowsSuccessState.test.tsx:299-307` asserts focus moves even with no token.
· **Assessment:** This separation is correct and tested. It prevents a future edit from making focus depend on token presence.
· **Recommendation:** None.

### I9 — The token alphabet trap is well-pinned
· **Severity:** **Positive**
· **Evidence:** The round-trip case asserts `[-_]` characters survive, and the latent-trap case documents that `+` would decode to a space.
· **Assessment:** This is a strong defensive case for a server-side encoding change.
· **Recommendation:** None.

### I10 — The guard-order source scan is literal but functional
· **Severity:** **Minor**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:195-206` uses `String.indexOf` on comment-stripped source to check that `handled.current = true` appears before `.POST(`.
· **Assessment:** The assertion is precise for the current code shape but will break if the code is reformatted (e.g., helper function extracted, async/await, or renamed variables). The failure would be loud, which is acceptable.
· **Recommendation:** Optional: make the assertion more robust by parsing the AST or asserting a behavioural property directly (e.g., the TR05 case from I1).

### I11 — TR20 remains unfalsifiable
· **Severity:** **Observation**
· **Evidence:** The self-review reports TR20 (`useEffect` → `useLayoutEffect`) survived as declared, with no observable effect.
· **Assessment:** Correct. A case that killed it would be pinning the hook choice rather than behaviour.
· **Recommendation:** None.

---

## Summary

T48's implementation correctly realises the Phase 3 design: a shared `VerifyScreen` that POSTs the `?token` on arrival, shows catalogue copy on success, and leaves failure states for task 49. The whole suite is green (2042/2042) and the two canaries died, so the new instruments are not inert.

The two Medium gaps (F1/F2) are confirmed independently and are each backed by a declared harness survivor. The two process/scope findings (F3/F4) need Phase 9 ratification. Everything else is either a positive observation or a minor robustness note.

Final verification is clean: **26/26** in the named suite, **2042/2042** in the whole suite, and **15/17 regressions killed** in the harness with no broken rows. The first harness attempt was blocked by the known FU-T42-1 flake; the second attempt ran cleanly.

**Phase 8 complete.** No code or contract changed.
