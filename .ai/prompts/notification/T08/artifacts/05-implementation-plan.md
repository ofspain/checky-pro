# notification · T08 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/preference/ChannelPreference.java`
2. `.../preference/ChannelPreferenceRepository.java`
3. `.../preference/PreferenceResolver.java`
4. `services/notification/src/main/resources/db/migration/V6__notification_app_channel_preferences_grant.sql`

## Files to modify

1. `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
   (Finding #5) — Flyway-history expectation `"1".."6"`; new grant-proof test for
   `channel_preferences`.

No files outside this list this phase (`T01SkeletonRegressionTest.java` remains a Phase 6 disclosed
deviation per the frozen brief's own note, not pre-authorized here).

## Public methods (signatures)

**`ChannelPreference`** (`@Entity`, `preference/`)
```java
@Entity
@Table(name = "channel_preferences", schema = "notifications")
public class ChannelPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_uuid", nullable = false)
    private UUID accountUuid;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelPreference() {} // JPA only

    // getters only — no write path in this task (Finding #4's own rationale)
}
```

**`ChannelPreferenceRepository`** (interface, package-private, `preference/`)
```java
interface ChannelPreferenceRepository extends JpaRepository<ChannelPreference, Long> {
    // Kimi Phase 3 Finding #4: extends JpaRepository per T04/T05's own established precedent -
    // save/delete are inherited but never called; V6 grants SELECT only, so any accidental call
    // fails at the DB layer regardless. findByAccountUuidAndCategoryAndChannel is the only
    // sanctioned method.
    Optional<ChannelPreference> findByAccountUuidAndCategoryAndChannel(
            UUID accountUuid, String category, String channel);
}
```

**`PreferenceResolver`** (`@Service`, `preference/`)
```java
@Service
public class PreferenceResolver {
    public PreferenceResolver(ChannelPreferenceRepository repository);

    public boolean resolve(UUID accountUuid, String category, String channel);
}
```
Logic (in order):
1. Require non-null `accountUuid`/`category`/`channel` (Finding #6 — a caller contract, not
   defensively handled; a `null` argument is expected to surface as a real `NullPointerException`
   from the uppercase normalization step, not silently swallowed).
2. Uppercase `category`/`channel` (Finding #2, `String.toUpperCase(Locale.ROOT)`).
3. If `(category, channel) == ("SECURITY", "EMAIL")`, return `true` unconditionally — never queries
   the repository for this specific pair (Phase 1's own resolved open question, option (a)).
4. Otherwise, query `repository.findByAccountUuidAndCategoryAndChannel(...)`; if present, return its
   `enabled` value.
5. Otherwise, look up the default table (a `Map<String, Boolean>` keyed by `"CATEGORY:CHANNEL"`,
   or an equivalent `switch`) for exactly the 6 named pairs; if the pair isn't in that table
   (`WEBHOOK`/`PUSH`/anything else — Finding #1/#3), return `false`.

## Private methods

- `PreferenceResolver` needs one private helper: `defaultFor(String category, String channel)` —
  returns `Optional<Boolean>` for the 6 named pairs, `Optional.empty()` otherwise (step 5 above
  then maps that to `false`). Kept private and small; the 6-entry table is the frozen brief's own
  VERBATIM default table, copied exactly with the exact DB constant spelling (Finding #8).

## Entities used

`ChannelPreference` (new, this task).

## Repositories used

`ChannelPreferenceRepository` (new, this task).

## Services used

None — `PreferenceResolver` has no dependency on any other module's own service (T04's
`IdempotencyGuard`, T05's `ContactProjectionUpdater`, T06's `NotificationDispatcher` are all
irrelevant to this task, which is pure read-and-resolve logic with no caller yet).

## Unit / integration tests required

Deferred to Phase 10 (per this module's own established rule) — no Phase 6 carve-out this task
(unlike T06's contract tests, nothing here encodes an external structural guarantee that must be
proven before other Phase 6 code can be trusted; T05's own precedent of "no new test file authored
in Phase 6" applies instead). Planned Phase 10 coverage:

1. `PreferenceResolver` returns the stored `enabled` value when a row exists, for a non-`SECURITY`/
   `EMAIL` pair (AC2).
2. `PreferenceResolver` returns each of the 6 documented defaults when no row exists (AC3).
3. `PreferenceResolver` returns `true` for `SECURITY`/`EMAIL` both when no row exists and
   (defensively) when a row exists with `enabled = false` (AC4).
4. `PreferenceResolver` returns `false` for `WEBHOOK`, `PUSH`, and one made-up unknown pair,
   whether or not a row exists for them (AC6 — a stored row for an unsupported channel/category
   is out of this task's own control, but the resolver's own fallback logic should still be proven
   not to consult the default table for something absent from it).
5. Case-insensitivity: `resolve(uuid, "security", "email")` equals
   `resolve(uuid, "SECURITY", "EMAIL")` (AC7).
6. `V6` grant-proof, in `NotificationBaselineMigrationIntegrationTest`: `notification_app` can
   `SELECT` but not `INSERT`/`UPDATE`/`DELETE` on `channel_preferences` (Finding #5, AC5).

## Execution order

1. `ChannelPreference` (no dependencies on anything else new).
2. `ChannelPreferenceRepository` (depends on step 1).
3. `V6__notification_app_channel_preferences_grant.sql` (independent of 1/2, but needed before any
   Testcontainers-backed test in step 6 below can pass).
4. `PreferenceResolver` (depends on steps 1-2).
5. `NotificationBaselineMigrationIntegrationTest` update (Finding #5) — depends on step 3 existing.
6. Phase 10's own planned tests (depends on steps 1-4).
