# notification · T19 · Phase 10 — Test Generation

The four T19 methods were written in Phase 6 and strengthened in Phase 9. This phase audited the
append-only claim for a gap the existing assertions did not close.

## Gap found and closed in this phase

- **TRUNCATE was not tested.** AC1 proved UPDATE and DELETE are rejected for `notification_app`, but
  a role that can `TRUNCATE` a table can empty the whole dispute log without issuing a single
  `DELETE`. That would make the "append-only" claim false while every existing assertion still
  passed. Verified against source first: `V2__notification_app_role_and_grants.sql:40` grants only
  `INSERT, SELECT`, so `TRUNCATE` should be denied. The test now proves it live: `TRUNCATE
  notifications.delivery_log` as `notification_app` must fail with SQLState `42501`.
- **Shown live, not assumed:** the expected SQLState on the new check was broken to `00000`, the
  method failed, and the value was restored and confirmed by `grep`.

## Separate issue found, not in T19's scope

A full-suite run failed once in `PreferenceResolverIntegrationTest.channelPreferenceEntityMapsAllSixColumnsCorrectly`
(T08's test): `updatedAt` was a few milliseconds later than the JVM's `Instant.now()`. The timestamp
is written by the database, and the assertion compares it against the host JVM clock with no
tolerance. The overshoot is consistent with clock skew between the Docker VM and the host.

It is intermittent: two subsequent full runs were clean (373 tests, 0 failures each). T19's own tests
use separate Testcontainers databases and do not touch that table, so they did not cause it.

**Disposition:** not fixed here. It is a latent flake in another task's test, and changing it is
outside this task's scope. It is recorded for the user as a follow-up: the assertion should compare
against the database's own clock or allow a tolerance.

## Verification

- `DeliveryLogDisputeGradeIntegrationTest` — 4/4 pass with the TRUNCATE check.
- Full suite: two consecutive clean runs, 373 tests, 0 failures, 0 errors, exit 0. One earlier run
  hit the separate intermittent T08 timestamp flake described above.
