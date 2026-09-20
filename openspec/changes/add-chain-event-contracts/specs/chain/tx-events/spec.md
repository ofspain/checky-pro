## Purpose

Defines the published contract for blockchain transaction lifecycle observations — what the
crypto service asserts about a transaction at each stage of confirmation, the quorum and
finality conditions that must hold before it asserts anything, how a chain reorganisation
retracts an assertion that consumers have already acted on, and how a failure of provider
agreement is surfaced rather than silently swallowed.

## ADDED Requirements

### Requirement: Single ordered topic per transaction
The system SHALL publish all transaction lifecycle observations to one topic,
`chain.tx.lifecycle`, discriminated by an `eventType` field whose value is one of
`seen`, `confirmed`, `finalized`, `reorged`, or `held`, partitioned by transaction hash.

#### Scenario: Stage ordering is preserved for one transaction
- **WHEN** the system observes a transaction progress from first sighting through finality
- **THEN** a consumer reading the topic receives `seen`, then `confirmed`, then `finalized`
  for that transaction hash in that order, never out of sequence

#### Scenario: Independent transactions do not block one another
- **WHEN** two transactions on the same chain progress concurrently
- **THEN** ordering is guaranteed per transaction hash and no ordering is implied between them

### Requirement: Chain identification spans EVM and non-EVM chains
The system SHALL identify the originating chain on every event by a namespaced chain
identifier of the form `<namespace>:<reference>`, capable of expressing both EVM chains
(`eip155` namespace) and Tron. A bare numeric identifier SHALL NOT be used.

#### Scenario: Ethereum mainnet observation
- **WHEN** the system emits an event observed on Ethereum mainnet
- **THEN** the event carries `chainId` of `eip155:1`

#### Scenario: Tron observation
- **WHEN** the system emits an event observed on Tron
- **THEN** the event carries the registered Tron namespace identifier, and no field on the
  event assumes an EVM-shaped chain

#### Scenario: A later chain is added
- **WHEN** a chain outside the launch set is added
- **THEN** it is expressible in the same identifier field without a new contract version

### Requirement: Addresses are validated per chain namespace
The system SHALL validate address fields according to the namespace of the emitting chain:
EIP-55 checksum form for `eip155`, Base58 form for Tron. A single address format SHALL NOT
be applied across namespaces.

#### Scenario: EVM address with a broken checksum
- **IF** an `eip155` event carries an address failing EIP-55 checksum validation
- **THEN** the event is invalid and SHALL NOT be published

#### Scenario: Tron address
- **WHEN** a Tron event carries a Base58 address
- **THEN** it validates, and it is not required to satisfy the EVM address form

### Requirement: Quorum precedes every assertion
The system SHALL NOT publish `seen`, `confirmed`, or `finalized` unless at least 2 of 3
independent RPC providers agree on the asserted fact.

#### Scenario: Providers agree
- **WHEN** 2 or more of 3 providers report the same transaction at the same state
- **THEN** the system publishes the corresponding lifecycle event

#### Scenario: A provider is unreachable
- **IF** one provider is unreachable but the remaining two agree
- **THEN** quorum is satisfied and the system publishes the event

#### Scenario: A single provider is never authoritative
- **IF** only one provider reports a fact
- **THEN** the system SHALL NOT treat that answer as an observation

### Requirement: Quorum failure is published, not swallowed
The system SHALL emit `eventType: held` when providers disagree on a fact about a watched
transaction, so that the verification can be held and operators alerted. A disagreement
SHALL NOT be resolved automatically, and SHALL NOT be resolved in the payer's favour.

#### Scenario: Providers report conflicting amounts
- **WHEN** providers disagree on the amount of a watched transaction
- **THEN** the system emits `held` identifying the transaction and the nature of the conflict,
  and emits no `seen`, `confirmed`, or `finalized` for it

#### Scenario: Disagreement later resolves
- **WHEN** providers subsequently reach quorum on a previously held transaction
- **THEN** the system emits the appropriate lifecycle event, and the transaction proceeds
  normally from that point

#### Scenario: Held is never an implicit success
- **IF** a transaction is in `held`
- **THEN** no consumer may infer the transaction is valid, and no receipt may be issued

### Requirement: Transaction seen
The system SHALL emit `eventType: seen` when a transaction matching an active watch first
reaches quorum in a block, before any confirmation threshold is met.

#### Scenario: First sighting of a watched transfer
- **WHEN** a transaction transferring a watched token to a watched address reaches quorum in a block
- **THEN** the system emits `seen` carrying the transaction hash, chain identifier, block number,
  block hash, sender, recipient, token contract address, amount, and decimals

#### Scenario: Transaction not matching any watch
- **IF** a transaction does not match an active watch
- **THEN** the system SHALL emit no event for it

### Requirement: Transaction confirmed
The system SHALL emit `eventType: confirmed` when a previously seen transaction has accumulated
the confirmation depth required by that chain's finality policy but has not yet reached finality.

#### Scenario: Confirmation depth reached
- **WHEN** a seen transaction accumulates the chain's configured confirmation depth
- **THEN** the system emits `confirmed` carrying the current confirmation count

#### Scenario: Confirmed is never emitted before seen
- **IF** the system has not emitted `seen` for a transaction
- **THEN** it SHALL NOT emit `confirmed` for that transaction

### Requirement: Transaction finalized
The system SHALL emit `eventType: finalized` only when the transaction has reached the
irreversibility condition defined by that chain's finality policy, and SHALL NOT emit
`finalized` for a transaction that can still be reorganised out.

#### Scenario: Ethereum finality
- **WHEN** a confirmed Ethereum transaction is included at or below the beacon-chain finalized
  checkpoint
- **THEN** the system emits `finalized`, and this assertion is terminal for that transaction

#### Scenario: Tron finality
- **WHEN** a confirmed Tron transaction reaches a solidified block
- **THEN** the system emits `finalized`

#### Scenario: Finalized is never retracted
- **WHEN** the system has emitted `finalized` for a transaction
- **THEN** it SHALL NOT subsequently emit `reorged` for that same transaction

### Requirement: Reorg retracts a prior assertion
The system SHALL emit `eventType: reorged` when a transaction previously reported as `seen` or
`confirmed` is no longer present in the canonical chain, so that consumers can reverse state
derived from the retracted assertion.

#### Scenario: Seen transaction disappears from the canonical chain
- **WHEN** a transaction previously reported `seen` is absent from the canonical chain at quorum
- **THEN** the system emits `reorged` identifying the transaction hash and the block hash that
  was abandoned

#### Scenario: Transaction reappears in a different block
- **WHEN** a reorged transaction is later observed at quorum in a new canonical block
- **THEN** the system emits a fresh `seen` for it carrying the new block number and block hash,
  and that event is distinguishable from the original `seen` it supersedes

### Requirement: Deterministic event identity
Every event SHALL carry an event key derived deterministically from the observation, so that a
consumer can dedupe at-least-once redelivery without consulting the crypto service, while still
distinguishing a genuine re-observation from a duplicate.

#### Scenario: Duplicate delivery of the same observation
- **WHEN** the same observation is delivered to a consumer more than once
- **THEN** both deliveries carry an identical event key and the consumer discards the repeat

#### Scenario: Re-observation after a reorg is not a duplicate
- **WHEN** a transaction is seen, reorged, and then seen again in a different block
- **THEN** the second `seen` carries an event key distinct from the first, so a consumer
  deduping on that key does not discard it

#### Scenario: Consumer replays the topic from the beginning
- **WHEN** a consumer replays the topic from its earliest offset
- **THEN** each event carries the chain state as observed at the time it was published, and the
  consumer can rebuild its state from the ordered sequence alone

### Requirement: Exact amount representation
The system SHALL express token amounts as the chain's base units in an `amount` field of type
string, accompanied by a `decimals` integer, and SHALL NOT perform decimal conversion, rounding,
or floating-point representation of any amount.

#### Scenario: Six-decimal stablecoin transfer
- **WHEN** the system observes a transfer of 1.5 units of a token with 6 decimals
- **THEN** it emits an `amount` of `1500000` as a string and `decimals` of 6

#### Scenario: Amount exceeding 64-bit range
- **WHEN** an observed amount exceeds the range of a 64-bit integer
- **THEN** the value is still carried exactly, because `amount` is a string

### Requirement: Tokens are identified by contract address
The system SHALL identify the transferred token by its contract address and SHALL NOT identify
a token by symbol or name.

#### Scenario: Transfer of a stablecoin
- **WHEN** the system emits any transfer observation
- **THEN** the event carries the token contract address, and carries no symbol field that a
  consumer could mistake for an identity

### Requirement: No secret material on the wire
Events SHALL carry only publicly observable chain data and SHALL NOT include private keys,
attestation signatures, RPC provider credentials, provider endpoint URLs, or internal
database identifiers.

#### Scenario: Event payload inspected
- **WHEN** any transaction lifecycle event is inspected
- **THEN** it contains no credential, key, signature, or provider endpoint value
