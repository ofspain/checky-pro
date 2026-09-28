STATUS: FROZEN

# notification · T05 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings independently verified against source (`services/auth/src/main/java/com/themistra/auth/account/{Account,CitextJdbcType}.java`) before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `email` CITEXT mapping unspecified, likely fails `ddl-auto=validate` | High | **ACCEPTED** | Verified: `Account.email` needs both `@Column(columnDefinition = "citext")` (for schema validation) and `@JdbcType(CitextJdbcType.class)` (for parameter binding in `WHERE email = ?` queries — `ObjectJdbcType`'s own `String`-as-`Serializable` special case otherwise binds as `VarbinaryJdbcType`, producing `"Could not convert 'java.lang.String' to '[B'"`). `ContactProjection.email` gets `@Column(name = "email", columnDefinition = "citext")` — required, since every Testcontainers `@SpringBootTest` in this task triggers real `ddl-auto=validate` against the real `citext` column. The custom `JdbcType` itself is **deferred** (Kimi's own offered option (b)): T05 never queries by `email` (only by `account_uuid`, via `insertIfNew`'s native query and `findById`/`existsById`), so the binder problem `CitextJdbcType` solves doesn't arise in this task's own scope — folded into Finding #6's own disposition below, not built speculatively. |
| 2 | Out-of-order guard's `<=` tie-breaking semantics undocumented | Medium | **ACCEPTED** | Keeping `<=` (ties go to whichever is later-processed), now explicitly documented: both source events derive `email` from the same `Account.email` field at auth-service, so two events for the same account at genuinely equal `occurredAt` cannot carry conflicting `email` values in practice — the tie-break choice is safe either way, `<=` chosen for consistency with "redelivery of the identical event is a harmless no-op" (re-applying the same `occurredAt`/`email` under `<=` still succeeds, under `<` it would silently skip — both are correct outcomes for a true redelivery, but `<=` avoids a redundant no-op skip being visible in row-count-style test assertions). |
| 3 | No explicit test requirement that `notification_app` cannot `DELETE` | Medium | **ACCEPTED** | Added to Required Tests: the `contact_projection` grant-proof integration test must attempt `DELETE` as `notification_app` and assert `permission denied`, not just assert the 3 allowed privileges positively. |
| 4 | `T01SkeletonRegressionTest`'s method name stays T04-specific | Low | **ACCEPTED** | `noExtraProductionClassesExistBeyondT04sOwnAuthorizedSet` renamed to `...T05sOwnAuthorizedSet` when the list is widened to 15 files, mirroring T04's own rename of T03's equivalent method. |
| 5 | `displayName` nullability not explicit | Low | **ACCEPTED** | `ContactProjection.displayName` mapped `@Column(name = "display_name")` (nullable by omission, matching `V1`'s own `VARCHAR(200)` with no `NOT NULL`). Added to Required Tests: the integration test asserts `getDisplayName()` is `null` after both the insert and the update path. |
| 6 | Future email-delivery tasks may need `CitextJdbcType`, deferral is unstated | Low | **ACCEPTED** | Added explicitly under Out/Open Questions below: any future task adding a query-by-`email` (e.g., a preference-resolution or delivery task needing to look up a projection by address) must introduce or reuse a `CitextJdbcType` at that time — T05 itself needs only the `columnDefinition` half of the fix. |

## Task

Unchanged from Phase 2, with all 6 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `ContactProjection.email` mapped `@Column(name = "email", columnDefinition = "citext")` (Finding #1,
  schema-validation half only — no custom `JdbcType`, deferred per Finding #6).
- `ContactProjection.displayName` mapped nullable, no `nullable = false` (Finding #5).
- Out-of-order guard's `<=` semantics documented in `ContactProjectionRepository`'s own Javadoc
  (Finding #2) — no SQL change, `<=` was already the plan.
- Required Tests gains: a `DELETE`-denied assertion in the grant-proof test (Finding #3); a
  `displayName IS NULL` assertion after both insert and update (Finding #5).
- `T01SkeletonRegressionTest`'s widened-list method renamed to
  `noExtraProductionClassesExistBeyondT05sOwnAuthorizedSet` (Finding #4).

**Out (unchanged from Phase 2, plus):** a notification-local `CitextJdbcType` — explicitly deferred
to whichever future task first needs a query-by-`email` (Finding #6), not this task's own scope.

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L2.

## Dependencies

Unchanged from Phase 2.

## Inputs

Unchanged from Phase 2, plus: `services/auth/src/main/java/com/themistra/auth/account/{Account,CitextJdbcType}.java`
as the direct, verified precedent for the `citext` mapping problem and its two-part fix.

## Outputs

Unchanged from Phase 2.

## State Changes

Unchanged from Phase 2.

## Files to Create

Unchanged from Phase 2.

## Files to Modify

Unchanged from Phase 2 (both file names carried over; the `T01SkeletonRegressionTest` method rename
happens within the same file already listed).

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1-AC6, with AC1 now requiring the explicit `columnDefinition = "citext"`
mapping (Finding #1) and AC5 now requiring an active `DELETE`-denial proof, not just the 3 allowed
privileges (Finding #3).

## Required Tests

Unchanged from Phase 2, plus: the `DELETE`-denial assertion (Finding #3) and the `displayName IS
NULL` assertion after both insert and update (Finding #5), both folded into the already-planned
Testcontainers integration test.

## Constraints

Unchanged from Phase 2, plus: `ContactProjection.email`'s mapping must include `columnDefinition =
"citext"` or `ddl-auto=validate` fails at every Testcontainers `@SpringBootTest`'s own startup
(Finding #1) — verify this empirically at Phase 6, not by inspection alone, mirroring T03/T04's own
"verify, don't assume" discipline for exactly this class of Hibernate/Postgres interaction.

## Open Questions

No blockers. All 6 Phase 3 findings resolved above. One deferred item, explicitly tracked (not
open/blocking): a `CitextJdbcType` for `ContactProjection.email`, needed only once a future task
queries this table by `email` rather than `account_uuid` (Finding #6).
