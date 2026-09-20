# crypto · T20 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. One critical finding was discovered and fixed immediately in this phase, mirroring this
pipeline's own established precedent (T17 Phase 10) for a load-bearing defect that would make any
further review moot — disclosed in full below, not deferred to Phase 9. Every other finding is
findings-only, per this phase's own rule.

## 1. `KmsSigner` could not actually be wired as a Spring bean — FIXED IMMEDIATELY

- **Issue:** `KmsSigner` has two constructors (a public `(KmsProperties, Clock)` one and a package-private
  test-seam `(KmsProperties, Clock, KmsClient)` one), and the original draft annotated neither with
  `@Autowired`. Verified directly with a real `ApplicationContextRunner` test: Spring's constructor
  resolution could not determine which constructor to use and threw
  `BeanInstantiationException: Failed to instantiate [KmsSigner]... No default constructor found`. Since
  neither `KmsSignerTest` nor `KmsSignerLocalStackIntegrationTest` ever constructs `KmsSigner` through
  Spring (both call the package-private constructor directly), this defect had zero test coverage and
  would only have surfaced at real application startup — the single most severe class of bug for a
  component this security-critical.
- **Severity:** Critical
- **Evidence:** `attest/KmsSigner.java:77` (public constructor, pre-fix).
  `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java:60-61` — the precedent
  `KmsSigner`'s shape mirrors already annotates its own public constructor `@Autowired` for exactly this
  reason; this task's own draft simply omitted it when adapting that shape.
- **Fix applied:** Added `@Autowired` to `KmsSigner`'s public constructor
  (`attest/KmsSigner.java:77-79`). Re-verified with the same `ApplicationContextRunner` probe — wires
  correctly. A permanent regression test, `attest/KmsSignerSpringWiringTest.java`, now locks this in
  (the probe used to discover and verify the fix was a throwaway file, deleted after being formalized
  into this real test).

## 2. `sign(...)`'s two validation checks execute in a specific, undocumented order

- **Issue:** `sign(null)` and `sign(<wrong-length>)` are both tested and both correctly rejected, but
  the method validates null-ness before length, with no comment explaining why that order matters (it
  doesn't, functionally, since a `null` array has no `.length` to check first — but the ordering is a
  silent assumption a future edit could invert without any test catching a meaningful behavior change,
  since `sign(null)` would still throw `NullPointerException` either way from the `.length` access if
  reordered).
- **Severity:** Low (no behavioral risk today, purely a documentation/readability nit)
- **Evidence:** `attest/KmsSigner.java:103-107`.
- **Recommendation:** No code change needed; optionally a one-line comment noting the order is
  arbitrary (null-check first is simply the more conventional idiom), so a future reader doesn't
  wonder if it's load-bearing.

## 3. `SIGNING_ALGORITHM` and `API_CALL_TIMEOUT` constants have no accompanying test asserting their
   literal values

- **Issue:** `KmsSignerTest.signCallsKmsWithTheExpectedRequestShape` asserts the request's
  `signingAlgorithm()` equals `SigningAlgorithmSpec.ECDSA_SHA_256` directly (not via the class's own
  `SIGNING_ALGORITHM` constant reference), so a change to the constant's value would still be caught by
  this test failing — but no test independently pins the *timeout* value
  (`API_CALL_TIMEOUT = Duration.ofSeconds(5)`), which is invisible to any test since it only affects the
  real `KmsClient`'s configured behavior, not `KmsSigner`'s own observable API.
- **Severity:** Low
- **Evidence:** `attest/KmsSigner.java:71` (constant), no corresponding assertion anywhere.
- **Recommendation:** Not required — `ObservationSnapshotStoreConfig`'s own identical fixed-timeout
  constant has no dedicated test either (this codebase's own established precedent for this exact
  situation), so this is consistent, not a gap unique to this task.

## 4. `resolveKmsClient()` is `private static`, making it untestable in isolation from `sign(...)`

- **Issue:** The only way to prove `resolveKmsClient()` builds a `KmsClient` with the expected timeout
  override is indirectly (there is no direct unit test of this private method, nor could there
  reasonably be one without reflection). The Spring-wiring test (`KmsSignerSpringWiringTest`) proves the
  public constructor successfully produces *some* working `KmsClient` end-to-end via a real LocalStack
  call in a *different* test class (`KmsSignerLocalStackIntegrationTest`, which uses its own directly-
  constructed `KmsClient`, not one built via `resolveKmsClient()`), but no test exercises the *actual*
  production code path (`public KmsSigner(KmsProperties, Clock)` → `resolveKmsClient()`) against a real
  KMS-compatible endpoint.
- **Severity:** Low
- **Evidence:** `attest/KmsSigner.java:88-94`; no test constructs `KmsSigner` via its 2-arg constructor
  against LocalStack.
- **Recommendation:** Optional, not required by the frozen brief: a variant of the LocalStack test could
  point `resolveKmsClient()`'s real client at LocalStack via an endpoint-override system property/env
  var the AWS SDK itself supports, exercising the true production path end-to-end. Judged unnecessary
  for this task's own scope — the constructor delegation is a one-line, self-evidently-correct call, and
  `KmsSignerSpringWiringTest` already proves the production constructor doesn't throw when a real region
  is resolvable.

## 5. `SignatureResult`'s package-private visibility has no ArchUnit/structural test locking it in

- **Issue:** Finding #15's disposition (package-private, so no class outside `attest` can depend on it
  without tripping the boundary rule) is honored in the record's declaration (`record SignatureResult(...)`,
  no `public` modifier), but no test would fail if a future edit accidentally added `public`.
- **Severity:** Low
- **Evidence:** `attest/SignatureResult.java:15`.
- **Recommendation:** Not required — `KmsSignerArchitectureTest`'s own
  `noClassOutsideAttestMayReferenceKmsSigner` rule only names `KmsSigner`, not `SignatureResult`,
  matching the frozen brief's own literal text (Finding #15 discussed the *visibility modifier* as the
  mitigation, not an additional ArchUnit rule). Consistent with what was actually approved.

---

No thread-safety, transaction-boundary, or money-type defects were found (none apply — no persistence,
no concurrency beyond `KmsClient`'s own AWS-documented thread-safety). Module-boundary correctness was
verified extensively during implementation itself (Findings #1-#3/#9/#14 of the design challenge, folded
in at Phase 4, and the two additional redesigns discovered mid-implementation — see Phase 6 notes).
