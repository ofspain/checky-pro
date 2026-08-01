# auth · T13 — Phase 0: Repository Understanding

Grounding only — no design, no requirements extraction. Read: `spec/auth-service/{package.md,
requirements.md, design.md, tasks.md, agents.md}` plus the actual repository state.

## Headline finding: the "pre-existing, unrelated `token` package break" is two one-line import bugs, and T13 must touch that exact file

Every task since T09 has cited "the pre-existing, unrelated `token` package compile break, tracked
since T03" as a reason `mvn -pl services/auth compile`/`test` can't run, and has correctly treated
it as out of scope — none of T09-T12 needed to touch anything in `token`. **T13 is different: its
own task statement requires modifying the SAS authentication flow, which lives in
`SecurityChainsConfig.java` — one of the two broken files.**

Root-caused this phase (previously only described as "OAuth2TokenType/JwtAuthenticationConverter
symbol not found," never actually diagnosed):
- `SecurityChainsConfig.java:12` imports `org.springframework.security.oauth2.jwt
  .JwtAuthenticationConverter`. The resolved dependency (`spring-security-oauth2-resource-server
  6.5.2`) puts that class at `org.springframework.security.oauth2.server.resource.authentication
  .JwtAuthenticationConverter` instead — confirmed by inspecting the jar directly.
- `ReuseDetectingAuthorizationService.java:10` imports `org.springframework.security.oauth2.core
  .OAuth2TokenType`. The resolved dependency (`spring-security-oauth2-authorization-server 1.5.1`)
  puts that class at `org.springframework.security.oauth2.server.authorization.OAuth2TokenType`
  instead — same verification method.

Both are wrong-package import statements, not missing dependencies, not a version incompatibility,
not an architectural gap. Verified by copying both files to a scratch directory, correcting only
the two import lines, and compiling against the module's real resolved classpath: **zero errors**.
This is a mechanical, two-line fix.

**This does not mean T13 should fix it as a drive-by.** It means the "obey `agents.md`, never
touch pre-existing unrelated things, log anything that looks wrong under Open Questions" guardrail
now has a real decision to make: T13 cannot exercise its own change to `SecurityChainsConfig.java`
through a real `mvn compile`/`test` run without either (a) this import being fixed, or (b) finding
some other isolated-compilation path the way T09-T12 did (which won't work as cleanly here, since
T13's own new code *is* a change to the file that's currently broken, not just a sibling file that
avoids importing it). Flagged for an explicit Phase 1/4 scope decision, not resolved here.

## 1. Architecture summary

Same as documented at T11/T12 Phase 0 (Spring Boot 3.5.4/Java 21, package-by-feature, Flyway
DDL-only migrations, outbox-mediated cross-service events, ArchUnit-enforced module boundaries).
New for this task: the **SAS (Spring Authorization Server) interactive login flow**. Two security
filter chains are configured in `SecurityChainsConfig.java`:
1. `authorizationServerChain` (Order 1) — matches only SAS protocol endpoints
   (`/oauth2/**`, `/.well-known/**`, `/userinfo`), unauthenticated requests redirect to `/login`.
2. `applicationChain` (Order 2) — catch-all for everything chain 1 doesn't match, includes
   `.formLogin(Customizer.withDefaults())`. **This is where `/login`'s actual GET/POST handling
   lives** — chain 1's `securityMatcher` doesn't cover `/login` itself, so form-login processing
   (and therefore success/failure outcomes) happens in chain 2, not chain 1.
- Login authentication itself: Spring Security's default `DaoAuthenticationProvider`, backed by
  `AccountUserDetailsService` (`authn` package, pre-existing) as the `UserDetailsService`.
  `AccountUserDetailsService.loadUserByUsername(email)` calls `AccountService.findLoginView(email)`
  and returns a `UserDetails` whose **username is the account UUID string**, not the email — the
  UUID becomes the authenticated principal's name only on success. `accountLocked` is already set
  from `AccountStatus.LOCKED`, so Spring's own account-locked check already participates in
  rejecting locked accounts, independent of anything T13 adds (worth reconciling with L4's
  `LockoutService`-driven lock/unlock in Phase 1/2 — two different mechanisms currently express
  "locked," not yet unified).

## 2. Existing code this task touches

**Already exists, unmodified by this task's predecessors (T11/T12):**
- `LockoutService.recordFailedAttempt(UUID, Instant)` / `.recordSuccessfulAttempt(UUID, Instant)`
  (T12, `authn` package) — the two methods this task's own name ("Integrate lockout counter
  increment into the SAS authentication failure path and reset-on-success into the success path")
  is describing wiring up. Neither has any caller yet.
- `AuditService.record(RecordAuditEventRequest)` (`audit` package, pre-existing, used by every
  prior task) — **already does exactly what R43 requires**: appends an `auth_audit` row (INSERT-
  only) and mirrors a reduced event to Kafka via the outbox, topic resolved through
  `EventTopics.forAggregateType("audit")` → `"auth.security.audit"` (`EventTopics.java:14`,
  confirmed matches R43's named topic exactly). T13's "record `login.failed` audit events" clause
  may need nothing more than calling this existing method with `eventType = "login.failed"` — no
  new audit infrastructure appears necessary. `design.md`'s package map proposes a new
  `LoginAttemptAuditService.java` (`authn/`, "login success/failure auditing") — whether that's a
  genuinely separate class or just inline `AuditService` calls from wherever the SAS hooks land is
  a Phase 1/2 question, not decided here; flagging that `AuditService` already covers the
  persistence+outbox mechanics either way, so any new class here would be a thin wrapper/router at
  most, not new plumbing.
- `AccountUserDetailsService` (`authn` package) — `loadUserByUsername`, described above.
- `SecurityChainsConfig.java` (`token` package, **currently broken**, see Headline finding) —
  `applicationChain`'s `.formLogin(Customizer.withDefaults())` is the literal integration point
  this task's "SAS authentication failure/success path" refers to. No custom
  `AuthenticationSuccessHandler`/`AuthenticationFailureHandler` exists yet; the default Spring
  handlers just redirect.
- `Account.canAuthenticate()` (`Account.java`) — returns `status == ACTIVE`. Not yet referenced by
  anything in the authentication path found this phase; possibly relevant to how T13 decides
  whether a login attempt is even eligible for lockout tracking (mirrors T12's own "ACTIVE, or
  LOCKED-but-expired" precondition language).

**New, expected by `design.md` §6's package map (`authn/`):**
- `LoginAttemptAuditService.java` — "login success/failure auditing." See note above: may end up
  thin given `AuditService` already exists, or may not be needed as a distinct class at all.
- `TotpAuthenticationProvider.java`, `TotpStepUpAuthenticationToken.java` — **not this task**.
  `design.md` lists these under the same `authn/` heading but they belong to the MFA step
  (`tasks.md` items 16-22, D-014), explicitly out of T13's scope (R43 mentions "MFA events" but
  T13's own scoped requirement IDs are R16/R18/R43, and the task statement only names login
  failure/success, not MFA).

## 3. Established patterns to follow

- **Spring Security handler pattern:** standard `AuthenticationSuccessHandler`/
  `AuthenticationFailureHandler` (or their `*RequestHandler` successors) are how Spring Security
  lets code observe login outcomes without touching the authentication provider itself. No
  precedent for either interface exists anywhere in this codebase yet (confirmed by search) — this
  would be new territory, same as T12's `LockoutService`→`AccountService` dependency direction was
  initially (mis)believed to be novel. Given that mistake, this phase does **not** claim there's no
  precedent elsewhere in the Spring ecosystem/this team's other services — only that this specific
  repository has none to copy from.
- **Outbox/audit:** `AuditService.record(...)` is the sole sanctioned path (T11/T12's own
  established convention, restated at every prior Phase 0) — confirmed still true, and directly
  relevant here since it already implements R43's exact requirement.
- **Module boundaries (L12):** whatever new class(es) this task adds should follow the same
  pattern `LockoutService` established: live in `authn`, never import `Account`, reach it only via
  `AccountService`'s public methods if needed. `AccountUserDetailsService` (pre-existing) is the
  actual precedent for this now (T12 Phase 12 corrected the earlier "no precedent" claim) —
  confirmed still accurate.
- **Configuration:** flat `application.properties`, validated `@ConfigurationProperties`. No new
  config keys obviously implied by this task's scope (unlike T12's `LockoutProperties`) — the
  lockout constants already exist from T12.

## 4. Testing conventions

- **Unit tests:** plain JUnit 5 + AssertJ + Mockito, no Spring context — established convention,
  confirmed unchanged.
- **Integration tests:** `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, real
  Postgres + Kafka. **New consideration for this task specifically**: testing an actual Spring
  Security form-login flow (submitting credentials via HTTP, observing the resulting
  redirect/session/lockout state) typically needs `MockMvc` or a real HTTP client against the
  running security filter chain — this module has **zero precedent for `MockMvc`/`@WebMvcTest`
  anywhere** (confirmed by the same search T09/T10/T11 already ran and found empty). Whether T13's
  own tests can avoid needing that (e.g., by testing the failure/success handler class directly,
  unit-style, rather than the full filter chain) or whether this task is the one that finally
  needs to introduce `MockMvc` to this codebase is a real, consequential Phase 1/2/5 design
  question — not decided here.
- **ArchUnit:** `ArchitectureTest.java`, unchanged, will check whatever new classes this task adds.

## 5. Known gaps / unknowns

- **I do not know** whether fixing the two-line import bug in `SecurityChainsConfig.java`/
  `ReuseDetectingAuthorizationService.java` is in scope for this task. It is mechanically necessary
  for this task's own changes to that file to be verifiable via a real build, but it's also
  exactly the kind of "unrelated" pre-existing issue every prior task was told to leave alone. This
  is the single most consequential open question this phase surfaced — explicitly deferred to
  Phase 1/4, not decided here.
- **I do not know** how a failed login attempt's `AuthenticationException` (or the
  `AuthenticationFailureHandler`'s visible request state) exposes enough information to resolve an
  account UUID. `AccountUserDetailsService.loadUserByUsername` is called with whatever the user
  typed as the login form's username field (presumably email, matching `AccountService
  .findLoginView(String email)`'s parameter), and only sets the UUID as the *authenticated*
  principal's name — on a bad-password failure, the authentication never completes, so the
  UUID-bearing `UserDetails` object may not be reachable from the failure handler at all without an
  explicit re-lookup by email. Whether that re-lookup is necessary, and how it interacts with
  enumeration-safety (L5 — must not reveal whether an email exists) when the email doesn't
  correspond to any account, is unresolved here.
- **I do not know** whether a successful login should also be audited (R43's text is broad: "any
  security-relevant action... login success/failure"), even though the task statement's audit
  clause only explicitly names "`login.failed` audit events." Possibly deliberate (success is
  already implicitly evidenced by token issuance / other events), possibly an extraction question
  for Phase 1.
- **I do not know** how Spring's own `accountLocked` flag (already set by
  `AccountUserDetailsService` from `AccountStatus.LOCKED`) is meant to interact with
  `LockoutService`'s counter/decision logic during the actual authentication attempt — e.g.,
  whether Spring's built-in locked-account rejection (which happens *before* password checking, a
  different code path than a bad-password failure) should itself be treated as a "failed attempt"
  for counting purposes, or whether it's already sufficient on its own and only bad-password
  failures need `LockoutService.recordFailedAttempt`.
- **Confirmed, not a gap:** `contracts/events/auth/security-audit.v1.schema.json` (listed in this
  task's header) still does not exist in the repo — same already-logged gap as T11/T12. `R43`'s
  actual event shape is only informally described (`requirements.md:70`); `AuditMirrorPayload`
  (existing, used by every `AuditService.record` call) is the closest thing to a schema-in-code.
