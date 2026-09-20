# auth · T16 · Phase 11 — Test Review

Consumes `artifacts/10-test-generation.md`. Findings only — no rewrites.

## Summary

The new test suite (`TotpGeneratorTest`, `MfaSeedEncryptionTest`) covers the T16 frozen brief’s AC1–AC8 well, including the Phase 8/9 review fixes (malformed-envelope wrapping, version-`0x00` wrapped-key-length enforcement, `generateDataKey` exception wrapping, `DisposableBean.destroy()`, RFC 3986 encoding). The named `package.md` test `shouldReturnTotpProvisioningUriOnEnrollmentBegin` is correctly out of scope for T16 because it requires the persistence layer and controller introduced in tasks 17/19.

The gaps below are about tightening assertions to catch silent request-shape regressions and about representing real-world failure modes (GCM tampering, actual wrong-key decryption) rather than KMS-network errors.

---

### 1. KMS `GenerateDataKey` request contents are not verified

- **Gap:** `kmsModeProducesAdr0003EnvelopeLayoutAndRoundTrips` stubs `generateDataKey(any(GenerateDataKeyRequest.class))`. It never asserts the request carries the configured CMK ARN or `KeySpec.AES_256`.
- **Why it matters:** A refactor could drop the ARN, switch to `DataKeySpec.AES_128`, or pass the wrong field, and the test would still pass while real KMS calls would fail or use the wrong key size.
- **Suggested test:** Use `ArgumentCaptor<GenerateDataKeyRequest>` and assert `keyId().equals(SEED_KEK_ARN)` and `keySpec().equals(DataKeySpec.AES_256)`.

---

### 2. KMS `Decrypt` request contents are not verified

- **Gap:** The decrypt path is stubbed with `any(DecryptRequest.class)`. The round-trip test asserts the stored wrapped key in the envelope but does not verify that `decrypt` actually sends that exact wrapped key (or the configured ARN) to KMS.
- **Why it matters:** A bug that reads the wrong byte range from the envelope or sends an empty/uninitialized wrapped key could be hidden by a permissive mock.
- **Suggested test:** Add a test that decrypts a KMS-mode envelope and verifies the `DecryptRequest` passed to the mock contains the wrapped-key bytes extracted from the envelope and `keyId().equals(SEED_KEK_ARN)`.

---

### 3. Wrong-key failure is tested via KMS rejection, not via GCM authentication failure

- **Gap:** `wrongKeyDecryptFailsDistinctlyInsteadOfSilently` makes `kmsClient.decrypt` throw `KmsException`. This exercises KMS-level rejection, but ADR-0003/AC6’s real concern is a *valid* KMS response containing a *different* 32-byte key (rotated/wrong key) that would decrypt the ciphertext to a bad GCM tag rather than the original seed.
- **Why it matters:** The current test does not prove the AES-GCM tag prevents silently-wrong plaintext. If the code ever skipped GCM authentication, the KMS-rejection test would still pass.
- **Suggested test:** Mock `decrypt` to return `SdkBytes.fromByteArray(different32ByteKey)` and assert `MfaEncryptionException` is thrown, not a silently-wrong plaintext.

---

### 4. No test for GCM ciphertext / nonce tampering

- **Gap:** There is no test that flips a bit in the ciphertext or nonce portion of a valid envelope and asserts `decrypt` fails with `MfaEncryptionException`.
- **Why it matters:** AC6 requires that decryption "fails distinctly" for corrupted data, not just for KMS errors or version mismatches.
- **Suggested test:** For both local and KMS modes, encrypt a secret, flip one bit in the ciphertext or nonce, then assert `decrypt` throws `MfaEncryptionException` with an authentication-related message/cause.

---

### 5. No test for the default (no active profile) guard behavior

- **Gap:** `constructorRefusesToBootInNonLocalProfileWithBlankArn` uses `devEnvironment` (`"dev"`). There is no test for an `Environment` with zero active profiles, which is the state of the service if `SPRING_PROFILES_ACTIVE` is absent.
- **Why it matters:** L13/ADR-0003 says a blank ARN is only legal in the `local` profile. A blank ARN with no profile is the deployed-misconfiguration case that must fail closed.
- **Suggested test:** Add `constructorRefusesToBootWithBlankArnWhenNoProfileIsActive` using an empty `MockEnvironment` and a blank ARN.

---

### 6. No test for `local` profile combined with other profiles

- **Gap:** `constructorSucceedsInLocalProfileWithBlankArn` and `publicConstructorNeverBuildsARealKmsClientInLocalMode` only set `"local"` as the sole active profile.
- **Why it matters:** `Environment.acceptsProfiles(Profiles.of("local"))` returns true if any active profile matches, but this is easy to regress if the guard is later rewritten to compare a single profile string. A test locks the intended semantics.
- **Suggested test:** Add `constructorAllowsLocalModeWhenLocalProfileIsPresentAmongOthers` with active profiles `["local", "test"]` and a blank ARN.

---

### 7. `buildProvisioningUriIsSyntacticallyValid` is a weak assertion

- **Gap:** The test only asserts `URI.create(uri).getScheme().equals("otpauth")`. It does not parse or assert the path, query parameters, or that the secret param is present.
- **Why it matters:** A malformed string could still parse as a URI with scheme `otpauth` but be missing the label or contain unencoded characters that break authenticator apps.
- **Suggested test:** Assert `URI.getPath()` equals `/Themistra:user%40example.com` (or similar), extract the query string, and parse its `secret`/`issuer`/`algorithm`/`digits`/`period` values.

---

### 8. `TotpGenerator` input validation is not tested

- **Gap:** Neither `buildProvisioningUri` nor `generateSecret` has tests for invalid inputs (null/empty `accountLabel`, null `secret`, wrong-length secret).
- **Why it matters:** The production code currently delegates to `UriUtils.encodePathSegment`, which throws on null; callers in tasks 17/19 may pass an account UUID, but without a documented contract the behavior is accidental.
- **Suggested test:** Add tests asserting `IllegalArgumentException` (or whatever the intended failure) for null/blank `accountLabel` and null `secret`, ensuring no secret material leaks in the exception message.

---

### 9. `destroy()` idempotency / double-close is not tested

- **Gap:** `destroyClosesKmsClientWhenPresent` calls `destroy()` once. There is no test for calling it twice or calling it after an exception during construction.
- **Why it matters:** While the production implementation is currently safe, `close()` on an AWS SDK client is documented to be idempotent; a test locks this and catches a future regression if the destroy logic becomes more complex.
- **Suggested test:** Invoke `encryption.destroy()` twice and verify `kmsClient.close()` is called exactly once, or at least that no exception is thrown.

---

### 10. `MfaProperties` `@NotBlank issuerName` validation has no test

- **Gap:** `issuerName` is annotated `@NotBlank`, but there is no unit or Spring-context-lite test that a blank issuer causes startup failure.
- **Why it matters:** `agents.md` states startup must fail on missing/invalid config in non-local profiles. T16 introduces this new `@ConfigurationProperties` record, so its validation behavior should be verified.
- **Suggested test:** Add a small test that creates the `MfaProperties` record with a blank issuer and asserts the validation constraint violation (or assert startup fails when the record is bound with a blank value). This could live in a new `MfaPropertiesTest`.

---

## Final Verdict

Acceptable for T16 with the above additions recommended. The tests are focused, fast, plain JUnit as required, and the only named test left uncovered is correctly deferred to tasks 17/19.
