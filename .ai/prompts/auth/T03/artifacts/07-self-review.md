# auth · T03 — Phase 7: Self-Review

Findings only, against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No fixes
applied here — Phase 9 handles remediation after independent review (Phase 8).

---

## Finding 1 — HIBP request URI resolution is unverified and could silently defeat R9 (HIGH)

**Issue:** `BreachCheckClient` sets `baseUrl` to `themistra.auth.password.breach-check.url-prefix`
(`https://api.pwnedpasswords.com/range/`, trailing slash) and issues the request via
`.uri("{prefix}", prefix)` with no leading slash, relying on `RestClient`'s
`DefaultUriBuilderFactory` to merge the two into `.../range/ABCDE`. This merge behavior was never
exercised — Phase 6 explicitly deferred unit tests to Phase 10, and `mvn test` cannot currently run
at all (unrelated pre-existing `token` module compile failure, per `06-implementation-notes.md`).
If the actual merge produces a double slash, drops the `range/` segment, or otherwise resolves
incorrectly, every real HIBP call fails — which `isBreached` reports as
`BreachCheckUnavailableException`, which `PasswordPolicy` interprets as R10's fail-open condition.
Every password would silently pass breach screening, permanently and without any visible error.

**Severity:** HIGH — the failure mode is silent (no exception surfaces to callers, no failed test,
no log above `WARN`) and defeats the entire purpose of R9/L2's breach-screening requirement.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java:47`
(`.uri("{prefix}", prefix)`), `:63` (`.baseUrl(properties.breachCheck().urlPrefix())`).

**Recommendation:** Before this can be considered verified, Phase 10 must include a test asserting
the exact resolved request path (e.g. `MockRestServiceServer`'s
`.andExpect(requestTo("https://api.pwnedpasswords.com/range/ABCDE"))`) against the real configured
`url-prefix` value, not just that *a* request was made.

---

## Finding 2 — No cross-field validation that `minLength <= maxLength` (MEDIUM)

**Issue:** `PasswordPolicyProperties` validates `minLength` and `maxLength` independently
(`@Min(1)` each) but nothing enforces `minLength <= maxLength`. A misconfiguration (e.g. an
operator transposing the two values) would make `validateLength` reject every password
unconditionally (no length satisfies both `>= minLength` and `<= maxLength`), silently locking out
all registration/password-change flows once wired in task 9 — and nothing at startup would catch
it, unlike the "fail boot on invalid config" pattern this record's own javadoc claims to follow.

**Severity:** MEDIUM — plausible operator error, not a code-path bug, but the config record's
stated purpose (fail fast on bad config) doesn't actually cover this case.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java:21-23`.

**Recommendation:** Add a class-level constraint (e.g. a `@AssertTrue`-annotated derived method, or
a compact-constructor check throwing `IllegalArgumentException`) asserting `minLength <= maxLength`.

---

## Finding 3 — `account` and `authn` now have a two-way package dependency (MEDIUM)

**Issue:** `account.PasswordPolicy` depends on `authn.BreachCheckClient` (per `design.md` §6's
package map), and `authn.BreachCheckClient`'s constructor depends on
`account.PasswordPolicyProperties` (for `urlPrefix`/`timeoutMs`). That's a dependency in both
directions between the same two packages — a package-level cycle. No existing `ArchitectureTest`
rule catches this today, but it sits against the module-boundary discipline `agents.md` states
generally ("Each module owns its entities, repositories, services, and API... Shared plumbing
lives only in `common`"). This was effectively locked in by the frozen brief/plan's constructor
signature (Phase 4/5), not introduced as a new decision here — flagging for visibility since Phase
7 is the first point this gets evaluated against "module boundaries" explicitly.

**Severity:** MEDIUM — not a build failure, but a real architectural smell that could bite if a
future `ArchitectureTest` rule adds a "modules must be free of cycles" check (a common ArchUnit
pattern), which would then fail on this pairing specifically.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java:3`
(`import com.themistra.auth.account.PasswordPolicyProperties;`),
`services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java:6`
(`import com.themistra.auth.authn.BreachCheckClient;`).

**Recommendation:** No action required to satisfy T03's acceptance criteria (already
brief-authorized). Worth a note to the author for awareness; a future cleanup could hoist the
breach-check-specific config into its own leaf record `authn` owns directly, leaving `account`
only the length bounds.

---

## Finding 4 — `BreachCheckClient.isBreached` has no null-guard of its own (LOW)

**Issue:** `isBreached(String rawPassword)` is a `public` method on a Spring-managed `@Component`.
Its only current caller (`PasswordPolicy.validate`) null-checks first, but the method itself will
NPE inside `sha1UppercaseHex` (`rawPassword.getBytes(...)`) rather than fail with an intentional,
descriptive error if any future caller invokes it directly with `null`.

**Severity:** LOW — no current caller triggers this; purely a defensive-boundary gap on a public
API surface.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java:40-41`.

**Recommendation:** Add an explicit guard (`Objects.requireNonNull` or an `IllegalArgumentException`)
at the top of `isBreached`.

---

## Finding 5 — Non-2xx-throws behavior relies on an implicit `RestClient` default (LOW)

**Issue:** `isBreached` calls `.retrieve().body(String.class)` with no explicit `.onStatus(...)`
handler, relying on `RestClient`'s default behavior (any 4xx/5xx throws
`RestClientResponseException`, a `RestClientException` subtype, caught by the surrounding
`catch (RestClientException e)`). This is correct, but not self-evident from reading the method —
a reader unfamiliar with `RestClient`'s defaults could reasonably assume error statuses fall
through to `responseContainsSuffix` with a non-null body.

**Severity:** LOW — readability only; behavior is correct.

**Evidence:** `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java:45-53`.

**Recommendation:** A one-line comment noting that `.retrieve()` throws on non-2xx by default
(caught below) would make the control flow legible without needing to know `RestClient` internals.

---

## Dimensions checked with no findings

Boundary conditions (length inclusivity, breach-count-zero, blank-line/case-insensitive parsing),
thread-safety (all three classes are stateless singletons), transaction boundaries (`PasswordPolicy`
correctly has none; `AuditService.record`'s own `@Transactional` is relied on and its failure is
caught, not propagated, per Finding 7 from Phase 3), idempotency (no event publication, no
consumer, N/A), money types (N/A — no monetary values in this task), and
enumeration-safety/secret-handling (only the 5-character hash prefix ever leaves the process; raw
password, full hash, and breach suffix are never logged) were reviewed and found consistent with
the frozen brief and `agents.md`.
