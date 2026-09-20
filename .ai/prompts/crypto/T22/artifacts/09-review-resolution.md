# crypto · T22 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review and Phase 8 (Kimi) independent review. Phase 8
confirmed both of my own Phase 7 findings and raised 2 new ones — one of which was verified factually
incorrect against the actual source before any action was taken.

Phase 8 also reported `KmsSignerLocalStackIntegrationTest` failing in its own sandbox with an SDK
connectivity error during test setup. Re-run directly in this environment: **3/3 pass** — confirming
Kimi's own diagnosis (a transient Docker/network issue in its sandbox, not a code defect) rather than
accepting the failure report at face value.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | `sign(...)`'s hardcoded `SIGNING_ALGORITHM` and `publicKeyInfo()`'s reported `alg` could diverge if the provisioned key type changes before Q7 closes (Self-Review #1 = Phase 8 #1) | **ACCEPTED, DOCUMENT-ONLY** | No code change. Already Q7's own disclosed, standing open question since T20; both review passes agree KMS's own validation already prevents an actual wrong-algorithm signature, and this is a reactive-discovery/operational-clarity concern, not a correctness defect. |
| 2 | `toPem(byte[])` produces a syntactically valid but semantically empty PEM for a zero-length input (Self-Review #2 = Phase 8 #2) | **ACCEPTED, DOCUMENT-ONLY** | No code change. An empty-but-non-null `publicKey()` from a genuinely successful `GetPublicKey` call is not a realistic KMS response shape, confirmed by the LocalStack test's own real, non-empty DER bytes. |
| 3 | `VerificationKeysController`'s constructor is package-private, inconsistent with every other controller | **REJECTED — factually incorrect** | Verified directly against the actual source (`attest/VerificationKeysController.java:21`): the constructor is already `public VerificationKeysController(KmsSigner kmsSigner)`. No change made — there was nothing to fix. |
| 4 | The controller failure test only proves a generic `RuntimeException` isn't swallowed, not the actual `IllegalStateException` `KmsSigner`'s own guards produce | **ACCEPTED** | Parameterized `aPublicKeyInfoFailurePropagatesToAGenericFiveHundred` over both a generic `RuntimeException` and a realistic `IllegalStateException` (matching the exact guard-message shape `publicKeyInfo()` produces), asserting `500`/`"Internal error"` for both. |

## Files changed in this phase

- `attest/VerificationKeysControllerTest.java` — Finding #4's parameterized test; no production code
  changed (Finding #3 required no fix, Findings #1/#2 are document-only).

No public API, class name, or method signature changed. No refactoring beyond what the one accepted
finding required.

## Verification

`mvn -pl services/crypto test -Dtest=VerificationKeysControllerTest,KmsSignerTest,KmsSignerLocalStackIntegrationTest,KmsSignerArchitectureTest`
— 28/28 pass (was 27/27 before this phase's 1 new test — the `VerificationKeysControllerTest` parameterized
test contributes 2 executions in place of the prior single test). Full module regression
(`mvn -pl services/crypto -am test`): 716 tests, same 6 pre-existing, disclosed, unrelated failing tests;
zero regressions.
