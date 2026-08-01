# auth · T11 — Phase 9: Review Resolution

Human-approved disposition of Phase 8's (Kimi) 5 findings against the Phase 6 implementation and
Phase 7 self-review. Phase 7 and Phase 8 converged independently on Findings 1/2/4/5 below
(same file:line evidence, same recommendations) — treated as one corroborated set, not five
unrelated claims.

---

### Finding 1 — Stale `lockedUntil` returned when a post-unlock failure does not re-lock

**Disposition:** ACCEPTED.

**Reason:** Confirmed — direct violation of the frozen brief's own Outputs contract (`lockedUntil`
must be "null if not/no-longer locked"). Corroborated independently by both Phase 7 self-review
and Phase 8 independent review with identical evidence.

**Exact change made:** `LockoutStateMachine.java`, `applyFailure`'s non-locking branch (was lines
79-83) now returns `null` for `lockedUntil` instead of echoing `snapshot.lockedUntil()` through
unchanged. `lockCount` is still passed through unmodified — L4 only resets `lockCount` on success
(R18), untouched by this fix.

---

### Finding 2 — No `UNLOCK` status signal when the unlock boundary is crossed by a failed attempt

**Disposition:** ACCEPTED — human-approved: emit `UNLOCK` (mirror `applySuccess`).

**Reason:** Escalated to the human for explicit decision (both reviews flagged this as a genuine,
unresolved design question, not a mechanical bug). Decision: whenever a call clears a previously
non-null `lockedUntil` back to `null` — success or a non-relocking failure alike — the machine
signals `UNLOCK`, keeping `Account.status` and `lockout_state.locked_until` always in sync and
closing the inconsistent-pair risk Kimi flagged. Applied using the exact same conditional
`applySuccess` already used, reused in the sibling branch — no new logic invented.

**Exact change made:** Same branch as Finding 1. `applyFailure`'s non-locking return now computes
`AccountStatusChange statusChange = snapshot.lockedUntil() != null ? UNLOCK : NONE` (was hardcoded
`NONE`), matching `applySuccess`'s existing pattern. Added a class-Javadoc paragraph documenting
this rule and citing "Phase 9" so a future reader can trace the decision.

---

### Finding 3 — Required unit-test artifact is missing

**Disposition:** REJECTED.

**Reason:** False premise, same phase-boundary pattern already established at T09 Phase 9 Finding
1. The Phase 6 prompt (`06-implementation.md`) states explicitly: "Do NOT write tests here (that
is Phase 10) unless the task itself is test-only." T11 is not test-only. The Phase 5 plan
(`05-implementation-plan.md`) places `LockoutStateMachineTest.java` at its own numbered step,
distinct from the production-code step — Phase 6's implementation notes already recorded this
explicitly ("no test file touched in this phase... T11's own unit tests are still deferred to the
framework's normal test-generation phase"). Not a blocker; `LockoutStateMachineTest.java` remains
scheduled for Phase 10.

---

### Finding 4 — No positivity validation for `maxAttempts`, `decayWindow`, or `baseLockDuration`

**Disposition:** ACCEPTED.

**Reason:** Confirmed by both reviews independently. Low risk, no public API change — adds runtime
validation inside the existing constructor, directly mirroring the precedent already established
for `LockoutSnapshot`'s compact constructor (AC9). Closes a real gap: an unvalidated
`maxAttempts <= 0` would silently lock every account on its first failure with no error signaling
the misconfiguration, and this class has no other enforcement point of its own (T12's
`LockoutProperties` validation is a separate, later concern, not a substitute for this class being
safe to construct directly).

**Exact change made:** `LockoutStateMachine.java` constructor now throws `IllegalArgumentException`
for `maxAttempts <= 0`, `!decayWindow.isPositive()`, or `!baseLockDuration.isPositive()`, in
addition to the existing null checks. No signature change — same three parameters, same types.

---

### Finding 5 — Extreme `lockCount` shifts can wrap to a negative multiplier

**Disposition:** CONFIRMED, no code-behavior change; documentation only.

**Reason:** Both reviews (Phase 7 Finding 4, Phase 8 Finding 5) independently concluded this is the
exact theoretical limit already reviewed and explicitly accepted in the frozen brief's Finding 10
disposition ("L4 states no cap... a documented, accepted, theoretical limit, not a defect to fix").
Kimi's own recommendation agrees: "No code change required." Adding a cap or overflow guard would
change L4's explicitly no-cap behavior, which is out of scope for a resolution phase.

**Exact change made:** Added a one-line, non-behavioral comment on `effectiveLockDuration` pointing
back to this accepted disposition, exactly as Kimi's recommendation suggested, so a future
maintainer doesn't mistake the missing cap for an oversight.

---

## Summary

- **Accepted:** 3 (Finding 1 — bug fix; Finding 2 — human-approved design decision, applied;
  Finding 4 — constructor validation, applied).
- **Confirmed, documentation only:** 1 (Finding 5 — comment added, no behavior change).
- **Rejected:** 1 (Finding 3 — false premise, contradicts this pipeline's own Phase 6/10 boundary,
  same pattern as T09 Phase 9 Finding 1).

No refactoring, optimization, public-API change, or renaming was performed. All three code changes
(Findings 1, 2, 4) stay inside `applyFailure`'s existing non-locking branch and the existing
constructor — no new methods, no signature changes. Verified compiling clean after all changes
(`javac`, standalone — this class still has zero external dependencies).
