# payment · T23 · Phase 6 — Implementation Notes

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md`

## Files created (exactly as planned)

1. `services/payment/src/main/java/com/themistra/payment/common/ProblemTypes.java` — 11
   constants, exactly as frozen.

## Files modified (exactly as planned)

1. `ApiExceptionHandler.java` — 7 call sites updated to the matching `ProblemTypes` constant;
   removed the now-unused `java.net.URI` import.
2. `InvoiceExceptionHandler.java` — 3 call sites updated; removed the now-unused import.
3. `ExportExceptionHandler.java` — 1 call site updated; removed the now-unused import.
4. `ResourceServerConfig.java` — `writeProblemJson`'s signature frozen exactly as Phase 4
   Finding 6 specified (`URI type` parameter); both callers (`problemJsonAuthenticationEntryPoint`,
   `problemJsonAccessDeniedHandler`) now pass `ProblemTypes.UNAUTHORIZED`/`ProblemTypes.FORBIDDEN`
   respectively; added the `java.net.URI` import this file previously didn't need.

## A refinement to Phase 4's own disposition, disclosed — not a reversal of the human-approved decision

Phase 4's disposition of Kimi's Finding 3 said the two historical Javadoc mentions of
`"about:blank"` (`ApiExceptionHandler.java:29`, `InvoiceExceptionHandler.java:17`) should be
"left untouched... a true historical statement about what Phase 9 did, not a claim about current
behavior." Rereading both while actually editing the file: they are **not** purely historical —
each asserts a present-tense fact about current code ("every `ProblemDetail` here also sets
`type(\"about:blank\")`"), merely dated by when that behavior was introduced. After this task's
edit, that present-tense claim becomes flatly false, which is a different problem than the one
AC2 (the mechanical litmus test) was scoped to avoid — a stale comment that actively misdescribes
current code, not a historical decision record being erased.

Updated both to describe the new reality while preserving the Phase 9 attribution (disclosing
forward, not rewriting history: the sentence now reads "...originally a uniform
`\"about:blank\"` placeholder from Phase 9, Kimi Phase 8 finding 5, replaced with the actual
stable catalogue in T23/R29"). This does not reopen or contradict Phase 4's actual decision —
AC2 itself (zero literal `"about:blank"` in *executable* code) is satisfied identically either
way; this is a comment-accuracy refinement found while implementing, not a scope change.

## Expected, pre-identified test failures (NOT fixed this phase — Phase 10's job)

Running the full suite after this change produces exactly the 7 failing test methods the frozen
brief's own "Files to Modify" list already named as a certain consequence, confirming that
earlier grounding was accurate:

- `InvoiceControllerTest`: `shouldRejectNonMerchantCallerWithForbidden`,
  `shouldReturnServiceUnavailableWhenWatchRegistrationFails`,
  `shouldReturnProblemJsonForInvalidInvoiceRequest`,
  `shouldReturnProblemJsonForBeanValidationFailure`,
  `shouldRejectNonMerchantCallerOnReadEndpoints` — each asserts `$.type` equals the literal
  `"about:blank"`, now a real catalogue URI.
- `ResourceServerConfigTest.unauthenticatedRequestToNonPublicPathIsRejected` — same.
- `AccessDeniedHandlerTest.accessDeniedHandlerWritesRfc9457ProblemJson` — same.

These are intentionally left red at the end of this phase, per the frozen brief's own explicit
note that these updates are "deferred to Phase 10 per the standard rule" (Phase 6 is code only;
updating a test assertion to match an intentional behavior change is test-writing, not a defect
to silently patch out of sequence). Not a new discovery — Phase 0 and Phase 4 both already
enumerated these exact sites before any code was written.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- `grep -rn "about:blank" services/payment/src/main/java`: exactly the two historical Javadoc
  mentions remain (now corrected per the refinement above); zero executable occurrences (AC2).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **444/451 passing, 7 failing**
  — exactly, and only, the 7 pre-identified sites above. No other test regressed.
