# auth · T10 — Phase 6: Implementation (Test-Only Task)

T10's task statement is "Add tests..." — no production code is authorized or needed (frozen brief
Files NOT to Modify explicitly lists every production class). Per this phase's own guardrail
("Do NOT write tests here (that is Phase 10) unless the task itself is test-only"), the two tests
planned at Phase 5 are implemented here, not deferred.

## Changes

### `VerificationTokenServiceTest.java`

Added `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose`, placed
after `shouldRejectConsumeForPurposeWhenAccountIsUnusable` (the last of the existing
`PASSWORD_RESET`-side `consumeForPurpose` tests) for locality with its sibling tests. One test
method, five cases in sequence — not-found, expired, already-used, deleted-account,
suspended-account — each calling `consumeForPurpose(rawToken, EMAIL_VERIFY)` and asserting
`.isEmpty()`, with `verify(tokenRepository, never()).markConsumed(...)` on the two account-level
cases (mirroring the existing `shouldRejectConsumeForPurposeWhenAccountIsUnusable`'s
never-markConsumed assertion). Structure mirrors the existing `PASSWORD_RESET`-side five-test
pattern exactly, consolidated into one method to match the style of the existing `EMAIL_VERIFY`-side
named test (`shouldNotRevealAccountExistenceForInvalidVerificationToken`, line 93), which the new
test is deliberately named to echo — it's the `consumeForPurpose` analog of that test, not a
replacement for it. Wrong-purpose is not repeated (frozen brief Finding 2's resolution — already
covered by `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify`, cited in the new test's
comment).

Maps to: frozen brief AC2a (token-level reasons: not-found, expired, used — wrong-purpose cited,
not duplicated) and AC2b (account-level reasons: deleted, suspended).

### `AccountExceptionHandlerTest.java`

Added `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`,
placed immediately after the existing
`onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` for locality. Two
independently constructed `VerificationTokenRejectedException` instances (standing in for a
verify-email rejection and a reset rejection respectively) are passed through
`handler.onVerificationTokenRejected(...)`, and the two resulting `ProblemDetail`s are asserted
equal on `status`/`type`/`title`, with both `detail` fields asserted `null`. Comment explicitly
distinguishes this from the existing sibling test: that one proves uniformity *within* one
rejection surface (two reasons, one surface); this one proves it *across* surfaces (R5 vs. R15).

Maps to: frozen brief AC4, at the handler layer per Finding 5's resolution — confirmed zero Spring
context needed (this file's existing `handler = new AccountExceptionHandler()` pattern, unchanged).

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Not modified — already satisfied by pre-existing tests, cited not re-tested (frozen brief Finding 3/4's resolution) |
| AC2a | Done — token-level reasons (not-found, expired, used) proven via the new consolidated test; wrong-purpose cited from the existing `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` |
| AC2b | Done — account-level reasons (deleted, suspended) proven via the same new test |
| AC3 | Not modified — already satisfied by five pre-existing `PASSWORD_RESET`-side tests, regression-only |
| AC4 | Done — proven directly by the new `AccountExceptionHandlerTest` cross-surface test |

## Deviations from the plan

None. Both new tests match `05-implementation-plan.md`'s planned structure, naming, and placement
exactly.

## Build verification

`mvn -pl services/auth compile`/`test` still cannot run to completion — the pre-existing, unrelated
`token` package compile break (tracked since T03) blocks it. Verified instead by compiling both
changed files directly against the module's resolved classpath, then running the full account-test
set (both new tests plus every existing test in both files, plus every T09 file as a
cross-task regression check) via the JUnit Platform Launcher:

```
javac -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java:services/auth/src/test/java \
  VerificationTokenServiceTest.java AccountExceptionHandlerTest.java
java ... RunTests   # selectClass for AccountServiceTest, AccountControllerTest,
                     # RegisterAccountRequestValidationTest, PasswordPolicyTest,
                     # AccountExceptionHandlerTest, VerificationTokenServiceTest
```

**Result: 91/91 tests passing** (63 from T09's five files, unchanged; 28 across the two T10-touched
files, including the 2 new tests), no Spring context, no database.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 10.
- Requirements: R2 (unchanged, already satisfied), R5 (AC2a/AC2b closed), R15 (unchanged, already
  satisfied), L5 (AC4 closed — cross-surface consistency now directly proven).
- Frozen brief: `04-frozen-task-brief.md`, both Files to Modify are covered exactly; no file outside
  that list was touched.
