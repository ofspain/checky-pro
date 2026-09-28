# notification · T08 · Phase 7 — Self-Review

## Files reviewed

- `preference/{ChannelPreference,ChannelPreferenceRepository,PreferenceResolver}.java`
- `db/migration/V6__notification_app_channel_preferences_grant.sql`
- `T01SkeletonRegressionTest.java`, `NotificationBaselineMigrationIntegrationTest.java` (both diffs)

## Verification performed — empirical, not just inspection

Same discipline as T05/T06's own Phase 7: `PreferenceResolver.resolve`'s real logic had never
actually been executed before this review (Phase 6's own "no tests" rule meant only Hibernate's
`ddl-auto=validate` had touched `ChannelPreference`'s mapping, indirectly, via other tests'
Testcontainers boots). Wrote a temporary, uncommitted scratch test
(`ScratchPreferenceResolverVerificationTest`, deleted before this artifact was written) exercising
5 scenarios against a real Postgres instance:

1. A stored row (`PAYMENT`/`EMAIL`, `enabled=false`) takes precedence over the default. **Passed.**
2. No stored row falls back to the documented default (`PAYMENT`/`EMAIL` → `true`,
   `MARKETING`/`EMAIL` → `false`). **Passed.**
3. `SECURITY`/`EMAIL` resolves `true` even with a stored `enabled=false` row present — the hard
   floor actually holds against a real adversarial row, not just an absent one. **Passed.**
4. `WEBHOOK`/`PUSH` resolve `false` with no stored row. **Passed.**
5. Lowercase input (`"payment"`/`"email"`) matches an uppercase-stored row
   (`PAYMENT`/`EMAIL`) — case normalization genuinely reaches the repository query, not just the
   default-table lookup. **Passed.**

**No defect found.** All 5 scenarios behaved exactly as the frozen brief and implementation
intended.

## Findings

No new findings this review — the implementation matches the frozen brief exactly, and every
behavior the brief specifies was now empirically exercised, not merely asserted correct by
inspection.

## Confirmed non-issue — no concurrent-access test needed

Unlike T04's `IdempotencyGuard` (a genuine TOCTOU race under concurrent writers) or T05's
`ContactProjectionRepository` (a genuine upsert with an out-of-order guard),
`PreferenceResolver.resolve` is pure read logic with no write path at all in this task's own scope.
There is no race to defend against — concurrent callers reading the same row concurrently is
ordinary, unremarkable concurrent `SELECT` traffic. Deliberately not planning a T04-style
concurrency test for Phase 10.

## Verification performed

- `mvn -pl services/notification clean verify` — 128 tests, 0 failures, unchanged from Phase 6's
  own final record (the scratch test above was deleted before this run).
- Scratch verification (described above, deleted before this commit): confirmed
  `PreferenceResolver`'s full behavior (stored-row precedence, default fallback, the `SECURITY`+
  `EMAIL` floor against an adversarial stored row, `WEBHOOK`/`PUSH` non-default, case
  normalization) against a real Postgres instance.
