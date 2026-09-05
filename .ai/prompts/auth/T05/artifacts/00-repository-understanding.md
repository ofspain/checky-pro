# auth · T05 — Phase 0: Repository Understanding

## 1. Architecture summary

Same service as T03/T04: `services/auth`, Spring Boot 3.5.4 / Java 21, package-by-feature,
Flyway-owned schema (Hibernate `validate`-only), one Postgres schema (`auth`). Relevant subset for
T05:

- **`account` module** owns the `Account` aggregate and, per `design.md` §6's package map, will
  also own `VerificationToken`/`VerificationTokenRepository`/`VerificationTokenService` — this
  task's target.
- **Persistence**: the `verification_tokens` table already exists in schema (V1 migration, part of
  the original DDL — not new work for this task). No new Flyway migration is needed.
- **Outbox/events**: not directly touched by T05 itself — `auth.email.requested` (R3) is emitted by
  the *registration* path (task 6's `AccountController`/`AccountService` wiring), not by the token
  service in isolation. T05 builds the token issue/verify/consume primitives task 6 will call.

## 2. Existing code this task touches

**New (nothing under these names exists yet — confirmed via `ls
services/auth/src/main/java/com/themistra/auth/account/`):**
- `VerificationToken.java` (entity)
- `VerificationTokenRepository.java`
- `VerificationTokenService.java`
- Config key `themistra.auth.verification-token.ttl-minutes` — not present in
  `application.properties` yet (T03 added only the `themistra.auth.password.*` block; the
  verification-token line from `design.md` §4c's verbatim config block was out of T03's scope and
  was not added).

**Existing, to read/reuse, not modify:**
- `verification_tokens` table (V1 migration, `design.md` §4c "Relevant existing DDL"): `id`,
  `account_id` (FK, internal bigint), `purpose` (`CHECK (purpose IN ('EMAIL_VERIFY',
  'PASSWORD_RESET'))`), `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at`, `used_at`,
  `created_at`. Indexed on `account_id`.
- `common.Hashing.sha256(String)` — existing SHA-256 hex-digest utility, the established pattern
  for storing only a hash of a sensitive raw value (already used this way for `api_keys.key_hash`
  conceptually and for audit user-agent hashing). Directly reusable for `token_hash`.
- `Account.java` — `activateEmail()` (line 68) already exists and is guarded
  (`requireStatus(PENDING_VERIFICATION, "activate email")`); `AccountStatus` enum already has all
  five values (`PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, `SUSPENDED`, `DELETED`). `AccountService`
  has a `getAccount(UUID)`-style internal lookup (`findByAccountUuid`) T05's service will need to
  resolve the internal `account_id` FK from an external `accountUuid`.
- `AccountService.register(...)`'s own javadoc (already in the codebase) explicitly says: *"The
  `auth.email.requested` event that would trigger the verification email belongs to the
  not-yet-built verification-token flow (account module, email-verification stage)"* — i.e., the
  codebase already anticipates this task and defers the event-emission wiring to it/task 6.
- `AdminAccountController`'s javadoc: *"verification flow is not yet built, so this admin action
  stands in for it until it is"* — same forward-reference.

## 3. Established patterns to follow

- **Entities**: `bigint identity` internal PK, external UUID never required here (verification
  tokens are looked up by hashed token value, not by their own UUID — the table has no UUID
  column). FK to `accounts.id` (internal), not `account_uuid`.
- **Hashing sensitive values before persistence**: `common.Hashing.sha256(...)`, matching the
  `CHAR(64) NOT NULL UNIQUE` column shape already in the table.
- **Repositories**: package-private, one per aggregate, consistent with `AccountRepository`
  (ArchUnit-enforced — `repositories_are_never_public`).
- **Services**: constructor-injected `@Service`, `Clock` injected for TTL/expiry logic (never
  `Instant.now()`/`Clock.systemUTC()` inline), consistent with `AccountService`/`AuditService`.
- **Config**: a new validated `@ConfigurationProperties` record for
  `themistra.auth.verification-token.ttl-minutes`, following the `PasswordPolicyProperties`
  precedent from T03 (record, `@Validated`, Jakarta Bean Validation annotations,
  `@ConfigurationPropertiesScan` already enabled service-wide — no manual bean registration
  needed).
- **Module boundaries**: stays within `account` per `design.md` §6; no cross-module entity leakage
  (ArchUnit's `only_the_account_module_may_touch_the_Account_entity` rule doesn't apply to
  `VerificationToken`, but the same discipline should hold — this task's own entity should stay
  `account`-package-private the same way).

## 4. Testing conventions

Unit tests: plain JUnit 5 + Mockito, no Spring context, fixed `Clock` via
`Clock.fixed(NOW, ZoneOffset.UTC)` — required here specifically, since "Implement... TTL checks"
means expiry-boundary tests are the core of this task's test surface (same shape as T03's
`BreachCheckClient` timeout tests, T02's account tests). No Testcontainers test is implied by this
task's literal scope (no controller, no cross-service Kafka assertion needed for pure issue/verify/
consume logic).

## 5. Known gaps / unknowns

**Likely recurrence of the T03/T04 named-test-scoping issue — flagging early this time.**

T05's named tests (`shouldActivateAccountWithValidVerificationToken`,
`shouldNotRevealAccountExistenceForInvalidVerificationToken`) describe end-to-end behavior — an
account actually transitioning to `ACTIVE`, and a uniform *response* for invalid tokens — that
requires an HTTP endpoint and `AccountService` wiring. T05's own task statement is narrower: *"Add
`VerificationToken`, repository, and service. Implement issue, verify, consume, and TTL checks."*
No controller, no `AccountService` change, no endpoint is mentioned. The endpoint work
("Self-service verification endpoints," `POST /accounts/verify-email` /
`POST /accounts/resend-verification`, wiring `activateEmail()` and emitting
`auth.email.requested`/`auth.user.registered`) is `tasks.md` task 6, a separate task.

This is the same shape as the T03/T04 overlap (T03 absorbed T04's audit-wiring because T03's own
named test required it). Here, T05's named tests plausibly require *task 6's* work (an endpoint) to
be meaningfully testable as literally named — a pure `VerificationTokenService` unit test can prove
"issue/verify/consume/TTL work correctly" but cannot itself prove "an account was activated" (no
`AccountService` call in scope) or "an HTTP response is uniform" (no controller in scope).

**Resolved by investigating T06's own Phase 0 header directly** (`.ai/prompts/auth/T06/
00-repository-understanding.md`) before proceeding, given the T03/T04 precedent:

This is *not* the same shape as T03/T04 (which was full redundancy). T06's task statement ("Extend
`AccountController` with `POST /accounts/verify-email` and `POST /accounts/resend-verification`...")
is genuinely separate, later work that depends on T05 existing first — a real sequential
dependency, not an overlap. T06 scopes R3, R4, R6, R44 and its *own* named-test list includes
`shouldActivateAccountWithValidVerificationToken` — the same name T05 lists — confirming that
test's literal, full (HTTP-level) realization belongs to T06, not T05.

T05's *other* named test, `shouldNotRevealAccountExistenceForInvalidVerificationToken`, maps to R5
— which is scoped to T05, not T06 (T06's scoped IDs are R3/R4/R6/R44, no R5). R5's uniform-response
behavor most naturally fits `tasks.md` task 10 ("Enumeration safety tests... invalid verification
tokens... produce identical responses"), a later task not yet started.

**Decision (human-confirmed):** T05 proceeds narrowly, exactly as literally scoped — service layer
only, no endpoint. Both named tests are treated as describing intent to be verified at the
*service* level for T05's own purposes:
- `shouldActivateAccountWithValidVerificationToken` → service-level equivalent: issuing then
  verifying/consuming a valid token succeeds and returns whatever the service's own contract
  promises (its literal, full HTTP-level realization comes with T06's endpoint).
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` → service-level equivalent: an
  invalid, expired, already-used, or wrong-purpose token is rejected *uniformly* (same outcome
  shape, no distinguishing exception type or return value between reasons) — its literal
  HTTP-response-level realization belongs to task 10.

This will be made explicit and traced in Phase 1.
