# auth · T09 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds no new endpoints and changes no public API; it wires an
already-existing, already-unit-tested policy service into two more internal call paths.

## Commit title

```
Apply PasswordPolicy to registration and password-reset (T09)
```

## Commit message

```
Apply PasswordPolicy to registration and password-reset (T09)

PasswordPolicy (T03) was already applied to change-password at T08.
This task closes the remaining two gaps R8-R10 named: registration
enforced length only via a redundant DTO annotation (no breach check
at all), and password-reset enforced nothing beyond @NotBlank. Both
now call PasswordPolicy.validate, matching change-password's existing
pattern.

Adversarial design review (Phase 3) caught two real ordering bugs the
frozen brief's first draft would have shipped:

- register: validating after the duplicate-email check would have let
  a caller infer whether an email is registered just from the content
  of the password they submit (a policy violation returns 202 for an
  existing email but 400 for a new one) - an enumeration-safety
  regression against L5. Fixed by constructing the Account (which
  already assigns its UUID at construction) before existsByEmail, so
  the policy check runs first and fails identically either way. Human
  decision at Phase 4: reorder within AccountService rather than widen
  Account.register's signature, keeping the change to two files
  instead of touching a shared entity and ~30 test call sites. Cost:
  one BCrypt encode is now spent even on a later-duplicate-rejected
  registration - an accepted, documented trade-off.

- resetPassword: validating after unlock()/changePasswordHash would
  have mutated lockout state before every gate had passed. Fixed by
  moving the check to immediately after the existing eligibility gate,
  before any mutation - proven by an InOrder test spanning the policy
  check, a spied Account, the encoder, session revocation, and audit.

A third finding was reviewed and explicitly accepted as residual risk,
not fixed: a policy-violating password on an otherwise-valid, unused
reset token still returns a response distinguishable from an
invalid-token rejection. Human decision at Phase 4: an attacker who
already holds a valid raw token gains nothing from this signal, since
they could submit a compliant password and complete the reset
directly. A Phase 9 correction (Kimi Finding 8) fixed the code
comment describing this: the token is not durably consumed on a
policy-violation rollback, since consumeForPurpose and the later throw
share one @Transactional boundary - the residual signal is the
response type, not token loss, which if anything weakens the case for
concern further.

RegisterAccountRequest's @Size(min=12,max=128) was removed so
PasswordPolicy is the sole length/breach enforcement point on every
password-setting path, matching PasswordResetConfirmRequest and T08's
ChangePasswordRequest, neither of which ever had DTO-level length
validation.

Kimi's independent code review (Phase 8) raised 9 findings; 7 were
rejected as evaluating the diff against work explicitly scoped to this
phase's own successor (Phase 10 test generation), not a real gap; 1
duplicated an already-logged self-review finding (HIBP calls now
executing inside two more @Transactional public/authenticated
methods, one of them - registration - outside R41's rate-limited
endpoint list); 1 was a genuine documentation-accuracy fix, applied.
Kimi's independent test review (Phase 11) raised 5 gaps; 2 were
factually incorrect (the suggested assertions already existed in the
actual test/handler files, just not spelled out in the Phase 10
manifest's prose); 2 were rejected as either introducing
integration-test infrastructure with zero precedent in this module to
prove a standard framework guarantee, or re-testing message content
that AccountService never touches and PasswordPolicyTest already
covers; 1 led to a manifest wording clarification only, no test
change.

63 unit tests cover both named tests (now exercised by two more real
production call paths), the register/reset policy-integration and
ordering proofs (InOrder across both new call sites), the
enumeration-safety regression guard, the accepted encoder-cost
trade-off, controller-level exception propagation, and the DTO
validation consistency fix - plus every pre-existing test, unmodified,
still passing.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified —
  `register` reordered to construct-then-validate-then-check-duplicate; `resetPassword` gains a
  `passwordPolicy.validate` call between eligibility and mutation; two Javadoc additions, one
  corrected at Phase 9)
- `services/auth/src/main/java/com/themistra/auth/account/dto/RegisterAccountRequest.java`
  (modified — `@Size(min=12,max=128)` removed from `password()`)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified — 1
  test renamed/updated, 6 new tests; 36 total, was 29)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (modified —
  2 new tests; 14 total, was 12)
- `services/auth/src/test/java/com/themistra/auth/account/dto/RegisterAccountRequestValidationTest.java`
  (modified — 1 test replaced; 5 total, unchanged count)

**Process artifacts** (`.ai/prompts/auth/T09/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decisions (register ordering/UUID correlation, accepted encoder cost, reset ordering, accepted
token-validity residual risk, DTO consistency), the Phase 3/8/11 Kimi reviews and their
dispositions, the Phase 9 documentation correction, and the Phase 12 PASS verdict with full
traceability matrix.

## Summary

Implements `tasks.md` task 9: applies `PasswordPolicy` (already production-proven at T08 for
change-password) to `register` and `resetPassword`, the two remaining password-setting call sites
R8-R10 cover. Three things worth a reviewer's attention: (1) `register`'s reordering closes a real
enumeration-safety gap the frozen brief's first draft would have shipped, caught by adversarial
design review before any code was written, not after; (2) the reset-token-validity residual signal
is a deliberate, human-approved trade-off, not an oversight, and its documentation was corrected
mid-pipeline when an independent review caught it overstating what the code actually does; (3) two
separate Kimi review passes (code, then tests) both raised findings that turned out to already be
addressed in the real code or genuinely out of this task's scope — each was verified against
source before being accepted or rejected, not applied or dismissed on the reviewer's word alone.

## Testing performed

Same situation as every task in this chain: `mvn -pl services/auth test` cannot run to completion
due to the pre-existing, unrelated `token` package compile failure (tracked since T03, still
unfixed, not touched by this branch). Verified by compiling every new/changed production and test
class against the module's resolved test-scope classpath via `javac`, then executing via the JUnit
Platform `Launcher` API.

**Result: 63/63 tests passing** (`AccountServiceTest` 36, `AccountControllerTest` 14,
`RegisterAccountRequestValidationTest` 5, `PasswordPolicyTest` 8 — unchanged), most recently
re-run in full at Phase 12, ~800ms, no Spring context, no database.

Kimi's independent code review (Phase 8) found 9 findings; 1 accepted (a Javadoc correction, Phase
9) and applied, 1 confirmed as a duplicate of an already-logged self-review finding (no new
action), 7 rejected with cited evidence (a process misunderstanding about which phase owns test
work). Kimi's independent test review (Phase 11) found 5 gaps; 2 were factually incorrect
(evidence already present in the real files), 2 rejected as introducing unwarranted testing
infrastructure or redundant coverage, 1 resulted in a documentation-only manifest fix. Full
requirement-to-evidence-to-test traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 9 — "Password policy enforcement."
- **Requirements:** R8, R9, R10 (`requirements.md`), fully implemented at all three password-setting
  call sites — `changePassword` already covered at T08, `register`/`resetPassword` newly covered
  here.
- **LOCKED decisions:** L2 (`design.md` §4a, policy content unchanged, newly wired into two more
  callers). L5 confirmed and strengthened for `register` (enumeration-safety ordering now explicit
  and regression-tested, not merely assumed).
- **Named tests:** `shouldRejectPasswordShorterThan12OrLongerThan128`,
  `shouldRejectBreachedPasswordUsingHibpRange` (`package.md` §8) — both pre-existing, unchanged,
  now exercised by two more real production call paths.
