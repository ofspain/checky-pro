# auth · T05 — Task Implementation Brief (TIB)

## Task

Verification token service: add `VerificationToken`, `VerificationTokenRepository`, and
`VerificationTokenService`. Implement issue, verify, consume, and TTL checks. Unit-test with a
fixed `Clock`.

## Purpose

Provide a purpose-generic (`EMAIL_VERIFY` / `PASSWORD_RESET`), single-use, hashed, TTL'd token
primitive that later tasks (T06's self-service verification endpoints; task 7's password-reset
flow) will call to issue and redeem tokens. Not wired to any endpoint, controller, or
`AccountService` mutation in this task.

## Scope

**In:**
- `VerificationToken` entity mapping the existing `verification_tokens` table (V1 migration).
- `VerificationTokenRepository` (package-private, lookup by hashed token value).
- `VerificationTokenService` with issue / verify / consume operations and TTL enforcement,
  supporting both `EMAIL_VERIFY` and `PASSWORD_RESET` purposes per `design.md` §6.
- A new validated `@ConfigurationProperties` record for
  `themistra.auth.verification-token.ttl-minutes` (config key not yet present; verbatim value `30`
  per `design.md` §4c), following the `PasswordPolicyProperties` (T03) precedent.
- Secure raw-token generation (`SecureRandom`); only the SHA-256 hash is ever persisted.
- Unit tests with a fixed `Clock`, including TTL boundary cases.

**Out:**
- `AccountController`/`AccountService` changes (register event emission, `activateEmail` wiring) —
  T06.
- `EventTopics`/`OutboxPublisher` wiring — T06.
- Password-reset endpoint behavior — task 7 (T05 only makes the service purpose-generic; task 7
  exercises the `PASSWORD_RESET` path).
- Any contract file — none apply to this task.
- HTTP-response-level enumeration-safety testing — task 10.

## Business Rules

- **R3** (partial). `issue(...)` produces the token data an `auth.email.requested` event needs;
  this task does not emit that event.
- **R4** (partial). `verify`/`consume` correctly recognize a valid, unexpired, unused token as
  valid; this task does not perform the resulting account activation.
- **R5.** An invalid, expired, already-used, or deleted/suspended-account-owning token produces the
  *same* outcome shape from `verify`/`consume` in every case — no distinguishing signal between
  reasons.

## Locked Decisions

- **L5.** Enumeration-safe responses — governs the *shape* of this service's outputs (R5); the
  HTTP-response-level guarantee itself is realized later (T06/task 10).
- **L1** (widened, directly operative). V1–V4 migrations are immutable. `verification_tokens`
  already exists in V1 — this task adds no migration, only JPA mapping onto the existing schema.

## Dependencies

- `AccountRepository` — resolve internal `account_id` from `accountUuid` at issue time; read
  account status at verify time (read-only, no mutation).
- `Account`/`AccountStatus` — status check only (`DELETED`/`SUSPENDED` → uniform rejection per R5).
- `common.Hashing.sha256(String)` — hash the raw token before persistence.
- `Clock` (existing bean) — TTL/expiry comparisons, `created_at`; never `Instant.now()` inline.
- `java.security.SecureRandom` — new capability, no prior precedent in this codebase.
- Config key `themistra.auth.verification-token.ttl-minutes=30` (new).

## Inputs

- To `issue`: account UUID, purpose (`EMAIL_VERIFY` or `PASSWORD_RESET`).
- To `verify`/`consume`: the raw token string as presented by a caller.

## Outputs

- `issue` returns the raw token value (for a future caller — T06 — to embed in an email link) and/
  or the persisted `VerificationToken`; the raw value is never itself persisted or logged.
- `verify`/`consume` return a uniform "valid" result (resolving to the associated account) or a
  uniform "invalid" result — same shape regardless of *why* invalid (R5). Exact type (`Optional`,
  a result record, or a single exception type) is a Phase 5 implementation decision, not fixed
  here — but it must be a single shape, not one exception/return-path per failure reason.

## State Changes

- `issue`: inserts one `verification_tokens` row (hashed token, purpose, `expires_at`, no
  `used_at`).
- `consume` (on success): sets `used_at` on the matched row. No cascading changes to `accounts` —
  that's T06.
- No outbox/Kafka activity — this task emits no events.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java`
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenProperties.java`
  (name indicative; TTL config record)
- Mirrored unit tests under `services/auth/src/test/java/com/themistra/auth/account/`

## Files to Modify

- `services/auth/src/main/resources/application.properties` — append
  `themistra.auth.verification-token.ttl-minutes=30`.

## Files NOT to Modify

- `account/AccountController.java`, `account/AccountService.java` (event emission and
  `activateEmail` wiring are T06).
- `events/EventTopics.java`, `events/OutboxPublisher.java` call sites.
- Any file under `spec/` or `contracts/`.
- Any Flyway migration file (table already exists).

## Acceptance Criteria

- **R3-adjacent** — `issue(accountUuid, purpose)` creates a row with a fresh, unique, hashed token,
  correct purpose, and `expires_at` set from the configured TTL.
- **R4-adjacent** — a token that exists, is unexpired, unused, and whose account is not
  `DELETED`/`SUSPENDED` verifies/consumes as valid.
- **R5** — token-not-found, expired, already-used, and account-`DELETED`/`SUSPENDED` all produce
  the identical outcome shape; none is distinguishable from the return value alone.
- **TTL boundary** — a token exactly at its `expires_at` instant (per the test's fixed `Clock`) is
  treated as expired.
- **Single-use** — consuming a token twice: the second attempt is rejected via the same R5 uniform
  path, not a distinct "already used" signal.
- **Purpose round-trip** — both `EMAIL_VERIFY` and `PASSWORD_RESET` can be issued and stored
  correctly (task 7 exercises `PASSWORD_RESET` behaviorally; this task only proves the data model
  isn't email-only).

## Required Tests

- `shouldActivateAccountWithValidVerificationToken` (service-level: issue → verify/consume →
  resolves to the correct account).
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` (service-level: not-found / expired
  / already-used / deleted-or-suspended-account all produce the same outcome shape).
- Boundary: token exactly at `expires_at`; one tick before/after.
- Double-consume of the same token.
- Both purposes round-trip through `issue`.
- Raw token is never persisted or logged; only its hash appears in any assertion on stored state.

## Constraints

- **Security:** raw token generated via `SecureRandom` (not `Random`/`Math.random`); only the
  SHA-256 hash is persisted, matching `token_hash CHAR(64)`; the raw value is never logged.
- **Thread-safety:** `VerificationTokenService` is a stateless Spring singleton, safe for
  concurrent use, consistent with `AccountService`/`PasswordPolicy`.
- **Transaction:** `issue` and `consume` are the state-mutating operations and should be
  `@Transactional`; `verify` (if a separate read path) is `@Transactional(readOnly = true)`. No
  outbox call is made here (no event emitted by this task).
- **Module boundaries:** stays entirely within `account`; no new dependency on `authn`, `audit`, or
  `events` beyond what's listed above.
- **Null handling:** `issue`/`verify`/`consume` must not silently NPE on a null raw token or null
  account UUID — reject with a clear, intentional signal (exact exception type is a Phase 5
  decision).
- **Uniqueness:** `token_hash` has a DB `UNIQUE` constraint — a hash collision on insert must not
  surface as an unhandled `DataIntegrityViolationException`; at minimum this should be a known,
  named risk for Phase 5 to address (e.g., retry-on-collision), not silently ignored.

## Open Questions

No blockers.
