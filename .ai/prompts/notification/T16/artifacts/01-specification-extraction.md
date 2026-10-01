# notification · T16 · Phase 1 — Specification Extraction

## Business Rules

**No R-numbered requirement governs this task's own substance — confirmed directly, not assumed.**
`requirements.md` contains exactly 19 requirements (R1-R19); none mentions "module," "boundary,"
"synchronous," or "public endpoint" anywhere in its own text. R19 (the last requirement, under the
same "Contracts & boundaries" section header this task's own header also cites) belongs to T15
(consumed-contract tests), not this task. T16's entire business content comes from its own three
scoped LOCKED decisions alone — this mirrors `services/auth`'s own T32/T35 and `services/crypto`'s
own T20/T25/T27, each of which built an analogous ArchUnit task purely from LOCKED/design decisions,
with no corresponding numbered functional requirement either.

## Locked Decisions

- **L2**. Consume-only at launch — this service initiates no domain state and makes no synchronous
  cross-service call on the delivery path. This task's own job: turn this from a design-doc
  assertion into a CI-enforced ArchUnit check.
- **L8**. Zero trust on the in-app surface — the in-app stream and read API validate the recipient's
  JWT; no endpoint is public except actuator health/info/prometheus. This task's own job: assert,
  structurally, that no class other than the one real security-config class can ever declare a new
  unauthenticated path.
- **L11**. Module boundaries — package-by-feature; no feature module imports another feature
  module's entity; shared plumbing lives in `common`; "Enforced by ArchUnit, mirroring the auth
  service" (`design.md`'s own literal text). This task's own job: build that ArchUnit enforcement,
  which has been asserted in prose since this service's own Phase 1 but never actually built until
  now.

## Files involved

**Existing — read, not modified, the real subjects these new rules check:**
- The 7 real `@Entity` classes across 5 packages (`consumer/ProcessedEvent.java`,
  `delivery/DeliveryLog.java`, `delivery/DeliveryRetry.java`, `inapp/InappNotification.java`,
  `preference/ChannelPreference.java`, `preference/ContactProjection.java`, `template/Template.java`)
  — L11's own subject.
- `common/ResourceServerConfig.java` (its own single `permitAll()` call) and
  `common/PublicEndpoints.java` (the allowlist it reads) — L8's own subject.
- `delivery/DeliveryOrchestrator.java` and everything it calls (`channel/EmailChannel.java`,
  `channel/InAppChannel.java`, and transitively `channel/SesEmailTransport.java`) — L2's own
  subject; confirmed at Phase 0 that none of these currently import any sibling-service package or
  HTTP-client class.
- `pom.xml` — `archunit-junit5:1.3.0` already present since T01, unused until now.

**New — this task's own real deliverable:**
- A new ArchUnit test class (exact name/location is Phase 2's own decision) implementing
  `shouldPreventCrossModuleEntityImports` (the literal `package.md` §8 name) plus the L8/L2 rules
  the task statement also names, mirroring `services/auth`'s `ArchitectureTest.java` and
  `services/crypto`'s `CrossModuleEntityArchitectureTest.java`/`KmsSignerArchitectureTest.java`
  structurally.
- A dedicated `archtestfixtures` test-source package (new, mirrors `services/crypto`'s own
  identical precedent) holding deliberately-violating fixture classes, so every new rule has a real
  negative-proof test, not only a canary that passes on already-clean code.

## Dependencies

`com.tngtech.archunit:archunit-junit5:1.3.0` (already present); JUnit Jupiter (the real execution
engine for the "canary" tests — see the Phase 0 finding that `@ArchTest` fields alone do not run
under this monorepo's own Surefire configuration); no new Maven dependency.

## Acceptance Criteria

1. **AC1** (L11). `shouldPreventCrossModuleEntityImports` exists, is enforced by a real `@Test`
   canary (not only an `@ArchTest` field), and correctly identifies every one of the 7 real
   `@Entity` classes' own feature module.
2. **AC2** (L11). A genuine negative-proof test exists, using a deliberately-violating fixture class
   kept outside the real scanned package, proving the rule can actually fail — not only that it
   currently finds nothing.
3. **AC3** (L11). If any pre-existing cross-module entity coupling is found when the rule first
   runs against this real codebase, it is either fixed (if cheap and in-scope) or explicitly,
   narrowly allowlisted by fully-qualified name with its own staleness-guard test — never silently
   absorbed by loosening the rule itself. (Phase 0's own expectation, to be confirmed not assumed,
   is that zero such couplings exist today.)
4. **AC4** (L8). A rule exists asserting no class other than `ResourceServerConfig` may call
   `permitAll()` on an `AuthorizeHttpRequestsConfigurer.AuthorizedUrl`, backed by a real `@Test`
   canary and a genuine negative-proof test.
5. **AC5** (L2). A rule exists asserting the delivery path makes no synchronous cross-service HTTP
   call — the exact scope (narrow to `delivery/` or service-wide) is a Phase 2 design decision, not
   resolved here.

## Required Tests

Named (`package.md` §8): `shouldPreventCrossModuleEntityImports` (L11). Plus, mirroring both
existing sibling precedents exactly: a real `@Test` canary for every rule this task adds (since
`@ArchTest` fields alone do not execute under `mvn test` in this monorepo); a genuine negative-proof
test per rule, using a dedicated fixture class outside the real scanned package; a fail-fast test
proving an `@Entity`/`@RestController` outside every known feature module fails loudly rather than
being silently unenforced; a staleness-guard test for any allowlist entry this task's own
implementation turns out to need (none expected, to be confirmed).

## Open Questions

No blockers. Two genuine design decisions remain for Phase 2 to resolve (not blockers to
extraction): the exact class/package name for the new ArchUnit test class, and the exact scope of
the L2 rule (narrow to `delivery/` vs. service-wide). Both are concrete implementation choices a
senior engineer can make from this extraction alone, consistent with how `services/auth`/`services/crypto`'s
own analogous tasks resolved equivalent choices at their own Phase 2.
