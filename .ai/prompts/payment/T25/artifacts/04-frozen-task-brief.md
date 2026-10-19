# payment · T25 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**: approved by the human on 2026-10-19. Downstream phases may not renegotiate
it.

Consumes:
- `artifacts/02-task-implementation-brief.md` (TIB)
- `artifacts/03-design-challenge.md` (Kimi)

Every Phase 3 finding was checked against source before disposition.

## Phase 3 dispositions

| # | Finding | Verified against source | Disposition |
|---|---|---|---|
| F1 | The new rule excludes `ResourceServerConfig`, so a second `permitAll()` *inside* it escapes. | True for the ArchUnit rule. But `PublicEndpointsTest.resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints` (`:80-93`) already asserts exactly one `.permitAll()` line in `ResourceServerConfig.java`, and that it contains `PublicEndpoints.PATTERNS`. The test is in the standard build. (Kimi cited `ResourceServerConfig` under `src/test`; it actually lives in `src/main/.../common/`.) | **ACCEPTED (record only).** The brief records the split of responsibility (§Guarantee split). No second rule is added. |
| F2 | The rule bans only `permitAll()`, not `anonymous()` or `web.ignoring()`. | True. Neither is used in `src/main`: grep finds only `ResourceServerConfig:57` for `permitAll`, and no `anonymous`, `WebSecurityCustomizer` or `ignoring`. | **ACCEPTED (document the limit).** The Javadoc states that the rule covers `permitAll()` only, as in auth and notification. The human chose not to extend coverage. |
| F3 | The fixture's compile-ability is unverified. | **Prototyped.** The shape below compiled against the module's real classpath (`javac`). `javap` shows the call target is `AuthorizeHttpRequestsConfigurer$AuthorizedUrl.permitAll`, the exact method the rule bans. Kimi's raw `AuthorizedUrl` parameter sketch is not used: the outer class is generic, and the qualified generic form is correct. | **ACCEPTED (resolved).** The sketch is fixed below. |
| F4 | The rule is coupled to the Spring Security API surface. | True. A rename would break compilation, which is loud, not silent. | **ACCEPTED (note).** One Javadoc line. |
| F5 | There is no `@PreAuthorize`-on-every-handler sweep. | True. Every handler carries one today (`InvoiceController:52, 72, 84`; `ExportController:44`). | **ACCEPTED as a deferred item.** It stays out of scope, logged as follow-up FU-2 (human decision). |
| F6 | The rule name over-promises. | Partly true: it is the stray-caller guard, while `PublicEndpointsTest` validates the contents. | **ACCEPTED (Javadoc).** The name `shouldEnforcePublicEndpointAllowlist` is kept for parity with auth and notification. The Javadoc says it is the companion guard. |
| F7 | Verify that `FEATURE_MODULES` is complete for the final codebase. | All 8 `@Entity` classes are already covered: `invoice/Invoice`, `payment/Payment`, `verification/VerificationRecord`, `receipt/Receipt`, `receipt/ReceiptPayload`, `receipt/ledger/LedgerEntry`, `consumer/ProcessedEvent`, `events/OutboxEvent`. All are in listed modules. | **REJECTED.** The existing rule already does this check on *every* build: it fails fast for any `@Entity` outside `FEATURE_MODULES` (`ArchitectureTest:150-160`, plus the canary `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`). A one-time scan would be redundant. |

## Open Questions: resolution

| # | Question | Resolution | Owner |
|---|---|---|---|
| OQ1 | The `agents.md:32` `api → application → domain` layering is unenforced. | **DEFERRED.** It is out of T25. It is a platform-wide standing-rule gap (no service has those sub-packages), logged as FU-1. | Human |
| OQ2 | `@PreAuthorize` handler sweep | **DEFERRED.** It is out of T25 (F5), logged as FU-2. | Human |

No Open Question remains unresolved.

## Task (unchanged)

Confirm that L12, L6 and L3 are gated by `mvn test`. Add the missing L10 ArchUnit `permitAll`
ban, with a canary that runs it against the real code and a canary that runs it against a
violation fixture.

## Scope

**In:**
- In `ArchitectureTest`:
  - add the new rule `shouldEnforcePublicEndpointAllowlist`
  - add two plain `@Test` canaries
  - add the Javadoc required by F1, F2, F4 and F6
- One new fixture, `RoguePermitAllUser`.

**Out:**
- Production code.
- Changes to the three existing rules or `FEATURE_MODULES`.
- `PublicEndpoints`, `PublicEndpointsTest`, `ResourceServerConfig`.
- The layering rule (FU-1).
- The `@PreAuthorize` sweep (FU-2).
- Coverage of `anonymous()` or `web.ignoring()` (F2).

## Rule (frozen)

```java
private static final ArchRule shouldEnforcePublicEndpointAllowlist = noClasses()
        .that().resideInAPackage("com.themistra.payment..")
        .and().doNotBelongToAnyOf(ResourceServerConfig.class)
        .should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")
        .because("L10: ...");
```

The Javadoc must state the following.
- **F6:** this is the stray-caller guard. The allowlist contents and the single call inside
  `ResourceServerConfig` are guarded by `PublicEndpointsTest` (F1).
- **F2:** it covers `permitAll()` only. `anonymous()` and `web.ignoring()` are not used in this
  service and are not covered.
- **F4:** it is coupled to the Spring Security API. A rename breaks compilation, and the fix is
  mechanical.

## Guarantee split (F1)

| Threat | Guard |
|---|---|
| A `permitAll()` in any class other than `ResourceServerConfig` | **New ArchUnit rule** (T25) |
| A second or unscoped `permitAll()` inside `ResourceServerConfig` | `PublicEndpointsTest.resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints` (existing) |
| `PATTERNS` contents drift | `PublicEndpointsTest.patternsListExactlyTheThreeDeclaredPaths` (existing) |
| A sensitive path left reachable | `PublicEndpointsTest`'s behavioural 401 checks (existing) |

## Fixture (frozen; prototype-compiled)

`services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java`:

```java
public class RoguePermitAllUser {
    void openUp(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/rogue/**").permitAll();
    }
}
```

The fixture is not a Spring bean. It has no annotations, and `archtestfixtures` is never
component-scanned.

## Files

- **Create:** `archtestfixtures/RoguePermitAllUser.java`.
- **Modify:** `ArchitectureTest.java`, to add the rule, the 2 canaries, the Javadoc and the
  imports.
- **NOT modify:**
  - anything under `src/main`
  - `PublicEndpointsTest`
  - the existing rules, `FEATURE_MODULES`, and the existing fixtures
  - `spec/`

## Acceptance Criteria (final)

- **AC1 (L12):** `shouldPreventCrossModuleEntityImports` and its 3 canaries pass unchanged.
- **AC2 (L6):** `shouldRejectFloatingPointMoneyTypesAtArchUnit` and its violation canary pass
  unchanged.
- **AC3 (L3):** `shouldBanKmsAndChainClientImports` and its 2 canaries pass unchanged.
- **AC4 (L10):** `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` passes on
  the real code. `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`
  asserts an `AssertionError` whose message names `RoguePermitAllUser`.
- **AC5:**
  - No `@ArchTest` or `@AnalyzeClasses` is introduced.
  - The full suite is green at **492/492**: 490 plus the 2 new canaries.
  - The Javadoc required by F1, F2, F4 and F6 is present.

## Required Tests

- **Named (already exist):**
  - `shouldPreventCrossModuleEntityImports`, via its canary
  - `shouldRejectFloatingPointMoneyTypesAtArchUnit`
- **New:**
  - `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`
  - `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`

## Follow-ups (human-owned, out of T25)

- **FU-1:** the `agents.md:32` `api → application → domain` layering rule is unenforced in
  every service. Either the rule is reworded or a restructuring task is created.
- **FU-2:** add an ArchUnit sweep that every `@RestController` handler carries
  `@PreAuthorize`.
