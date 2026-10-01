<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T16 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T16 — ArchUnit module-boundary tests |
| **Spec section** | L2, L8, L11 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + sibling precedents + current package/entity layout |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the T16 brief before implementation.

---

## Finding 1 · `FEATURE_MODULES` must exactly match the current top-level package layout

**Challenge:** The notification service currently has six feature modules under `com.themistra.notification`: `channel`, `consumer`, `delivery`, `inapp`, `preference`, `template`. `common` is shared plumbing and must be excluded. If `FEATURE_MODULES` omits one of the six, every `@Entity` in that module will fail-fast as "outside every known module." If it includes a package that does not exist (e.g., `events`), the rule still works but is stale documentation.

**Evidence:**
- `services/notification/src/main/java/com/themistra/notification/` direct subdirectories: `channel`, `common`, `consumer`, `delivery`, `inapp`, `preference`, `template`.
- Seven `@Entity` classes distributed across `delivery` (2), `consumer` (1), `template` (1), `inapp` (1), `preference` (2).

**Resolution:** `FEATURE_MODULES = List.of("channel", "consumer", "delivery", "inapp", "preference", "template")`. `common` is deliberately excluded; a `common`-package class importing any feature-module entity is correctly flagged because its `featureModuleOf` returns `null`. Add a comment instructing future developers to update the list when a new top-level package is added.

---

## Finding 2 · The L2 rule is broader than the literal wording of L2

**Challenge:** L2 says "no synchronous cross-service call on the delivery path." The brief scopes the rule to the whole service and bans all synchronous HTTP client packages (`RestTemplate`, `WebClient`, `HttpClient`, Apache HTTP). This also forbids using those clients for external non-platform calls, even though the service's only external call (AWS SES) is allowed.

**Evidence:**
- The brief explicitly lists `org.springframework.web.client..`, `org.springframework.web.reactive.function.client..`, `java.net.http..`, `org.apache.http..` as banned.
- AWS SES is permitted because it is called through the AWS SDK, not a raw HTTP client.

**Resolution:** Accept the broader ban. The service has no legitimate use for raw synchronous HTTP clients today (AWS SDK is the only external integration), and the broader rule is simpler to enforce and review than trying to distinguish "external" from "sibling" at the import level. Document that any future external HTTP integration must use an allowed asynchronous/non-blocking client or be explicitly allowlisted.

---

## Finding 3 · The L2 ban list may miss future synchronous HTTP clients

**Challenge:** The brief lists four HTTP-client package trees. Libraries such as OkHttp (`okhttp3..`) or Feign (`org.springframework.cloud.openfeign..`) are not listed. A future developer could add one and evade the rule.

**Evidence:**
- No current dependency on OkHttp or Feign exists in `services/notification/pom.xml`.
- `agents.md` does not enumerate permitted HTTP clients.

**Resolution:** Keep the finite list from the brief (mirroring the established precedent rather than inventing a longer, unvalidated list). Treat any new HTTP client as a design-review gate. Optionally add a code comment listing the covered packages and stating that additions require an explicit decision.

---

## Finding 4 · No negative proof is possible for the sibling-service-package half of L2

**Challenge:** The rule bans dependencies on `com.themistra.auth..`, `com.themistra.crypto..`, and `com.themistra.payment..`. `services/notification/pom.xml` has no test/main dependency on those service modules (consistent with `agents.md`: services depend only on `libs/` and `contracts/`). Therefore no compiling test fixture can import a class from those packages.

**Evidence:**
- `grep` of `services/notification/pom.xml` and source imports shows no `com.themistra.auth|crypto|payment` references.
- The brief already discloses this limitation and scopes it out.

**Resolution:** Accept the disclosed limitation. The HTTP-client half (`RogueHttpClientUser`) provides a genuine negative proof that the same rule mechanism works. Document in the artifact that the sibling-package half is structural-only and enforced by the same rule as the HTTP-client half.

---

## Finding 5 · L8 rule mirrors auth exactly and relies on a Spring Security internal API

**Challenge:** The rule targets `AuthorizeHttpRequestsConfigurer.AuthorizedUrl.permitAll`. This is an internal Spring Security class/method. A future Spring Security upgrade could change the class hierarchy or method signature, causing the ArchUnit rule to silently stop matching anything.

**Evidence:**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` uses the same target and is already accepted as precedent.
- `ResourceServerConfig.java` currently calls `auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll()`.

**Resolution:** Accept the precedent. The rule is intentionally narrow and aligns with the existing codebase. If a Spring Security upgrade breaks it, the canary test will fail because the rule no longer matches the real `permitAll` call, surfacing the need for an update. No negative-proof test is added, consistent with auth's own precedent.

---

## Finding 6 · `@ArchTest` fields alone do not execute under Surefire

**Challenge:** This monorepo's Maven Surefire configuration does not run ArchUnit's JUnit 5 engine for `@ArchTest` fields. Every rule needs a real `@Test` canary that imports the same classes and invokes `rule.check(analyzedClasses)`.

**Evidence:**
- Both `services/auth` and `services/crypto` document this finding and use `@Test` canaries.
- A real negative proof in auth confirmed that a deliberately introduced violation did not fail `mvn test` via `@ArchTest` alone.

**Resolution:** Provide one `@Test` canary per rule in `ArchitectureTest.java`. Use a single, eagerly-initialized `JavaClasses` instance imported with the same package and `ImportOption` as `@AnalyzeClasses`, so the canary and the annotation can never drift.

---

## Finding 7 · Test fixtures must not pollute the main scan

**Challenge:** `RogueChannelEntityReferencer` lives inside `channel` (a real feature module) and deliberately imports `DeliveryLog` from `delivery`. If the main `JavaClasses` scan includes test sources, this fixture will cause the real L11 canary to fail.

**Evidence:**
- The fixture is intentionally placed in `src/test/java/com/themistra/notification/channel/`.
- The negative-proof test imports it via a separate, narrow `ClassFileImporter`.

**Resolution:** Use `ImportOption.DoNotIncludeTests` for both `@AnalyzeClasses` and the canary's `ClassFileImporter`, exactly as auth and crypto do. Negative-proof tests explicitly import only the fixture classes they need.

---

## Finding 8 · No pre-existing cross-module entity violations are expected, but must be confirmed

**Challenge:** The brief states Phase 0 expects zero violations, but the rule cannot be assumed clean until it actually runs. If a violation exists, it must be either fixed or explicitly allowlisted with a staleness-guard test (AC3).

**Evidence:**
- A `grep` for direct imports of entity classes across feature modules found none.
- Cross-module imports of services/utilities exist (e.g., `channel.EmailChannel` imports `template.TemplateRenderer`; `delivery.DeliveryOrchestrator` imports `channel.NotificationChannel`, `preference.PreferenceResolver`, `template.TemplateRenderer`), but these are not entity imports and are permitted by L11.

**Resolution:** Proceed assuming clean. If the first canary run fails, evaluate the violation: fix it if cheap/in-scope, otherwise add a narrow, named, justified allowlist entry and a staleness-guard test. Do not add speculative allowlists.

---

## Decisions Made

1. **Single combined `ArchitectureTest.java`** at `com.themistra.notification`, mirroring `services/auth`.
2. **`FEATURE_MODULES`** = `channel`, `consumer`, `delivery`, `inapp`, `preference`, `template`; `common` excluded.
3. **L11 rule** uses `getDirectDependenciesToSelf()` (dependency-based), fail-fast on unmapped entities, no allowlist unless a real violation is found.
4. **L8 rule** mirrors auth exactly: only `ResourceServerConfig` may call `permitAll` on `AuthorizedUrl`.
5. **L2 rule** is scoped to `com.themistra.notification..`, bans sibling service packages and the four listed synchronous HTTP client packages, whole service.
6. **Real `@Test` canary per rule** plus `ImportOption.DoNotIncludeTests` for the main scan.
7. **Negative-proof fixtures**:
   - `RogueChannelEntityReferencer` (in `channel`, references `delivery.DeliveryLog`).
   - `RogueUnmappedEntity` (outside `com.themistra.notification`).
   - `RogueHttpClientUser` (outside `com.themistra.notification`, uses `RestTemplate`).
8. **Disclosed limitations**:
   - No negative proof for the sibling-service-package half of L2 (no compiling fixture possible).
   - No negative proof for L8 (consistent with auth precedent).
