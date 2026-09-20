# auth · T08 — Phase 9: Review Resolution

**Human Approval gate.** Self-review (`07-self-review.md`) found 3 findings; Kimi's independent
review (`08-independent-review.md`) validated the two compile-break findings, agreed with the
transaction-boundary observation, and added 3 more.

---

## Accepted and fixed

### Self-review 1 / Kimi Finding 1 — `AccountServiceTest.java` no longer compiles

**Reason accepted:** confirmed by direct compilation — a hard, existing-test build break.

**Change made:** added `@Mock private PasswordPolicy passwordPolicy;` and included it in
`setUp()`'s constructor call.

### Self-review 2 / Kimi Finding 2 — `PasswordPolicyTest.java` doesn't compile, plus a
now-contradicted assertion

**Reason accepted:** confirmed — `PasswordPolicy.validate`'s new `(String, UUID, UUID)` signature
broke all 11 existing call sites; Kimi additionally caught that
`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` asserted `accountUuid`/`actorUuid` are
`null`, which is now structurally impossible to keep true once real UUIDs are threaded through.

**Change made:** added `ACCOUNT_UUID`/`ACTOR_UUID` fixture constants; updated all 11 call sites to
pass both; flipped the two `isNull()` assertions to `isEqualTo(ACCOUNT_UUID)`/
`isEqualTo(ACTOR_UUID)` (AC10).

### Kimi Finding 4 — `PasswordPolicy.validate` doesn't reject `null` UUIDs

**Reason accepted:** low-cost, closes a real risk Kimi correctly identified — a future task-9
caller passing `null` would silently reintroduce the exact audit-context gap this task just closed.

**Change made:** added `Objects.requireNonNull` for both `accountUuid` and `actorUuid` at the top
of `validate`, matching the same intentional-NPE convention used elsewhere in this module (e.g.
`VerificationTokenService`). Added `shouldRejectNullAccountOrActorUuid` to `PasswordPolicyTest`.

---

## Accepted, no code change (documented trade-offs)

### Self-review 3 / Kimi Finding 3 — HIBP network call now runs inside a live `@Transactional` method

**Reason accepted as a trade-off, not fixed:** both reviews agree this isn't a correctness defect
introduced by T08 — it's `PasswordPolicy`'s existing (T03) design being exercised by a real caller
for the first time. The frozen brief explicitly authorized wiring `PasswordPolicy` in as-is. Kimi's
own recommendation (moving the check outside the write transaction) is ADR-level design work, out
of scope for this task. Recorded here so it isn't rediscovered as a surprise when task 9 wires the
same call into `register`/`resetPassword` too.

### Kimi Finding 5 — `InvalidAccountStateException`'s detail exposes the account's current status

**Reason accepted, no change:** the frozen brief explicitly chose to reuse the existing
`InvalidAccountStateException`/`409`/`INVALID_STATE` mapping (Finding 4's resolution at Phase 4).
Checked against precedent: this exact exception already exposes status this way for
`suspend`/`reinstate`/`markDeleted`'s own guards, unchanged by this task. Not an enumeration
concern (L5 doesn't scope this endpoint - confirmed at Phase 0/1) since the caller is already
authenticated as this exact account; learning your own account's current status when an action on
it is rejected is expected self-service behavior, not a leak to a third party.

---

## Rejected

### Kimi Finding 6 — `UUID.fromString(authentication.getName())` has no defensive handling

**Reason rejected:** checked directly against the source. `AccountController.me()`
(`AccountController.java:64`) has used this exact, unguarded pattern since T02 — T08's
`changePassword` (`AccountController.java:126`) only reuses the established convention, it doesn't
introduce a new risk. Kimi itself marked this **Low** confidence and "unreachable" given the
current security configuration. Fixing it only for the new endpoint while leaving the identical,
long-standing pattern in `me()` untouched would be inconsistent and out of T08's scope; a
consistent fix across both would be a separate, deliberate hardening task, not a T08 finding.

---

## Verification

All four affected/related test files (`AccountServiceTest`, `PasswordPolicyTest`,
`AccountControllerTest`, `AccountExceptionHandlerTest`) compiled and ran via the established
`javac` + JUnit Platform `Launcher` method.

**Result: 44/44 tests successful, 0 failed, 0 skipped, ~700ms.**
