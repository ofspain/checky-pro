# auth · T14 — Phase 12: Specification Verification

Verifying the final implementation (`AccountService.adminUnlock`, `AdminAccountController.unlock`)
and test suite (56 executed, 0 unexecuted — the first task in this chain with zero Testcontainers
tests, by deliberate Phase 2 scoping) against `spec/auth-service/requirements.md`, `design.md`,
and `tasks.md` for T14 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R20** — admin unlock transitions `LOCKED → ACTIVE`, clears the counter and `lock_count` | Yes | `AdminAccountController.java:74-77` (`unlock`, `@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")`, calls `lockoutService.resetLockout` then `accountService.adminUnlock`); `AccountService.java:346-354` (`adminUnlock`, reuses `unlock(UUID)`'s guard) | `shouldUnlockAccountViaAdminEndpoint` (named), `unlockCallsResetLockoutThenAdminUnlockWithTheAuthenticatedActor` | No | No |
| **R43** — security-relevant actions audited via the outbox | Yes | `AccountService.java:351-352` (`publishLifecycleEvent`/`recordAudit`, both conditioned on a real transition per Phase 9 Finding A) | `shouldUnlockAccountViaAdminEndpoint` (asserts both the audit row and the lifecycle payload's `accountUuid`/`status`) | No | No |

**Named test (`package.md` §8):**
- `shouldUnlockAccountViaAdminEndpoint` — exists verbatim (`AccountServiceTest.java:517`), maps to
  R17 in `package.md` — the same pre-existing numbering drift confirmed at every prior task in
  this chain (T09, T11-T13); the real match is R20, confirmed by this task's own header and
  `requirements.md`'s exact text.

---

## Beyond the literal task statement: what Phase 3/8/9's adversarial review actually changed

R20's text says only "transition to `ACTIVE`, clear counters, audit." Three things this task
delivers go beyond that literal reading, all human-approved or independently re-derived through
review, not silently added:

- **Lifecycle event** (`"user.unlocked"` on `auth.user.lifecycle`) — not named by R20 at all;
  added because every sibling `AdminAccountController` mutation already publishes one, and leaving
  `unlock` as the sole silent exception would have been the actual inconsistency (Phase 3 Finding
  1, human-approved at Phase 4).
- **Conditional audit/lifecycle firing** (only on a genuine `LOCKED → ACTIVE` transition, not
  unconditionally on every call) — the frozen brief's original AC7 required unconditional firing;
  Phase 7/8 independently discovered that "unconditional" would let a call against a `SUSPENDED`/
  `DELETED`/`PENDING_VERIFICATION` account emit a `"user.unlocked"` event whose own payload still
  showed the unchanged status. Human-approved narrowing at Phase 9, verified: `AccountService.java:
  347-353` captures `wasLocked` before mutating and gates both side effects on it.
- **Stale `LockoutService.resetLockout` Javadoc corrected** (Phase 9 Finding B) — a documentation
  fix with no behavioral effect, closing a comment that had been factually wrong since this task's
  own Phase 6 made it inaccurate.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. Both the endpoint and its authorization requirement are
implemented exactly as R20 states, plus the audit trail R20's "and audit" clause requires.

**(2) Does it satisfy every acceptance criterion?** All seven (AC1-AC7) from the frozen brief are
implemented, with AC7 narrowed by the human-approved Phase 9 fix (documented above, not a silent
deviation from what was approved — the frozen brief itself is unmodified per this pipeline's own
rule that frozen briefs aren't amended after Phase 4; the narrowing is recorded in Phase 9's
resolution log instead). All seven map to at least one passing test; the four non-`LOCKED`
statuses (`ACTIVE`, `SUSPENDED`, `DELETED`, `PENDING_VERIFICATION`) are each individually tested
for the no-op/no-audit/no-publish behavior (Phase 11 Gap 1), not just the two originally covered.

**(3) Does it violate any LOCKED decision?** No. L4 unchanged — no new lockout arithmetic. L12
verified clean this phase via a fresh `grep`: `AccountService.java` has zero imports from
`com.themistra.auth.authn`; only `AdminAccountController` carries the new cross-module dependency,
matching `AccountUserDetailsService`'s already-established precedent (T12 Phase 12).

**(4) Remaining risks:**
- **Finding C from Phase 9 (two-transaction race, confirmed real, explicitly out of scope):**
  `LockoutService.resetLockout` and `AccountService.adminUnlock` commit in two independent
  transactions with no shared lock spanning both. A concurrent `suspend`/`delete` against the same
  account, interleaved between the two calls, could in principle produce a `"user.unlocked"` event
  for an account the database now shows as `SUSPENDED`/`DELETED`. Verified at Phase 8/9 that this
  is a codebase-wide characteristic shared by every admin lifecycle method (`suspend`, `reinstate`,
  `delete`, `activateEmail` all write `Account.status` without row-level or optimistic locking) —
  not a regression this task introduces, and fixing it only for `unlock` would be inconsistent
  with the other four. Explicitly not fixed here; a cross-cutting concern for a future task, not
  T14's to resolve unilaterally.
- **`AdminAccountControllerTest`'s pre-existing `UnnecessaryStubbingException`
  (`getDelegatesDirectlyWithoutRequiringAnActor`), first discovered at T13 Phase 10:** resolved as
  a side effect of Phase 11's `lenient()` fix (itself necessitated by this task's own new
  reflection test, not a deliberate fix-the-old-issue effort) — noted here for completeness, not
  claimed as an intentional T14 contribution.
- `contracts/api/auth.yaml` still does not exist anywhere in the repo (Phase 4 Finding 5,
  re-confirmed this phase by directory listing) — same gap carried since T11, tracked explicitly
  again rather than silently re-citing "same as before."
- `package.md` §11 Q2 (rate-limit thresholds) remains unresolved by the spec author — unrelated to
  this task, unchanged.

---

## Verdict

**PASS** — R20 and its audit requirement are fully implemented and tested, all seven acceptance
criteria are met (one narrowed by explicit human decision at Phase 9, not silently deviated from),
no LOCKED decision is violated, module boundaries are clean (re-verified fresh this phase, not
just carried forward), and both `mvn -pl services/auth clean compile` and `clean test-compile`
succeed with zero errors. This is the first task in this entire chain with **zero** unexecuted
tests — no Testcontainers suite was needed or added, so there is no residual "unverified in this
environment" risk to carry forward, unlike every prior task since T12.
