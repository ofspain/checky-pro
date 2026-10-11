# payment · T03 · Phase 10 — Test Generation (retroactive, disclosed)

**This is not a normal Phase 10 artifact.** The tests this phase would normally produce were
already written during Phase 6 (implementation) — a process error disclosed in full in
`artifacts/09-review-resolution.md`, not something to quietly paper over here. This artifact
exists so the per-task directory structure stays complete and the deviation stays visible to
anyone reading the pipeline in order, not to re-narrate work already described in Phases 6, 7,
and 9's own artifacts.

## What exists (written at Phase 6, refined at Phases 7 and 9)

- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerTestController.java`
  (test-only, never shipped)
- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/AccessDeniedHandlerTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/PublicEndpointsTest.java`
  (added at Phase 7, applying that phase's own finding 1)
- `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/config/ConfigPropertiesBindingTest.java`
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` (modified,
  not new — updated at Phase 6 for the `@ConfigurationPropertiesScan` annotation count change)

## Test → Acceptance Criterion mapping

| Test class | Verifies |
|---|---|
| `ResourceServerConfigTest` | AC3, AC4 (401 half) |
| `AccessDeniedHandlerTest` | AC4 (403 half) |
| `PublicEndpointsTest` | AC3 (Phase 7 finding 1) |
| `ConfigPropertiesBindingTest` | AC1, AC5 |
| `ApplicationPropertiesContentTest` | AC2, AC6, AC7, AC8, Phase 3 findings 4/5/7/8, Phase 8 finding 3 |

## Final state

`mvn -pl services/payment test` → **63/63 passing**, as confirmed in `09-review-resolution.md`.
