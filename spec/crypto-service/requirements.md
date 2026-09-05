# 3. Requirements — acceptance criteria (EARS)

Each requirement is independently testable and maps to a named test in [`package.md`](package.md) §8. Tests use scripted fake provider adapters; real RPC providers are never called in tests.

## Multi-provider quorum

- R1. WHEN a verification fact (tx existence, amount, token contract, confirmations, finality status) is needed, THEN the system SHALL fetch it from N independent providers and SHALL treat the fact as true only when at least 2-of-3 agree.
- R2. IF the providers disagree on a fact, THEN the system SHALL mark the fact `HELD`, alert ops, and SHALL NOT emit a downstream event for it.
- R3. IF a fact is `HELD` due to disagreement, THEN the system SHALL NOT auto-resolve it in the payer's (or any party's) favor; resolution is manual.
- R4. WHEN any provider returns a response for a fact, THEN the system SHALL persist that response verbatim to the observation log (Postgres `chain` schema + S3) before the quorum decision is finalized.
- R5. IF a provider is unhealthy, lagging, or repeatedly disagreeing with the quorum, THEN the system SHALL emit a `chain.provider.degraded` event and continue with the remaining providers if quorum is still achievable.

## Per-chain finality (reorg safety)

- R6. WHEN evaluating Ethereum finality, THEN the system SHALL require the transaction's block to be at or below the beacon-chain `finalized` checkpoint, NOT a fixed confirmation count.
- R7. WHEN evaluating Tron finality, THEN the system SHALL require the transaction's block to be solidified (~19 confirmations) per the Tron finality policy object.
- R8. WHEN a watched transaction is first observed with quorum agreement, THEN the system SHALL emit `chain.tx.seen`.
- R9. WHEN a `SEEN` transaction gains confirmations under quorum, THEN the system SHALL emit `chain.tx.confirmed` carrying the confirmation count.
- R10. WHEN a transaction meets its chain's finality policy under quorum, THEN the system SHALL emit `chain.tx.finalized`, and SHALL NOT emit `chain.tx.finalized` before that policy is met.
- R11. IF a previously observed transaction is invalidated by a chain reorg, THEN the system SHALL walk the affected watcher cursor backward and emit `chain.tx.reorged`.
- R12. WHEN any `chain.tx.*` event is emitted, THEN it SHALL carry the deterministic idempotency key `chain:txhash:eventtype` so consumers can dedupe.

## Token & address validation

- R13. WHEN identifying the token of a transfer, THEN the system SHALL identify it by `<chain, contractAddress>` and SHALL NOT rely on a token symbol.
- R14. IF a transfer's contract address is not on the signed, versioned canonical-token allowlist for its chain, THEN the system SHALL classify it as `UNKNOWN_TOKEN` and surface it loudly rather than guessing an identity.
- R15. WHEN validating an EVM (Ethereum) address, THEN the system SHALL enforce EIP-55 checksum validity.
- R16. WHEN validating a Tron address, THEN the system SHALL enforce Base58Check validity.
- R17. IF a payer address closely resembles (matching prefix and/or suffix) a previously seen counterparty for the same watch but differs, THEN the system SHALL flag address poisoning on the observation so it propagates to downstream events and views.

## Watch registration & lifecycle

- R18. WHEN the Payment Service calls `POST /internal/v1/watches` with a valid service token and watch parameters, THEN the system SHALL register a watch, begin watching the address, and return a `watchId` with status `REGISTERED`.
- R19. WHEN the Payment Service calls `DELETE /internal/v1/watches/{watchId}`, THEN the system SHALL stop watching and return `204`.
- R25. WHERE a TypeScript chain sidecar supplies an observation, THEN the system SHALL treat that observation as just another provider answer, subject to the same 2-of-3 quorum, and SHALL NOT grant it quorum authority, signing access, or business state.

## Attestation & key custody

- R20. WHEN `POST /internal/v1/attest` is called with a receipt digest for a transaction that has passed quorum, finality, and screening, THEN the system SHALL request a signature from AWS KMS and return `{ signature, kmsKeyId, signedAt, outcome: "SIGNED" }`; the key material SHALL never leave KMS.
- R21. IF the counterparty address for an attest request is a sanctioned/OFAC hit under screening, THEN the system SHALL return `{ outcome: "BLOCKED", reason }`, place the item in the compliance queue, and SHALL NOT produce a signature.
- R22. The system SHALL invoke `kms:Sign` on the attestation key only from the attest path; no other module or endpoint SHALL be able to reach the signer.
- R23. IF an attest request references a transaction that has not met quorum and finality, THEN the system SHALL refuse to sign and return an error (never a signature).
- R24. WHEN a third party requests the verification public keys, THEN the system SHALL publish them (with `kid`/`kmsKeyId`) at a well-known URL so any Themistra receipt can be independently verified.

## Events, contracts, and boundaries

- R26. WHEN a `chain.tx.seen|confirmed|finalized|reorged` or `chain.provider.degraded` event is emitted, THEN `EventTopics` SHALL route it to the `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized`, `chain.tx.reorged`, and `chain.provider.degraded` topics respectively, via the outbox.
- R27. WHEN the internal watch or attest endpoints are called, THEN the system SHALL require a valid service-to-service JWT bearing the `internal.crypto:write` scope and SHALL reject unauthenticated or under-scoped callers.
- R28. WHERE `contracts/api/crypto-internal.yaml` and `contracts/events/chain/*` are authored, THEN internal responses and emitted events SHALL conform to them.
