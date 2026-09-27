# notification · T04 · Phase 9 — Review Resolution

Disposition of the 6 findings from `artifacts/08-independent-review.md`, cross-referenced with
`artifacts/07-self-review.md`'s own 3 findings (Kimi's #2/#3/#4 are the same substance as
self-review's #1/#2/#3 — resolved once, together, below).

## Finding 1 (Kimi) — required T04 tests are entirely missing

**Disposition: DEFERRED, not implemented now.** Verified true: no T04-specific test file exists.
Same disposition as T03's own Phase 9 (its own Findings #1-5): Phase 6's own explicit rule ("tests
are Phase 10's job") and this task's own established precedent both defer new tests to Phase 10 —
not a defect in T04's current state, just premature. Kimi's own recommended test list (unit test +
5 integration-test cases, including the transaction-join, concurrent-call, and round-trip proofs) is
retained verbatim as Phase 10's own required-test list — it matches almost exactly what Phases 2/4/5
already planned, plus the round-trip case Findings 4/self-review-3 both independently called for.

## Finding 2 (Kimi) / Self-review Finding 1 — `ProcessedEvent.create(...)` dead code

**Disposition: ACCEPTED, fixed.** Removed `create(...)` entirely (the option both reviews offered as
one of two reasonable choices) — no plausible near-term use case is committed anywhere in the spec
that would justify keeping unused construction code around "just in case." `ProcessedEvent`'s own
class Javadoc rewritten to state plainly that the entity is read-only in practice, with the real
write path (`ProcessedEventRepository.insertIfNew`) named explicitly so a future reader isn't misled
the way the old Javadoc's "IdempotencyGuard is the only writer" line risked.

## Finding 3 (Kimi) / Self-review Finding 2 — native query doesn't clear the first-level cache

**Disposition: ACCEPTED, fixed.** Added `clearAutomatically = true, flushAutomatically = true` to
`@Modifying` on `insertIfNew`, with the Javadoc explaining why (a native modifying query bypasses
the persistence context; without clearing it, a caller that read this key earlier in the same
session could see stale cached state on a later read in the same transaction). No behavioral change
for the current single caller (`IdempotencyGuard`, which never reads before writing).

## Finding 4 (Kimi) / Self-review Finding 3 — no automated proof `processedAt` round-trips

**Disposition: DEFERRED to Phase 10.** This finding's own remediation is entirely test-shaped (retrieve
the row, assert the stored instant matches) — nothing to fix in production code. Tracked as a
required Phase 10 test case, folded into the same integration test class already planned.

## Finding 5 (Kimi) — no mutation-test proof that reverting `ON CONFLICT DO NOTHING` fails the tests

**Disposition: ACCEPTED, deferred to Phase 10 (test-generation), tracked as a required mutation-test
step.** This mirrors the established mutation-testing discipline already used repeatedly this
session (T02/T03's own precedent: mutate, confirm the new test fails, revert). Cannot be performed
now since the test it would mutate-check doesn't exist yet — explicitly required as part of Phase
10's own verification, not merely a suggestion.

## Finding 6 (Kimi) — `eventKey` column mapping should state `nullable = false` explicitly

**Disposition: ACCEPTED, fixed.** Added `nullable = false` to `@Column(name = "event_key", ...)`,
matching the other two columns' own explicitness and documenting the constraint Hibernate already
infers from `@Id`, removing any ambiguity for a future reader.

## Verification

`mvn -pl services/notification clean verify` — 68 tests, 0 failures. `git status -s services/auth
services/crypto` — empty; no sibling service touched.
