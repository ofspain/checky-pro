# auth · T12 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds new, self-contained classes with no callers yet (T13/T14 wire them up
later) plus two new guarded, no-op-safe methods on `AccountService`. No existing endpoint,
behavior, or public signature changes.

## Commit title

```
Add LockoutService to persist and apply lockout decisions (T12)
```

## Commit message

```
Add LockoutService to persist and apply lockout decisions (T12)

Wires T11's pure-logic LockoutStateMachine to the real lockout_state
table and real Account.status transitions. LockoutService loads a row
under a pessimistic lock, calls the machine, persists the result, and
applies the decision via two new guarded AccountService methods -
lock(UUID)/unlock(UUID) - the only sanctioned path from this module to
the Account entity (L12).

Adversarial design review (Phase 3) caught a bug before any code was
written: a bare Account.lock() wrapper would throw
InvalidAccountStateException on T11's own escalating re-lock case,
since lock() requires ACTIVE but the account is still LOCKED at that
exact moment. Fixed by making both new AccountService methods guarded,
idempotent no-ops outside their applicable precondition - lockout_state
still updates on that path, only the redundant Account-side transition
is skipped. The same review exposed a real contradiction between two
of T11's own artifacts (its Finding 4 disposition said "ACTIVE only,"
its own AC7 test requires evaluating a LOCKED-but-expired account) -
resolved by widening the documented precondition to match what T11
actually tests, not what its prose said.

UUID-to-internal-id resolution (lockout_state's PK is account_id, a
Long; this service only ever sees UUIDs) uses two native SQL queries,
not JPQL against the Account entity - no Java-level dependency, so the
existing ArchUnit module-boundary rule holds without a new one. The
first version of that query used an unqualified FOR UPDATE, which
Postgres documents as locking every table in the join - both
lockout_state and accounts - serializing unrelated AccountService
operations (password change, suspend, etc.) against every login
attempt for no reason. Caught independently by both self-review and
Kimi's review with identical evidence; fixed with FOR UPDATE OF ls.

A second review-cycle finding: the insert-on-first-failure path threw
IllegalStateException for an unresolvable UUID while the other two
entry points silently no-op for the same condition. Kimi's citation
for this (claiming the frozen brief promised "no throw") was checked
against the actual brief text and found to be wrong - that sentence
was in an earlier, superseded draft - but the underlying inconsistency
was real regardless of the citation error, and is now resolved: all
three entry points trust the caller's documented precondition (T13
only invokes this service for accounts it has already resolved) rather
than one of three surfacing its own existence error.

Concurrency: two simultaneous failed attempts for the same account
previously risked losing an update (read-evaluate-write with no
locking). The FOR UPDATE OF ls query serializes concurrent evaluations
on lockout_state specifically. One residual, accepted, documented risk
remains: two truly concurrent *first-ever* failures for one account
can both observe "no row exists" and race on insert - the same shape
as AccountService.register's already-accepted duplicate-email race,
not engineered around for the same proportionality reason.

resetLockout(UUID) exists ahead of need, for T14's admin-unlock
endpoint - T11 built LockoutStateMachine.reset() specifically
anticipating this; adding the one-line service method now avoids T14
needing to modify this class later.

68 tests (60 executed and passing here; 8 Testcontainers-backed tests,
including a real two-thread concurrency proof, compile clean but could
not run in this environment - no Docker daemon available. Every
scenario those 8 tests target has an equivalent passing mocked-unit
proof; the Testcontainers run against real Postgres remains a flagged,
unexecuted residual for whoever next has Docker access, not silently
claimed as verified.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java` (new) — JPA entity,
  `account_id` as both PK and FK, no surrogate id.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java` (new) —
  package-private; two native `@Query` methods (`FOR UPDATE OF ls` row lock, scalar id
  resolution).
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java` (new) — validated
  `@ConfigurationProperties` record for the three L4 constants.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` (new) —
  `recordFailedAttempt`, `recordSuccessfulAttempt`, `resetLockout`; wraps `LockoutStateMachine`
  (T11, unmodified).
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified) — two
  new methods, `lock(UUID)`/`unlock(UUID)`, guarded and idempotent. No existing method's body
  changed.
- `services/auth/src/main/resources/application.properties` (modified) — three new
  `themistra.auth.lockout.*` keys (VERBATIM per `design.md` §4c).

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java` (new, 14 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPropertiesTest.java` (new, 4 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java`
  (new, 8 tests, Testcontainers — unexecuted in this environment)
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified — 4
  new tests for `lock`/`unlock`; 42 total, was 38)

**Process artifacts** (`.ai/prompts/auth/T12/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decisions (call direction, precondition widening, native-query approach, pessimistic locking), the
Phase 3/8/11 Kimi reviews and their dispositions (including one corrected citation), the Phase 9
human decision + two bug fixes (lock-scope, existence-check consistency), and the Phase 12 PASS
verdict with full traceability matrix and a self-correction of an inaccurate claim made in
Phases 0-2.

## Summary

Implements `tasks.md` task 12: `LockoutService`, the persistence and `Account`-application layer
around T11's pure decision logic. Four things worth a reviewer's attention: (1) the guarded,
idempotent `AccountService.lock`/`unlock` methods exist specifically because a naive wrapper would
crash on T11's own tested re-lock behavior — this is load-bearing, not defensive-programming
excess; (2) the native `FOR UPDATE OF ls` query is deliberately scoped to avoid locking the joined
`accounts` row, a real production-contention bug caught before merge, not after; (3) all three
`LockoutService` entry points now share one consistent trust boundary for account existence,
after a review cycle that both corrected a wrong citation from Kimi *and* found a real underlying
inconsistency worth fixing regardless; (4) the concurrency fix's test coverage exists but is
unexecuted in this environment — flagged consistently since Phase 5, not a last-minute disclosure.

## Testing performed

`mvn -pl services/auth test` cannot run to completion — the pre-existing, unrelated `token`
package compile failure (tracked since T03, untouched by this branch). This task's own classes
were verified via `javac` against the module's resolved test classpath, then executed via the
JUnit Platform `Launcher` API directly.

**Result: 60/60 executable tests passing** (`AccountServiceTest` 42, `LockoutServiceTest` 14,
`LockoutPropertiesTest` 4), most recently re-run in full at Phase 11, ~800ms, no Spring context,
no database. `LockoutPersistenceIntegrationTest`'s 8 tests (Testcontainers + `@SpringBootTest`)
compile clean but could not execute here — `docker info` fails in this environment. This includes
the concurrency-proving test the frozen brief's Required Tests section explicitly asked for.

Kimi's independent code review (Phase 8) found 4 findings; 1 duplicated self-review's own Finding 1
exactly (lock scope, fixed); 2 more (existence-check asymmetry, viewed from two angles) were
accepted with one citation correction; 1 (missing-row data-integrity edge case) was accepted as
documentation-only. Kimi's independent test review (Phase 11) found 6 gaps; all 6 held up on
inspection and were applied directly — including a concurrency test that closed a gap in this
task's own frozen brief requirements, not just a Kimi suggestion. Full requirement-to-
evidence-to-test traceability, plus a self-correction of an inaccurate "novel precedent" claim
made in earlier phases, is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 12 — "Lockout service."
- **Requirements:** R16, R17, R18, R19 (`requirements.md`), implemented at the persistence/
  `Account`-application layer (decision logic itself is T11's, unmodified).
- **LOCKED decisions:** L4 (`design.md` §4a, constants wired through unchanged from T11's adopted
  formula). L12 (module boundary) — confirmed clean via direct `grep`, corrected an inaccurate
  "first precedent" claim from earlier phases in the process.
- **Named tests:** `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes`,
  `shouldResetLockoutCounterOnSuccessfulLogin` (`package.md` §8) — both present verbatim at this
  service layer, on top of T11's own machine-layer versions.
