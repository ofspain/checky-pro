# auth · T11 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds one new, self-contained class with zero Spring wiring, zero
collaborators, and zero callers today (T12 wires it up next). No existing file's behavior changes.

## Commit title

```
Add LockoutStateMachine for brute-force lockout rules (T11)
```

## Commit message

```
Add LockoutStateMachine for brute-force lockout rules (T11)

Pure-logic, unit-testable decision function encoding the R16-R19/L4
brute-force lockout rules: 5 failed attempts / 30-minute inactivity
decay / 15-minute base lock with doubling via lockCount. No
persistence, no Spring wiring, no Account mutation - it returns a
decision (counters, a blocked flag, a lock/unlock/none signal) for a
future LockoutService (T12) to apply.

Adversarial design review (Phase 3) forced two decisions before any
code was written:

- The spec's "rolling 30-minute window" cannot be implemented from the
  lockout_state schema as designed - there is no window-start column,
  and V1-V4 are immutable. Formally adopted the simplified
  inactivity-decay interpretation (5 failures with no gap > 30 min
  since the last one) as the approved reading of R17/R19, not a
  stopgap pending a schema change.
- What happens to failedAttempts at the moment of a lock was
  undefined. Escalated for explicit human decision at Phase 4:
  failedAttempts is left at the threshold value, not reset - R18 gates
  the reset on a successful post-unlock login only. Confirmed
  consequence: a failed attempt landing within the decay window of the
  lock-triggering failure re-locks immediately with lockCount doubled
  again. Intentional, tested behavior, not an oversight.

Independent code review (Phase 8) caught a real bug self-review had
already found by different means: the non-locking failure branch
echoed a stale, already-expired lockedUntil instead of nulling it,
violating the class's own documented output contract ("null if
not/no-longer locked"). Fixed at Phase 9, paired with a second human
decision the bug exposed: whether clearing lockedUntil via a failed
(non-relocking) attempt should also signal UNLOCK to the caller.
Decided yes, mirroring the existing success-path behavior, so
Account.status and lockout_state.locked_until can never drift apart.
Constructor validation for the three rule constants (previously
null-checked only, not range-checked) was added in the same pass.

Independent test review (Phase 11) found six real gaps, all applied:
missing lastFailedAt/lockCount assertions on intermediate failures,
no coverage of the strictly-after-lockedUntil boundary, no coverage of
the tightest same-second non-decay boundary, and untested constructor
null-checks. One gap (the named reset test not literally covering
R18's locked-then-succeeds wording) was resolved by documentation - a
Javadoc cross-reference to the sibling test that already covers it -
rather than restructuring the named test.

22 unit tests cover both named tests, every acceptance-criteria
boundary (doubling across two full cycles, the exact-30:00 boundary in
both directions, the exact-lockedUntil boundary in both directions),
the two human-approved extensions above, reset(), and every
null/negative-input guard. No Spring context, no database - plain
JUnit + AssertJ.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java` (new) —
  `LockoutSnapshot`/`LockoutDecision` records, `LockoutAttemptOutcome`/`AccountStatusChange` enums,
  `evaluate(...)` and `reset()`. No cross-module import (`account` untouched); no Spring
  annotations.

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java` (new) — 22
  tests.

**Process artifacts** (`.ai/prompts/auth/T11/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decision (failedAttempts persists at threshold post-lock, escalating re-lock confirmed), the
Phase 3/8/11 Kimi reviews and their dispositions, the Phase 9 human decision + bug fix (stale
`lockedUntil` / `UNLOCK` signal), and the Phase 12 PASS verdict with full traceability matrix.

## Summary

Implements `tasks.md` task 11: `LockoutStateMachine`, the pure decision logic T12
(`LockoutService`), T13 (SAS auth-path wiring), and T14 (admin unlock) will all call. Three things
worth a reviewer's attention: (1) the "rolling 30-minute window" language in `requirements.md` is
formally reinterpreted as an inactivity-decay rule because the persisted schema has no
window-start column — this is a locked, documented decision, not an approximation; (2) locking
does not reset `failedAttempts`, so a post-unlock failure within the decay window re-locks
immediately with doubled duration — a deliberate escalating-lockout design, human-approved at
Phase 4 after being flagged for its real UX consequence; (3) a genuine bug (stale `lockedUntil`
surviving past its own expiry) was caught by two independent review passes using the same
evidence and fixed at Phase 9, alongside a second human decision (the `UNLOCK` signal) the fix
exposed — both are now covered by dedicated tests, not just fixed and hoped for.

## Testing performed

Same situation as every task in this chain: `mvn -pl services/auth test` cannot run to completion
due to the pre-existing, unrelated `token` package compile failure (tracked since T03, unrelated
to and untouched by this branch). `LockoutStateMachine.java` and its test have zero dependencies
beyond `java.time`/`java.util`/JUnit/AssertJ, so this task's own verification never depended on
that broken package: compiled via `javac` against the module's resolved test classpath, then
executed via the JUnit Platform `Launcher` API directly.

**Result: 22/22 tests passing**, most recently re-run in full at Phase 12, ~60ms, no Spring
context, no database, no mocks (zero collaborators to mock).

Kimi's independent code review (Phase 8) found 5 findings; 3 accepted and applied (the
`lockedUntil` bug fix, the human-approved `UNLOCK` signal, constructor validation), 1 confirmed
documentation-only (no cap on `lockCount`, already an accepted Phase 4 disposition), 1 rejected
(missing test file — false premise, test authorship is explicitly this pipeline's Phase 10, not
Phase 6). Kimi's independent test review (Phase 11) found 6 gaps; all 6 held up on inspection and
were applied directly to the test file. Full requirement-to-evidence-to-test traceability is in
`12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 11 — "Lockout state machine."
- **Requirements:** R16, R17, R18, R19 (`requirements.md`), fully implemented and tested.
- **LOCKED decisions:** L4 (5/30/15 + doubling, `design.md` §4a) — implemented exactly as adopted,
  no cap. L12 (module boundary) — confirmed clean, no `account`-package import.
- **Named tests:** `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes`,
  `shouldResetLockoutCounterOnSuccessfulLogin` (`package.md` §8) — both present verbatim.
