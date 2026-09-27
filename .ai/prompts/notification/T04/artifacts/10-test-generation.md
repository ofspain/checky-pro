# notification · T04 · Phase 10 — Test Generation

All tests deferred from Phase 6 and tracked at Phase 9 (Kimi Phase 8 Findings #1, #4, #5) are added
here. `package.md` §8's remaining 17 named tests still require feature code this task doesn't add
(consumer, preference resolution, template rendering, in-app controllers); only its own two —
`shouldDedupeDuplicateEventDeliveryByEventKey`, `shouldNotDoubleSendWhenSameEventRedelivered` — apply
to T04. 2 new test files, 8 new tests (76 total: 68 T01-T03 unaffected + 8 new).

## Files created

- `consumer/IdempotencyGuardUnitTest.java` — 3 tests, mocked repository + fixed `Clock`, no Spring
  context, no Docker. Satisfies the task statement's own literal "unit-test the dedupe" instruction.
- `consumer/IdempotencyGuardIntegrationTest.java` — 5 tests, `@Testcontainers` + `@SpringBootTest`
  (the first test class in this module needing a real Spring context, per Phase 5's own design).

## Test manifest

| Test method | Verifies | AC / requirement |
|---|---|---|
| `IdempotencyGuardUnitTest.shouldDedupeDuplicateEventDeliveryByEventKey` | Named test (`package.md` §8) — `insertIfNew` returns 1 → `recordIfNew` returns `true` | R7, AC2 |
| `IdempotencyGuardUnitTest.shouldNotDoubleSendWhenSameEventRedelivered` | Named test — `insertIfNew` returns 0 → `recordIfNew` returns `false` | R8, AC2 |
| `IdempotencyGuardUnitTest.shouldUseTheInjectedClockNotWallClockTime` | `clock.instant()`, not `Instant.now()`, is what's passed to the repository | AC4 |
| `IdempotencyGuardIntegrationTest.recordIfNewJoinsAnExternallyOpenedTransactionAndRollsBackWithIt` | `REQUIRED` propagation genuinely joins and rolls back with the caller's own transaction | L1, AC3 |
| `IdempotencyGuardIntegrationTest.recordIfNewCommitsWhenAnExternallyOpenedTransactionCommits` | The success-path counterpart — a caller's commit makes the row durable | L1, AC3 |
| `IdempotencyGuardIntegrationTest.secondCallWithSameKeyReturnsFalseSerially` | Real DB proof of R7/R8 outside a mock | R7, R8, AC2 |
| `IdempotencyGuardIntegrationTest.concurrentCallsWithSameKeyResolveToExactlyOneTrue` | Kimi Phase 8 Finding #1's own required proof — 8 real concurrent threads, exactly one `true`, zero unchecked exceptions | AC2 |
| `IdempotencyGuardIntegrationTest.processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow` | Kimi Phase 8 Finding #4 / self-review Finding #3 — the persisted value round-trips through native-query binding, not just "some non-null value exists" | AC4 |

## Negative-proof (mutation testing) — Kimi Phase 8 Finding #5

Reverted `ProcessedEventRepository.insertIfNew`'s native query from `INSERT ... ON CONFLICT
(event_key) DO NOTHING` to a plain `INSERT ... VALUES (...)`, re-ran
`IdempotencyGuardIntegrationTest` alone: `concurrentCallsWithSameKeyResolveToExactlyOneTrue` failed
with a real, uncaught `DataIntegrityViolationException` (7 of 8 threads), and
`secondCallWithSameKeyReturnsFalseSerially` also failed the same way — confirms these tests would
catch a regression that reintroduced the exact defect Phase 6 discovered and fixed. Reverted; `git
status -s` on `ProcessedEventRepository.java` empty afterward; full suite re-verified green.

## Verification

`mvn -pl services/notification clean verify` — 76 tests, 0 failures (68 T01-T03 unaffected + 8 new).
No production code left modified in this phase (the mutation above was reverted before the final
verification run).

## Addendum (post Phase 11) — all 7 gaps accepted and added

Kimi's Phase 11 review raised 7 gaps. All verified against source before acting, all genuine, all
added (76 tests → 79):

- **Gap #1** (round-trip test used a wall-clock range, not proof the injected `Clock` is what's
  used) — added `FixedClockConfig`, a `@TestConfiguration` overriding `ClockConfig`'s own
  `Clock.systemUTC()` bean with `Clock.fixed(...)` via `@Primary` (a same-name `@Bean` override hit
  `BeanDefinitionOverrideException` on first attempt - Spring Boot disables bean-definition
  overriding by default; fixed by naming the override bean differently, `fixedClock()`, relying on
  `@Primary` for autowiring resolution instead of same-name replacement). The round-trip test now
  asserts exact equality against the fixed instant, plus `eventKey`/`eventType` (folds in Gap #6).
- **Gap #2 / #7** (no permanent guard for the `ON CONFLICT DO NOTHING` SQL shape) — added
  `insertIfNewUsesOnConflictDoNothing`, a static text-scan test.
- **Gap #3** (concurrent test didn't verify DB row count) — added `repository.findById` +
  a direct row-count query assertion to `concurrentCallsWithSameKeyResolveToExactlyOneTrue`.
- **Gap #4** (no proof of the `@Transactional` mechanism itself, only its behavior) — added
  `recordIfNewUsesDefaultRequiredPropagation` (reflection-based, unit-level, no DB needed).
- **Gap #5** (unit tests never varied `eventType` independently) — added
  `shouldPassTheSuppliedEventTypeThroughUnchanged`.
- **Gap #6** — folded into Gap #1's fix above.

**Verification:** `mvn -pl services/notification clean verify` — 79 tests, 0 failures. Two real
mutation tests performed and reverted clean (`git status -s` empty afterward): (1) Gap #4's own
check — changed `recordIfNew`'s propagation to `REQUIRES_NEW`, confirmed both the new reflection
test (unit-level, fast) and the pre-existing transaction-join integration test independently caught
it; (2) the Phase 10 `ON CONFLICT DO NOTHING` mutation (unchanged from before, still valid).
