# auth · T16 · Phase 8 — Independent Code Review

Consumes Phase 6 implementation and Phase 7 self-review. Findings only — no rewrites.

---

### 1. Deployed environments can silently fall back to the local-only TOTP key

- **Issue:** `services/auth/deploy/k8s/base.yaml` was not updated to set `MFA_SEED_KEK_REQUIRED: "true"`. Combined with `application.properties` defaulting `themistra.auth.mfa.seed-kek-required=false`, a deployed pod whose External Secret omits or leaves `MFA_SEED_KEK_ARN` blank will boot with `seedKekRequired=false` and use the fixed version-`0x00` local key instead of failing closed.
- **Evidence:**
  - `services/auth/src/main/resources/application.properties:78`: `themistra.auth.mfa.seed-kek-required=${MFA_SEED_KEK_REQUIRED:false}`
  - `services/auth/deploy/k8s/base.yaml:57-64`: sets `JWT_REQUIRE_CONFIGURED: "true"` but no `MFA_SEED_KEK_REQUIRED`; `MfaSeedEncryption`’s guard only fails when `seedKekRequired=true`.
  - ADR-0003: "`dev`/`staging`/`prod` must never produce a version-`0x00` envelope".
- **Recommendation:** Add `MFA_SEED_KEK_REQUIRED: "true"` to `base.yaml`’s env list, mirroring `JWT_REQUIRE_CONFIGURED`, or revert to a profile-aware guard that refuses blank ARNs when `SPRING_PROFILES_ACTIVE` is not `local`. Do not merge without one of these fixes.
- **Confidence:** High
- **Severity:** Critical

---

### 2. Implementation deviates from the LOCKED profile-based local-mode rule

- **Issue:** L14 and ADR-0003 tie the version-`0x00` local fallback to the active Spring profile being `local` plus a blank ARN. The implementation replaced that with a separate `seed-kek-required` boolean that defaults to `false` everywhere. This is a silent change to a LOCKED decision and shifts the safety burden to deploy-time config (which is currently missing; see #1).
- **Evidence:**
  - ADR-0003: "usable only when the active Spring profile is `local` and `themistra.auth.mfa.seed-kek-arn` is blank".
  - `MfaSeedEncryption.java:81-87`: guard checks `properties.seedKekRequired()`, not the active profile.
  - `MfaProperties.java:22`: new `seedKekRequired` boolean is unannotated and defaults to `false` in `application.properties`.
- **Recommendation:** Either restore a profile-aware check (e.g., inject `Environment` and assert `!acceptsProfiles("local")` implies ARN present) and update `base.yaml` accordingly, or formally amend L14/ADR-0003 and `base.yaml` to document the boolean-guard pattern. Do not ship with the document saying one thing and the code doing another.
- **Confidence:** High
- **Severity:** High

---

### 3. Malformed or truncated envelopes leak raw JDK exceptions

- **Issue:** `MfaSeedEncryption.decrypt` does not translate structural envelope corruption into `MfaEncryptionException`. AC8 only asserts the unsupported-version path. A null/empty array, an empty envelope, or a `wrappedKeyLength` larger than the remaining bytes will surface `NullPointerException`, `ArrayIndexOutOfBoundsException`, or `BufferUnderflowException` to callers.
- **Evidence:**
  - `MfaSeedEncryption.java:110`: `byte version = envelope[0]` with no null/length guard.
  - `MfaSeedEncryption.java:203-213`: `parseEnvelope` reads `wrappedKeyLength`, allocates an array, and calls `buffer.get(wrappedKey)`; any truncation throws `BufferUnderflowException`.
- **Recommendation:** Add a minimum-length check before indexing and wrap the entire `parseEnvelope` body plus the version read in a try/catch that converts `RuntimeException` (or the specific JDK exception types) into `MfaEncryptionException` with a generic "corrupted envelope" message.
- **Confidence:** High
- **Severity:** Medium

---

### 4. KMS `GenerateDataKey` failures are not wrapped like KMS `Decrypt` failures

- **Issue:** `encryptKms` lets AWS SDK runtime exceptions from `generateDataKey` propagate unmodified, while `decryptKms` wraps them in `MfaEncryptionException`. This asymmetry means callers of `encrypt` may see `KmsException`/`KmsServiceException` types, leaking the AWS SDK dependency beyond `MfaSeedEncryption` and making error handling inconsistent.
- **Evidence:**
  - `MfaSeedEncryption.java:130-133`: direct `kmsClient.generateDataKey(...)` call without a try/catch.
  - `MfaSeedEncryption.java:150-157`: `decryptKms` does wrap AWS failures.
- **Recommendation:** Wrap the `generateDataKey` call in the same try/catch pattern used for `decrypt`, throwing `MfaEncryptionException("Failed to generate MFA seed data key via KMS", e)`.
- **Confidence:** High
- **Severity:** Medium

---

### 5. No ArchUnit rule enforces the single-class AWS SDK exception

- **Issue:** ADR-0003, D-025, and `agents.md` all state that `MfaSeedEncryption` is the only class in `services/auth` permitted to import the AWS SDK. `ArchitectureTest` encodes several similar invariants but has no rule for this one, so a future class could silently import `software.amazon.awssdk.*` and CI would still pass.
- **Evidence:**
  - `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`: no AWS SDK import restriction.
  - `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java:24-39`: Javadoc explicitly claims the single-class exception.
- **Recommendation:** Add an ArchUnit rule such as `noClasses().that().doNotHaveFullyQualifiedName("com.themistra.auth.mfa.MfaSeedEncryption").should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")`.
- **Confidence:** High
- **Severity:** Medium

---

### 6. `KmsClient` is never closed, inconsistent with graceful shutdown

- **Issue:** `MfaSeedEncryption` holds a constructed AWS `KmsClient` (an `AutoCloseable` resource) but does not close it on context shutdown. `application.properties:82` sets `server.shutdown=graceful`, showing the project cares about clean teardown.
- **Evidence:**
  - `MfaSeedEncryption.java:66`: `private final KmsClient kmsClient;`
  - `MfaSeedEncryption.java:96`: built inline and stored, never closed.
- **Recommendation:** Implement `AutoCloseable` (or `DisposableBean`) and close the client in `destroy()` when it is non-null.
- **Confidence:** High
- **Severity:** Low–Medium

---

### 7. Zeroing the KMS plaintext data key is ineffective

- **Issue:** `encryptKms` and `decryptKms` call `Arrays.fill(plaintextKey, ...)` in `finally` blocks, but `new SecretKeySpec(plaintextKey, "AES")` clones the input array internally before the zeroing runs. The scrubbed array is the caller’s copy, while the live secret material remains in the `SecretKeySpec` (and any copy the `Cipher` keeps) until GC.
- **Evidence:**
  - `MfaSeedEncryption.java:135-142`: `SecretKeySpec` constructed at line 139 before `Arrays.fill` at line 142.
  - `MfaSeedEncryption.java:159-164`: same pattern at lines 161/163.
- **Recommendation:** Remove the misleading zeroing (it provides no real guarantee without `Destroyable` support), or attempt `key.destroy()` on the `SecretKeySpec` and accept that it may not be supported. Document the chosen posture.
- **Confidence:** High
- **Severity:** Low

---

### 8. `Decrypt` request unnecessarily pins the call to the configured ARN

- **Issue:** `decryptKms` passes `.keyId(properties.seedKekArn())` to `DecryptRequest`. AWS KMS `Decrypt` can derive the CMK from the ciphertext blob; adding `keyId` ties decryption to whatever ARN is currently configured. If the configured value is a re-pointed alias or if the original encryption used a different but still valid key, decryption can fail even though the blob is valid.
- **Evidence:**
  - `MfaSeedEncryption.java:150-154`: explicit `.keyId(...)` on `DecryptRequest`.
  - AWS SDK v2 docs for `DecryptRequest.keyId()`: optional, used only to verify the same CMK.
- **Recommendation:** Remove `.keyId(...)` from `DecryptRequest` unless there is an explicit policy requirement to restrict decryption to a specific key identifier. The ciphertext blob already contains the CMK reference.
- **Confidence:** Medium
- **Severity:** Low–Medium

---

### 9. Local-dev key constant may trigger secret-scanning false positives

- **Issue:** `LOCAL_DEV_KEY` is a 32-byte hardcoded AES key. While ADR-0003 explicitly permits this and the Javadoc marks it local-only, the repo runs a gitleaks gate (`agents.md` Security section). A 32-byte hex array is exactly the shape secret scanners flag.
- **Evidence:**
  - `MfaSeedEncryption.java:58-63`: hardcoded key array.
  - `agents.md`: "gitleaks gate in CI".
- **Recommendation:** Add a gitleaks allowlist entry in `.gitleaks.toml` (or the repo-equivalent) for `MfaSeedEncryption.LOCAL_DEV_KEY` before merge, or place a gitleaks-ignore comment on the constant and verify the scanner honors it.
- **Confidence:** Medium
- **Severity:** Low

---

### 10. `TotpGenerator` does not validate `secret` or `accountLabel` inputs

- **Issue:** `buildProvisioningUri` accepts `null` or empty inputs without checks, producing a malformed URI or a `NullPointerException`. Passing an empty `accountLabel` yields `otpauth://totp/Themistra:?secret=...`, which is ambiguous for authenticator apps.
- **Evidence:**
  - `TotpGenerator.java:45-54`: no validation of `secret` length or `accountLabel` content.
  - `TotpGenerator.java:26-28`: `MfaProperties.issuerName()` is `@NotBlank` via configuration, but `accountLabel` is caller-controlled.
- **Recommendation:** Add non-null/non-blank guards for `secret` (must be 20 bytes) and `accountLabel`; throw `IllegalArgumentException` with a message that never includes the secret material.
- **Confidence:** High
- **Severity:** Low

---

### 11. `URLEncoder` is HTML form encoding, not strict RFC 3986 percent encoding

- **Issue:** `urlEncode` uses `URLEncoder.encode(...).replace("+", "%20")`. `URLEncoder` is designed for `application/x-www-form-urlencoded`, not RFC 3986 query parameters. It over-encodes unreserved characters such as `~` as `%7E` and does not handle the differences between path and query encoding. In practice most authenticators accept this, but the code deviates from the brief’s "RFC-3986-encoded" claim.
- **Evidence:**
  - `TotpGenerator.java:76-78`: `URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")`.
  - RFC 3986 §2.3: `~` is unreserved and should not be percent-encoded.
- **Recommendation:** Use `org.springframework.web.util.UriUtils.encodeQueryParam` (which follows RFC 3986 for query parameters) or document that the implementation uses form-encoding-with-space-fix and that authenticators must tolerate it.
- **Confidence:** Medium
- **Severity:** Low

---

### 12. Envelope version `0x00` does not enforce a zero wrapped-key length

- **Issue:** ADR-0003 specifies that for version `0x00`, `N = 0` (wrapped-data-key length is zero). `parseEnvelope` does not validate this, so a corrupted version-`0x00` envelope with a non-zero `wrappedKeyLength` would still be accepted and the wrapped-key bytes would be skipped/ignored.
- **Evidence:**
  - `MfaSeedEncryption.java:203-213`: `parseEnvelope` reads `wrappedKeyLength` and advances the buffer unconditionally.
  - ADR-0003 table: `N = 0 for version 0x00`.
- **Recommendation:** In `decryptLocal`, assert `parsed.wrappedKey().length == 0` and throw `MfaEncryptionException` if the local envelope contains a non-empty wrapped key.
- **Confidence:** Medium
- **Severity:** Low

---

## Summary

The implementation is largely correct, but the guard mechanism is a real deviation from the LOCKED ADR-0003/L14 contract and is not yet wired into the deployment manifest, creating a critical silent-fallback risk in deployed environments. The remaining findings are security-hardening items (malformed-envelope handling, AWS SDK containment via ArchUnit, KMS exception wrapping, resource cleanup) and lower-severity interoperability/secret-scanning concerns.
