# auth · T05 — Phase 1: Specification Extraction

## Business Rules

- **R3.** When account registration succeeds, an `auth.email.requested` event with purpose
  `verify_email` must eventually be emitted in the same transaction — T05's contribution is
  providing the `issue(...)` capability that produces the token data such an event needs (the
  event emission itself, from inside `AccountService.register`, is T06's wiring, not T05's).
- **R4.** A valid, unused verification token submitted within its TTL must be accepted (T05's
  contribution: `verify`/`consume` must correctly recognize a valid token as valid) — the resulting
  account transition to `ACTIVE` and `auth.user.registered` emission are T06's wiring.
- **R5.** An invalid, expired, already-used, or deleted/suspended-account-owning token must be
  rejected *uniformly* — no distinguishing signal between those four failure reasons. This is
  squarely T05's responsibility: the service's `verify`/`consume` return/exception shape must not
  leak which reason applied. (The HTTP-response-level realization of this uniformity is a later
  task — see Phase 0 §5.)

## Locked Decisions

- **L5. Enumeration-safe responses.** Email verification (among others) must return uniform
  responses that don't reveal account/token state. Scoped to this task's header — governs the
  *shape* `VerificationTokenService`'s outputs must have (see R5 above), even though the actual
  HTTP response uniformity is realized later.
- **L1 (widened — not in the header, but directly operative).** "Immutability of existing
  migrations... V1–V4 are immutable." The `verification_tokens` table already exists in V1. This
  task must not add a migration or alter that table's semantics — pure JPA mapping onto the
  existing schema. Included because it directly forecloses a plausible wrong turn (adding a
  migration) that the header's scoped-ID list wouldn't otherwise flag.

## Files involved

**New (per `design.md` §6's package map and this task's statement):**
- `account/VerificationToken.java` (entity, mapping onto the existing `verification_tokens` table)
- `account/VerificationTokenRepository.java`
- `account/VerificationTokenService.java` — per `design.md` §6's own annotation, "(issue, verify,
  consume, reset flows)" — i.e., the design explicitly anticipates this service being
  purpose-generic (`EMAIL_VERIFY` *and* `PASSWORD_RESET`, matching the table's
  `CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET'))`), not email-verification-only, even though
  R3/R4/R5 only exercise the `EMAIL_VERIFY` path. Password-reset's own task (task 7) will be the
  first *caller* of the `PASSWORD_RESET` purpose, but the service itself should not be built
  email-only and need rework later.
- A new validated `@ConfigurationProperties` record for
  `themistra.auth.verification-token.ttl-minutes` (record name not fixed here — Phase 2/5 decision;
  `PasswordPolicyProperties` from T03 is the established precedent to follow).
- Mirrored unit tests under `src/test/java/com/themistra/auth/account/`.

**Existing to read/reuse, not modify:**
- `account/Account.java`, `account/AccountStatus.java` — `VerificationTokenService.verify(...)`
  needs to read the associated account's status (to enforce "belongs to a deleted/suspended
  account" per R5) without mutating it.
- `account/AccountRepository.java` — resolving the internal `account_id` FK from an external
  `accountUuid` at issue time, and reading status at verify time.
- `common.Hashing.sha256(String)` — the established pattern for the `token_hash CHAR(64)` column;
  only the hash is ever persisted, never the raw token.

**Existing to extend:**
- `application.properties` — add `themistra.auth.verification-token.ttl-minutes=30` (the value is
  fixed by `design.md` §4c's verbatim config block; not yet present, per Phase 0).

**Not touched by T05** (confirmed via Phase 0's T06 investigation): `AccountController`,
`AccountService` (its `register`/`activateEmail` methods stay as they are), `EventTopics`,
`OutboxPublisher` call sites. All of that is T06.

## Dependencies

- `AccountRepository` (read-only: resolve account by UUID at issue; read status at verify).
- `common.Hashing.sha256(String)`.
- `Clock` (existing bean) — required for TTL/expiry comparisons and `created_at`/`used_at`
  timestamps; never `Instant.now()` inline, per `agents.md`.
- **New capability needed: cryptographically secure random raw-token generation.** No precedent
  exists yet in this codebase (T03 didn't need one; the API-key secret generation this might
  otherwise resemble is task 24's work, not yet built). `java.security.SecureRandom` is the
  standard JDK primitive; the exact token format (length/encoding) is a Phase 2/5 decision, not
  fixed by any requirement or LOCKED decision read so far.
- Config key `themistra.auth.verification-token.ttl-minutes` (verbatim value: `30`).
- Contracts: none of `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/*.schema.json` apply to T05 directly — no endpoint, no event payload
  authored by this task (same "listed in the header because they apply to the service overall, not
  because this task touches them" pattern as T03).

## Acceptance Criteria

- **R3-adjacent.** `VerificationTokenService.issue(accountUuid, purpose)` (exact signature TBD)
  creates a `VerificationToken` row with a fresh, unique, hashed token value, the given purpose,
  and an `expires_at` set from the configured TTL — providing the data `auth.email.requested`
  needs, without emitting the event itself.
- **R4-adjacent.** Given a token that exists, is unexpired, unused, and whose account is not
  `DELETED`/`SUSPENDED`, `verify`/`consume` recognizes it as valid.
- **R5.** Given any of: token not found, expired, already used, or account `DELETED`/`SUSPENDED` —
  `verify`/`consume` produces the *same* outcome shape (same exception type, or the same "empty"
  return) in every case; no caller can distinguish which condition applied from the service's
  return value alone.
- **TTL boundary.** A token exactly at its `expires_at` instant (per the fixed `Clock` in tests) is
  treated as expired, not valid (standard "TTL checks" boundary — exact inclusive/exclusive
  semantics not specified by any requirement text found; a Phase 2/5 decision, flagged, not a
  blocker).
- **Consume is single-use.** Once consumed (`used_at` set), a second `verify`/`consume` on the same
  token is rejected via the same uniform R5 path as any other invalid token — not a distinct
  "already used" signal.

## Tests required

**Named (`package.md` §8), interpreted at the service level per Phase 0's resolution:**
- `shouldActivateAccountWithValidVerificationToken` → service-level equivalent: issuing a token for
  an account, then verifying/consuming that same raw token, succeeds and resolves back to the
  correct account. (Its literal, full HTTP/account-activation realization is T06's.)
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` → service-level equivalent: a
  nonexistent, expired, already-used, or deleted/suspended-account token all produce the identical
  outcome shape from `verify`/`consume`. (Its literal HTTP-response-uniformity realization is task
  10's.)

**Boundary tests implied by "TTL checks" and fixed-`Clock` unit testing (`agents.md`):**
- Token expiring exactly at the fixed-clock instant (boundary inclusivity).
- Token one tick before/after expiry.
- Double-consume of the same token.
- Issuing a token for `EMAIL_VERIFY` vs `PASSWORD_RESET` purpose (given design.md's purpose-generic
  service) — at minimum, that `purpose` is stored and round-trips correctly; task 7 owns exercising
  the `PASSWORD_RESET` path's actual behavior.
- Token hash uniqueness / collision handling consistent with the `UNIQUE` DB constraint on
  `token_hash` (not a functional requirement, but a real constraint the service must not violate
  silently).

## Open Questions

No genuine blockers. (`package.md` §11 has no item covering verification-token TTL semantics or
raw-token format; both are ordinary implementation decisions deferred to Phase 2/5, not spec
ambiguities requiring a human decision before proceeding.)
