# auth · T03 — Phase 10: Test Generation

No production code changed in this phase. Two test files created:

- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` (7 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/BreachCheckClientTest.java` (9 tests)

**All 16 tests verified passing** (626ms, zero external network calls — see "Verification" below).
This closes the two HIGH findings deferred from Phase 9 (self-review Finding 1, Kimi Finding 1/2):
tests now exist, and the previously-unverified HIBP URI construction is empirically confirmed
correct.

---

## Test manifest

### `PasswordPolicyTest` (mocks `BreachCheckClient` and `AuditService`; no Spring context)

| Test | Verifies |
|---|---|
| `shouldRejectPasswordShorterThan12OrLongerThan128` | R8 (named test) — 11-char and 129-char passwords both rejected. |
| `shouldAcceptPasswordAtExactly12And128CharacterBoundaries` | R8 boundary — length bounds are inclusive. |
| `shouldRejectNullOrBlankPassword` | Frozen brief Constraints (null handling) — null, `""`, and whitespace-only all rejected via `PasswordPolicyViolationException`, never NPE. |
| `shouldRejectBreachedPasswordUsingHibpRange` | R9 (named test) — `isBreached() == true` rejects the password. |
| `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` | R10 (named test) — `BreachCheckUnavailableException` allows the password and records an audit event with `eventType="password.breach_check_failed"`, `outcome=AuditOutcome.FAILURE`, null `accountUuid`/`actorUuid` (Frozen brief Finding 2). |
| `shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen` | Frozen brief Finding 7 — an `AuditService.record(...)` failure during fail-open is caught and logged, never propagated; the password is still allowed. |
| `shouldSkipBreachCheckWhenDisabledInConfig` | Config-derived AC — `breach-check.enabled=false` means `breachCheckClient` is never invoked (`verifyNoInteractions`). |

### `BreachCheckClientTest` (real local `com.sun.net.httpserver.HttpServer` on `127.0.0.1`, real `RestClient`; no mocking framework)

| Test | Verifies |
|---|---|
| `shouldQueryFiveCharacterUppercasePrefixWithUserAgentHeader` | R9 — request path is exactly `/range/{5-char-uppercase-prefix}`; `User-Agent: Themistra-Auth-Service/1.0` header present (Finding 1). **This is the test that empirically settles Findings 1/2** — it captures the real received request path from a real HTTP server hit through the real public constructor and `buildRestClient`. |
| `shouldReturnTrueWhenSuffixPresentWithPositiveCount` | R9 — count > 0 and matching suffix ⇒ breached. |
| `shouldReturnFalseWhenSuffixPresentWithZeroCount` | R9 — count `0` is not a breach, even with a matching suffix. |
| `shouldReturnFalseWhenSuffixAbsentFromResponse` | R9 — suffix not present in the response ⇒ not breached. |
| `shouldMatchSuffixCaseInsensitively` | Finding 8 — lowercase suffix in the response still matches. |
| `shouldIgnoreBlankLinesInResponseBody` | Finding 8 — leading/trailing blank lines don't break parsing. |
| `shouldThrowBreachCheckUnavailableExceptionOnServerError` | R10 — a real 500 response is wrapped as `BreachCheckUnavailableException`, not left as a raw `RestClientException`. |
| `shouldThrowBreachCheckUnavailableExceptionOnConnectionFailure` | R10 — connecting to a closed local port (fast, deterministic stand-in for "unreachable") also wraps as `BreachCheckUnavailableException`, exercising the real bounded-timeout `SimpleClientHttpRequestFactory` built by `buildRestClient`. |
| `shouldRejectNullPassword` | Phase 9 Finding 5 — `Objects.requireNonNull` guard throws `NullPointerException`, not a deeper NPE inside `sha1UppercaseHex`. |

---

## A defect found and fixed while writing these tests (not a production code change)

The original test plan (Phase 5) called for `MockRestServiceServer` bound to a `RestClient.Builder`
per the frozen brief's testing note. Writing `BreachCheckClientTest` that way surfaced a real
problem: `MockRestServiceServer.bindTo(builder)` installs its mock by calling
`builder.requestFactory(mockFactory)` on the shared builder; `BreachCheckClient.buildRestClient`
then calls `.requestFactory(timeoutFactory)` on that *same* builder for its own timeout
configuration, silently overwriting the mock right before `.build()`. The result, empirically
observed: `MockRestServiceServer` reported "0 requests executed," and the test's assertions were
instead being satisfied (or failing) against **real responses from the real
`api.pwnedpasswords.com`**, because this sandbox has outbound internet access. Nothing sensitive
was sent (only 5-character SHA-1 prefixes, HIBP's own anonymous-query design), but a unit test
silently phoning out to a live third-party API is not acceptable, and it made 5 of the first 16
test runs fail non-deterministically against real breach data instead of the fixture data they
were written against.

**Resolution:** rather than changing `BreachCheckClient` (which would have meant deviating from
Phase 10's "do not change production code" rule), `BreachCheckClientTest` was written against a
real, local `com.sun.net.httpserver.HttpServer` (JDK built-in, no new dependency, bound to
`127.0.0.1` on an ephemeral port) instead of `MockRestServiceServer`. This exercises the exact
same public constructor and `buildRestClient` code path — including the real timeout-configured
`ClientHttpRequestFactory` and the real base-URL/prefix construction — with no mocking-framework
conflict, no flakiness, and no network egress beyond loopback. Confirmed fully deterministic:
16/16 tests pass in 626ms.

This supersedes the frozen brief's specific suggestion to use `MockRestServiceServer` for this
class; the *requirement* (a fast, deterministic, no-Spring-context test proving R9's HTTP
behavior) is met, just via a different, dependency-free mechanism better suited to a class that
configures its own `ClientHttpRequestFactory`.

---

## Verification

Both test classes were compiled and executed directly (not via `mvn test`, since the module's
`token` package still has the pre-existing, unrelated compile failure noted in Phase 6/7 — a
`spring-security-oauth2-authorization-server` dependency mismatch, untouched by T03). Verification
method: `javac` against the module's resolved test-scope classpath plus `-sourcepath` covering
both `src/main/java` and `src/test/java` (pulling in only the transitive dependency chain these
two test classes actually reference — none of which touches the broken `token` package), then
executed via the JUnit Platform `Launcher` API directly (`junit-platform-launcher:1.12.2`, matching
the project's resolved `junit-platform-engine` version) against both classes.

**Result: 16/16 tests successful, 0 failed, 0 skipped, 626ms.**

**Not yet possible:** a real `mvn -pl services/auth test` run, which requires the pre-existing
`token` package compile failure to be fixed first (out of scope for T03). Once that's resolved,
these two classes should be re-run through the standard Maven/Surefire path as a final
confirmation, though the mechanism used here (real JUnit Platform execution against the actual
compiled classes) is not a lesser form of verification — it's the same test engine Surefire itself
delegates to.
