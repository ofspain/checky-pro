# auth · T05 — Phase 9: Review Resolution

**Human Approval gate.** Decisions below made by femi, applied by the model. Self-review
(`07-self-review.md`) found Findings 1 and 2 independently; Kimi's independent review
(`08-independent-review.md`) confirmed both and added Findings 3–7.

---

## Accepted

### 1 — `issue`'s collision-retry loop doesn't work correctly against PostgreSQL
(Self-review Finding 1 + Kimi Finding 1)

**Reason accepted, with a design change from the original Phase 4 spec:** Postgres aborts the
entire transaction on the first constraint violation, so a same-transaction retry loop cannot
work as originally specified (Phase 4 Finding 6: "retry up to 3 times"). Building correct
`REQUIRES_NEW`-per-attempt retry machinery would add real architectural complexity (a
self-injected proxy or a new collaborator bean) for an event with ~2⁻²⁵⁶ probability. Per
explicit human decision, simplified instead of fixed-as-specced: single insert attempt,
`IllegalStateException` (with the original exception chained as cause) on collision.

**Change made** (`VerificationTokenService.java`): removed `MAX_ISSUE_ATTEMPTS` and the retry
loop; `issue` now makes one `saveAndFlush` attempt, catching `DataIntegrityViolationException`
and rethrowing as `IllegalStateException("Verification token hash collision on issue", e)`.
Javadoc updated to explain why no retry loop exists.

### 2 — `VerificationTokenResult`'s auto-generated `toString()` leaks the raw token
(Self-review Finding 2 + Kimi Finding 2)

**Reason accepted:** direct, currently-live violation of the frozen brief's explicit Finding 10
requirement; real secret-leakage risk the moment any future caller logs the result.

**Change made:** overridden `toString()` on `VerificationTokenResult` to include only
`accountUuid` and `purpose`, never `rawToken`.

### 3 — `consume`'s account-usability check isn't atomic with the mark-used write
(Kimi Finding 3)

**Reason accepted:** cheap to close (one extra read, same transaction) and keeps R5's uniform
rejection guarantee airtight against the narrow window where an account is deactivated between
the pre-check and the atomic consume.

**Change made:** `consume` now re-resolves account usability *after* `markConsumed` succeeds,
returning `Optional.empty()` if the account is no longer usable — even though the token has
already been spent by that point (accepted trade-off: the token is burned in this rare
interleaving, but no unusable-account UUID is ever returned).

### 5 — `invalidateActive` widens lock scope by not filtering out already-expired tokens
(Kimi Finding 5)

**Reason accepted:** functionally harmless but a free, low-risk improvement — one added clause.

**Change made** (`VerificationTokenRepository.java`): `invalidateActive`'s query now includes
`AND t.expiresAt > :now`.

### 6 — `ttlMinutes` has no upper bound (`Instant` overflow risk)
(Kimi Finding 6)

**Reason accepted:** matches `agents.md`'s "fail startup on invalid config" intent; trivial
addition.

**Change made** (`VerificationTokenProperties.java`): added `@Max(525_600)` (one year in
minutes) alongside the existing `@Min(1)`.

### 7 — Collision catch-block is overly broad
(Kimi Finding 7)

**Reason accepted, lighter-weight than proposed:** now moot as a "masks a retry" concern since
Finding 1's fix removes the retry loop entirely. Rather than inspecting the exception's cause
chain for a specific constraint name (fragile, DB-version-dependent), the original
`DataIntegrityViolationException` is chained as the `cause` of the thrown `IllegalStateException`
(see Finding 1's change) — so if it were ever caused by something other than a `token_hash`
collision, the real root cause remains visible in the stack trace rather than being masked by a
generic message.

---

## Rejected

### 4 — No unit tests exist yet
(Kimi Finding 4)

**Reason rejected (for Phase 9):** by design, this pipeline splits implementation (Phase 6) from
test generation (Phase 10) — same precedent as T03. Not disputing the concern; it's simply
Phase 10's job, not a Phase 9 code-defect fix.

---

## Summary

6 accepted (1, 2, 3, 5, 6, 7), 1 rejected as out-of-phase-scope (4, carried forward to Phase 10).
Finding 1's fix is a genuine, human-approved simplification of Phase 4's original retry
requirement, not a pure gap-fill — recorded explicitly above. All four modified files
(`VerificationTokenService.java`, `VerificationTokenRepository.java`,
`VerificationTokenProperties.java`) recompiled successfully (verified via targeted `javac`
against the module's dependency classpath, bypassing the pre-existing, unrelated `token`-package
build failure).
