# auth · T16 — Phase 5: Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN). Plan only — no code in this artifact.

---

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/TotpGenerator.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaSeedEncryption.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaProperties.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEncryptionException.java` — new,
  authorized by the frozen brief's AC8 ("a dedicated exception"); follows the existing
  one-class-per-exception convention (`RoleNotFoundException`, `AccountNotFoundException`, etc. —
  no shared exception hierarchy in this codebase). Package-private-safe `RuntimeException` subclass,
  no fields carrying key material.
- `services/auth/src/test/java/com/themistra/auth/mfa/TotpGeneratorTest.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaSeedEncryptionTest.java`

All four main-code files trace to the frozen brief's "Files to Create" list plus AC8's dedicated-
exception requirement (an artifact of the same "Files to Create" scope, not a new file class the
brief didn't anticipate). No entity, repository, controller, or `ExceptionHandler` — none exist for
`mfa/` yet (tasks 17-19), so `MfaEncryptionException` is unmapped for now, exactly like the brief's
"Out" scope says.

## Files to Modify

- `services/auth/src/main/resources/application.properties` — add:
  ```properties
  themistra.auth.mfa.issuer-name=${MFA_ISSUER_NAME:Themistra}
  themistra.auth.mfa.seed-kek-arn=${MFA_SEED_KEK_ARN:}
  ```
  (Mirrors `SigningKeysProperties`'s `${ENV_VAR:default}` placeholder convention exactly.)
- `services/auth/pom.xml` — add, in a new top-level `<dependencyManagement>` block (none exists
  in this pom today — this is the first BOM the module needs):
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>bom</artifactId>
        <version><!-- confirmed against Maven Central at Phase 6, not guessed here --></version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```
  and, in `<dependencies>`:
  ```xml
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>kms</artifactId>
  </dependency>
  ```

## Public Methods (signatures)

**`TotpGenerator`** (`@Component`, no AWS import — TIB/ADR-0003 constraint):
```java
public byte[] generateSecret()
public String buildProvisioningUri(byte[] secret, String accountLabel)
```
- `generateSecret()`: 20 bytes via `SecureRandom` (AC1).
- `buildProvisioningUri`: builds `otpauth://totp/<urlEncode(issuer)>:<urlEncode(accountLabel)>?secret=<base32>&issuer=<urlEncode(issuer)>&algorithm=SHA1&digits=6&period=30`
  (AC2). `issuer` read from injected `MfaProperties`.

**`MfaProperties`** (`@ConfigurationProperties(prefix = "themistra.auth.mfa")`, record):
```java
public record MfaProperties(@NotBlank String issuerName, String seedKekArn) { }
```
- `seedKekArn` deliberately has no `@NotBlank` — blank is a legal value in `local` profile (mirrors
  `SigningKeysProperties.KeySlot`'s un-validated-at-the-record-level pattern; the profile-conditional
  check is programmatic, in `MfaSeedEncryption`, not declarative bean validation, since
  `@Validated` cannot express "required unless profile X" — same reasoning Phase 3 Finding #5 raised).

**`MfaSeedEncryption`** (`@Component`):
```java
public MfaSeedEncryption(MfaProperties properties, Environment environment, KmsClient kmsClient)
public byte[] encrypt(byte[] rawSecret)
public byte[] decrypt(byte[] envelope)
```
- Constructor performs the AC5 startup guard eagerly (fail-fast on the calling thread during bean
  construction — same shape as `SigningKeyMaterial.load`'s `IllegalStateException`, not a separate
  `@PostConstruct`): if `!environment.acceptsProfiles(Profiles.of("local")) && properties.seedKekArn().isBlank()`,
  throw `IllegalStateException` immediately, message modeled on `SigningKeyMaterial`'s wording
  ("Refusing to start ... outside local development").
- `encrypt`/`decrypt` branch on `environment.acceptsProfiles(Profiles.of("local")) && properties.seedKekArn().isBlank()`
  to select the version-`0x00` local path vs. version-`0x01` KMS path (AC4). ADR-0003 names "the
  active Spring profile is `local`" explicitly, so profile-based detection here is spec-mandated,
  not just a Phase 3 suggestion (Finding #6) — `Environment.acceptsProfiles` is the correct call,
  not `getActiveProfiles()` string-equality.
- A new `Cipher.getInstance("AES/GCM/NoPadding")` per call (AC7); a new 12-byte `SecureRandom` nonce
  per call.
- `decrypt` on an envelope whose version byte is not `0x00`/`0x01` throws `MfaEncryptionException`
  (AC8), never falls through.

## Private / Package-Private Methods

**`TotpGenerator`**:
```java
private static String base32Encode(byte[] data)   // uppercase A-Z2-7, unpadded, RFC 4648
private static String urlEncode(String value)     // RFC 3986 via java.net.URLEncoder + space fixup
```

**`MfaSeedEncryption`**:
```java
private boolean localKeyMode()                          // environment.acceptsProfiles(local) && arn blank
private byte[] encryptLocal(byte[] rawSecret)            // version 0x00 path, static local-only AES key
private byte[] encryptKms(byte[] rawSecret)               // version 0x01 path, GenerateDataKey + AES-GCM
private byte[] decryptLocal(byte[] envelope)
private byte[] decryptKms(byte[] envelope)
private static byte[] gcmEncrypt(SecretKey key, byte[] nonce, byte[] plaintext)
private static byte[] gcmDecrypt(SecretKey key, byte[] nonce, byte[] ciphertextAndTag)
```
- `LOCAL_DEV_KEY`: `private static final byte[]` (32 bytes), compile-time constant, doc comment
  marking it local-only/unsafe outside `local` (Phase 3 Finding #4, restating ADR-0003 verbatim).

**`KmsClient` bean** — new `@Bean` (in the existing config style; likely a small
`MfaKmsClientConfig` inline in `MfaSeedEncryption`'s package is unnecessary — a single `@Bean
KmsClient kmsClient()` method is enough, added to `AuthServiceApplication` or a new minimal
`@Configuration` class colocated in `mfa/`). Decision for Phase 6: colocate as
`@Bean` inside a tiny `mfa/MfaConfig.java`— **not listed separately above because it was not in the
frozen brief's Files-to-Create list.** Flagging this now rather than silently adding a file:

> **Open Question (new, raised at Phase 5):** the frozen brief's Files-to-Create list has no file
> for the `KmsClient` bean definition. Constructing `KmsClient.builder().build()` inline inside
> `MfaSeedEncryption`'s constructor (guarded so it's skipped entirely in local-key mode, avoiding
> any requirement for AWS credentials in local dev) fits inside the brief's existing three files
> and needs no new file. Recommended resolution: build the `KmsClient` lazily/conditionally inside
> `MfaSeedEncryption` itself, not as a separate Spring `@Bean`. Proceeding on this basis; flag here
> rather than deviating silently, per guardrails.

## Entities Used

None (T16 has no persistence — matches the frozen brief's Scope/Out).

## Repositories Used

None.

## Services Used

None — `TotpGenerator` and `MfaSeedEncryption` are the services created by this task, consumed by
no one yet (tasks 17/18 wire them in).

## Unit / Integration Tests Required

**`TotpGeneratorTest`** (pure unit, no Spring context):
- `generateSecret` returns exactly 20 bytes, and two calls differ (randomness sanity check, not a
  statistical entropy proof).
- `buildProvisioningUri` matches `otpauth://totp/<issuer>:<accountLabel>?secret=[A-Z2-7]{32}&issuer=<issuer>&algorithm=SHA1&digits=6&period=30`
  for a known secret (deterministic byte array fixture) — asserts exact Base32 output, no padding,
  uppercase-only, and that label/issuer segments are URL-encoded when they contain reserved
  characters (AC2).

**`MfaSeedEncryptionTest`** (pure unit; `KmsClient` mocked via Mockito — AWS SDK v2 clients are
interfaces):
- Local mode (`local` profile, blank `seed-kek-arn`): `encrypt` produces a version-`0x00` envelope
  with no interaction with the mocked `KmsClient`; `decrypt` round-trips (AC4).
- KMS mode (non-`local` profile, or `local` with a non-blank ARN): mocked `GenerateDataKey` returns
  a fixed 32-byte plaintext key + non-empty `CiphertextBlob`; `encrypt` produces a version-`0x01`
  envelope with the mock's `CiphertextBlob` in the wrapped-key field, correct 2-byte big-endian
  length prefix, 12-byte nonce, then ciphertext+tag; mocked `Decrypt` of that same blob returns the
  same plaintext key so `decrypt` round-trips (AC3, Finding #13's pinned mock contract).
- Constructing `MfaSeedEncryption` in a non-`local` profile with a blank `seed-kek-arn` throws
  `IllegalStateException` before any encrypt/decrypt call (AC5).
- Ciphertext bytes never contain the raw seed as a subsequence, for both local and KMS modes (AC6).
- A mocked "wrong key" `Decrypt` response (SDK throws, or returns a different plaintext key)
  causes `decrypt` to fail distinctly — either propagating the SDK exception or wrapping it, but
  never returning a silently-wrong plaintext (AC6).
- An envelope with version byte `0x02` (or any value other than `0x00`/`0x01`) makes `decrypt`
  throw `MfaEncryptionException` (AC8).
- Concurrent `encrypt`/`decrypt` calls from multiple threads on one `MfaSeedEncryption` instance
  complete correctly (exercises AC7's new-`Cipher`-per-call requirement without relying on
  implementation internals — e.g. `ExecutorService` + `CompletableFuture.allOf` over N parallel
  round-trips, asserting each thread's own plaintext comes back unmodified).

No integration test with LocalStack/Testcontainers KMS, per the frozen brief's confirmed scope
decision.

## Execution Order

1. `pom.xml` — add `<dependencyManagement>` BOM + `kms` dependency (must resolve before any `mfa`
   code references `software.amazon.awssdk.services.kms.*`).
2. `MfaProperties.java` — config record (`TotpGenerator` and `MfaSeedEncryption` both depend on it).
3. `MfaEncryptionException.java` — trivial, no dependencies, needed before `MfaSeedEncryption`
   compiles its version-byte guard.
4. `TotpGenerator.java` — no dependency on `MfaSeedEncryption`; can be built and tested standalone.
5. `TotpGeneratorTest.java` — validate step 4 before moving to the more complex component.
6. `MfaSeedEncryption.java` — depends on steps 1-3.
7. `MfaSeedEncryptionTest.java` — validate step 6.
8. `application.properties` — add the two new keys (needed for the app context to start at all
   once `MfaProperties` exists as a `@ConfigurationProperties` bean; safe to do any time after step
   2, placed last here only because it has no compile-time ordering dependency, just a runtime one).

## Open Questions

- **`KmsClient` bean placement** (raised above): resolved as "construct inline/lazily inside
  `MfaSeedEncryption`, not a separate `@Bean` file" — no new file needed, stays inside the frozen
  brief's authorized file set. Flagged, not silently decided, per guardrails; revisit at Phase 6 if
  it turns out awkward to test.
