<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# crypto · T02 · Phase 9 — Review Resolution

All 8 Phase 8 (Kimi) findings verified before disposition. All 8 confirmed accurate on their facts;
one led to a real design correction, one led to a real testing-methodology correction, the rest were
already-adequate or correctly low-priority.

| # | Finding | Disposition |
|---|---|---|
| 1 | `V2` commits a plaintext local password inside a versioned migration | **Human-gate decision, combined with #2: fixed.** `CREATE ROLE crypto_app LOGIN;` — no password. Real environments set it out-of-band (External Secrets Operator, matching L13). Local dev sets it via one documented `ALTER ROLE` step (`services/crypto/README.md`), never committed. |
| 2 | Hardcoded `checky` database name makes `V2` non-portable | **Fixed alongside #1.** `GRANT CONNECT ON DATABASE %I` via `EXECUTE format(...)` targeting `current_database()` — `V2` no longer assumes any specific database name. |
| 3 | No proof `spring.flyway.enabled=false` actually disables runtime Flyway | **Verified for real, not just re-read.** Started the app via `mvn spring-boot:run` (real host→container JDBC path, not `docker exec`): boots clean in ~1.7s as `crypto_app`, zero `Flyway` log lines anywhere in the full output. |
| 4 | Sibling `services/auth` build check was git-status-only | **Strengthened.** Added `mvn -pl services/auth validate` (fast, non-flaky) — `BUILD SUCCESS`. Did not re-run the full, historically-flaky `verify` suite again for a task that touches zero files under `services/auth` — disproportionate cost for no new signal beyond what `validate` + the unchanged mechanism argument (established at T01, re-confirmed via `git status` every phase since) already provide. |
| 5 | `V2` only grants, never narrows pre-existing broader privileges | **Accepted, documented, no change.** Low-probability local-dev-only edge case (a `crypto_app` role somehow pre-existing with broader rights before this migration ever runs) — not worth a `REVOKE ALL` step for a single-developer local environment with no other consumer of this role name. |
| 6 | `outbox` shape unreconciled with the shared outbox library | **Accepted, reinforced.** Same disposition as Phase 4/6 (deferred to T04+); noted again here so the reconciliation isn't lost between now and then. |
| 7 | `outbox` lacks an index on `aggregate_id` | **Accepted as an open question for T04+, not actioned.** `V1` is verbatim; changing it needs its own human gate when the outbox relay's real query pattern is known, not a guess now. |
| 8 | `observations.s3_snapshot_key VARCHAR(256)` may be short for real S3 keys | **Accepted as an open question for T08+ (observation log task), not actioned.** Same verbatim-constraint reasoning as #7. |

## Real corrections made this phase

1. **`V2__crypto_app_role_and_grants.sql` rewritten** — no committed password, dynamic-SQL database
   grant. Re-verified from a clean slate: dropped `chain` schema and the `crypto_app` role entirely,
   re-ran `mvn -pl services/crypto flyway:migrate` (both migrations reapplied clean), confirmed the
   role has no password until the documented `ALTER ROLE` step runs.
2. **`services/crypto/README.md`** gained a "Local development database" section documenting the
   one-time `ALTER ROLE crypto_app PASSWORD '...'` step.
3. **A real gap in my own Phase 6/7 verification methodology, caught while re-testing Finding 1/2's
   fix**: every earlier grant check (`INSERT`/`UPDATE`/`DELETE` as `crypto_app`) ran via `docker exec
   ... psql`, which — executing from *inside* the container — matches the official Postgres image's
   default `pg_hba.conf` `trust` rule for `127.0.0.1`/local-socket connections, bypassing password
   authentication entirely regardless of what password was supplied. Those results are still valid
   for what they actually tested (table-level privilege enforcement is independent of auth method),
   but they never exercised real password authentication. Caught and closed this gap directly:
   started the app from the **host** (the real `jdbc:postgresql://localhost:5432/...` path an actual
   deployment uses) with a deliberately wrong password via `DB_PASSWORD=totally-wrong-password mvn
   spring-boot:run` — got `FATAL: password authentication failed for user "crypto_app"`, confirming
   the host→container path really does hit Postgres's `scram-sha-256` catch-all rule. Then confirmed
   the correct password boots clean. This is the first time in this pipeline a Kimi-finding
   verification pass surfaced a gap in the *verification technique itself*, not just the code under
   review — worth carrying into T03+'s own review discipline.

## Verification re-run after all changes

- `mvn -pl services/crypto flyway:migrate` — clean, from a fully reset schema+role state.
- Grant checks (INSERT/UPDATE/DELETE on the three named tables) — still pass post-rewrite.
- `mvn spring-boot:run` — correct password boots clean, zero Flyway activity; wrong password fails
  fast with a real Postgres auth error, proving the negative case too.
- `mvn -pl services/auth validate` — `BUILD SUCCESS`; `git status --porcelain services/auth` — empty.
- `mvn -pl services/crypto verify` — `BUILD SUCCESS`.

---

**Phase 9 complete — all findings resolved; two real fixes shipped (portable `V2`, corrected local
setup docs); one verification-methodology gap in this pipeline's own practice caught and closed.**
Proceed to Phase 10 (Test Generation) on approval.
