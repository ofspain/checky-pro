# auth · T09 — Phase 0: Repository Understanding

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 module, package-by-feature under
`com.themistra.auth.{account,authn,authz,audit,events,token,common}`, one module per bounded
concern with ArchUnit enforcing `api → application → domain` layering and forbidding cross-module
entity imports (`agents.md`). Config is flat `application.properties` bound to validated
`@ConfigurationProperties` records. Persistence is Postgres/Flyway (V1–V4 immutable); every
state change other services care about is published through the outbox
(`OutboxPublisher.publish(...)`) in the same transaction as the DB write. This service is the
platform's identity issuer (OIDC/OAuth2 via Spring Authorization Server). Errors are RFC 9457
`application/problem+json` via `AccountExceptionHandler` (`@RestControllerAdvice`) + `ProblemTypes`
constants. Security-relevant actions are recorded via `AuditService.record(RecordAuditEventRequest)`
into the append-only `auth_audit` table, mirrored to Kafka.

## 2. Existing code this task touches

**`PasswordPolicy`** (`account` package) already exists in full, built in T03, and already has a
real production caller as of T08:

- `PasswordPolicy.validate(String rawPassword, UUID accountUuid, UUID actorUuid)` — null-guards
  both UUIDs (`Objects.requireNonNull`), then validates length (`L2`: 12–128 chars) and queries
  `BreachCheckClient` (HIBP k-anonymity range API, `authn` module) for breach status. On breach,
  throws `PasswordPolicyViolationException`. On breach-check API failure, fails open and records
  a `password.breach_check_failed` audit event with `accountUuid`/`actorUuid` as target/actor
  (R9/R10, `L2`).
- `AccountExceptionHandler` already maps `PasswordPolicyViolationException` → `400`
  `application/problem+json` with `ProblemTypes.VALIDATION_ERROR` and the exception's message as
  `detail` (added at T08). This mapping is generic — it will apply to any caller of
  `PasswordPolicy.validate`, not just `changePassword`. Nothing new needs to be added here for T09.

**`AccountService.changePassword`** (T08) is the only current production caller of
`PasswordPolicy.validate`:
```java
passwordPolicy.validate(newPassword, accountUuid, accountUuid);
```
called after the current-password check, before the mutation, inside a fixed method order
(status → current-password → policy → mutate → audit) proven by `InOrder`-based tests.

**`AccountService.register(RegisterAccountRequest request)`** — does **not** call
`PasswordPolicy.validate` anywhere. It goes straight to
`passwordEncoder.encode(request.password())`. The only length enforcement on this path today is
bean validation on the DTO: `RegisterAccountRequest.password()` carries `@NotBlank @Size(min =
12, max = 128)`. That DTO's own Javadoc says: *"Breached-password screening is a separate policy
check in the authn module, not bean validation."* — i.e. the DTO annotation was always meant to be
a partial stand-in, not the real enforcement point; HIBP breach screening (R9/R10) is completely
absent from the registration path today.

**`AccountService.resetPassword(String rawToken, String newPassword)`** — also does **not** call
`PasswordPolicy.validate`. It goes straight to
`account.changePasswordHash(passwordEncoder.encode(newPassword))` after token/eligibility checks.
Unlike registration, there isn't even partial bean-validation coverage: `PasswordResetConfirmRequest
.newPassword()` carries only `@NotBlank`, no `@Size` and no length bound at all. This is the
weakest-covered of the three paths today — a reset can currently set a password of any length or a
known-breached password.

**Controller layer** (`AccountController`) needs no changes for this task — `register`,
`passwordReset`, and `changePassword` all already propagate exceptions to
`AccountExceptionHandler` uncaught; a new `PasswordPolicyViolationException` thrown deeper inside
`AccountService.register`/`resetPassword` will be handled by the exact same existing mapping
`changePassword` already relies on.

**Tests that will need updating** (named explicitly by the task statement):
- `AccountServiceTest.java` — `registerHashesPasswordNormalizesEmailAndReturnsView`,
  `registerRejectsKnownDuplicateWithoutTouchingEncoder`, `registerMapsConstraintRaceToDuplicateEmail`,
  and the `resetPassword`/`shouldResetPasswordAndRevokeAllFamiliesWithValidToken`-family tests
  currently exercise `register`/`resetPassword` with no `passwordPolicy` mock interaction at all.
  The `passwordPolicy` field is already a `@Mock` in this test class (wired into the
  `AccountService` constructor since T08) — no constructor-signature change is needed this time,
  only new stubbing/verification on the existing mock plus new rejection-path tests.
- `AccountControllerTest.java` — register/password-reset tests will need
  `PasswordPolicyViolationException`-propagation coverage mirroring the pattern T08 already
  established for `changePassword` (`changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate`).

## 3. Established patterns to follow

- **Fixed method ordering + `InOrder` proof.** T08's `changePassword` set the precedent: each gate
  runs only if the prior one passed, and tests prove the sequence with Mockito `InOrder`
  (`AccountServiceTest.java` T08 tests), not just that each mock was eventually called.
- **`PasswordPolicy.validate` signature is already `(rawPassword, accountUuid, actorUuid)`,
  both non-null.** Both `register` and `resetPassword` have a real account UUID available at the
  point policy validation would run (`saved.getAccountUuid()` for register, once persisted;
  `account.getAccountUuid()` for reset, once the token is resolved) — self-service callers pass
  the same UUID for both parameters, exactly as `changePassword` already does.
- **Exceptions propagate uncaught from `AccountService` to `AccountExceptionHandler`** — no
  per-endpoint try/catch in the controller for policy violations, consistent everywhere in this
  module.
- **Outbox/audit only on the paths that already have it.** `register` already emits
  `auth.email.requested` after save; `resetPassword` already records `password.reset` and revokes
  refresh-token families. Adding a policy check doesn't add or remove any event — it only adds a
  new possible failure mode before the existing success path.
- **DTO `@Size`/`@NotBlank` bean validation is a UX nicety, not the enforcement point** —
  `PasswordPolicy.validate` is the actual policy authority (per `RegisterAccountRequest`'s own
  Javadoc). Whether `PasswordResetConfirmRequest` also gains a `@Size` annotation to match
  `RegisterAccountRequest`'s existing pattern, or is left as `PasswordPolicy.validate` alone, is a
  design question for Phase 1/2 — not decided here.

## 4. Testing conventions

Plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock.fixed(...)`. No `MockMvc` /
`@WebMvcTest` anywhere in the module (confirmed by grep in T08 Phase 11 — still true, no new files
since). ArchUnit (`ArchitectureTest`) enforces module boundaries; a pre-existing, unrelated
violation (`AccountResponse.from(Account)`) was found and logged at T07 Phase 10, still unfixed,
out of scope for this task. `mvn -pl services/auth test` still cannot run to completion due to the
pre-existing `token` package compile break (tracked since T03) — verification for this task will
need the same `javac` + JUnit Platform Launcher workaround used every phase since.

## 5. Known gaps / unknowns

- **Requirement-ID drift between `package.md` §8 and the current `requirements.md`.** This task's
  generated prompt header cites scoped requirement IDs `R8`/`R9`/`R10` (which do match
  `requirements.md`'s current length/breach/fail-open text). But `package.md` §8's own
  test→requirement table maps `shouldRejectPasswordShorterThan12OrLongerThan128` → `R11` and
  `shouldRejectBreachedPasswordUsingHibpRange` → `R12` — numbers that in the *current*
  `requirements.md` refer to unrelated requirements (change-own-password and
  password-reset-request-acknowledgement, respectively). The named tests' actual content
  (length bound, HIBP breach) matches `requirements.md`'s current `R8`/`R9`, not its `R11`/`R12`.
  This looks like `package.md` was written against an earlier `requirements.md` numbering that has
  since shifted, and was never re-synced. **I do not know** which document is meant to be treated
  as authoritative when they disagree — this needs to be resolved explicitly in Phase 1 by
  matching test intent to requirement *content*, not by requirement *number* alone, and flagged
  again there rather than silently picked.
- **Whether `PasswordResetConfirmRequest` should also gain a `@Size(min=12,max=128)` annotation**
  to mirror `RegisterAccountRequest`'s existing partial bean-validation coverage, or whether
  `PasswordPolicy.validate` alone is sufficient (matching `changePassword`'s existing pattern,
  which has no DTO-level `@Size` on `ChangePasswordRequest.newPassword()` either). Not decided
  here — Phase 1/2 design question.
- **Whether `register`'s policy-violation failure path needs to stay enumeration-safe.** `L5`
  lists registration among the enumeration-safe endpoints, but `L5` is about *account/email
  existence*, not password-content rejection — `DuplicateEmailException` already returns the
  identical `202` regardless of pre-existing account, while a `PasswordPolicyViolationException`
  is presumably fine to surface distinctly (the caller already knows what password they submitted,
  there's no existence signal). I do not know if this distinction is written down anywhere
  explicitly; worth confirming in Phase 1 rather than assuming.
