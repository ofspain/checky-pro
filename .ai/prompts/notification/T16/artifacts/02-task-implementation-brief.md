# notification · T16 · Phase 2 — Task Implementation Brief

## Task

Build this service's first real ArchUnit tests, converting three already-stated, prose-only
standing rules (L2, L8, L11) into CI-enforced checks, mirroring `services/auth`'s
`ArchitectureTest.java` and `services/crypto`'s `CrossModuleEntityArchitectureTest.java`/
`KmsSignerArchitectureTest.java` structurally and in spirit — including the one fact that makes
this task non-trivial: `@ArchTest` fields alone do not execute under this monorepo's own Maven
Surefire configuration (confirmed independently in both sibling services via real negative-proof
runs), so every rule needs a real `@Test` canary that manually invokes it.

## Purpose

Closes the gap between what `design.md` has asserted since this service's own Phase 1 ("Enforced by
ArchUnit, mirroring the auth service" — L11's own literal text) and what has actually existed in
code until now: nothing. Gives three standing rules a permanent, build-breaking regression guard
instead of a comment a future change could silently violate.

## Scope

**In:**
- `src/test/java/com/themistra/notification/ArchitectureTest.java` — one combined file (mirrors
  `services/auth`'s own single-file precedent for its own analogous, multi-rule task), with:
  - `shouldPreventCrossModuleEntityImports` (L11, the literal `package.md` §8 name) — dependency-
    based (`getDirectDependenciesToSelf()`), feature-module-aware, fail-fast on an unmapped
    `@Entity`, mirroring both existing precedents' exact shape.
  - `shouldEnforcePublicEndpointAllowlist` (L8) — `noClasses().that().doNotBelongToAnyOf(ResourceServerConfig.class).should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")`,
    mirroring `services/auth`'s own identical rule exactly.
  - `shouldMakeNoSynchronousCrossServiceCall` (L2) — `noClasses()` in
    `com.themistra.notification..` may depend on `com.themistra.auth../com.themistra.crypto../com.themistra.payment..`
    or a synchronous HTTP client package (`org.springframework.web.client..`,
    `org.springframework.web.reactive.function.client..`, `java.net.http..`,
    `org.apache.http..`). **Scoped to the whole service, not only `delivery/`** — L2's own
    canonical `design.md` text is a whole-service guarantee ("this service... makes no synchronous
    cross-service call"); the task statement's own "the delivery path" phrasing explains *why* this
    matters, read as the functional pipeline, not a literal, narrower package-only reading. AWS SES
    (an already-accepted, resolved external dependency, O2/Q2) is deliberately *not* banned — L2
    concerns calls to *sibling platform services*, not the external provider this service's own job
    requires calling.
  - A real `@Test` canary per rule (the actual enforcement, given the Phase 0 finding).
  - A genuine negative-proof test per rule where a compiling fixture is actually possible (see
    below for the one disclosed exception).
- `src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java` — a test-only
  fixture *inside* a real feature module (`channel`), referencing a different module's real entity
  (`delivery.DeliveryLog`), mirroring `services/crypto`'s own `RogueWatchEntityReferencer` exactly —
  proves the genuine `entityModule != dependingModule` violation path, not only the fail-fast path.
- `src/test/java/archtestfixtures/RogueUnmappedEntity.java` — a bare `@Entity` class *outside*
  `com.themistra.notification` entirely, mirroring `services/crypto`'s own identical fixture —
  proves the fail-fast "entity outside every known module" path.
- `src/test/java/archtestfixtures/RogueHttpClientUser.java` — a class with a real
  `org.springframework.web.client.RestTemplate` field, proving `shouldMakeNoSynchronousCrossServiceCall`
  can actually fail for the HTTP-client half of its own ban list.

**Out:**
- A negative-proof test for the sibling-service-package half of `shouldMakeNoSynchronousCrossServiceCall`
  — **disclosed, not silently skipped**: `services/notification`'s own `pom.xml` has no Maven
  dependency on `services/auth`/`services/crypto`/`services/payment` at all (by design,
  `agents.md`'s own "Services depend only on `libs/` and `contracts/`" rule) — there is no real
  class on this module's own classpath to construct a compiling fixture against for this specific
  half. The HTTP-client half's own negative proof (`RogueHttpClientUser`) proves the same rule's
  own mechanism genuinely works; this is the same class of disclosed, structural-only limitation
  this pipeline has accepted before when a defensive check has no reachable, compiling violation to
  construct.
- A negative-proof test for `shouldEnforcePublicEndpointAllowlist` — **disclosed, consistent with
  precedent, not an oversight**: `services/auth`'s own identical rule has no such test either (only
  its canary exists); mirrored exactly, not gold-plated beyond what the established precedent
  itself judged necessary.
- Any production code change — this task only adds tests; `ResourceServerConfig`/`PublicEndpoints`/
  every `@Entity` class/the delivery path are read, never modified.
- Fixing any real violation the new rules happen to surface — Phase 0's own expectation is zero
  exist, but if one is found when the rule first runs, Phase 6's own implementation notes will
  disclose it and either fix it (if cheap, in-scope) or allowlist it explicitly (AC3) — not decided
  here, since it cannot be known until the rule actually runs.

## Business Rules

None — confirmed at Phase 1 that no R-numbered requirement governs this task.

## Locked Decisions

L2, L8, L11 (unchanged from Phase 1).

## Dependencies

`com.tngtech.archunit:archunit-junit5:1.3.0` (already present, unused until now); JUnit Jupiter
(the real execution engine for every canary).

## Inputs

None at runtime — static analysis of this module's own compiled test/main classes.

## Outputs

Three new, permanently-enforced ArchUnit rules, each backed by a real, Surefire-executing `@Test`
canary. No production behavior change.

## State Changes

None.

## Files to Create

- `services/notification/src/test/java/com/themistra/notification/ArchitectureTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java`
- `services/notification/src/test/java/archtestfixtures/RogueUnmappedEntity.java`
- `services/notification/src/test/java/archtestfixtures/RogueHttpClientUser.java`

## Files to Modify

None expected. **Conditional**: if the new L11/L2 rules surface a real, pre-existing violation when
first run, either the violating production file (if a cheap, in-scope fix) or `ArchitectureTest.java`
itself (to add a narrow, named, justified allowlist entry plus its own staleness-guard test) — not
assumed necessary, confirmed only once the rule actually runs in Phase 6.

## Files NOT to Modify

`common/ResourceServerConfig.java`, `common/PublicEndpoints.java`, every `@Entity` class, every
class on the delivery path, `pom.xml` (no new dependency needed), every file under `spec/`, every
sibling service.

## Acceptance Criteria

Unchanged AC1-AC5 from Phase 1, now concrete:
1. **AC1** (L11). `shouldPreventCrossModuleEntityImports` exists, backed by a real canary, correctly
   resolves every one of the 7 real `@Entity` classes to its own feature module.
2. **AC2** (L11). `RogueChannelEntityReferencer` proves the rule genuinely fails against a real,
   in-module violation; `RogueUnmappedEntity` proves the fail-fast path for an entity outside every
   known module.
3. **AC3** (L11). If the rule surfaces any real, pre-existing violation on first run, it is fixed or
   explicitly, narrowly allowlisted with a staleness-guard test — not assumed clean.
4. **AC4** (L8). `shouldEnforcePublicEndpointAllowlist` exists, backed by a real canary, mirrors
   `services/auth`'s own identical rule exactly.
5. **AC5** (L2). `shouldMakeNoSynchronousCrossServiceCall` exists, scoped to the whole service,
   backed by a real canary and `RogueHttpClientUser`'s own genuine negative proof.

## Required Tests

Unchanged from Phase 1, now itemized: 3 rules × (1 canary each); 2 genuine negative-proof tests
(`RogueChannelEntityReferencer`'s own violation, `RogueHttpClientUser`'s own violation); 1 fail-fast
test (`RogueUnmappedEntity`); any staleness-guard test a real allowlist entry turns out to need
(none expected).

## Constraints

- **Dependency-based entity checking** (`getDirectDependenciesToSelf()`), not access-based —
  mirrors both existing precedents' own documented rationale exactly (a declared-but-unused
  cross-module field is already a real coupling an access-based check would miss).
- **Fail loudly, never silently skip** — an `@Entity` outside every known `FEATURE_MODULES` entry
  must fail the build, not be quietly unenforced.
- **Module boundaries (L11) applied to this task's own new code too** — `ArchitectureTest.java`
  itself lives at the service root (`com.themistra.notification`, mirroring `services/auth`'s own
  identical placement), not inside any one feature module, since it analyzes all of them.
- **No new Maven dependency, no production code change** (absent a real, found violation).

## Open Questions

No blockers. Both design decisions Phase 1 left open are resolved above: the test class lives at
the service root as one combined file (mirroring `services/auth`'s own precedent for an
analogous, multi-rule task), and the L2 rule is scoped to the whole service, not narrowly to
`delivery/`, reading the task statement's own "the delivery path" as the functional pipeline L2's
own canonical, whole-service wording already describes.
