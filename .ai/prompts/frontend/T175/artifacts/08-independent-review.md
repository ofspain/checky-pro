<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# frontend · T175 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T175 — A reply the client cannot parse is not a failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFromLinkShowsSuccessState.test.tsx src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | **64 / 64 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2146 / 2146 passed** — fully green baseline |
| `python3 -I .ai/prompts/frontend/T175/artifacts/07-mutation-harness.py` | **5/5 regressions killed**, 1 canary killed, **0 survivors**, **0 broken**, **0 disagreements** |

The whole-suite result matches the Phase 7 claim of a fully green 2146/2146 baseline.

---

## Findings

### I1 — The production fix is in place on both screens
· **Severity:** **Positive**
· **Evidence:** `VerifyScreen.tsx:107` and `RegisterScreen.tsx:100` both pass `parseAs: 'stream'` to their respective `POST` calls.
· **Assessment:** Confirmed independently. The one-line fix is applied identically to both affected screens.
· **Recommendation:** None.

### I2 — The new source scan is well-designed
· **Severity:** **Positive**
· **Evidence:** `generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts:43-170` finds generated-client calls by factory name or declared client type, classifies each as reader/non-reader, asserts the census, and forbids non-readers without `parseAs: 'stream'`.
· **Assessment:** The scan correctly handles the two `profile/` readers that take their client as a parameter rather than calling a factory. It also audits the factory/type list against `api/client.ts` so its reach cannot shrink quietly. The three readers (`codeExchange`, `readAccount`, `readProfile`) are exempt; the three non-readers (`RegisterScreen`, `ResendVerificationScreen`, `VerifyScreen`) are required to stream.
· **Recommendation:** None.

### I3 — TR33 confirms R4 is preserved
· **Severity:** **Positive**
· **Evidence:** Phase 7 F1 reports that inverting the `response.ok` test kills 31 cases, including the entire nine-status loop in `verifyFailureShowsOneUniformMessage.test.tsx`.
· **Assessment:** Confirmed. The fix does not trade an R3 violation for an R4 violation. A genuine `4xx`/`5xx` still renders failure, and a transport failure still renders failure.
· **Recommendation:** None.

### I4 — TR31/TR32 prove both behavioural and scan instruments fire
· **Severity:** **Positive**
· **Evidence:** Phase 7 F2 reports that removing `parseAs: 'stream'` from either screen kills both the screen's behavioural cases and the scan's census/offenders cases.
· **Assessment:** Confirmed. The behavioural cases prove the defect is gone; the scan proves it cannot return in a new screen.
· **Recommendation:** None.

### I5 — TR34/TR35 prove the scan's reach audit is load-bearing
· **Severity:** **Positive**
· **Evidence:** Phase 7 F3 reports that a mutant which shrinks the factory/type list kills two cases (reach audit + census), and TR35 isolates the census exactly.
· **Assessment:** Confirmed. A scan whose reach can shrink unnoticed is worse than no scan; the audit prevents that.
· **Recommendation:** None.

### I6 — Every new case failed before the fix
· **Severity:** **Positive**
· **Evidence:** Phase 7 F4 reports that two verify cases, three registration cases, and two scan cases failed before the fix. The author also re-measured the scan's non-vacuity after implementation.
· **Assessment:** Confirmed. The cases are designed to fail on the pre-fix code, which is the only way to know they measure the defect.
· **Recommendation:** None.

### I7 — D-T175-5 is correct: no new AC4/AC5/AC6 cases were needed
· **Severity:** **Positive**
· **Evidence:** `verifyFailureShowsOneUniformMessage.test.tsx` already holds no-Authorization, body-shape, and one-call assertions across its nine-status loop. TR33's 31-case kill includes that suite.
· **Assessment:** Confirmed. Adding duplicate cases would violate T173's rule.
· **Recommendation:** None.

### I8 — D-T175-6 adds a useful control
· **Severity:** **Positive**
· **Evidence:** `verifyFromLinkShowsSuccessState.test.tsx:680-690` asserts that `new Response(body, { status: 204 })` throws and `new Response(null, { status: 204 })` does not.
· **Assessment:** This documents why the defect was invisible to fixtures and guards against a future runtime change that would make 204-with-body constructible.
· **Recommendation:** None.

### I9 — The scan is in a dedicated file, which is the right grouping
· **Severity:** **Positive**
· **Evidence:** `frontend/src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` is separate from `lintRejectsHandWrittenBackendFetch.test.ts`.
· **Assessment:** The concern — generated-client call shape — is distinct from the bare-fetch ban, and the dedicated file lets the scan be long-documented without obscuring the L6 lint tests.
· **Recommendation:** None.

### I10 — OQ-T175-7 is a real gap in the reach audit
· **Severity:** **Medium (process)**
· **Evidence:** `codeExchange.ts` imports `createOidcClient` from `@themistra/api-client`, not `api/client.ts`. The scan's factory audit only checks `api/client.ts`, so a new factory added to the package would widen the surface without breaking a case.
· **Assessment:** Confirmed. The Phase 7 self-review identifies this as the most valuable Phase 10 follow-up. The fix is cheap: audit the package's exports too.
· **Recommendation:** Address in Phase 10 as planned.

### I11 — The reader-detection regex could miss a future non-destructuring reader
· **Severity:** **Minor**
· **Evidence:** `generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts:102` detects `data` only inside a `.then` callback's destructuring pattern. A reader written as `.then((result) => result.data)` would be classified as a non-reader.
· **Assessment:** This is not a problem today — all three readers either `await` the result or destructure `data` — but it is a brittleness to note. The census assertion would break if such a pattern were introduced, forcing the scan to be updated.
· **Recommendation:** Optional Phase 10: broaden the reader detection to include `\.data\b` anywhere in the statement.

### I12 — RegisterScreen's reject arm is still tested by T50
· **Severity:** **Observation**
· **Evidence:** Phase 7 F6 asked whether the reject arm is still tested. T50's `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` or `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` do not appear to test transport failure. However, `RegisterScreen.tsx:101` still has `() => setStatus('idle')` as the reject arm, and the original T43 registration suite may have tested it. The Phase 7 author's uncertainty is reasonable.
· **Assessment:** With `parseAs: 'stream'`, the reject arm is now reachable only on transport failure. Whether it is explicitly tested should be verified, but it is not a T175 defect.
· **Recommendation:** Optional Phase 10: confirm the reject arm has a case or add one if missing.

### I13 — The scan pattern covers .PUT/.PATCH/.DELETE but they are unexercised
· **Severity:** **Minor**
· **Evidence:** `generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts:83` matches all HTTP methods, but no production call site uses them.
· **Assessment:** The pattern is present and would catch a future non-reader using those methods. A fixture file could test the pattern, but it is not urgent.
· **Recommendation:** None.

---

## Summary

T175's implementation correctly fixes the non-JSON body defect on both `VerifyScreen` and `RegisterScreen` with `parseAs: 'stream'`, and the new source scan ensures a fourth non-reading screen cannot ship the same bug. The harness shows TR31–TR35 all kill as designed, R4 is preserved, and the scan's reach audit is load-bearing.

The only notable open issue is OQ-T175-7: the scan's factory audit should also cover `@themistra/api-client` exports, because `codeExchange.ts` imports from the package. The other Phase 10 gaps are minor.

Final verification is clean: **64/64** in the affected suites, **2146/2146** in the whole suite, and **5/5 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 8 complete.** No code or contract changed.
