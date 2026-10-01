# notification · T15 · Phase 6 — Implementation Notes

Implemented exactly per the Phase 5 plan — no deviation, no surprise. The smallest, most
self-contained task in this pipeline so far: one new test file, zero production code changes, zero
new dependencies, zero files modified.

## Files created

- `consumer/dto/ConsumedEventSchemaConformanceTest.java` — one `@Test` method,
  `shouldConformToConsumedEventSchemas` (the literal `package.md` §8 name), proving both
  `EmailRequestedEvent` and `UserLifecycleEvent` conform to their own real schema files
  (`contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json`): every schema-`required`
  field present, no undeclared field serialized, and every present field's own JSON type matches
  the schema's declared `type` (the Phase 3/4 type-check widening). Two private helpers
  (`assertConformsToSchema`, `matchesJsonSchemaType`) implement the three checks exactly as planned.

## Files modified

None.

## Deviation from the plan

None. The one open question Phase 4 had already resolved by direct inspection — "no DTO drift
exists" — was reconfirmed the moment the new test actually ran: it passed on the first run, with no
DTO change needed.

## Mapping to acceptance criteria

- **AC1**: `shouldConformToConsumedEventSchemas` exists, passes, and asserts all three checks
  (required-present, no-undeclared, type-match) for both real consumed schemas.
- **AC2**: confirmed - no genuine drift exists between either DTO and its own real schema file; the
  new test's own first run is the empirical proof, not merely the Phase 4 inspection alone.
- **AC3**: every assertion in `assertConformsToSchema` operates on field names and `JsonNode` types
  only - never a field's own value - so `EmailRequestedEvent`'s raw `token` can never appear in a
  failure message, confirmed by direct code review (no assertion anywhere references `.asText()`
  on the `token` field's own value, only its presence/declaredness/type).
- **AC4**: no test was written against `contracts/events/payments/*` - disclosed, not silently
  absent, consistent with every prior phase's own identical disclosure.

## Verification

- `mvn -pl services/notification test-compile` — clean.
- `mvn -pl services/notification test -Dtest=ConsumedEventSchemaConformanceTest,EmailRequestedEventContractTest,UserLifecycleEventContractTest`
  — all 9 tests pass (1 new + 4 + 4 pre-existing, unaffected).
- `mvn -pl services/notification clean verify` — 358 tests, 0 failures, 0 errors (357 + 1 new).
