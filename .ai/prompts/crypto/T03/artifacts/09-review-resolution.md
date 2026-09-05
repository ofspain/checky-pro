# crypto · T03 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review) and Phase 8 (Kimi
independent review) findings into one resolution log. Only accepted comments were applied; no
refactoring, public-API changes, or renames were made beyond what each accepted fix required.

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | Actuator `/actuator/info` and `/actuator/prometheus` unreachable — no `management.endpoints.web.exposure.include` (self-review Finding 1 / Kimi Finding 5) | **ACCEPTED** | Confirmed against `services/auth`'s identical precedent; without it those paths 404 regardless of security config, making AC4/AC6 false as shipped | Added `management.endpoints.web.exposure.include=health,info,prometheus`, `management.endpoint.health.probes.enabled=true`, `management.endpoint.health.show-details=never` to `application.properties` |
| 2 | `chain`/`enabledChains` accept any string, no closed-set check (self-review Finding 2 / Kimi Finding 6) | **ACCEPTED** | `design.md` §2 fixes ETHEREUM/TRON as the launch scope — a closed set, unlike the genuinely-open provider/screening vendor fields | Added `@Pattern(regexp = "ETHEREUM\|TRON")` to `ProviderProperties.ChainProviders.chain` and to each element of `FinalityProperties.enabledChains` |
| 3 | `ScreeningProperties.enabled=false` silently no-ops screening if `base-url` is set without the flag (self-review Finding 3 / Kimi Finding 9) | **ACCEPTED, modified** | Kimi's stronger option (flip the default to `true`) would break `local`'s required `enabled=false` boot shape (AC6); took the lighter fail-fast option instead | Added a compact-constructor check: `base-url` non-blank with `enabled=false` now throws `IllegalStateException` at bind time |
| 4 | JWT issuer not validated — only `jwk-set-uri` configured, no `issuer-uri` (Kimi Finding 3) | **ACCEPTED** | The frozen brief's own Constraints section explicitly requires "signature **+ issuer**"; confirmed Spring Boot's autoconfiguration only adds `JwtIssuerValidator` when `issuer-uri` is set — code was missing it | Added `spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_ISSUER_URI:http://localhost:8080}` to `application.properties`, matching auth-service's own confirmed issuer value (`spring.security.oauth2.authorizationserver.issuer` in `services/auth/application.properties`) |
| 5 | No check that `quorumThreshold` doesn't exceed configured provider count per chain (Kimi Finding 7) | **ACCEPTED** | Contained entirely within `ProviderProperties`; a startup-time invariant fitting this task's "validated, fail-fast configuration" purpose; no coupling to another properties class | Added a compact constructor to `ProviderProperties` that throws `IllegalStateException` if any chain's provider count is below `quorumThreshold` (null-guarded so an incomplete binding still surfaces as a clean `@NotEmpty` error instead of an NPE) |
| 6 | 401 response missing `WWW-Authenticate: Bearer` header (Kimi Finding 10) | **ACCEPTED** | RFC 6750 §3 requires it on Bearer-scheme 401s; one-line, contained fix | Added `response.setHeader("WWW-Authenticate", "Bearer")` in `problemJsonAuthenticationEntryPoint` before writing the problem body |
| 7 | Required T03 tests are entirely missing (Kimi Finding 1) | **ACKNOWLEDGED, not a Phase 9 action** | Test authorship is Phase 10 by this pipeline's own design (Phase 6's own instructions say "do NOT write tests here"); not a Phase 6 code defect to fix now | No change — carried forward as Phase 10's job |
| 8 | `anyRequest().authenticated()` is a weaker third access tier that could expose a future endpoint without the internal scope (Kimi Finding 2) | **REJECTED** | Verified this exactly mirrors `services/auth`'s own `SecurityChainsConfig.applicationChain` pattern (`PublicEndpoints` allowlist, then `anyRequest().authenticated()`) — standard secure-by-default Spring Security convention. No non-public, non-`/internal/v1/**` endpoint exists anywhere in this task's scope for the "weaker tier" to actually expose today | No change |
| 9 | Placeholder-looking values (`local-only-fake-*`) should be auto-rejected in non-local profiles (Kimi Finding 4) | **REJECTED** | This exact ask was already decided against at the Phase 4 human-approval gate (frozen brief amendment #14: "no automated placeholder-detection validation logic is built"). The frozen brief may not be renegotiated downstream | No change |
| 10 | `FinalityProperties.enabledChains` and `ProviderProperties.chains[*].chain` could drift out of sync (Kimi Finding 8) | **REJECTED, deferred** | Cross-couples two properties classes the brief scoped as independent (L15-adjacent separation); nothing in T03 itself consumes both together — real teeth only once a later task (T14 finality policies / T15-16 watchers) actually wires them up together | No change — noted here for whichever future task first consumes both `FinalityProperties` and `ProviderProperties` together |
| 11 | Hardcoded `spring.profiles.active=local` is a deployment hazard (Kimi Finding 11) | **REJECTED** | Pre-existing from T01 (not introduced or touched by T03); real-environment profile activation is a deployment-pipeline concern (`SPRING_PROFILES_ACTIVE` override in the container/K8s manifest), out of this task's scope | No change |
| 12 | `/internal/v1/**` vs `agents.md`'s literal `/internal/v1/*`, `agents.md` never updated (Kimi Finding 12) | **REJECTED** | Already resolved at the Phase 4 gate (frozen brief amendment #10: keep `**`, documented in code, treat as an `agents.md` wording nit). Its own suggested remedy (edit `agents.md`) would additionally violate the standing "never modify files under `spec/`" guardrail every phase carries | No change |
| — | `PublicEndpoints.PATTERNS` is a mutable array (self-review Finding 4) | **REJECTED (informational only)** | Exactly mirrors `services/auth`'s own identical `PublicEndpoints.PATTERNS` shape; not a regression, no instruction to revisit the established pattern | No change |

**6 accepted, 6 rejected (2 of those already-decided re-raises), 1 acknowledged-but-deferred-to-Phase-10.**

## Files changed this phase

- `services/crypto/src/main/resources/application.properties` — added `issuer-uri` and the three
  `management.*` properties (items 1, 4).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java` —
  added `@Pattern` on `chain` and a compact-constructor quorum/provider-count check (items 2, 5).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java` —
  added `@Pattern` on `enabledChains` elements (item 2).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java` —
  added the reverse-direction compact-constructor check (item 3).
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java` — added the
  `WWW-Authenticate` header (item 6).

All five files were already on the frozen brief's Files-to-Create/Modify list — no file outside that
list was touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS` after all six fixes.

No public API/class was renamed; no unrelated refactoring was performed.
