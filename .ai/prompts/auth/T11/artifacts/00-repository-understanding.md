# auth · T11 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/auth` is a package-by-feature Spring Boot 3.5.4 / Java 21 module (`com.themistra.auth`),
acting as the platform's OIDC/OAuth2 identity issuer via Spring Authorization Server. Relevant
existing packages:

- **`account`** — the `Account` aggregate (JPA entity, guarded state-transition methods),
  `AccountService`, `AccountController`, `PasswordPolicy`/`PasswordPolicyProperties`,
  `VerificationToken`/`VerificationTokenService` (issue/verify/consume, hashed + TTL'd, fixed
  `Clock`).
- **`authn`** — currently holds `AccountUserDetailsService`, `BreachCheckClient`, and an empty
  `package-info.java`. This is where T11's `LockoutStateMachine` (and later T12's
  `LockoutService`) belong per §6 of `design.md`.
- **`common`** — shared plumbing only (per L12/module boundaries). `SecurityBeansConfig` exposes
  the single `Clock` bean (`Clock.systemUTC()` in prod) that all time-dependent services inject
  rather than calling `Instant.now()` directly.
- **`audit`** — `AuditService`/`AuditOutcome`/`RecordAuditEventRequest`, the append-only
  `auth_audit` mirror used by every security-relevant action.
- Persistence: PostgreSQL, Flyway DDL-only migrations, V1–V4 immutable, JPA for simple
  find/save. `lockout_state` table already exists from **V1**:
  ```sql
  CREATE TABLE lockout_state (
      account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
      failed_attempts INT NOT NULL DEFAULT 0,
      last_failed_at TIMESTAMPTZ,
      locked_until TIMESTAMPTZ,
      lock_count INT NOT NULL DEFAULT 0
  );
  ```
  V5 (already delivered in T01) added only `idx_lockout_state_locked_until` and the `shedlock`
  table — no changes to `lockout_state`'s shape.
- Config: flat `application.properties`, validated `@ConfigurationProperties` records
  (`PasswordPolicyProperties` is the reference pattern), startup fails on missing/invalid values
  in non-local profiles. `application.properties` does **not yet** contain the `themistra.auth.lockout.*`
  keys from `design.md` §4c — only `password.*` keys are present today.

## 2. Existing code this task touches

**Already exists (do not duplicate/re-derive):**
- `Account.lock()` / `Account.unlock()` — guarded `ACTIVE ↔ LOCKED` transitions, already wired
  and used by T09's `resetPassword` (`unlock()` is called there before password change). These
  are the only legal way to flip `AccountStatus.LOCKED`; `LockoutStateMachine` must not attempt
  to mutate `Account` itself — that is `LockoutService`'s job in T12, which "ties `Account.lock()`
  / `unlock()` to `AccountService`" per `tasks.md` item 12.
- `lockout_state` table (V1) and its expiry index (V5) — schema is ready; no `LockoutState` JPA
  entity or repository exists yet in `main/java`. Entity/repository mapping is listed as `LockoutService`
  territory (T12), not T11.
- `SecurityBeansConfig.clock()` — the `Clock` bean T11's unit tests will inject a fixed instance
  of directly (no Spring context needed, per `agents.md` Testing rule).

**New in this task (per `design.md` §6, `authn/`):**
- `LockoutProperties.java` — validated config record for `max-attempts` / `window-minutes` /
  `base-lock-minutes` (config keys already specified in `design.md` §4c but not yet added to
  `application.properties` — T11 is pure logic + unit tests only, so whether adding the
  properties file/keys is in-scope for T11 or deferred to T12 is a judgment call for Phase 1/2).
- `LockoutStateMachine.java` — "pure logic, unit-testable" per the package map annotation. Not a
  `@Service`; no repository, no `AccountService` wiring, no persistence side effects. Given
  `lock_count` in the table doubles the *effective* lock duration on each subsequent lock (L4),
  the machine's output is most naturally a pure function of
  `(failedAttempts, lastFailedAt, lockedUntil, lockCount, now)` → next state, not a
  Spring-managed singleton with injected dependencies.

**Not in scope for T11** (explicitly later tasks per `tasks.md`):
- T12 — `LockoutService` (loads/updates `lockout_state`, decay, ties to `Account.lock()`/`unlock()`).
- T13 — wiring lockout counter increment/reset into the SAS authentication success/failure path.
- T14 — admin unlock endpoint.
- T15 — indistinguishable-response security test across locked/suspended/deleted/non-existent.

## 3. Established patterns to follow

- **Pure-logic classes** live alongside `@Service` classes in the same package but carry no
  Spring annotations and take no Spring-managed collaborators — construct directly with `new` in
  tests. `PasswordPolicy` is the closest analogue but is itself a `@Service` (it has audit/HTTP
  collaborators); `LockoutStateMachine` should be simpler still, closer to a stateless
  calculator (inputs in, decision out), consistent with the "pure logic, unit-testable"
  annotation in `design.md` §6.
- **Config records**: `@ConfigurationProperties(prefix = "themistra.auth.lockout")` +
  `@Validated`, bounded with `@Min`/`@Max`/`@Positive`/`@NotBlank` as appropriate, mirroring
  `PasswordPolicyProperties`. Locked numeric defaults (5 attempts / 30 min window / 15 min base
  lock) come from L4 and should not be silently reconfigurable outside sane bounds, per the
  Phase 8/9 precedent set on `PasswordPolicyProperties.minLength/maxLength`.
- **Time**: no `java.util.Date`; all instants via `java.time` and an injected `Clock` — but since
  `LockoutStateMachine` is pure logic, `Clock` (or a plain `Instant now` parameter) is passed in
  by the caller rather than injected as a field, keeping the class trivially testable without
  Mockito.
- **Exceptions**: existing account-domain exceptions (`InvalidAccountStateException`,
  `PasswordPolicy.PasswordPolicyViolationException`) are unchecked, nested where scoped to one
  class. Whether `LockoutStateMachine` needs an exception type at all is unclear — it likely just
  returns a decision/result value rather than throwing (Phase 1/2 to decide).
- **Doubling lock duration (L4)**: "Each subsequent lock doubles the effective duration via
  `lock_count` until it is reset." No existing code implements this yet — this is new domain
  logic T11 must define precisely (e.g. `base-lock-minutes * 2^lock_count`, capped or uncapped)
  since the spec text alone is ambiguous on cap/overflow behavior. Flagged as an open question
  for Phase 1.

## 4. Testing conventions

- Plain JUnit 5, fixed `Clock`/fixed `Instant` values, no Spring context for pure/unit-level
  classes (`agents.md` Testing rule, confirmed by `PasswordPolicyTest`'s class javadoc: "Plain
  JUnit + Mockito, no Spring context, per `agents.md`").
- `@ExtendWith(MockitoExtension.class)` only for classes with collaborators to mock; a pure
  `LockoutStateMachine` with no collaborators would need no Mockito at all — direct
  construction and assertion.
- AssertJ (`assertThat`, `assertThatThrownBy`, `assertThatCode`) is the assertion library used
  throughout.
- Named tests from `package.md` §8 scoped to this task: `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes`
  and `shouldResetLockoutCounterOnSuccessfulLogin`. Note `package.md` labels these `→ R15` /
  `→ R16`, while `requirements.md`'s actual numbering has the lockout rules at `R16`–`R19` and
  the T11 phase-template header cites `R16`–`R19` — this is the same kind of requirement-ID
  drift already seen and triaged in earlier tasks (see memory: several Kimi-flagged
  numbering issues turned out to be pre-existing spec inconsistencies, not implementation bugs).
  Not a T11 blocker; worth a one-line Open Question note in Phase 1 rather than silently picking
  one numbering.
- "Boundary" unit-testing (per the task statement "Unit-test boundaries") implies tests at
  exactly 4 vs 5 failed attempts, exactly at the 30-minute decay window edge, and exactly at
  lock expiry — standard off-by-one-sensitive pure-function testing.

## 5. Known gaps / unknowns

- **Doubling formula for `lock_count`**: L4 says duration doubles "via `lock_count`" but does not
  specify the exact formula, any cap, or whether `lock_count` increments before or after the
  duration is computed for the *current* lock (i.e., does the first lock use `lock_count=0` →
  15 min, and the *second* lock double to 30 min?). I do not know the intended formula from the
  spec text alone — this needs to be resolved in Phase 1 (requirements extraction) or Phase 2
  (design), not assumed here.
- **Decay semantics precision**: R19 says the counter decays to zero if it "does not reach 5
  within 30 minutes of the last failure" — this reads as a lazy decay evaluated against
  `last_failed_at`, not an active/scheduled reset. I do not know whether `LockoutStateMachine` is
  expected to expose a decay check as a discrete method/step or whether decay is just an implicit
  precondition inside the attempt-recording transition. Left for Phase 1/2.
- **`LockoutProperties` config keys**: `design.md` §4c specifies the three `themistra.auth.lockout.*`
  properties, but they are not present in `application.properties` yet. Whether T11 must add
  them (since `LockoutStateMachine` needs default values to be unit-testable in isolation) or
  whether T11's tests just construct a `LockoutProperties`-equivalent record directly with
  literal values is a Phase 2 design call, not resolved here.
- Everything else required by T11 (entity, repository, service wiring, SAS integration, admin
  endpoint) is confirmed out of scope by `tasks.md` items 12–14 and not investigated further here.
