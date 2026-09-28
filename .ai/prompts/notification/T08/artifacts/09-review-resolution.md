# notification · T08 · Phase 9 — Review Resolution

Disposition of the 8 findings from `artifacts/08-independent-review.md`. All 8 independently
verified against real source before disposition — none were false. All 8 map cleanly to Phase 10
(test generation) except Finding 8, which duplicates an already-tracked Phase 4 disposition — no
production code change is required at this phase.

## Finding 1 (Kimi) — no T08-specific tests exist yet

**Disposition: DEFERRED to Phase 10, as planned.** Verified true: `preference/` currently contains
only T05's `ContactProjectionUpdater*Test` files. Same disposition as T03/T04/T05/T06's own Phase 9
precedent — Phase 6's own explicit rule ("tests are Phase 10's job") defers all new tests, and this
is not a defect in T08's own completion state at this point in the pipeline. Kimi's own recommended
test shape (unit + integration, covering stored-row precedence/default fallback/hard floor/unknown
pairs/case normalization/null rejection) is retained verbatim as Phase 10's own required-test list.

## Finding 2 (Kimi) — no test proves the `SECURITY`+`EMAIL` floor against a stored `enabled=false` row

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
(Phase 7) already proved exactly this — inserted a `SECURITY`/`EMAIL`/`enabled=false` row and
confirmed `resolve` still returns `true` — then was deleted per this pipeline's own established
scratch-test discipline. Kimi's own recommended test is retained verbatim as a required Phase 10
test, matching the deleted scratch test exactly.

## Finding 3 (Kimi) — no test proves the default table matches `design.md` §4c verbatim

**Disposition: DEFERRED to Phase 10.** Verified true: no test compares `PreferenceResolver.DEFAULTS`
against the spec. Retained as a required Phase 10 test — mirrors
`NotificationBaselineMigrationIntegrationTest.v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock`'s
own established pattern for verbatim-artifact drift protection, though a package-visible accessor
(or a same-package test) is needed since `DEFAULTS` is currently `private`.

## Finding 4 (Kimi) — no test proves `WEBHOOK`/`PUSH`/unknown-category behavior

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
already covered `WEBHOOK`/`PUSH` with no stored row (Phase 7's own `webhookAndPushResolveFalseWithNoRow`).
Kimi's own broader recommended coverage (also an unknown category like `COMPLIANCE`, and both
unsupported channels under every documented category) is retained as Phase 10's own required-test
list, extending beyond what the scratch test already proved.

## Finding 5 (Kimi) — no test proves case normalization against the real DB

**Disposition: DEFERRED to Phase 10, risk already closed once.** Self-review's own scratch test
already proved this (`lowercaseInputMatchesUppercaseStoredRow`). Retained as a required Phase 10
test.

## Finding 6 (Kimi) — no test proves null-input rejection

**Disposition: DEFERRED to Phase 10.** Verified true: no test asserts `NullPointerException` for
any of the three arguments. Retained as a required Phase 10 test (3 cases: `null` `accountUuid`/
`category`/`channel`).

## Finding 7 (Kimi) — no test proves `PreferenceResolver` is a resolved Spring bean

**Disposition: DEFERRED to Phase 10.** Verified true: no `@SpringBootTest` currently autowires
`PreferenceResolver`. Mirrors T06's own identical Kimi Phase 8 Finding #8 resolution
(`IdempotencyGuardIntegrationTest.theRealNoOpDispatcherIsTheResolvedSpringBean`) — the same test
class is the natural home for a `PreferenceResolver`-is-a-bean assertion too, since it already
boots a plain `@SpringBootTest` context with no dispatcher/resolver override.

## Finding 8 (Kimi) — `ChannelPreferenceRepository` still exposes inherited mutators

**Disposition: ALREADY TRACKED, no new action.** Identical concern to Finding #4 of this task's own
Phase 3 design challenge, already dispositioned at Phase 4 (Frozen Task Brief): kept
`extends JpaRepository` for consistency with T04/T05's own established precedent, documented via
Javadoc rather than narrowed, with the `V6` `SELECT`-only grant as the real defense-in-depth layer.
Kimi's own new suggestion here (an ArchUnit/reflection test asserting no production code calls the
inherited mutators) is a genuinely different, cheaper idea than Phase 3's own two options — noted
as an optional Phase 10 nicety, not required: with only one caller (`PreferenceResolver`) and one
method actually used, the value of a dedicated regression guard for "nothing else calls `save`" is
marginal compared to `processed_events`/`contact_projection`'s own established precedent, where no
such guard exists either.

## Verification

`mvn -pl services/notification clean verify` — 128 tests, 0 failures, unchanged (no production or
test code modified this phase). `git status -s services/auth services/crypto services/payment` —
empty; no sibling service touched.
