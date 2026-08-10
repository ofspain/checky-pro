# auth · T24 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

## Commit title

```
feat(auth): implement ApiKeyService create/list/revoke/exchange (T24)
```

## Commit message

```
feat(auth): implement ApiKeyService create/list/revoke/exchange (T24)

Add ApiKeyService: create (MERCHANT role + ACTIVE status + confirmed
MFA), list, revoke, and exchange (constant-time secret verification),
generating keys as ck_live_<24-char suffix>.<32-char secret> per L7.

L7's public prefix (32 characters) didn't fit the existing
api_keys.prefix VARCHAR(16) column - a conflict T23 identified but
deferred. Resolved this task via V7, an additive migration widening
the column to VARCHAR(32) (human-authorized), implementing L7 exactly
as specified rather than shrinking the key format to fit an
undersized column.

No controller exists yet (T25/T26), so create() independently
re-verifies its own preconditions - role, account status, and
confirmed MFA - rather than trusting a caller, matching MfaService's
established defense-in-depth precedent.

Two rounds of adversarial review (design-challenge, independent code
review) plus a test review ran against this task. Two real bugs were
caught and fixed:

- ApiKey.prefix's @Column mapping was never updated to length = 32
  after V7 widened the actual column - would have failed Hibernate
  schema validation at startup. Caught by independent review, not
  self-review.

- exchange's audit-target resolution used the first prefix-sharing
  candidate rather than the specifically-matched (but revoked/expired)
  row's own account - a latent misattribution bug for the rare case
  of a genuine prefix collision. Both self-review and independent
  review converged on this finding independently.

Also fixed: revoke() now checks revokeIfActive's return value and
only audits on an actual state change, matching RoleService's
established idempotent-no-op-skips-audit precedent (previously it
audited unconditionally, including on a repeat revoke of an
already-revoked key). Both fixes are now regression-tested
(revokeIsIdempotent, exchangeAuditsTheMatchedAccountEvenWhenItIsNotThe
FirstCandidate) - both tests would fail against the pre-fix code.

Two Kimi findings were checked against the frozen brief before
accepting and found to be based on a misreading of an earlier,
superseded draft (the Phase 2 TIB rather than the Phase 4 frozen
brief) - rejected rather than reopening an already-made scope
decision (ApiKeyExceptionHandler is deliberately deferred to T25/T26,
who own the controllers that will actually use it).

Full suite: 516 tests, same 6 pre-existing/unrelated failures as
before this task (Kafka delivery timing, audit-FK ordering, Mockito
strict-stubbing) - unchanged baseline.

Refs: spec/auth-service/tasks.md T24, requirements R30/R32/R33, L7, L12

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

(Note: as in prior tasks this session, the phase template's boilerplate trailer names "Claude Opus 4.8" — generic scaffolding text, not a record of which model did the work. This entire task ran on Claude Sonnet 5, so the trailer above reflects that.)

## Files changed

**Production**
- `services/auth/src/main/resources/db/migration/V7__widen_api_key_prefix.sql` — new. Widens `api_keys.prefix` to `VARCHAR(32)`.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java` — modified. `prefix` column mapping widened to `length = 32` (Phase 9 fix); class Javadoc updated to describe the actual `lastUsedAt`/`revokedAt` update mechanism.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java` — modified. Five new methods: `findByAccountId`, `findByKeyUuid`, `findAccountIdByUuid`, `findAccountUuidById`, `updateLastUsedAt`, `revokeIfActive`.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java` — new. `create`/`list`/`revoke`/`exchange` and three result records.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java` — new. Validated `@ConfigurationProperties` for the API-key prefix.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyHasher.java` — new. Constant-time comparison wrapper.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyNotAuthorizedException.java`, `ApiKeyExchangeRejectedException.java`, `ApiKeyNotFoundException.java` — new. Domain exceptions; no `@RestControllerAdvice` handler yet (deferred to T25/T26).
- `services/auth/src/main/java/com/themistra/auth/common/Hashing.java` — modified. Added `constantTimeEquals(String, String)`.
- `services/auth/src/main/resources/application.properties` — modified. Added `themistra.auth.api-key.prefix`.

**Tests**
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyHasherTest.java` — new. 3 tests.
- `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyServiceIntegrationTest.java` — new. 15 tests.

No other module, no `spec/` file touched.

## Summary

T24 gives the `apikey` module a working, fully-tested service layer on top of T23's entity/repository foundation: merchants can create keys (gated on role, account status, and confirmed MFA, all independently re-verified since no controller exists yet), list their own keys, revoke them idempotently, and have a presented key validated via constant-time comparison. Along the way, this task resolved the L7-vs-schema-width conflict T23 had identified and deliberately deferred, via a human-authorized additive migration. Two adversarial review rounds caught two real, would-have-shipped-silently bugs (a stale entity/schema mapping, and an audit-attribution error on the rare prefix-collision path); both are now fixed and specifically regression-tested. `ApiKeyExceptionHandler` and the corresponding `ProblemTypes` entries are deliberately not built yet — that's T25/T26's job, once an actual HTTP boundary exists to need them.

## Testing performed

- `mvn -pl services/auth -am compile` / `test-compile` — clean throughout.
- `mvn -pl services/auth test -Dtest=ArchitectureTest` — re-run after Phase 6 and again after Phase 9's fixes; clean both times, confirming L12 and repository-visibility rules hold.
- `mvn -pl services/auth test -Dtest=ApiKeyHasherTest,ApiKeyServiceIntegrationTest` — 18/18 pass against real Testcontainers Postgres + Kafka.
- Full suite: 516 tests, 510 passing, the same 6 pre-existing/unrelated failures as the established baseline before this task (498 tests, same 6 failures) — confirmed unchanged.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 24 — "Key service."
- **Requirements:** R30 (create), R32 (exchange updates `last_used_at`), R33 (uniform rejection) — all implemented and tested at the service layer; R33's HTTP-level "401" awaits T25/T26's exception handler.
- **LOCKED decisions:** L7 (API key format) — implemented exactly, via an authorized schema widening rather than a spec change. L12 (module boundaries) — respected, CI-verified.
