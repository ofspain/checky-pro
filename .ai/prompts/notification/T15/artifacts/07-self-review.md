# notification · T15 · Phase 7 — Self Review

Self-review of the Phase 6 implementation against the frozen brief and `agents.md`. Findings only —
no fixes applied here (Phase 9's own job). Given this task's own small, self-contained scope (one
new test file, zero production code), only two genuine findings surfaced — proportionate to the
size of the change, not manufactured to fill a quota.

## Finding 1 · `matchesJsonSchemaType` assumes `type` is always a single string, not the JSON-Schema-legal array form

**Severity:** Low

**Evidence:**
- `consumer/dto/ConsumedEventSchemaConformanceTest.java` — `assertConformsToSchema`: `String
  declaredType = declaredProperties.get(field).get("type").asText();` then
  `matchesJsonSchemaType(serialized.get(field), declaredType)`.
- Confirmed directly: all three real schemas under `contracts/events/auth/` (`email-requested`,
  `user-lifecycle`, and `security-audit`, checked for completeness even though the last is not
  consumed) declare every property's own `type` as a single JSON string, never the array form.

**Issue:** JSON Schema legally allows `"type": ["string", "null"]` (the common idiom for an
optional/nullable field) - a real, standard construct, not an exotic one. If a future schema
revision ever used it, `.asText()` on an array-typed node returns `""` (Jackson's own default for a
non-scalar node), which `matchesJsonSchemaType`'s own `default` branch would then reject with
`IllegalArgumentException("unsupported JSON Schema type: ")`, failing the whole test with a
confusing message rather than correctly validating the field against either allowed type. Not
reachable by either of today's two real consumed schemas (confirmed above) - a latent gap in the
helper's own claimed generality, not a live bug.

**Recommendation:** Either explicitly document this helper only supports the single-string `type`
form (matching what both real schemas use today), or extend it to handle the array form if a future
schema actually needs one. Not worth fixing speculatively for a case neither real schema uses.

## Finding 2 · A failure in the first schema's own assertion masks any drift in the second, within one test run

**Severity:** Low

**Evidence:** `shouldConformToConsumedEventSchemas` calls `assertConformsToSchema` for
`EmailRequestedEvent` first, then for `UserLifecycleEvent` - both inside one `@Test` method, with
no `try/catch`/soft-assertion grouping between them.

**Issue:** An AssertJ assertion failure throws immediately, so if the `EmailRequestedEvent` call's
own assertion fails, the `UserLifecycleEvent` call never runs in that same test execution - a
simultaneous drift in the second DTO would only surface after the first is fixed and the test is
rerun, not in one single run's own failure report. The two existing, per-DTO contract test classes
don't share this characteristic, since each DTO's own conformance lives in its own independent test
method.

**Recommendation:** Low-impact given how rarely both DTOs would drift at once, and the existing,
more detailed per-DTO test classes would likely surface either drift independently anyway. Not
worth restructuring into `assertSoftly`/parameterized tests for a two-item list, per the frozen
brief's own explicit rejection of `@ParameterizedTest` (Finding #4) to preserve the literal method
name - a `SoftAssertions` wrapper would be the only way to fix this without violating that
constraint, and is a reasonable option for Phase 9 to consider if wanted, not a hard requirement.

## Open Questions

No blockers. Both findings are real but minor, proportionate to the size of this task's own change,
and neither contradicts a LOCKED decision or `agents.md`.
