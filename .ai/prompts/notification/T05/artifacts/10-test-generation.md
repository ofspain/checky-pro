# notification · T05 · Phase 10 — Test Generation

All tests deferred from Phase 6 and tracked at Phase 9 (Kimi Phase 8 Findings #1, #3, #4, plus
Phase 4's own Finding #5) are added here. `package.md` §8's 19 named tests still require feature
code this task doesn't add; none apply directly to T05, mirroring T02/T04's own precedent. 2 new
test files, 8 new tests (88 total: 80 T01-T04 unaffected + 8 new).

## Files created

- `preference/ContactProjectionUpdaterUnitTest.java` — 3 tests, mocked repository, no Spring
  context, no Docker.
- `preference/ContactProjectionUpdaterIntegrationTest.java` — 5 tests, `@Testcontainers` +
  `@SpringBootTest` (the second test class in this module needing a real Spring context, after
  T04's own `IdempotencyGuardIntegrationTest`).

## Test manifest

| Test method | Verifies | AC / requirement |
|---|---|---|
| `ContactProjectionUpdaterUnitTest.upsertEmailDelegatesWithExactArgumentsAndReturnsTrueWhenAccepted` | Delegates with the exact arguments; returns `true` on acceptance (Phase 9 Finding #5) | AC2 |
| `ContactProjectionUpdaterUnitTest.upsertEmailReturnsFalseWhenTheGuardRejectsAStaleWrite` | Returns `false` on rejection | AC3 |
| `ContactProjectionUpdaterUnitTest.upsertEmailUsesDefaultRequiredPropagation` | Kimi Phase 8 Finding #4 — reflection-based mechanism proof, not just behavior | AC3 |
| `ContactProjectionUpdaterIntegrationTest.firstCallCreatesARowWithNullDisplayName` | Kimi Phase 8 Finding #3 / frozen brief Finding #5 — `display_name` stays null | AC1, AC4 |
| `ContactProjectionUpdaterIntegrationTest.laterCallUpdatesEmailAndUpdatedAt` | Genuine upsert — a second call updates the row | AC2 |
| `ContactProjectionUpdaterIntegrationTest.olderCallDoesNotOverwriteANewerProjection` | Kimi Phase 8 Finding #1's own required out-of-order proof | AC3 |
| `ContactProjectionUpdaterIntegrationTest.upsertEmailJoinsAnExternallyOpenedTransactionAndRollsBackWithIt` | Kimi Phase 8 Finding #4's own required transaction-join proof | AC3, L2 |
| `ContactProjectionUpdaterIntegrationTest.upsertEmailCommitsWhenAnExternallyOpenedTransactionCommits` | The success-path counterpart | AC3 |

Every Testcontainers-backed `@SpringBootTest` in this suite (both the new class and the pre-existing
`IdempotencyGuardIntegrationTest`, which shares the same context cache) also serves as a permanent,
committed proof of Kimi Phase 3 Finding #1 — `ContactProjection`'s `citext` mapping passes
`ddl-auto=validate` — closing the gap the Phase 6/7 uncommitted scratch checks only closed
temporarily.

## Negative-proof (mutation testing)

Removed the `WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at` guard from
`ContactProjectionRepository.upsertEmail`'s native query entirely, re-ran
`ContactProjectionUpdaterIntegrationTest` alone: `olderCallDoesNotOverwriteANewerProjection` failed
immediately, and only that test — confirming the guard is both necessary and precisely covered.
Reverted; `git status -s` on `ContactProjectionRepository.java` empty afterward; full suite
re-verified green.

## Verification

`mvn -pl services/notification clean verify` — 88 tests, 0 failures (80 T01-T04 unaffected + 8 new).
No production code left modified in this phase (the mutation above was reverted before the final
verification run).

## Addendum (post Phase 11) — all 6 actionable gaps accepted and added (Gap #7 already covered)

Kimi's Phase 11 review raised 7 gaps. All verified against source before acting. Gap #7 (verify
`ContactProjectionUpdater` is a Spring bean) was Kimi's own low-priority, already-covered-by-the-
integration-test concession — no action taken. The other 6 were genuine and are all added
(92 tests → from 88):

- **Gap #1** (no permanent guard that the `WHERE` guard itself exists) — added
  `upsertEmailNativeQueryContainsTheOutOfOrderGuard`, a static text-scan test.
- **Gap #2** (equal-`occurredAt` tie-breaking untested) — added
  `equalOccurredAtTiesResolveToTheLaterProcessedCallWinning`.
- **Gap #3** (no test exercises the repository's own `int` return directly) — added
  `repositoryUpsertEmailReturnsAffectedRowCountDirectly`. **Real bug found while writing this test**:
  calling `repository.upsertEmail(...)` directly (not through `ContactProjectionUpdater`, which
  supplies its own `@Transactional`) failed with `InvalidDataAccessApiUsage: No EntityManager with
  actual transaction available for current thread - cannot reliably process 'flush' call` —
  `flushAutomatically = true` needs an open transaction to flush against. Fixed by wrapping both
  direct calls in a `TransactionTemplate`, mirroring the pattern already used elsewhere in this file.
- **Gap #4** (no `citext` case-preservation proof) — added `citextPreservesOriginalCaseOnReadBack`.
- **Gap #5** (stale-write rejection only proven via the Hibernate read path) — folded into
  `olderCallDoesNotOverwriteANewerProjection`: added a direct JDBC assertion alongside the existing
  entity-based one.
- **Gap #6** (`display_name` not re-checked after a rejected stale write) — folded into the same
  test: added a `getDisplayName()` null assertion after the rejected write.

**Verification:** `mvn -pl services/notification clean verify` — 92 tests, 0 failures. A second real
mutation test performed and reverted clean (`git status -s` empty afterward): removed the `WHERE`
clause entirely, confirmed only Gap #1's own new static-scan test caught it (the behavioral
`olderCallDoesNotOverwriteANewerProjection` test was not re-run in this specific check, since the
static scan is deliberately the faster, cheaper permanent guard Gap #1 asked for).
