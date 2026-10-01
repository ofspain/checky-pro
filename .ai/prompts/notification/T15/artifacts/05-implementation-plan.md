# notification · T15 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's own Files to Create section. No file is added beyond
what Phase 4 authorized. No code — signatures and behavior only.

## Files to create

### `consumer/dto/ConsumedEventSchemaConformanceTest.java`
```
class ConsumedEventSchemaConformanceTest {
    private final ObjectMapper objectMapper  // .findAndRegisterModules(), WRITE_DATES_AS_TIMESTAMPS disabled

    @Test
    void shouldConformToConsumedEventSchemas() throws IOException

    private void assertConformsToSchema(JsonNode serialized, Path schemaPath) throws IOException
    private static boolean matchesJsonSchemaType(JsonNode node, String declaredType)
}
```

`shouldConformToConsumedEventSchemas` (the literal `package.md` §8 name, AC1) constructs one real
`EmailRequestedEvent` and one real `UserLifecycleEvent` (fixed, fake values — `"redacted-test-token"`
for the token field, an obviously-fake `Instant` literal, mirroring both existing contract test
classes' own construction style), serializes each via `objectMapper.valueToTree`, and calls
`assertConformsToSchema` once per DTO/schema pair, in sequence. If either call's own assertion
fails, the test fails with the real DTO/schema pair identified in the failure message — no
`@ParameterizedTest` (Finding #4 — would break the literal method-name requirement).

`assertConformsToSchema(JsonNode serialized, Path schemaPath)`: reads the real schema file at
`schemaPath` into a `JsonNode` (mirrors both existing contract test classes' own identical
`objectMapper.readTree(Files.readString(schemaPath))` call). Asserts, in order:
1. Every field name in `schema.get("required")` is present in `serialized` (AC1's own "required
   field present" half) — failure message names only the field and the schema's own file name
   (`schemaPath.getFileName()`), never any value (AC3/Finding #3).
2. Every field name actually present in `serialized` is declared in `schema.get("properties")`
   (AC1's own "no undeclared field" half, same no-value-in-message discipline).
3. For every property the schema declares that `serialized` actually has, the serialized node's own
   JSON type matches the schema's declared `type` string via `matchesJsonSchemaType` (AC1's own new
   type-check half, Finding #2) — again, the failure message names only the field and the declared
   type string, never the actual value.

`matchesJsonSchemaType(JsonNode node, String declaredType)`: a small, `switch`-expression mapping
of the 6 JSON Schema primitive type names (`string`, `integer`, `number`, `boolean`, `object`,
`array`) to the matching `JsonNode` predicate (`isTextual()`, `isIntegralNumber()`, `isNumber()`,
`isBoolean()`, `isObject()`, `isArray()`); an unrecognized `declaredType` throws
`IllegalArgumentException` rather than silently returning `false` — both real schemas only ever
declare `"string"` today (confirmed at Phase 4), so every other branch is exercised by neither
schema file yet, but the exhaustive `switch` keeps the helper correct, not merely convenient for
today's two schemas, and an unrecognized type fails loudly rather than silently passing.

## Files to modify

None — Phase 4's own direct re-read confirmed no DTO drift exists; no change to either existing
contract test class; no change to either real schema file.

## Public methods (signatures)

`ConsumedEventSchemaConformanceTest.shouldConformToConsumedEventSchemas()` — the one, sole public
(package-private, `@Test`-annotated, matching both existing contract test classes' own identical
visibility) method this task adds.

## Private methods

`assertConformsToSchema(JsonNode, Path)`, `matchesJsonSchemaType(JsonNode, String)` — both described
above.

## Entities used

None — no JPA entity, no database involvement. Plain record instances
(`EmailRequestedEvent`/`UserLifecycleEvent`) only.

## Repositories used

None.

## Services used

None beyond the plain, already-present Jackson `ObjectMapper` — no Spring context, no `@Autowired`
dependency, matching both existing contract test classes' own identical "plain JUnit, no Spring
context" shape.

## Unit tests required

The one new test method itself, `shouldConformToConsumedEventSchemas` — this phase's own "tests
required" output IS the production deliverable (a test-only task). No further unit test is needed
beyond confirming the existing `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest`
suites still pass unchanged (regression, not new coverage).

## Integration tests required

None — no Spring context, no Testcontainers, no Kafka/Postgres involvement anywhere in this task's
own real scope.

## Execution order

1. Re-verify (not re-litigate — already confirmed at Phase 4) both real schema files against both
   real DTOs field-by-field, one final time immediately before writing the test, so the test's own
   fixed fake values are known to match every `required` field and declared `type` before the test
   is even run once.
2. `consumer/dto/ConsumedEventSchemaConformanceTest.java` — write `matchesJsonSchemaType` first (the
   one truly new piece of logic), then `assertConformsToSchema` (depends on it), then
   `shouldConformToConsumedEventSchemas` (depends on both).
3. Run the new test alone first (`mvn -pl services/notification test -Dtest=ConsumedEventSchemaConformanceTest`).
4. Run `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest` to confirm no regression.
5. Full suite: `mvn -pl services/notification clean verify`.
