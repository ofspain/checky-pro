# crypto · T03 · Phase 7 — Self Review

Reviewed the Phase 6 diff (`common/config/*Properties.java`, `common/PublicEndpoints.java`,
`common/ResourceServerConfig.java`, `CryptoServiceApplication.java`, `application.properties`)
against the frozen brief and `agents.md`. No rewrites performed — findings only, fixes are Phase 9.

---

## Finding 1 — Two of the four declared-public actuator paths are unreachable without `management.endpoints.web.exposure.include`

**Issue:** `PublicEndpoints.PATTERNS` declares `/actuator/info` and `/actuator/prometheus` as public
(AC4), but Spring Boot Actuator only exposes the `health` endpoint over HTTP by default. Without
`management.endpoints.web.exposure.include`, a request to either path 404s before security is even
the deciding factor — being `permitAll` doesn't make an endpoint exist. `/actuator/health/liveness`
and `/actuator/health/readiness` (covered by the `/actuator/health/**` pattern) have the same problem
via a different property (`management.endpoint.health.probes.enabled`), though the base
`/actuator/health` path itself is unaffected (exposed by default).

**Severity:** High — AC4 ("actuator... remains reachable") and AC6 (`local` boots and is usable) are
not actually true as shipped; this will fail the first real request to either path.

**Evidence:**
- `services/crypto/src/main/resources/application.properties` — no `management.*` property exists
  anywhere in the file (confirmed via direct grep of the file as written this phase).
- `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java:16-20` — declares
  all four paths public, including `/actuator/info` and `/actuator/prometheus`.
- `services/auth/src/main/resources/application.properties:113-115` — auth's own precedent sets
  exactly `management.endpoints.web.exposure.include=health,info,prometheus` and
  `management.endpoint.health.probes.enabled=true` for the identical reason; crypto's
  `application.properties` (modified this phase) never added the equivalent.

**Recommendation:** Add `management.endpoints.web.exposure.include=health,info,prometheus` and
`management.endpoint.health.probes.enabled=true` to `application.properties`, mirroring auth's
precedent exactly. This file is already on the frozen brief's Files-to-Modify list, so the fix stays
in scope.

---

## Finding 2 — `chain`/`enabledChains` string fields accept any value; no closed-set check against the two in-scope chains

**Issue:** `ProviderProperties.ChainProviders.chain` and `FinalityProperties.enabledChains` are plain
`@NotBlank String`/`List<@NotBlank String>` with no constraint on the actual value. Unlike the
provider-vendor and screening-vendor fields (genuinely open per Q1/Q2, correctly left unconstrained),
`design.md` §2 fixes the in-scope chain set for this spec explicitly ("In scope (launch: Tron +
Ethereum)") — a closed, known set this task could validate against without pre-empting anything
unresolved.

**Severity:** Medium — a typo (e.g. `"ETHERUM"`) binds successfully as a "valid" config value in any
profile, including non-local, silently producing a dead/no-op chain entry instead of the fail-fast
behavior L13 calls for elsewhere in this same task.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java:24-27`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java:18-19`
- `spec/crypto-service/package.md` §2 (via the TL;DR/scope section of `package.md`/`design.md`):
  launch scope is exactly `ETHEREUM` and `TRON`.

**Recommendation:** Add `@Pattern(regexp = "ETHEREUM|TRON")` (or equivalent) to `chain` and to each
element of `enabledChains`, scoped to the two launch chains `design.md` §2 already fixes — this
doesn't require resolving Q1/Q2 and doesn't lock in a vendor choice, only the chain identifiers
themselves.

---

## Finding 3 — `ScreeningProperties.enabled` has no fail-fast path if `base-url`/`api-key-secret-name` are set without flipping the flag

**Issue:** `enabled` is a primitive `boolean` with no explicit requirement that it be set. If a
non-local deployment supplies `base-url`/`api-key-secret-name` but omits (or misspells)
`themistra.crypto.screening.enabled=true`, Spring's relaxed binder silently defaults the primitive to
`false` rather than failing — screening quietly stays off instead of the misconfiguration surfacing at
boot.

**Severity:** Low — `enabled=false` is never itself an "invalid" value by L13's own wording, so this
is a boundary-condition gap rather than a clear rule violation; the actual `ScreeningClient`'s
fail-closed behavior (L12, a later task) is the real backstop against a genuinely unreachable
screening vendor at attest time.

**Evidence:** `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java:16-23`.

**Recommendation:** Judgment call for the team, not asserting a required fix — options include a
cross-field check requiring `enabled` to be explicit whenever `base-url` is non-blank, or accepting
this as acceptable given the downstream fail-closed backstop.

---

## Finding 4 — `PublicEndpoints.PATTERNS` is a mutable array (informational, no action recommended)

**Issue:** `public static final String[] PATTERNS` makes the field reference immutable but not its
contents — any caller in the same JVM could mutate an element at runtime.

**Severity:** Low/informational.

**Evidence:** `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java:16-20`.
This exactly mirrors `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java`'s
own `PATTERNS` field shape (also a plain mutable array) — not a regression introduced by this task.

**Recommendation:** No action recommended here — changing only crypto's copy to `List.of(...)` would
diverge from the established, identical auth precedent without instruction to revisit it there too.
Noted for completeness per this phase's own "readability, complexity" review criteria, not because a
fix is warranted in this task.

---

## Not flagged (checked and found correct)

- Default `JwtGrantedAuthoritiesConverter` behavior against a JSON-array `scope` claim — verified
  against Spring Security's own source behavior (checks the `scope` claim first; if it's a
  `Collection`, as `contracts/api/token-claims.md` Path 2 specifies, it's used directly with no
  space-split needed) — `hasAuthority("SCOPE_internal.crypto:write")` is correct as written, no bug.
- Nested list cascading validation (`@Valid` on `List<ChainProviders>`/`List<ProviderEntry>`) —
  supported by Spring Boot 3.x's `@ConfigurationProperties` record binding; no issue.
- Module boundaries (L15), money types, transactions, idempotency, thread-safety — not applicable to
  this task's pure config/security scope; nothing to flag.
- `mvn -pl services/crypto -am compile` and `test-compile` both clean, no warnings.
