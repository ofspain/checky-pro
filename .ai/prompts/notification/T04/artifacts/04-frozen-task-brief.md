STATUS: FROZEN

# notification · T04 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified against source before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `recordIfNew` unsafe under concurrent duplicate calls (TOCTOU race) | High | **ACCEPTED** | A real defect in the Phase 2 design, not just the test plan: two concurrent transactions can both observe `existsById=false`, then both attempt `save`, and the loser fails with a `DataIntegrityViolationException` rather than returning `false`. Fixed by wrapping `repository.save(...)` in a try/catch for `DataIntegrityViolationException`, returning `false` on catch (Kimi's own option (a) — simpler than a native `ON CONFLICT` query, and keeps the method fully within the JPA repository abstraction the rest of this codebase uses). The Testcontainers test now explicitly drives two real concurrent calls (two threads, same key) and asserts exactly one `true`, the rest `false`, no unchecked exception escapes. |
| 2 | Open Question references allegedly copy-pasted from another task (design.md §11's Q1/Q2/Q3) | Medium | **REJECTED — verified false against source** | Checked directly: the Phase 2 TIB's own text says "resolving **Phase 1's Open Question #1/#2/#3**" every time (lines 27, 38, 44 of `artifacts/02-task-implementation-brief.md`) — this refers to `artifacts/01-specification-extraction.md`'s own 3 self-numbered Open Questions (the dedupe helper's shape, the grant migration, and transaction-join verification), never to `design.md` §11's globally-numbered Q1-Q8. The TIB does not mention Q1 (recipient resolution), Q2 (email transport), or Q3 (SSE/WebSocket) anywhere. Kimi's own finding conflates two independent numbering schemes. **No content is wrong or copy-pasted.** As a low-cost courtesy fix (not because the finding's claim was correct, but because the naming collision it stumbled into is real and could mislead a future reader the same way), Phase 2's own 3 references are renamed from "Open Question #N" to "Dedupe-Shape Question" / "Grant-Scope Question" / "Transaction-Join Question" respectively, so no reader can conflate them with `design.md`'s own Q-numbers again. |
| 3 | `T01SkeletonRegressionTest` must be updated but was listed "do not modify" | Medium-High | **ACCEPTED** | Verified: the file's current `noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet()` asserts an exact 8-file list; T04 adds 4 new production files (12 total), which would fail that assertion unmodified. Moved to **Files to Modify**: the 8-file list becomes 12, alphabetically ordered per the existing convention, with the same explicit-naming discipline T03 itself used (never loosened to a count check). Disclosure requirement carried into Phase 6/7 self-review, mirroring T03's own precedent exactly. |
| 4 | `NotificationBaselineMigrationIntegrationTest`'s stale `UNGRANTED_TABLES` flagged but not authorized to fix | Medium | **ACCEPTED** | Moved to **Files to Modify**, explicitly authorized and required: remove `processed_events` from `UNGRANTED_TABLES`, add it to `GRANTED_TABLES`-equivalent proof (reusing the existing `assertInsertAndSelectSucceedUpdateAndDeleteAreDenied`-style helper, which already generalizes across tables via its `insertStatementFor`/`noWhereUpdateStatementFor`/`noWhereDeleteStatementFor` switch statements — this task adds a `"processed_events"` case to each). Disclosed as a justified T04-driven amendment to T02's own test file, not a silent rewrite. |
| 5 | `ProcessedEvent`'s schema mapping unspecified (implicit `search_path` reliance) | Low | **ACCEPTED** | `@Table(name = "processed_events", schema = "notifications")` now required explicitly, matching the Flyway migrations' own explicit schema-qualification and removing reliance on `search_path` ordering. |
| 6 | `@Transactional` propagation should be stated explicitly, not just in prose | Low | **ACCEPTED** | Method must be annotated exactly `@Transactional` (relying on Spring's own documented `REQUIRED` default, the same style `agents.md`/sibling services use elsewhere) — not an explicit `Propagation.REQUIRED` (redundant) and never `REQUIRES_NEW`/`NOT_SUPPORTED`. A reflection-based regression test asserting the annotation carries no divergent `propagation` attribute is added to Required Tests. |
| 7 | `ProcessedEvent` getter/accessor shape unspecified | Low | **ACCEPTED** | Read-only getters for `eventKey`, `eventType`, `processedAt`; no setters — mirrors `OutboxEvent` exactly. |
| 8 | `V4` grant migration should explain the append-only rationale in a comment | Low | **ACCEPTED** | Migration file must include a comment explaining why `UPDATE`/`DELETE` are deliberately absent (a processed event key is immutable once recorded), mirroring `crypto`'s own `V3__crypto_app_outbox_grant.sql` documentation style. |

## Task

Unchanged from Phase 2, with all 8 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `IdempotencyGuard.recordIfNew` catches `DataIntegrityViolationException` around `save`, returning
  `false` (Finding #1).
- `ProcessedEvent` carries `@Table(name = "processed_events", schema = "notifications")` (Finding #5)
  and read-only getters for all 3 fields, no setters (Finding #7).
- `V4__notification_app_processed_events_grant.sql` includes an explanatory comment on the
  `INSERT`/`SELECT`-only scope (Finding #8).
- `T01SkeletonRegressionTest.java` — moved to Files to Modify; its 8-file authorized list becomes 12
  (Finding #3).
- `NotificationBaselineMigrationIntegrationTest.java` — moved to Files to Modify; `processed_events`
  moves from `UNGRANTED_TABLES` to grant-proof coverage (Finding #4).
- A concurrent-call test (two threads, same key) proving exactly one `true` and no unchecked
  exception (Finding #1).
- A reflection-based test asserting `recordIfNew`'s `@Transactional` carries no divergent
  `propagation` value (Finding #6).

**Out:** Unchanged from Phase 2.

## Business Rules

Unchanged from Phase 2: R7, R8.

## Locked Decisions

Unchanged from Phase 2: L1.

## Dependencies

Unchanged from Phase 2, plus: `org.springframework.dao.DataIntegrityViolationException` (already
transitively available via `spring-boot-starter-data-jpa`, T01).

## Inputs

Unchanged from Phase 2.

## Outputs

Unchanged from Phase 2, plus: updated `T01SkeletonRegressionTest.java` and
`NotificationBaselineMigrationIntegrationTest.java`.

## State Changes

Unchanged from Phase 2.

## Files to Create

Unchanged from Phase 2.

## Files to Modify

- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (Finding #3 — 8-file list becomes 12, explicitly named).
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (Finding #4 — `processed_events` moves from `UNGRANTED_TABLES` to grant-proof coverage).

## Files NOT to Modify

Unchanged from Phase 2 otherwise: `V1-V3` migrations, every file under `spec/`, sibling services.

## Acceptance Criteria

Unchanged from Phase 2's AC1-AC6, with AC2 now explicit that concurrent duplicate calls must each
resolve to exactly `true` once / `false` thereafter with no unchecked exception (Finding #1), AC1
now requiring the explicit `schema = "notifications"` table mapping (Finding #5), and AC3 now
requiring the exact `@Transactional` annotation shape (Finding #6).

## Required Tests

Unchanged from Phase 2, plus: the concurrent-call proof (Finding #1) and the `@Transactional`
reflection proof (Finding #6), both folded into the same Testcontainers integration test class
Phase 2 already planned.

## Constraints

Unchanged from Phase 2.

## Open Questions

No blockers. All 8 Phase 3 findings resolved above (7 accepted and fixed, 1 rejected as verified
false against source with a small courtesy clarity fix applied regardless).
