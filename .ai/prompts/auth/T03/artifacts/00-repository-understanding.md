# auth · T03 — Phase 0: Repository Understanding

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 Maven module (`auth-service`), package-by-feature
under `com.themistra.auth`, one Postgres schema (`auth`) with Flyway-owned DDL (V1–V4, immutable)
and Hibernate in `validate`-only mode. Modules present today: `account`, `admin`, `apikey` (empty
package so far), `audit`, `authn` (currently only `AccountUserDetailsService`), `authz`, `common`,
`events`, `mfa` (empty), `token`.

- **Persistence**: JPA entities + package-private Spring Data repositories (ArchUnit-enforced —
  `repositories_are_never_public`). Internal `bigint identity` PKs never leave the service; UUIDs
  (`account_uuid`, etc.) are the external identifier.
- **Outbox/events**: every state change of interest to other services is written via
  `OutboxPublisher.publish(aggregateType, key, eventType, schemaVersion, payload)` inside the same
  `@Transactional` boundary as the DB write (see `AccountService`, `AuditService`). `EventTopics`
  maps `aggregateType → Kafka topic`. `events` module is domain-agnostic by ArchUnit rule
  (`events_module_stays_domain_agnostic`) — it never imports feature-module types.
- **Security**: `SecurityBeansConfig` (in `common`) provides the shared `PasswordEncoder`
  (`{bcrypt}` strength 12, delegating encoder) and the injectable `Clock` bean
  (`Clock.systemUTC()` in prod, `Clock.fixed(...)` in unit tests) that all time-sensitive logic
  must use instead of `Instant.now()`. `common.PublicEndpoints` is the CI-enforced exhaustive
  unauthenticated-path allowlist, consumed only by the token module's security chain
  (ArchUnit-enforced).
- **Audit**: `AuditService.record(RecordAuditEventRequest)` is the only sanctioned way to write to
  `auth_audit` — it inserts the durable row and mirrors a reduced payload to
  `auth.security.audit` via the outbox, in one transaction. `audit` never depends on `account`
  (ArchUnit-enforced); callers pass `accountUuid`/`actorUuid` only.
- **Config**: flat `application.properties`, values bound to validated
  `@ConfigurationProperties` records (see §3 below). `@ConfigurationPropertiesScan` is enabled on
  `AuthServiceApplication`, so a new properties record needs no manual `@Bean`/`@Import` — it is
  picked up automatically as long as it's under `com.themistra.auth`.

## 2. Existing code this task touches

T03 is **Foundation** work — no controller/endpoint wiring yet (that's task 9, out of scope here).
Scope is: `PasswordPolicyProperties`, `PasswordPolicy`, and the HIBP range-check HTTP call.

**New (nothing under these names exists yet):**
- `account.PasswordPolicy` — length + breach-check domain rule (per `design.md` §6, lives in the
  `account` package, not `authn`).
- `account.PasswordPolicyProperties` — validated `@ConfigurationProperties` record.
- `authn.BreachCheckClient` — `RestClient`-based HIBP k-anonymity caller (per `design.md` §6, this
  lives in `authn`, a package that today contains only `AccountUserDetailsService` and
  `package-info.java`).
- Config keys `themistra.auth.password.min-length`, `.max-length`, `.breach-check.enabled`,
  `.breach-check.url-prefix` — none of these exist in `application.properties` yet (verbatim block
  is in `design.md` §4c and must be added exactly as written).
- No `RestClient` bean exists anywhere in the service today (`grep` for `RestClient` in
  `src/main` and `src/test` returns nothing), and no `RestTemplate`/WireMock/MockWebServer
  dependency is on the classpath either — this is a genuinely new integration point for the
  service, not a pattern to imitate.

**Existing code that overlaps or interacts:**
- `account.dto.RegisterAccountRequest` already has `@Size(min = 12, max = 128)` bean validation on
  `password`, with a comment attributing 12/128 to NIST 800-63B (D-006). This is a **hardcoded**
  duplicate of the length rule T03 is asked to make config-driven
  (`themistra.auth.password.min-length`/`max-length`). T03 does not touch this DTO (no endpoint
  wiring in scope), but the eventual relationship between this annotation and the new
  `PasswordPolicy` domain check is unresolved — flagged under Known gaps below.
- `common.Hashing` currently exposes only `sha256(String)`. R9/L2 require the HIBP check to hash
  with **uppercase SHA-1**, a different algorithm and case convention than anything that exists
  today. Whether to extend `Hashing` or keep the SHA-1 logic local to `BreachCheckClient` is open.
- `common.SecurityBeansConfig` is the established home for shared, chain-agnostic beans
  (`PasswordEncoder`, `Clock`). No `RestClient` bean lives there yet; T03 needs to decide whether
  `BreachCheckClient` builds its own `RestClient` inline or a bean is added here — `design.md` §6
  lists `BreachCheckClient` as the only new class for this concern, implying the former.
- `common.ProblemTypes` holds the fixed set of RFC 9457 problem-type URIs. Not needed for T03
  itself (no controller/error-response work in this task), but a future breached-password
  rejection response will likely need a new entry here.

## 3. Established patterns to follow

- **Config records**: `@ConfigurationProperties(prefix = "themistra.auth.<x>")` + `@Validated` +
  Jakarta Bean Validation annotations (`@NotBlank`, `@NotEmpty`, `@NotNull`) on record components,
  with nested records for grouped settings (see `AuthClientsProperties`, `SigningKeysProperties`).
  `SigningKeysProperties` shows the pattern for defaulting a null nested record via a compact
  constructor rather than requiring callers to pass an empty instance.
- **Services**: constructor-injected `@Service`, final fields, `@Transactional` on
  mutating methods, `@Transactional(readOnly = true)` on reads. `Clock` is always injected, never
  `Instant.now()`/`Clock.systemUTC()` called inline in domain/service code.
- **Outbox/idempotency**: any new state that other services must react to goes through
  `OutboxPublisher.publish(...)` in the same transaction as the write; T03 itself has no such
  state change (a breach check is a stateless HTTP call), but task 4 (breach-check audit event,
  explicitly out of scope for T03 per tasks.md, yet named in T03's own test list — see Known gaps)
  will use `AuditService.record(...)`, not a direct outbox call.
- **Module boundaries**: package-by-feature, ArchUnit-enforced (`ArchitectureTest`). Repositories
  are package-private. A module's own service class is the only entry point other modules use.
  `PasswordPolicy`/`PasswordPolicyProperties` in `account` and `BreachCheckClient` in `authn` will
  each need to respect this — e.g., if `PasswordPolicy` (account) needs to call
  `BreachCheckClient` (authn), check whether that crosses a boundary ArchUnit would flag; no
  existing rule explicitly forbids `account → authn` or vice versa, but this should be verified in
  the design phase, not assumed.
- **Error handling**: RFC 9457 `application/problem+json` via per-module `*ExceptionHandler`
  classes (e.g. `AccountExceptionHandler`) plus the shared `common.ApiExceptionHandler`; stable
  problem-type URIs live only in `common.ProblemTypes`. Not directly exercised by T03 (no
  controller in scope) but `PasswordPolicy` should raise a domain exception, not a raw
  `IllegalArgumentException`, for consistency with the rest of the codebase (see
  `DuplicateEmailException`, `InvalidAccountStateException` in `account`) — exact exception type
  is a Phase 1/3 design decision, not decided here.
- **Secrets/config discipline**: `application.properties` never contains secrets; HIBP is a public,
  unauthenticated API, so `breach-check.url-prefix` is a plain value, not `${...}`-injected from
  External Secrets — consistent with how non-secret external endpoints are already configured.

## 4. Testing conventions

- **Unit tests**: plain JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock` fields,
  `AssertJ` assertions), no Spring context, fixed `Clock` built inline
  (`Clock.fixed(NOW, ZoneOffset.UTC)`) — see `AccountServiceTest`. This is the pattern
  `PasswordPolicy` unit tests (length boundaries, breach rejection, fail-open) should follow.
- **HTTP-call testing**: no existing precedent in this service. There is no WireMock/MockWebServer
  dependency on the classpath and `RestClient` has not been used before, so the T03 implementer
  will need to decide how to unit-test `BreachCheckClient` without real network calls (e.g.
  constructing `RestClient` over a mocked `ClientHttpRequestFactory`, or injecting a seam) —
  this is a design question the repository does not already answer.
- **Integration tests**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, real
  Postgres + Kafka via Testcontainers (see `AccountPersistenceIntegrationTest`,
  `AuditTrailIntegrationTest`). Per `agents.md`, these sit above unit + ArchUnit + contract tests
  in the pipeline. T03's scope (pure domain logic + an outbound HTTP call, no persistence) likely
  does not need a Testcontainers test, but that is a Phase 1/2 call, not decided here.
- **ArchUnit**: `ArchitectureTest` at the service root, `@AnalyzeClasses(packages =
  "com.themistra.auth", importOptions = ImportOption.DoNotIncludeTests.class)`. Existing rules
  cover `account` entity isolation, `authz`/`audit` independence from `account`, `events`
  domain-agnosticism, repository visibility, `PublicEndpoints` consumption, and admin
  `@PreAuthorize` coverage. No existing rule mentions `authn` or a `PasswordPolicy`/
  `BreachCheckClient` boundary — nothing to violate today, but nothing to lean on either.
- **Fixed `Clock`**: required for any time-based assertion; `SecurityBeansConfig` supplies the real
  bean, tests always construct their own fixed instance.

## 5. Known gaps / unknowns

- I do not know how `BreachCheckClient` should be unit-tested given there is no HTTP-mocking
  library (WireMock/MockWebServer/`MockRestServiceServer`) currently on the classpath and no
  existing `RestClient` usage to imitate. Adding a test dependency may be needed and is a design
  decision, not something I should assume here.
- I do not know whether `PasswordPolicy` (per `design.md` §6, package `account`) is meant to call
  `BreachCheckClient` (per `design.md` §6, package `authn`) directly, or whether an interface/port
  should sit in `account` with the HTTP implementation in `authn` to avoid a cross-module
  dependency the way `audit`/`authz` avoid depending on `account`. `ArchitectureTest` does not yet
  encode a rule either way.
- I do not know the intended relationship between the new config-driven length check
  (`PasswordPolicy`, `themistra.auth.password.min-length/max-length`) and the existing hardcoded
  `@Size(min = 12, max = 128)` bean validation on `RegisterAccountRequest.password`. Both currently
  encode 12/128, but one is compile-time and the other will be config-driven; whether the
  annotation should be removed, loosened, or left as a redundant guard is not answered by the spec
  package and is out of scope for T03 anyway (no DTO/controller work here).
- T03's own named test list (`00-repository-understanding.md` header, sourced from the Phase 0
  prompt) includes `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`, which asserts the
  fail-open-with-audit behavior — but `tasks.md` assigns "wire `AuditService.record(...)` for
  `password.breach_check_failed`" to **task 4**, a separate task from T03's "task 3." I do not know
  whether T03 is expected to implement the audit call itself to satisfy its named test, or whether
  the fail-open behavior in T03 should be verified without an audit assertion and the audit wiring
  deferred to T04. This is a scope-boundary question for Phase 1 (specification extraction), not
  something to resolve or guess at here.
- I do not know the exact shape of the HIBP response parsing requirement (R9 says "query ... IF the
  trailing hash suffix appears in the range with a count greater than zero, THEN ... reject" — the
  response body format itself, e.g. `SUFFIX:COUNT` per line, is HIBP's well-known API convention
  but is not written anywhere in the spec package, so the parsing contract should be treated as
  external-API knowledge to confirm, not something the repo already documents).
