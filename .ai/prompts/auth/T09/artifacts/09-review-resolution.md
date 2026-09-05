# auth · T09 — Phase 9: Review Resolution

Human-approved disposition of Phase 8's (Kimi) 9 findings against the Phase 6 implementation and
Phase 7 self-review.

---

### Finding 1 — "Phase 6 omitted all test-file changes required by the frozen brief"

**Disposition:** REJECTED.

**Reason:** False premise. `.ai/prompts/auth/T09/06-implementation.md` (the Phase 6 prompt itself)
states explicitly: *"Do NOT write tests here (that is Phase 10) unless the task itself is
test-only."* T09 is not test-only. The frozen brief's "Files to Modify" list spans this task's
*entire* lifecycle across all remaining phases, not Phase 6 specifically — `05-implementation-plan.md`
itself places the three test files at Execution-order steps 4-6, distinct from steps 1-3
(production code, Phase 6's actual scope). This is the same phase boundary every prior task in this
pipeline (T07, T08) has followed. Not a blocker.

---

### Finding 2 — "`registerRejectsKnownDuplicateWithoutTouchingEncoder` is now a failing test"

**Disposition:** REJECTED as a Phase 6 defect; CONFIRMED as already-planned content, no new action.

**Reason:** The test hasn't been touched yet because test changes are Phase 10's job (Finding 1's
resolution). Kimi's own evidence cites `05-implementation-plan.md`'s exact prescribed fix (rename,
flip the encoder assertion) — this finding independently corroborates the Phase 5 plan is correct,
it does not surface anything the plan missed. No change made here; the fix stays scheduled for
Phase 10.

---

### Finding 3 — "`RegisterAccountRequestValidationTest.passwordBoundaries` asserts behavior that no longer exists"

**Disposition:** REJECTED as a Phase 6 defect; CONFIRMED as already-planned content, no new action.

**Reason:** Same as Finding 2 — `05-implementation-plan.md` step 6 already specifies this exact
update. Deferred to Phase 10 by design.

---

### Finding 4 — "No new `AccountServiceTest` coverage for `register` policy enforcement or ordering"

**Disposition:** REJECTED.

**Reason:** Same false premise as Finding 1. The three named tests Kimi asks for are already
specified verbatim in `05-implementation-plan.md`'s "Unit/integration tests required" section.
Phase 10 owns writing them.

---

### Finding 5 — "No new `AccountServiceTest` coverage for `resetPassword` policy enforcement or ordering"

**Disposition:** REJECTED.

**Reason:** Same as Finding 4 — already specified in `05-implementation-plan.md`, including the
`InOrder` proof Kimi asks for. Phase 10's job.

---

### Finding 6 — "`AccountControllerTest` missing propagation tests for `register` and `passwordReset`"

**Disposition:** REJECTED.

**Reason:** Same as Finding 4/5 — already specified in `05-implementation-plan.md`. Phase 10's job.

---

### Finding 7 — "HIBP breach check executes inside a `@Transactional` public endpoint with no mandated rate-limit backstop"

**Disposition:** CONFIRMED, no new action.

**Reason:** Duplicates Self-Review (Phase 7) Finding 1 exactly, down to the same evidence
(`application.properties:65`, R41's endpoint list). Already logged there as an accepted residual
risk with a recommended out-of-scope follow-up for the spec author (move the breach-check call
outside the transaction, or add `POST /accounts` to R41). Kimi's own recommendation agrees: "Treat
as an accepted residual risk per the self-review... Do not silently ignore it." It isn't being
silently ignored — it's recorded in two independent artifacts now. No code change; still out of
scope for T09 (`PasswordPolicy.java` and its transactional context are in Files NOT to Modify).

---

### Finding 8 — "`resetPassword` Javadoc overstates token consumption on a policy violation"

**Disposition:** ACCEPTED.

**Reason:** Correct and verified. `resetPassword` is `@Transactional`; `consumeForPurpose`'s
`markConsumed` write and the later `passwordPolicy.validate` throw both execute inside that same
transaction. A thrown `PasswordPolicyViolationException` rolls the whole transaction back,
undoing `markConsumed`'s effect — the token is never durably consumed on this path. The Phase
6-added Javadoc sentence "still consumes that token" was factually wrong about the persistence
outcome, though it didn't affect any actual code behavior (Spring's rollback semantics were already
correct and untouched by this task).

**Exact change made:** `AccountService.java`, `resetPassword`'s Javadoc — replaced "A
policy-violating password submitted with an otherwise-valid, unused token still consumes that
token (mirrors the existing ineligible-account case, which already does the same) and is
distinguishable from an invalid-token rejection" with a corrected paragraph stating the token is
*not* durably consumed (transaction rollback undoes `markConsumed`), and that the residual signal
is the distinguishable *response type*, not token consumption. The accepted-risk conclusion itself
is unchanged — if anything, the corrected understanding weakens the case for concern further (no
legitimate token is wasted on a mistyped password retry) without introducing any new risk, so
Phase 3/4's human decision to accept this as residual risk stands and does not need to be revisited.

Verified compiling after the change (`javac` against the resolved test-scope classpath, clean, no
errors).

---

### Finding 9 — "Implementation plan and implementation notes disagree on whether tests belong in Phase 6"

**Disposition:** REJECTED.

**Reason:** Not a real disagreement. `05-implementation-plan.md`'s Execution order plans the task's
full remaining lifecycle (steps 1-3 are Phase 6's scope, steps 4-6 are Phase 10's, step 7 is Phase
6's own verification substep) — it was never asserting all seven steps happen inside Phase 6.
`06-implementation-notes.md` correctly describes what Phase 6 itself did. No conflict requiring
resolution; both artifacts are internally consistent with the pipeline's own phase boundaries.

---

## Summary

- **Accepted:** 1 (Finding 8 — Javadoc correction, applied, compiles clean).
- **Confirmed, no new action:** 1 (Finding 7 — duplicate of an already-logged self-review finding).
- **Rejected:** 7 (Findings 1-6, 9 — all stem from evaluating the Phase 6 diff as if it should
  already include Phase 10's test work, contradicting Phase 6's own explicit prompt guardrail).

No refactoring, optimization, public-API change, or renaming was performed — only the one accepted
Javadoc correction, exactly as scoped by this phase's rules.
