# payment · T25 · Phase 0 — Repository Understanding

No design here: this is grounding only. Everything below was read directly from source on
2026-10-19, at branch `spec/service-specs-and-ai-framework`, HEAD `b6f0276c` (T24 done, suite
490/490).

## 1. Architecture summary

- **Modules:** package-by-feature under `com.themistra.payment`. The top-level packages are:
  - `common`: shared plumbing (security, errors, `CryptoClient`, canonical JSON)
  - `invoice`
  - `payment`
  - `verification`
  - `receipt`, including the `receipt.ledger` sub-package
  - `consumer`: `chain.tx.*` listeners and `ProcessedEvent`
  - `events`: outbox, relay, and topics
  - `export`

  The entry point is `PaymentServiceApplication` at the root.
- **Persistence:** JPA plus Flyway, with the `payments` schema in `V1__payments_baseline.sql`.
  Money is `NUMERIC` / `BigDecimal` (L6).
- **Events:** the outbox (`events/OutboxPublisher`, `OutboxRelay`) publishes the four
  `payments.*` topics. Consumers are idempotent via `consumer/ProcessedEvent`.
- **Security:**
  - One stateless resource-server chain in `common/ResourceServerConfig`.
  - The only `permitAll` is `auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll()`.
  - `common/PublicEndpoints.PATTERNS` contains exactly `/actuator/health/**`, `/actuator/info`
    and `/actuator/prometheus`.
  - Every controller handler carries `@PreAuthorize("hasRole('MERCHANT')")`
    (`InvoiceController:52, 72, 84`; `ExportController:44`).
  - L3: no chain client and no KMS client. The signature comes only via `CryptoClient`
    (`POST /attest`).

## 2. Existing code this task touches

**Most of T25's rules already exist.** They were built in earlier tasks inside
`services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` (333 lines):

| T25 item | Already exists? | Where | Origin |
|---|---|---|---|
| L12: no cross-module entity imports | **Yes** | `shouldPreventCrossModuleEntityImports` (`:146`); `FEATURE_MODULES` = invoice, payment, events, verification, consumer, receipt (`:136`); fail-fast for an unmapped `@Entity` | T09 |
| L12 canaries | **Yes** | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (`:228`), `…ActuallyFailsAgainstAGenuineViolation` (`:271`), `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` (`:287`) | T09 |
| L6: no floating-point money | **Yes** | `shouldRejectFloatingPointMoneyTypes` (`:60`). It covers fields, return types, method params and constructor params, and blanket-bans `float`/`double`/`Float`/`Double` plus their array forms. | T05, hardened later |
| L6 named test `shouldRejectFloatingPointMoneyTypesAtArchUnit` | **Yes** | `:223`, plus a violation canary at `:302` | T05 |
| L3: KMS/chain-client ban | **Yes** | `shouldBanKmsAndChainClientImports` (`:190`) bans `org.web3j..`, `org.tron..`, `software.amazon.awssdk.services.kms..`; canaries at `:233` and `:250` | T15 |
| L10: public-endpoint allowlist | **Partially, and not via ArchUnit** | see below | — |

The L10 allowlist is covered today by `common/PublicEndpointsTest.java`:
- `patternsListExactlyTheThreeDeclaredPaths`
- behavioral MockMvc checks that the declared paths aren't blocked and that sensitive actuator
  and application paths return 401
- `resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints`, a **source-text** sweep that
  counts `.permitAll()` lines in `ResourceServerConfig.java` only

There is **no ArchUnit rule** that stops a `permitAll()` call in *another* class, for example a
second `SecurityFilterChain` bean in some other module. Both sibling services that have one do
have that rule:
- `services/auth/.../ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (`:283`)
- `services/notification/.../ArchitectureTest.shouldEnforcePublicEndpointAllowlist` (`:117`)

Both use `noClasses().that().doNotBelongToAnyOf(<SecurityConfig>.class).should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")`.

**Test fixtures** live in `src/test/java/com/themistra/payment/archtestfixtures/`:
`RogueFloatingPointMoneyUser`, `RogueInlineProblemTypeUser`, `RogueKmsAndChainClientUser`,
`RogueUnmappedEntity`. A cross-module referencer is cited at `:144`
(`RogueInvoiceEntityReferencer`). There are also stub packages for web3j, tron and kms under
`src/test/java`.

No tables, entities or production classes are involved; this task is test-only by nature.

## 3. Established patterns to follow

- **ArchUnit style:** see `ArchitectureTest.java:27-37`.
  - **No `@ArchTest` fields and no `@AnalyzeClasses`.** Every rule is a
    `private static final ArchRule`, enforced by a plain `@Test` canary that calls
    `rule.check(analyzedClasses())`.
  - The reason is the Surefire 3.5.3 bug: any class with an `@ArchTest` field reports 0 tests and
    silently skips its `@Test` methods too.
- **Canary pair per rule:** one `…IsCheckedDuringStandardBuild` test against the real code, and
  one `…ActuallyFailsAgainstAGenuineViolation` test against a fixture class, asserting an
  `AssertionError` with a specific message.
- **Imported classes:** `ClassFileImporter().withImportOption(DoNotIncludeTests).importPackages("com.themistra.payment")`.
  The violation canaries import their fixture classes separately.
- **Sibling precedent:** auth's and notification's allowlist rule (shape above). Notification's
  is the closest analog: a single `ResourceServerConfig` and the same `PublicEndpoints` layout.

## 4. Testing conventions

Testing follows `agents.md:57-58`:
- Unit tests are plain JUnit with a fixed `Clock`.
- Then ArchUnit and contract tests.
- Then Testcontainers integration tests (Postgres + Kafka).

ArchUnit 1.3.0 is a test-scope dependency (`pom.xml`). The `ArchitectureTest` canaries are
proven to execute under `mvn test`, and counts appear in the 490 total.

## 5. Known gaps / unknowns

1. **L10 "assert the public-endpoint allowlist":** it isn't clear what is still missing.
   - The only identified gap is the ArchUnit `permitAll` ban outside `ResourceServerConfig`,
     mirroring auth and notification.
   - The existing `PublicEndpointsTest` already asserts the list contents and the behaviour.
   - Whether T25 should *also* add an ArchUnit sweep that every `@RestController` handler
     declares `@PreAuthorize`, as auth's `admin_controller_handlers_require_preauthorize` does,
     is a Phase 1/2 decision. **I do not know** whether L10 intends it.
2. **`agents.md:32` says "ArchUnit enforces `api → application → domain` within a module."**
   - This service has no such layering. Each feature package is flat: controller, service,
     repository and entity side by side, with no `api`/`application`/`domain` sub-packages.
   - That is the same layout auth, crypto and notification use. **I do not know** whether that
     standing rule is meant to apply.
   - It is *not* in T25's task statement, so it's logged here rather than assumed in scope. It
     needs Phase 1 to confirm it is out of scope, or the human to decide.
3. **`FEATURE_MODULES` omits `export` and `common`.**
   - The omission is deliberate and correct today: neither owns an `@Entity`.
   - An `export` class depending on another module's entity is still flagged, because
     `featureModuleOf` returns null, which is not the same module.
   - T25 needs no change here, but Phase 1 should confirm that the existing rules fully satisfy
     L12 and L6 as written, rather than assuming so.
4. **Chain-SDK coverage of the L3 ban.** It bans web3j, tron and the KMS SDK. `design.md` says
   "web3j, chain SDKs, and any AWS KMS signing client". **I do not know** whether other chain
   SDKs should be listed; none is a dependency of this module or any sibling.
