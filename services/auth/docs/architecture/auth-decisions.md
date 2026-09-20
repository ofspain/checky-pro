# Auth Service — Decision Log

Maintained continuously per the provisioning prompt. Format: Decision · Context · Alternatives ·
Selected Approach · Trade-offs · Impact · Reference-Project Influence · Accept/Modify/Reject Reason.

Reference project: `netra-identity-service` ("authrex"). Analysis: `reference-analysis.md`; full
mapping: `gap-analysis.md`.

---

## D-001 · Use Spring Authorization Server, not hand-rolled JWT issuance

- **Context:** Three sibling resource-server services and a React SPA must validate our tokens; ARCHITECTURE §3.2 specifies an OIDC/OAuth2 issuer.
- **Alternatives:** (a) inherit authrex's jjwt-based custom issuance; (b) external IdP (Cognito/Keycloak/Auth0); (c) Spring Authorization Server.
- **Selected:** (c) SAS.
- **Trade-offs:** More framework surface to learn than (a); more code to own than (b). But (a) reimplements audited standards badly (no JWKS/kid/discovery/revocation), and (b) surrenders the merchant/API-key/MFA customization depth we need and adds vendor coupling at the identity core of a trust product.
- **Impact:** Standard endpoints (`/oauth2/token`, `/oauth2/jwks`, `/.well-known/openid-configuration`, `/oauth2/revoke`); sibling services use stock `spring-security-oauth2-resource-server`.
- **Reference influence:** Demonstrated the cost of custom issuance (raw-PEM key endpoint, per-client special-casing).
- **Verdict on reference:** **Rejected.**

## D-002 · Authorization Code + PKCE for the SPA; no password grant

- **Context:** Reference exposes a password grant; OAuth 2.1 removes it. Our client is a public SPA.
- **Alternatives:** (a) password grant; (b) auth code + PKCE.
- **Selected:** (b).
- **Trade-offs:** Slightly more complex frontend flow (redirect-based); modern, phishing-resistant, standard.
- **Impact:** Frontend uses standard OIDC client; no credentials pass through app JS beyond the IdP-hosted or first-party login page.
- **Reference influence / verdict:** **Rejected** (deprecated flow).

## D-003 · Refresh tokens: hashed at rest, token families with reuse detection, multi-session

- **Context:** Reference stores plaintext UUIDs, forces single session, no replay defense.
- **Alternatives:** (a) plaintext + revoke-all (reference); (b) hash-at-rest + rotation; (c) (b) + family-based reuse detection.
- **Selected:** (c) — OAuth 2.1 recommended practice.
- **Trade-offs:** More state (family id, chain), one extra index; replay of a rotated token kills the whole family — strictly better security with negligible cost.
- **Impact:** Schema: `token_hash`, `family_id`, `device_label`; per-device session listing/revocation becomes possible (user-facing feature later).
- **Reference influence:** Rotation-on-refresh and scheduled cleanup ideas **kept**; storage and session policy **rejected**.

## D-004 · Own domain model; no shared entity artifact

- **Context:** Reference imports `Identity` etc. from `commons-netra`, shared across services.
- **Alternatives:** (a) shared model library; (b) service-owned entities, sharing only via `contracts/`.
- **Selected:** (b) — repo dependency rule.
- **Trade-offs:** Some model duplication across services; that duplication is the decoupling.
- **Impact:** Auth defines its own JPA entities; events/APIs generated from `contracts/`.
- **Reference influence / verdict:** **Rejected** (distributed-monolith pattern).

## D-005 · JPA + Flyway DDL; no stored-procedure business logic

- **Context:** Reference keeps upserts/searches/password history in Postgres sprocs.
- **Alternatives:** (a) sprocs; (b) JPA repositories + Flyway DDL-only migrations.
- **Selected:** (b) — repo standard, matches payment service.
- **Trade-offs:** Sprocs can be faster for hot paths; auth's data volume doesn't justify splitting logic across languages and losing testability/reviewability.
- **Impact:** Migrations contain schema only; logic is unit-testable Java.
- **Reference influence / verdict:** **Rejected.**

## D-006 · Password policy per NIST 800-63B; drop history + forced rotation

- **Context:** Reference has history checks (sproc) plus two bugs: inverted rotation logic and a config misread.
- **Alternatives:** (a) fix and keep history/rotation; (b) NIST 800-63B: length ≥ 12, breached-password screening, no periodic forced rotation.
- **Selected:** (b).
- **Trade-offs:** "Password history" disappears as a feature — intentionally; NIST guidance says forced rotation degrades password quality. Breached-password check (k-anonymity) gives strictly better protection.
- **Impact:** Simpler schema (no history table), HIBP-style range API integration, both reference bugs cease to exist structurally.
- **Reference influence / verdict:** **Rejected** (and its bugs become our unit-test cases).

## D-007 · Remove multi-tenancy claims (`domain_code`/`domain_type`) from Phase 1

- **Context:** Reference threads tenancy through every token and the auth filter, including a client-controlled `X-` header check. Phase 1 has one user population; institutions arrive in Phase 5.
- **Alternatives:** (a) inherit tenancy now; (b) single-tenant claims with an extensible namespace.
- **Selected:** (b).
- **Trade-offs:** If tenancy arrives, it's an additive claim + issuance change behind the same JWKS — cheap later; carrying it now taxes every consumer immediately.
- **Impact:** Leaner token spec; documented extension point.
- **Reference influence / verdict:** **Rejected for now**; concept noted for Phase 5.

## D-008 · Remove token exchange; record RFC 8693 as the future path

- **Context:** Reference flow is unreachable (required claims never issued) and would trust inbound role claims.
- **Selected:** Remove entirely; if Phase 3+ delegation needs arise, enable SAS's RFC 8693 support via a new ADR.
- **Reference influence / verdict:** **Rejected** (broken dead code).

## D-009 · Events via transactional outbox; auth emits, Notification delivers

- **Context:** Reference has no messaging. Architecture requires `user.registered`, `user.suspended`, audit mirroring, and email flows (verification/reset) delivered by the Notification service.
- **Selected:** `libs/java/outbox` + schemas in `contracts/events/`. Auth never sends email directly.
- **Trade-offs:** Event round-trip for emails adds latency (acceptable); preserves service boundaries and gives the Phase 2+ intelligence engine an auth-event stream.
- **Reference influence:** None (absent).

## D-010 · Secrets via External Secrets/IRSA; secret-scanning CI gate

- **Context:** Reference committed real credentials (properties + a pasted RDS password in a comment) and had a disabled hand-rolled Secrets Manager client that printed secrets.
- **Selected:** No AWS SDK code in the service; ESO injects from Secrets Manager; gitleaks gate added to CI repo-wide.
- **Reference influence / verdict:** **Rejected**; its failure becomes a platform control.

## D-011 · JWT signing keys: Secrets-Manager-stored keypair with dual-key JWKS rotation (resolves OD-1)

- **Context:** §6.4 custody ethos says keys shouldn't be exfiltratable; but token signing is high-frequency (every issuance), unlike receipt attestation.
- **Alternatives:** (a) keys baked into artifact (reference); (b) Secrets Manager-stored keypair injected via External Secrets, dual-key JWKS rotation; (c) KMS-backed `JWKSource` — key never leaves KMS.
- **Selected:** (b) for Phase 1.
- **Trade-offs:** (c) is stronger custody but adds per-token KMS latency + cost and a custom Nimbus/SAS signing integration — meaningful engineering for a token whose blast radius is already capped by 10-min TTL + quarterly rotation + instant JWKS un-publication. (a) is unacceptable. Receipt attestation keys remain KMS-only (different risk profile: long-lived, legally load-bearing).
- **Impact:** JWKS publishes current+previous `kid`s; rotation automated; runbook for emergency rotation.
- **Reference influence / verdict:** **Rejected** (classpath keys); its raw-PEM endpoint replaced by standard JWKS.
- **Revisit trigger:** partner/institutional token verification (Phase 5) or any signing-key incident → KMS ADR.

## D-012 · SPA: pure PKCE public client, tokens in memory, SAS session cookie for silent renewal (resolves OD-2)

- **Context:** Mobile-first PWA, no BFF (lean edge decision), sibling services validate JWTs directly.
- **Alternatives:** (a) BFF with cookie-bound tokens (requires building the gateway we deliberately deferred); (b) PKCE public client — access token in memory, rotating refresh token via the OIDC client, SAS httpOnly session cookie enables silent re-auth.
- **Selected:** (b).
- **Trade-offs:** (a) is the gold standard against XSS token theft but resurrects the gateway service. (b) with 10-min access TTL + refresh rotation + family reuse detection caps theft impact; strict CSP (already platform policy) is the XSS backstop.
- **Impact:** Frontend uses a standard OIDC library; no custom auth code in the SPA.
- **Revisit trigger:** if a BFF materializes for other reasons (gap-analysis graduation triggers), move token custody into it.

## D-013 · Rate limiting: in-process per replica + durable lockout as backstop (resolves OD-3)

- **Context:** Edge (ingress-nginx) does IP-level limits; auth needs per-account limits on login/token/reset/MFA.
- **Alternatives:** (a) shared Redis-backed buckets; (b) in-process Bucket4j per replica + DB-backed lockout state as the durable control.
- **Selected:** (b). With 2–3 replicas, per-replica limits are within ~3× of intended and the lockout table is exact where it matters (failed logins).
- **Trade-offs:** Limits are approximate across replicas; acceptable. Avoids reintroducing Redis (removed in gap analysis) for a marginal gain.
- **Revisit trigger:** replica count > 5 or observed distributed brute-force patterns.

## D-014 · MFA enforced inside the SAS interactive authentication flow (resolves OD-4)

- **Context:** MFA must gate token issuance, not be advisory.
- **Alternatives:** (a) post-auth step-up claim checked by resource servers; (b) TOTP step inside SAS's authentication chain — no authorization code until MFA passes.
- **Selected:** (b). Resource servers stay dumb (zero trust means they shouldn't re-implement MFA policy); tokens carry `amr`/`acr` as facts, not gates.
- **Trade-offs:** Requires SAS authentication-flow customization (named spike in the roadmap — highest technical risk in the service, prototyped first).
- **Impact:** Mandatory for `MERCHANT`/`ADMIN` roles; enrollment enforced at next login after role grant.

## D-015 · OAuth2 stage interims: dev JWKS + in-memory authorization store — RESOLVED (JWT stage)

- **Context:** Stage ordering (user template 2026-07-13) puts OAuth2 wiring before the JWT stage.
- **Selected:** This stage ships with Boot's autoconfigured in-memory JWKS (fresh dev key per boot) and SAS's default in-memory OAuth2AuthorizationService. Registered clients DO persist (JDBC, deterministic ids) so stored authorizations stay resolvable later.
- **Resolution:** JWKS interim replaced by `JwksConfig` + `SigningKeyMaterial` (Secrets-Manager-sourced current/previous PEM keys, dual-`kid` JWKS, `require-configured` boot guard — D-011). Authorization-store interim replaced by `AuthorizationServiceConfig` wiring `JdbcOAuth2AuthorizationService` (real, persistent) decorated by `ReuseDetectingAuthorizationService`. Both now deployable multi-replica.
- **Reference influence:** None; sequencing convenience only.

## D-016 · Refresh-token family/reuse tracking lives outside SAS's own storage columns

- **Context:** D-003 calls for refresh tokens "hashed at rest." `JdbcOAuth2AuthorizationService`'s row/parameter mapper classes are the only sanctioned customization point for altering how it serializes a stored `OAuth2Authorization`, but their exact method signatures have changed across Spring Security minor versions and are not part of the stable public contract in the way `OAuth2AuthorizationService` (the interface) is.
- **Alternatives:** (a) hash the token values inside `oauth2_authorization` itself by overriding the delegate's row/parameter mappers; (b) fully reimplement `OAuth2AuthorizationService` from scratch against JPA, bypassing the delegate entirely; (c) leave the delegate's own storage untouched and add a decorator (`ReuseDetectingAuthorizationService`) plus dedicated tracking tables (`refresh_token_family`, `refresh_token_archive`) that hash and compare independently.
- **Selected:** (c).
- **Trade-offs:** The `oauth2_authorization.refresh_token_value` column itself still holds the value in whatever form the delegate's default mapper writes it (not independently re-hashed by us) — meaning D-003's "hashed at rest" guarantee is delivered by *this service's* tracking tables (which are hash-only) and not yet by the delegate's own table. The security property that matters most — **a replayed, already-rotated refresh token revokes the entire family** — is fully implemented and independent of the delegate's storage format, since detection happens via our own hash tables. (a)/(b) were rejected as unacceptable API-version risk to guess at without the ability to verify against the exact Spring Security release; a wrong guess there risks silently breaking token issuance/refresh entirely, a worse outcome than a scoped interim.
- **Impact:** V2 migration adds `refresh_token_family` / `refresh_token_archive` and drops the now-unused `family_id`/`device_label` columns from `oauth2_authorization` (V1). Column-level hashing of the delegate's own table is deferred.
- **Revisit trigger:** Testing-stage integration tests (Testcontainers, real SAS refresh-grant flow against the pinned Spring Boot 3.5.4 / Spring Security version) should (1) confirm end-to-end rotation/reuse behavior against the real token endpoint, and (2) evaluate whether hashing the delegate's own columns is worth the version-specific mapper work once verified against a running SAS instance rather than guessed at.
- **Reference influence:** None (reference stored refresh tokens in plaintext with no family concept at all — gap-analysis §1 #5).

## D-017 · RBAC keys on account_uuid, not the internal account id

- **Context:** V1's `account_roles`/`account_role_templates` FK'd `accounts.id` (bigint). `Account`'s own invariant (its Javadoc, target-design §3) is that the internal id never leaves the account module — every other module (token, authn) already addresses accounts purely by UUID.
- **Alternatives:** (a) keep the bigint FK and add a method on `AccountService` resolving UUID→internal id for authz to call; (b) rekey the join tables on `accounts.account_uuid` (a unique column, so still FK-able) and give authz zero dependency on the account module.
- **Selected:** (b) — V3 migration drops and recreates both tables before any data exists in them (V1/V2 are otherwise untouched, migrations stay immutable).
- **Trade-offs:** One extra migration file for what could have been right in V1; accepted since V1 was written before the authz module's design was finalized, and this is cheap to fix pre-data.
- **Impact:** `RoleService` and its repositories never import anything from `com.themistra.auth.account`; `TokenClaimsCustomizer` resolves roles for the interactive principal (`sub` = account UUID) directly.
- **Reference influence:** None (reference's `identity_role`/role-template mapper tables key on the shared `commons-netra` `Identity.id` — exactly the cross-module coupling this decision avoids).

## D-018 · Outbox implemented locally in services/auth; extraction to libs/java/outbox deferred

- **Context:** The monorepo scaffold reserves `libs/java/outbox` as a shared library (root `libs/README.md`), but no second service exists yet with real code to share it with.
- **Alternatives:** (a) build `libs/java/outbox` now as a shared Maven module; (b) implement the outbox entity/repository/publisher/relay directly in `services/auth`'s `events` module, extract later.
- **Selected:** (b).
- **Trade-offs:** Payment/notification/crypto services will need to re-implement (or, better, prompt an extraction PR) the same mechanism when they're built. Accepted: building a shared library against a single consumer risks guessing at an abstraction shape that doesn't fit the second consumer either — the "rule of three" applies, and the project's own principles reject designing for hypothetical future requirements.
- **Impact:** `OutboxEvent`, `OutboxEventRepository`, `OutboxPublisher`, `OutboxRelay`, `EventTopics` all live in `com.themistra.auth.events`. If/when a second service needs identical behavior, extract these classes into `libs/java/outbox` verbatim — the design was kept domain-agnostic specifically to make that extraction mechanical.
- **Reference influence:** None (reference has no messaging at all).

## D-019 · Explicit Kafka producer bean instead of Boot's autoconfigured KafkaTemplate

- **Context:** `OutboxRelay` needs a `KafkaTemplate<String, String>`. Spring Boot autoconfigures a `KafkaTemplate` bean from `spring-kafka` on the classpath, but its exact generic type resolution is version-sensitive and not part of a contract worth depending on blindly (the same category of risk flagged in D-016 for `JdbcOAuth2AuthorizationService`'s internals).
- **Selected:** `KafkaProducerConfig` declares an explicit `ProducerFactory<String, String>` (built directly from `ProducerConfig` constants and the configured bootstrap-servers) and a `KafkaTemplate<String, String>` bean over it — using only long-stable, directly-documented `kafka-clients`/`spring-kafka` public API, avoiding any dependency on Boot autoconfiguration's inferred generics.
- **Impact:** One small, explicit config class; no ambiguity for future readers about which producer settings are in effect (`acks=all`, idempotence enabled).
- **Reference influence:** None.

## D-020 · auth_audit rekeyed on account_uuid; ip column simplified to text

- **Context:** V1's `auth_audit.account_id` had the same cross-module coupling problem D-017 fixed for RBAC (references the account module's internal bigint id). Separately, `ip INET` has no verified-safe Hibernate/JDBC mapping in this stack without an extra dependency.
- **Selected:** V4 migration drops `account_id` (auto-dropping its index) and adds `account_uuid UUID REFERENCES accounts(account_uuid)`; drops `ip INET` and re-adds `ip VARCHAR(45)`.
- **Trade-offs:** Loses Postgres's native inet operators/indexing (subnet queries, etc.) — not something this audit log needs; a text column is sufficient for "what IP made this request," which is all the current design asks of it.
- **Impact:** `audit` module has zero dependency on `account` module entities, matching D-017's precedent.
- **Reference influence:** None (reference has no audit trail at all — `updateLastLogin` only, gap-analysis §2).

## D-021 · TokenHashing moved to common.Hashing

- **Context:** The audit module needs the same SHA-256 hex-digest primitive the token module already had (`TokenHashing`, D-003) to hash user-agent strings before storage.
- **Selected:** Moved and renamed to `com.themistra.auth.common.Hashing` — a generic, domain-free utility belongs in `common` (its stated purpose), not inside the `token` module that happened to need it first.
- **Impact:** `token` and `audit` both depend on `common` (already true for both); neither depends on the other for this. All call sites and tests updated.
- **Reference influence:** None.

## D-022 · Audit scope: suspend/reinstate/delete are audited; routine registration is not

- **Context:** target-design §15 calls for auditing "every auth-relevant action," but its own example catalogue (login_failed, account_locked, token.reuse_detected, mfa_disabled, api_key_created) skews toward security incidents and admin actions, not every routine business transition.
- **Selected:** `AccountService.suspend/reinstate/delete` call `AuditService.record(...)` (these are typically admin/compliance-initiated and security-relevant); `activateEmail` (routine, self-service email verification) does not — it remains a business event only (`auth.user.lifecycle`), not also a security-audit event.
- **Trade-offs:** A future need to audit registrations for a different reason (e.g., fraud signals at signup) would require deliberately adding that call, not get it for free — an explicit line was judged better than blanket-auditing every state change into a trail meant for security review.
- **Impact:** `actorUuid` is currently always null on these calls — no authenticated-caller context exists yet at this layer. It is recorded honestly as "unknown actor" rather than fabricated; the admin API stage plumbs the real actor through when these become ADMIN-scoped endpoints.
- **Reference influence:** None (reference has no audit concept to compare against).

## D-023 · Testing-stage scope: what was deliberately not attempted

- **Context:** target-design §17 names an "endpoint-authentication sweep," an "authorization" test layer (role/scope matrix per endpoint), and implies exercising real OAuth2/OIDC flows. No module in this service has a REST controller yet — every stage stopped at entity/repository/service, by design, since the User Module stage. Additionally, tests in this workflow are written but never run before commit (standing instruction), which means any test written against genuinely uncertain framework-internal behavior can't be caught by a compiler or a test run before it ships.
- **Selected:** This stage delivers three things that ARE safely and honestly buildable now: (1) an ArchUnit suite (`ArchitectureTest`) compiling every module-boundary decision (D-004, D-017, D-018, D-020, D-021) into a permanent check, using only ArchUnit's long-stable core `classes()`/`noClasses()`/`resideInAPackage()` API; (2) four Testcontainers integration tests (account persistence + Kafka delivery, RBAC's custom JPQL, the audit mirror's field-scrubbing, and the refresh-token family/reuse lifecycle) — each targets something a mocked unit test structurally cannot verify, using only plain Spring Data JPA / Testcontainers / KafkaConsumer APIs already proven within this codebase; (3) a structural contract test for the one JSON Schema that exists (`user-lifecycle.v1.schema.json`), via plain Jackson rather than a new schema-validation library.
- **What is explicitly deferred, and why:** (a) an endpoint-authentication sweep and (b) a role/scope authorization matrix — both require real controllers to sweep; writing them against an empty controller set would be vacuous, and inventing fake test-only controllers to give them something to assert against would be dishonest scaffolding. Both land naturally with the admin/API stage. (c) A full MockMvc-driven OAuth2 Authorization Code + PKCE flow test against Spring Authorization Server's actual `/oauth2/authorize` → `/login` → `/oauth2/token` sequence — this requires exact knowledge of SAS's session/CSRF/redirect behavior for the specific Spring Security/SAS version bundled with Boot 3.5.4, which cannot be verified without running it (the same category of risk already declined in D-016 and D-019). Writing speculative flow-orchestration code that looks thorough but is unverified would be worse than not writing it. This flow is verified by running the application directly (the user's own IntelliJ-driven workflow), not by fabricated test code.
- **Reference influence:** None — the reference project's own test suite (gap-analysis §1 #25) was `@SpringBootTest` controller tests against a live local database with hard-coded users; the explicit rejection of that pattern (Testcontainers, no live shared DB) is carried through consistently here.

## D-024 · Controller stage: scope corrections found while wiring real endpoints

- **Context:** The user flagged that no REST controllers existed after eight stages of service-layer work. Scoped to "only what's already built" (account, roles, audit read-only). Building the controllers surfaced four real gaps that would have been vulnerabilities or dead authorization if shipped as originally structured.
- **`activateEmail` has no token verification.** It took a bare account UUID and activated unconditionally — safe only because nothing called it. The intended flow (single-use, hashed, TTL'd verification token emailed to the user) is not built. **Selected:** expose activation only via `POST /admin/accounts/{id}/activate`, ADMIN-only, and audit it (`account.activated`) since it's now an admin action, not routine self-service — this revises D-022's boundary now that the call site changed. Self-service `POST /accounts/verify-email` is deferred until the token flow exists; recorded here, not silently dropped.
- **Registration must not reveal duplicate emails** (target-design §4 explicitly requires enumeration-safe registration). **Selected:** `AccountController.register` catches `DuplicateEmailException` locally and always returns the identical `RegistrationAcknowledgement`, regardless of outcome — the exception is deliberately NOT given a `@RestControllerAdvice` mapping, so a future refactor can't accidentally let it leak through as a distinguishing 409.
- **Roles claim was invisible to `@PreAuthorize`.** Resource-server default authority derivation reads the `scope` claim, not our custom `roles` claim — every `hasRole('ADMIN')` in the plan would have been a no-op. **Selected:** `JwtRoleAuthoritiesConverter` maps `roles` → `ROLE_*` `GrantedAuthority`s, wired into a `JwtAuthenticationConverter` bean used by the application security chain — standard, stable Spring Security OAuth2 resource-server customization, not internal/unverified API.
- **Actor was always null.** D-022 recorded `actorUuid=null` as an honest interim because no authenticated-caller context existed at the service layer. Now that admin controllers have a real `Authentication`, `AccountService.suspend/reinstate/delete/activateEmail` and `RoleService.assign*/remove*` all gained an `actorUuid`/real-actor parameter, threaded from `Authentication.getName()` (the account UUID, by the same convention used everywhere else). Existing tests updated to assert the real actor propagates into the audit record.
- **Role assignment/removal were unaudited.** Now that these are real, reachable admin actions, `RoleService` gained an `AuditService` dependency and records `role.assigned`/`role.removed`/`role_template.assigned`/`role_template.removed` — but only on an actual state change, not on the idempotent no-op path (existence is checked before delete now, where it wasn't before).
- **The endpoint-authentication sweep deferred in D-023 is now real**, since real controllers exist: `ArchitectureTest` gained `admin_controller_handlers_require_preauthorize`, asserting every public handler method in an `Admin*`-named `@RestController` carries `@PreAuthorize`. The full role/scope authorization matrix (target-design §17 item 4) and the OAuth2/PKCE flow test remain deferred per D-023's original reasoning — nothing about this stage changed that risk calculus.
- **Reference influence:** The enumeration-safety and admin-authorization decisions are direct, deliberate corrections of the reference's two worst failures (existsByEmail-style duplicate reveal at registration-adjacent endpoints, and the "testing only" permitAll admin whitelist) — see gap-analysis §2 and §4.

## D-025 · TOTP seed encryption: narrow KMS envelope exception to D-010 (resolves Q1/O1)

- **Context:** `target-design.md` specifies AES-GCM with a KMS-enveloped data key for TOTP seeds, but D-010 forbids AWS SDK code in the service. Open blocker `spec/auth-service/package.md` §11 Q1 / `design.md` §4b O1, named as blocking the MFA implementation task (#16).
- **Alternatives:** (a) local-only AES-GCM with a symmetric key injected via External Secrets — fully respects D-010, but weaker custody (plaintext key resident in the pod for the process lifetime); (b) a narrow, scoped KMS `GenerateDataKey`/`Decrypt` client call inside a single class; (c) delegate to the Crypto Service — real (`spec/crypto-service/`), but its charter is blockchain attestation/receipt signing, not application-secret encryption, and no contract exists for this use.
- **Selected:** (b), per ADR-0003. Confined to `com.themistra.auth.mfa.MfaSeedEncryption` (task #16) only; D-010's general prohibition stands everywhere else in the service.
- **Trade-offs:** Stronger custody than (a) — the data key is unwrapped per-operation via KMS rather than living in the environment for the pod's lifetime — without grafting an unrelated responsibility onto the Crypto Service (c). Costs a KMS network call on enroll and on every verification, plus a narrow, named D-010 exception (scoped IAM: `kms:GenerateDataKey`/`kms:Decrypt` on one CMK ARN, no wildcard).
- **Impact:** Ciphertext envelope layout (format-version byte, wrapped-data-key length/bytes, nonce, AES-GCM ciphertext+tag) fixed in ADR-0003. `mfa_enrollments.secret_encrypted`'s existing V1 comment ("AES-GCM, KMS-enveloped data key") already describes this outcome accurately — no migration needed. KMS's own automatic annual CMK rotation (infra/CDK) handles rotation; the application does not track key versions.
- **Reference influence:** None (reference has no MFA).

## D-026 · Rate-limit thresholds: 10/5/30 per minute, MFA folded into the login bucket (resolves O2)

- **Context:** D-013 selected the *mechanism* (in-process Bucket4j per replica + durable lockout
  backstop) but deferred the specific threshold numbers `design.md` §4b O2 asks for: login,
  `/oauth2/token`, password-reset confirm, and MFA verify.
- **Alternatives:** no rich numeric trade-off study — task #31's Phase 1/2 proposed the specific
  values below directly (tight enough to meaningfully slow credential guessing, loose enough not to
  false-positive-lockout a legitimate user retrying a typo), flagged explicitly per `package.md` §11
  Q2's "confirm or replace the placeholders" instruction, and confirmed via human gate rather than
  chosen after weighing several concrete alternative numbers.
- **Selected:** `login-per-minute=10`, `password-reset-per-minute=5`,
  `oauth-token-per-minute=30` — the last applies only to `POST /oauth2/token` with
  `grant_type=refresh_token` (`RateLimitFilter.isOAuthTokenRefreshRequest`), not every grant type;
  `authorization_code` requests are not limited by this filter (`application.properties:104-106`).
  No separate MFA-verify threshold exists — MFA code submission is folded into the same `/login`
  bucket by construction, not an oversight: `TotpAuthenticationProvider` verifies the TOTP/recovery
  code inside the same `/login` POST as the password (T20's single-request design, O4/D-028), so
  there is no separate HTTP call for MFA verification to rate-limit independently.
  `RateLimitFilter.java`'s own Javadoc (lines 23-26) states this explicitly.
- **Trade-offs:** A merchant with confirmed MFA who mistypes their TOTP code repeatedly consumes the
  same budget as password-guessing attempts — accepted, since both are legitimately part of "how many
  login attempts per minute is reasonable," not two independent concerns. Separately, both the
  password-reset and `/oauth2/token` refresh buckets are keyed by the SHA-256 hash of the submitted
  token, not by account (`RateLimitFilter.java`'s own Javadoc, lines 32-44) — a per-token, not
  strictly per-account, granularity, accepted (Kimi Phase 8 Finding 4, femi's T31 gate decision)
  because each reset/refresh token is already single-use and time-limited; the limit's real value is
  slowing brute-force guessing of one specific token, not capping how many tokens an account can
  request.
- **Impact:** Three `@ConfigurationProperties` values, each environment-overridable
  (`RATE_LIMIT_LOGIN_PER_MINUTE`, etc.); `RateLimitFilter` enforces all three paths before credential
  validation (D4, DoS-backstop value).
- **Reference influence:** None (reference had no rate limiting at all — gap-analysis §1 #14).

## D-027 · Session device-label source: still open (O3 — recorded as unresolved, not resolved)

See `spec/auth-service/design.md` §4b O3 for the original framing — this entry records that none of
the three options it names was ever selected; `design.md` itself is not edited by this task.

- **Context:** `design.md` §4b O3 asks how a refresh-token family's `device_label` is determined:
  (a) a client-supplied label in the authorize request, (b) a hash of the `User-Agent`, or (c) a
  generic default.
- **Current state:** None of the three was ever chosen. `ReuseDetectingAuthorizationService.java:95`
  passes a literal `null` for `deviceLabel` on every real token issuance
  (`tracker.trackIssuance(authorization.getId(), authorization.getPrincipalName(), null, hash)`).
  `SessionResponse`'s own Javadoc already documents the consequence: `deviceLabel` is `null` for
  every session returned by `GET /accounts/me/sessions` today.
- **Why it's recorded as open, not closed:** The schema and API surface fully support the concept
  (D-003) — this is a genuine gap in a decision that was never made, not a deliberate "generic
  default" selection retroactively dressed up as one. Recording it as resolved (e.g., "chose (c),
  generic default/null") would misrepresent that any choice was actually weighed; `null` is simply
  what happens when nothing computes a value.
- **What would resolve it:** A future task implementing one of the three named options — most likely
  (b), a `User-Agent` hash, since it requires no client-side change and gives genuinely
  distinguishing information for the `GET /accounts/me/sessions` UI this was designed to support.
- **Reference influence:** None (reference had no per-device session concept at all).

## D-028 · Login page: default Spring Security form, no custom template (resolves O4)

- **Context:** `design.md` §4b O4 asks whether SAS's first-party `/login` page should be the default
  Spring Security form or a custom Thymeleaf template supporting password + TOTP/recovery-code
  fields.
- **Alternatives:** (a) default Spring Security form login page; (b) a custom Thymeleaf template.
- **Selected:** (a) — no custom template was built; `src/main/resources` contains no login HTML.
- **Trade-offs:** The default form's markup is less polished than a purpose-built template, but it is
  fully sufficient: `TotpAuthenticationProvider` consumes an additional `mfaCode` form parameter on
  the same `/login` POST the default form already submits (T20's single-request password+MFA
  design), so no template customization was needed to support the second factor. A custom template
  remains a low-risk future addition (branding/UX) if ever wanted — nothing about the authentication
  mechanism depends on the page's markup.
- **Impact:** Zero frontend/template code in this service; `SecurityChainsConfig.applicationChain`'s
  `.formLogin(...)` configures `authenticationDetailsSource`/`failureHandler`/`successHandler` but
  never calls `.loginPage(...)`, so Spring Security's default auto-generated login page renders
  as-is. `SasLoginIntegrationTest`'s own CSRF-scraping helper relies on the real default-login-page
  markup, confirming this is the actual, currently-shipped behavior, not an assumption.
- **Reference influence:** None (reference's login flow was a JSON API, not a server-rendered page).

## D-029 · Recovery-code hashing: SHA-256 (resolves O5)

- **Context:** `design.md` §4b O5 asks for the recovery-code hashing primitive, defaulting to
  SHA-256 unless a case for bcrypt (defensible if rotation is rare) is made.
- **Alternatives:** (a) SHA-256 (the suggested default); (b) bcrypt.
- **Selected:** (a) SHA-256. `RecoveryCode.codeHash` stores a SHA-256 hex digest
  (`RecoveryCode.java:15-17,39-41`, `CHAR(64)`), produced via `Hashing.sha256(rawCode)`
  (`MfaService.java:139`, the same shared primitive D-021 established).
- **Trade-offs:** Unlike a password, a recovery code is high-entropy and single-use (consumed and
  invalidated on first successful use, L6) — bcrypt's deliberate slowness defends against offline
  guessing of a *low*-entropy secret, which does not apply here. SHA-256 is the correct-cost choice
  for a value that is never brute-forceable in the first place.
- **Impact:** No new hashing dependency; reuses `common.Hashing` (D-021).
- **Reference influence:** None (reference has no MFA/recovery-code concept).

## D-030 · No maximum active API-key count per merchant (partially resolves Q3)

- **Context:** `package.md` §11 Q3 asks two things: the launch scope vocabulary (answered — only
  `merchant.api` exists, `ApiKeyService.DEFAULT_SCOPES`) and whether a maximum active-key count per
  merchant should be enforced. `ApiKeyProperties` (`prefix`, `tokenTtlMinutes`) has no such field;
  `ApiKeyService.create` has no quota check.
- **Alternatives:** (a) no limit (current, implicit); (b) a fixed, configurable default limit (e.g.
  100 active keys/merchant) as a defensive guard even without a demonstrated abuse pattern; (c) no
  limit now, add one only if operational abuse is actually observed.
- **Selected:** (c), made explicit here rather than left as an unexamined absence. No demonstrated
  operational need exists yet (no merchant-facing API-key abuse pattern observed in this service's
  history); a speculative limit would be exactly the kind of "design for a hypothetical future
  requirement" this project's own engineering principles decline to do without evidence.
- **Trade-offs:** An unbounded key count is a real, if currently theoretical, exposure (a
  compromised or careless merchant integration could accumulate many live credentials). Accepted
  because revoking is already self-service and cheap (`DELETE /api-keys/{keyUuid}`), and a wrong
  guess at a limit now (too low blocks legitimate multi-environment merchant integrations; too high
  provides no real protection) is worse than deferring to real usage data.
- **Impact:** None to current code. If a limit is added later, `ApiKeyProperties` gains a
  `maxActiveKeysPerMerchant` field and `ApiKeyService.create` gains a count check against
  `ApiKeyRepository`'s existing per-account query — no other design change anticipated.
- **Revisit trigger:** observed operational abuse (a merchant with an anomalously high active-key
  count) or a concrete partner/compliance requirement.
- **Reference influence:** None (reference has no API-key concept — merchant integrations are
  net-new, gap-analysis §1 #12).
