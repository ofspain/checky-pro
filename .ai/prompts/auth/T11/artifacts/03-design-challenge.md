# auth · T11 · Phase 3 — Design Challenge Findings

Reviewer: Kimi 2.7 (adversarial review of the Phase 2 task implementation brief).
Scope: `LockoutStateMachine` only; no T12–T14 redesign, no implementation.

---

## 1. Output snapshot omits `lastFailedAt`

- **Issue:** The brief’s output list (`failedAttempts`, `lockedUntil`, `lockCount`, status-change flags, blocked flag) does not contain the new `lastFailedAt` value.
- **Severity:** High
- **Evidence:**
  - R16 increments the counter on a failed login; the persistence layer must store the timestamp of that failure so R19 can compute decay on the next attempt.
  - The brief already lists `lastFailedAt` as an input but never says how it is updated, nor that the caller (T12) must persist it.
  - Without this field in the result, T12 has no machine-authoritative value to write to `lockout_state.last_failed_at`, risking stale timestamps and incorrect decay.
- **Recommended brief amendment:** Add `lastFailedAt: Instant | null` to the output snapshot, define that a failed attempt sets it to `now`, and that a successful reset clears it to `null`.

---

## 2. `failedAttempts` state at and after a lock event is undefined

- **Issue:** R17 says reaching 5 attempts within the window transitions to locked and increments `lock_count`; the brief does not state what happens to `failedAttempts` at that instant.
- **Severity:** High
- **Evidence:**
  - If `failedAttempts` stays at 5 after locking, then on the first post-lock failure (after `lockedUntil` expires) the counter is already at the threshold and can immediately re-lock, depending on the `lastFailedAt` age.
  - This makes the 5-attempt budget effectively persistent until a successful login resets it (R18), which is a harsher interpretation than a typical rolling-window lockout and is not explicitly adopted anywhere in the spec.
  - AC3 only covers the *successful* post-lock attempt; there is no test for a *failed* attempt shortly after lock expiry.
- **Recommended brief amendment:** State explicitly whether the 5th failure leaves `failedAttempts` at 5, resets it to 0, or resets it to 1. Add an acceptance test for a failed attempt immediately after lock expiry.

---

## 3. Rolling 30-minute window cannot be implemented from the proposed snapshot

- **Issue:** The brief describes a rolling 30-minute window, but the input snapshot only carries `failedAttempts` and `lastFailedAt`. A true rolling window needs the timestamp of the oldest counted failure (or the full history).
- **Severity:** High
- **Evidence:**
  - R17: “5 failed attempts within a rolling 30-minute window.”
  - The brief instead encodes R19 as a binary decay rule based solely on whether `now - lastFailedAt > 30 min`. This is an approximation: a burst of failures at t0…t0+3min followed by a failure at t0+31min will count as attempt 5 even though the first failure is outside the 30-minute window.
  - AC5 introduces “elapsed == 30:00 from the *window start*,” but the snapshot does not include a window-start value.
- **Recommended brief amendment:** Either (a) add `windowStart: Instant` (timestamp of the current window’s first failure) to the snapshot/result and compute the 5-in-window check against it, or (b) explicitly adopt the simplified “decay after 30 min of inactivity” rule as the approved interpretation of R17/R19 and remove the rolling-window language.

---

## 4. “ACTIVE-eligible” is undefined

- **Issue:** The brief uses the phrase “`ACTIVE`-eligible account” in R16, but this is not a defined status or rule in `requirements.md`, `design.md`, or `agents.md`.
- **Severity:** Medium
- **Evidence:**
  - `requirements.md` R16 applies to an `ACTIVE` account only; R21 separately handles `LOCKED`, `SUSPENDED`, `DELETED`, and non-existent accounts with uniform failure responses.
  - The state machine has no account-status input, so it cannot itself enforce the eligibility rule. If `LockoutService` (T12) calls it for a `PENDING_VERIFICATION` or `DELETED` account, the machine will still increment counters.
  - This shifts a security-critical precondition out of the state machine and into T12 without documenting it.
- **Recommended brief amendment:** Define “ACTIVE-eligible” as `status == ACTIVE` only, and add a precondition note that T12 must not invoke the machine for any other account status.

---

## 5. Behaviour while the account is currently locked is ambiguous

- **Issue:** The brief says the caller needs a `blocked` flag before applying an outcome, but it does not say what the result is if a failed/succeeded attempt is evaluated while `now < lockedUntil`.
- **Severity:** Medium
- **Evidence:**
  - Inputs include `lockedUntil`, and the output includes a `blocked` flag, implying the machine can detect this state.
  - It is unclear whether the machine should ignore the attempt outcome entirely, increment `failedAttempts` anyway, or refresh `lastFailedAt`.
  - For enumeration safety (R21/L5), callers will reject the attempt before password validation, so counting those failures artificially inflates the counter after the account is already locked.
- **Recommended brief amendment:** State that when `now < lockedUntil`, the machine returns `blocked=true` and leaves `failedAttempts`, `lastFailedAt`, `lockedUntil`, and `lockCount` unchanged, regardless of the supplied attempt outcome.

---

## 6. AC6 resets the counter on *any* successful login, but the requirements only mandate reset after a post-lock login

- **Issue:** AC6 requires resetting `failedAttempts` and `lockCount` on a successful login whenever `failedAttempts > 0`, even if the account was never locked.
- **Severity:** Medium
- **Evidence:**
  - R18 resets only after a locked account’s lockout interval has elapsed: “WHEN a locked account’s lockout interval has elapsed, THEN the system SHALL allow the next authentication attempt; IF it succeeds, THEN the system SHALL transition the account to ACTIVE and reset the failed-attempt counter and `lock_count`.”
  - R19 provides the alternative decay path (30 min of no failures). It does not say that success resets pre-lock failures.
  - The named test `shouldResetLockoutCounterOnSuccessfulLogin` maps to R16 in `package.md` §8 (likely a numbering typo), which further confuses the intended scope.
- **Recommended brief amendment:** Either confirm AC6 as an intentional extension of R18 and record it as an implementer/Phase-4 decision, or restrict the reset to successful logins that occur at or after `lockedUntil`.

---

## 7. AC4 and AC5 use contradictory boundary definitions

- **Issue:** AC4 defines the 30-minute boundary relative to `lastFailedAt`, while AC5 refers to “elapsed == 30:00 from the *window start*.” These are not the same moment.
- **Severity:** Medium
- **Evidence:**
  - AC4: “`Duration.between(lastFailedAt, now)` strictly greater than 30 minutes decays `failedAttempts` to 0 … Exactly 30 minutes elapsed does not decay.”
  - AC5: “5th failure exactly at the 30-minute window boundary (elapsed == 30:00 from the *window start*, not `last_failed_at` of the 5th attempt) still locks.”
  - If the window start is the first failure and the 5th failure occurs 30 min after that but only 1 min after the 4th failure, AC4 says no decay and AC5 says lock — consistent by coincidence. But if the 5th failure is > 30 min after the 4th failure yet still within 30 min of the first failure, the two rules conflict on whether to decay first.
- **Recommended brief amendment:** Remove the “window start” wording from AC5 or align it with AC4 by computing the boundary exclusively from `lastFailedAt` (after first resolving Finding 3).

---

## 8. Admin unlock and password-reset unlock are not represented in the state machine

- **Issue:** The brief excludes R20 (admin unlock) and the password-reset unlock path from T11, but both must clear `failedAttempts`, `lockCount`, and `lockedUntil`.
- **Severity:** Medium
- **Evidence:**
  - R20: admin unlock transitions `LOCKED → ACTIVE` and clears the counter and `lock_count`.
  - `AccountService.resetPassword` (already implemented) calls `account.unlock()` for a locked account; it does not currently clear lockout counters, which would leave the account `ACTIVE` but with stale lockout state.
  - Since T11 is the single source of lockout-transition logic, excluding these reset paths means T12/T14 must implement the same clearing logic in multiple places, creating a duplication/bug hazard.
- **Recommended brief amendment:** Add a `reset()` (or `clearCounters()`) transition to the state machine that returns a zeroed snapshot, and note that T12 admin unlock and T07/T08 password-reset success will call it.

---

## 9. Requirement mapping for named tests is inconsistent

- **Issue:** `package.md` §8 maps the named lockout tests to R15/R16, while `requirements.md` numbers the corresponding rules as R16/R17/R18/R19.
- **Severity:** Low
- **Evidence:**
  - `package.md`: `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` → R15; `shouldResetLockoutCounterOnSuccessfulLogin` → R16.
  - `requirements.md`: lockout rules are R16–R19.
  - The brief references R16–R19 / L4 correctly but does not reconcile the package.md numbering.
- **Recommended brief amendment:** Add a cross-reference note in the named-tests section mapping each named test to the correct `requirements.md` rule ID (R17 for the lock test, R18 for the reset test) and flag the `package.md` typo under Open Questions.

---

## 10. No invariants defined for corrupted/edge inputs

- **Issue:** The brief does not state how the machine behaves when given negative `failedAttempts`, negative `lockCount`, `lockCount > 0` while `lockedUntil == null`, or `lockedUntil` in the past with `lockCount == 0`.
- **Severity:** Low
- **Evidence:**
  - The snapshot is described as mirroring nullable DB columns, but DB corruption or caller bugs can produce inconsistent snapshots.
  - `effectiveDurationMinutes = baseLockMinutes * 2^lockCountBeforeThisLock` has no upper bound; a very large `lockCount` could overflow `Duration`.
  - Without documented invariants, T11 unit tests cannot assert error behavior, and T12 cannot know what preconditions to enforce.
- **Recommended brief amendment:** Add an “Input invariants” subsection: `failedAttempts >= 0`, `lockCount >= 0`, and define the machine’s response to invalid snapshots (e.g., throw `IllegalArgumentException`, or clamp). Also note the theoretical `Duration` overflow for high `lockCount` and whether it should be capped.

---

## 11. Referenced contract artifacts do not exist yet

- **Issue:** The brief’s header lists `contracts/api/auth.yaml`, `contracts/api/token-claims.md`, `contracts/events/auth/email-requested.v1.schema.json`, and `contracts/events/auth/security-audit.v1.schema.json` as relevant contracts, but only `contracts/events/auth/user-lifecycle.v1.schema.json` is present in the repo.
- **Severity:** Low
- **Evidence:**
  - The security-audit contract in particular is needed later to emit the `account.locked` audit event (R17/R43). T11 does not emit events, but T13 will consume that contract.
  - T11’s result flags (`shouldNowBeLocked`) are the only contract-relevant output; without a contract definition, T13 cannot know what metadata (e.g., lock duration) to include.
- **Recommended brief amendment:** Keep the contract references but add an Open Question/dependency noting that `contracts/events/auth/security-audit.v1.schema.json` must define an `account.locked` event shape before T12/T13 can act on the state machine’s lock signal.

---

## 12. Successful-attempt semantics while still locked are unspecified

- **Issue:** If a caller erroneously passes `outcome=success` while `now < lockedUntil`, the brief does not say whether the machine ignores it, resets counters, or treats it as blocked.
- **Severity:** Low
- **Evidence:**
  - The output includes both `blocked` and status-change flags; callers are expected to check `blocked` before applying an outcome, but the machine should still have deterministic behaviour if misused.
  - A partial reset-only-on-success could create an inconsistent `LOCKED`/`ACTIVE` state if the caller naïvely applies the result.
- **Recommended brief amendment:** State explicitly that when `blocked=true`, the attempt outcome is disregarded and no state change occurs.

---

*End of findings. No implementation or redesign performed; Phase 4 human approval should fold accepted findings into the frozen brief.*
