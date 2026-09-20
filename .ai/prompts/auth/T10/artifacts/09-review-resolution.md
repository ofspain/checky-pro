# auth · T10 — Phase 9: Review Resolution

Human-approved disposition of Phase 8's (Kimi) 6 findings against the Phase 6 implementation and
Phase 7 self-review.

---

### Finding 1 — Cross-surface comparison test does not exercise the two production surfaces

**Disposition:** ACCEPTED.

**Reason:** Correct, and independently corroborates Phase 7's own self-review Finding 1: the
existing `AccountExceptionHandlerTest` cross-surface test constructs two default,
indistinguishable `VerificationTokenRejectedException` instances directly — it never calls
`AccountService.activateFromVerificationToken` or `AccountService.resetPassword`, so it can't
detect a regression where either real call site starts throwing something else before reaching the
handler.

**Exact change made:** Added
`verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods` to
`AccountServiceTest.java`. Both real service methods are invoked with a rejection-triggering setup
(`consumeForPurpose` stubbed to return `Optional.empty()` for each), the two real thrown
`VerificationTokenRejectedException` instances are captured via AssertJ's `catchThrowableOfType`,
and routed through a locally-constructed `new AccountExceptionHandler()` (same zero-dependency
pattern `AccountExceptionHandlerTest` already uses) to assert equal `status`/`type`/`title`/`detail`.
The existing `AccountExceptionHandlerTest` cross-surface test is left in place — it still has
value as an explicit, named statement of the handler's own determinism, now supplemented rather
than replaced by this stronger, real-call-site proof.

---

### Finding 2 — New `consumeForPurpose(EMAIL_VERIFY)` boundary test omits two reachable rejection reasons

**Disposition:** SPLIT — wrong-purpose REJECTED, LOCKED-account ACCEPTED with corrected placement.

**Reason (wrong-purpose):** Re-raises a question the frozen brief (`04-frozen-task-brief.md`,
Finding 2's disposition) already explicitly settled: `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify`
(`VerificationTokenServiceTest.java`) already proves this exact case. Kimi's own evidence
acknowledges the coverage exists elsewhere ("exercised by adjacent tests"). No new evidence
justifies reopening a frozen decision.

**Reason (LOCKED-account, accepted but relocated):** Verified `VerificationTokenService.isAccountUsable`
(private method) excludes only `DELETED`/`SUSPENDED` — a `LOCKED` account's token is *successfully*
consumed by `consumeForPurpose`. Rejection for `LOCKED` happens only via
`AccountService.activateFromVerificationToken`'s own separate `PENDING_VERIFICATION`-only status
check, a layer `consumeForPurpose` has no involvement in. Kimi's evidence citation
(`AccountService.java:129-130`) actually already points at this — but the recommendation (add the
case to the `VerificationTokenServiceTest` boundary test) targets the wrong file: that test can
only prove what `consumeForPurpose` itself does, and `consumeForPurpose` does not reject `LOCKED`.
The real, confirmed gap: `shouldRejectVerificationWhenAccountIsNotPendingVerification`
(`AccountServiceTest.java`) only exercises `ACTIVE`, not the full non-pending status set.

**Exact change made:** Added `shouldRejectVerificationForEveryNonPendingAccountStatus` to
`AccountServiceTest.java`, looping over `ACTIVE`/`LOCKED`/`SUSPENDED`/`DELETED` — mirroring the
existing loop-style pattern already used by `shouldRejectChangePasswordForEveryNonActiveAccountStatus`
and `shouldRejectPasswordResetForIneligibleAccountStatuses`. The pre-existing
`shouldRejectVerificationWhenAccountIsNotPendingVerification` (covering `ACTIVE` alone, with
additional spy-based `activateEmail()`-never-called assertions) is left untouched, not renamed or
merged — per this phase's "do not refactor" rule.

---

### Finding 3 — R2 duplicate-registration coverage unchanged and named-test mismatched

**Disposition:** REJECTED.

**Reason:** Identical in substance to Phase 3's (Kimi design-challenge) Finding 4, already
explicitly resolved and frozen at Phase 4 with cited rationale: no rename, no alias test —
`registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` already fully
satisfies R2, and `package.md`'s naming drift is a known, separately-tracked documentation issue
(not unique to this task). No new evidence is presented here beyond what Phase 4 already
considered and decided. Re-litigating a frozen brief decision without new information is out of
scope for this phase.

---

### Finding 4 — No end-to-end HTTP-response comparison across the two surfaces

**Disposition:** REJECTED.

**Reason:** Recommends `@WebMvcTest`/`MockMvc` integration-style testing. Confirmed (again, as at
every prior task's review phases this session) zero precedent for `MockMvc`/`@WebMvcTest` anywhere
in this module. `agents.md`'s testing pyramid is unit (plain JUnit) → ArchUnit + contract →
integration (Testcontainers: Postgres + Kafka) — `MockMvc` is not part of that pyramid at all. This
also directly contradicts the frozen brief's explicit Constraints section ("plain JUnit 5 +
Mockito + AssertJ, no Spring context"). Introducing new testing infrastructure for a single task is
the same category of over-scoped suggestion rejected at T08 Phase 11 Gap 6/7 and T09 Phase 11
Gap 3.

---

### Finding 5 — Cross-surface handler test asserts detail nullness separately rather than equality

**Disposition:** ACCEPTED.

**Reason:** Correct and trivial — asserting `verifyEmailRejection.getDetail()).isEqualTo(passwordResetRejection.getDetail())`
is a strict superset of the two separate `isNull()` checks (proves both null *and* equal to each
other in one assertion) and matches the adjacent sibling test's established equality-assertion
style exactly.

**Exact change made:** In `AccountExceptionHandlerTest.java`,
`onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`'s two
`isNull()` assertions on `getDetail()` replaced with one `isEqualTo(...)` comparison.

---

### Finding 6 — No guard against account/token detail leaking into `ProblemDetail` metadata

**Disposition:** ACCEPTED.

**Reason:** Cheap, reasonable defensive strengthening consistent with this module's existing
conservative style around explicitly asserting "no variable content" for uniform-rejection
responses (the same philosophy already applied to `getDetail()`).

**Exact change made:** In the same test as Finding 5, added
`assertThat(...getInstance()).isNull()` and `assertThat(...getProperties()).isNull()` for both
`ProblemDetail` instances.

Verified compiling cleanly (no warnings, including after fixing an incidental deprecated-API usage
in the Finding 1 fix — `AssertJ`'s `catchThrowableOfType(ThrowingCallable, Class)` overload is
deprecated in the resolved AssertJ version; used the non-deprecated `catchThrowableOfType(Class,
ThrowingCallable)` overload instead) and passing.

---

## Summary

- **Accepted:** 4 (Findings 1, 2-partial [LOCKED-account, relocated], 5, 6 — all applied).
- **Rejected:** 2 full + 1 partial (Findings 3, 4, and Finding 2's wrong-purpose part) — all with
  cited evidence, two of them re-litigating already-frozen Phase 3/4 decisions with no new
  information.

No refactoring, optimization, public-API change, or renaming was performed — only additive new
tests and one in-place assertion strengthening, exactly as scoped by this phase's rules.

**Full suite result: 93/93 tests passing** (91 from Phase 6, plus the 2 new tests from Findings 1
and 2), verified via the established `javac` + JUnit Platform Launcher workaround.
