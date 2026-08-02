# auth · T14 — Phase 9: Review Resolution

Human-approved disposition of Phase 7 (self-review, 1 finding) and Phase 8 (Kimi independent
review, 3 findings) against the Phase 6 implementation. Phase 7's Finding 1 and Phase 8's Finding
2 are the same underlying issue, independently discovered — treated as one, not two.

---

### Finding A — `"user.unlocked"`/`"account.unlocked"` fired even when `Account.status` was never `LOCKED`

*(Phase 7 Finding 1 = Phase 8 Finding 2, corroborated with additional evidence: Kimi confirmed
`UserLifecycleEventPayload` carries the `AccountStatus status` field, so a `user.unlocked` event
could literally be published with `status: SUSPENDED` in its own payload.)*

**Disposition:** ACCEPTED, human-approved: only fire when a real `LOCKED → ACTIVE` transition
occurs.

**Reason:** Confirmed by both reviews independently, and Kimi's additional evidence
(`UserLifecycleEventPayload.java:14`) made the concrete harm unambiguous — the misleading status
would be directly visible in the published event, not just inferable. Escalated to the human
because this narrows Phase 4's Finding 6/7 disposition ("every successful invocation appends a
distinct audit row"), which was approved only considering the `LOCKED` vs. `ACTIVE` contrast, never
`SUSPENDED`/`DELETED`/`PENDING_VERIFICATION`. Decision: only fire when the account was actually
`LOCKED` at call time.

**Exact change made:** `AccountService.java`, `adminUnlock` — captures `wasLocked =
getAccount(accountUuid).getStatus() == AccountStatus.LOCKED` **before** calling `unlock(
accountUuid)` (the local reference's live status would otherwise already read `ACTIVE` post-call,
since it's the same managed entity within one persistence context). `publishLifecycleEvent`/
`recordAudit` are now inside `if (wasLocked)`. Javadoc updated to explain the narrowing and why.
Verified compiling clean afterward.

*(Note: this changes the frozen brief's AC7 in effect — a duplicate call against an already-`ACTIVE`
account now produces zero new audit rows/lifecycle events on the second call, not two identical
ones. This is the direct, intended consequence of the human decision above, not a separate,
undecided deviation.)*

---

### Finding B — Stale Javadoc on `LockoutService.resetLockout`

**Disposition:** ACCEPTED.

**Reason:** Confirmed — the Javadoc still read "for T14's future use; not called by anything in
this task," written at T12 before T14 existed. `AdminAccountController.unlock` (Phase 6, this
task) now calls it as its first statement, making the comment factually wrong, not just stale in
tone.

**Exact change made:** `LockoutService.java` — replaced the "for T14's future use; not called by
anything in this task" sentence with "Called by `AdminAccountController.unlock` (T14, R20 admin
unlock); also intended for any future password-reset-driven unlock." No behavior change. Verified
compiling clean.

---

### Finding C — Two-transaction split widens the race window to *any* concurrent admin operation, not just crash/partition

**Disposition:** CONFIRMED, accepted as a pre-existing, cross-cutting characteristic — no code
change, no new documentation beyond what's already logged here.

**Reason:** Verified: `LockoutStateRepository`'s `FOR UPDATE OF ls` is deliberately scoped to
`lockout_state` only (T12/T13's own accepted design, to avoid contending with unrelated
`AccountService` operations on the `accounts` row); `Account.java` has no `@Version` column
(confirmed by `grep`); `suspend`/`reinstate`/`delete`/`activateEmail` all share the identical
no-locking pattern on `Account.status` writes. This is real, but it is not specific to T14 or a
regression this task introduces — it is a codebase-wide characteristic of every admin lifecycle
transition method. Kimi's own recommendation (c) explicitly frames a real fix as "a cross-cutting
change affecting `suspend`/`reinstate`/`delete`/`activateEmail` too, not just T14" — fixing it only
for `unlock` would be inconsistent with the other four methods and disproportionate scope for this
task. Not escalated to the human as a T14-specific decision, since T14 has no unilateral authority
to redesign locking for four other methods it doesn't touch.

---

## Summary

- **Accepted, code changed:** 2 (Finding A — event-semantics fix, human-approved; Finding B —
  documentation fix).
- **Confirmed, no action (pre-existing, cross-cutting, out of this task's scope):** 1 (Finding C).

Verified compiling clean after both changes (`mvn -pl services/auth clean compile`, zero errors —
confirmed on a clean build). No public API changed (both fixes are internal to existing method
bodies/comments), no refactoring beyond what each finding specifically required, no scope creep
into the four sibling admin methods Finding C's full fix would have required.
