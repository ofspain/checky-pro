# auth · T10 — Phase 12: Specification Verification

Verifying the final test suite (Phase 6/9/11) against `spec/auth-service/requirements.md`,
`design.md`, and `tasks.md` for T10 only. No production code exists to verify — this task is
test-only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R2** — duplicate-email registration returns the identical `202` as new registration | Yes (pre-existing, T02/T09) | `AccountController.java` (register endpoint, catches `DuplicateEmailException` locally); `AccountService.java` (register, throws `DuplicateEmailException`) | `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` (`AccountControllerTest.java:53`); `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` (`AccountServiceTest.java`, T09); `registerMapsConstraintRaceToDuplicateEmail` (`AccountServiceTest.java`) | No | No — regression-only, no new test needed |
| **R5** — every invalid-verification-token reason is uniform | Yes | `AccountService.activateFromVerificationToken` (single `VerificationTokenRejectedException` for every reason); `VerificationTokenService.consumeForPurpose` (`isAccountUsable` excludes DELETED/SUSPENDED at line ~178; markConsumed's DB-level filter excludes expired/used) | `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` (`VerificationTokenServiceTest.java:424`, new T10 — not-found/expired/used/deleted/suspended); `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` (pre-existing T07, wrong-purpose, cited not duplicated); `shouldRejectVerificationForEveryNonPendingAccountStatus` (`AccountServiceTest.java:324`, new T10 — closes the LOCKED-account gap `consumeForPurpose` itself doesn't cover) | No | No |
| **R15** — every invalid-reset-token reason is uniform | Yes (pre-existing, T07) | `AccountService.resetPassword`; `VerificationTokenService.consumeForPurpose(..., PASSWORD_RESET)` | Five pre-existing tests (`VerificationTokenServiceTest.java`, lines 335-421) plus `shouldRejectPasswordResetForIneligibleAccountStatuses` (`AccountServiceTest.java`) | No | No — regression-only, no new test needed |
| Cross-surface consistency (L5) — an invalid-verification-token response and an invalid-reset-token response are identical to each other, not merely each internally uniform | Yes | `AccountExceptionHandler.onVerificationTokenRejected` (single mapping shared by both surfaces, unchanged) | `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces` (`AccountExceptionHandlerTest.java:51`, new T10 — handler-layer proof); `verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods` (`AccountServiceTest.java:362`, new T10 — real-call-site proof, strengthened at Phase 11 Gap 3 with absolute-value and leak-prevention assertions, not just relative equality) | No | No |

**Named tests (`package.md` §8):**
- `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` (R2) — does not exist under this
  exact name. Satisfied in substance by `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`.
  This is an explicit, human-approved decision (frozen brief `04-frozen-task-brief.md`, Finding
  3/4's resolution), re-raised and re-rejected twice more during independent review (Phase 8
  Finding 3, Phase 11 Gap 1) with no new evidence surfacing each time — not an oversight.
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` (R5) — exists verbatim at
  `VerificationTokenServiceTest.java:93`, but exercises `verify()`/`consume()`, methods with zero
  production callers today (confirmed via `grep` at Phase 0/2 — nothing in
  `services/auth/src/main/java` calls either). The method the live production call path
  (`AccountService.activateFromVerificationToken`, since T07) actually uses is `consumeForPurpose`,
  tested by this task's new `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose`
  (Phase 11 Gap 4, explicitly recorded here per Kimi's own fallback recommendation rather than
  silently left implicit).

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. All three surfaces the task statement names — duplicate
registration, invalid verification tokens, invalid reset tokens — have tests proving uniform
responses, and this task additionally closes two gaps Phase 1's extraction confirmed were real:
`consumeForPurpose(..., EMAIL_VERIFY)`'s own boundary set (the method the live production path
actually uses, previously only proven for the superseded `verify`/`consume` API) and explicit
cross-surface consistency (previously only an inferred consequence of shared plumbing, never
directly asserted).

**(2) Does it satisfy every acceptance criterion?** All four (AC1-AC4) from the frozen brief are
implemented and tested. AC1/AC3 were already satisfied pre-task (regression-only, cited not
duplicated per the frozen brief's explicit no-unrelated-churn constraint). AC2a/AC2b/AC4 are this
task's genuine new coverage, further strengthened during independent review (Phase 9 added the
LOCKED-account case and the real-call-site cross-surface proof; Phase 11 added absolute-value
assertions to the latter).

**(3) Does it violate any LOCKED decision?** No. L5's enumeration-safety guarantee is unchanged in
behavior — this task only adds evidence for it. No production code was touched (confirmed: `git
diff` across this task's lifecycle shows only test files).

**(4) Remaining risks:**
- Module-wide `mvn -pl services/auth verify` still cannot run end-to-end — the same pre-existing,
  unrelated compile break tracked since T03. Every test in this task was verified via isolated
  `javac` + JUnit Platform Launcher instead (93/93 passing, most recent run this phase).
- **No end-to-end HTTP-response comparison exists** (`@WebMvcTest`/`MockMvc` or integration-level).
  Raised independently at Phase 8 Finding 4 and Phase 11 Gap 6, rejected both times: zero precedent
  for `MockMvc`/`@WebMvcTest` anywhere in this module (confirmed by `grep` at multiple phases this
  task and every prior task this session), and it would contradict the frozen brief's explicit
  "no Spring context" constraint. Per Kimi's own Phase 11 fallback recommendation, recorded here
  explicitly as an accepted residual risk rather than silently omitted: a controller-level
  regression (e.g., a conditional exception wrapper, a content-type change) could in principle
  break response identity between the two surfaces without failing any current unit test. Given
  this module's existing testing pyramid places true integration coverage at the Testcontainers
  layer (per `agents.md`), not at a `MockMvc` layer that doesn't exist in this module at all,
  closing this risk (if ever prioritized) would be a cross-cutting infrastructure decision for the
  spec author, not a single-task fix.
- The R2 named-test/`package.md` §8 naming mismatch (see traceability matrix above) is a documented,
  three-times-affirmed decision, not a defect — but remains a literal mismatch against
  `package.md`'s own verification checklist wording ("All §3 acceptance criteria have a passing
  named test from §8"), which is itself known to have drifted from current `requirements.md`/task
  state (same category of staleness first logged at T09 Phase 1).

---

## Verdict

**PASS** — every requirement, LOCKED decision, and both named tests (one verbatim, one satisfied in
substance under an explicitly-decided different name) in T10's scope are implemented and tested. No
production code was touched, matching this task's test-only nature. The one residual risk (no
end-to-end HTTP comparison) was surfaced twice during review, evaluated against this module's
actual testing conventions both times, and explicitly recorded rather than silently accepted or
silently ignored.
