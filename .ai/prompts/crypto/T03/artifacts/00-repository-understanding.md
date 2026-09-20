# crypto · T03 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

`services/crypto` (`com.themistra.crypto`, artifact `crypto-service`) is a Spring Boot 3.5.4 / Java 21
module registered in the root `<modules>` after `services/auth`. Only two tasks have shipped so far
(T01, T02); everything else in `design.md` §6's package map is still unbuilt.

- **Modules/packages present:** none of the feature packages (`adapter`, `provider`, `quorum`,
  `observation`, `finality`, `watch`, `reorg`, `token`, `screening`, `attest`, `events`, `common`) exist
  yet. The only Java source is `CryptoServiceApplication` (bare `@SpringBootApplication`, deliberately no
  `@ConfigurationPropertiesScan`/`@EnableScheduling`/`@EnableSchedulerLock` — T01 comment says these are
  added "the moment the first such class exists", which is relevant to this task since it introduces the
  first `@ConfigurationProperties` classes).
- **Persistence:** PostgreSQL, `chain` schema, Flyway DDL-only migrations. `V1__chain_baseline.sql`
  (T02) created all 10 baseline tables verbatim from design.md §4c (`watches`, `observations`,
  `quorum_decisions`, `provider_health`, `chain_cursors`, `token_allowlist`, `screening_results`,
  `attestations`, `outbox`, `shedlock`). `V2__crypto_app_role_and_grants.sql` created the `crypto_app`
  runtime role: INSERT+SELECT-only on `observations`/`attestations`/`quorum_decisions`, no committed
  password (`ALTER ROLE` is a documented manual local step per README). Runtime datasource in
  `application.properties` connects as `crypto_app`, never the migration/owner role (`checky`);
  `spring.flyway.enabled=false` at runtime — migrations only run via the Maven Flyway plugin.
- **Events/outbox:** the `outbox` table exists in the schema (T02), but no `OutboxPublisher` or
  `EventTopics` class exists yet — that's T04, not this task.
- **Security:** nothing exists yet. No `SecurityFilterChain`, no `PublicEndpoints`, no
  `@ConfigurationProperties` class, no resource-server wiring at all. This task (T03) is what
  introduces all of it.
- **Config:** `application.properties` currently has only `spring.application.name`,
  `spring.threads.virtual.enabled=true`, `spring.profiles.active=local`, datasource/Hikari settings, and
  `spring.flyway.enabled=false`. No `themistra.crypto.*` namespace exists yet. No `dev`/`staging`/`prod`
  profile-specific properties files exist (agents.md names these four profiles as standing rule, but only
  `local` has been touched so far).
- **pom.xml (T01)** already declares everything this task needs at the dependency level:
  `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server`,
  `spring-boot-starter-validation`, `software.amazon.awssdk:kms` (scoped to the future `attest` module
  per ADR-0004 — **not** relevant to this task beyond validating a KMS config properties shape), and
  `spring-security-test` for tests. No new dependency should be needed for T03.

## 2. Existing code this task touches — what's already there vs. new

**Already exists (context, not to be modified unless the task requires it):**
- `services/crypto/pom.xml` — resource-server starter already present.
- `services/crypto/src/main/resources/application.properties` — will gain new
  `themistra.crypto.*` property keys; existing datasource/flyway/virtual-thread lines are untouched.
- `CryptoServiceApplication.java` — will need `@ConfigurationPropertiesScan` added once the first
  `@ConfigurationProperties` class exists (per its own doc comment), and possibly `@EnableWebSecurity`
  wiring lives in a `@Configuration` bean instead (see auth's pattern below — auth does NOT annotate
  the application class with `@EnableWebSecurity`; that lives on `SecurityChainsConfig`).
- `V1__chain_baseline.sql` / `V2__...sql`, `ChainBaselineMigrationIntegrationTest`,
  `T01SkeletonRegressionTest` — unrelated to this task, do not touch.

**New in this task (per design.md §6 file map, scoped to T03's statement):**
- `common/config/*Properties.java` — validated `@ConfigurationProperties` records for **providers,
  finality, screening, KMS, and S3 snapshot keys** (design §4c references; the task statement names
  these five areas explicitly — note design §6 only labels the directory `common/config/*Properties.java`
  generically, so the concrete class names/shapes are this task's own design work in Phase 1/2).
- `common/PublicEndpoints.java` — actuator + the verification-keys well-known path only (mirrors
  auth's `PublicEndpoints` shape but with a different, narrower content set — crypto has no
  self-registration-style public POST endpoints).
- `common/ResourceServerConfig.java` — service-JWT validation requiring `internal.crypto:write`
  scope (R27). This is new architectural ground for the codebase (see §3 below — no prior
  resource-server-only pattern exists in this repo yet).
- Likely also `common/ApiExceptionHandler.java` per the package map, though the task statement's own
  text doesn't mention RFC 9457 error handling explicitly — worth flagging as a Phase 1 scoping
  question (design §6 lists it under `common/` alongside the other three files this task's statement
  does name).

**Explicitly NOT this task** (later tasks own these, do not build them now): `WatchController`
(T15), `AttestController` (T21), `VerificationKeysController` (T22), `OutboxPublisher`/`EventTopics`
(T04), any adapter/quorum/finality/token/screening/attest logic.

## 3. Established patterns to follow

**No prior resource-server-only precedent exists in this codebase.** `services/auth` is an OAuth2
**authorization server** (issuer) that also happens to validate its own issued JWTs for its own
management endpoints — it builds its `JwtDecoder` from **local** key material
(`JwksConfig.jwkSource` → `SigningKeysProperties` → `ImmutableJWKSet`), because it owns the keys. Crypto
service has no keys of its own to validate JWTs with; it must fetch auth-service's public JWKS remotely
(`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointing at auth's `/oauth2/jwks`, or an
injected `NimbusJwtDecoder.withJwkSetUri(...)`). `services/payment` and `services/notification` have no
code at all yet, so there's no sibling module to mirror either. **This is genuinely new pattern
territory** — take auth's shape as a structural model (two-chain-like separation of public vs.
authenticated paths, `PublicEndpoints` constant, `@EnableWebSecurity` on a `@Configuration` class) but
not its literal implementation (no local `JWKSource`, no `JwtEncoder`, no `OAuth2AuthorizationServerConfigurer`).

- **Token shape to validate against** (`contracts/api/token-claims.md`, Path 2 — service-to-service
  `client_credentials`): 9 of 13 claims present, `scope` is a **JSON array of strings** (not
  space-separated). Spring Security's default `JwtGrantedAuthoritiesConverter` reads the `scope` (or
  `scp`) claim and prefixes each entry with `SCOPE_`, so `hasAuthority("SCOPE_internal.crypto:write")`
  (or `.hasAuthority("internal.crypto:write")` via a scope-matcher DSL) should work with Spring's
  **default** converter — crypto-service does not need auth's custom `JwtRoleAuthoritiesConverter`
  (that maps a `roles` claim, which Path 2 tokens don't even carry).
- **`@ConfigurationProperties` pattern** (`ApiKeyProperties`, `SigningKeysProperties` in
  `services/auth`): a `record`, `@ConfigurationProperties(prefix = "themistra.auth.xxx")`, `@Validated`,
  Jakarta Bean Validation annotations (`@NotBlank`, `@Min`/`@Max`) on components, doc comment explaining
  *why* the bound/default was chosen. Crypto's equivalents should use `themistra.crypto.*` prefixes
  (e.g. `themistra.crypto.providers`, `themistra.crypto.finality`, `themistra.crypto.screening`,
  `themistra.crypto.kms`, `themistra.crypto.snapshot` or similar — exact naming is Phase 1/2 work, not
  decided here). agents.md/L13 requires startup to **fail** on missing/invalid config in non-local
  profiles — auth's precedent for that is plain Bean Validation (`@Validated` + constraint annotations),
  not custom failure logic.
- **`PublicEndpoints` pattern** (auth): a `final` utility class, `private` constructor, a
  `String[] PATTERNS` array for any-method-public paths, plus a `List<MethodScoped>` record list for
  method-scoped public paths, with a doc comment noting "a future sweep test asserts no permitAll exists
  outside this list." Crypto's version is simpler (task statement: "actuator + the verification-keys
  well-known path" only) — likely just `PATTERNS`, no `METHOD_SCOPED` needed, but confirm in Phase 1/2
  once the exact actuator sub-paths to expose are decided (auth exposes `/actuator/health/**`,
  `/actuator/info`, `/actuator/prometheus` specifically, not a blanket `/actuator/**`).
- **Security config class shape** (auth's `SecurityChainsConfig`): `@Configuration`,
  `@EnableWebSecurity`, `@EnableMethodSecurity`, one or more `@Bean SecurityFilterChain` methods with
  `@Order`, `.authorizeHttpRequests(auth -> { auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll(); ...; auth.anyRequest().authenticated(); })`,
  `.oauth2ResourceServer(rs -> rs.jwt(...))`. Crypto needs only one chain (no authorization-server chain
  to layer), so this should be simpler than auth's two-chain setup.
- **Error handling:** agents.md mandates RFC 9457 `application/problem+json`, no stack traces/internal
  detail. No existing `ApiExceptionHandler`-equivalent exists anywhere in the repo yet to pattern-match
  against (auth-service's own error handling was not located in this pass — worth confirming in Phase 1
  whether auth has one worth mirroring, or whether crypto is first to build this too).
- **Idempotency/outbox/stored-proc-DAO patterns:** agents.md explicitly says to load the `idempotency`
  and `stored-proc-dao` Skills rather than restating — not directly relevant to T03 (config/security),
  flagging only because agents.md's "Reusable procedures" section applies to every task.

## 4. Testing conventions

- Unit tests: plain JUnit 5, no Spring context where avoidable, fixed `Clock` (not yet needed by this
  task — no time-dependent logic in config/security). ArchUnit 1.3.0 is already a test-scope dependency
  (`pom.xml`) for module-boundary rules (L15) — not yet exercised by any test; T25 is the task that adds
  the ArchUnit ban on non-`attest` KMS access, but a narrower boundary check could plausibly start here
  if Phase 1/2 scope it that way (design.md doesn't explicitly assign an ArchUnit test to T03).
- Integration tests: Testcontainers (Postgres — Kafka not yet used by any shipped test).
  `ChainBaselineMigrationIntegrationTest` (T02) is the only precedent so far, and it's schema/grant
  focused, not security focused. A resource-server test for T03 will likely use
  `spring-security-test`'s `@WithMockUser`/`SecurityMockMvcRequestPostProcessors.jwt()` and
  `MockMvc` rather than a full Testcontainers stack, but since there are no controllers yet to test
  against (`WatchController`/`AttestController` don't exist until T15/T21), **the named test for this
  task (`shouldRequireInternalScopeForWatchAndAttestEndpoints`) cannot exercise a real endpoint yet** —
  this is a known gap/tension to raise in Phase 1, not resolve here.
- Surefire reports on disk confirm T01 (`T01SkeletonRegressionTest`, 6 tests) and T02
  (`ChainBaselineMigrationIntegrationTest`, 10 tests) both currently pass.
- No `dev`/`staging`/`prod` application-\<profile\>.properties exist yet to verify "startup fails on
  missing config in non-local profiles" against — this task will likely need to either create profile
  property files or test the fail-fast behavior via `@Validated` binding failures directly (e.g.
  `ApplicationContextRunner` with an active non-local profile and missing properties), matching how
  `ApiKeyProperties`/`SigningKeysProperties` are validated in auth (no dedicated test for those was
  located in this pass — worth checking in Phase 1 whether auth even has such a test to mirror, or
  whether this is untested precedent too).

## 5. Known gaps / unknowns

- **I do not know** the exact planned property key structure/namespace under `themistra.crypto.*` for
  providers/finality/screening/KMS/S3-snapshot — design.md §4b (O1, O4) leaves provider set and
  screening vendor as open questions (Q1, Q2 unanswered in §11), so the *shape* of those
  `@ConfigurationProperties` records has to be designed generically enough to not assume a specific
  vendor yet. This is Phase 1/2 work, not something derivable from the repo today.
  - Task statement says "S3 snapshot keys" — the `observation` module's `ObservationSnapshotStore` is a
    T08 concern; T03 only needs the **config properties** for S3 (bucket/key-prefix/etc.), not the store
    implementation itself.
- **I do not know** whether auth-service has an existing RFC 9457 `ApiExceptionHandler`-style class
  worth mirroring — not located in this pass (only files under `services/auth/src/main/java/com/themistra/auth/common/`,
  `token/`, `apikey/`, `ratelimit/` were inspected; a broader search wasn't run since design.md doesn't
  explicitly assign error-handling to T03's task statement, only to the package map).
- **I do not know** the exact scope-matcher idiom the team prefers (`hasAuthority("SCOPE_x")` vs. a
  custom `JwtAuthenticationConverter` with `setAuthorityPrefix("")`) — no service in this repo has built
  a pure resource-server scope check yet to copy.
- `contracts/api/crypto-internal.yaml` does not exist yet (confirmed: `contracts/api/` only has
  `auth.yaml` and `token-claims.md`) — it's T23's deliverable. R28 (contract conformance) is out of
  scope for T03; the task statement itself doesn't cite R28, only R27.
- No `jwk-set-uri` (or equivalent auth-JWKS-endpoint) value is configured or documented anywhere yet —
  Phase 1/2 will need to decide the config key name and default (likely pointing at auth-service's
  `/oauth2/jwks` per `JwksConfig`, but the exact hostname/URI convention across services isn't
  established anywhere in the repo).

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
