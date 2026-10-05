# notification · T16 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T16: ArchUnit module-boundary tests`

## Commit message

```
notification-service T16: ArchUnit module-boundary tests

Convert three already-stated, prose-only standing rules into real,
CI-enforced ArchUnit checks: L11 (no feature module may import another
feature module's entity), L8 (only ResourceServerConfig may declare an
unauthenticated path), L2 (no synchronous cross-service call anywhere
on the delivery path). All three pass against the real, current
codebase; both structurally-possible negative-proof paths genuinely
fail against their own dedicated fixtures.

The real finding this task produced: implementing this file's first
draft exactly as planned (@ArchTest fields + plain @Test canaries,
mirroring services/auth and services/crypto structurally) surfaced
that `mvn test` reported zero tests for the whole class. Root-caused
by direct reproduction, not inference - three minimal isolated repro
classes run through a real mvn test, plus Surefire's own exact forked
classpath (extracted from its generated args file) fed straight into
the real org.junit.platform.launcher.Launcher API outside Surefire
(discovers and executes everything correctly there, every time): the
mere presence of an @ArchTest field in a class causes this
repository's Maven Surefire 3.5.3 JUnitPlatformProvider to report
Tests run: 0 for the entire class, silently swallowing every ordinary
@Test method in that same class too - including a plain-@Test canary
written specifically as the known workaround for @ArchTest fields not
executing.

This class avoids the whole failure mode: each rule is a plain
private static final ArchRule (no @ArchTest, no @AnalyzeClasses -
confirmed inert without an @ArchTest field present), with the plain
@Test canaries as the only, and only confirmed-working, enforcement
mechanism.

This also means services/auth's ArchitectureTest.java and
services/crypto's CrossModuleEntityArchitectureTest.java/
KmsSignerArchitectureTest.java - which all use the @ArchTest-field-
plus-canary shape this finding breaks - have never actually enforced
their own architecture rules in CI, despite their own Javadocs
claiming the canary works as a backup. Raised directly to the user as
a standalone, repo-wide finding; fixing those two services was
explicitly scoped out of this task, per the user's own instruction,
and recorded in memory (surefire-archtest-field-bug) so it isn't lost.

Two review rounds (Kimi Phase 8: 5 findings; Phase 11: 6 gaps) were
each independently checked against actual source before disposition,
never taken on word - this caught a citation error in Kimi's own
Phase 8 Finding 3 (it attributed a dual-assertion test pattern to
services/auth; the pattern is real but actually lives in
services/crypto, since auth has no negative-proof test for that rule
at all). The underlying recommendation held anyway, on stronger
grounds (this file's own L11 negative-proof test already used the
pattern) - applied as a one-line fix to the L2 negative-proof test.
A second, analogous weak-assertion gap in the L11 fail-fast test was
found and closed during this task's own Phase 10 self-audit, before
Kimi's Phase 11 review flagged the same class of issue generally.

One other gap was found and deliberately left open, disclosed, not
fixed: featureModuleOf's own sub-package (startsWith) branch has zero
test coverage anywhere - confirmed this isn't unique to this task,
since no entity in this service, or in auth/crypto's identical helper,
lives in a sub-package today.

One FROZEN (Phase 4) file path was amended one phase later (Phase 5,
RogueHttpClientUser.java: archtestfixtures -> channel) without a
renewed approval gate - correct on the merits (confirmed independently
by self-review and Kimi both: the rule's own subject condition would
not admit a fixture at the original path at all), but still an open
process question for the user about this pipeline's own freeze
discipline.

368 tests total (362 T01-T15 unaffected + 6 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Created**
- `services/notification/src/test/java/com/themistra/notification/ArchitectureTest.java`
- `services/notification/src/test/java/com/themistra/notification/channel/RogueChannelEntityReferencer.java`
- `services/notification/src/test/java/com/themistra/notification/channel/RogueHttpClientUser.java`
- `services/notification/src/test/java/archtestfixtures/RogueUnmappedEntity.java`

**Modified**

None (test-source only; `ArchitectureTest.java` was revised in place across Phases 6/9/10, all
captured in the single final file above — no separate production file touched).

**Deleted**

None.

**Process artifacts**
- `.ai/prompts/notification/T16/artifacts/00-13-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Converts L2/L8/L11 from prose-only standing rules into real, CI-enforced ArchUnit checks. The
task's own planned scope (three rules, three canaries, three fixtures) was straightforward and
closed exactly as the frozen brief specified, modulo one disclosed path correction. Its real value
turned out to be a severe, previously-unknown, monorepo-wide finding surfaced by simply trying to
run the first draft: Maven Surefire 3.5.3 cannot correctly report results for a class containing an
`@ArchTest` field, which silently breaks not just that field but every other test in the same class
— including the exact "plain `@Test` canary as a backup" workaround `services/auth` and
`services/crypto` already rely on. This task avoids the failure mode entirely for its own three
rules; the sibling services' own exposure remains open, by the user's own explicit choice to scope
this task narrowly, and is recorded for a future task to pick up.

## Testing performed

- `mvn -pl services/notification test -Dtest=ArchitectureTest` — 6/6, 0 failures, run fresh
  repeatedly across Phases 6, 9, 10, 11, 12.
- `mvn -pl services/notification clean verify` — 368 tests, 0 failures, 0 errors, `BUILD SUCCESS`,
  run fresh multiple times, most recently at Phase 12.
- The Surefire/`@ArchTest` root cause was proven, not inferred: three minimal isolated repro classes
  (`@AnalyzeClasses` + plain `@Test` only; `@ArchTest` field only; both together) each run through a
  real `mvn test`; Surefire's own exact forked classpath (199 jar entries, extracted from its
  generated args file) fed directly into the real `org.junit.platform.launcher.Launcher` API outside
  Surefire, confirming discovery and execution both work correctly there (17/17) — isolating the
  failure to Surefire's own result-reporting adapter specifically.
- Every Kimi finding (Phase 8: 5, Phase 11: 6) was independently re-verified against actual source
  before disposition — direct `grep`/file reads of `services/auth`'s and `services/crypto`'s real
  architecture-test files and `pom.xml`, not accepted on word. One citation error caught (Phase 8
  Finding 3's misattribution of a test pattern from crypto to auth).
- `git diff --stat b57ff09..HEAD -- services/auth services/crypto services/payment` — empty; no
  sibling service touched.
- `git diff --stat b57ff09..HEAD -- spec/` — empty; no specification file modified.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 16 ("ArchUnit module-boundary tests").
- **LOCKED decisions:** L2, L8, L11 (unchanged from Phase 4's frozen brief).
- **Required tests:** all 6 present — 3 canaries (AC1/AC4/AC5), 2 L11 negative/fail-fast proofs
  (AC2), 1 L2 negative proof (AC5).

## Known, deliberate gaps (not this task's scope)

- **The L2 rule's sibling-service-package half has no negative-proof test** — structural: no
  compiling fixture is possible without a real dependency on auth/crypto/payment, which would
  itself violate the rule under test. Disclosed since Phase 2.
- **`featureModuleOf`'s own sub-package (`startsWith`) branch has zero test coverage** — no entity
  in this service, or in auth/crypto's identical helper, lives in a sub-package today; would surface
  automatically via the real canary the moment one ever does.
- **`services/auth`'s and `services/crypto`'s own existing architecture tests are silently
  non-enforced** by the same Surefire/`@ArchTest` mechanism this task discovered — the single
  largest finding this task produced, deliberately left unfixed outside this task's own scope, per
  the user's explicit instruction. Recorded in memory (`surefire-archtest-field-bug`).
- **A FROZEN file path was amended one phase after the freeze** (`RogueHttpClientUser.java`) without
  a renewed approval gate — correct on the merits, still an open process question for the user.

## Reviewer notes

- **Kimi's Phase 8 independent review (5 findings)** concurred with both self-review findings,
  correctly flagged a real weak-assertion gap (Finding 3) using supporting evidence that turned out
  to be misattributed on re-verification (crypto, not auth) — the recommendation itself survived the
  correction on even stronger grounds. Finding 4 correctly identified the `@ArchTest` removal as
  repo-wide significant, consistent with what independent empirical reproduction had already
  confirmed before the review arrived.
- **Kimi's Phase 11 test review (6 gaps)** — all 6 verified accurately against source this time, no
  citation errors. All either already-disclosed limitations or Maven-sandbox-unavailability notes,
  resolved by a fresh, real verification run rather than waved away.
- **This task's own Phase 6 implementation, not a later review, found the most significant issue**:
  the Surefire/`@ArchTest` bug was discovered by the agent actually trying to run its own first draft
  and refusing to accept "0 tests" as a shrug-worthy result — both sibling reviews only confirmed and
  characterized a finding that was already fully root-caused before either review ran.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T16.**
