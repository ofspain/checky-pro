# auth · T17 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the three test files it describes.

No duplicate tests were found, and none of the current test designs look flaky on their own. The integration suite's inability to run to green in this sandbox is the pre-existing Testcontainers/environment issue documented in Phase 10, not a test-design problem.

The following gaps remain against the acceptance criteria in `artifacts/04-frozen-task-brief.md` and the named tests in `spec/auth-service/package.md` §8.

---

1. **Gap · Positive case for `MfaEnrollmentRepository.findAccountIdByUuid` is missing.**
   `findAccountIdByUuidReturnsEmptyForUnknownUuid` only asserts the negative path. The production usage is resolving a real account's internal id from its UUID; a broken native-query column/table reference could silently return empty for known UUIDs and still pass the existing test.
   **Suggested test:** `findAccountIdByUuidResolvesExistingAccount` in `MfaPersistenceIntegrationTest` that registers/activates an account and asserts the method returns the row's `accounts.id`.

2. **Gap · `findByAccountIdAndType`'s "queryable" contract is not directly asserted.**
   AC5 requires the finder to expose whether an enrollment already exists, and the unique-constraint test proves the DB enforces at-most-one row. It does not prove the derived query returns the saved row with the correct fields. A wrong parameter order or a silently broken query could return `Optional.empty()` while the duplicate-insert test still passes.
   **Suggested test:** `findByAccountIdAndTypeReturnsSavedEnrollment` that saves an unconfirmed TOTP enrollment and asserts the finder returns it with matching `id`, `type`, `accountId`, and `secretEncrypted`.

3. **Gap · `RecoveryCodeRepository.findByAccountIdAndCodeHash` boundary is only half-tested.**
   `findByAccountIdAndCodeHashFindsTheSpecificCode` proves a matching hash is found, but does not prove the query distinguishes hashes or accounts. If the query accidentally ignored `codeHash` or `accountId`, the existing test could pass while cross-account or wrong-hash lookups leaked or misidentified codes.
   **Suggested test:** `findByAccountIdAndCodeHashReturnsEmptyForWrongHashOrOtherAccount` saving different hashes for two accounts and asserting that each account/hash combination only returns its own row.

4. **Gap · Round-trip column assertions for mapping completeness are weak.**
   AC1 and AC3 require every column to be mapped correctly, but `mfaEnrollmentMapsAllColumnsAndPersistsUnconfirmed` and `recoveryCodeMapsAllColumnsAndMultipleRowsPersistPerAccount` check only id/null flags/count/secret. They do not reload from the database and assert `account_id`, `type`, `created_at` (enrollment) or `code_hash`, `created_at` (recovery code). A wrong `@Column(name=...)` or missing `updatable=false` could survive.
   **Suggested test:** enhance the two mapping tests (or add `*ReloadsAllColumnsFromDb`) to fetch the saved row via `EntityManager.find` or the repository and assert each mapped field equals the input value.

5. **Gap · `confirm` second-call state is not asserted.**
   `confirmTwiceThrowsIllegalStateException` verifies the exception and message, but does not assert that `confirmedAt` stays at the first timestamp after the second call. A regression that mutates the field before throwing would still pass.
   **Suggested test:** add an assertion to the existing test (or a new `confirmTwiceLeavesFirstTimestamp`) that `enrollment.getConfirmedAt()` equals the first confirmation instant after the second `confirm` invocation.

6. **Gap · Concurrency proof for `RecoveryCodeRepository.markUsed` atomicity is missing.**
   `markUsedIsAtomicAndSucceedsOnlyOnce` is sequential; it verifies the conditional update logic but not that two simultaneous calls cannot both succeed. AC7's "only succeeds the first time" is meant to be race-safe.
   **Suggested test:** `markUsedIsAtomicUnderConcurrentRedemption` that submits two concurrent `markUsed` calls for the same code and asserts exactly one returns `1`, total affected rows is `1`, and the final `usedAt` matches the winner.

7. **Gap · `RecoveryCode.codeHash` length boundary (L6) is not exercised.**
   `RecoveryCode.codeHash` is declared as a 64-character SHA-256 hex digest via `@Column(length=64, columnDefinition="char(64)")`, but no test proves the schema rejects a longer value. A future regression that relaxes the column mapping could persist invalid hashes.
   **Suggested test:** `persistRejectsCodeHashLongerThan64Characters` in `MfaPersistenceIntegrationTest` that attempts to save a 65-character hash and expects a persistence/constraint failure.

8. **Gap · Named test `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` is not reachable at T17.**
   This named test (`package.md` §8, R20/R23) spans service/controller layers: provisioning URI generation, recovery-code creation, plaintext return exactly once, and exactly 10 single-use codes. T17 correctly scoped it out, but it is still a coverage gap against the feature spec.
   **Suggested test:** tasks 18/19 should implement a `@SpringBootTest` named exactly `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` that exercises the full confirm endpoint and asserts the returned codes are 10, single-use, and stored only as hashes.
