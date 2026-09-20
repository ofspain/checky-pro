# auth · T14 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds one endpoint and one service method to existing files, with no change
to any other endpoint's behavior.

## Commit title

```
Add admin unlock endpoint (T14)
```

## Commit message

```
Add admin unlock endpoint (T14)

Exposes LockoutService.resetLockout (T12, built anticipating this
exact task) through POST /admin/accounts/{accountUuid}/unlock -
ADMIN or COMPLIANCE, same @PreAuthorize shape as this controller's
existing suspend/reinstate. AccountService.adminUnlock is the new
counterpart to suspend/reinstate/delete: reuses the existing guarded
unlock(UUID), audits, and publishes a lifecycle event.

Adversarial review (Phase 3) caught that the original design would
have been the only mutation in AdminAccountController never
publishing to auth.user.lifecycle - every sibling action already
does. Human-approved to add it (user.unlocked) rather than leave a
new, unexplained asymmetry.

Independent review (Phase 7/8, corroborated from two directions) then
caught a real semantic bug the "unconditional audit" framing exposed:
unlock(UUID)'s guard is a no-op for any non-LOCKED status, not just
ACTIVE - so calling this endpoint against a SUSPENDED or DELETED
account would have fired "user.unlocked" with a lifecycle payload
that still showed SUSPENDED, materially misrepresenting what happened.
Human-approved fix: audit and lifecycle events now fire only on a
genuine LOCKED -> ACTIVE transition, narrowing the original AC7
(status-transition idempotent AND side-effect idempotent now, not
just the former).

A third finding (Kimi Phase 8) - that the two-call orchestration
(resetLockout, then adminUnlock, two independent transactions) widens
a race window beyond the crash/partition risk already accepted at
Phase 4 - was confirmed real but left unfixed: every other admin
lifecycle method (suspend, reinstate, delete, activateEmail) shares
the identical no-row-locking pattern on Account.status writes. Fixing
it only for unlock would be inconsistent with the other four; fixing
it for all five is a cross-cutting change well beyond this task.

56 unit tests (49 + 7 across AccountService.adminUnlock and the
controller's delegation/authorization checks) - the first task in
this chain needing no Testcontainers suite at all (Phase 2 scoping:
the underlying mechanics are already integration-tested by T12/T13's
own suites), so for the first time since T11 there is no
"unexecuted in this environment" residual risk to carry forward.
Both `mvn compile` and `mvn test-compile` succeed cleanly on this
branch, re-verified fresh at Phase 12.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified) — new
  `adminUnlock(UUID, UUID)`, inserted after the existing `unlock(UUID)`.
- `services/auth/src/main/java/com/themistra/auth/account/AdminAccountController.java` (modified)
  — new `unlock(UUID, Authentication)`; constructor gains a `LockoutService` dependency (the
  controller's first cross-module dependency).
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` (modified) — one
  Javadoc correction on `resetLockout` (no behavior change): removed the now-inaccurate "not
  called by anything in this task" note.

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified — 7
  new tests for `adminUnlock`; 49 total, was 42).
- `services/auth/src/test/java/com/themistra/auth/account/AdminAccountControllerTest.java`
  (modified — constructor updated for the new `LockoutService` dependency; 2 new tests; 7 total,
  was 5. A shared stub was made `lenient()`, necessitated by one of the new tests and incidentally
  resolving a pre-existing, T13-discovered `UnnecessaryStubbingException` in this same file as a
  side effect).

**Process artifacts** (`.ai/prompts/auth/T14/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decisions (lifecycle event, accepted atomicity risk, allowed self-unlock, no session revocation),
the Phase 3/8/11 Kimi reviews and their dispositions, the Phase 9 human decision + two fixes
(event-semantics narrowing, Javadoc correction), and the Phase 12 PASS verdict.

## Summary

Implements `tasks.md` task 14: the admin unlock endpoint, the last piece of the R16-R20/L4 lockout
feature this multi-task chain has been building since T11. Three things worth a reviewer's
attention: (1) the event-semantics fix is the most consequential change — without it, a caller
action against a `SUSPENDED`/`DELETED`/`PENDING_VERIFICATION` account would have published a
"user.unlocked" event that materially misrepresented what happened, caught by two independent
review passes converging on the same evidence; (2) the accepted two-transaction race (Finding C)
is a real, if low-probability, characteristic this task shares with four other pre-existing admin
methods, not a regression it introduces, and fixing it here alone would be inconsistent rather than
correct; (3) this is the first task since T11 with zero residual "unexecuted in this environment"
risk — the whole test suite actually ran, not just compiled.

## Testing performed

`mvn -pl services/auth clean compile` and `mvn -pl services/auth clean test-compile` **both
succeed with zero errors** — re-verified fresh at Phase 12, on clean (not incremental) builds
after Phase 6 itself caught one stale-compile false-positive earlier in this task.

**Result: 56/56 unit tests passing** (`AccountServiceTest` 49, `AdminAccountControllerTest` 7),
executed via the JUnit Platform Launcher, most recently re-run in full at Phase 11. No
Testcontainers suite for this task (Phase 2 scoping decision) — the underlying `LockoutService`/
`AccountService` mechanics this endpoint orchestrates are already integration-tested by T12's and
T13's own Testcontainers suites, unchanged by this task.

Kimi's independent code review (Phase 8) found 3 findings; 2 accepted (a documentation fix, and
independent corroboration of the self-review's event-semantics finding with sharper evidence); 1
confirmed as real but explicitly out of scope (the cross-cutting transaction-race observation).
Kimi's independent test review (Phase 11) found 5 gaps; all 5 held up and were applied, including
a reflection-based role-expression check chosen deliberately over introducing this module's first
`MockMvc`/`WebTestClient` test for a single assertion. Full requirement-to-evidence-to-test
traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 14 — "Admin unlock endpoint."
- **Requirements:** R20 (`requirements.md`), fully implemented and tested; R43 (audit, widened
  into scope at Phase 1 since the task statement's "and audit" clause has no other governing rule).
- **LOCKED decisions:** L4 (unchanged — no new lockout arithmetic). L12 (module boundary —
  confirmed clean via `grep`, fresh at Phase 12: `AccountService.java` has zero imports from
  `com.themistra.auth.authn`; only the controller gained the new dependency, matching
  `AccountUserDetailsService`'s established precedent).
- **Named test:** `shouldUnlockAccountViaAdminEndpoint` (`package.md` §8) — present verbatim,
  strengthened at Phase 11 to assert the actual lifecycle payload contents, not just the outbox
  call's metadata.
