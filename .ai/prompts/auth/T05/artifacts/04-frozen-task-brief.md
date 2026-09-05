# auth · T05 — Frozen Task Brief

**STATUS: FROZEN**
**Approved by:** femi (human approval gate, Phase 4)
**Date:** 2026-07-24
**Supersedes:** `artifacts/02-task-implementation-brief.md`, amended per `artifacts/03-design-challenge.md`.

Downstream phases (5 onward) implement against this document only.

---

## Phase 3 finding disposition

All 10 Kimi findings reviewed and accepted by the human approver, folded directly into the brief
below. Two (Findings 4 and 8) involved a real design trade-off rather than a pure gap-fill —
dispositions recorded explicitly.

| # | Finding | Disposition |
|---|---|---|
| 1 | Raw token format undefined | **ACCEPTED** — locked to 32 bytes from `SecureRandom`, URL-safe Base64 (no padding), 43-character string. |
| 2 | Double-consume not atomic | **ACCEPTED** — `consume` is a single conditional `UPDATE ... WHERE token_hash = ? AND used_at IS NULL`; 0 rows affected ⇒ uniform invalid outcome. |
| 3 | `verify`/`consume` relationship ambiguous | **ACCEPTED** — `verify` is a read-only check (no side effects); `consume` is the sole atomic verify-and-mark redemption path. |
| 4 | Purpose-specific account-state validation incomplete | **ACCEPTED — option (a).** T05 rejects only `DELETED`/`SUSPENDED` accounts (uniformly, via R5). Purpose-specific state requirements (e.g. email-verify only for `PENDING_VERIFICATION`, reset only for `ACTIVE`/`LOCKED`) are the caller's (T06/task 7's) responsibility. Chosen to keep the service purpose-generic per `design.md` §6, rather than hardcoding per-purpose business rules into a shared primitive. |
| 5 | `issue` semantics for missing/bad accounts undefined | **ACCEPTED** — null account UUID: intentional error (not NPE). Account not found: a domain exception (not the uniform R5 path — `issue` is an internal service call from T06/task 7, not a public, enumeration-sensitive boundary). |
| 6 | Hash-collision handling unresolved | **ACCEPTED** — on insert collision, retry with a new random token up to 3 times; if still colliding, throw `IllegalStateException` (defensive fallback for an astronomically unlikely event). |
| 7 | TTL validation/boundaries unspecified | **ACCEPTED** — TTL config gets `@Min(1)`; add a boundary test (TTL=1 minute expires exactly at boundary). |
| 8 | Reissue doesn't address prior active tokens | **ACCEPTED — invalidate.** `issue` marks any prior unexpired, unconsumed token for the same `(account, purpose)` as used (`used_at = clock.instant()`) before inserting the new one, in the same transaction. Chosen over "leave prior tokens valid" because a stale, still-valid verification/reset link remaining usable after a newer one was issued is the more conventional security risk to close, and matches Kimi's stated rationale for password-reset flows. |
| 9 | Timestamps should derive from `Clock`, not `Instant.now()` | **ACCEPTED** — `created_at`, `expires_at`, and `used_at` are all set by the service using the injected `Clock`; no `@PrePersist`/`@PreUpdate` lifecycle callback using `Instant.now()` (the existing `Account.java` pattern is not to be copied here). |
| 10 | `issue` return type ambiguous | **ACCEPTED** — `issue` returns a dedicated result type (e.g. `VerificationTokenResult`) carrying the raw token string (returned exactly once), the persisted `VerificationToken` (hash only — no raw-token field on the entity), the account UUID, and the purpose. No `toString`/serialization path may expose the raw token. |

No findings rejected.

---

## Task

Verification token service: add `VerificationToken`, `VerificationTokenRepository`, and
`VerificationTokenService`. Implement issue, verify, consume, and TTL checks. Unit-test with a
fixed `Clock`.

## Purpose

Provide a purpose-generic (`EMAIL_VERIFY` / `PASSWORD_RESET`), single-use, hashed, TTL'd token
primitive that T06 (verification endpoints) and task 7 (password-reset flow) will call. Not wired
to any endpoint, controller, or `AccountService` mutation in this task.

## Scope

**In:**
- `VerificationToken` entity mapping the existing `verification_tokens` table (V1 migration).
- `VerificationTokenRepository` (package-private, lookup by hashed token value).
- `VerificationTokenService`: `issue` (creates + invalidates prior same-purpose tokens for that
  account), `verify` (read-only), `consume` (atomic verify-and-mark), TTL enforcement, both
  `EMAIL_VERIFY` and `PASSWORD_RESET` purposes.
- `VerificationTokenProperties` — validated `@ConfigurationProperties` record,
  `themistra.auth.verification-token.ttl-minutes` (`@Min(1)`, verbatim default `30`).
- Secure raw-token generation: 32 bytes `SecureRandom`, URL-safe Base64 no padding (43 chars); only
  the SHA-256 hash is ever persisted.
- Insert-collision retry (up to 3 attempts, then `IllegalStateException`).
- Unit tests with a fixed `Clock`, including TTL and atomicity boundary cases.

**Out:**
- `AccountController`/`AccountService` changes (register event emission, `activateEmail` wiring,
  purpose-specific account-state filtering) — T06/task 7.
- `EventTopics`/`OutboxPublisher` wiring — T06.
- Password-reset endpoint behavior — task 7 (T05 only makes the service purpose-generic).
- Any contract file.
- HTTP-response-level enumeration-safety testing — task 10.

## Business Rules

- **R3** (partial). `issue(...)` produces the token data an `auth.email.requested` event needs;
  this task does not emit that event.
- **R4** (partial). `verify`/`consume` correctly recognize a valid, unexpired, unused token as
  valid; this task does not perform the resulting account activation.
- **R5.** An invalid, expired, already-used, or deleted/suspended-account-owning token produces the
  *same* outcome shape from `verify`/`consume` in every case.

## Locked Decisions

- **L5.** Enumeration-safe responses — governs the *shape* of this service's outputs (R5); full
  HTTP-response-level uniformity is realized later (T06/task 10).
- **L1** (widened). V1–V4 migrations are immutable; `verification_tokens` already exists — no new
  migration.
- **This task's frozen implementation decisions** (Phase 3/4, not spec-level LOCKED IDs, but not
  renegotiable by Phase 5+): raw token format (Finding 1), atomic `consume` (Finding 2),
  `verify`/`consume` contract split (Finding 3), account-state check scope (Finding 4, option a),
  `issue` error semantics (Finding 5), collision retry (Finding 6), TTL `@Min(1)` (Finding 7),
  reissue invalidates prior tokens (Finding 8), Clock-derived timestamps (Finding 9), `issue`
  result type (Finding 10).

## Dependencies

- `AccountRepository` — resolve internal `account_id` from `accountUuid` at issue time (throwing a
  domain exception if not found, per Finding 5); read account status at verify/consume time
  (read-only).
- `Account`/`AccountStatus` — status check only (`DELETED`/`SUSPENDED` → uniform rejection per R5;
  no other status filtering per Finding 4).
- `common.Hashing.sha256(String)` — hash the raw token before persistence.
- `Clock` (existing bean) — sole source of all timestamps (Finding 9): `created_at`, `expires_at`
  (`clock.instant().plus(ttlMinutes, MINUTES)`), `used_at`.
- `java.security.SecureRandom` — 32 bytes, encoded via
  `Base64.getUrlEncoder().withoutPadding()` (Finding 1).
- Config key `themistra.auth.verification-token.ttl-minutes=30`, `@Min(1)` (Finding 7).

## Inputs

- To `issue`: account UUID, purpose (`EMAIL_VERIFY` or `PASSWORD_RESET`).
- To `verify`/`consume`: the raw token string as presented by a caller.

## Outputs

- `issue` returns a `VerificationTokenResult`-shaped value: raw token (once), the persisted
  `VerificationToken` (hash only, no raw-token field), account UUID, purpose (Finding 10).
- `verify` returns a uniform valid/invalid read-only result (resolving to the associated account on
  success) — never mutates state.
- `consume` performs the atomic conditional update and returns the same uniform valid/invalid
  shape; 0 rows affected (already used, or never existed) is indistinguishable from any other
  invalid reason (Finding 2, R5).

## State Changes

- `issue` (single transaction): marks any prior unexpired, unconsumed token for the same
  `(account, purpose)` as used (`used_at = clock.instant()`), then inserts the new row (hashed
  token, purpose, `expires_at` from Clock+TTL). Retries up to 3 times on a `token_hash` collision;
  `IllegalStateException` if still colliding.
- `consume` (single transaction): one conditional `UPDATE ... SET used_at = ? WHERE token_hash = ?
  AND used_at IS NULL`.
- No outbox/Kafka activity — this task emits no events.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenProperties.java`
- Mirrored unit tests under `services/auth/src/test/java/com/themistra/auth/account/`

## Files to Modify

- `services/auth/src/main/resources/application.properties` — append
  `themistra.auth.verification-token.ttl-minutes=30`.

## Files NOT to Modify

- `account/AccountController.java`, `account/AccountService.java`.
- `events/EventTopics.java`, `events/OutboxPublisher.java` call sites.
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file.

## Acceptance Criteria

- **R3-adjacent** — `issue` creates a row with a fresh, unique, hashed 43-character URL-safe
  token, correct purpose, `expires_at` from Clock+TTL.
- **R4-adjacent** — a token that exists, is unexpired, unused, and whose account is not
  `DELETED`/`SUSPENDED` verifies/consumes as valid.
- **R5** — not-found, expired, already-used, and `DELETED`/`SUSPENDED`-account tokens all produce
  the identical outcome shape from `verify`/`consume`.
- **Atomicity** — two concurrent `consume` calls on the same token: exactly one succeeds; the other
  gets the uniform invalid outcome, not a race-dependent result.
- **TTL boundary** — a token exactly at `expires_at` (fixed `Clock`) is expired.
- **Reissue** — issuing a second token for the same `(account, purpose)` invalidates the first
  (previously-valid token now fails `verify`/`consume`).
- **Collision fallback** — a forced `token_hash` collision triggers a retry, not an unhandled
  `DataIntegrityViolationException`; exhausting retries throws `IllegalStateException`.
- **Config validation** — `ttl-minutes=0` or negative fails startup validation.
- **No raw-token leakage** — the persisted entity, its `toString()`, and any logging never contain
  the raw token.

## Required Tests

- `shouldActivateAccountWithValidVerificationToken` (service-level: issue → consume → resolves to
  correct account).
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` (not-found / expired / already-used
  / deleted-or-suspended-account all produce the same outcome shape).
- TTL boundary: exactly-at-expiry, one tick before/after.
- Atomic double-consume: only one of two attempts succeeds.
- Reissue invalidates the prior token.
- Collision retry succeeds after N attempts; exhausted retries throw `IllegalStateException`.
- `ttl-minutes` config validation (`@Min(1)`).
- Both purposes (`EMAIL_VERIFY`, `PASSWORD_RESET`) round-trip through `issue`.
- Null account UUID / null raw token rejected with an intentional exception, not NPE.
- `issue` for a nonexistent account UUID throws a domain exception (not the uniform R5 path).
- Raw token never appears in the persisted entity's fields or `toString()`.

## Constraints

- **Security:** `SecureRandom`, 32 bytes, URL-safe Base64 no padding; only SHA-256 hash persisted;
  raw token never logged; no raw-token field on the JPA entity.
- **Thread-safety:** `VerificationTokenService` stateless singleton; `consume`'s atomicity is
  enforced at the SQL level (conditional `UPDATE`), not via in-JVM locking.
- **Transaction:** `issue` and `consume` are `@Transactional`; `verify` is
  `@Transactional(readOnly = true)`. No outbox call in this task.
- **Module boundaries:** entirely within `account`; no new dependency on `authn`, `audit`, or
  `events`.
- **Null handling:** null account UUID or null raw token → intentional exception, never NPE.
- **Timestamps:** exclusively from the injected `Clock`; no `Instant.now()`, no
  `@PrePersist`/`@PreUpdate` lifecycle callbacks for these fields.

## Open Questions

No blockers.
