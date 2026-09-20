# crypto · T22 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (and the established T19-T21
precedent), tests were written alongside production code. One test was extended at Phase 9's fold-in
(the parameterized controller-failure test). No production code changes in this phase — this artifact
is the traceability manifest.

## Test files (this task's own new/extended files)

| File | New/extended tests | Purpose |
|---|---|---|
| `attest/KmsSignerTest.java` | +9 | `publicKeyInfo()`'s request shape, field mapping, the two guard cases, the uncaught-failure case; `toPem`'s output shape and line-wrap boundary. |
| `attest/KmsSignerLocalStackIntegrationTest.java` | +2 | The named test (real `GetPublicKey` round-trip, PEM parses to the exact real `PublicKey`) and the `kmsKeyId` parity test between `sign(...)` and `publicKeyInfo()`. |
| `attest/VerificationKeysControllerTest.java` | 2 (1 parameterized ×2 executions) | `@WebMvcTest` slice: response shape, `Cache-Control: no-store`, the AC6 failure-propagation case (parameterized over both a generic and the real guard-produced exception type, Phase 9). |

**Total: 13 new/extended test methods** (14 executions, counting the Phase 9 parameterization), all
passing.

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` | AC1 (named test) | `200`, correct JSON shape, single-element array, `Cache-Control: no-store`. |
| `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl` | AC1 (named test, real infra) | A real `GetPublicKey` round-trip through `KmsSigner`'s actual production code path produces a PEM that parses back to the exact `PublicKey` object created at test setup. |
| `KmsSignerArchitectureTest` (both tests, re-run) | AC2 | Still passes unmodified with all three new `attest` files present. |
| `KmsSignerTest.toPemProducesTheExpectedShapeForFixedInputBytes` / `.toPemWrapsLongInputAtSixtyFourCharactersPerLine` | AC3 | `toPem`'s DER-to-PEM conversion is structurally correct, including the 64-char line-wrap boundary. |
| `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl` (PEM-parse assertion) | AC3 (real proof) | The real, end-to-end PEM output is valid, JCA-parseable key material. |
| `KmsSignerTest.publicKeyInfoMapsAResponseIntoTheExpectedFields` | AC4 | `alg` is sourced from `signingAlgorithmsAsStrings()`, not a hardcoded literal. |
| `KmsSignerLocalStackIntegrationTest.shouldPublishVerificationKeysAtWellKnownUrl` (`alg` assertion) | AC4 (real proof) | The real key's own metadata reports exactly `"ECDSA_SHA_256"`. |
| `VerificationKeysControllerTest.shouldPublishVerificationKeysAtWellKnownUrl` | AC5 | The JSON response contains only `kid`/`kmsKeyId`/`alg`/`publicKeyPem` — no other field. |
| `KmsSignerTest.publicKeyInfoPropagatesAGetPublicKeyFailureUncaught` | AC6 | A genuine `GetPublicKey` call failure propagates uncaught, unmodified. |
| `VerificationKeysControllerTest.aPublicKeyInfoFailurePropagatesToAGenericFiveHundred` (parameterized) | AC6 (HTTP boundary, Phase 9) | Both a generic infrastructure failure and the real `IllegalStateException` `KmsSigner`'s own guards produce surface as `500`, not swallowed or remapped. |
| `KmsSignerTest.publicKeyInfoThrowsIllegalStateExceptionWhenSigningAlgorithmsIsEmpty` | Frozen brief Finding #1 | An empty `signingAlgorithms()` list produces a named, actionable exception, not an opaque `IndexOutOfBoundsException`. |
| `KmsSignerTest.publicKeyInfoThrowsIllegalStateExceptionWhenPublicKeyIsNull` | Frozen brief Finding #6 | A `null` `publicKey()` produces a named, actionable exception, not a raw `NullPointerException`. |
| `KmsSignerTest.publicKeyInfoCallsKmsWithTheConfiguredKeyId` | Schema fidelity | `GetPublicKeyRequest.keyId()` equals `KmsProperties.keyId()`. |
| `KmsSignerLocalStackIntegrationTest.signAndPublicKeyInfoAgreeOnKmsKeyIdForTheSameKey` | Frozen brief Finding #5 | `sign(...).kmsKeyId()` and `publicKeyInfo().kmsKeyId()` agree for the same real key — a verifier-facing correctness concern. |

**R27/public endpoint** — already covered by the pre-existing `PublicEndpointsTest` (parameterized over
exactly this path, proving the security layer doesn't block it with 401/403) and
`ResourceServerConfigIntegrationTest`; no new test needed, confirmed redundant in Phase 6.

## Verification run

`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerLocalStackIntegrationTest,VerificationKeysControllerTest,KmsSignerArchitectureTest,PublicEndpointsTest,ResourceServerConfigIntegrationTest`
— 52/52 pass (28 from the four `attest`-focused files + 24 from the two pre-existing `common` files,
re-run unmodified).

Full module regression (`mvn -pl services/crypto -am test`): 716 tests, 6 failures, all pre-existing and
unrelated to this task (disclosed since T18/T19/T20/T21). Zero regressions.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved (at the time this section was
first written — see the Phase 11 additions below).

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into the test suite. Kimi raised 3 gaps, all framed as regression-lock
additions; all 3 were accepted, but implementing Gap 3 surfaced a real, pre-existing, out-of-scope
defect that changed what the new tests could correctly assert.

| Gap | Disposition | Test added |
|---|---|---|
| 1. No assertion that `KmsSigner.publicKeyInfo()` is called exactly once per request | **ACCEPTED** | `verify(kmsSigner, times(1)).publicKeyInfo()` added to `shouldPublishVerificationKeysAtWellKnownUrl`. |
| 2. `toPemWrapsLongInputAtSixtyFourCharactersPerLine`'s 100-byte input never hits the exact-64-character-multiple boundary | **ACCEPTED** | `KmsSignerTest.toPemWrapsExactMultiplesOfSixtyFourCharactersWithNoPartialLine`, parameterized over 48 and 96 input bytes (encoding to exactly 64 and 128 base64 characters, no padding). |
| 3. No test locks the endpoint's exact HTTP contract (`POST` → `405`, unknown sub-path → `404`) | **ACCEPTED, with a real discovery** | Implementing this gap's suggested assertions (`405`/`404`) failed — direct log inspection showed Spring throws `HttpRequestMethodNotSupportedException`/`NoResourceFoundException` exactly as expected, but `common.ApiExceptionHandler`'s generic `@ExceptionHandler(Exception.class)` catch-all has no more specific handler for either, so both are swallowed into a `500 "Internal error"` instead of the correct `405`/`404`. This is a genuine, pre-existing defect in shared code, not something T22 introduced, and `common/ApiExceptionHandler.java` is explicitly listed as Files NOT to Modify in this task's own frozen brief — out of scope to fix here. `VerificationKeysControllerTest.postIsNotAllowedOnTheWellKnownPath` and `.unknownSubPathReturnsAnErrorRatherThanTheSameHandler` were written to assert the actual, current `500` behavior instead, with the discovery fully documented inline so a future fix to `ApiExceptionHandler` is a deliberate, visible test change here, not a silent regression. |

**Verification run (Phase 11):**
`mvn -pl services/crypto test -Dtest=KmsSignerTest,VerificationKeysControllerTest,KmsSignerLocalStackIntegrationTest,KmsSignerArchitectureTest,PublicEndpointsTest,ResourceServerConfigIntegrationTest`
— 56/56 pass (was 52/52 before this phase's 4 new test executions). Full module regression: 720 tests,
same 6 pre-existing unrelated failing tests, zero regressions.

**Flagged for the user, not fixed in this task:** `common.ApiExceptionHandler`'s catch-all converts
*every* unmapped framework exception — including routing-level ones like a wrong HTTP method or an
unmatched path — into a generic `500`, across every controller in this service, not just this task's
own endpoint. This predates T22 and was only newly discovered because this is the first test in the
codebase to exercise a wrong-method/unknown-path case against that shared advice. Worth a dedicated,
properly-scoped follow-up task if the team wants `405`/`404` to actually reach callers.
