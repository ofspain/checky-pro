STATUS: FROZEN

# notification · T02 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 7 findings independently verified before disposition.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `citext` extension unresolved for the Testcontainers-backed test | **ACCEPTED** | The Testcontainers instance is a fresh Postgres 16 container, isolated from the shared local-dev Postgres — `citext` isn't enabled there by default regardless of auth's own migration ordering. Resolved via option (b): the integration test itself executes `CREATE EXTENSION IF NOT EXISTS citext;` as a setup step before running notification's migrations, self-contained rather than coupled to auth's own migration file. |
| 2 | `application.properties` doesn't exist; runtime-Flyway-disabled state is ambiguous | **ACCEPTED — corrects a real planning error** | Notification's own `tasks.md` task 3 is explicitly titled "Config & resource server" — creating `application.properties` in T02 would preempt that task's own deliverable, the same task-boundary discipline this whole pipeline has followed throughout. Resolved via Kimi's second option: no `application.properties` in T02; no `runtimeFlywayIsDisabledInApplicationProperties`-style assertion in this task's own test (there is nothing yet for it to check); the integration test uses the plain-Flyway-API style (`Flyway.configure()...migrate()`), mirroring crypto's own test, which never boots a Spring context and so was never at risk of Spring's own Flyway autoconfiguration regardless. |
| 3 | IN_APP template names unspecified | **ACCEPTED** | Adopts Kimi's own suggested table: the two auth-originated templates whose names are already channel-specific in the spec's own text (`email.verify`, `email.password_reset`) get distinct IN_APP names (`user.verify`, `user.password_reset`); the other five (`user.welcome`, `invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued`) are already channel-neutral and reuse the identical name for both channels, differentiated only by the `channel` column. |
| 4 | V3 seed-content assertions under-specified | **ACCEPTED** | Added `launchTemplatesAreSeededWithVersionOne()` to the required test list: asserts `COUNT(*) = 14`, `COUNT(DISTINCT name) = 7`, every row `version = 1`, and the exact expected `(name, channel)` pair set — content itself (`subject`/`body`) not asserted, since it's explicitly disclosed as provisional pending O6. |
| 5 | Sequence grant not acknowledged; risk of an over-strict "no privileges at all" test | **ACCEPTED** | Clarified explicitly: V2 also grants `USAGE` on the schema and `USAGE, SELECT` on all sequences (mirroring crypto's own V2 exactly) — the "no access to other tables" test targets table-level privileges only, never asserts zero sequence privileges. |
| 6 | Local-dev migration ordering commands not explicit | **ACCEPTED** | Added the exact three-command sequence (compose up, auth migrate, notification migrate) to the brief. |
| 7 | `shedlock` table exists without its own task's dependency yet | **ACCEPTED** | Added an explicit note: the table is created now because the VERBATIM `V1` requires it; the ShedLock Maven dependencies and `@EnableSchedulerLock` are deliberately deferred to task 14, mirroring T01's own "add each annotation/dependency in the task that actually introduces the thing it enables" discipline — not an inconsistency. |

## Task

Unchanged from Phase 2, with all 7 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- Integration test's own `CREATE EXTENSION IF NOT EXISTS citext;` setup step (Finding #1).
- No `application.properties` in this task (Finding #2) — explicitly deferred to task 3.
- IN_APP template naming table (Finding #3):

  | Event mapping | EMAIL name | IN_APP name |
  |---|---|---|
  | verify_email | `email.verify` | `user.verify` |
  | password_reset | `email.password_reset` | `user.password_reset` |
  | user.registered | `user.welcome` | `user.welcome` |
  | invoice.created | `invoice.created` | `invoice.created` |
  | payment.seen | `payment.seen` | `payment.seen` |
  | payment.finalized | `payment.finalized` | `payment.finalized` |
  | receipt.issued | `receipt.issued` | `receipt.issued` |

- `launchTemplatesAreSeededWithVersionOne()` test method (Finding #4).
- V2's own Javadoc/comment explicitly states the schema+sequence grants alongside the narrow
  `delivery_log` grant (Finding #5); the "ungranted tables" test targets table privileges only.
- Exact local-dev command sequence (Finding #6):
  ```bash
  docker compose -f services/auth/compose.local.yaml up -d
  mvn -pl services/auth flyway:migrate
  mvn -pl services/notification flyway:migrate
  ```
- A one-line note on `shedlock`'s own intentional dependency gap until task 14 (Finding #7).

**Out:** Unchanged from Phase 2, plus: no `application.properties`, no Spring Boot context in the
integration test.

## Acceptance Criteria

Unchanged from Phase 2's AC1–AC5, with AC2's own test now explicitly scoped to table-only privilege
denial (not sequences), and a new AC3 sub-requirement: the integration test itself establishes `citext`
before migrating, not relying on any external ordering.

## Required Tests

`NotificationBaselineMigrationIntegrationTest`, now including `launchTemplatesAreSeededWithVersionOne()`
and the `citext`-extension setup step, per the dispositions above.

## Open Questions

No blockers.
