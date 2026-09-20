<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T32 · Phase 6 — Implementation Notes

## What changed

One file: `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`. Added exactly
per the frozen brief/plan:

1. **`apiKeysTokenExchangeIsInThePublicAllowlist`** (plain `@Test`) — asserts
   `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token` (AC1).
2. **`shouldEnforcePublicEndpointAllowlist`** (`@ArchTest static final ArchRule`, the named test) —
   `noClasses().that().doNotBelongToAnyOf(SecurityChainsConfig.class).should().callMethod(
   AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")` (AC2), exactly the mechanism
   frozen at Phase 4.

New imports: `PublicEndpoints`, `SecurityChainsConfig`, `org.junit.jupiter.api.Test`,
`org.springframework.http.HttpMethod`, `AuthorizeHttpRequestsConfigurer`, and the static
`assertThat` import. No production code touched. No other file modified.

## Mapping to acceptance criteria

- **AC1** ← `apiKeysTokenExchangeIsInThePublicAllowlist`.
- **AC2** ← `shouldEnforcePublicEndpointAllowlist`.

## Verification performed (and a major deviation forced by reality)

`mvn clean test-compile` — clean, no errors.

**The frozen brief's own required "negative-proof" step (Files to Modify/Required Tests) surfaced
a serious pre-existing environmental defect, unrelated to this task's own code, that forced a
change in how verification had to be done:**

Running `mvn test -Dtest='ArchitectureTest'` reports `Tests run: 1` (my new plain `@Test`, via the
JUnit Jupiter engine) but **`Tests run: 0` for the ArchUnit engine itself** — including all 9
pre-existing rules and my new `shouldEnforcePublicEndpointAllowlist`. To determine whether this
was a cosmetic reporting quirk (as a prior session's memory note assumed — "needs Docker to report
non-zero counts") or a real non-execution problem, I ran the frozen brief's own planned
negative-proof step: added a throwaway `.permitAll()` call in a scratch class outside
`SecurityChainsConfig`, ran `mvn test -Dtest='ArchitectureTest'` again, and got **`BUILD SUCCESS`
with zero failures** — proving the rule (and by extension, every ArchUnit rule in this file) does
**not** actually execute under Maven Surefire in this environment, at all. This is not a
Docker-related issue: Docker has been up all session, and I confirmed the same "0 tests" result
both forked (`forkCount` default) and unforked (`-DforkCount=0`, same JVM as Maven itself).

To verify my own rule's correctness independent of Surefire, I built the exact test classpath
(`mvn dependency:build-classpath`) and invoked the JUnit Platform Launcher API directly (bypassing
Maven Surefire's provider entirely). Against that same classpath, the ArchUnit engine correctly
discovered and ran all 10 rules, and:
- **`shouldEnforcePublicEndpointAllowlist` correctly failed** against the deliberately-introduced
  stray `permitAll()` call, with an exact, well-formed violation message naming the offending
  method and line — proof the rule's mechanism (verified via `javap` at Phase 3/4) is correct.
- It also correctly passed once the scratch violation was removed.
- **A genuine, previously-undetected bug in a pre-existing rule was also surfaced**:
  `only_the_account_module_may_touch_the_Account_entity` failed against
  `AccountResponse.from(Account)` (in `account.dto`, calling `Account`'s getters) — because the
  rule's `.resideOutsideOfPackage("com.themistra.auth.account")` predicate has no `..` wildcard, so
  it does not include the `account.dto`/`account.event` subpackages the rule's own `.because(...)`
  text explicitly says are meant to be allowed. This rule has apparently never actually executed
  since it was written, so this bug has never surfaced.

The scratch probe file and its throwaway `.permitAll()` call were removed after the negative-proof
step; the working tree now contains only the `ArchitectureTest.java` change. I attempted the one
standard documented fix (declaring `org.junit.platform:junit-platform-launcher` as an explicit test
dependency, in case Surefire's internally-bundled copy was somehow version-skewed from the
project's own resolved JUnit Platform artifacts) — it did not change the result, and was reverted
(confirmed via `git status`, no diff remains in `pom.xml`).

**Per femi's explicit direction (human gate, this phase):** proceed with T32 as planned rather than
root-cause Surefire's internals further — this is a real, out-of-scope, pre-existing defect with a
blast radius far beyond this task (every `ArchitectureTest` rule, likely since whichever task first
introduced this file), not something T32's own frozen brief authorizes fixing. Logged for a
dedicated follow-up (see memory) rather than silently absorbed or ignored.

**Net effect on T32 itself:** both new members are implemented correctly and verified correct via
direct invocation (the only verification method that actually executes them in this environment
today). Whether they will *also* correctly gate a real CI run depends on the separate Surefire
issue being fixed at some point — a real, named risk, not a false "all clear."

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
