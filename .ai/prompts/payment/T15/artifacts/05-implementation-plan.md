# payment · T15 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestRequest.java`
2. `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestResponse.java`
3. `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestationResult.java`
4. `services/payment/src/main/java/com/themistra/payment/common/AttestationRefusedException.java`
5. `services/payment/src/test/java/org/web3j/StubWeb3jClass.java`
6. `services/payment/src/test/java/org/tron/StubTronClass.java`
7. `services/payment/src/test/java/software/amazon/awssdk/services/kms/StubKmsClass.java`
8. `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueKmsAndChainClientUser.java`

## Files to modify

1. `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
2. `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
3. `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java`

## Public methods (signatures)

**`AttestRequest`** (record, internal wire DTO — mirrors `RegisterWatchRequest`):
```java
public record AttestRequest(String receiptDigestSha256, String chain, String txHash) {}
```

**`AttestResponse`** (record, internal wire DTO — mirrors the real crypto-service's own shape,
nullable fields are correct HERE because this is the raw deserialization target, not the
caller-facing type):
```java
public record AttestResponse(String signature, String kmsKeyId, Instant signedAt,
                              String outcome, String reason) {}
```

**`AttestationResult`** (sealed interface, the caller-facing typed outcome — Phase 3 Finding 1):
```java
public sealed interface AttestationResult {
    record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {}
    record Blocked(String reason) implements AttestationResult {}
}
```

**`AttestationRefusedException`** (unchecked, Phase 3 Finding 3 — carries best-effort 409 body
text; Javadoc discloses the real crypto-service's message is always a fixed generic string):
```java
public class AttestationRefusedException extends RuntimeException {
    public AttestationRefusedException(String message) { super(message); }
}
```

**`CryptoClient`** (new method, same class/constructor unchanged):
```java
public AttestationResult attest(String receiptDigestSha256, String chain, String txHash)
```
Throws `AttestationRefusedException` (409) or `CryptoServiceUnavailableException` (400/5xx/
connection failure/timeout/malformed body) — never returns `null`.

**Stub classes** (`org.web3j.StubWeb3jClass`, `org.tron.StubTronClass`,
`software.amazon.awssdk.services.kms.StubKmsClass`) — each a trivial, empty public class, sole
purpose is to exist at that fully-qualified name so a real `import` statement against it compiles
without the actual third-party SDK on the classpath.

**`RogueKmsAndChainClientUser`** (archtestfixtures, mirrors `RogueFloatingPointMoneyUser`'s own
shape — public fields of each stub type, so the new rule's negative-proof test has a genuine,
compiling violation to check against):
```java
public class RogueKmsAndChainClientUser {
    public org.web3j.StubWeb3jClass web3jField;
    public org.tron.StubTronClass tronField;
    public software.amazon.awssdk.services.kms.StubKmsClass kmsField;
}
```

## Private methods

- `CryptoClient`: a private helper to parse the raw `AttestResponse` into `AttestationResult`
  (switch on `outcome`), mirroring the existing inline validation style in `registerWatch` (no
  need for a separate class — this is a 5-line switch).
- `CryptoClient`: the existing `attest`/`registerWatch`/`unregisterWatch` methods already share
  one `RestClient` instance and one `requestInterceptor` set up in the constructor — no new
  private setup method needed.
- `ArchitectureTest`: a new private static `ArchCondition<JavaClass>` factory method for the L3
  rule (mirrors `onlyBeAccessedFromTheSameFeatureModule()`'s own existing shape) — actually,
  since this rule is a simple package-dependency check with no custom traversal logic needed,
  the built-in `noClasses().should().dependOnClassesThat().resideInAPackage(...)` DSL covers it
  directly (confirmed against `KmsSignerArchitectureTest`'s own identical usage) — **no custom
  `ArchCondition` needed for this rule**, unlike the two existing ones.

## Entities used

None. This task adds no entity, no repository, no migration.

## Repositories used

None.

## Services used

- `CryptoServiceTokenProvider` (existing, unchanged) — supplies the bearer token via the
  already-shared `requestInterceptor`.

## Unit/integration tests required

All unit, no Spring context, no Testcontainers (mirrors every existing `CryptoClient`/
`ArchitectureTest` test):

**`CryptoClientTest` (new methods, following the existing `startServer(...)`-helper pattern —
note the existing helper hardcodes the `/internal/v1/watches` context path, so attest tests need
their own local `HttpServer.createContext("/internal/v1/attest", ...)` setup, not the shared
`startServer` helper as-is):**
1. `shouldSendCorrectBodyAndBearerTokenOnAttest` — captures the request body and
   `Authorization` header, asserts all three fields and the token.
2. `shouldReturnSignedOutcomeWhenAttestResponseIsSigned` — `200` body with
   `outcome:"SIGNED"`, asserts `AttestationResult.Signed` with correct fields.
3. `shouldReturnBlockedOutcomeWhenAttestResponseIsBlocked` — `200` body with
   `outcome:"BLOCKED"`, asserts `AttestationResult.Blocked` with correct `reason`, and
   `assertThatCode(...).doesNotThrowAnyException()` (it is not an error).
4. `shouldThrowAttestationRefusedExceptionOnConflict` — `409` body, asserts
   `AttestationRefusedException` (not `CryptoServiceUnavailableException`), and that the
   exception message contains the response body's text.
5. `shouldThrowCryptoServiceUnavailableOnAttestBadRequest` — `400` body, asserts
   `CryptoServiceUnavailableException` (AC4, Finding 4's accepted disposition).
6. `shouldThrowCryptoServiceUnavailableOnAttestServerError` — `500`, asserts
   `CryptoServiceUnavailableException` (mirrors existing `shouldThrowOnServerError`).
7. `shouldThrowCryptoServiceUnavailableOnAttestConnectionFailure` — unreachable port, mirrors
   existing `shouldThrowOnConnectionFailure`.
8. `shouldThrowWhenAttestServerExceedsConfiguredReadTimeout` — mirrors existing
   `shouldThrowWhenServerExceedsConfiguredReadTimeout` exactly, bounded-time assertion included.
9. `shouldThrowCryptoServiceUnavailableOnAttestMalformedResponseBody` — `200` with an
   unparseable/unexpected JSON body (e.g. missing `outcome`), asserts
   `CryptoServiceUnavailableException` (new edge case, not present in the existing
   `registerWatch` tests in this exact shape — needed since `AttestationResult`'s mapping switch
   must have a defined behavior for an unrecognized/missing `outcome` value).

**`ArchitectureTest` (new rule + 2 tests, mirrors the two existing rules' own paired shape):**
10. `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` — canary, passes on current
    (already-clean) code.
11. `shouldBanKmsAndChainClientImportsActuallyFailsAgainstAGenuineViolation` — imports
    `RogueKmsAndChainClientUser` via a narrow `ClassFileImporter().importClasses(...)` (mirrors
    the three existing negative-proof tests' own shape exactly), asserts the rule throws
    `AssertionError` mentioning the rogue class and at least one banned package.

## Execution order

1. Three stub classes (`org.web3j.StubWeb3jClass`, `org.tron.StubTronClass`,
   `software.amazon.awssdk.services.kms.StubKmsClass`) — no dependencies, needed before the rogue
   fixture compiles.
2. `RogueKmsAndChainClientUser` (archtestfixtures) — depends on the three stubs.
3. `ArchitectureTest`'s new L3 rule + its two tests — depends on the rogue fixture existing to
   write the negative-proof test against (though the rule itself can be written first; the
   canary test will pass trivially until the rogue fixture exists to exercise the failure path).
4. `AttestationResult` (sealed interface) — no dependencies, can be written any time before
   `CryptoClient`.
5. `AttestationRefusedException` — no dependencies.
6. `AttestRequest` / `AttestResponse` (wire DTOs) — no dependencies.
7. `CryptoClient.attest(...)` — depends on 4/5/6 and the existing `CryptoServiceTokenProvider`/
   `CryptoClientProperties`.
8. `CryptoClientTest`'s new `attest` test methods — depends on 7.

Steps 1–3 (the ArchUnit side) and steps 4–8 (the `attest` method side) are independent of each
other and could be done in either order; listed ArchUnit-first only because it has zero
dependency on anything else in this plan, consistent with "front-load the independent,
infrastructural piece."

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create"/"Files to Modify"
lists (Phase 4) — no file is added that the brief does not authorize. `CryptoClientProperties`,
`registerWatch`/`unregisterWatch`, and everything outside `services/payment` remain untouched,
per the frozen brief's own "Files NOT to Modify."
