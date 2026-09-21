# crypto · T29 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T29 — Bump spec status |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

---

## Summary

The implementation is disciplined and narrow: it changes only `package.md` and a single regression-test method, honestly leaves §9 item 13 unchecked because of Docker/build failures, and records explicit follow-ups. The main concern is whether bumping to `READY FOR IMPL` is appropriate while the full verify/build gate is still failing and the evidence for Q7's end-to-end claim is unclear. The review also found a few small robustness issues in the new regression test.

---

## Findings

### 1. Status bumped while §9 item 13 is unchecked and the full verify/build gate fails

- **Issue:** `package.md` now reads `Status: READY FOR IMPL`, but §9 item 13 (`mvn verify` passes; Docker image builds) is explicitly `[ ]` and the note discloses a genuine build failure.
- **Evidence:** `package.md` §9 line 119: item 13 is `[ ]` and its note says the Docker build "genuinely fails" with a Maven reactor validation error (`Child module /workspace/services/auth ... does not exist`).
- **Recommendation:** Either (a) fix the Dockerfile/reactor issue and re-run full verify before the bump, or (b) add a conspicuous header caveat that `READY FOR IMPL` is conditional on CI resolving item 13. Do not rely on the item-13 footnote alone, because a reader scanning the header will not see it.
- **Confidence:** High.

### 2. §9 item 13's failure is a real build defect, not just "Docker unavailable"

- **Issue:** The note describes a Maven reactor validation failure (`services/auth` module not found during the crypto Docker build), which is a structural Dockerfile/build-context problem, not merely the absence of a Docker daemon.
- **Evidence:** `package.md` §9 item 13 note: `"Maven reactor validation error, Child module /workspace/services/auth of /workspace/pom.xml does not exist"; services/auth/Dockerfile has the identical structural gap.`
- **Recommendation:** File a tracked follow-up task for both `services/crypto/Dockerfile` and `services/auth/Dockerfile`, and link the issue number in the item-13 note. Leaving it as a vague "follow-up" without a tracker risks it being forgotten before the first deploy.
- **Confidence:** High.

### 3. Test-run numbers in item 13 are inconsistent with prior disclosures and not reproducible

- **Issue:** T28 Phase 10/12 reported **698 tests, 0 failures, 14 Docker-only errors**. T29 now reports **760 tests, 6 failures, 4 errors**. The delta is explained only as "T29 added one test" and "pre-existing defects," but the change from 0 failures to 6 failures / 4 errors is not documented.
- **Evidence:** `artifacts/10-test-generation.md` (T28) vs. `package.md` §9 item 13 note (T29). No list of the 6 failures/4 errors is provided.
- **Recommendation:** Attach the exact command, commit hash, and the names of the 6 failing and 4 erroring tests to the note, or link a CI run. Without that, an independent reviewer cannot tell whether the failures are truly unrelated or new regressions introduced during T29.
- **Confidence:** Medium.

### 4. Q7 resolution overstates end-to-end verification

- **Issue:** `package.md` §11 Q7 says the ECDSA P-256 choice is "verified end-to-end against a compatible key (`KmsSignerTest`, `KmsSignerLocalStackIntegrationTest`)." `KmsSignerLocalStackIntegrationTest` is a Testcontainers/Docker test.
- **Evidence:** `KmsSignerLocalStackIntegrationTest.java` is annotated with `@Testcontainers` and starts a LocalStack container. The disclosed test run has Docker/build failures, so it is unclear whether this integration test actually executed and passed.
- **Recommendation:** Clarify in the Q7 note whether `KmsSignerLocalStackIntegrationTest` passed in a Docker-enabled environment or is still blocked. If it is blocked, rephrase "verified end-to-end" to "unit-verified plus LocalStack integration test ready to run once Docker is available."
- **Confidence:** Medium.

### 5. New regression test is brittle to markdown formatting

- **Issue:** `T01SkeletonRegressionTest.packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo` asserts exact substrings `| Version | `0.2` |` and `| Status | `READY FOR IMPL` |`.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java` lines 180–181.
- **Recommendation:** Parse the header table with a small regex that tolerates extra spaces, e.g., `\\|s*Version\\s*\\|\\s*`0?2`\\s*\\|`. This reduces the chance that an unrelated formatting cleanup breaks the build.
- **Confidence:** Low.

### 6. New regression test does not guard the four resolved questions

- **Issue:** T29 edits added resolution notes to Q1, Q2, Q3, and Q7. The new test only guards the header `Version`/`Status`, so a silent revert of those question resolutions would not fail CI.
- **Evidence:** `package.md` §11 lines 141–147 contain the new `**Resolved (2026-09-21, ...**` blocks. `T01SkeletonRegressionTest` has no assertions on them.
- **Recommendation:** Add lightweight assertions that each of Q1/Q2/Q3/Q7 contains `Resolved (2026-09-21` and that Q4/Q5/Q6/Q8 do not. This mirrors the file's existing style and costs little.
- **Confidence:** Low.

### 7. Header still contains placeholder author/implementer fields

- **Issue:** The header lists `Author (senior/owner) | <name>` and `Implementer | TBD` while the spec is now `READY FOR IMPL`.
- **Evidence:** `package.md` header lines 7–8.
- **Recommendation:** Either populate these fields or add a note that ownership assignment is tracked externally. A `READY FOR IMPL` spec with `<name>`/`TBD` looks incomplete to an implementer picking up the card.
- **Confidence:** Medium.

### 8. Title still says "Phase 1" despite status bump

- **Issue:** The document title is `# Feature Spec: Crypto Service — Phase 1` while the status is `READY FOR IMPL` and version is `0.2`.
- **Evidence:** `package.md` line 1.
- **Recommendation:** If "Phase 1" is the spec's name, leave it; otherwise consider updating to a title that does not suggest the document is still in an early phase. At minimum, add a note explaining that "Phase 1" refers to the launch scope, not the spec maturity.
- **Confidence:** Low.

### 9. No regression guard against silent greenwashing of item 13

- **Issue:** The honest partial status of item 13 is captured only in prose. A future edit could change `[ ]` to `[x]` or remove the note without failing a test.
- **Evidence:** `T01SkeletonRegressionTest` does not inspect §9 item 13.
- **Recommendation:** Add an assertion that item 13's line still contains a marker such as "Partially true" or "integration portion blocked" until the follow-up is resolved. This prevents someone from prematurely marking the item complete.
- **Confidence:** Low.

---

## Confirmations

- The implementation stayed within the agreed T29 scope: only `package.md` and `T01SkeletonRegressionTest.java` were modified.
- §9 item 13 was not falsely marked `[x]`; the failure was disclosed rather than hidden.
- §11 Q4–Q8 were left untouched, and Q1/Q2/Q3/Q7 were updated with resolution notes and open follow-ups.
