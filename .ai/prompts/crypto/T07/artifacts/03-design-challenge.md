<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) for crypto · T07. -->

# crypto · T07 · Phase 3 — Design Challenge Findings

**Scope:** Adversarial review of the Phase 2 Task Implementation Brief for `TronAdapter` (TronGrid / java-tron gRPC) before it is frozen.

**Directive:** Do not redesign and do not implement. Surface hidden assumptions, ambiguous rules, missing edge cases, and conflicts with locked decisions / `spec/crypto-service/agents.md`. Each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Issue 1 — One `ProviderEntry.url` is expected to serve both full-node and solidity-node gRPC endpoints

**Severity:** High

**Evidence:** The brief's Dependencies section notes that `ApiWrapperBuilder`'s constructor accepts `grpcEndpoint` and `grpcEndpointSolidity` separately, but `ProviderEntry` has only one `url`. The provisional plan is to pass the same value for both. This is an unvalidated assumption about real Tron provider topology. TronGrid and most java-tron deployments expose full-node and solidity-node on different host/port pairs; a single URL cannot route to both stub types internally.

**Recommended brief amendment:** Mark this as a hard Open Question with a fallback. State that the implementation must validate against a real provider's endpoint conventions before Phase 5 freezes the config. If the same URL fails in Phase 5/6, the brief must escalate to the author because `ProviderProperties` is frozen by T03 and cannot be silently extended.

---

## Issue 2 — Tron Base58Check address validation is deferred despite requirement R16 mandating it

**Severity:** High

**Evidence:** `requirements.md` R16 states: "WHEN validating a Tron address, THEN the system SHALL enforce Base58Check validity." `design.md` L8 says "Address validation is mandatory." The brief explicitly scopes Base58Check validation **out** of `TronAdapter` (mirroring T06's EIP-55 deferral) and assigns it to task 12 (`AddressValidator`). Until task 12 runs, `TronAdapter` may receive invalid addresses and forward them to gRPC or use them in topic filters, producing provider errors rather than clean validation failures.

**Recommended brief amendment:** Clarify that `TronAdapter` assumes all addresses passed to it have already been validated by `AddressValidator` (task 12), and that any `subscribeAddress`/`getTokenInfo` call with an invalid address is a caller bug that may surface as an unchecked transport exception. Add a note that this assumption must be enforced by watcher/attest boundary tests in later tasks, not silently relied upon.

---

## Issue 3 — Native TRX fallback path in `getTx` has no specified trident data source

**Severity:** High

**Evidence:** The Required Tests list includes "native-value fallback when no Transfer log is present" for `getTx`, and AC2 references it. However, the brief only specifies how to retrieve TRC-20 Transfer logs from `TransactionInfo`. It never states which `Transaction` proto field(s) contain the native TRX amount (e.g., `TransferContract.amount`, `TransferAssetContract.amount`) or how `ApiWrapper.getTransactionById`'s response exposes them. Without this, the acceptance test cannot be written unambiguously.

**Recommended brief amendment:** Specify the exact trident/proto path used to read native TRX value when no TRC-20 Transfer log is found, and explicitly scope the fallback to plain TRX transfers only (not TRC-10 or other contract types).

---

## Issue 4 — Conversion from Base58Check watch address to the 32-byte topic filter is unspecified

**Severity:** High

**Evidence:** AC7 requires the block-scan poll to filter by recipient topic. TRC-20 Transfer event topics contain 32-byte zero-padded address values derived from the raw 21-byte Tron address. The brief mentions `org.tron.trident.core.utils.Base58` exists but says it is "not built here." It never states how `subscribeAddress` converts the caller's Base58Check string into the hex topic value passed to the log filter, or whether trident provides a helper for this.

**Recommended brief amendment:** Specify the address-to-topic conversion mechanism (e.g., Base58 decode → raw 21-byte address → `0x` + 24 leading zeros + 40 hex chars of raw address). Include a required test that a known Base58 address produces the expected topic filter.

---

## Issue 5 — `getTokenInfo` contract address encoding is ambiguous

**Severity:** Medium-High

**Evidence:** AC3 says `getTokenInfo` is keyed by `contractAddress`. For Tron, contract identity is commonly expressed as Base58Check (e.g., USDT-TRC20 `TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t`). The brief does not state whether `Trc20Contract` expects Base58Check, hex, or raw bytes, nor whether `TronAdapter.getTokenInfo` normalizes the input. An encoding mismatch will fail acceptance tests.

**Recommended brief amendment:** Pin the expected `contractAddress` encoding for Tron (recommend Base58Check to match user-facing addresses) and specify how `TronAdapter` converts it before constructing `Trc20Contract`.

---

## Issue 6 — Per-block scanning causes RPC amplification and watcher lag

**Severity:** Medium

**Evidence:** The brief says Tron has no range-batched log query, so `subscribeAddress` must call `getTransactionInfoByBlockNum` for every block since the cursor. Tron produces a block roughly every 3 seconds. If the service lags by even a small number of blocks, or if multiple providers are polled independently, the RPC call volume is `providers × blocks_behind × (1 block-info call + per-tx info calls)` every poll interval. This is materially more chatty than Ethereum's single `eth_getLogs` range call.

**Recommended brief amendment:** Add an operational note and a design guard: cap the number of blocks scanned per poll (e.g., catch up at most N blocks per tick) and emit a metric/alert when the cursor falls behind, so the watcher-lag SLO is explicit.

---

## Issue 7 — `ProviderEntry.timeoutSeconds` is not mapped to the gRPC client

**Severity:** Medium

**Evidence:** The brief lists `timeoutSeconds` as consumed from `ProviderEntry` and used for Ethereum's OkHttp timeouts. It never states how the same field is applied to `ApiWrapper`/gRPC calls. If no timeout is configured, the Tron adapter may hang on a stuck gRPC stream with the default gRPC timeout, violating the brief's reliability expectations.

**Recommended brief amendment:** Specify how `timeoutSeconds` is translated into trident/gRPC call deadlines (e.g., `ManagedBuilder` channel settings, per-call `withDeadlineAfter`, or trident-specific timeout API). If trident exposes no timeout configuration, state that explicitly and flag a fallback plan.

---

## Issue 8 — `getTx` partial failure (transaction found but `TransactionInfo` missing) is undefined

**Severity:** Medium

**Evidence:** `getTx` plans to call both `getTransactionById` and `getTransactionInfoById`. The brief states "not found" returns `TxResult(exists=false)` and transport errors throw, but it does not address the case where `getTransactionById` returns a transaction while `getTransactionInfoById` returns null or throws. This is the direct Tron analogue of T06's "mined tx with null receipt" edge case.

**Recommended brief amendment:** Add a rule: if `getTransactionById` returns a mined transaction but `getTransactionInfoById` returns null, treat it as `exists=true` with native-value fallback and zero/unknown log-derived fields, or throw — but pick one and make it testable.

---

## Issue 9 — `ApiWrapper` testability / mockability is assumed but not confirmed in the brief

**Severity:** Medium

**Evidence:** AC6 requires "No unit test makes a real network/gRPC call." The brief says `ApiWrapper` is the injected dependency and that tests should mock it, but it does not confirm whether `ApiWrapper` is a non-final class with mockable methods. If trident's `ApiWrapper` is final or constructs internal channels eagerly, unit tests may need to wrap it in an interface, which changes the constructor design.

**Recommended brief amendment:** Add a brief note confirming `ApiWrapper` is non-final and mockable with Mockito (or that a thin wrapper interface will be introduced if it is not). This prevents a last-minute constructor change in Phase 5.

---

## Issue 10 — No inconsistency guard between current block and solidified block

**Severity:** Medium

**Evidence:** T06's `EthereumAdapter.getFinalityStatus` added a guard throwing when `finalizedBlockNumber > currentBlockNumber` because the two block tags are independent RPC round trips. The brief's Tron design makes the same independent round trips (`getNowBlock()` and `getNowBlockSolidity()`) but does not propose an equivalent guard. A provider returning a stale or corrupt solidity head could yield a logically impossible `FinalityStatus`.

**Recommended brief amendment:** Mirror T06's guard: if `finalizedBlockNumber > currentBlockNumber`, throw an `IllegalStateException` with provider name and block numbers. Add a required test.

---

## Issue 11 — `apiKeySecretName` resolution to empty string is unspecified

**Severity:** Low-Medium

**Evidence:** `ProviderEntry` requires `apiKeySecretName` to be non-blank. The brief states the resolved value is passed to `ApiWrapperBuilder.withApiKey(...)`. It does not say what happens if the secret resolves to an empty string (e.g., local profile or a provider that needs no key). Calling `withApiKey("")` may fail or attach an empty header, depending on trident's behavior.

**Recommended brief amendment:** Specify behavior when the secret resolves to empty: either skip `withApiKey` entirely, fail fast, or treat it as a valid empty key — but make it deterministic and tested.

---

## Issue 12 — TRC-10 vs TRC-20 scope ambiguity

**Severity:** Low

**Evidence:** The brief focuses on TRC-20 (`Trc20Contract`, Transfer events). Tron also has TRC-10 tokens, which do not have contract addresses in the EVM sense. AC3 says `getTokenInfo` is keyed by `contractAddress`, and AC7 says the poll has no contract-address restriction. A TRC-10 transfer could be misinterpreted or silently ignored.

**Recommended brief amendment:** Explicitly state that TRC-10 is out of scope for launch; `TronAdapter` detects and reports only TRC-20 Transfer events and native TRX transfers.

---

## Summary table

| # | Issue | Severity | Recommended brief amendment |
|---|-------|----------|------------------------------|
| 1 | Single `url` for full + solidity gRPC endpoints | High | Flag as Open Question; escalate if validation fails |
| 2 | Base58Check validation deferred despite R16 | High | Document caller-validates assumption |
| 3 | Native TRX fallback source unspecified | High | Specify exact `Transaction` field/path |
| 4 | Base58→32-byte topic conversion unspecified | High | Specify conversion and test it |
| 5 | `getTokenInfo` address encoding ambiguous | Medium-High | Pin Base58Check input + normalization |
| 6 | Per-block scan RPC amplification | Medium | Add catch-up cap and lag metric |
| 7 | `timeoutSeconds` not mapped to gRPC | Medium | Specify gRPC deadline mechanism |
| 8 | Partial `getTx` failure undefined | Medium | Define mined-tx/null-info behavior |
| 9 | `ApiWrapper` mockability assumed | Medium | Confirm non-final or wrapper plan |
| 10 | No current-vs-solidity inconsistency guard | Medium | Mirror T06 guard + test |
| 11 | Empty API-key resolution unspecified | Low-Medium | Define empty-secret behavior |
| 12 | TRC-10 vs TRC-20 scope ambiguous | Low | Explicitly exclude TRC-10 |

(End of design challenge.)
