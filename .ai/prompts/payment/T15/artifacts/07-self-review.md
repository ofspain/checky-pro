# payment · T15 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Findings only — no fixes applied here (Phase 9's job).

---

## Finding 1 (Medium) — a `SIGNED` outcome with a null `signature` is accepted without error

- **Issue:** `attest`'s success branch (`CryptoClient.java`, the `SIGNED_OUTCOME.equalsIgnoreCase(...)`
  branch) constructs `new AttestationResult.Signed(response.signature(), response.kmsKeyId(),
  response.signedAt())` with no check that `signature` (or `kmsKeyId`/`signedAt`) is actually
  non-null. `registerWatch` applies exactly this kind of defensive check on its own equivalent
  critical field (`if (response == null || response.watchId() == null) throw
  CryptoServiceUnavailableException`) — `attest` doesn't carry the same diligence over to its own
  analogous case.
- **Evidence:** `CryptoClient.java` — the `SIGNED_OUTCOME` branch, immediately after the outcome
  dispatch; contrast with `registerWatch`'s own `response.watchId() == null` guard a few methods
  above.
- **Impact:** A malformed or buggy crypto-service response (`outcome: "SIGNED"` with a missing
  `signature`) would silently produce a usable-looking `AttestationResult.Signed` with a `null`
  signature, rather than failing loudly at this layer. A future caller (T17's `ReceiptService`)
  could then persist a receipt with no real signature unless it independently re-validates —
  duplicating a check this client is the natural place to make once, not relying on every future
  caller to remember it.
- **Recommendation:** Treat a `SIGNED` outcome with a `null` `signature` the same way
  `registerWatch` treats a `null` `watchId` — throw `CryptoServiceUnavailableException` instead
  of returning a hollow `Signed` result.

---

## Finding 2 (Low) — `AttestationRefusedException`'s message charset is unverified

- **Issue:** `attest`'s `409` branch passes `e.getResponseBodyAsString()` straight into
  `AttestationRefusedException`. Spring's `HttpClientErrorException.getResponseBodyAsString()`
  (no-arg overload) decodes using the response's own declared charset when present, with a
  documented fallback otherwise — this implementation never checked which charset the real
  crypto-service's `problem+json` response actually declares, nor added an explicit
  `getResponseBodyAsString(StandardCharsets.UTF_8)` call to pin it.
- **Evidence:** `CryptoClient.java`, the `HttpClientErrorException.Conflict` catch branch.
- **Impact:** Low in practice — the real crypto-service's own `AttestationRefusedException`
  message is confirmed (Phase 4) to always be a fixed, ASCII-only generic string, so charset
  ambiguity has no realistic consequence today. Still worth a conscious decision rather than an
  implicit default, in case that message ever changes to include non-ASCII content.
- **Recommendation:** Either explicitly pin `getResponseBodyAsString(StandardCharsets.UTF_8)` (RFC
  9457 `problem+json` is UTF-8 by convention) or add a one-line comment explaining why the default
  is accepted as-is. Low priority — defer to Phase 9's judgment on whether this is worth a change
  at all given the confirmed-harmless real-world shape.

---

## Positive confirmations

- `attest`'s catch-order (`HttpClientErrorException.Conflict` before the generic
  `RestClientException`) is correct Java (more specific subtype first) and compiles/runs as
  verified in Phase 6.
- `400`, every `5xx`, connection failures, and read timeouts are all correctly left to fall
  through to the existing `CryptoServiceUnavailableException` catch — matches the frozen brief's
  AC4 exactly, verified by the Phase 6 build (no new exception types introduced for these cases).
- The new L3 ArchUnit rule correctly excludes the three stub classes from its own scan (they live
  outside `com.themistra.payment..` entirely) and the main-source canary passes cleanly while the
  negative-proof test genuinely throws — both already verified empirically in Phase 6, re-checked
  here by re-reading the rule's own `.that().resideInAPackage(ANALYZED_PACKAGE + "..")` scope.
- `AttestationResult` being a sealed interface (not the brief's original nullable-record
  proposal) is correctly implemented — no nullable-field leakage at the call site.
- `attest` reuses the existing `restClient`/`requestInterceptor`/timeout configuration with zero
  new setup code — no thread-safety or configuration-duplication concern.
- No monetary value, no secret, and no PII is logged or embedded in any new exception message.
- The `CryptoClient` class Javadoc's module-placement note (Phase 3 Finding 5) is a code comment
  only — no `spec/` file was touched, correctly honoring the standing guardrail Kimi's own
  suggestion would have violated.
