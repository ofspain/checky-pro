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
