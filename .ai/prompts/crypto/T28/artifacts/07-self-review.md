# crypto · T28 · Phase 7 — Self-Review

## Files reviewed

- `SECURITY-THREAT-MODEL.md`
- `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java`

## Findings and dispositions

### 1. Trailing paragraph conflated two different kinds of caveat — fixed

The document's own trailing explanatory paragraph originally grouped row #3 (Docker unavailable in this
*environment* — an execution gap, the proof exists in code) together with rows #4/#5 (part of the
mitigation is genuinely owned by infrastructure or another service — an ownership split) under one
phrase, "spans infrastructure or another service." These are different in kind: row #3's gap will close
the moment this runs in a Docker-available environment; rows #4/#5's gaps won't close by running
anywhere, since crypto-service genuinely doesn't own that portion of the mitigation. Rewrote the
paragraph to distinguish "execution gap" (row #3) from "ownership split" (rows #4/#5) explicitly.

### 2. `WatcherTest.java` edits — verified exact match to Phase 5's plan, no finding

Both `verifyNoInteractions(txLifecyclePublisher)` lines are in place exactly where pinned, using an
already-imported static method and an already-existing field. No import changes needed, none made.

### 3. `T01SkeletonRegressionTest.java`'s method name is now slightly imprecise — reviewed, not changed

The method `threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched` still uses the
verb "Tracks," which no longer literally matches the `closed` status it now asserts. Considered renaming
for precision, rejected: the verb reads naturally as "the threat model records status for," not as a
literal reference to the old `tracked` value, and renaming a test method carries real cost (any other
artifact referencing it by name would go stale) for no functional gain — out of proportion to this
task's own scope (a documentation/regression-guard update, not a refactor).

### 4. Searched for other stale references to the pre-T28 `tracked` status — none found

`grep -rn "tracked"` across the repo found no other place asserting or describing rows #1–#6 as
`tracked` in their current, post-T28 state; every other hit is either historical (other tasks' own
immutable artifact records, correctly untouched) or an unrelated use of the word.

## Verification performed

- `mvn -pl services/crypto -am verify` (re-run after this phase's own edit) — 697 tests, 0 failures, the
  same 14 already-disclosed Docker-only errors.
- Direct re-read of `SECURITY-THREAT-MODEL.md` in full, confirming rows #7–#8 and every non-Status
  column remain byte-for-byte unchanged from before this task.
