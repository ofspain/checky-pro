# auth · T16 · Phase 3 — Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md`. Findings only; no redesign or implementation.

---

### 1. `TotpGenerator` signature omits the account identifier required by the `otpauth://` label

- **Severity:** High
- **Evidence:**
  - TIB §Inputs says `TotpGenerator` has "no external input — generates its own secret and reads `issuer-name` from config".
  - `package.md` §8 named test `shouldReturnTotpProvisioningUriOnEnrollmentBegin` implies a URI usable by an authenticator app.
  - The Google Authenticator Key URI format requires `otpauth://totp/{label}` where `label` is `issuer:accountName` or just `accountName`; omitting it produces a technically valid but practically broken URI, and the `issuer` parameter is only a hint.
  - Conflict with R22, which returns a provisioning URI tied to a specific authenticated user.
- **Recommended brief amendment:**
  - Add an account identifier (account UUID or similar) as an input to `TotpGenerator`.
  - Specify how the URI `label` is constructed (e.g., `<issuer>:<account-uuid>`) and that values are URL-encoded per RFC 3986.

---

### 2. Raw secret length / entropy is unspecified and unverifiable

- **Severity:** Medium
- **Evidence:**
  - TIB §Scope says "random secret generation (`SecureRandom`, sufficient entropy for HMAC-SHA1 per L6)".
  - L6 only fixes the TOTP algorithm, digits, and period; it does not fix secret length or entropy.
  - "Sufficient entropy" is not independently testable (AC1 cannot be asserted without a length/entropy target).
  - RFC 6238 §5.1 recommends a 160-bit (20-byte) minimum; the Google Authenticator convention is 20 raw bytes encoded as 32 Base32 characters.
- **Recommended brief amendment:**
  - Pin the raw secret to a fixed byte length (e.g., 20 bytes / 160 bits) and state the expected Base32 length.
  - Make AC1 assertable: generated secrets are exactly N random bytes with SecureRandom.

---

### 3. Base32 encoding variant is unspecified

- **Severity:** Medium
- **Evidence:**
  - TIB §Scope says "Base32 encoding (hand-rolled RFC 4648, no existing dependency)".
  - RFC 4648 allows padding and is case-insensitive in decoding, but real TOTP clients expect uppercase, unpadded Base32.
  - Without specifying variant, two correct RFC 4648 implementations could produce URIs that some authenticators reject.
- **Recommended brief amendment:**
  - Specify uppercase A–Z2–7 alphabet, no padding, per the de-facto authenticator standard.
  - Add an acceptance criterion that the encoded secret matches `[A-Z2-7]{32}` (or chosen length) and contains no `=` padding characters.

---

### 4. Local-dev encryption key details are underspecified

- **Severity:** Medium
- **Evidence:**
  - ADR-0003 requires "a fixed, clearly-documented 32-byte AES key constant, defined in code and usable only when the active Spring profile is `local` and `seed-kek-arn` is blank".
  - TIB only says "the fixed local-dev key (version `0x00`)" and does not state where it lives, how it is documented, or how it is kept out of non-local profiles.
  - An undocumented or configurable local key risks being copied to a non-local environment or being flagged by secret scanning.
- **Recommended brief amendment:**
  - Require the local key to be a `private static final` compile-time constant in `MfaSeedEncryption` with a doc comment explicitly marking it local-only and unsafe for any deployed profile.
  - Add an AC that `dev`/`staging`/`prod` can never produce a `0x00` envelope.

---

### 5. Non-local startup guard mechanism is not described

- **Severity:** Medium
- **Evidence:**
  - TIB lists AC5: "In any non-`local` profile, a blank `seed-kek-arn` fails application startup."
  - Spring `@ConfigurationProperties` + `@Validated` cannot natively express "only blank in local". A conditional validator or `@PostConstruct` guard is required.
  - TIB does not say how the guard is implemented, making AC5 hard to test and easy to miss.
- **Recommended brief amendment:**
  - Specify the startup guard implementation (e.g., a custom `Validator` on `MfaProperties` aware of active profiles, or a `ProfileStartupGuard` bean).
  - Clarify how unit tests will exercise the guard without a full Spring context.

---

### 6. Active-profile detection is ambiguous

- **Severity:** Medium
- **Evidence:**
  - TIB §Inputs says `MfaSeedEncryption.encrypt` receives "the active Spring profile" as an argument.
  - Spring supports multiple active profiles (comma/space-separated set); "the" profile implies a single string.
  - `local` may coexist with `test` or `docker`; `String.equals("local")` would fail in those profiles.
- **Recommended brief amendment:**
  - Define local-profile detection via Spring's `Environment.acceptsProfiles("local")` or membership in `getActiveProfiles()`, not a raw string equality.
  - In unit tests, pass the resolved boolean `localMode` (derived from env) or a `Set<String>` of profiles.

---

### 7. AWS SDK BOM version is deferred to Phase 6

- **Severity:** Medium
- **Evidence:**
  - TIB §Scope says the BOM version "will be confirmed at Phase 6 implementation time against Maven Central".
  - T16 is the implementation phase; the brief cannot defer a concrete dependency version and still leave the module buildable.
  - A placeholder BOM prevents `mvn -pl services/auth verify` from passing and blocks the task acceptance.
- **Recommended brief amendment:**
  - Either pick a recent stable AWS SDK v2 BOM version now (e.g., the latest 2.x at implementation time) or explicitly add a Phase 4 decision gate with a fallback version.
  - Include the exact `<dependencyManagement>` block and `kms` dependency to be added.

---

### 8. KMS client configuration is omitted

- **Severity:** Medium
- **Evidence:**
  - TIB requires "real `GenerateDataKey`/`Decrypt` calls via a new AWS SDK v2 KMS client dependency" but says nothing about region, credentials, timeouts, or retry policy.
  - AWS SDK v2 defaults region to `US_EAST_1`; in an EKS pod this can produce a confusing runtime failure.
  - `agents.md` Security section notes IRSA role permissions, but the brief does not connect the client to the IRSA credential chain.
- **Recommended brief amendment:**
  - Add a note that the `KmsClient` uses the default AWS credentials and region provider chains (IRSA / default region from env / EC2 metadata), with no explicit keys in config.
  - If non-default timeouts/retries are required, state them; otherwise state that SDK defaults are acceptable for task T16.

---

### 9. AES-GCM cipher instance reuse is not ruled out

- **Severity:** Medium
- **Evidence:**
  - TIB §Constraints says both components must be "safe for concurrent use (Spring singleton beans)".
  - Java `Cipher` instances are not thread-safe; `MfaSeedEncryption` must create a new `Cipher.getInstance("AES/GCM/NoPadding")` per encrypt/decrypt call.
  - The brief does not mention this, risking a stateful implementation that passes single-threaded tests but fails in production.
- **Recommended brief amendment:**
  - Add an AC that `MfaSeedEncryption` must be thread-safe for concurrent encrypt/decrypt calls.
  - Include a test that exercises concurrent encryption/decryption (or at least asserts a new Cipher is used per call through behavior/ArchUnit if feasible).

---

### 10. Unknown / malformed envelope version handling is undefined

- **Severity:** Low–Medium
- **Evidence:**
  - ADR-0003 defines versions `0x00` and `0x01`; the format-version byte exists for future scheme changes.
  - TIB does not state what `MfaSeedEncryption.decrypt` should do when the version byte is not `0x00` or `0x01`.
  - Without a defined failure mode, an implementation could silently fall through or throw a generic unchecked exception, violating `agents.md` error-hygiene expectations later.
- **Recommended brief amendment:**
  - Specify that an unsupported version byte throws a dedicated runtime exception (e.g., `IllegalArgumentException` or a new `MfaEncryptionException`) that will later map to an RFC 9457 problem response.

---

### 11. The provisioning URI itself is secret material and must not be logged

- **Severity:** Low
- **Evidence:**
  - TIB §Constraints correctly notes the raw TOTP secret must never be logged.
  - The `otpauth://` URI returned by `TotpGenerator` contains the same secret in Base32 and is equally sensitive.
  - The issue is not stated, risking accidental logging in task 19's controller or integration tests.
- **Recommended brief amendment:**
  - Extend the security constraint to include the full provisioning URI and any intermediate Base32 secret string.

---

### 12. Naming discrepancy remains unresolved despite the brief's own naming call

- **Severity:** Low
- **Evidence:**
  - TIB §Scope correctly flags the `MfaSeedEncryption` vs `TotpSeedEncryption` naming conflict and defers it to Phase 4.
  - Since ADR-0003, `auth-decisions.md` D-025, `design.md` L14, and `agents.md` all agree on `MfaSeedEncryption`, the only outlier (`design.md` file map) should be explicitly reconciled rather than left as a lingering Phase 4 item.
- **Recommended brief amendment:**
  - Add a note to the TIB that `design.md` §6's `TotpSeedEncryption` reference is obsolete and should be treated as `MfaSeedEncryption`, matching the LOCKED decisions.

---

### 13. Mocked-KMS test scope may not prove the real envelope contract end-to-end

- **Severity:** Low
- **Evidence:**
  - TIB §Out scopes out LocalStack/Testcontainers and requires mocked `KmsClient` unit tests.
  - AC3/AC6 require asserting ADR-0003 byte layout and wrong-key behavior; a mock must faithfully simulate `GenerateDataKey` returning both `plaintext` (32 bytes) and `ciphertextBlob`, plus `Decrypt` returning a plaintext key.
  - The brief does not list what the mock should return, leaving room for an under-faithful mock that passes tests but fails against real KMS.
- **Recommended brief amendment:**
  - Add an AC describing the mocked KMS contract: `GenerateDataKey` returns a fixed or deterministic 32-byte plaintext data key and a non-empty `ciphertextBlob`; `Decrypt` of that blob returns the same plaintext key.
  - Note that the test must assert the exact byte envelope layout independently of the mock's internals.

---

## Summary

The brief is technically coherent but contains several gaps that will block implementation or produce a non-interoperable TOTP URI / fragile encryption component. The highest-priority fixes are (1) adding the account identifier to `TotpGenerator`, (2) pinning Base32 and secret-length conventions, and (3) defining how the local-profile guard and KMS client are configured and tested.
