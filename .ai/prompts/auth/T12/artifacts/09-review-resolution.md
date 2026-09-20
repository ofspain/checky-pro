# auth · T12 — Phase 9: Review Resolution

Human-approved disposition of Phase 7 (self-review, 2 findings) and Phase 8 (Kimi independent
review, 4 findings) against the Phase 6 implementation. Phase 7 Finding 1 and Phase 8 Finding 1
are the same finding (identical evidence, independently corroborated); Phase 7 Finding 2 and
Phase 8 Findings 2/3 are the same underlying issue viewed from different angles — treated as one
set below, not six unrelated items.

---

### Finding A — `FOR UPDATE` without a table qualifier locks both `lockout_state` and `accounts`

*(Phase 7 Finding 1 = Phase 8 Finding 1, identical evidence)*

**Disposition:** ACCEPTED.

**Reason:** Confirmed — PostgreSQL's documented behavior for an unqualified `FOR UPDATE` is to
lock every table contributing to the result set. This silently serializes unrelated
`AccountService` operations (password change, suspend, `resetPassword`'s own `unlock()`, etc.)
against every login attempt for that account, which nothing in the frozen brief's Finding 4
disposition or this method's own Javadoc intended.

**Exact change made:** `LockoutStateRepository.java` — appended `OF ls` to the native query's
`FOR UPDATE` clause, scoping the lock to the `lockout_state` alias only. Updated the method's
Javadoc to state the scoping explicitly and explain why (previously said "locking it" without
specifying which table). Verified compiling clean afterward.

---

### Finding B — Asymmetric account-existence handling across the three entry points

*(Phase 7 Finding 2 + Phase 8 Findings 2/3 — Kimi's Finding 2 citation corrected below, substance
retained)*

**Disposition:** ACCEPTED, with a citation correction.

**Citation correction:** Phase 8 Finding 2 claims the frozen brief's Outputs section states
"neither entry point throws for a missing account" and that the `IllegalStateException` throw
directly contradicts it. Verified by direct `grep` of `04-frozen-task-brief.md`'s Outputs
section (lines 121-125): that sentence is **not present** in the frozen brief. It appears only in
the superseded Phase 2 TIB (`02-task-implementation-brief.md:110`) and was not carried forward
when the brief was frozen. There is no literal spec contradiction. The underlying observation —
that `recordFailedAttempt`'s insert path throws while `recordSuccessfulAttempt` and the
blocked-attempt short-circuit silently no-op for the same "unknown account" condition — is real
and independently identified by both reviews regardless of the citation error.

**Reason for accepting:** The frozen brief's own documented trust boundary ("callers are
responsible for only invoking this service for accounts it already knows are eligible") already
establishes that `LockoutService` should not need to defend against a nonexistent account — a
throw in exactly one of three code paths for that same trusted-away condition is an inconsistency
worth removing, not a behavior worth keeping. `resetLockout`'s dependency on
`AccountService.unlock` (which can throw `AccountNotFoundException` via its own pre-existing
`getAccount` lookup) is **not** touched — that is `AccountService`'s own established, pre-existing
contract, unrelated to this task, out of scope to change.

**Exact change made:** `LockoutService.java`, `persistNewOrUpdated` — replaced the
`.orElseThrow(() -> new IllegalStateException(...))` with `.ifPresent(accountId -> ...)`, making a
failed id resolution a silent no-op, matching `recordSuccessfulAttempt`'s existing behavior. Added
a Javadoc note explaining the resulting behavior is a no-op by design (trusting the caller's
precondition) and that it is also structurally harmless: a first-ever failure can never itself
reach the 5-attempt lock threshold, so skipping persistence in this unreachable-in-practice branch
has no observable effect beyond the returned decision. Verified compiling clean afterward.

---

### Finding C — `recordSuccessfulAttempt` no-op on a missing row could leave a `LOCKED` account permanently stuck under data corruption

*(Phase 8 Finding 4, Low confidence)*

**Disposition:** ACCEPTED, documentation-only — no behavior change.

**Reason:** Matches the frozen brief's AC8 exactly (Kimi's own assessment: "only a latent
operational edge case"). Kimi's own primary recommendation was to document the invariant, not to
change code; the alternative (a defensive `AccountService` lookup) would require a new public
method and materially expand this task's scope — disallowed by this phase's guardrails ("do not
change public APIs," "no scope beyond this task").

**Exact change made:** `LockoutService.java`, `recordSuccessfulAttempt`'s Javadoc — added a
paragraph stating the invariant this method relies on (a `LOCKED` account always has a
`lockout_state` row, since only this service ever locks one and always writes the row first) and
what happens if that invariant is ever violated by external corruption: a no-op, not a repair,
explicitly an operator-facing data-integrity scenario. Verified compiling clean afterward.

---

## Summary

- **Accepted:** 3 (Finding A — lock-scope fix; Finding B — existence-check consistency fix, with a
  citation correction; Finding C — documentation only).
- **Rejected:** 0.

All three changes stayed inside the two files already touched in Phase 6
(`LockoutStateRepository.java`, `LockoutService.java`) — no new file, no public API change (the
`IllegalStateException` removal changes a method's *internal* behavior, not its signature), no
renaming, no refactoring or optimization beyond what each finding specifically required. Verified
compiling clean after all three changes (`javac`, same isolated-classpath approach as Phase 6 —
this task's classes still never touch the broken `token` package).
