# notification · T05 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/preference/ContactProjection.java`
2. `.../preference/ContactProjectionRepository.java`
3. `.../preference/ContactProjectionUpdater.java`
4. `services/notification/src/main/resources/db/migration/V5__notification_app_contact_projection_grant.sql`
5. `services/notification/src/test/java/com/themistra/notification/preference/ContactProjectionUpdaterUnitTest.java`
6. `services/notification/src/test/java/com/themistra/notification/preference/ContactProjectionUpdaterIntegrationTest.java`

## Files to modify

1. `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
   — `noExtraProductionClassesExistBeyondT04sOwnAuthorizedSet` renamed to
   `...T05sOwnAuthorizedSet`; 12-file list becomes 15 (adds the 3 new `preference/` files).
2. `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
   — `contact_projection` removed from `UNGRANTED_TABLES`; a new, self-contained
   `notificationAppCanInsertSelectAndUpdateButNotDeleteOnContactProjection` test added (mirrors T04's
   own dedicated `processed_events` test — `contact_projection`'s schema doesn't fit the
   `GRANTED_TABLES` shared helper any better than `processed_events` did, and now also needs a
   genuine `UPDATE`-succeeds proof the shared helper never had to make, since T04's own table was
   insert-only); Flyway-history expectation widens to `"1","2","3","4","5"`.

No files outside this list. `V1-V4`, `pom.xml`, and everything under `spec/` are untouched, per
frozen brief.

## Public methods (signatures)

**`ContactProjection`** (`@Entity`, `@Table(name = "contact_projection", schema = "notifications")`)
```java
@Entity
@Table(name = "contact_projection", schema = "notifications")
public class ContactProjection {
    @Id
    @Column(name = "account_uuid")
    private UUID accountUuid;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContactProjection() {} // JPA only

    public UUID getAccountUuid();
    public String getEmail();
    public String getDisplayName();
    public Instant getUpdatedAt();
}
```
No `create(...)` factory (T04's own Phase 9 lesson: this entity is never constructed by application
code — only Hibernate, via `findById`, ever instantiates it; the write path is the repository's own
native upsert, taking primitive arguments directly). `columnDefinition = "citext"` is required
(Kimi Phase 3 Finding #1) — no `@JdbcType`, deferred per Finding #6.

**`ContactProjectionRepository`** (package-private, mirrors `ProcessedEventRepository`'s shape)
```java
interface ContactProjectionRepository extends JpaRepository<ContactProjection, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.contact_projection (account_uuid, email, updated_at) "
            + "VALUES (:accountUuid, :email, :updatedAt) "
            + "ON CONFLICT (account_uuid) DO UPDATE SET email = EXCLUDED.email, updated_at = EXCLUDED.updated_at "
            + "WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at",
            nativeQuery = true)
    int upsertEmail(@Param("accountUuid") UUID accountUuid, @Param("email") String email,
                     @Param("updatedAt") Instant updatedAt);
}
```
Returns the affected-row count (0 or 1) — always 1 on a genuine insert or an accepted update, 0 when
the `WHERE` guard rejects an out-of-order update (Kimi Phase 3 Finding #2: `<=` means ties go to the
later-processed call).

**`ContactProjectionUpdater`**
```java
@Service
public class ContactProjectionUpdater {

    private final ContactProjectionRepository repository;

    public ContactProjectionUpdater(ContactProjectionRepository repository);

    @Transactional
    public void upsertEmail(UUID accountUuid, String email, Instant occurredAt) {
        repository.upsertEmail(accountUuid, email, occurredAt);
    }
}
```
No `Clock` dependency (Phase 2's own design note: `occurredAt` comes from the event itself, not a
freshly-sampled clock). `@Transactional` default `REQUIRED` propagation, same rationale as
`IdempotencyGuard`.

## Private methods

None.

## Entities used

`ContactProjection` (new, this task).

## Repositories used

`ContactProjectionRepository` (new, this task).

## Services used

`ContactProjectionUpdater` (new, this task) — depends on `ContactProjectionRepository`.

## Unit / integration tests required

1. **`ContactProjectionUpdaterUnitTest`** (plain JUnit + Mockito, no Spring context, no Docker):
   - `upsertEmail` calls `repository.upsertEmail` with the exact arguments passed through unchanged
     (mirrors `IdempotencyGuardUnitTest.shouldPassTheSuppliedEventTypeThroughUnchanged`'s own style).
   - `recordIfNew`-equivalent reflection test: `upsertEmail`'s own `@Transactional` carries no
     `REQUIRES_NEW`/`NOT_SUPPORTED` propagation (mirrors `IdempotencyGuardUnitTest.recordIfNewUsesDefaultRequiredPropagation`).
2. **`ContactProjectionUpdaterIntegrationTest`** (`@Testcontainers` + `@SpringBootTest`, real
   Postgres, mirrors `IdempotencyGuardIntegrationTest`'s own setup exactly — static container,
   `@BeforeAll` citext/migrate/password provisioning, `@DynamicPropertySource` pointing the real app
   at the container as `notification_app`):
   - First call for a new `account_uuid` creates a row with the given `email`/`updated_at`,
     `display_name` null (Kimi Phase 3 Finding #5).
   - A second call for the same `account_uuid` with a *later* `occurredAt` updates `email`/`updated_at`.
   - A call with an *older* `occurredAt` than the row's current `updated_at` does **not** overwrite
     `email`/`updated_at` (Kimi Phase 3 Finding #2's own out-of-order proof — AC3).
   - `display_name` remains null after both the insert and the update path (Finding #5).
   - This is the first `@Entity` in the module mapping a `citext` column — Hibernate's own
     `ddl-auto=validate` must succeed at context startup with no schema-mismatch error (Kimi Phase 3
     Finding #1's own real risk, verified here empirically, not by inspection).
3. **`NotificationBaselineMigrationIntegrationTest`**'s new dedicated test:
   `notificationAppCanInsertSelectAndUpdateButNotDeleteOnContactProjection` — real `INSERT`/`SELECT`/
   `UPDATE` succeed as `notification_app`, real `DELETE` is denied (Kimi Phase 3 Finding #3).
4. **`T01SkeletonRegressionTest`** — re-run with its renamed method and 15-file list.

## Execution order

1. `ContactProjection` (no dependencies on anything else new).
2. `ContactProjectionRepository` (depends on `ContactProjection`).
3. `ContactProjectionUpdater` (depends on `ContactProjectionRepository`).
4. `V5__notification_app_contact_projection_grant.sql` (independent of the Java changes; needed
   before step 6's integration test can connect as `notification_app` with `UPDATE` rights at all).
5. `ContactProjectionUpdaterUnitTest` (steps 1-3's own proof, no DB needed).
6. `ContactProjectionUpdaterIntegrationTest` (needs steps 1-4 all in place).
7. `T01SkeletonRegressionTest`'s renamed method and updated file list (now true, since steps 1-3 exist).
8. `NotificationBaselineMigrationIntegrationTest`'s updated grant coverage (needs step 4's `V5` to be
   a real, migratable file).
