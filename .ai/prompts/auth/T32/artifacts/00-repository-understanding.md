<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T32 · Phase 0 — Repository Understanding

## 1. Architecture summary

`auth-service` is a Spring Boot 3.5.4 / Java 21 service, feature-modularized under
`com.themistra.auth.*` (`account`, `authn`, `authz`, `apikey`, `audit`, `token`, `cleanup`,
`ratelimit`, `mfa`, `events`, `common`), each owning its own entities/repositories/services, with
module boundaries enforced by `ArchitectureTest` (ArchUnit). Persistence is PostgreSQL via Flyway
migrations (currently at `V8`) and Spring Data JPA; cross-module events go through a Kafka outbox
pattern confined to the `events` package. Security is two `SecurityFilterChain` beans in
`token/SecurityChainsConfig.java`:

1. **`authorizationServerChain`** (`@Order(1)`) — Spring Authorization Server's own protocol
   endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`), OIDC-enabled, unauthenticated requests
   redirected to `/login`. `.anyRequest().authenticated()` — SAS's own internals decide what's
   reachable pre-auth within its matched paths, this chain declares no `permitAll()` itself.
2. **`applicationChain`** (`@Order(2)`) — this service's own REST APIs plus `/login` (form login).
   This is the **only** chain that calls `.permitAll()`, and it does so exactly twice, both
   sourced from `common/PublicEndpoints.java`: once for `PublicEndpoints.PATTERNS` (any-method,
   e.g. actuator health), once per entry in `PublicEndpoints.METHOD_SCOPED` (method+path pairs,
   e.g. `POST /accounts`). Everything else on this chain requires a JWT (this service is a
   resource server for its own APIs, `roles` claim mapped to authorities).

## 2. Existing code this task touches

- **`common/PublicEndpoints.java`** — the exhaustive allowlist (L11). Already contains
  `new MethodScoped(HttpMethod.POST, "/api-keys/token")` with an explicit `// the API key itself
  is the credential (L11)` comment — **this entry already exists**, added during T25 (API key
  exchange). The task's "assert `/api-keys/token` is in the public list" is therefore a
  regression-lock on already-correct state, not new production wiring.
- **`token/SecurityChainsConfig.java`** — the only file that calls `.permitAll()` at all (2 call
  sites, both reading from `PublicEndpoints`, confirmed via `grep -rn "permitAll" src/main/java/`
  — no other hits anywhere in the codebase today).
- **`ArchitectureTest.java`** — already has one directly relevant rule,
  `only_token_module_references_public_endpoints` (no class outside `token`/`common` may even
  depend on the `PublicEndpoints` class). It does **not** yet have any rule constraining where
  `.permitAll()` itself may be called from, nor any rule/test asserting the allowlist's actual
  *contents*.
- **`common/PublicEndpointsTest.java`** (test-only, already exists) — a plain JUnit test,
  `methodScopedContainsBothPasswordResetEndpoints`, added at T07 per its own Javadoc ("Kimi Phase
  11 Gap 8 (T07): L11 requires both new password-reset paths registered in
  `PublicEndpoints#METHOD_SCOPED` — a future accidental removal must fail CI"). This is the
  **established precedent** for "assert entry X is in the public list" — and it lives in a plain
  JUnit content-assertion test, not in `ArchitectureTest` (ArchUnit). It does not yet assert
  `/api-keys/token`'s presence.
- The task's named test, **`shouldEnforcePublicEndpointAllowlist`**, does not exist anywhere in
  the codebase yet (confirmed via `grep -rn`).

## 3. Established patterns to follow

- **ArchUnit rules** (`ArchitectureTest.java`, `@AnalyzeClasses(packages =
  "com.themistra.auth", importOptions = ImportOption.DoNotIncludeTests.class)`) express permanent,
  CI-enforced module-boundary/dependency invariants — each rule's `.because(...)` names the design
  decision it encodes (e.g. `only_token_module_references_public_endpoints` cites the
  gap-analysis §2 "temporary whitelist" lesson this whole task also traces back to). Rules operate
  on class/package/dependency structure and method-declaration annotations (see
  `admin_controller_handlers_require_preauthorize`, which checks every public method in an
  `Admin*` `@RestController` is `@PreAuthorize`-annotated) — not on runtime configuration values or
  method-call string arguments.
- **Plain-JUnit content-assertion tests** (`PublicEndpointsTest.java`, and the broader
  `*PropertiesTest` family across `cleanup`/`ratelimit`/`apikey`) assert the actual *contents* of a
  constant/record against what the spec requires — no Spring context, fast, and this is exactly
  the shape T07's own `PublicEndpointsTest` already used for the identical "is entry X registered"
  question T32 is now asking for a different entry.
- **`common` package** hosts genuinely cross-cutting, framework-facing primitives with no business
  logic of their own (`PublicEndpoints`, `ProblemTypes`, `SecurityBeansConfig`,
  `ApiExceptionHandler`) — consistent with L12's "shared plumbing lives in `common`."

## 4. Testing conventions

- Unit tests: plain JUnit 5, no Spring context where avoidable, fixed `Clock` where time matters
  (not applicable here — this task has no time-dependent logic).
- ArchUnit tests: `@AnalyzeClasses` + `@ArchTest` static rule fields, one rule per named
  `static final ArchRule` field with a `.because(...)` explaining the design decision it encodes.
- Integration tests: Testcontainers (Postgres + Kafka), `@SpringBootTest(webEnvironment =
  RANDOM_PORT)` — not obviously needed for this task, since the allowlist's actual runtime
  enforcement (a request to a non-public path really does get 401'd) is already covered by every
  existing integration test that successfully authenticates before calling a protected endpoint;
  this task's own gap is specifically about *regression-proofing the allowlist's declared
  contents* and *the mechanism's shape*, not proving the mechanism works at all.

## 5. Known gaps / unknowns

- **The task statement says "Update `ArchitectureTest`," but the codebase's own established
  precedent for this exact kind of check (T07's `PublicEndpointsTest`) is a separate, non-ArchUnit
  file.** ArchUnit itself has no natural way to assert "the `METHOD_SCOPED` list contains this
  specific `MethodScoped` value" (that's a data/content assertion, not a structural one) — it CAN
  express "no class other than `SecurityChainsConfig` may call `.permitAll()`" as a
  method-call-site rule, which is the actual structural half of "no new handler is permitAll
  outside the list." This split (content-assertion vs. structural rule) needs a Phase 2/4 decision
  on where each half lives; I am not deciding this now.
- **`package.md`'s own named-test table maps `shouldEnforcePublicEndpointAllowlist → L9`**, but
  L9 in `design.md` is "Token claims contract" (unrelated — that's task 34's concern). The task's
  own actual subject (public endpoint allowlist enforcement) matches **L11** ("Public endpoint
  discipline"), which is exactly what T32's own Phase 0 prompt header already cites alongside L12.
  This looks like a stale/incorrect cross-reference in `package.md` predating a LOCKED-decision
  renumbering in `design.md` — flagging per the guardrail ("if one looks wrong, STOP and log it")
  rather than silently resolving it or editing the spec file. Not a blocker: the prompt header's
  own scoping (L11, L12) is correct and is what I will follow.
- **ArchUnit and method-call-argument inspection:** I do not know, without checking ArchUnit 1.x's
  API surface in Phase 2/5, exactly which ArchUnit construct (if any) can assert "no class outside
  X calls method `permitAll()`" at the call-site level (as opposed to class-dependency level,
  which is what every existing rule in this file uses). This needs verification before committing
  to an implementation approach, not assumed to exist just because it would be convenient.
- No LOCKED decision, requirement, or existing test in this codebase requires a new HTTP-level
  integration test proving the allowlist is *behaviorally* correct (i.e., an unauthenticated call
  to every non-public endpoint returns 401) — the task's own wording is scoped to `ArchitectureTest`
  specifically, and I found no named test or AC in `package.md`/`requirements.md` asking for that
  broader behavioral sweep. Flagging so Phase 1/2 doesn't silently narrow OR silently expand scope
  relative to what's actually written.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
