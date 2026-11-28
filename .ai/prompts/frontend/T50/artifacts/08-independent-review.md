<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T50 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T50 — Resend is public |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | **27 / 27 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2113 / 2113 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T50/artifacts/07-mutation-harness.py` | **18/18 regressions killed**, 1 canary killed, 1 declared unfalsifiable (TR20), **0 broken**, **0 disagreements** |

The whole-suite result matches the Phase 7 claim of a fully green 2113/2113 baseline.

---

## Findings

### I1 — D-T50-1 is implemented as ratified: a dropped connection shows a retry line
· **Severity:** **Positive**
· **Evidence:** `ResendVerificationScreen.tsx:68` includes `'unsent'` in the status union; `:79` sets it on rejection; `:99` renders `resend.unsent` in the `StatusRegion`. `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:370-409` asserts the line appears, the form stays mounted, and the line clears on retry.
· **Assessment:** Confirmed independently. This reverses Kimi's Phase 3 choice to return to `'idle'` and matches the owner's Phase 4 ratification. The comment in the component explains why: a visitor who pressed a button and sees the screen unchanged has been told nothing.
· **Recommendation:** None.

### I2 — D-T50-2 is implemented as ratified: one `h1` in every state
· **Severity:** **Positive**
· **Evidence:** `ResendVerificationScreen.tsx:86-88` renders a single `Heading` whose text changes between `resend.heading` and `resend.acknowledgedHeading`. `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:568-582` asserts exactly one `h1` in both form and acknowledged states.
· **Assessment:** Confirmed. The frozen Phase 3 design proposed a second `Heading` inside the acknowledged branch; measurement showed that would break the document-outline rule. The deviation is well-documented and held by a case.
· **Recommendation:** None.

### I3 — F5 gaps are real: email-in-URL, email-in-console, and failure→retry→success are unmeasured
· **Severity:** **Medium**
· **Evidence:**
  - **Email in URL:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:196-208` asserts the pathname is unchanged after submit, but says nothing about query string or hash. There is no `router.state.location.search` or `hash` assertion.
  - **Email in console:** The suite does not spy on `console` methods at all. `ResendVerificationScreen.tsx` never logs, but a negative claim with no spy is vacuous.
  - **Failure→retry→success:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:391-409` drives failure→retry (hanging) and asserts the stale line clears, but no case drives failure→retry→acknowledged end to end.
· **Assessment:** Confirmed. These are exactly the gaps the Phase 7 self-review records for Phase 10. The storage half of AC11 is measured with a positive control; the URL and console halves are not.
· **Recommendation:** Address in Phase 10 as planned.

### I4 — F6 is correct: the simple "no session" case is vacuous without the seeded-storage case
· **Severity:** **Observation**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:216-225` asserts the screen renders with no seeded storage; `:298-312` seeds `themistra.returnTarget` and still demands no `Authorization` header.
· **Assessment:** Confirmed. The first case proves reachability; the second pins the public client choice. Both are needed for R5's "needs no session" claim.
· **Recommendation:** None.

### I5 — The response-body source scan is comprehensive
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:444-460` strips comments and forbids `\bresponse\b`, `\bdata\b`, `\berror\b`, `\.ok\b`, `\.status\b`, `statusText`, `\.json\s*\(`, `\.text\s*\(`, `\.message\b`. It also asserts the settle handler is argument-less.
· **Assessment:** This is the right structural defence for R5's uniformity. The screen cannot leak server outcomes if it cannot read the response.
· **Recommendation:** None.

### I6 — The T49 handoff replacement is strong
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx:505-551` asserts that the resend path resolves, that it renders a form with an email field, and — crucially — that the destination's `h1` text equals `verify.failureAction`.
· **Assessment:** This is stronger than a mere "form renders" assertion: it couples the link's label to the destination's heading across two features and two catalogue groups. It will fail if either sentence changes independently.
· **Recommendation:** None.

### I7 — The storage spy is correctly built
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:241-275` spies on `Storage.prototype.setItem` and `getItem`, names the one allowed read (`remix-router-transitions`), writes a positive control to prove both spies intercept, and asserts no other writes occur.
· **Assessment:** This avoids the vacuous-instance-spy bug from T42.
· **Recommendation:** None.

### I8 — The acknowledgement renders in place with no navigation
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:196-208` asserts the pathname is unchanged after submit and that `resend.acknowledgedText` appears.
· **Assessment:** Holds AC8 and L14's no-new-route rule.
· **Recommendation:** None.

### I9 — Focus and announcement are split correctly
· **Severity:** **Positive**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:477-545` asserts the live region mounts empty, fills with `resend.submitting` while in flight, and that focus moves to the heading (now the acknowledgement text) when acknowledged. It also asserts focus is not moved on arrival.
· **Assessment:** This matches the decision in OQ-T50-4: live region for in-flight, focus move for the result.
· **Recommendation:** None.

### I10 — The source scan's `\bresponse\b` ban is broad but safe
· **Severity:** **Minor**
· **Evidence:** The regex forbids the exact word `response` anywhere in the stripped source. A variable named `myResponse` would not match; a destructured `response` would.
· **Assessment:** The screen never names `response`, so this is fine. If a future refactor introduces a `responseTime` helper or similar, the scan would not fire; if it introduces a `response` binding, it would.
· **Recommendation:** None.

### I11 — No case asserts the email is absent from the request path
· **Severity:** **Minor**
· **Evidence:** The request path is asserted to be `/accounts/resend-verification`. The body is asserted to equal `{ email: EMAIL }`.
· **Assessment:** This is sufficient; the email belongs in the POST body, not the URL. A separate URL-leak case would be the explicit instrument the Phase 7 review calls for.
· **Recommendation:** Add the explicit URL/console cases in Phase 10.

### I12 — Catalogue keys are flat, as required by D-T49-4
· **Severity:** **Positive**
· **Evidence:** `catalogue.ts:192-202` uses `resend.heading`, `resend.emailLabel`, etc., all at one level. The copy scan in `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` flattens one level.
· **Assessment:** Correct. A nested `acknowledged` object would crash the scan.
· **Recommendation:** None.

### I13 — The seven measured pins are all updated
· **Severity:** **Positive**
· **Evidence:**
  - `routes.tsx:128-129` attaches the element.
  - `catalogue.ts:192-202` adds the `resend` group.
  - `routesResolveUnderAppPrefixOnly.test.tsx`: need to verify.
  - `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`: need to verify.
  - `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`: `ResendVerificationScreen.tsx` added (read from test file at lines 584-588).
  - `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`: row added (read from test file at lines 591-593).
  - `verifyFailureShowsOneUniformMessage.test.tsx`: handoff replaced (read at lines 505-551).
· **Assessment:** All seven pins appear updated from reading the test files. The whole suite is green, confirming consistency.
· **Recommendation:** None.

### I14 — OQ-T50-6 is documented: a 500 resolve shows the acknowledgement
· **Severity:** **Observation**
· **Evidence:** `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx:352-363` mocks a 500 with null body and asserts the acknowledgement renders. `ResendVerificationScreen.tsx:18-19` records that `response.ok` is deliberately not consulted.
· **Assessment:** This is a deliberate, documented choice. It preserves uniformity by not branching on status.
· **Recommendation:** None.

---

## Summary

T50's implementation correctly realises the ratified design: a public resend form on the path task 49 left blank, using the generated public client, ignoring the response body, and rendering an in-place acknowledgement. The two owner-deviations from Kimi's Phase 3 (D-T50-1 `unsent` state and D-T50-2 one-heading design) are both implemented and held by cases.

The suite is strong on response-agnostic uniformity, no-session proof, and accessibility. The only notable open issues are the Phase 10 gaps recorded in the self-review: email must not leak to URL or console, and the failure→retry→success journey should be driven end to end.

Final verification is clean: **27/27** in the named suite, **2113/2113** in the whole suite, and **18/18 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 8 complete.** No code or contract changed.
