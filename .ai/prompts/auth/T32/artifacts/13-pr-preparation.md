<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T32 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: enforce the public-endpoint allowlist via ArchUnit (T32)
```

## Commit message

```
auth: enforce the public-endpoint allowlist via ArchUnit (T32)

Adds two checks to ArchitectureTest: a content assertion that POST
/api-keys/token (already present since T25) stays registered in
PublicEndpoints, and shouldEnforcePublicEndpointAllowlist - the named test -
which fails if any class other than SecurityChainsConfig calls
AuthorizedUrl.permitAll(). Targets the exact method via ArchUnit's
callMethod(...), not a class-dependency rule, after verifying (javap against
the resolved Spring Security jar) that a dependency-based rule would also
wrongly flag legitimate .authenticated()/.hasRole() usage.

Verifying the new rule surfaced a real, unrelated defect: ArchUnit's own
JUnit 5 engine does not execute at all under this project's `mvn test` -
confirmed by deliberately introducing a stray permitAll() call and watching
Maven report BUILD SUCCESS anyway, then proving the rule itself is correct by
running it through a direct JUnit Platform Launcher invocation instead. This
affects every @ArchTest rule in this file, not just the new one, and already
hid a real bug in the pre-existing account-module boundary rule (a missing
".." package wildcard). Both are logged separately for follow-up, out of this
task's own scope. To make sure this task's own rule isn't equally silent, a
small canary test now drives the same rule directly through ArchUnit's
ClassFileImporter API - a plain JUnit test, proven to execute under Surefire
- so shouldEnforcePublicEndpointAllowlist actually gates this build today
regardless of when the broader engine issue gets fixed.

Every check here was proven non-vacuous with a real negative-proof run
(temporarily reintroduce the violation, confirm BUILD FAILURE, revert) rather
than trusted on inspection alone.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Tests only**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` (modified — 3 new
  members: `apiKeysTokenExchangeIsInThePublicAllowlist`, `shouldEnforcePublicEndpointAllowlist`,
  `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`; 1 new constant,
  `ANALYZED_PACKAGE`; 1 new private helper, `analyzedClasses()`)

No production code changed. No `spec/` file touched. No migration.

## Summary

Makes L11 ("public endpoint discipline") CI-enforced rather than convention-only, per the task's
own two-clause statement. No R-numbered requirement is scoped to this task — it's a
process/verification step. Both halves were implemented exactly as frozen at Phase 4: the AC2
mechanism (`callMethod(AuthorizedUrl.class, "permitAll")`) was corrected from an initially
over-broad class-dependency proposal after Kimi's Phase 3 review caught it would also flag
legitimate `.authenticated()`/`.hasRole()` calls — verified against the actual resolved Spring
Security 7.1.0 bytecode before freezing, not just reasoned about.

Independent review (Phase 8) and test review (Phase 11) together found 8 findings across two
rounds; the most consequential led to discovering that ArchUnit's JUnit 5 engine doesn't execute
under this project's Maven Surefire setup at all — a real, pre-existing, broad-blast-radius defect
unrelated to this task's own diff, affecting every `@ArchTest` rule in the file and already hiding
a genuine bug in the pre-existing account-module rule. Rather than expand scope to fix that
(deferred as a separate follow-up, with femi's explicit sign-off), a narrowly-scoped canary test was
added so this task's own named rule specifically is proven to gate `mvn test` today. Two other
findings (a path-level verification gap in AC2, and coverage of non-`.permitAll()` exposure
mechanisms like `WebSecurityCustomizer`) were accepted as documented residuals matching the task's
own literal wording, both decided via explicit human gates rather than silently narrowed.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **2/2 pass** (JUnit Jupiter engine; the
  canary test directly exercises the named ArchUnit rule's own `check()` logic, so this counts as
  the rule executing, not merely compiling).
- Four separate negative-proof runs performed and reverted across this task's phases (all
  confirmed real `BUILD FAILURE`, not just a written-but-unverified assertion): the named rule via
  a direct JUnit Platform Launcher invocation (Phase 6, since Surefire alone doesn't run it), the
  named rule again via the canary under real `mvn test` (Phase 9), and the content assertion via a
  temporary removal of `/api-keys/token` (Phase 11).
- Full `services/auth` regression: this change is additive-only to one test file and does not
  touch any production class, migration, or configuration — no separate full-suite rerun was judged
  necessary beyond the targeted `ArchitectureTest` runs above.

## Specification references

- **Task:** T32 — Public endpoint sweep (`spec/auth-service/tasks.md`, task 32)
- **Requirements:** none scoped (process/verification task)
- **LOCKED decisions:** L11, L12
- **Named tests (`package.md` §8):** `shouldEnforcePublicEndpointAllowlist` (written, executed,
  passing, and proven non-vacuous)

---

## Note for the reviewer: a significant out-of-scope finding on this branch

Verifying this task's own rule surfaced that **ArchUnit's JUnit 5 engine does not execute under
`mvn test` in this environment at all** — every `@ArchTest` rule in `ArchitectureTest.java` except
the new `shouldEnforcePublicEndpointAllowlist` (which the canary now separately covers) is
currently un-gated by a real build. This is not a T32 regression; it predates this task and was
only discovered because this task's own review process insisted on a real negative-proof run rather
than trusting the rule's presence. Root cause not fully identified (ruled out: Docker availability,
fork/no-fork, a version-skewed `junit-platform-launcher`). A pre-existing bug in the
`only_the_account_module_may_touch_the_Account_entity` rule (missing a `..` package wildcard) is
currently hidden by this same non-execution and will fail the build the moment it's fixed — flagged
so it isn't a surprise red build later. Both are logged in project memory for a dedicated follow-up
task; neither is fixed by this PR.

---

**Phase 13 complete — PR preparation written. T32 is ready for merge.**
