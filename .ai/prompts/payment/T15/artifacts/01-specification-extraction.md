# payment · T15 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R19.** WHEN issuing a receipt, THEN the system SHALL compute the canonical receipt digest and
  obtain its signature by calling the Crypto Service `POST /attest` endpoint; the system SHALL
  NOT perform signing itself. *(T15's own scope is the mechanism — the `CryptoClient.attest(...)`
  method itself; the full "compute digest, call attest, honour the outcome" flow is T16/T17's
  job. T15 partially satisfies R19 — the HTTP call exists and is correct — but does not complete
  it.)*
- **R5 / R7 / R7a** (context only, not re-scoped here): already satisfied by the existing
  `registerWatch`/`unregisterWatch` methods (T06/T08). T15 does not change their behavior.

## Locked Decisions

- **L3.** This service never touches a blockchain or KMS. All chain facts arrive as Kafka events;
  the receipt signature is obtained by calling the Crypto Service `POST /attest`. The service
  holds no RPC endpoints, no chain client, and no signing key. An ArchUnit rule bans importing
  web3j, chain SDKs, and any AWS KMS signing client. **This task is explicitly named in
  `tasks.md` as the one that adds this ArchUnit ban** — it does not exist yet anywhere in this
  codebase.
- **L12** (context): no feature module imports another feature module's entity class — entity-only,
  does not restrict a plain client class like `CryptoClient`. Not violated either way by this
  task regardless of how Open Question 1 (package placement) resolves.

## Files involved

**Existing, to extend:**
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java` — add the
  `attest(...)` method (pending Open Question 1 on whether this class stays in `common` or moves).
- `services/payment/src/main/java/com/themistra/payment/common/config/CryptoClientProperties.java`
  — `attestPath` already exists, unused until now; no new field expected.
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — add the new L3
  rule alongside the two existing ones, following its established plain-`ArchRule`-field + `@Test`
  canary shape (never `@ArchTest`, due to this repo's own Surefire bug).
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java` — add
  `attest(...)` test coverage using the established local `HttpServer` pattern.

**New files expected:**
- A request DTO for `POST /attest` (`receiptDigestSha256`, `chain`, `txHash` — per
  `contracts/api/crypto-internal.yaml`'s `AttestRequest` schema).
- A response DTO (or a sealed/outcome-carrying shape) for the three real outcomes: `200 SIGNED`
  (`signature`, `kmsKeyId`, `signedAt`), `200 BLOCKED` (`reason`), and `409 REFUSED` (no body
  fields pinned in the contract — it's a generic `problem+json`). Exact shape is a Phase 2 design
  decision (Open Question 2).
- Possibly a new exception type distinguishing "attest was refused because the gate wasn't
  satisfied" (409) from the existing `CryptoServiceUnavailableException` (network/5xx/malformed
  response) — Open Question 2.
- A new ArchUnit rogue-fixture class that genuinely imports one of the banned SDK packages, to
  prove the new L3 rule can actually fail (mirroring every existing rule's own negative-proof
  fixture).

## Dependencies

- `CryptoServiceTokenProvider` — already supplies the bearer token for every `CryptoClient` call;
  the new `attest` method reuses the exact same `requestInterceptor`, no new auth wiring needed.
- `CryptoClientProperties.attestPath()` — already exists and validated, just unused.
- `contracts/api/crypto-internal.yaml` — `POST /internal/v1/attest`, schemas `AttestRequest`/
  `AttestResponse` — confirmed field-for-field against the real, built `services/crypto`
  (`AttestController`, `AttestRequest`, `AttestResponse`, `AttestationService`,
  `AttestExceptionHandler`). The contract itself is explicitly "success responses only... error
  responses... out of this contract's scope" — the `409` shape is a generic
  `common.ApiExceptionHandler`-style `problem+json`, not separately schema'd.
- ArchUnit (`com.tngtech.archunit`) — same version/DSL already used by `ArchitectureTest`'s
  existing two rules; `noClasses().should().dependOnClassesThat().resideInAPackage(...)` is the
  established syntax for an import-level ban (confirmed against `crypto-service`'s own
  `KmsSignerArchitectureTest`, which bans `software.amazon.awssdk.services.kms..` the identical
  way — payment's own rule is simpler, a blanket ban across the whole service with no allowlist,
  since unlike crypto-service, payment-service has zero legitimate reason to ever import any of
  these packages).

## Acceptance Criteria

- **AC1 (R19, partial).** `CryptoClient` has a method that calls `POST {attestPath}` with the
  JWT bearer token attached, sending `receiptDigestSha256`/`chain`/`txHash`, and returns a typed
  result distinguishing `SIGNED` (with `signature`/`kmsKeyId`/`signedAt`) from `BLOCKED` (with
  `reason`) — both are successful `200` business outcomes, not errors.
- **AC2 (L3).** A new ArchUnit rule, `shouldBanKmsAndChainClientImports` (derived name — no
  name is pre-mapped in `package.md` §8), fails the build if any class anywhere in
  `com.themistra.payment` imports `org.web3j..`, `org.tron..`, or
  `software.amazon.awssdk.services.kms..`. The rule must be proven to actually fail against a
  genuine violation (a rogue fixture), not just proven to pass on already-clean code — mirroring
  every existing rule in this file.
- **AC3 (error mapping).** A `409`/REFUSED response, a true network failure, a timeout, and a
  malformed/unexpected response body are all distinguishable from each other at the call site
  (exact exception/return-type shape is a Phase 2 design decision) — not silently collapsed into
  one generic failure the way `registerWatch`'s business-validation failures currently are.
- **AC4 (timeouts).** The `attest` call respects the same configured connect/read timeouts as
  `registerWatch`/`unregisterWatch` (`CryptoClientProperties.connectTimeoutMs()`/
  `readTimeoutMs()`) — no new timeout configuration needed, reuse what exists.
- **AC5 (auth).** The `attest` call sends the same bearer token (scope `internal.crypto:write`)
  as every other `CryptoClient` call, via the existing shared interceptor — no new auth code.

## Tests required

No test is pre-mapped to T15 in `package.md` §8 — `shouldRequestKmsSignatureViaCryptoAttestEndpoint`
(R19) is most naturally T17's own named test (it tests the full `ReceiptService` flow, not the
client method in isolation), mirroring how T06 only partially satisfied R1/R2 and T15 may only
partially satisfy R19. Derived tests for this task, following the established `CryptoClientTest`
pattern (real local `HttpServer`, no Spring context):

- A test proving `attest(...)` sends the correct request body/path/bearer token (mirrors
  `shouldSendBearerTokenFromProvider`/`shouldSendExpectedAmountAsDecimalStringNotJsonNumber`'s own
  shape for `registerWatch`).
- A test proving a `200 SIGNED` response is parsed into the signed outcome correctly.
- A test proving a `200 BLOCKED` response is parsed into the blocked outcome correctly (not
  thrown as an error — `BLOCKED` is a legitimate business result).
- A test proving a `409` response is distinguishable from a `5xx`/connection-failure/timeout
  (exact exception type pending Phase 2).
- A test proving the configured read timeout is respected (mirrors
  `shouldThrowWhenServerExceedsConfiguredReadTimeout`'s own existing pattern exactly).
- `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` (canary, passes on clean code)
  and a corresponding `...ActuallyFailsAgainstAGenuineViolation` test (mirrors the two existing
  ArchUnit rules' own paired-test shape exactly) — the new named test for AC2/L3.

## Open Questions

Both carried forward from Phase 0, confirmed still genuinely open after checking `package.md`
§8/§11 directly (neither is pre-answered there):

1. **Module placement.** `package.md` §4 ("`attestation` module: the outbound REST client to
   the Crypto Service...") **and** `design.md`'s own §4c module-layout diagram **both** place
   `CryptoClient` under a new `attestation` package — not `common`, where it has lived since T06.
   This is a two-document-confirmed expectation, not a single stray diagram line. No earlier
   task's own artifacts revisited this. Needs a Phase 2/4 recommendation: relocate (with its two
   existing callers in `invoice`/`consumer` now depending on a new module) or ratify the
   already-shipped `common` placement and treat the spec's module diagram as describing DTO
   ownership, not a literal requirement. Not a `package.md` §11-listed blocker — newly surfaced.
2. **`409 REFUSED` handling shape.** `requirements.md` names only `BLOCKED` (R20) as a
   receipt-blocking business outcome; `REFUSED` (the real crypto-service's own third outcome,
   confirmed via direct source read) is unnamed anywhere in the spec. T15's own client-method
   design needs to decide now how callers will distinguish it from a true
   `CryptoServiceUnavailableException`-style failure, since this is a method-signature/exception-
   hierarchy decision that would be a breaking change to retrofit once T17 depends on it. Not a
   `package.md` §11-listed blocker — newly surfaced.

Neither question blocks starting Phase 2 (Task Implementation Brief) — both are design-level
choices this task itself must make and record, following the same pattern as T09's `HELD`-edge
decision and T12/T13's invoiceId-vs-watchId decisions (resolved within the task's own Phase 2–4,
not escalated to the product owner as a `package.md` §11 entry, unless Phase 4's human-approval
gate judges otherwise).
