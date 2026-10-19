# payment · T25 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

No `R` requirement is scoped to T25. `requirements.md` has no ArchUnit or module-boundary
requirement, and the header cites none. T25 is driven entirely by LOCKED decisions and standing
rules. The adjacent requirements are continuity context only, and T25 does not re-implement
them:

- **R28.** Merchant-scoped reads return only the caller's resources, and `404` across merchants.
  This is already enforced in the controllers and services (T07).
- **R4.** A zero, negative or fractional amount is rejected. That is the *runtime* half of L6
  (T06); T25 owns only L6's *compile-time* ArchUnit half.

## Locked Decisions

| ID | Constraint on T25 (`design.md` §4a) | Existing coverage (Phase 0) |
|---|---|---|
| **L3** | An ArchUnit rule bans importing web3j, chain SDKs and any AWS KMS signing client. | `shouldBanKmsAndChainClientImports` (T15): web3j, tron, kms |
| **L6** | ArchUnit forbids floating-point money types: no `float`/`double`/`Double`/`Float` on any money-carrying field or DTO. | `shouldRejectFloatingPointMoneyTypes` (T05): blanket ban on fields, returns, params and constructors, including array forms |
| **L10** | Every non-internal endpoint requires a valid JWT, and the service never assumes ingress authenticated the caller. Task 25 adds: "Assert the public-endpoint allowlist." | `PublicEndpointsTest` (source-text and behavioural); **no ArchUnit `permitAll` ban** |
| **L12** | Package-by-feature; no feature module imports another feature module's entity class; shared plumbing lives in `common`; enforced by ArchUnit, "mirroring the auth service". | `shouldPreventCrossModuleEntityImports` (T09) |
| **L1 (continuity)** | `package.md` §8 maps `shouldPreventCrossModuleEntityImports` to "L1/L12". T25 must not alter L1's state machine. | n/a |

Standing rules (`agents.md`):
- `:32` Package-by-feature plus ArchUnit; see the Open Question on `api → application → domain`.
- `:57` The ArchUnit layer of the test pyramid.
- `:73` The ArchUnit ban on chain and KMS clients, which matches L3.

## Files involved

**Existing, to extend:**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`. This is where
  every existing rule and canary lives, and where any new rule goes, following the
  no-`@ArchTest`, plain-`@Test`-canary pattern (`:27-37`).
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/`. A new violation
  fixture goes here if a new rule is added.

**Existing, read-only:**
- `common/ResourceServerConfig.java` (the one `permitAll` call)
- `common/PublicEndpoints.java` (3 patterns)
- `common/PublicEndpointsTest.java`
- `InvoiceController.java`, `ExportController.java` (the `@PreAuthorize` sites)
- the two sibling precedents: `services/notification/.../ArchitectureTest.java:113-121` and
  `services/auth/.../ArchitectureTest.java:283-289`

**New files the spec expects:** none are named. Any new fixture is a Phase 2 decision.

**No production file and no `spec/` file is touched.**

## Dependencies

- **ArchUnit 1.3.0** (test scope, already present).
- **Spring Security's `AuthorizeHttpRequestsConfigurer.AuthorizedUrl`.** Its `permitAll()` is
  the method the allowlist rule targets, as both siblings do. It is on the test classpath via
  `spring-boot-starter-oauth2-resource-server`.
- **`org.springframework.security.access.prepost.PreAuthorize`**, if Phase 2 adopts a
  handler-annotation sweep.
- No config keys, entities, repositories or contracts. The contracts listed in the header are
  read-only and unaffected.

## Acceptance Criteria

- **AC1 (L12).** `shouldPreventCrossModuleEntityImports` fails the standard build on any
  cross-module `@Entity` dependency, and on any `@Entity` outside `FEATURE_MODULES`. *This is
  already met by T09; T25 confirms it with a passing canary and a violation canary.*
- **AC2 (L6).** `shouldRejectFloatingPointMoneyTypesAtArchUnit` fails on any
  `float`/`double`/`Float`/`Double` field, return type, parameter or constructor parameter,
  including array forms. *Already met by T05 and later hardening; confirmed by both canaries.*
- **AC3 (L3).** The KMS and chain-client package ban fails on any dependency on
  `org.web3j..`, `org.tron..` or `software.amazon.awssdk.services.kms..`. *Already met by T15;
  confirmed by both canaries.*
- **AC4 (L10, the one genuinely new item).** An ArchUnit rule fails the standard build if any
  class other than `ResourceServerConfig` calls `AuthorizeHttpRequestsConfigurer.AuthorizedUrl.permitAll()`.
  It mirrors auth and notification, which use the identical rule name
  `shouldEnforcePublicEndpointAllowlist`. Together with the existing `PublicEndpointsTest`,
  this bounds every unauthenticated path to `PublicEndpoints.PATTERNS`.
- **AC5.** Every rule, old and new, is gated by a plain `@Test` that is proven to run under
  `mvn test`. Each has a violation canary showing it can fail. The full suite stays green.

## Tests required

- **Named (`package.md` §8):**
  - `shouldPreventCrossModuleEntityImports` (L1/L12). The rule exists; its canary is
    `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`.
  - `shouldRejectFloatingPointMoneyTypesAtArchUnit` (L6). It exists at `ArchitectureTest:223`.
- **Implied by AC4 (new):**
  - `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`
  - `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`, which needs a
    new fixture class that calls `permitAll()`
- **Continuity:** every existing canary at `ArchitectureTest:222-333` stays green.

## Open Questions

None blocks the task. Two need a disposition at Phase 2 or the Phase 4 gate:

1. **`agents.md:32`, "ArchUnit enforces `api → application → domain` within a module."**
   - This service's feature packages are flat. There are no `api`/`application`/`domain`
     sub-packages, the same as in auth, crypto and notification.
   - Enforcing the rule would require restructuring every module's production packages. That
     is far beyond T25's statement, which names exactly L12, L6, L3 and L10, and it would
     violate "no unrelated refactoring".
   - **Recommendation: out of T25 scope.** Disclose it as a platform-wide standing-rule gap,
     like T24's `agents.md:39` model-generation note, for the human to own.
   - Per the guardrails, I am logging this rather than silently ignoring it.
2. **L10 scope: is a handler-level `@PreAuthorize` sweep in scope?**
   - Auth has `admin_controller_handlers_require_preauthorize`. A payment equivalent would be
     "every public `@RestController` handler is `@PreAuthorize`-annotated".
   - It isn't literally "the public-endpoint allowlist", but it guards L10's "every non-internal
     endpoint requires a valid JWT" one layer down. In this service, `anyRequest().authenticated()`
     already enforces authentication, and `@PreAuthorize` enforces the role.
   - This is a low-risk engineering choice for Phase 2: one rule, test-only.

`package.md` §11 was re-checked: nothing in it is scoped to T25. Q1–Q8 are all closed or
deferred, and none touches ArchUnit.
