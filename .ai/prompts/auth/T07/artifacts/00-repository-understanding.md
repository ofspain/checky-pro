# auth · T07 — Phase 0: Repository Understanding

## 1. Architecture summary

Same service as T03/T05/T06: `services/auth`, Spring Boot 3.5.4 / Java 21, package-by-feature.
Relevant subset for T07:

- **`account` module** — `AccountController`/`AccountService` (extended by T06 for email
  verification; this task extends them again for password reset), `PasswordPolicy` (T03, not yet
  wired anywhere), `VerificationTokenService` (T05, purpose-generic — `PASSWORD_RESET` is its
  *other* purpose, unused until now).
- **`token` module** — owns `RefreshTokenFamily`/`RefreshTokenFamilyRepository`/
  `RefreshTokenTracker` (refresh-token session tracking, D-003) and the currently-broken
  `SecurityChainsConfig`/`ReuseDetectingAuthorizationService` (pre-existing, unrelated compile
  failure tracked since T03 — see memory `auth-service-token-package-broken`). This task is the
  **first** to need something from `token` outside that module.
- **`events`/`common`** — `OutboxPublisher`, `EventTopics` (already has the
  `"verification-token" -> "auth.email.requested"` mapping from T06 — reused, not modified),
  `PublicEndpoints`, `ProblemTypes` (already has `INVALID_TOKEN` from T06 — likely reusable here
  too, see §5).

## 2. Existing code this task touches

**Task statement, verbatim:** *"Add `POST /accounts/password-reset-request` and `POST
/accounts/password-reset`. Ensure uniform responses. On valid reset, update password and revoke
all refresh-token families for the account."*

**Already built, to be reused for the first time in a new way:**
- `VerificationTokenService.issue(accountUuid, Purpose.PASSWORD_RESET)` /
  `.consume(rawToken)` (T05) — the `PASSWORD_RESET` purpose has existed since T05 but has had no
  caller until now.
- `Account.changePasswordHash(String)` (existing, pre-T03) — guarded only against `DELETED`
  accounts; already exactly what R14's "update the password hash" needs.
- `AccountRepository.findByEmail(String)`, `.findByAccountUuid(UUID)` (existing).
- `common.SecurityBeansConfig.passwordEncoder()` (existing bean, already injected into
  `AccountService`).
- `EventTopics`'s `"verification-token" -> "auth.email.requested"` mapping (T06) — password-reset
  events reuse the *same* topic/aggregate type as email-verification, differentiated by the
  existing `EmailRequestedEventPayload.purpose` field (already a plain `String`, already used with
  `"verify_email"`; this task supplies `"password_reset"`, per R13's literal wording and T06's own
  design intent — the payload type was deliberately not purpose-specific for exactly this reuse).
- `common.ProblemTypes.INVALID_TOKEN` (T06) — R15's "uniform failure response indistinguishable
  from a valid token" sounds identical in shape to R5's `verify-email` rejection; likely directly
  reusable rather than needing a new problem type (see §5).

**New capability needed — does not exist yet:** *"revoke all refresh-token families for the
account."* `token.RefreshTokenTracker` currently has `trackIssuance`, `trackRotation`, and
`checkAndRegisterPresentation` — no revoke-all method. However,
`RefreshTokenFamilyRepository.findByPrincipalNameAndRevokedAtIsNull(String principalName)`
**already exists** and is currently unused by anything (`grep` confirms only
`RefreshTokenFamilyRepository`/`RefreshTokenFamily`/`RefreshTokenTracker` reference
`RefreshTokenFamily` at all) — this repository method appears to have been scaffolded in
anticipation of exactly this need. `RefreshTokenFamily.revoke(String reason, Instant now)` is
public and already used elsewhere (reuse-detection). Per `ReuseDetectingAuthorizationService.java`'s
own comment: *"principalName is the account UUID for interactive grants
(AccountUserDetailsService)"* — confirming `principalName` is the account's UUID string, exactly
what's needed to look up a given account's families.

**Cross-module dependency this task would introduce:** `account` calling into `token` has no
precedent in this codebase. No existing `ArchitectureTest` rule forbids `account → token`
specifically (the existing rules cover `account` entity isolation, `authz`/`audit` independence
*from* `account`, `events` domain-agnosticism, repository visibility, and `PublicEndpoints`
consumption — none constrain this direction). `RefreshTokenTracker` is `public`; its own javadoc
currently says *"the SAS-facing decorator (`ReuseDetectingAuthorizationService`) is the only
caller"* — a statement of current fact, not an enforced rule, that this task would make untrue.

**Compile-safety check:** `RefreshTokenTracker.java`, `RefreshTokenFamilyRepository.java`, and
`RefreshTokenFamily.java` import nothing from the two broken files (`OAuth2TokenType`,
`JwtAuthenticationConverter` — confirmed via each file's import list). Extending
`RefreshTokenTracker` with a new method should compile independently of the pre-existing,
unrelated `token` package break, the same way T05/T06's isolated `javac` verification worked
around it.

**New, per `design.md` §6's package map:**
- `account/dto/PasswordResetRequest.java`
- `account/dto/PasswordResetConfirmRequest.java`

## 3. Established patterns to follow

- **Purpose-generic verification tokens** (T05): `VerificationTokenService` already supports
  `PASSWORD_RESET`; T05's Phase 4 explicitly built it this way so task 7 wouldn't need rework.
- **Public, email/token-identified, enumeration-safe endpoints** (T06's exact shape):
  `password-reset-request` mirrors `resend-verification` (email in, uniform ack out, act only on
  match); `password-reset` mirrors `verify-email` (token in, distinct success/failure, uniform
  *among* failure reasons).
- **`EmailRequestedEventPayload` reuse, not a new payload type**: same event, same topic, `purpose`
  field is the discriminator — already designed this way in T06.
- **`AccountExceptionHandler`'s single-mapping-per-uniform-outcome pattern** (T06): if R15's
  rejection is byte-identical to R5's, reuse `VerificationTokenRejectedException`/`INVALID_TOKEN`
  rather than inventing a parallel type — the whole point of that exception was to be the uniform
  rejection for verification-token redemption, not verify-email-specific (its own javadoc already
  says "every reason a verification token redemption can fail," not scoped to one purpose).
- **Outbox-in-transaction, `Clock`-sourced timestamps, `actorUuid` conventions** — unchanged from
  every prior task in this chain.

## 4. Testing conventions

Unchanged: plain JUnit 5 + Mockito, no Spring context, fixed `Clock`. No `MockMvc` precedent exists
in this module (confirmed during T06); T06 Phase 11 Gap 3 (an un-built, deferred integration-test
gap for the analogous `verify-email` HTTP path) applies equally here and is not re-litigated by
this task.

## 5. Known gaps / unknowns

**(a) Does T07 itself call `PasswordPolicy`, or is that task 9's job?** R14 requires "a
policy-compliant new password," which sounds like this task's own acceptance criterion. But task
9's own task statement (`tasks.md`, verified directly) is: *"Apply `PasswordPolicy` to
registration, change-password, and password-reset."* — explicitly listing password-reset as
something task 9 wires up. This is the same shape as T06's registration-password-policy question,
which T06 resolved by *not* touching `PasswordPolicy` at all and leaving it entirely to task 9.
I do not know whether T07 should follow the same precedent (build the reset flow without a
`PasswordPolicy.validate(...)` call, deferring to task 9) or whether R14's explicit
"policy-compliant" wording means this task must call it. **Resolved (human-confirmed):** T07 follows T06's precedent exactly — the reset flow (token
consume, password hash update via `Account.changePasswordHash`, refresh-family revocation, audit)
is built without any `PasswordPolicy.validate(...)` call. Task 9 wires policy enforcement into
registration, change-password, and password-reset together, consistent with its own stated scope.
R14's "policy-compliant new password" wording is the spec's description of the *end state* once
all tasks land, not a literal requirement on this task alone — same reasoning T06 applied to R3's
"emit... event" not requiring registration to also enforce password policy.

**(b) Where should "revoke all families for account" live?** Two structurally different options:
(i) add a new method to `token.RefreshTokenTracker` (e.g. `revokeAllForPrincipal(String, String)`)
that `account.AccountService` calls directly — the minimal, most direct change, but establishes a
new `account → token` dependency with no precedent; or (ii) some indirection (an interface in a
neutral package, an application event) to avoid the direct dependency. Given no ArchUnit rule
currently forbids (i), and the repository method it would use appears to have been scaffolded
specifically for this, I lean toward (i) as the straightforward reading — but this is a Phase 2/3
design decision, not decided here.

**(c) I do not know the exact requirements/design-doc text for how the reset endpoint's new
password reaches the endpoint** — i.e., whether `PasswordResetConfirmRequest` carries the raw
token and new password together (single endpoint call) or separately. R14's wording ("a caller
submits a valid, unused password-reset token and a policy-compliant new password to `POST
/accounts/password-reset`") strongly implies a single request with both fields — noted for Phase 1
confirmation, not assumed as settled here.

**(d) `password.reset` audit event's `actorUuid`** — same question T06 Finding 5 raised and
resolved for self-service email activation (actor = the account's own UUID). Likely the same
answer applies here, but not assumed without Phase 1/2 confirmation.
