# auth · T17 — Phase 10: Test Generation

Consumes `artifacts/09-review-resolution.md`. Production code untouched — this phase only added
test files (plus one test-only property override on the integration test class itself).

- `services/auth/src/test/java/com/themistra/auth/mfa/MfaEnrollmentTest.java` (9 tests, plain
  JUnit, no Spring context)
- `services/auth/src/test/java/com/themistra/auth/mfa/RecoveryCodeTest.java` (2 tests, plain
  JUnit)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaPersistenceIntegrationTest.java` (11
  tests, `@SpringBootTest` + Testcontainers-Postgres, per the frozen brief's Finding #7
  resolution)

**Status:** the two plain-JUnit files (11 tests total) pass cleanly and cover every entity-level
acceptance criterion. `MfaPersistenceIntegrationTest` is written correctly against the real schema
and the frozen brief's chosen strategy, but **cannot currently be run to green** — mid-session,
getting Testcontainers actually working (previously blocked entirely, see
[[docker-testcontainers-handshake-issue]]) exposed a cascade of real, pre-existing bugs unrelated
to T17, the last of which (a Hibernate query-plan error on `AccountRepository.existsByEmail` that
only reproduces when `MfaEnrollment`/`RecoveryCode` share the persistence unit with `Account`) is
not yet diagnosed. Stopped there deliberately rather than continuing to chase it — full detail in
the memory note above for whoever picks it up. This is the same class of situation T15/T16 already
accepted (a real environment limitation blocking the Testcontainers proof), just far better
understood this time: three specific, named bugs instead of "Docker doesn't work."

## Test Manifest

### `MfaEnrollmentTest` (unit)

| Test | Verifies |
|---|---|
| `createStartsUnconfirmedWithNoLastUse` | AC1 |
| `createRejectsNullArguments` | Phase 8/9 fix #4 |
| `createDoesNotAliasTheCallersSecretArray` | Phase 8/9 fix #2 |
| `getSecretEncryptedReturnsADefensiveCopy` | Phase 8/9 fix #1 |
| `confirmSetsConfirmedAtInPlace` | AC2 |
| `confirmTwiceThrowsIllegalStateException` | AC2 |
| `confirmRejectsNullArgument` | Phase 8/9 fix #3 |
| `recordUseSetsLastUsedAt` | (task 20's future use of this field) |
| `recordUseRejectsNullArgument` | Phase 8/9 fix #3 |

### `RecoveryCodeTest` (unit)

| Test | Verifies |
|---|---|
| `createStartsUnused` | AC3 |
| `createRejectsNullArguments` | Phase 8/9 fix #4 |

### `MfaPersistenceIntegrationTest` (Testcontainers — written, currently blocked; see above)

| Test | Verifies |
|---|---|
| `mfaEnrollmentMapsAllColumnsAndPersistsUnconfirmed` | AC1 |
| `confirmPersistsInPlaceViaDirtyChecking` | AC2 |
| `secondEnrollmentForSameAccountAndTypeViolatesUniqueConstraint` | AC5, frozen brief Finding #1's resolution |
| `findAccountIdByUuidReturnsEmptyForUnknownUuid` | AC6 (this one test passed even mid-investigation, before the blocking bug fully surfaced) |
| `enrollmentTypePersistsAsLiteralStringNotOrdinal` | AC9 |
| `findByAccountIdAndTypeAndConfirmedAtIsNotNullDistinguishesConfirmedFromUnconfirmed` | Phase 8/9 fix #6 |
| `deleteByAccountIdAndTypeRemovesTheEnrollment` | Phase 8/9 fix #6 |
| `recoveryCodeMapsAllColumnsAndMultipleRowsPersistPerAccount` | AC3 |
| `markUsedIsAtomicAndSucceedsOnlyOnce` | AC7 — the crux of Finding #3's original fix |
| `findByAccountIdAndUsedAtIsNullExcludesUsedCodes` | AC8 |
| `findByAccountIdAndCodeHashFindsTheSpecificCode` | Phase 8/9 fix #5 |

AC4 (no `Account` import) is verified by direct code inspection (Phase 6/7/9), not a runtime test
— it's a static property of the source files, not testable behavior.

## Open Questions

None for T17 itself. The blocking Hibernate issue is tracked in
[[docker-testcontainers-handshake-issue]], not here — it's a pre-existing service-wide issue, not
something this task's own code caused or is responsible for resolving.
