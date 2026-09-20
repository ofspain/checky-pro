# auth · T05 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md` against the spec package,
`agents.md`, and existing code. Findings are numbered for reference.

## Finding 1 — Raw token format, length, and encoding are undefined (HIGH)

**Issue:** The brief only says "Secure raw-token generation (`SecureRandom`)"; it does not specify
token length, character set, or encoding. This is a hidden assumption at the core of security,
collision probability, and the email links T06 will build.

**Severity:** HIGH — without a defined format, two implementers could choose incompatible schemes
(e.g., 16-byte base64 vs. 32-byte hex), affecting entropy, URL-safety, and whether the hash
uniqueness constraint can ever be meaningfully evaluated.

**Evidence:**
- TIB Scope lines 17–27: only says "Secure raw-token generation (`SecureRandom`)".
- TIB Constraints lines 136–139: requires SHA-256 hash persisted, raw value never logged, but gives
  no raw-token shape.
- `verification_tokens.token_hash` is `CHAR(64)` (`design.md` lines 147–156), which constrains only
  the hash, not the raw token.

**Recommended brief amendment:** Lock the raw token to a concrete shape, e.g.:
"A raw token is 32 bytes from `SecureRandom`, encoded with URL-safe Base64 (no padding) producing a
43-character string. The service returns this string once from `issue(...)`; only its SHA-256 hex
hash is persisted."

---

## Finding 2 — Double-consume is not guaranteed to be atomic (HIGH)

**Issue:** The brief describes `consume` as "sets `used_at` on the matched row" but does not specify
how to prevent two concurrent requests from both seeing `used_at == null` and succeeding. A
read-then-write `consume` can violate the single-use guarantee under load.

**Severity:** HIGH — R5/R14 depend on a token being single-use; a race that allows two successful
consumes is a security defect.

**Evidence:**
- TIB State Changes lines 79–85: `consume` sets `used_at`; no mention of atomicity.
- TIB Acceptance Criteria line 119–120: "Single-use — consuming a token twice: the second attempt is
  rejected."
- `verification_tokens` DDL (`design.md` lines 147–156) has no `@Version`/optimistic-locking column
  and no partial unique index on `used_at`.

**Recommended brief amendment:** Require an atomic update for `consume`:
"`consume` must execute a conditional update equivalent to `UPDATE verification_tokens SET used_at =
? WHERE token_hash = ? AND used_at IS NULL`, and treat zero affected rows as an already-used token
(returns the uniform invalid outcome)." Alternatively, require an `@Version` column and optimistic
locking with uniform handling of `OptimisticLockingFailureException`.

---

## Finding 3 — The relationship between `verify` and `consume` is ambiguous (MEDIUM)

**Issue:** The brief says implement "issue / verify / consume" but does not say whether callers
should call `verify` first and then `consume`, or whether `consume` is itself verity-and-mark. A
T06/T07 caller that calls `verify` then `consume` non-atomically creates a time-of-check/time-of-use
window.

**Severity:** MEDIUM — could produce code paths where a token is valid at verify but consumed by a
concurrent request before the account is updated, leading to confusing failures or inconsistent
state.

**Evidence:**
- TIB Scope lines 21–22: "`VerificationTokenService` with issue / verify / consume operations and
  TTL enforcement."
- TIB Outputs lines 72–77: "`verify`/`consume` return a uniform 'valid' result ... or a uniform
  'invalid' result."

**Recommended brief amendment:** Define the contract explicitly:
- `verify(rawToken)` — read-only check, returns valid/invalid without side effects.
- `consume(rawToken)` — atomic verify-and-mark; the only state-mutating redemption path.
- Later tasks should call `consume` exactly once and perform account mutations in the same
  transaction as the caller (T06/T07).

---

## Finding 4 — Account-state validation at `consume`/`verify` is incomplete for purpose-generic handling (MEDIUM)

**Issue:** R4 allows email verification only for `PENDING_VERIFICATION` accounts; R14 implies
password reset for `ACTIVE` or `LOCKED` (not deleted/suspended) accounts. The brief only says
check `DELETED`/`SUSPENDED` → uniform rejection. It does not state whether T05 should reject a
`PASSWORD_RESET` token for a `PENDING_VERIFICATION` account, or an `EMAIL_VERIFY` token for a
`LOCKED` account.

**Severity:** MEDIUM — leaving this to callers (T06/T07) is a hidden dependency. If T05 returns
"valid" for these purpose/state mismatches, later tasks must add filtering; if they don't, the
system may accept tokens in invalid states.

**Evidence:**
- TIB Dependencies lines 57–59: "read account status at verify time (`DELETED`/`SUSPENDED` → uniform
  rejection per R5)."
- TIB Business Rules lines 40–46: R4/R5 only.
- `requirements.md` R4 (line 10) and R14 (line 23) have different implied valid account states.

**Recommended brief amendment:** State one of:
(a) T05 rejects `DELETED`/`SUSPENDED` only; T06/T07 must enforce purpose-specific states, or
(b) T05 rejects any token whose account status is not appropriate for the purpose (list the allowed
states per purpose).

Option (a) is simpler and preserves the generic service; option (b) is safer but couples purpose
to account state in the service.

---

## Finding 5 — `issue` semantics for non-existent or non-issuable accounts are undefined (MEDIUM)

**Issue:** `issue` takes an account UUID and purpose. The brief does not say what happens if the
account UUID does not exist, or if the account is `DELETED`/`SUSPENDED`. T06 and T07 expect to issue
tokens only for issuable accounts, but the service boundary needs a clear contract.

**Severity:** MEDIUM — a hidden assumption that callers always pass a valid, issuable account UUID
makes the service fragile.

**Evidence:**
- TIB Inputs lines 65–66: "To `issue`: account UUID, purpose (`EMAIL_VERIFY` or `PASSWORD_RESET`)."
- TIB Dependencies lines 57–59: uses `AccountRepository` to resolve internal id and read status, but
  only at verify time.

**Recommended brief amendment:** Specify `issue` behavior:
- If account UUID is null → clear intentional error (per Null handling constraint).
- If account is not found → throw a domain exception (e.g., `AccountNotFoundException` or a generic
  `VerificationTokenException`), because `issue` is an internal service call from T06/T07, not an
  enumeration-sensitive public boundary.
- Optionally, reject `issue` for `DELETED`/`SUSPENDED` accounts; if deferred, document that callers
  must pre-check.

---

## Finding 6 — Hash-collision handling is flagged but not resolved (MEDIUM)

**Issue:** The brief notes that a `DataIntegrityViolationException` on `token_hash` unique
constraint must not be unhandled, but it stops at "retry-on-collision as a named risk for Phase 5
to address." Without a specified token length (Finding 1), even the collision strategy is
meaningless.

**Severity:** MEDIUM — a 32-byte random token makes collisions practically impossible, but the
service still needs a deterministic failure mode if a duplicate is somehow generated or if an
attacker pre-computes a hash.

**Evidence:**
- TIB Constraints lines 150–152: uniqueness risk noted but not resolved.
- `verification_tokens.token_hash` has `UNIQUE` (`design.md` line 151).

**Recommended brief amendment:** After locking the raw-token format (Finding 1), add:
"On insert, if the hash collides with an existing row, `issue` must retry up to N times with a new
random token. If all retries fail, throw an `IllegalStateException`. This is a defensive fallback
for an astronomically unlikely event and must never expose the existing token."

---

## Finding 7 — `ttl-minutes` validation and boundaries are unspecified (MEDIUM)

**Issue:** The config key `themistra.auth.verification-token.ttl-minutes=30` is described as
"validated @ConfigurationProperties record" but no constraints are listed. A TTL of `0` or negative
would create instantly-expired tokens and break registration/reset flows silently after startup.

**Severity:** MEDIUM — violates `agents.md` "startup FAILS on missing/invalid values in non-local
profiles" if invalid values are not rejected.

**Evidence:**
- TIB Scope lines 23–25: "validated @ConfigurationProperties record ... config key not yet present;
  verbatim value `30` per `design.md` §4c."
- TIB Dependencies line 63: config key `themistra.auth.verification-token.ttl-minutes=30`.
- `agents.md` (Configuration): "Config is bound to validated `@ConfigurationProperties` records;
  startup FAILS on missing/invalid values in non-local profiles."

**Recommended brief amendment:** Add a `@Min(1)` (and optionally `@Max(...)`) constraint to the TTL
record, and include a test that a TTL of 1 minute expires exactly at boundary and a TTL of 0 is
rejected/impossible.

---

## Finding 8 — Resending/reissuing tokens does not address previously issued active tokens (LOW/MEDIUM)

**Issue:** R6 says resending verification generates a new token. The brief does not say whether
previous, unexpired tokens for the same account and purpose remain valid. Both choices are
defensible, but it is an unstated assumption that affects user experience, security, and test
assertions.

**Severity:** LOW/MEDIUM — not a bug in itself, but tests and T06 cannot be authored consistently
without a decision.

**Evidence:**
- TIB Scope lines 17–27 and State Changes lines 79–85: no mention of invalidating prior tokens.
- `requirements.md` R6 (line 12): "generate a new verification token."

**Recommended brief amendment:** Decide and document:
- Either "issuing a new token for `(account, purpose)` does NOT invalidate previously issued tokens;
  all unexpired, unused tokens remain valid until consumed or TTL," or
- "`issue` invalidates (sets `used_at`/`expires_at` or deletes) any prior unexpired token of the
  same purpose for that account before inserting the new one."

The second option is more conventional for password-reset flows.

---

## Finding 9 — `created_at` / `used_at` timestamps and `expires_at` calculation should use the Clock (LOW/MEDIUM)

**Issue:** The brief says "Clock (existing bean) — TTL/expiry comparisons, `created_at`; never
`Instant.now()` inline." It does not explicitly say `used_at` must use the Clock, nor how `expires_at`
is derived from the TTL and the current instant.

**Severity:** LOW/MEDIUM — if `used_at` uses `Instant.now()` while `expires_at` uses the fixed Clock,
tests can pass but production drift/clock skew handling becomes inconsistent. The existing
`Account` entity already uses `Instant.now()` in `@PrePersist`/`@PreUpdate`, which is a precedent the
new entity should avoid.

**Evidence:**
- TIB Dependencies line 61: "`Clock` (existing bean) — TTL/expiry comparisons, `created_at`; never
  `Instant.now()` inline."
- `Account.java` lines 126–136: uses `Instant.now()` in lifecycle callbacks.
- TIB State Changes lines 79–85: mentions `used_at` but not Clock.

**Recommended brief amendment:** Explicitly state:
"All timestamps (`created_at`, `expires_at`, and `used_at`) are derived from the injected `Clock`.
`issue` computes `expires_at = clock.instant().plus(ttlMinutes, MINUTES)`; `consume` sets
`used_at = clock.instant()`. The JPA entity fields are set by the service, not by `@PrePersist`
lifecycle callbacks using `Instant.now()`."

---

## Finding 10 — The `issue` return type is ambiguous ("and/or") (LOW/MEDIUM)

**Issue:** The brief says `issue` "returns the raw token value ... and/or the persisted
`VerificationToken`". This "and/or" leaves the API shape undecided, which T06 depends on.

**Severity:** LOW/MEDIUM — T06 needs the raw token to embed in the email link and the account/purpose
to emit the event. If `issue` returns the raw token and the persisted entity, the entity must not
accidentally include the raw token as a persisted field (risk of logging/serialization).

**Evidence:** TIB Outputs lines 72–74.

**Recommended brief amendment:** Define a clear return record, e.g.:
"`issue` returns a `VerificationTokenResult` containing the raw token string ( returned exactly once),
the persisted `VerificationToken` (hash only, no raw token field), and the account UUID/purpose."
Ensure the entity has no raw-token field and the result record has no `toString`/JSON serialization
that would leak the raw token.

---

## Summary

The brief correctly captures the scope and R5 uniform-response intent, but it leaves several
security-critical decisions unspecified (raw-token format, atomic consume, issue semantics, TTL
validation, timestamp source). The highest-priority amendments are Finding 1 (token format), Finding
2 (atomic consume), and Finding 4/5 (account-state and issue error semantics), because they directly
affect the correctness of token redemption and downstream tasks T06/T07.
