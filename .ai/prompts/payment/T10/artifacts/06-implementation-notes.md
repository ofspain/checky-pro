# payment · T10 · Phase 6 — Implementation Notes

Implemented exactly per `artifacts/05-implementation-plan.md`. No test files written this phase
(Phase 10's own job).

## Files created

- `VerificationRecord.java` — maps `verification_records` exactly; `fromState`/`toState` are
  `@Enumerated(STRING)` `PaymentState`; `detail` uses Hibernate 6's native
  `@JdbcTypeCode(SqlTypes.JSON)` (`org.hibernate.annotations`/`org.hibernate.type.SqlTypes`) —
  compiled clean with no additional Maven dependency, confirming Phase 1's own research. A
  `@PrePersist onCreate()` last-resort `Instant.now()` fallback mirrors `Invoice`/`OutboxEvent`'s
  own identical precedent.
- `VerificationRecordRepository.java` — plain `JpaRepository`, package-private, no custom method.
- `VerificationRecordService.java` — `record(...)` and `recordIgnored(...)`, both
  `@Transactional`. `recordIgnored` stores `fromState = null` per Phase 3/4 Finding 1's own
  decided change (not `currentState`).

## Files modified

- `ArchitectureTest.java` — `FEATURE_MODULES` gains `"verification"`; its own Javadoc updated to
  name all four modules and explain `payment`'s own inclusion (no entity yet, included for
  forward consistency since it's a real business-feature package, not cross-cutting like
  `events`).

## Verification performed this phase

- `mvn -pl services/payment -am compile` / `test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='ArchitectureTest'` — 5/5 passing (the
  `FEATURE_MODULES` addition introduced no regression; no `VerificationRecord`-specific rogue
  fixture needed since this task's own entity imports nothing across a module boundary).
- Full non-Docker-dependent suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'` —
  **183/183 passing, unchanged** (no new tests yet — Phase 10's own job).

## Net result

Production code for `VerificationRecord`/`VerificationRecordRepository`/
`VerificationRecordService` complete per the frozen brief's own Finding-1-revised design
(`fromState = null` for ignored events). `ArchitectureTest` extended cleanly. Proceeding to
Phase 7.
