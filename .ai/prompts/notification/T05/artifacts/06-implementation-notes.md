# notification · T05 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). `src/main` files created, plus the two justified,
frozen-brief-authorized test-file updates (T01/T02's own files) — no new test file authored (Phase
10 scope, per this phase's own rule), mirroring T04's own precedent exactly.

## Files created

- `preference/ContactProjection.java` — `@Table(name = "contact_projection", schema =
  "notifications")`, client-assigned `@Id` on `accountUuid`, `email` mapped
  `columnDefinition = "citext"` (Kimi Finding #1), `displayName` nullable (Finding #5, never written
  by this task), no `create(...)` factory (mirrors T04's own post-Phase-9 `ProcessedEvent` shape —
  the write path never constructs an entity instance).
- `preference/ContactProjectionRepository.java` — one native upsert query,
  `ON CONFLICT (account_uuid) DO UPDATE ... WHERE updated_at <= EXCLUDED.updated_at`, the
  out-of-order guard with its `<=` tie-break rationale documented in the method's own Javadoc
  (Finding #2).
- `preference/ContactProjectionUpdater.java` — the public wrapper `consumer.AuthEventConsumer`
  (task 6) will call; `@Transactional` default `REQUIRED`, no `Clock` dependency (takes the event's
  own `occurredAt`).
- `db/migration/V5__notification_app_contact_projection_grant.sql` — `INSERT, SELECT, UPDATE`, with
  the rationale comment Finding-set required.

## Files modified

- `T01SkeletonRegressionTest.java` — `noExtraProductionClassesExistBeyondT04sOwnAuthorizedSet`
  renamed to `...T05sOwnAuthorizedSet` (Finding #4); 12-file list widened to 15.
- `NotificationBaselineMigrationIntegrationTest.java` — `contact_projection` removed from
  `UNGRANTED_TABLES` (and its 3 now-dead cases removed from the ungranted-side fixture switches);
  a new, self-contained `notificationAppCanInsertSelectAndUpdateButNotDeleteOnContactProjection`
  test added, proving `INSERT`/`SELECT`/`UPDATE` succeed and `DELETE` is denied (Finding #3 — the
  first table in this module where a real `UPDATE`-succeeds proof is required, since T04's own
  `processed_events` test never needed one); Flyway-history expectation widened to
  `"1","2","3","4","5"`.

## Verification performed

- `mvn -pl services/notification clean verify` — 80 tests, 0 failures, clean `package`/`repackage`.
- **Kimi Finding #1's own real risk, verified empirically, not by inspection**: every
  Testcontainers-backed `@SpringBootTest` in this suite (including the pre-existing
  `IdempotencyGuardIntegrationTest`, which now also boots with `ContactProjection` on the classpath)
  logged `Found 2 JPA repository interfaces` and `Initialized JPA EntityManagerFactory for
  persistence unit 'default'` with **no schema-validation error** — confirming
  `columnDefinition = "citext"` alone is sufficient for `ddl-auto=validate` to accept the real
  `citext` column, exactly as Finding #1's own recommended minimum fix predicted.
- `V5` migrated successfully in every Testcontainers run (`Successfully applied 5 migrations ...
  now at version v5`).

## Acceptance criteria mapping

- **AC1** — `ContactProjection` maps exactly onto the 4 existing columns, explicit schema and
  `citext` column definition. ✅ (validated against the real schema by Hibernate itself.)
- **AC2/AC3** — the upsert mechanism and its out-of-order guard are implemented as designed; real
  proof of both (new-row creation, later-write-wins, older-write-rejected) is Phase 10's own job per
  this phase's "no tests" rule — not yet automated, but the SQL shape itself is in place and was
  exercised implicitly (no error) by every Testcontainers boot in this suite.
- **AC4** — `displayName` is never written anywhere in this task's own code — confirmed by
  inspection (`ContactProjectionUpdater.upsertEmail`'s only repository call is `upsertEmail`, which
  has no `displayName` parameter at all).
- **AC5** — `V5` grants `notification_app` `INSERT, SELECT, UPDATE` on `contact_projection`;
  `DELETE` still denied — proven by the new dedicated test.
- **AC6** — both a unit test and a Testcontainers integration test remain Phase 10's own job, per
  this phase's own rule.
