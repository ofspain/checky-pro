# auth · T03 — Phase 6: Implementation Notes

## What changed

**Created:**
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java` —
  validated `@ConfigurationProperties(prefix = "themistra.auth.password")` record: `minLength`,
  `maxLength`, nested `BreachCheck(enabled, urlPrefix, timeoutMs)`.
- `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java` — `@Component`
  wrapping a `RestClient` built from the injected `RestClient.Builder`. `isBreached(String)`
  computes an uppercase SHA-1 hex digest locally, queries `{urlPrefix}{first-5-chars}` with a
  static `User-Agent: Themistra-Auth-Service/1.0` header and a `SimpleClientHttpRequestFactory`
  bounded to `timeoutMs` for both connect and read timeouts, and parses the
  `SUFFIX:COUNT`-per-line response (blank lines skipped, suffix compared case-insensitively,
  non-positive-integer counts treated as non-breach). Any `RestClientException` (timeout, non-2xx,
  connection failure) is wrapped and rethrown as the nested
  `BreachCheckClient.BreachCheckUnavailableException`.
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java` — `@Service` with
  the single public entry point `validate(String rawPassword)`. Rejects null/blank and
  out-of-bounds-length passwords via the nested `PasswordPolicy.PasswordPolicyViolationException`.
  When breach-check is enabled, delegates to `BreachCheckClient`; on
  `BreachCheckUnavailableException` it fails open and calls `recordBreachCheckFailedAudit()`,
  which itself wraps `AuditService.record(...)` in a try/catch that only logs — an audit-write
  failure can never block the password from being allowed.

**Modified:**
- `services/auth/src/main/resources/application.properties` — appended the password-policy block
  (including `breach-check.timeout-ms=3000`, the Finding-6 addition) verbatim as planned, directly
  above the `# --- Server ---` section.

No other files were touched.

## Mapping to the plan

Matches `artifacts/05-implementation-plan.md` exactly:
- File list, public/private method signatures, and the two nested exception classes are as
  planned — no new top-level files were added.
- `BreachCheckClient` takes the injected `RestClient.Builder` (not a self-built `RestClient`), as
  planned, so `BreachCheckClientTest` (Phase 10) can bind `MockRestServiceServer` to it.
- Execution order followed: config → `PasswordPolicyProperties` → `BreachCheckClient` →
  `PasswordPolicy`.

## Mapping to acceptance criteria (frozen brief §Acceptance Criteria)

- **R8** (length): `validateLength` rejects `< minLength` or `> maxLength`; bounds are
  config-driven, not hardcoded, so 12/128-inclusive behavior comes from `application.properties`.
- **R9** (breach): `isBreached` sends only the 5-character uppercase prefix; a match requires exact
  suffix equality (case-insensitive) AND count > 0; a present-with-count-0 line is not a match.
  `User-Agent` header set on every request (Finding 1).
- **R10** (fail-open + audit): `BreachCheckUnavailableException` triggers
  `recordBreachCheckFailedAudit()`, not a thrown violation — the password is allowed. Audit event
  uses `AuditOutcome.FAILURE` with null `accountUuid`/`actorUuid` (Finding 2, frozen brief State
  Changes — no authenticated context exists at this layer in T03). Audit-write failure is caught
  and logged only (Finding 7).
- **Config-derived** (`breach-check.enabled=false`): `validateNotBreached` returns immediately
  without calling `breachCheckClient.isBreached(...)`.
- **Parsing** (Finding 8): implemented in `responseContainsSuffix` exactly as specified — blank
  lines skipped, case-insensitive suffix compare, non-positive-integer counts skipped.
- **Null/blank** (Finding 9): handled in `validate(...)` before any other check, via the same
  `PasswordPolicyViolationException` type used for length violations.

## Deviations from the plan

None. Implementation matches the plan's signatures and behavior exactly.

## Notes on things encountered while implementing (not deviations, but worth recording)

- **Timeout mechanism:** the plan didn't pin down which `ClientHttpRequestFactory` to use for the
  bounded timeout. Used `org.springframework.http.client.SimpleClientHttpRequestFactory`
  (`setConnectTimeout`/`setReadTimeout`, both in ms) rather than Spring Boot 3.4+'s newer
  `ClientHttpRequestFactoryBuilder`/`ClientHttpRequestFactorySettings` API — it's simpler, has been
  stable across Spring Framework versions for years, and avoids depending on a less-familiar newer
  API for a single bounded HTTP call. Functionally equivalent for this use case.
- **URI construction:** `RestClient.Builder.baseUrl(...)` is set to
  `themistra.auth.password.breach-check.url-prefix` (`.../range/`, trailing slash). The request
  uses `.uri("{prefix}", prefix)` — no leading slash — so it resolves to `.../range/ABCDE` rather
  than a double-slash `.../range//ABCDE`.
- **Cross-module dependency confirmed, not new:** `authn.BreachCheckClient`'s constructor takes
  `account.PasswordPolicyProperties` (for `urlPrefix`/`timeoutMs`), meaning `authn` imports a class
  from `account`. This was already fixed by the frozen brief/plan (constructor signature was
  specified there), not a decision made in this phase. No existing `ArchitectureTest` rule
  forbids it — the entity-isolation rule only covers the `Account` entity itself, not
  `PasswordPolicyProperties`.

## Build verification

`mvn -pl services/auth -am compile` currently fails — but on **pre-existing, unrelated** code:
`token/ReuseDetectingAuthorizationService.java` and `token/SecurityChainsConfig.java` fail with
"cannot find symbol" on `OAuth2TokenType` and `JwtAuthenticationConverter` (a
`spring-security-oauth2-authorization-server` dependency/version mismatch). Confirmed via `git log`
that these files are unmodified since 2026-07-13 and untouched this session — this is not caused
by T03 and is out of scope to fix here (guardrail: no unrelated refactoring, no scope beyond this
task).

To verify the three new files independently or the broken module state, `javac` was run directly
against `PasswordPolicy.java`, `PasswordPolicyProperties.java`, and `BreachCheckClient.java` with
`-sourcepath services/auth/src/main/java` and the module's resolved dependency classpath (via
`mvn dependency:build-classpath`). This pulls in only the transitive dependency chain these three
files actually reference (`audit.*`, `events.OutboxPublisher`, `common.Hashing` — none of which
touch the broken `token` package) and **compiled with zero errors**.

**Not run in this phase (Phase 10 scope, per guardrails: "Do NOT write tests here"):** unit tests
for `PasswordPolicy`/`BreachCheckClient`, and therefore `mvn test`. The pre-existing `token`
compile failure will also need to be resolved (by someone, outside T03) before the full module
test suite can run at all.
