# notification · T16 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's own Files to Create section. No file is added beyond
what Phase 4 authorized — except one placement correction, caught during this phase's own design
pass, disclosed below rather than silently applied.

## A placement correction caught during this phase, before any code was written

`shouldMakeNoSynchronousCrossServiceCall`'s own subject-side condition is
`.that().resideInAPackage(ANALYZED_PACKAGE + "..")` — a *positive inclusion* restriction ("the
class under examination must be inside `com.themistra.notification`"), unlike `KmsSignerArchitectureTest`'s
own two rules (whose subject-side conditions are "resides *outside* attest" / "not named
`KmsSigner*`" — an *exclusion* shape that trivially admits a fixture from anywhere, including a
top-level `archtestfixtures` package entirely outside the service). Placing `RogueHttpClientUser` in
the top-level `archtestfixtures` package (the frozen brief's own literal file path) would make it
**not a valid subject for this rule at all** — it would never be examined, and the negative-proof
test would silently prove nothing. **Corrected placement**: `RogueHttpClientUser` moves to
`src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java` (package
`com.themistra.notification.channel`, alongside `RogueChannelEntityReferencer`) — genuinely inside
the service, so it is a valid subject, while still excluded from the real canary's own scan via
`ImportOption.DoNotIncludeTests` (which filters by source set, not package). `RogueUnmappedEntity`
is unaffected by this correction — its own purpose specifically requires living *outside*
`com.themistra.notification` entirely, to trigger `shouldPreventCrossModuleEntityImports`'s own
fail-fast path for an entity outside every known `FEATURE_MODULES` entry.

## Files to create

### `src/test/java/com/themistra/notification/ArchitectureTest.java`
```
@AnalyzeClasses(packages = ArchitectureTest.ANALYZED_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    static final String ANALYZED_PACKAGE = "com.themistra.notification";

    private static final List<String> FEATURE_MODULES = List.of(
            "channel", "consumer", "delivery", "inapp", "preference", "template");

    @ArchTest
    static final ArchRule shouldPreventCrossModuleEntityImports = ...;
    private static ArchCondition<JavaClass> onlyBeAccessedFromTheSameFeatureModule() { ... }
    private static String featureModuleOf(JavaClass javaClass) { ... }

    @ArchTest
    static final ArchRule shouldEnforcePublicEndpointAllowlist = ...;

    @ArchTest
    static final ArchRule shouldMakeNoSynchronousCrossServiceCall = ...;

    private static final JavaClasses analyzedClasses = ...;
    private static JavaClasses analyzedClasses() { return analyzedClasses; }

    @Test void shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild()
    @Test void shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild()
    @Test void shouldMakeNoSynchronousCrossServiceCallIsCheckedDuringStandardBuild()

    @Test void shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation()
    @Test void shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule()
    @Test void shouldMakeNoSynchronousCrossServiceCallActuallyFailsAgainstAGenuineViolation()
}
```

`shouldPreventCrossModuleEntityImports` (AC1/AC2/AC3, the literal `package.md` §8 name): `classes()
.that().areAnnotatedWith(Entity.class).should(onlyBeAccessedFromTheSameFeatureModule())`, mirroring
`services/crypto`'s `CrossModuleEntityArchitectureTest` exactly — dependency-based
(`getDirectDependenciesToSelf()`), fail-fast (an `@Entity` whose own `featureModuleOf` resolves to
`null` reports a violation naming itself, not silently passing), no allowlist (Phase 4's own
confirmation: zero pre-existing violations exist today).

`shouldEnforcePublicEndpointAllowlist` (AC4): `noClasses().that().doNotBelongToAnyOf(ResourceServerConfig.class)
.should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")`, mirroring
`services/auth`'s own identical rule exactly, including its own precedent of no negative-proof test
(Finding #5's own disposition).

`shouldMakeNoSynchronousCrossServiceCall` (AC5): `noClasses().that().resideInAPackage(ANALYZED_PACKAGE + "..")
.should().dependOnClassesThat().resideInAnyPackage("com.themistra.auth..", "com.themistra.crypto..",
"com.themistra.payment..", "org.springframework.web.client..",
"org.springframework.web.reactive.function.client..", "java.net.http..", "org.apache.http..")` —
whole-service scope (Finding #2's own kept decision), the finite ban list from Phase 2/3 (Finding
#3's own kept decision), AWS SES deliberately not banned (an already-accepted external dependency,
not a sibling-service call).

`analyzedClasses`: one single, eagerly-initialized `JavaClasses` (mirroring both existing
precedents' own documented "never let `@AnalyzeClasses` and the canaries drift apart" lesson,
Finding #6) — `new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(ANALYZED_PACKAGE)`.

Three canaries (the real enforcement, Finding #6) each call `someRule.check(analyzedClasses())`.

Three negative-proof/fail-fast tests (Findings #2 self-review precedent, #7, #8 from Phase 3) each
build their own separate, narrow `JavaClasses` via a fresh `ClassFileImporter().importClasses(...)`
naming exactly the fixture(s) involved, and assert via `assertThatThrownBy(...).isInstanceOf(AssertionError.class).hasMessageContaining(...)`
(mirroring both existing precedents' own exact assertion shape — `ArchRule.check(...)` throws plain
`java.lang.AssertionError` on a violation, already confirmed directly by both sibling services).

### `src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java`
```
class RogueChannelEntityReferencer {
    private DeliveryLog deliveryLog;  // declared, never read - a real coupling regardless
}
```
Package `com.themistra.notification.channel` — inside a real feature module, as a test-source file
(excluded from the real canary's own scan via `DoNotIncludeTests`, included when the negative-proof
test imports it directly). References `delivery.DeliveryLog`, a real entity in a *different*
module, proving the genuine `entityModule != dependingModule` violation path.

### `src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java`
```
class RogueHttpClientUser {
    private RestTemplate restTemplate;  // declared, never read
}
```
Package `com.themistra.notification.channel` (the corrected placement, above) — a declared,
unused `org.springframework.web.client.RestTemplate` field (already transitively available via
`spring-boot-starter-web`, no new dependency), proving `shouldMakeNoSynchronousCrossServiceCall`
can genuinely fail against the HTTP-client half of its own ban list.

### `src/test/java/archtestfixtures/RogueUnmappedEntity.java`
```
@Entity
public class RogueUnmappedEntity {
    private Long id;
}
```
Package `archtestfixtures` — a top-level test-source package, deliberately *outside*
`com.themistra.notification` entirely (mirroring `services/crypto`'s own identical fixture and
package name), proving `shouldPreventCrossModuleEntityImports`'s own fail-fast path for an `@Entity`
outside every known `FEATURE_MODULES` entry. No `@Id`/JPA-mapping correctness needed — ArchUnit
inspects only the bytecode annotation, and no persistence unit ever scans this test-only class.

## Files to modify

None expected, per Phase 4's own confirmed-clean disposition (Finding #8).

## Public methods (signatures)

None beyond the `@Test`-annotated methods themselves listed above (package-private, matching both
existing precedents' own identical visibility).

## Private methods

`ArchitectureTest.featureModuleOf(JavaClass)`, `ArchitectureTest.onlyBeAccessedFromTheSameFeatureModule()`,
`ArchitectureTest.analyzedClasses()` — all described above.

## Entities used

The 7 real `@Entity` classes are read (via ArchUnit's own bytecode analysis), never modified:
`ProcessedEvent`, `DeliveryLog`, `DeliveryRetry`, `InappNotification`, `ChannelPreference`,
`ContactProjection`, `Template`.

## Repositories used

None.

## Services used

None beyond ArchUnit's own `ClassFileImporter`/`JavaClasses` API and JUnit Jupiter.

## Unit tests required

All of `ArchitectureTest.java`'s own test methods, listed above — this phase's own "tests
required" output IS the production deliverable (an ArchUnit/testing-only task).

## Integration tests required

None — no Spring context, no Testcontainers, no Kafka/Postgres anywhere in this task's own scope.

## Execution order

1. `src/test/java/archtestfixtures/RogueUnmappedEntity.java` — no dependency on anything else this
   task creates.
2. `src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java` — depends
   on the already-existing `delivery.DeliveryLog`.
3. `src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java` — depends on the
   already-available `RestTemplate`.
4. `src/test/java/com/themistra/notification/ArchitectureTest.java` — the three rules, the shared
   `analyzedClasses`, the three canaries, then the three negative-proof/fail-fast tests (each
   depends on the fixture it names).
5. Run `ArchitectureTest` alone first (`mvn -pl services/notification test -Dtest=ArchitectureTest`),
   confirming every canary passes against the real, current codebase and every negative-proof test
   genuinely throws against its own fixture.
6. Full suite: `mvn -pl services/notification clean verify`.
