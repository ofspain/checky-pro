# payment · T25 · Phase 12 — Specification Verification

Consumes: all prior T25 artifacts (00–11, including `11-test-review-resolution.md`).

Spec sources:
- `tasks.md:43` (task 25)
- `design.md` §4a: L3 (`:7`), L6 (`:10`), L10 (`:14`), L12 (`:16`)
- `package.md:109-110` (named tests)
- `agents.md:32, 57, 73`

`requirements.md` scopes no R-requirement to T25 (Phase 1).

Final state: commit `8812729b`. Full suite: **493/493 green**.

All paths are relative to `services/payment/src/test/java/com/themistra/payment/`.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **Task 25, L12:** no cross-module entity imports | Yes (T09; confirmed) | `ArchitectureTest.java:150` (rule); `:128` (`FEATURE_MODULES`; all 8 `@Entity` classes covered) | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (`:262`), `…ActuallyFailsAgainstAGenuineViolation` (`:310`), `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` (`:326`) | No | No |
| **Named test** `shouldPreventCrossModuleEntityImports` (`package.md:109`) | Yes | rule field `:150` plus canary `:262` | itself | No | No. Rule field and canary together satisfy the name, as accepted at T09 Phase 12. |
| **Task 25, L6:** no floating-point money types | Yes (T05; confirmed) | `ArchitectureTest.java:64` | `shouldRejectFloatingPointMoneyTypesAtArchUnit` (`:257`), `…ActuallyFailsAgainstAGenuineViolation` (`:341`) | No | No. The rule is a blanket ban, which is stricter than "money-carrying fields only". |
| **Named test** `shouldRejectFloatingPointMoneyTypesAtArchUnit` (`package.md:110`) | Yes | `:257` | itself | No | No |
| **Task 25, L3:** ban on KMS and chain-client imports | Yes (T15; confirmed) | `ArchitectureTest.java:194` (`org.web3j..`, `org.tron..`, `software.amazon.awssdk.services.kms..`) | `:267`, `:289` | No | No |
| **Task 25, L10:** assert the public-endpoint allowlist | Yes (**new in T25**) | `ArchitectureTest.java:242` (rule plus Javadoc); `archtestfixtures/RoguePermitAllUser.java` | `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` (`:277`), `…ActuallyFailsAgainstAGenuineViolation` (`:378`, direct and lambda forms), `…ExclusionIsLoadBearing` (`:397`, exactly one real call) | No | No |
| **L10, allowlist contents and in-config call** | Yes (pre-existing) | `src/main/java/com/themistra/payment/common/PublicEndpoints.java`; `common/PublicEndpointsTest.java:80-93` | `PublicEndpointsTest` | No | No. This is the guarantee split recorded at Phase 4 (F1). |
| **AC5:** enforced under `mvn test`, no `@ArchTest` | Yes | no `@ArchTest` or `@AnalyzeClasses` in the class | all 12 tests named in the surefire XML; 493/493 | No | No |
| **`agents.md:57`:** ArchUnit layer of the test pyramid | Yes | `ArchitectureTest` | — | No | No |
| **`agents.md:73`:** ArchUnit ban on chain and KMS clients | Yes | `:194` | `:267`, `:289` | No | No |
| **`agents.md:32`:** ArchUnit enforces `api → application → domain` | **No** | — | — | Yes | **Deferred** by the human at Phase 4 as **FU-1**: it is a platform-wide gap and no service has these sub-packages. It is not in task 25's statement. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. All four items in task 25 are enforced and gated by
`mvn test`:
- **L12, L6 and L3:** built in T05, T09 and T15, and re-confirmed here.
- **L10:** its only missing piece, the ArchUnit `permitAll` ban, is added.
- **No production code changed**, as frozen.

**(2) Does it satisfy every acceptance criterion?** Yes, AC1–AC5.
- **Proven able to fail:** every T25 assertion was shown to fail under a targeted temporary edit
  (Phases 6, 9 and 11).
- **Not vacuous:** the load-bearing test (`:397`) proves the real-code canary's pass depends on
  the exclusion, not on the scan finding nothing.

**(3) Does it violate any LOCKED decision?** No.
- **L3, L6 and L12:** those rules are unchanged.
- **L10:** it is strengthened.
- **Spec files:** no `spec/` file was touched.

**(4) Remaining risks.**
- **FU-1:** the `agents.md:32` layering rule is unenforced in every service. The human owns it.
- **FU-2:** there is no ArchUnit sweep requiring `@PreAuthorize` on every handler. Every handler
  has one today. The human owns it.
- **The rule covers `permitAll()` only.** It does not cover `anonymous()` or `web.ignoring()`.
  Neither is used, and the limit is documented in the rule's Javadoc (human decision, Phase 4).
- **Nested classes of `ResourceServerConfig` are exempt** from the rule. `PublicEndpointsTest`'s
  source-line count still covers them.

**PASS:** all four task-25 guards are enforced under `mvn test` (493/493 green), the new L10 rule
is proven non-vacuous and able to fail, and no LOCKED decision is violated. FU-1 and FU-2 are
human-owned deferrals.
