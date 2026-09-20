# auth · T05 — Phase 6: Implementation Notes

## What changed

**Created:**
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenProperties.java` —
  `@ConfigurationProperties(prefix = "themistra.auth.verification-token")` record, single field
  `ttlMinutes` with `@Min(1)`.
- `services/auth/src/main/java/com/themistra/auth/account/VerificationToken.java` — JPA entity
  mapping the existing `verification_tokens` table. Static factory `create(...)` takes all
  timestamps as parameters (no `@PrePersist`/`@PreUpdate`); nested `Purpose` enum
  (`EMAIL_VERIFY`/`PASSWORD_RESET`); no `usedAt` mutator.
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java` —
  `findByTokenHash`, plus two `@Modifying @Query` bulk updates: `markConsumed` (atomic
  verify-and-mark, folding the `usedAt IS NULL` and `expiresAt > :now` conditions into one
  statement) and `invalidateActive` (marks prior unused same-purpose tokens used).
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java` —
  `issue`/`verify`/`consume` exactly per the frozen brief and plan, plus the nested
  `VerificationTokenResult` record.

**Modified:**
- `services/auth/src/main/resources/application.properties` — appended
  `themistra.auth.verification-token.ttl-minutes=30` (verbatim `design.md` §4c value), above the
  password-policy block T03 added.

No other files touched.

## Mapping to the plan

Matches `artifacts/05-implementation-plan.md` exactly — method signatures, the private-method flow
descriptions for `issue`/`verify`/`consume`, and the reuse of `AccountNotFoundException` (no new
exception file) all as planned. `VerificationTokenResult` is a nested `public record` inside
`VerificationTokenService`, as planned (no separate file authorized).

## Mapping to acceptance criteria (frozen brief §Acceptance Criteria)

- **Token format (Finding 1):** `generateRawToken()` — 32 bytes from `SecureRandom`,
  `Base64.getUrlEncoder().withoutPadding()` — a 43-character URL-safe string.
- **Atomicity (Finding 2):** `markConsumed`'s single conditional `UPDATE` is the only place
  `usedAt` is ever set on redemption; two calls racing on the same token can only have one succeed
  (DB-level, not JVM-level, so it holds across pods too).
- **`verify`/`consume` contract (Finding 3):** `verify` never calls anything mutating; `consume` is
  the sole redemption path.
- **Account-state scope (Finding 4, option a):** `isAccountUsable` checks only `DELETED`/
  `SUSPENDED`; no purpose-specific filtering.
- **`issue` error semantics (Finding 5):** null args → `NullPointerException` with a descriptive
  message; unknown `accountUuid` → `AccountNotFoundException` (not the uniform R5 path).
- **Collision retry (Finding 6):** up to 3 attempts via `saveAndFlush` inside a `try/catch
  (DataIntegrityViolationException)`; exhausted → `IllegalStateException`.
- **TTL validation (Finding 7):** `@Min(1)` on `ttlMinutes`.
- **Reissue invalidates prior tokens (Finding 8):** `invalidateActive` called before creating the
  new token, same transaction.
- **Clock-derived timestamps (Finding 9):** `issue` computes `now`/`expiresAt` from the injected
  `Clock`; `consume`/`markConsumed` use `clock.instant()`; no `Instant.now()` anywhere in these
  four files.
- **`issue` return type (Finding 10):** `VerificationTokenResult(rawToken, token, accountUuid,
  purpose)` — the entity has no raw-token field, so no serialization path can leak it.
- **R5 uniformity:** `verify`/`consume` return `Optional<UUID>`; every failure path (not found,
  expired, used, race lost, account unusable) returns `Optional.empty()` — no distinguishing
  exception or return shape between them.

## Deviations from the plan

None. Implementation matches the plan's signatures and flow descriptions exactly.

## Notes on things encountered while implementing (not deviations, but worth recording)

- **`AccountRepository.findById(Long)`** is inherited from `JpaRepository<Account, Long>` — no
  new repository method was needed to resolve the account by internal FK inside
  `resolveUsableAccount`.
- **Ordering in `consume`:** the account-usability check happens strictly before the atomic
  `markConsumed` call, so a token whose account has since become `DELETED`/`SUSPENDED` is rejected
  without ever being marked used — confirmed this matches the plan's explicit flow description
  (not left to interpretation during coding).

## Build verification

Same situation as T03/T05's predecessors: `mvn -pl services/auth compile` still fails on the
pre-existing, unrelated `token` package issue (`OAuth2TokenType`, `JwtAuthenticationConverter` —
unchanged since 2026-07-13, per the memory note saved during T03). Verified the four new/changed
files independently via targeted `javac` against the module's resolved dependency classpath
(`mvn dependency:build-classpath`) with `-sourcepath services/auth/src/main/java` — this pulls in
only the transitive chain these files actually reference (`account.*`, `common.Hashing`), none of
which touches `token`. **Compiled with zero errors.**

**Not run in this phase** (Phase 10 scope, per guardrails): unit tests for
`VerificationTokenService`, and therefore `mvn test`.
