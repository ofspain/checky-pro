# payment · T15 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1, confirmed by Kimi Phase 8 | `SIGNED` outcome accepts a null `signature` without error | **ACCEPTED** | Real parity gap vs. `registerWatch`'s own `watchId`-null guard. Added an explicit check in `CryptoClient.attest`'s `SIGNED` branch, throwing `CryptoServiceUnavailableException` if `signature`/`kmsKeyId`/`signedAt` is null — before constructing `AttestationResult.Signed`. |
| 2 | Self-review Finding 2, confirmed by Kimi Phase 8 | `AttestationRefusedException`'s 409-body read uses the default-charset `getResponseBodyAsString()` overload | **ACCEPTED** | Cheap, future-proofs against a non-ASCII message. Pinned `e.getResponseBodyAsString(StandardCharsets.UTF_8)`, matching RFC 9457 `problem+json` convention. |
| 3 | Kimi Phase 8 New Finding 1 | `CryptoClientTest`'s `startServer(...)` helper is hardcoded to `/internal/v1/watches`, not reusable for `attest` as-is | **NOTED, no code change this phase** | No test code exists yet for `attest` (explicitly deferred to Phase 10 per Phase 6's own implementation notes). This is accurately a Phase 10 concern — recorded here so Phase 10 doesn't rediscover it, not actioned now since there is nothing to fix without the test code existing. |
| 4 | Kimi Phase 8 New Finding 2 | Make `AttestResponse` package-private since it is never used outside `CryptoClient` | **REJECTED — factually incorrect premise, verified before applying** | Kimi's own premise ("`CryptoClient` is in the same package") is wrong: `CryptoClient` is in `com.themistra.payment.common`, while `AttestResponse` is in `com.themistra.payment.common.crypto` — a genuinely different Java package (Java package-private visibility does not span sub-packages despite the nested-looking name). Applying this change was attempted, confirmed to break compilation (`AttestResponse` became inaccessible from `CryptoClient`), and reverted immediately. `AttestResponse` stays `public`, matching `RegisterWatchResponse`'s own identical, already-established precedent in the same package. |
| 5 | Kimi Phase 8 New Finding 3 | `AttestationResult.Signed`'s record type itself permits null fields at construction, independent of `CryptoClient`'s own guard | **ACCEPTED** | Real defense-in-depth — makes the type self-guarding against any other construction path (a test, a future caller), not just the one call site Finding 1 already fixed. Added a compact constructor with `Objects.requireNonNull` on all three fields. Not applied to `Blocked.reason` — confirmed against `crypto-internal.yaml`'s own schema that `reason` is legitimately optional even when `outcome` is `BLOCKED` (not in the schema's own `required` list), so a null `reason` is a valid, expected shape, not a defect. |
| 6 | Kimi Phase 8 New Finding 4 | Parse the 409 body as structured `ProblemDetail` for a cleaner exception message | **REJECTED — contradicts the already-frozen Phase 4 decision** | Verified directly against `artifacts/04-frozen-task-brief.md`: Phase 4 explicitly decided "best-effort — read as a plain `String`, not a strict `ProblemDetail` parse, since the exact body shape isn't contract-tested per `crypto-internal.yaml`'s own disclosed scope limit." Kimi's own finding re-proposes exactly what was already settled and approved, with no new information in the re-raise. Not applied. |

## Exact changes made

- **`CryptoClient.attest`'s `SIGNED` branch** — added a null-field guard (`signature`/`kmsKeyId`/
  `signedAt`) throwing `CryptoServiceUnavailableException` before constructing
  `AttestationResult.Signed` (row 1).
- **`CryptoClient.attest`'s `409` catch branch** — `e.getResponseBodyAsString()` →
  `e.getResponseBodyAsString(StandardCharsets.UTF_8)` (row 2), new `java.nio.charset.StandardCharsets`
  import.
- **`AttestationResult.Signed`** — new compact constructor with `Objects.requireNonNull` on all
  three fields, `public` (required — a compact constructor on a public record cannot narrow
  visibility; the first compile attempt without `public` failed with exactly this error, caught
  and fixed before this resolution closed) (row 5).
- **`AttestResponse`** — attempted-then-reverted visibility change (row 4); file is unchanged
  from Phase 6.

## Verification

`mvn -o -pl services/payment -am compile`: clean (after fixing the compact-constructor
visibility error caught on the first attempt). `mvn -o -pl services/payment -am test`: **299/299
passing**, unchanged from Phase 6/7/8's own baseline (no new test methods added this phase —
Phase 10's own job).
