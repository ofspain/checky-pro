# auth · T03 — Phase 5: Implementation Plan

Plans against `artifacts/04-frozen-task-brief.md` only. Every file below traces to that brief's
Files to Create / Files to Modify sections — nothing added or renamed.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`
2. `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java`
3. `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java`
4. `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java`
5. `services/auth/src/test/java/com/themistra/auth/authn/BreachCheckClientTest.java`

No new top-level exception class files: the frozen brief's Files-to-Create list does not authorize
one, so `PasswordPolicyViolationException` (in `PasswordPolicy`) and
`BreachCheckUnavailableException` (in `BreachCheckClient`) are `public static` nested classes
inside the two files above, not separate files.

## Files to modify

1. `services/auth/src/main/resources/application.properties` — append:
   ```properties
   themistra.auth.password.min-length=12
   themistra.auth.password.max-length=128
   themistra.auth.password.breach-check.enabled=true
   themistra.auth.password.breach-check.url-prefix=https://api.pwnedpasswords.com/range/
   themistra.auth.password.breach-check.timeout-ms=3000
   ```

## Public methods (signatures)

**`PasswordPolicyProperties`** (record, `@ConfigurationProperties(prefix = "themistra.auth.password")`, `@Validated`):
```java
record PasswordPolicyProperties(
        @Min(1) int minLength,
        @Min(1) int maxLength,
        @Valid @NotNull BreachCheck breachCheck)

record BreachCheck(
        boolean enabled,
        @NotBlank String urlPrefix,
        @Positive long timeoutMs)
```
Nested-record shape mirrors `AuthClientsProperties`/`SigningKeysProperties`. Relaxed binding maps
`breach-check.*` → `breachCheck` automatically; no manual defaulting needed since all three
sub-fields are always present in `application.properties`.

**`BreachCheckClient`** (`@Component`, package `authn`, must be `public` — `PasswordPolicy` in
`account` calls it):
```java
public BreachCheckClient(RestClient.Builder restClientBuilder, PasswordPolicyProperties properties)

public boolean isBreached(String rawPassword) throws BreachCheckUnavailableException

public static class BreachCheckUnavailableException extends RuntimeException {
    public BreachCheckUnavailableException(String message, Throwable cause)
}
```
Constructor takes the Spring Boot auto-configured `RestClient.Builder` (from
`RestClientAutoConfiguration`, already on the classpath via `spring-boot-starter-web`) rather than
building a `RestClient` internally — this is also what lets `BreachCheckClientTest` bind
`MockRestServiceServer` to the same builder instance.

**`PasswordPolicy`** (`@Service`, package `account`):
```java
public PasswordPolicy(PasswordPolicyProperties properties, BreachCheckClient breachCheckClient,
                       AuditService auditService)

public void validate(String rawPassword) throws PasswordPolicyViolationException

public static class PasswordPolicyViolationException extends RuntimeException {
    public PasswordPolicyViolationException(String message)
}
```
`validate` is the sole public entry point (matches Outputs in the frozen brief: accept silently /
reject via one exception type for null, length, and breach violations).

## Private methods

**`BreachCheckClient`:**
- `private RestClient buildRestClient(RestClient.Builder builder, PasswordPolicyProperties properties)`
  — sets `baseUrl(properties.breachCheck().urlPrefix())`, a default `User-Agent:
  Themistra-Auth-Service/1.0` header (Finding 1), and a `ClientHttpRequestFactory` configured with
  `properties.breachCheck().timeoutMs()` as both connect and read timeout (Finding 6). Called once
  from the constructor; the built `RestClient` is stored in a final field.
- `private String sha1UppercaseHex(String rawPassword)` — SHA-1 digest, uppercase hex. Lives here,
  not in `common.Hashing` (Finding 5).
- `private boolean responseContainsSuffix(String responseBody, String suffix)` — parses
  newline-delimited `SUFFIX:COUNT` lines; skips blank/whitespace lines; compares suffix
  case-insensitively (both sides uppercased); treats a line with a non-positive-integer count as
  non-breach; returns `true` only on an exact suffix match with count > 0 (Finding 8).

**`PasswordPolicy`:**
- `private void validateLength(String rawPassword)` — throws `PasswordPolicyViolationException` if
  `< minLength` or `> maxLength` (R8).
- `private void validateNotBreached(String rawPassword)` — no-ops if
  `!properties.breachCheck().enabled()`; otherwise calls `breachCheckClient.isBreached(...)`,
  throwing `PasswordPolicyViolationException` if breached, and catching
  `BreachCheckClient.BreachCheckUnavailableException` to invoke the fail-open path instead of
  propagating (R9/R10).
- `private void recordBreachCheckFailedAudit()` — calls:
  ```java
  auditService.record(new RecordAuditEventRequest(
          "password.breach_check_failed", AuditOutcome.FAILURE,
          null, null, null, null, null, null));
  ```
  wrapped in its own `try/catch (Exception e)` that only logs (SLF4J, `WARN`) — never rethrows, so
  an audit-write failure cannot block the password from being allowed (Finding 7, frozen brief
  State Changes).

## Entities used

None. T03 introduces no JPA entity and touches no table.

## Repositories used

None.

## Services used

- `com.themistra.auth.audit.AuditService` (existing, injected into `PasswordPolicy`) —
  `record(RecordAuditEventRequest)` only.
- `com.themistra.auth.authn.BreachCheckClient` (new, this task, injected into `PasswordPolicy`).

## Unit / integration tests required

All unit-only (plain JUnit 5 + Mockito/AssertJ, no Spring context, per `agents.md`); no
integration/Testcontainers test is authorized by the frozen brief.

**`PasswordPolicyTest`** (mocks `BreachCheckClient`, `AuditService`; real
`PasswordPolicyProperties` instance, no `Clock` needed):
- `shouldRejectPasswordShorterThan12OrLongerThan128` — named test; parameterized over
  length 0/11/12/128/129/200.
- `shouldRejectBreachedPasswordUsingHibpRange` — named test; `breachCheckClient.isBreached(...)`
  stubbed `true`.
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` — named test;
  `breachCheckClient.isBreached(...)` stubbed to throw `BreachCheckUnavailableException`; assert
  `validate(...)` does not throw and `auditService.record(...)` was called once with
  `eventType="password.breach_check_failed"`, `outcome=AuditOutcome.FAILURE`, null
  account/actorUuid.
- Null/blank password rejected via `PasswordPolicyViolationException`, not NPE.
- `breach-check.enabled=false` → `breachCheckClient` never invoked.
- `auditService.record(...)` throwing during the fail-open path is caught; `validate(...)` still
  does not throw (Finding 7).

**`BreachCheckClientTest`** (real `RestClient.Builder` bound to
`MockRestServiceServer.bindTo(builder)`; real `PasswordPolicyProperties`):
- Request assertions: path is `{url-prefix}` + 5-character uppercase SHA-1 prefix;
  `User-Agent: Themistra-Auth-Service/1.0` header present (Finding 1).
- Response `SUFFIX:COUNT` with matching suffix, count > 0 → `isBreached` returns `true`.
- Response containing the suffix with count `0` → `isBreached` returns `false` (not breached).
- Response suffix present in a different case → still matches (case-insensitive compare).
- Trailing blank line in response body → ignored, does not affect the result.
- Simulated server error / timeout beyond `timeout-ms` → `isBreached` throws
  `BreachCheckUnavailableException`, not a raw `RestClientException` leaking to the caller.

## Execution order

1. `application.properties` — add the config block (front-loaded, everything else binds against it).
2. `PasswordPolicyProperties.java` — config binding, no dependencies on other new classes.
3. `BreachCheckClient.java` + `BreachCheckClientTest.java` — the HTTP client `PasswordPolicy`
   depends on; build and test it standalone first.
4. `PasswordPolicy.java` + `PasswordPolicyTest.java` — depends on steps 2 and 3 plus the existing
   `AuditService`.
5. `mvn -pl services/auth test` — full module unit-test run to confirm the three named tests and
   all boundary/edge-case tests pass before moving to Phase 6.
