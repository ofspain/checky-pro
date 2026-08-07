# auth · T20 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 Maven module, package-by-feature under
`com.themistra.auth`: `account`, `authn`, `authz`, `apikey`, `audit`, `common`, `events`, `mfa`,
`token`, `admin`. Module boundaries (services expose services, never entities) are CI-enforced by
`ArchitectureTest` (ArchUnit).

- **Persistence:** PostgreSQL, one schema (`auth`), Flyway DDL-only migrations `V1`–`V5`
  (`V1`–`V4` immutable). JPA for simple find/save. Internal `bigint identity` PKs never leave the
  service; account UUID is the external identifier and the JWT `sub`.
- **Security / SAS:** Spring Authorization Server (OIDC), configured in
  `token.SecurityChainsConfig` as two ordered filter chains:
  1. `authorizationServerChain` (`@Order(1)`) — `/oauth2/*`, `/.well-known/*`, `/userinfo`, OIDC
     enabled, unauthenticated browser requests redirected to `/login`. **This is the chain T20
     customizes** (its doc comment already says: "the MFA step (D-014) plugs into this chain's
     authentication in a later stage").
  2. `applicationChain` (`@Order(2)`) — resource-server chain for this service's own management
     APIs, `PublicEndpoints`-driven allowlist, `.formLogin(...)` with `LoginSuccessHandler` /
     `LoginFailureHandler`.
  `AccountUserDetailsService` bridges accounts into `.formLogin`: principal name = account UUID.
  `AuthorizationServiceConfig` wires a `JdbcOAuth2AuthorizationService` decorated by
  `ReuseDetectingAuthorizationService` (persistent, multi-replica-safe authorization store).
- **Token claims:** `token.TokenClaimsCustomizer` (an `OAuth2TokenCustomizer<JwtEncodingContext>`)
  currently sets, unconditionally for every interactive access token: `roles`, `amr: ["pwd"]`,
  `acr: "urn:themistra:acr:pwd"`, `email_verified`. Its class Javadoc already states the design
  intent: `// MFA stage appends "otp" when MFA passed` — i.e. it anticipates T20/T21 making this
  conditional, but as of today it does not vary by MFA outcome.
- **Events/outbox:** Kafka via an in-service outbox (`events` package), extraction to
  `libs/java/outbox` deferred (D-018). `events` is domain-agnostic by ArchUnit rule — no dependency
  this task needs to worry about.
- **MFA module (`mfa`, built in T16–T18):** `TotpGenerator`, `TotpVerifier` (RFC 6238, HMAC-SHA1,
  6 digits, 30s step ± 1 step), `MfaSeedEncryption` (KMS-enveloped AES-GCM, D-025's narrow,
  ArchUnit-enforced AWS SDK exception), `MfaEnrollment` / `RecoveryCode` entities + package-private
  repositories, and `MfaService` (begin-enroll, confirm, disable, `verifyRecoveryCode`). No MFA
  REST controller exists yet (see §5).
- **Audit:** `auth_audit` append-only, mirrored to Kafka, via `AuditService.record(...)`.

## 2. Existing code this task touches

**Already exists and is directly reusable:**
- `mfa.MfaService.verifyRecoveryCode(UUID accountUuid, String rawCode)` — its Javadoc explicitly
  says *"for task 20's login-time use"*. Throws `InvalidRecoveryCodeException` on failure, records
  `mfa.failed` audit itself.
- `mfa.TotpVerifier.verify(byte[] secret, String submittedCode, Instant now)` — stateless,
  reusable, but the secret itself is only ever decrypted inside `MfaService` today (via the
  package-private `MfaEnrollmentRepository` and `MfaSeedEncryption`); nothing outside `mfa` can
  currently decrypt a stored TOTP secret.
- `mfa.MfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull` — package-private,
  described in its own Javadoc as "for mandatory-MFA enforcement (R24, task 18)" — but it is
  package-private, so `authn` cannot call it directly; only `MfaService` (same package) can.
- `authn.AccountUserDetailsService` — already returns the account UUID as principal name;
  `authn.LoginSuccessHandler` / `LoginFailureHandler` already establish the pattern of hooking the
  form-login chain without touching `.formLogin(Customizer.withDefaults())`'s redirect behavior.
- `authz.RoleService.resolveEffectiveRoles(UUID accountUuid)` — the only role source; used today
  by `TokenClaimsCustomizer`. This is how T20 determines MERCHANT/ADMIN membership (L10).
- `mfa_enrollments` / `recovery_codes` tables — already created in `V1__auth_baseline_schema.sql`
  (not new; T17 mapped them, no new migration expected for T20).
- `token.SecurityChainsConfig` — the file whose `authorizationServerChain` bean T20 must customize.
- `token.TokenClaimsCustomizer` — the file that must eventually read the MFA outcome to emit
  `amr`/`acr` correctly per R26/R27, though full claim wiring is task 21 (see §5 on scope overlap).

**Does not exist yet (new for T20 or a later task):**
- No `MfaController` (task 19 — self-service `POST /accounts/me/mfa/totp` etc.) exists in
  `src/main/java`. T20 does not need it: the SAS login-time MFA step is a different code path
  (interactive auth chain, not a self-service REST endpoint), and `MfaService`'s methods it needs
  (`verifyRecoveryCode`, and a TOTP-verify/enrollment-check capability — see §5) are already public
  service methods independent of the controller.
- No public `MfaService` method to (a) check whether an account has a confirmed enrollment, or
  (b) verify a submitted TOTP code at login time without the side effects of `confirm`/`disable`.
  Both are needed by T20 and do not exist today — likely new methods on `MfaService`, not new
  classes (see §5).
- No authentication step / filter / `AuthenticationProvider` implementing an MFA challenge inside
  `authorizationServerChain` — this is the core of what T20 builds.
- `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/email-requested.v1.schema.json`,
  `contracts/events/auth/security-audit.v1.schema.json` — **none of these files exist in the repo**
  (verified: no `contracts/` directory content under those paths). They are scheduled as tasks 33
  and 34, both after T20. See §5.

## 3. Established patterns to follow

- **Persistence:** JPA entities + package-private `JpaRepository` (ArchUnit-enforced:
  `repositories_are_never_public`). Conditional/atomic updates via `@Modifying @Query` returning
  affected-row counts (`confirmIfUnconfirmed`, `deleteByIdIfUnconfirmed`, `markUsed`) rather than
  read-then-write, specifically to close concurrent-race windows — `MfaService`'s Javadocs name the
  exact races these guard against (T18 Phase 9 findings). Follow the same shape for any new
  conditional persistence T20 introduces.
- **Module boundaries:** a module's entities/repositories are never imported outside the module;
  cross-module calls go through the owning module's `@Service` (e.g. `MfaService`,
  `AccountService`, `RoleService`), addressed by account UUID, never the internal `bigint` id
  (D-017). `authn` must not import `mfa`'s entities or repository — only `MfaService`.
  `only_the_account_module_may_touch_the_Account_entity` and the general never-import-siblings'
  repository rule both apply and are CI-enforced.
- **SAS chain customization:** extend/hook, don't replace, Spring Security's default form-login
  building blocks (`SavedRequestAwareAuthenticationSuccessHandler`,
  `SimpleUrlAuthenticationFailureHandler`) the way `LoginSuccessHandler`/`LoginFailureHandler`
  already do, so default redirect/CSRF/session behavior is preserved. D-014 says the MFA step
  belongs *inside* the SAS interactive authentication chain (no authorization code until MFA
  passes) — not as a post-auth step-up check.
- **Enumeration/uniform-response safety:** every failure path in `authn` (bad credentials, locked,
  suspended, deleted, unknown account) currently produces an identical response
  (`LoginFailureHandler`'s Javadoc explains this in detail). Any new MFA-challenge failure path
  T20 introduces must preserve this — a wrong/missing TOTP code must not be distinguishable from
  other failure modes by response shape/timing beyond what `TotpVerifier`'s existing
  constant-time comparison already provides.
- **Audit:** every security-relevant action calls `AuditService.record(...)` with a
  `RecordAuditEventRequest`; `mfa.failed` and `login.failed` event-type strings already exist as
  precedent for what T20's own event names should look like.
- **Error handling:** RFC 9457 `application/problem+json` via `ApiExceptionHandler`/
  `AuthzExceptionHandler`-style handlers; no stack traces or internal detail. `ProblemTypes` holds
  the registered problem types.
- **Configuration:** flat `application.properties` bound to validated
  `@ConfigurationProperties` records (e.g. `MfaProperties`, `LockoutProperties`) — startup fails on
  invalid/missing values outside `local`.
- **Clock:** `java.time.Clock` is injected everywhere business logic needs "now" (never
  `Instant.now()` directly) — `LoginSuccessHandler`, `LoginFailureHandler`, `MfaService` all take a
  `Clock` constructor argument.

## 4. Testing conventions

- **Unit tests:** plain JUnit, no Spring context, fixed `Clock.fixed(NOW, ZoneOffset.UTC)`
  (`MfaServiceTest` explicitly mirrors `LockoutServiceTest`'s pattern per its own Javadoc).
  Dependencies are hand-constructed/mocked, not `@MockBean`.
- **Architecture tests:** `ArchitectureTest` (ArchUnit), one `@ArchTest` field per durable
  module-boundary decision — new T20 code must not trip `only_the_account_module_may_touch_...`,
  `repositories_are_never_public`, or `only_MfaSeedEncryption_may_use_the_aws_sdk`. If T20's step
  needs a new architectural invariant (e.g. "only `authn`/`mfa` may call
  `MfaService.verifyRecoveryCode`"), that would be a new `@ArchTest`, not a comment.
- **Integration tests:** Testcontainers, shared `TestcontainersConfiguration`
  (`@ServiceConnection` Postgres 16-alpine + Kafka via `apache/kafka:3.8.0` — real Postgres/Kafka,
  never a shared live DB).
- **Named tests for this task (`package.md` §8):**
  `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization`,
  `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled`,
  `shouldIssueTokenWithOtpAmrAndAcrAfterMfa`. `agents.md`/target-design precedent (D-024 discussion)
  notes a full MockMvc-driven `/oauth2/authorize` → `/login` → `/oauth2/token` flow test was
  explicitly *declined* earlier in the project as unverifiable without running the app — T20's test
  design will need to reconcile that precedent with these three named tests, which sound like they
  exercise exactly that flow (Phase 1/5 concern, not resolved here).

## 5. Known gaps / unknowns

- **Contracts referenced by this task's brief do not exist in the repository.**
  `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/email-requested.v1.schema.json`, and
  `contracts/events/auth/security-audit.v1.schema.json` are all listed as "Contracts" for T20, but
  none are present under any `contracts/` path in the repo. `tasks.md` items 33–34 (contract
  authoring) are sequenced *after* T20 (item 20). I do not know whether T20 is expected to proceed
  without them (nothing to conform to yet) or whether this ordering is itself the open question —
  flagging per Phase 0 instructions rather than assuming either way.
- **`package.md` §8's requirement-ID mapping looks stale/drifted** for the tests this task cares
  about. It maps `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` → R21,
  `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` → R22,
  `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` → R23, and separately maps
  `shouldPreventCrossModuleEntityImports` → L10. But `requirements.md`'s *current* R21 is the
  lockout-indistinguishability rule, current R22/R23 are the enroll/confirm requirements (tasks
  18/19), and current R24–R27 (this task's actually-scoped IDs, matching the T20 prompt header) are
  the ones that state the MFA-step-at-login and `amr`/`acr` rules. `design.md`'s current L10 is the
  "MFA enforcement role rule" (MERCHANT/ADMIN mandatory), not the cross-module-import rule. The
  scoped IDs given in this task's own header (R24–R27, L10) are internally consistent with
  `requirements.md`/`design.md` as they stand today; `package.md` §8's numbers next to these test
  names are not. I'm treating `requirements.md`/`design.md` as authoritative and flagging the
  `package.md` drift rather than resolving it.
- **No public `MfaService` method exists yet for a side-effect-free "is this account's MFA
  confirmed" check or a login-time TOTP verify.** `verifyRecoveryCode` is ready to reuse as-is; the
  TOTP-code equivalent and the enrollment-status query are not present as public methods today —
  Phase 1/5 will need to design their shape (new `MfaService` methods vs. something else), not
  invent them in this phase.
- **How the MFA outcome reaches `TokenClaimsCustomizer`** (so `amr`/`acr` reflect `pwd` vs.
  `pwd+otp`, R26/R27) is not yet wired anywhere in the code — only foreshadowed by a comment.
  `tasks.md` assigns "Token claim updates" to a separate task 21, but R26/R27 are listed as scoped
  requirements *for T20*. I do not know whether T20 is expected to fully satisfy R26/R27 itself or
  only lay the groundwork (e.g. record the AMR fact on the `Authentication`/session) for task 21 to
  consume — this is a scope question for Phase 1, not something to resolve here.
- **Q1 (TOTP seed encryption) is resolved** (D-025, KMS envelope AES-GCM, confined to
  `mfa.MfaSeedEncryption`) — confirmed not a blocker for T20.
- **T19 (MFA controller, `tasks.md` item 19) has not been implemented** — its artifacts directory
  is empty and no `MfaController` class exists. I do not know whether this is an intentional
  reordering (T20 does not appear to have a hard code dependency on the self-service controller) or
  an oversight; noting it since the task numbering otherwise tracks `tasks.md` sequentially.
