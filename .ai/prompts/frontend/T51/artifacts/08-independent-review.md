<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T51 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T51 — Resend acknowledgement is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | **47 / 47 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2133 / 2133 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T51/artifacts/07-mutation-harness.py` | **4/4 regressions killed**, 1 canary killed, **0 survivors**, **0 broken**, **0 disagreements** |

The whole-suite result matches the Phase 7 claim of a fully green 2133/2133 baseline.

---

## Findings

### I1 — The production fix is in place
· **Severity:** **Positive**
· **Evidence:** `ResendVerificationScreen.tsx:92` calls `.POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })`.
· **Assessment:** Confirmed independently. This is the one-line change that prevents `openapi-fetch` from JSON-parsing a body this screen never reads, which was the root cause of the uniformity defect.
· **Recommendation:** None.

### I2 — The uniformity cases are well-designed
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:789-815` defines thirteen arms covering different statuses and body shapes. The baseline (`DECLARED`) is captured, not hardcoded, and is guarded against a uniformly broken screen (`html.length > 100`, `text === ACK_TEXT`, no `<form` in baseline).
· **Assessment:** The design correctly avoids the "five empty lists are equal" trap. Comparing against a captured baseline that is itself asserted to be the real acknowledgement makes the comparison meaningful.
· **Recommendation:** None.

### I3 — D-T51-2 is valuable: focus and live region are not in serialised markup
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:882-913` collect focus and live-region state for every arm and compare the whole list at once.
· **Assessment:** Confirmed. The Phase 2 probe measured `focus=BODY` on the non-uniform arm while `focus=H1` on the uniform ones, so this axis is real. `innerHTML` alone would have missed it. The cases also assert the expected focus is not `NONE:` and not `BODY:`, which catches degenerate outcomes.
· **Recommendation:** None.

### I4 — The cases failed before the fix
· **Severity:** **Positive**
· **Evidence:** Phase 7 F5 reports that removing the fix made five cases fail: the three non-JSON 200 arms, the focus case, and the live-region case.
· **Assessment:** This is the most important property of a regression suite: it detects the defect it was written for. A suite that passed before the fix would be measuring nothing.
· **Recommendation:** None.

### I5 — D-T51-3 was correctly identified as a duplicate and removed
· **Severity:** **Positive**
· **Evidence:** Phase 7 F3 explains that the timer patterns added locally duplicated `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1576`, which already has a tree-wide timer scan with a pinned allow-list. The local patterns were removed and TR27 was retagged `inherited` to point to the real owner.
· **Assessment:** Confirmed. Naming the owning cases is the right way to keep the instrument visible without maintaining a second, weaker copy.
· **Recommendation:** None.

### I6 — TR26 is real but non-isolating
· **Severity:** **Observation**
· **Evidence:** Phase 7 F2 notes that the email-interpolation mutant kills 18 cases, four of them from T50. The differing-email case is one of them.
· **Assessment:** The mutant is a real regression that must die, but it is not uniquely caught by T51's new case — T50's `textContent` equality already catches it. Keeping the case is still correct because it documents the claim explicitly, but it should not be counted as a new isolated instrument.
· **Recommendation:** None.

### I7 — The location assertion now covers search and hash
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:838` uses `${pathname}${search}${hash}`.
· **Assessment:** This closes the gap where a `navigate('?sent=1')` would leave pathname unchanged. The naming makes L4's redirects clause explicit.
· **Recommendation:** None.

### I8 — F7 gap: no redirect (3xx) arm
· **Severity:** **Minor**
· **Evidence:** The thirteen arms do not include a `302` or `301`. Phase 7 F7 notes this and says the author does not know what the screen would render.
· **Assessment:** This is a real gap, but testing redirects with a stubbed `fetch` and a memory router is non-trivial because `fetch` follows redirects automatically. It is reasonable to leave for Phase 10 or a separate investigation.
· **Recommendation:** Optional Phase 10 addition if a practical stub can be built.

### I9 — F7 gap: `serverWords` optional for four arms
· **Severity:** **Minor**
· **Evidence:** Four arms (`200 no body`, `200 empty string`, `200 plain text`, `503`) have no `serverWords`, so the "server words do not reach DOM" assertion does not run for them.
· **Assessment:** Defensible — there are no distinctive words to check — but the count should be stated, as Phase 7 notes.
· **Recommendation:** Optional Phase 10: give every arm a distinctive `serverWords` or document why four cannot carry one.

### I10 — F7 gap: region case asserts end state only
· **Severity:** **Minor**
· **Evidence:** The live-region comparison in `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:904-913` captures state after `send()` settles.
· **Assessment:** A screen that announced a different region text mid-flight per outcome would pass. T50 holds the in-flight line for one arm, so the risk is low.
· **Recommendation:** Optional Phase 10: assert the region text is empty before settle and identical during flight across outcomes.

### I11 — F7 gap: no case asserts the request is identical across arms
· **Severity:** **Minor**
· **Evidence:** The uniformity cases prove what the SPA renders, not what it sends.
· **Assessment:** R5 is about rendering the same acknowledgement. Request uniformity is already partially held by T50's body equality. A cross-arm request comparison would be a reasonable addition but is not required for the current claim.
· **Recommendation:** Optional Phase 10.

### I12 — OQ-T51-6 remains the highest-value open item
· **Severity:** **Observation**
· **Evidence:** Phase 7 F8 carries OQ-T51-6: `RegisterScreen` and `VerifyScreen` read `response.ok`, so a `SyntaxError` from a non-JSON body reaches them by a different path.
· **Assessment:** Out of T51 scope by `tasks.md`, but it is a real follow-up. `RegisterScreen` navigates on resolve; a non-JSON body would prevent navigation and leave the form hanging. `VerifyScreen` sets failure on non-ok; a non-JSON 200 would be treated as success (since `response.ok` is true) but the promise would reject, so it would show failure instead.
· **Recommendation:** Owner should schedule a hygiene pass or dedicated task.

### I13 — No catalogue or pin changes were needed
· **Severity:** **Positive**
· **Evidence:** No new catalogue keys; `parseAs: 'stream'` contains none of the source-scan forbidden words; the whole suite is green.
· **Assessment:** The fix is minimally invasive, matching the probe's blast-radius prediction.
· **Recommendation:** None.

---

## Summary

T51's implementation correctly fixes the uniformity defect T50 shipped: adding `parseAs: 'stream'` stops `openapi-fetch` from rejecting on non-JSON bodies, and the new uniformity cases prove thirteen different server arms all render the same acknowledgement. The cases failed before the fix, which is the essential property. D-T51-2's focus and live-region cases close a channel `innerHTML` cannot see, and D-T51-3's duplicate removal keeps the timer instrumentation clean.

The remaining issues are minor Phase 10 gaps (redirect arm, optional `serverWords`, mid-flight region, request uniformity) and the out-of-scope OQ-T51-6 follow-up for other public screens.

Final verification is clean: **47/47** in the named suite, **2133/2133** in the whole suite, and **4/4 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 8 complete.** No code or contract changed.
