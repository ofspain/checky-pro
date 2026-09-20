# auth · T08 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds one new authenticated endpoint and, for the first time, wires
already-existing (T03) password-policy logic into a real production call path.

## Commit title

```
Add change-own-password endpoint (T08)
```

## Commit message

```
Add change-own-password endpoint (T08)

Add POST /accounts/me/password, authenticated - the caller's UUID
comes from Authentication.getName(), same pattern as the existing
/me endpoint, never a request-supplied identifier. Verifies the
current password via PasswordEncoder.matches, validates the new
password against PasswordPolicy, updates the hash, and records a
password.changed audit event, in that fixed order (R11).

PasswordPolicy (T03) gets its first production caller here - it
existed as pure, unit-tested domain logic with zero callers until
now. Adversarial design review (Phase 3) caught that the frozen brief
had initially scoped policy enforcement out, following T07's
precedent for password-reset; the task's own named test
(shouldRejectPasswordShorterThan12OrLongerThan128) is mapped directly
to R11 in package.md, making that deferral untenable here. Human
decision at Phase 4: wire it in.

Three more human decisions closed the remaining ambiguity Kimi's
design review surfaced, all recorded at Phase 4:
- Only ACTIVE accounts may change their password. Account.
  changePasswordHash's guard widened from DELETED-only to
  ACTIVE-only - confirmed backward-compatible with T07's
  resetPassword, which always reaches that method with ACTIVE status
  already. The status check runs before the current-password check
  specifically to avoid calling PasswordEncoder.matches against a
  DELETED account's null password hash.
- No refresh-token family revocation on success - unlike T07's
  password-reset, R11 doesn't call for it, and change-password isn't
  a compromise-recovery flow; the caller is already using a valid,
  currently-authenticated session.
- Resubmitting the current password as the new one is allowed, not
  rejected - R11 doesn't restrict it and NIST 800-63B doesn't forbid
  it.

PasswordPolicy.validate also gained accountUuid/actorUuid parameters,
closing a real gap Kimi found: its breach-check-failure audit event
previously always recorded null actor/target, since nothing had ever
called it with real context before. Both parameters are
null-guarded, so a future caller (task 9) can't silently reintroduce
that gap.

Two things intentionally NOT done, both recorded rather than silently
dropped: refresh-token revocation (decision above), and any change to
InvalidAccountStateException's existing behavior of exposing the
account's current status in its problem-detail response - consistent
with how that exception already behaves for every other guarded
transition in this module, not a new risk this task introduces.

61 unit tests cover the named test (now actually exercised by a real
production call path for the first time), every rejection reason
(wrong current password, policy violation, all four non-ACTIVE
statuses), the no-revocation and password-reuse decisions as explicit
regression guards, gate-ordering proofs via Mockito InOrder, DTO
validation and toString() redaction, and both new problem-detail
mappings.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` (modified —
  one new endpoint)
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified — new
  `changePassword` method, new `PasswordPolicy` constructor dependency, new nested
  `CurrentPasswordMismatchException`)
- `services/auth/src/main/java/com/themistra/auth/account/Account.java` (modified —
  `changePasswordHash`'s guard widened from `DELETED`-only to `ACTIVE`-only)
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java` (modified —
  `validate` gains `accountUuid`/`actorUuid` parameters, null-guarded, threaded into the
  breach-check-failure audit)
- `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java`
  (modified — two new mappings)
- `services/auth/src/main/java/com/themistra/auth/account/dto/ChangePasswordRequest.java` (new)
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` (modified — one new
  constant)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified —
  constructor fix, 6 new tests, `InOrder` gate-ordering proofs)
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` (modified —
  11 call-site signature updates, one assertion flipped, 1 new test)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (modified
  — 3 new tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java`
  (modified — 3 new tests)
- `services/auth/src/test/java/com/themistra/auth/account/dto/ChangePasswordRequestValidationTest.java`
  (new — 5 tests)

**Process artifacts** (`.ai/prompts/auth/T08/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decisions (policy wiring, status eligibility, no revocation, password reuse allowed), the Phase
3/8/11 Kimi reviews, and the Phase 12 PASS verdict with full traceability matrix.

**Repository state note:** `12-specification-verification.md` is the only file this phase adds
that isn't already committed (commits `66cb9a4`..`ed6d469` cover Phases 0–11). Nothing has been
committed by this phase — Phase 13 only documents the intended commit; committing remains an
explicit, separate step.

## Summary

Implements `tasks.md` task 8: the change-own-password endpoint, and — for the first time in this
codebase — a real production caller of T03's `PasswordPolicy`. Three things worth a reviewer's
attention: (1) the frozen brief's central tension, caught by adversarial design review: the task's
own named test is mapped to R11 specifically, which made deferring policy enforcement to task 9
(the pattern T07 established for password-reset) untenable here — resolved by an explicit human
decision to wire it in now, not silently; (2) `Account.changePasswordHash`'s guard was
deliberately widened, and separately verified backward-compatible with T07's `resetPassword`
rather than assumed safe; (3) `PasswordPolicy.validate`'s new signature closes a real
audit-context gap (previously always recording `null` actor/target) and guards against a future
caller silently reintroducing it.

## Testing performed

Same situation as every task in this chain: `mvn -pl services/auth test` cannot run to completion
due to the pre-existing, unrelated `token` package compile failure (tracked since T03, still
unfixed, not touched by this branch). Verified by compiling the new/changed test classes and their
real transitive dependency chain directly with `javac` against the module's resolved test-scope
classpath, then executing via the JUnit Platform `Launcher` API.

**Result: 61/61 tests passing**, ~800ms, no Spring context, no database.

Kimi's independent design review (Phase 8) found 6 findings; the blocker (policy enforcement) and
one High finding (account-status gate) were resolved by human decision at Phase 4, closing all 9
Phase 3 findings between them. Kimi's independent test review (Phase 11) found 7 gaps; 5 were
applied (encoder call-argument verification, gate-ordering `InOrder` proofs, a dedicated DTO
validation test, audit assertions on the password-reuse path, a stable-response test for the new
exception type), 2 were rejected with cited evidence (a `MockMvc`/`@WebMvcTest` integration test
and a log-capture test, both introducing testing patterns with zero precedent anywhere in this
module). Full requirement-to-evidence-to-test traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 8 — "Change own password."
- **Requirements:** R11 (`requirements.md`), fully implemented — unlike T07, no clause was
  deferred; Phase 4's human-approval gate resolved every open question before implementation.
- **LOCKED decisions:** L2 (`design.md` §4a, policy content unchanged, newly wired in), L3
  (BCrypt encoder, reused for both operations). L5/L11 confirmed not applicable to this endpoint,
  not silently assumed.
- **Named test:** `shouldRejectPasswordShorterThan12OrLongerThan128` (`package.md` §8) —
  implemented and passing, now exercised by a real production call path for the first time.
