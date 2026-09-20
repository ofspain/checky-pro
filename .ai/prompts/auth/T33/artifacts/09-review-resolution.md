<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T33 · Phase 9 — Review Resolution

Consumes `artifacts/07-self-review.md` (1 finding) and `artifacts/08-independent-review.md` (Kimi,
5 findings). All findings verified against actual source before disposition. femi decided the two
High-confidence findings with real trade-off weight via human gate; the remaining three are
dispositioned directly.

## Comment resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| Self-review Finding 1 / Kimi Finding 4 | `controllerRoutes()` silently skips a hypothetical bare-`@RequestMapping` handler | **Accepted as documented, no behavior change.** No handler in this codebase triggers it today. | Added an in-code Javadoc comment on `controllerRoutes()` referencing both findings, per Kimi's own suggestion, so the limitation is visible in the source, not only in phase artifacts. |
| Kimi Finding 1 | No operation-level check that a `$ref` names the *correct* component — a wrong reference would pass all 3 original tests | **ACCEPTED, femi's gate decision, scoped.** Full fix per D2's original text (incl. status codes) rejected as disproportionate — exact status codes aren't reliably reflectable (set at runtime via `ResponseEntity.status(...)`). Scoped fix: verify request/response `$ref` names match an explicit expectation table derived from Phase 5's own inventory. | Added `ExpectedSchema` record + `expectedResponseSchemas()`/`expectedRequestSchemas()` + `actualResponseSchema`/`actualRequestSchema`/`parseSchemaNode` helpers + 2 new `@Test` methods (`everyOperationResponseReferencesTheExpectedSchema`, `everyOperationRequestBodyReferencesTheExpectedSchema`) to `AuthOpenApiContractTest.java`. Re-verified with a live negative-proof: swapped `POST /accounts`'s request-body `$ref` from `RegisterAccountRequest` to `VerifyEmailRequest` (Kimi's own example scenario) — confirmed real failure with the exact wrong/expected names in the assertion message; reverted. |
| Kimi Finding 2 | No enum-coverage test for `AccountStatus`/`AuditOutcome` in the OpenAPI schemas | **ACCEPTED, femi's gate decision.** Matches existing established pattern exactly (`everyAccountStatusValueIsCoveredByTheSchemaEnum` precedent). | Added `everyAccountStatusValueIsCoveredByTheAccountResponseSchemaEnum` and `everyAuditOutcomeValueIsCoveredByTheAuditEventResponseSchemaEnum` to `AuthOpenApiContractTest.java`. |
| Kimi Finding 3 | Component matching is structural only (field presence), not type/format-checked | **REJECTED, no change.** Consistent with the *original*, already-accepted `UserLifecycleEventPayloadContractTest` pattern this whole task was instructed to mirror — this is an inherent, pre-existing characteristic of the established technique, not a regression introduced by this task. Kimi itself frames this as "document, don't necessarily fix." Documented via this log rather than a code comment, since the limitation is inherent to the technique everywhere it's used in this codebase, not specific to this one test. |
| Kimi Finding 5 | `required` is checked in only one direction (schema→instance, not instance→schema) | **REJECTED, no change.** Kimi's own write-up correctly identifies the complication: several sample instances deliberately populate genuinely-nullable fields as non-null (e.g. `AuditEventResponse`'s `accountUuid`/`ip`/etc. in the happy-path sample) specifically to prove the *populated* shape matches — a naive reverse check would produce false positives against schemas that are already correct, flagging legitimately-nullable fields as "should be required" just because one sample happened to populate them. Fixing this properly would need dedicated null-value sample variants per nullable field (real added complexity for a task whose own frozen scope is documentation-gap closure, not building an exhaustive contract-testing framework). |

## Summary

Two code changes applied to `AuthOpenApiContractTest.java`: (1) a scoped operation-level
`$ref`-correctness check (request + response, no status-code verification) closing the most
material gap Kimi found, verified via a live negative-proof reproducing Kimi's own example
scenario; (2) two enum-coverage tests matching this codebase's own established pattern. One
documentation-only change (an in-code comment on the already-flagged bare-`@RequestMapping`
limitation). Two findings rejected with reasoning recorded above, not silently dropped.

Test count: `AuthOpenApiContractTest` grew from 3 to 7 tests; combined with the two unchanged event
contract test files, T33's full new-test count is now **12** (up from 8 at Phase 6).

Verification after all changes: `mvn clean test-compile` clean;
`mvn test -Dtest='AuthOpenApiContractTest,EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest'`
— **12/12 pass**. `git status` confirms only `AuthOpenApiContractTest.java` changed this phase; no
other file touched.

---

**Phase 9 complete — resolution log written; sign-off recorded.** Proceed to Phase 10 (Test
Generation) on approval.
