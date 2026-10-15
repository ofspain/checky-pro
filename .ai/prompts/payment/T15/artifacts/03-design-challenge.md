# payment · T15 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T15/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `CryptoClient.java`, `CryptoClientProperties.java`, `CryptoClientTest.java`, `CryptoServiceUnavailableException.java`, `ArchitectureTest.java`, `contracts/api/crypto-internal.yaml`, crypto-service's `AttestRequest`, `AttestResponse`, `AttestController`, `AttestExceptionHandler`, `AttestOutcome`, and the existing `RegisterWatchRequest/Response` DTOs.

---

## Executive summary

The brief is clear and mostly ready for Phase 4 freeze. The HTTP call shape, the `AttestationRefusedException` split from `CryptoServiceUnavailableException`, and the new L3 ArchUnit rule are all the right calls.

The design challenge surfaces four items that need a decision before implementation:

1. **Outcome representation:** a nullable-field record is not the "typed way to distinguish SIGNED/BLOCKED" the brief asks for — a sealed interface is safer.
2. **Rogue-fixture classpath problem:** the banned KMS/chain-client packages are not on the payment classpath, so the genuine-failure ArchUnit proof needs stub classes, not real SDK test dependencies.
3. **409 body parsing:** detecting the status is enough, but parsing the RFC 9457 `detail` field would make the exception far more useful.
4. **400 vs. 409 mapping:** crypto-service returns `400` for bean-validation failures (invalid hex digest, bad chain). The brief does not say how the payment client should map it.

The module-placement decision (keep `CryptoClient` in `common`) is pragmatic and correct.

---

## Finding 1 (Medium) — `SIGNED`/`BLOCKED` should be a sealed interface, not a nullable-field record

- **Issue:** The brief asks for "a typed way to distinguish `SIGNED` / `BLOCKED` ... from a `409 REFUSED` and from a true service-unavailable failure." It then proposes "one record with nullable fields mirroring the real `AttestResponse`'s own shape." A record with nullable `signature`/`kmsKeyId`/`signedAt`/`reason` is *not* typed at the call site — a future `ReceiptService` caller can still access `signature()` on a `BLOCKED` outcome and get `null`.
- **Impact:** Caller bugs where `BLOCKED` is mistaken for `SIGNED` will compile and only fail at runtime with `NullPointerException`. This undermines the stated purpose of making the distinction representable.
- **Evidence:**
  - `services/crypto/src/main/java/com/themistra/crypto/attest/AttestResponse.java:18-28` — the wire record uses nullable fields because it is a serialization shape.
  - The payment-side consumer does not have to mirror that shape.
- **Recommendation:** Introduce a payment-side sealed interface, e.g.:
  ```java
  public sealed interface AttestationResult {
      record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {}
      record Blocked(String reason) implements AttestationResult {}
  }
  ```
  `CryptoClient.attest(...)` returns `AttestationResult`. `AttestationRefusedException` covers 409; `CryptoServiceUnavailableException` covers transport/5xx/malformed response. This gives the compiler-enforced three-way distinction the brief actually wants.
- **Confidence:** High.

---

## Finding 2 (Medium) — The ArchUnit rogue fixture needs a way to compile without real banned SDKs

- **Issue:** The brief wants to ban imports from `org.web3j..`, `org.tron..`, and `software.amazon.awssdk.services.kms..`. None of these artifacts are on the payment module's compile or test classpath today (`payment-service` only depends on `software.amazon.awssdk:s3`, not `kms`, and has no web3j/tron dependencies). A rogue fixture under `archtestfixtures` that imports `org.web3j..` or `software.amazon.awssdk.services.kms.KmsClient` will not compile unless the build gains those dependencies.
- **Impact:** The genuine-failure ArchUnit test cannot be written as described without either (a) adding heavyweight test-scoped SDK dependencies, or (b) creating stub classes in the forbidden packages under `src/test/java`.
- **Evidence:**
  - `services/payment/pom.xml:42-90` — dependencies include `s3` but not `kms`, `web3j`, or `tron`.
  - `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueFloatingPointMoneyUser.java` — existing rogue fixtures only use JDK types that are always on the classpath.
- **Recommendation:** Create minimal stub classes in `src/test/java` under the banned package names, e.g.:
  - `src/test/java/org/web3j/StubWeb3jClass.java`
  - `src/test/java/org/tron/StubTronClass.java`
  - `src/test/java/software/amazon/awssdk/services/kms/StubKmsClass.java`
  Then the rogue fixture can import those stubs. The main `ArchitectureTest` scan uses `DoNotIncludeTests()`, so these stubs are not normally inspected; they are only pulled in by the negative canary test via explicit class import. This avoids adding real KMS/chain-client JARs to the payment module.
- **Confidence:** High.

---

## Finding 3 (Low) — `AttestationRefusedException` should carry the problem detail from the 409 body

- **Issue:** Crypto-service returns `409 CONFLICT` with an RFC 9457 `ProblemDetail` body whose `detail` field explains why attestation was refused (quorum/finality unmet, screening error, etc.). The brief only says "throw `AttestationRefusedException`." If the exception discards the body, T17 callers and operators lose the reason.
- **Impact:** Debugging a refused attestation requires reading crypto-service logs instead of the payment-service exception message.
- **Evidence:**
  - `services/crypto/src/main/java/com/themistra/crypto/attest/AttestExceptionHandler.java:18-24` — sets `title` and `detail` on the problem detail.
  - `contracts/api/crypto-internal.yaml:66-71` — describes the 409 as a gate failure.
- **Recommendation:** Add a `String reason` field to `AttestationRefusedException`. In `CryptoClient.attest`, when the response status is 409, read the response body as a `String` (or `ProblemDetail`) and pass the `detail` value (or the raw body fallback) into the exception. Even a best-effort parse is better than none.
- **Confidence:** Medium.

---

## Finding 4 (Low) — The brief is silent on `400` responses from crypto-service

- **Issue:** Crypto-service validates `receiptDigestSha256` with `@Pattern(regexp = "^[0-9a-fA-F]{64}$")` and `chain` with `@Pattern(regexp = "ETHEREUM|TRON")`. An invalid digest or chain produces `400 BAD_REQUEST`. The brief's AC4 maps 5xx/network/timeout/malformed to `CryptoServiceUnavailableException` but does not mention 400.
- **Impact:** A caller bug that passes a bad digest will currently be caught by the generic `RestClientException` handler and wrapped as `CryptoServiceUnavailableException`. That is defensible (upstream rejected the request), but it is semantically different from "the service is unavailable." If the caller wants to distinguish its own bugs from upstream failures, it cannot.
- **Evidence:**
  - `services/crypto/src/main/java/com/themistra/crypto/attest/AttestRequest.java:19-22` — validation annotations.
- **Recommendation:** Disposition explicitly. Two valid options:
  1. Treat `400` like any other non-2xx/non-409 response and wrap it in `CryptoServiceUnavailableException` (simplest, consistent with "trust the caller" and no local validation).
  2. Map `400` to a new `IllegalArgumentException` or a dedicated `AttestationRequestInvalidException` so caller bugs fail fast and distinctly.
  Either is acceptable, but the choice should be documented in the brief's Outputs/AC4, not left to implementation inference.
- **Confidence:** Medium.

---

## Finding 5 (Low) — The module-placement decision is pragmatic but contradicts design/package.md

- **Issue:** The brief correctly notes that `package.md` §4 and `design.md` place this client under an `attestation` module, but keeps `CryptoClient` in `common` because `registerWatch`/`unregisterWatch` already have callers in `invoice` and `consumer`. This is a reasoned, disclosed deviation.
- **Impact:** Future readers may expect `attestation` to own `CryptoClient` and try to refactor it. The deviation needs to be discoverable.
- **Recommendation:** Add a short note to `package.md` (or a code comment/Javadoc on `CryptoClient`) recording the decision: "`CryptoClient` lives in `common` because it is shared plumbing called from multiple feature modules (`invoice`, `consumer`, and the future receipt/attestation flow); it is not owned by `attestation`." Do not move the class.
- **Confidence:** Low.

---

## Positive confirmations

- The `attest` method signature `attest(String receiptDigestSha256, String chain, String txHash)` matches the contract's `AttestRequest` exactly.
- Reusing the existing `CryptoServiceTokenProvider` and `CryptoClientProperties.attestPath()` is correct; no new config is needed.
- Distinguishing `409 REFUSED` from transport failures via `AttestationRefusedException` is the right design; retrofitting later would be a breaking change.
- `BLOCKED` must be a normal return value, not an exception — the brief gets this right.
- The new ArchUnit rule belongs in `ArchitectureTest`, mirroring the existing L6 and L12 rules with paired canary/negative tests.
- `CryptoClient` remains stateless and thread-safe.
- The real-local-`HttpServer` test pattern from `CryptoClientTest` should be reused for `attest` tests.

---

## Disposition summary

| Finding | Severity | Disposition |
|---|---|---|
| 1. Sealed interface for SIGNED/BLOCKED | Medium | Change brief to require `AttestationResult` sealed interface |
| 2. Rogue-fixture classpath | Medium | Add stub classes in banned packages under `src/test/java` |
| 3. Parse 409 problem detail | Low | Add `reason` field to `AttestationRefusedException` |
| 4. Map 400 responses | Low | Document choice in AC4 (recommend option 1: `CryptoServiceUnavailableException`) |
| 5. Document module-placement deviation | Low | Add note to `package.md` or `CryptoClient` Javadoc |

---

## Recommended next step

Update the brief with the sealed-interface outcome shape and the 400/409 mapping choice, then freeze and proceed to Phase 5 implementation.
