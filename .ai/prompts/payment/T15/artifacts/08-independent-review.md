# payment · T15 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T15/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `CryptoClient.java`, `AttestationResult.java`, `AttestRequest.java`, `AttestResponse.java`, `AttestationRefusedException.java`, `ArchitectureTest.java`, `RogueKmsAndChainClientUser.java`, the three stub classes, `06-implementation-notes.md`.

---

## Executive summary

- T15 production code is cleanly implemented and compiles. The sealed-interface `AttestationResult`, the `AttestationRefusedException` split, and the new L3 ArchUnit rule all match the approved brief.
- The full suite is **299/299 green**.
- The most consequential finding confirms the self-review: `CryptoClient.attest` does not validate that a `SIGNED` response actually contains a non-null `signature`, which could let a malformed upstream response propagate as a usable outcome.
- `CryptoClientTest` has no `attest` tests yet — this is explicitly deferred to Phase 10 per the implementation notes, but the existing test helper is watch-specific, so Phase 10 will need a small helper addition.

---

## Confirming self-review Finding 1 (Medium) — `SIGNED` outcome accepts null `signature`

- **Independent assessment:** Confirmed. The `SIGNED_OUTCOME` branch returns `new AttestationResult.Signed(response.signature(), response.kmsKeyId(), response.signedAt())` without checking any of the three fields. `registerWatch` rejects a null `watchId` at the same layer; `attest` should do the equivalent for `signature` (and arguably `kmsKeyId`/`signedAt`, since a `SIGNED` outcome without any of them is semantically broken).
- **Impact:** A buggy or malicious Crypto Service returning `{"outcome":"SIGNED"}` would produce a caller-visible `Signed` record with null fields. A future `ReceiptService` caller could persist an invalid receipt unless it re-validates.
- **Recommendation:** Add a guard after the outcome dispatch:
  ```java
  if (response.signature() == null || response.kmsKeyId() == null || response.signedAt() == null) {
      throw new CryptoServiceUnavailableException(
          "Crypto Service returned SIGNED outcome with missing fields for " + chain + ":" + txHash, null);
  }
  ```
- **Confidence:** High.

---

## Confirming self-review Finding 2 (Low) — `AttestationRefusedException` charset

- **Independent assessment:** Confirmed, but low impact. The 409 catch uses `e.getResponseBodyAsString()` (no charset argument). The real crypto-service message is documented as ASCII-only and generic, so this is harmless today.
- **Recommendation:** Pin UTF-8 explicitly: `e.getResponseBodyAsString(StandardCharsets.UTF_8)`. This matches RFC 9457 `problem+json` convention and is future-proof if the message ever contains non-ASCII detail.
- **Confidence:** Low.

---

## New Finding 1 (Medium) — No `CryptoClientTest` coverage for `attest` yet

- **Issue:** `CryptoClientTest` still only exercises `registerWatch`/`unregisterWatch`. The implementation notes correctly disclose that the 9 `attest` unit tests are deferred to Phase 10. However, the existing `startServer(...)` helper is hard-wired to `/internal/v1/watches`, so Phase 10 cannot reuse it for `/internal/v1/attest` without modification.
- **Impact:** A future test author may duplicate the server setup or make a path mistake.
- **Recommendation:** When writing Phase 10 tests, either add a `startAttestServer(...)` helper or refactor `startServer` to take the path as a parameter. Also cover the self-review Finding 1 fix with a test that sends `{"outcome":"SIGNED","signature":null,...}` and asserts `CryptoServiceUnavailableException`.
- **Confidence:** Medium.

---

## New Finding 2 (Low) — `AttestResponse` is public but only consumed internally

- **Issue:** `AttestResponse` is declared `public` in `common.crypto`. It is only used as a deserialization target inside `CryptoClient`; callers never see it because `CryptoClient` returns `AttestationResult`.
- **Impact:** Minimal — it does not leak across packages. It just widens the public surface slightly.
- **Recommendation:** Consider making it package-private (`class AttestResponse`) since `CryptoClient` is in the same package. This would not affect the sealed-interface public API. If the Phase 10 tests need to inspect raw response parsing, they can live in the same package or use the public `AttestationResult` outcomes.
- **Confidence:** Low.

---

## New Finding 3 (Low) — `AttestationResult.Signed` record allows null fields at the type level

- **Issue:** The `Signed` record itself does not enforce non-null `signature`, `kmsKeyId`, or `signedAt`. The planned client-side guard (self-review Finding 1) fixes the production path, but the record type still permits a null-bearing instance if constructed elsewhere (e.g., in a test or a future caller).
- **Impact:** A regression in the guard or a test helper could reintroduce nulls.
- **Recommendation:** Add compact-constructor validation in the record:
  ```java
  record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {
      public Signed {
          Objects.requireNonNull(signature, "signature must not be null");
          Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
          Objects.requireNonNull(signedAt, "signedAt must not be null");
      }
  }
  ```
  This makes the type itself self-guarding and complements the client-side check.
- **Confidence:** Low.

---

## New Finding 4 (Low) — `AttestationRefusedException` carries raw problem+json text

- **Issue:** The 409 body from crypto-service is an RFC 9457 `ProblemDetail` JSON object. Passing the raw JSON into `AttestationRefusedException` makes the exception message contain escaped JSON, which is noisy in logs.
- **Impact:** Operators see `"{\"title\":\"Attestation refused\",\"detail\":\"...\"}"` instead of a clean message.
- **Recommendation:** Best-effort parse the body as `ProblemDetail` (Spring's `ProblemDetail` or Jackson into a small Map) and use `detail` as the exception message, falling back to the raw body only if parsing fails. This was raised in Phase 3 Finding 3 and implemented as raw-body best-effort; parsing would be a polish improvement, not a correctness fix.
- **Confidence:** Low.

---

## Positive confirmations

- `AttestationResult` is correctly a sealed interface with `Signed`/`Blocked` records — no nullable-field leakage at the call site.
- `CryptoClient.attest` reuses the existing `restClient`, bearer-token interceptor, and timeouts.
- Catch order (`HttpClientErrorException.Conflict` before `RestClientException`) is correct and compiles.
- `400`, all `5xx`, connection failures, read timeouts, and malformed/unrecognized outcomes all collapse into `CryptoServiceUnavailableException`, matching AC4.
- `409` is mapped to `AttestationRefusedException`, distinct from transport failures.
- The L3 ArchUnit rule uses the built-in `noClasses().should().dependOnClassesThat().resideInAnyPackage(...)` DSL, scans `com.themistra.payment..`, and has a paired canary + negative-proof test.
- The three stub classes in `org.web3j`, `org.tron`, and `software.amazon.awssdk.services.kms` packages are a clean way to prove the rule without adding real SDK dependencies.
- `RogueKmsAndChainClientUser` is placed in `archtestfixtures` and excluded from the production scan.
- The module-placement Javadoc on `CryptoClient` documents the `common` vs. `attestation` deviation.
- No new production config or persistence is introduced.
- Full payment suite passes at **299/299**.

---

## Build context

- `ArchitectureTest`: **7/7 passing** (new L3 canary + negative proof both green).
- `CryptoClientTest`: **15/15 passing** (existing `registerWatch`/`unregisterWatch` coverage; no `attest` tests yet, deferred to Phase 10).
- Full `mvn -pl services/payment -am test`: **299/299 passing**.

---

## Recommended follow-up order

1. Add null-field guard in the `SIGNED` branch (self-review Finding 1).
2. Add compact-constructor null checks to `AttestationResult.Signed` (New Finding 3).
3. Pin UTF-8 in the 409 body read (self-review Finding 2).
4. In Phase 10, add the 9 deferred `CryptoClientTest.attest` tests, including a malformed-`SIGNED`-with-null-signature test.
5. Optionally best-effort parse the 409 problem+json body for a cleaner `AttestationRefusedException` message (New Finding 4).
6. Consider making `AttestResponse` package-private (New Finding 2) unless Phase 10 tests need it public.
