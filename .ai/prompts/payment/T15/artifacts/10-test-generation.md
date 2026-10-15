# payment · T15 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test manifest (`CryptoClientTest`, new methods)

| Test | Verifies |
|---|---|
| `shouldSendCorrectBodyAndBearerTokenOnAttest` | AC1/AC5 — `attest` POSTs `receiptDigestSha256`/`chain`/`txHash` and the bearer token correctly |
| `shouldReturnSignedOutcomeWhenAttestResponseIsSigned` | AC1 — `200 SIGNED` → `AttestationResult.Signed` with correct fields |
| `shouldReturnBlockedOutcomeWhenAttestResponseIsBlocked` | AC2 — `200 BLOCKED` → `AttestationResult.Blocked`, not thrown |
| `shouldThrowCryptoServiceUnavailableWhenSignedOutcomeHasMissingFields` | Phase 9 (self-review Finding 1 / Kimi confirmed) — a `SIGNED` outcome with a missing field fails loudly instead of producing a hollow result; mutation-verified this phase (guard removed → test failed red, restored → green) |
| `shouldThrowAttestationRefusedExceptionOnConflict` | AC3 — `409` → `AttestationRefusedException`, message carries the response body |
| `shouldThrowCryptoServiceUnavailableOnAttestBadRequest` | AC4 (Phase 3/4 Finding 4 disposition) — `400` → `CryptoServiceUnavailableException`, not a distinct exception |
| `shouldThrowCryptoServiceUnavailableOnAttestServerError` | AC4 — `5xx` → `CryptoServiceUnavailableException` |
| `shouldThrowCryptoServiceUnavailableOnAttestConnectionFailure` | AC4 — connection failure → `CryptoServiceUnavailableException` |
| `shouldThrowWhenAttestServerExceedsConfiguredReadTimeout` | AC6 — the configured read timeout is respected and bounded (elapsed time asserted `<500ms` against a 200ms-configured timeout and a 1000ms-sleeping server) |
| `shouldThrowCryptoServiceUnavailableOnAttestMalformedResponseBody` | AC4 — a `200` response with no recognizable `outcome` field → `CryptoServiceUnavailableException`, not a silent pass-through |

**New helper:** `startAttestServer(...)`, added alongside the existing `startServer(...)` rather
than generalizing it (Kimi Phase 8 New Finding 1's own first option) — the existing watch-focused
tests above are untouched, zero risk to already-reviewed code for a purely additive change.

## ArchUnit (L3) — already written and verified in Phase 6, re-confirmed here unchanged

`shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` (canary) and
`...ActuallyFailsAgainstAGenuineViolation` (negative proof, via `RogueKmsAndChainClientUser` and
the three stub classes) — both already pass; this phase added no further ArchUnit tests (none
needed — the rule's own positive+negative coverage was complete at Phase 6, since the task
statement's L3 requirement has exactly one shape to prove: the ban holds, and can genuinely
fail).

## Why no additional tests are needed

- **R19 (partial — the client mechanism only):** every realistic response shape from the real,
  built crypto-service (`SIGNED`, `BLOCKED`, `409 REFUSED`, `400`, `5xx`, connection failure,
  timeout, and a malformed/unrecognized body) is covered. The full R19 flow (computing the
  digest, deciding what to do with each outcome) is T16/T17's own scope, not retested here.
- **L3:** one ban, one canary, one negative proof — already complete since Phase 6, no further
  case exists to add.
- **Contract conformance (`shouldConformToPaymentsOpenApiContract`, R31):** out of scope for
  this task — `contracts/api/payments.yaml` doesn't exist yet (task 24); `AttestRequest`/
  `AttestResponse` are confirmed field-for-field against `crypto-internal.yaml` by direct
  comparison (Phase 1/2), not a generated-contract test, consistent with every prior
  `CryptoClient` method's own precedent (`RegisterWatchRequest`/`Response` have no contract test
  either).

## Verification

`mvn -o -pl services/payment -am test-compile`: clean. `mvn -o -pl services/payment -am test
-Dtest=CryptoClientTest`: **25/25 passing** (15 existing + 10 new). Mutation-tested the one new
guard this phase covers (removed the null-field check in `CryptoClient.attest`'s `SIGNED` branch
→ `shouldThrowCryptoServiceUnavailableWhenSignedOutcomeHasMissingFields` failed red; restored →
green). Full suite `mvn -o -pl services/payment -am test`: **309/309 passing** (299 pre-task
baseline + 10 new).
