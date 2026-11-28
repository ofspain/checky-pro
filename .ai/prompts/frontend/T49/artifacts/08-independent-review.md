<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T49 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T49 — Verify failure is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | **35 / 35 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2083 / 2083 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T49/artifacts/07-mutation-harness.py` | **17/17 regressions killed**, 1 canary killed, 2 declared unfalsifiable (TR17, TR20), **0 broken**, **0 disagreements** |

The whole-suite result matches the Phase 7 claim of a fully green 2083/2083 baseline.

---

## Findings

### I1 — D-T49-2 is implemented as ratified: tokenless arrivals render the uniform failure
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:93-95` sets `status` to `'failure'` when token is null or empty. `verifyFailureShowsOneUniformMessage.test.tsx:161-181` asserts the same DOM for tokenless and empty-token arrivals, with no request sent.
· **Assessment:** Confirmed independently. The implementation matches the owner's Phase 4 reversal of Kimi's Phase 3 decision. The comments in both the component and the test explain why.
· **Recommendation:** None.

### I2 — The state machine correctly prevents a flash of failure before success
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:98` sets `setStatus('idle')` before the POST. `verifyFailureShowsOneUniformMessage.test.tsx:297-327` asserts that a valid token in flight shows only the heading, and that an earlier failure is cleared when a good token is in flight.
· **Assessment:** Confirmed. The `idle`-means-in-flight design, combined with the reset line, means a visitor with a valid token never sees failure copy, even for one frame.
· **Recommendation:** None.

### I3 — The response-body source scan is comprehensive
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:475-487` strips comments and forbids `\berror\b`, `\.title\b`, `\.detail\b`, `\.type\b`, `response\.status\b`, `statusText`, `\.json\s*\(`, `\.text\s*\(`.
· **Assessment:** This is the right structural defence for L4. The screen only reads `response.ok`, so a future edit that branches on reason would break the scan.
· **Recommendation:** None.

### I4 — F5 is real: nothing will fail when task 50 attaches the resend element
· **Severity:** **Medium (process)**
· **Evidence:** The resend link target is asserted as `${APP_BASENAME}/${RESEND_VERIFICATION_PATH}` (`verifyFailureShowsOneUniformMessage.test.tsx:253`), but no case asserts that the target currently renders no element or will later render one.
· **Assessment:** The self-review records this correctly. When task 50 attaches an element, the T49 suite will stay green without prompting anyone to re-check that the link still does the right thing. The self-review's recommendation — a positive claim that inverts when the element lands — is sensible.
· **Recommendation:** Address in Phase 10 as planned.

### I5 — D-T49-1 is implemented correctly: routes.tsx gained the constant
· **Severity:** **Positive**
· **Evidence:** `routes.tsx:60` exports `RESEND_VERIFICATION_PATH = 'resend-verification'`, and `VerifyScreen.tsx:5` imports it. The route table still declares the path with no element.
· **Assessment:** The constant removes the literal from the component and tests, consistent with L14 and `SignedOutScreen`'s use of `SIGN_IN_PATH`.
· **Recommendation:** None. Already ratified at Phase 4.

### I6 — D-T49-3 and D-T49-4 need Phase 9 ratification
· **Severity:** **High/Medium (process)**
· **Evidence:**
  - `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:976-991` now lists `verify.failureMessage` and `verify.failureAction` in the catalogue key-set pin (D-T49-3).
  - `catalogue.ts:180-181` uses flat keys `failureMessage`/`failureAction` instead of the Phase 3-designed nested `failure.message`/`failure.action` because the nested shape crashed the copy scan (D-T49-4).
· **Assessment:** Both deviations are documented and reasoned, but they are deviations from the brief / Phase 3 design and need ratification.
· **Recommendation:** Phase 9 should ratify or reverse these.

### I7 — The console spy is wider than T48's
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:518-522` covers `log`, `warn`, `error`, `debug`, `info`, `table`, `dir`, `trace`.
· **Assessment:** This addresses the minor gap noted in T48 Phase 11 (F4). Good completeness.
· **Recommendation:** None.

### I8 — Uniformity cases cover a wider status range than the brief required
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:194-202` tests 400, 401, 403, 404, 409, 410, 429, 500, 503.
· **Assessment:** The brief listed 400, 404, 410, 429, 500. The extra statuses strengthen the uniformity claim, particularly 401/403/409 which could tempt reason-specific handling.
· **Recommendation:** None.

### I9 — The rewritten T48 handoff case avoids the "both empty" trap
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:278-305` keeps the equality assertion but adds a third assertion that both arms contain `en.verify.failureMessage`.
· **Assessment:** This is exactly the right fix. The original case would have stayed green if both arms rendered nothing; the new case fails if the failure branch is deleted.
· **Recommendation:** None.

### I10 — TR16 survival was a harness-authoring defect, not a product gap
· **Severity:** **Observation**
· **Evidence:** Phase 7 F1 explains that the first TR16 mutant logged through an aliased console in the tokenless branch, where `token` is null or `''` by definition, so it could not leak anything.
· **Assessment:** Confirmed. A mutant must isolate the hole it claims to measure. The re-aimed mutant in the failure settle path kills in one case.
· **Recommendation:** None.

### I11 — TR17 is correctly declared unfalsifiable
· **Severity:** **Observation**
· **Evidence:** Phase 7 F2/F3 document that TR17 swaps the path constant for the identical literal. The first run reported a disagreement because the declaration was prose-only; after adding `TR17` to `UNFALSIFIABLE`, it survived as expected.
· **Assessment:** Correct. The constant's value is measured elsewhere (route-set pins and T48 suite via TR18), so a behavioural case here would be redundant.
· **Recommendation:** None.

### I12 — The resend link is correctly placed outside the live region
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:117-123` renders the `StatusRegion` with only the message, and the `TextLink` as a sibling. `verifyFailureShowsOneUniformMessage.test.tsx:378-389` asserts no anchor inside `[role="status"]`.
· **Assessment:** This avoids re-announcing the interactive control whenever the region text changes.
· **Recommendation:** None.

### I13 — Focus management follows OQ-T49-4
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:86-88` focuses the heading unconditionally on mount and never again. `verifyFailureShowsOneUniformMessage.test.tsx:391-402` asserts focus remains on the heading after failure lands.
· **Assessment:** This matches the Phase 3 decision and avoids interrupting a screen-reader user.
· **Recommendation:** None.

---

## Summary

T49's implementation correctly realises the ratified design: a three-state `VerifyScreen` where failure (including tokenless) renders one uniform message and a neutral resend action, with no flash before success and no read of the response body. The named suite is strong (35 cases), the whole suite is green (2083/2083), and type/lint checks are clean.

The only notable open issue is F5: the suite will not prompt re-verification when task 50 attaches the resend element. The self-review correctly records this for Phase 10. The two process deviations (D-T49-3, D-T49-4) are documented and await Phase 9 ratification.

Final verification is clean: **35/35** in the named suite, **2083/2083** in the whole suite, and **17/17 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 8 complete.** No code or contract changed.
