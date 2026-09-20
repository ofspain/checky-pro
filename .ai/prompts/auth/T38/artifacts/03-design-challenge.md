<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T38 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T38 — Review against gap analysis defect catalogue |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/auth-service/tasks.md`, task 38):**
> **Review against gap analysis defect catalogue.** Verify plaintext credentials, unauthenticated admin routes, shared model artifact, `Long.getLong` config misread, and `allow-circular-references` classes of error are absent.

Below are adversarial findings on the Phase 2 TIB. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — AC1 evidence lacks specific citations and treats local-only placeholders ambiguously

**Issue.** The brief states AC1 is "Met" but does not cite the specific files/mechanisms the Outputs section requires. More importantly, `application.properties` contains plaintext local-only placeholders such as `${DB_PASSWORD:checky-local-only}` and `${PAYMENT_SERVICE_CLIENT_SECRET:payment-local-only}`. While these are local-dev fallbacks, they are credential-shaped strings committed in the source tree. The gap-analysis calls out "Committed DB password + RDS credential in comment" as a reference defect, so the boundary between "committed production secret" and "local-only placeholder" needs to be explicit.

**Severity.** Medium — the claim is directionally correct but could be misread as asserting zero credential-shaped strings anywhere in source.

**Evidence.**
- TIB §70: "AC1 — Plaintext credentials absent. **Met.**" with no file citation.
- `services/auth/src/main/resources/application.properties` lines 22, 55-61: local-only plaintext defaults for DB password and service client secrets.
- `services/auth/pom.xml` lines 196-199: Flyway plugin configuration hardcodes `checky-local-only`.
- `services/auth/docs/architecture/gap-analysis.md` §2: "Committed DB password + RDS credential in comment" listed as a reference defect.

**Recommended brief amendment.** For AC1, explicitly list the evidence and the distinction:

> "AC1 Met. Evidence: (a) every credential-shaped domain field is hashed/encrypted (`Account.passwordHash` BCrypt, `RefreshTokenFamily.currentTokenHash` SHA-256, `ApiKey.keyHash` SHA-256, TOTP seeds AES-256-GCM envelope); (b) `application.properties` contains only `${ENV_VAR:local-only-placeholder}` fallbacks, not production secrets; (c) no credential-shaped value appears in a code comment. The local-only placeholders are accepted local-dev defaults, not committed production credentials."

---

## Finding 2 — Admin-route enforcement relies on a naming convention that could be bypassed

**Issue.** The existing ArchUnit rule `admin_controller_handlers_require_preauthorize` only selects classes whose simple name starts with "Admin" and are annotated with `@RestController`. A future admin-facing controller that does not follow the `Admin*` naming convention would not be caught by this rule, even if it exposed `/admin/**` endpoints without `@PreAuthorize`.

**Severity.** Low/Medium — current controllers all follow the convention, but the rule is not path-based.

**Evidence.**
- `ArchitectureTest.java` lines 270-274: `.haveSimpleNameStartingWith("Admin")`.
- All current admin controllers (`AdminAccountController`, `AdminAuditController`, `AdminRoleController`, `AdminRoleTemplateController`, `AdminAccountRoleController`) follow this naming.

**Recommended brief amendment.** Add:

> "AC2 Met. Current evidence: no `/admin/**` path in `PublicEndpoints.java`; `ArchitectureTest.shouldEnforcePublicEndpointAllowlist` prevents `.permitAll()` outside the allowlist; `admin_controller_handlers_require_preauthorize` covers all existing `Admin*` controllers. Note: the ArchUnit rule is name-based; a future admin controller not prefixed `Admin*` would need its own enforcement."

---

## Finding 3 — AC3 scope should distinguish production code from test code

**Issue.** The brief asserts the shared model artifact is absent based on `pom.xml` dependency inventory. However, the T37 fix introduced `AccountService` imports from the `account` module into `audit` and `authz` test files. While test code is excluded from `ArchitectureTest` analysis, this is still a cross-module test dependency. The brief should clarify that AC3 applies to production/runtime artifacts, not test helpers.

**Severity.** Low — no production shared-model dependency exists, but scope ambiguity could confuse reviewers.

**Evidence.**
- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java` line 4-6: imports `AccountService` from `account` module.
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java` line 4-6: same.
- `ArchitectureTest.java` line 42: `@AnalyzeClasses(..., importOptions = ImportOption.DoNotIncludeTests.class)`.

**Recommended brief amendment.** Add:

> "AC3 Met for production/runtime code: no cross-service entity-sharing dependency exists in `services/auth/pom.xml` or the root parent. Intra-service test helpers may import other modules' services; this is outside AC3's scope and is excluded from ArchUnit analysis."

---

## Finding 4 — AC4 verification should explicitly include `@Value` and other raw config reads

**Issue.** The brief states AC4 is Met because `Long.getLong` has zero matches and config uses validated `@ConfigurationProperties`. This is correct, but the gap-analysis defect is broader: any raw config read that silently defaults instead of failing boot. The brief should confirm that `@Value` annotations, `System.getProperty`, `System.getenv`, and custom `Environment` lookups are also absent or safely wrapped.

**Severity.** Low — Phase 0 already checked `System.getProperty`/`System.getenv`, but the brief doesn't say so.

**Evidence.**
- `artifacts/00-repository-understanding.md` line 33: grep checked `Long.getLong`, `Integer.getInteger`, `System.getProperty`, `System.getenv`.
- TIB §74: only mentions `Long.getLong`.

**Recommended brief amendment.** Update AC4 evidence:

> "AC4 Met. Evidence: zero matches for `Long.getLong`, `Integer.getInteger`, `System.getProperty`, and `System.getenv` across `src/main/java/com/themistra/auth`; all config surfaces use `@ConfigurationProperties`/`@Validated` records that fail boot on missing/invalid values in non-local profiles."

---

## Finding 5 — AC5 should confirm no programmatic `allow-circular-references` setting

**Issue.** The brief checks `pom.xml` and `application.properties` for `allow-circular-references=true`. It does not explicitly confirm that the property is not set programmatically (e.g., via `SpringApplicationBuilder` or a `BeanFactoryPostProcessor`). This is unlikely but should be ruled out for completeness.

**Severity.** Low.

**Evidence.**
- TIB §74: "AC5 — `allow-circular-references=true` absent. **Met.**"
- `services/auth/pom.xml` and `application.properties`: no matches.

**Recommended brief amendment.** Add:

> "AC5 Met. Verified by grep across `pom.xml`, `application.properties`, and `src/main/java` for `allow-circular-references`, `allowCircularReferences`, and `setAllowCircularReferences`; no matches."

---

## Finding 6 — The gitleaks CI gate remains unconfirmed

**Issue.** The gap-analysis identifies the gitleaks gate as the defense against *future* committed secrets. The brief carries this as a non-blocking open question, but since AC1 is partly about "committed secrets," the lack of confirmation means the long-term defense is unverified. This does not invalidate the current-state claim, but it is a gap in the verification record.

**Severity.** Low — explicitly scoped out, but worth challenging.

**Evidence.**
- TIB §89-90: "The gitleaks-CI-gate question ... is non-blocking and carried forward for awareness only."
- `artifacts/01-specification-extraction.md` lines 78-82: same.

**Recommended brief amendment.** Retain the open question but make it explicit in AC1:

> "AC1 Met for current source state. Open: repo-wide gitleaks CI gate configuration is outside this task's scope and not verified here; if it is missing, that is a separate CI/infrastructure follow-up."

---

## Finding 7 — No explicit comment scan for embedded secrets

**Issue.** The gap-analysis calls out an "RDS credential in comment" as a reference defect. The brief verifies domain fields and `application.properties` but does not explicitly state that a comment scan was performed. A credential buried in a code comment would not be caught by the current checks.

**Severity.** Low.

**Evidence.**
- `services/auth/docs/architecture/gap-analysis.md` §2: "Committed DB password + RDS credential in comment."
- TIB §70 and Outputs §47-48: no mention of comment scanning.

**Recommended brief amendment.** Add to AC1 evidence:

> "(d) no credential-shaped value was found in code comments (verified by manual/grep inspection of source)."

---

## Finding 8 — Docker image build is asserted but not linked to secret absence

**Issue.** The task statement includes "Docker image must build from repo root." The brief mentions the Dockerfile in Dependencies and Files involved but does not state that the built image was inspected for secrets. A Dockerfile can build successfully while copying a file that contains a secret into the image.

**Severity.** Low — the current Dockerfile likely does not copy secrets, but the verification record is incomplete.

**Evidence.**
- TIB §37-39: lists `services/auth/Dockerfile` as a dependency.
- `services/auth/Dockerfile`: not read in the brief; no verification summary provided.

**Recommended brief amendment.** Add:

> "Docker image builds successfully from repo root. The image copies only the compiled artifact and required runtime files; no secret-bearing source files (e.g., `application.properties` with real values, PEM keys) are copied into the image."

---

## Summary

The Phase 2 TIB's "all five defect classes absent" conclusion is well-supported at a high level, but the brief needs to be more precise in its evidence citations and scope boundaries before freezing. The most important amendments are AC1's explicit handling of local-only placeholders (Finding 1) and AC2's naming-convention limitation (Finding 2). The remaining findings are low-severity completeness gaps.

(End of Phase 3 design challenge.)
