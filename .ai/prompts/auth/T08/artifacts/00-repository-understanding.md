# auth · T08 — Phase 0: Repository Understanding

No design or requirement extraction here — Phase 1's job. This is grounding only.

## 1. Architecture summary

`services/auth` is a package-by-feature Spring Boot 3.5.4 / Java 21 module. Relevant modules for
this task:

- **`account`** — the `Account` aggregate (guarded state transitions only), `AccountRepository`,
  `AccountService` (transactional use-cases, outbox publishing, audit recording),
  `AccountController` (REST), `AccountExceptionHandler` (`@RestControllerAdvice`), plus
  `VerificationTokenService`/`VerificationToken` (T05/T06/T07: email-verify and password-reset
  tokens) and `PasswordPolicy`/`PasswordPolicyProperties` (T03: NIST 800-63B length + HIBP breach
  screening, implemented but **not yet wired into any call path** — confirmed below).
- **`common`** — `PublicEndpoints` (CI-enforced exhaustive unauthenticated-path allowlist),
  `SecurityBeansConfig` (the delegating `PasswordEncoder`, `{bcrypt}` strength 12 — L3),
  `ApiExceptionHandler`/`ProblemTypes` (RFC 9457 `application/problem+json`).
- **`audit`** — `AuditService.record(RecordAuditEventRequest)`, append-only, mirrored to Kafka.
- **`events`** — `OutboxPublisher.publish(aggregateType, aggregateId, eventType, schemaVersion,
  payload)`, `EventTopics.forAggregateType(...)` (throws `IllegalStateException` on an unmapped
  type — fail loud).
- **`token`** — `RefreshTokenTracker`/`RefreshTokenFamily` (T02/T07: session tracking,
  `revokeAllForPrincipal` added in T07).

Persistence: PostgreSQL, Flyway (V1–V4 immutable, L1). Every account-facing state change that
other services or clients care about goes through the outbox in the same transaction.

## 2. Existing code this task touches

**Already exists, will be reused as-is:**
- `Account.changePasswordHash(String newPasswordHash)` (`Account.java:109-114`) — the guarded
  mutator already used by T07's `resetPassword`. Its only guard is `status == DELETED` →
  `InvalidAccountStateException`. No other status restriction — unlike password-reset (R13/R14,
  restricted to `ACTIVE`/`LOCKED`), R11 places no status condition on change-own-password beyond
  the caller being authenticated at all (which itself implies a live, valid JWT).
- `PasswordEncoder` (field already on `AccountService`, `SecurityBeansConfig`'s delegating
  encoder, L3) — `encode(...)` is already used; `matches(raw, encoded)` is not yet used anywhere
  in this module and will be needed here for "protected by current password."
- `AccountController`'s existing `/me` pattern (`AccountController.java:61-65`): derives the
  account from `Authentication.getName()` (the JWT `sub`, an account UUID string) rather than a
  path variable — no caller can target another account's identifier. This is the established
  pattern for every authenticated self-service endpoint and should be reused verbatim for
  `POST /accounts/me/password`.
- `AccountExceptionHandler` — the existing `@RestControllerAdvice` for account-scoped exceptions
  (currently maps `DuplicateEmailException`, `AccountNotFoundException`,
  `InvalidAccountStateException`, `VerificationTokenRejectedException`). A new exception/mapping
  for "wrong current password" will be needed — nothing existing fits.
- `PasswordPolicy.validate(String rawPassword)` (`PasswordPolicy.java`) — throws
  `PasswordPolicyViolationException`. Built and unit-tested in T03, **still not called from any
  production code path** — `register()` doesn't call it, T07's `resetPassword()` doesn't call it.
  `tasks.md` task 9 ("Password policy enforcement... apply to registration, change-password, and
  password-reset") is the dedicated later task that wires it into all three paths at once. T07's
  Phase 0 already surfaced and got a human-confirmed decision to defer this exact wiring for
  password-reset to task 9; the identical question applies here for change-password (see Known
  gaps below).

**New for this task:**
- `POST /accounts/me/password` endpoint on `AccountController`.
- A request DTO — `design.md`'s package map (§6) names it `dto/ChangePasswordRequest.java`
  (currently does not exist).
- An `AccountService` method (name TBD at Phase 2) taking the authenticated account UUID, the
  current password, and the new password.
- A way to signal "current password didn't match" distinctly from other rejection reasons — this
  endpoint is **not** in L5's enumeration-safety list (see Known gaps), so unlike T06/T07 there is
  no obvious requirement to make this failure uniform with anything else.

**Explicitly NOT touched by this task** (per its own literal scope):
- `PublicEndpoints.java` — this endpoint requires authentication; no new public path.
- `RefreshTokenTracker` — R11's text says only "update the password hash," unlike R14 which
  explicitly names family revocation. Not assumed here (see Known gaps).
- `PasswordPolicy` wiring — deferred to task 9, per the T07 precedent above.

## 3. Established patterns to follow

- **Authenticated self-service identity:** `Authentication.getName()` → `UUID`, never a path
  variable (`AccountController.me`).
- **Transactional service methods:** `@Transactional` on the `AccountService` method; any outbox
  publish and audit record happen inside the same transaction as the entity mutation (D-009).
- **Guarded entity mutations:** state/credential changes go through a method on `Account` itself
  (`changePasswordHash`), never field assignment from the service layer.
- **Exception-per-rejection-reason, mapped once:** `AccountExceptionHandler` maps exactly one
  exception type per rejection *class*; RFC 9457 `application/problem+json` throughout
  (`ApiExceptionHandler`/`ProblemTypes`).
- **Flat `application.properties`, validated `@ConfigurationProperties`** — no new config expected
  for this task (no new tunables implied by R11).
- **Constructor injection, no field injection** — every existing service in this module follows
  this; `AccountService`'s constructor already grew twice (T06, T07) for new collaborators.

## 4. Testing conventions

Plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock.fixed(...)`, per `agents.md`.
No `MockMvc`/`@WebMvcTest` precedent anywhere in this module — `AccountControllerTest` constructs
`AccountController` directly with a mocked `AccountService`, so HTTP-layer concerns
(`@RestControllerAdvice` translation) are tested separately, directly against the handler method
(`AccountExceptionHandlerTest`, established in T06).

Module-wide `mvn -pl services/auth compile`/`test` is still blocked by the pre-existing, unrelated
`token` package break (`SecurityChainsConfig`, `ReuseDetectingAuthorizationService`,
`AuthorizationServiceConfig` — tracked since T03, untouched, not this task's concern). Verification
in every prior task used isolated `javac` compilation against `mvn dependency:build-classpath`,
then execution via the JUnit Platform Launcher API — the same workaround applies here.

## 5. Known gaps / unknowns

- **PasswordPolicy wiring for change-password.** R11's text says "a new password meeting policy,"
  but `tasks.md` task 9 is the dedicated, later task that wires `PasswordPolicy` into
  registration, change-password, *and* password-reset together. T07 hit the identical tension for
  password-reset and got an explicit human decision at its Phase 0 ("Defer to task 9"). I do not
  know whether the same deferral should apply here without asking again — flagging it now,
  expect to raise it at Phase 1/3 for the same human-approval treatment, not assuming the answer
  carries over silently.
- **Is `POST /accounts/me/password` enumeration-sensitive?** L5's enumeration-safe endpoint list
  (design.md §4a) is "Login, registration, password-reset request, password-reset confirmation,
  and email verification" — it does **not** include change-own-password. That's consistent with
  the caller already being authenticated (they already know their own account exists), so a
  distinguishing "wrong current password" response doesn't leak anything an attacker doesn't
  already have (a valid JWT for that exact account). I do not know if the team wants this response
  distinguishable (e.g. a specific 400) or still folded into a generic uniform shape — this is a
  design question for Phase 1/2, not resolved here.
- **Session/family revocation on password change.** R14 (password-reset) explicitly names
  "revoke all refresh-token families." R11 (change-password) does not mention this at all. I do
  not know whether that's a deliberate distinction (password-reset implies possible compromise;
  a self-initiated change-password while already logged in does not) or an oversight in the spec
  text — flagging for Phase 1/3, not assuming either way.
- **Audit event name.** No `password.changed`-style event (or any name) is mentioned anywhere in
  `requirements.md`, `design.md`, or `package.md` for this task, unlike R14's explicit
  `password.reset`. `agents.md`'s general rule ("every security-relevant action is recorded")
  suggests one should exist by convention, but the spec package doesn't name it. Flagging for
  Phase 1/2.
- **`ChangePasswordRequest` field shape.** `design.md`'s package map names the file but not its
  fields. Inferring `currentPassword`/`newPassword` from R11's own wording ("their current
  password and a new password") — reasonable, but not spec-verbatim; will confirm in Phase 2's
  TIB rather than assume silently.
- **Contracts** (`auth.yaml`, `token-claims.md`, `email-requested.v1.schema.json`,
  `security-audit.v1.schema.json`) referenced in this task's header still do not exist anywhere in
  the repo (`contracts/api/` is empty except `.gitkeep`) — same pre-existing, not-T08-specific gap
  noted in every prior task since T06; `tasks.md` items 33/34 create them later.
