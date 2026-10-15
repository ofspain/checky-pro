# payment · T15 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Add a `CryptoClient.attest(...)` method for `POST /internal/v1/attest` (design §4c), with
timeouts and error mapping, using the existing service-to-service JWT (`internal.crypto:write`).
Add an ArchUnit rule banning any import of a KMS SDK or chain client anywhere in
`com.themistra.payment` (L3).

## Purpose

Give this task-15 wiring its own real HTTP mechanism before T16/T17 need to call it. `attest` is
the one external call this service ever makes to obtain a receipt signature — L3 exists
precisely because this service must never obtain that signature any other way (no local KMS
client, no chain RPC client). Formalizing the ban as an enforced ArchUnit rule (not just an
unenforced pom.xml convention, as it has been since T01) closes that gap permanently.

## Scope

**In:**
- `CryptoClient.attest(String receiptDigestSha256, String chain, String txHash)` (or equivalent
  signature), calling `POST {attestPath}`.
- New request/response DTOs for the attest call.
- A typed way to distinguish `SIGNED` / `BLOCKED` (both `200`, both legitimate business outcomes)
  from a `409 REFUSED` and from a true service-unavailable failure.
- A new ArchUnit rule banning `org.web3j..`, `org.tron..`, `software.amazon.awssdk.services.kms..`
  imports, with a canary + a genuine-failure proof (rogue fixture), mirroring the two existing
  rules in `ArchitectureTest`.

**Out:**
- Computing the receipt digest (`ReceiptDigest`, T16 — not built yet; `attest` takes the digest
  as a pre-computed `String` parameter).
- Deciding what happens to the payment/invoice on `BLOCKED` or `REFUSED` (R20, T17's own
  `ReceiptService` — out of scope here; T15 only makes the distinction *representable*, not
  *acted on*).
- S3 storage, the ledger, exports, contracts — all later tasks (16–24).
- Any change to `registerWatch`/`unregisterWatch` — both already correct and untouched.

## Business Rules

- **R19** (partial): the system obtains the receipt signature by calling `POST /attest`; it
  never signs itself. This task implements the call mechanism only.

## Locked Decisions

- **L3**: no KMS/chain-client import anywhere in `com.themistra.payment`. Enforced here for the
  first time via a new ArchUnit rule (previously an informal pom.xml-level convention only).

## Dependencies

- `CryptoServiceTokenProvider` (existing — reused as-is).
- `CryptoClientProperties.attestPath()` (existing, already validated, previously unused).
- `contracts/api/crypto-internal.yaml` — `AttestRequest`/`AttestResponse` schemas, confirmed
  field-for-field against the real `services/crypto` (`AttestController`, `AttestRequest`,
  `AttestResponse`, `AttestationService`, `AttestExceptionHandler`).
- ArchUnit `noClasses().should().dependOnClassesThat().resideInAPackage(...)` (same DSL/version
  `crypto-service`'s own `KmsSignerArchitectureTest` already uses in this codebase).

## Inputs

- `receiptDigestSha256` (64 hex chars, a SHA-256 digest — caller-computed, no validation needed
  here since T16 doesn't exist yet and this method trusts its caller, mirroring
  `registerWatch`'s own trust-the-caller stance on `amount`/`expiresAt`).
- `chain` (`"ETHEREUM"`/`"TRON"`, same convention as `registerWatch`).
- `txHash` (opaque string, no format validation — mirrors the real crypto-service's own
  `AttestRequest`, which is also only `@NotBlank` on this field).

## Outputs

A typed result/exception combination distinguishing three cases, resolving Open Question 2
(Phase 1) directly rather than deferring it:

- **`SIGNED`** → return an outcome object carrying `signature`/`kmsKeyId`/`signedAt`.
- **`BLOCKED`** → return the *same* outcome type, carrying `reason` instead (a sealed
  interface/record pair, or one record with nullable fields mirroring the real
  `AttestResponse`'s own shape — exact Java shape is an implementation-phase choice, not fixed
  here). Both are **successful return values**, not exceptions — `BLOCKED` is an expected business
  result (R20 already anticipates it), not a client error.
- **`REFUSED`** (`409`) → throw a **new, distinct** `AttestationRefusedException` (unchecked),
  separate from `CryptoServiceUnavailableException`. **Rationale:** `REFUSED` means "the signing
  gate wasn't satisfied yet" (quorum/finality unmet, or a screening error on the Crypto Service's
  own side) — a fundamentally different condition from "the Crypto Service is unreachable or
  broken." A future caller (T17) needs to tell these apart (e.g. to decide whether retrying later
  makes sense), and retrofitting this distinction after T17 already depends on one exception type
  would be a breaking change. This mirrors the existing precedent of giving `registerWatch`'s
  distinct failure modes their own narrow treatment (a non-`REGISTERED` status and a null
  `watchId` both still map to the single existing `CryptoServiceUnavailableException` today — but
  neither of those represents a *recorded, meaningful alternative business outcome* the way
  `REFUSED` does; `BLOCKED`/`REFUSED` are each one of a fixed, named enumeration the real
  `crypto-service` already round-trips through its own `AttestOutcome`).
- **Network failure / timeout / malformed response** → existing `CryptoServiceUnavailableException`,
  unchanged.

## State Changes

None. `CryptoClient` is stateless; this task adds no persistence, no entity, no migration.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestRequest.java` (new
  DTO, mirrors `RegisterWatchRequest`'s own package).
- `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestResponse.java` (new
  DTO/outcome shape).
- `services/payment/src/main/java/com/themistra/payment/common/AttestationRefusedException.java`
  (new, unchecked, mirrors `CryptoServiceUnavailableException`'s own package/visibility).
- A rogue ArchUnit fixture class proving the new L3 rule can fail against a genuine violation
  (package TBD in Phase 5 — likely `archtestfixtures`, mirroring `RogueFloatingPointMoneyUser`/
  `RogueUnmappedEntity`'s own placement).

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java` — add
  `attest(...)`.
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — add the new L3
  rule + its paired canary/negative-proof tests.
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java` — add
  `attest(...)` coverage.

## Files NOT to Modify

- `CryptoClientProperties.java` (no new config field needed — `attestPath` already exists).
- `registerWatch`/`unregisterWatch` and their existing tests.
- Any file outside `services/payment` (this task has no cross-service footprint).

## Module placement decision (resolving Phase 1 Open Question 1)

**Decision: keep `CryptoClient` in `common`, do not relocate to a new `attestation` package.**
`package.md` §4 and `design.md`'s own module-layout diagram both describe `attestation` as
owning this client, but neither document's own text was written with the knowledge that
`registerWatch`/`unregisterWatch` would need to be called from `invoice` (`InvoiceController`,
T06) and `consumer`/the expiry sweep (T08/T11) *before* any attestation flow exists — both
already-shipped, already-tested, already-reviewed call sites. `agents.md`'s own platform rule
("shared plumbing lives only in `common`") is the more specific, durable rule once a class has
genuine callers in three different feature modules (`invoice`, `consumer`, and the future
`receipt`/`attestation` flow for T16-19); relocating now would force `invoice` and `consumer` to
depend on a new `attestation` module for no behavioral gain, and would mean re-reviewing two
already-closed tasks' own call sites for a purely organizational change outside T15's own scope
(`tasks.md` task 15 says nothing about moving existing code). This is the same category of
disclosed, reasoned deviation as T06's own `CryptoServiceUnavailableException` placement
correction (`invoice` → `common`, because `common` must never depend on a feature module). Not
escalated as a blocker — flagged here for Phase 3's design challenge to stress-test, per
established precedent (e.g. T09's `HELD`-edge proposal) of proposing a reasoned call at Phase 2
rather than defaulting to silence or an unnecessary `AskUserQuestion` for an organizational,
non-product decision.

## Acceptance Criteria

- **AC1 (R19 partial).** `CryptoClient.attest(...)` POSTs to `{attestPath}` with the bearer token
  attached and the three required fields in the body; a `200 SIGNED` response is parsed into an
  outcome carrying `signature`/`kmsKeyId`/`signedAt`.
- **AC2 (R19 partial).** A `200 BLOCKED` response is parsed into the same outcome type, carrying
  `reason`, and is **not** thrown as an exception.
- **AC3 (new, L3 context).** A `409` response throws `AttestationRefusedException`, distinct from
  `CryptoServiceUnavailableException`.
- **AC4.** A `5xx`, a connection failure, a read-timeout, or a malformed/unparseable response
  throws the existing `CryptoServiceUnavailableException`, unchanged from today's
  `registerWatch`/`unregisterWatch` behavior.
- **AC5 (L3).** `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` passes on current
  code, and a paired `...ActuallyFailsAgainstAGenuineViolation` test proves the rule genuinely
  fails against a rogue fixture that imports `org.web3j..`/`org.tron..`/
  `software.amazon.awssdk.services.kms..`.
- **AC6.** The `attest` call respects the already-configured `connectTimeoutMs`/`readTimeoutMs` —
  no new config.

## Required Tests

Following `CryptoClientTest`'s established real-local-`HttpServer` pattern (not
`MockRestServiceServer` — the documented interceptor/builder gotcha applies identically here):

1. `attest` sends the correct body (`receiptDigestSha256`/`chain`/`txHash`) and bearer token.
2. A `200 SIGNED` body parses into the signed outcome with all three fields correct.
3. A `200 BLOCKED` body parses into the blocked outcome with `reason` correct, and does not throw.
4. A `409` body throws `AttestationRefusedException` (not `CryptoServiceUnavailableException`).
5. A `5xx` throws `CryptoServiceUnavailableException` (existing behavior, re-proven for the new
   method).
6. A connection failure throws `CryptoServiceUnavailableException` (mirrors
   `shouldThrowOnConnectionFailure`'s own existing pattern for `registerWatch`).
7. Exceeding the configured read timeout throws `CryptoServiceUnavailableException` within a
   bounded time (mirrors `shouldThrowWhenServerExceedsConfiguredReadTimeout` exactly).
8. ArchUnit: `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` (canary) +
   `...ActuallyFailsAgainstAGenuineViolation` (rogue fixture).

## Constraints

- **Transaction:** none — `CryptoClient` has never participated in a transaction and does not
  start here (mirrors the established "never call this from inside `InvoiceService.create`'s own
  `@Transactional`" rule, though `attest` has no transactional caller yet at all — T17 will decide
  its own transactional shape).
- **Thread-safety:** `CryptoClient` is already a stateless `@Component`; `attest` adds no mutable
  state.
- **Null handling:** none of the three input parameters may be `null` from a well-formed caller;
  no explicit null-guard is added, mirroring `registerWatch`'s own identical stance (trusts the
  caller, exactly as it has since T06).
- **Module boundaries:** `common` package placement is the Phase 2 decision above; no cross-module
  entity import is introduced either way.
- **Security:** reuses the existing shared `requestInterceptor` (same `internal.crypto:write`
  bearer token) — no new security code.
- **Wire shape:** no monetary value is carried by `AttestRequest`/`AttestResponse` (L6 does not
  apply here — `receiptDigestSha256` is a hex string, not an amount).

## Open Questions

No blockers. Both questions raised in Phase 1 are resolved directly above (module placement) or
converted into a concrete design decision in Outputs (the `AttestationRefusedException` split) —
both flagged clearly for Phase 3's adversarial design challenge to stress-test before Phase 4
freezes them.
