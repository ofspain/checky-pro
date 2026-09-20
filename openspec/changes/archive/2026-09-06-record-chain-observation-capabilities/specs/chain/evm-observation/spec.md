## Purpose

What a single EVM provider reports about a watched transfer, and the conditions under which it
must report nothing rather than something plausible but wrong.

## ADDED Requirements

### Requirement: A payment is an ERC-20 transfer log
The system SHALL derive an observation from the ERC-20 `Transfer` event in a transaction's
receipt logs, not from the transaction's native value field.

#### Scenario: A stablecoin transfer
- **WHEN** a transaction's receipt carries an ERC-20 `Transfer` log
- **THEN** the observation reports the sender, recipient, token contract, and value decoded from
  that log

#### Scenario: A transaction carrying no transfer log
- **IF** a receipt contains no ERC-20 `Transfer` log
- **THEN** the system reports no observation for it

#### Scenario: A receipt carrying unrelated logs
- **WHEN** a receipt contains logs from other contracts and events
- **THEN** those are not errors; they are ignored and the transfer is still found

### Requirement: Reverted transactions moved no money
The system SHALL report no observation for a transaction whose receipt indicates failure.

#### Scenario: A reverted transaction
- **IF** a receipt reports a failed status
- **THEN** the system reports no observation, so no consumer can advance a payment on it

### Requirement: A malformed log yields nothing, never a wrong answer
Decoding SHALL reject any log it cannot read exactly, rather than produce a partial or guessed
result. Address and amount fields SHALL be rejected when they are the wrong width, non-hexadecimal,
or absent.

#### Scenario: A truncated address field
- **IF** an indexed address field is not a full word
- **THEN** decoding yields nothing, rather than an address assembled from what was present

#### Scenario: A non-hexadecimal field
- **IF** a field contains characters outside hexadecimal
- **THEN** decoding yields nothing

#### Scenario: A value field of unexpected width
- **IF** the value payload is not exactly one word
- **THEN** decoding yields nothing, rather than interpreting a prefix of it

#### Scenario: An empty value payload
- **IF** the value payload is empty
- **THEN** decoding yields nothing, and it is not interpreted as a zero-value transfer

#### Scenario: A genuine zero-value transfer
- **WHEN** the value payload is a full word of zeroes
- **THEN** decoding yields a transfer of zero, distinct from an empty payload

### Requirement: Amounts are exact
The system SHALL carry the value exactly as the chain reports it, in base units, with no
conversion, rounding, or fixed-width numeric representation.

#### Scenario: An amount beyond 64 bits
- **WHEN** a transfer's value exceeds the range of a 64-bit integer
- **THEN** the observation carries the exact value without loss

#### Scenario: Token decimals accompany the amount
- **WHEN** an observation is produced
- **THEN** it carries the token's decimals alongside the base-unit amount, so a consumer can
  interpret it without a second lookup

### Requirement: Addresses are checksummed on output
Every EVM address the system reports SHALL be in EIP-55 checksummed form.

#### Scenario: An observation is produced
- **WHEN** the system reports sender, recipient, or token contract
- **THEN** each is EIP-55 checksummed, not the lowercase hexadecimal the chain returns

### Requirement: Tokens are identified by contract address
The system SHALL identify a token by the contract that emitted the transfer, and SHALL NOT treat
a symbol or name as identity.

#### Scenario: An observation names its token
- **WHEN** the system reports a transfer
- **THEN** it carries the emitting contract's address as the token's identity

### Requirement: Ethereum finality follows the chain's finalized checkpoint
Finality for an Ethereum transaction SHALL be determined by whether its block is at or below the
chain's finalized checkpoint, not by a confirmation count threshold.

#### Scenario: A transaction below the finalized checkpoint
- **WHEN** a transaction's block is at or below the finalized checkpoint
- **THEN** the system reports it as finalized

#### Scenario: A recently mined transaction
- **WHEN** a transaction's block is above the finalized checkpoint
- **THEN** the system reports it as not finalized, however many confirmations it has

#### Scenario: Depth is reported alongside finality
- **WHEN** the system reports finality
- **THEN** it also reports how deep the transaction currently is

### Requirement: A provider that cannot answer says so
When a provider cannot be reached or returns an unusable response, the system SHALL surface that
as a failure distinct from the provider reporting a transaction absent.

#### Scenario: The provider is unreachable
- **IF** a request to the provider fails
- **THEN** the system raises a failure naming the provider, rather than reporting absence

### Requirement: Chain-client details do not escape the adapter
Types and abstractions belonging to the underlying chain client library SHALL NOT appear in the
observations the adapter returns.

#### Scenario: A consumer handles an observation
- **WHEN** any caller consumes an observation
- **THEN** it depends only on the common chain vocabulary, so the client library can be replaced
  without changing that caller

### Requirement: The adapter refuses a chain it cannot speak for
An EVM adapter SHALL be constructible only for a chain in the EVM namespace.

#### Scenario: Constructed for a non-EVM chain
- **IF** an EVM adapter is constructed for a chain outside the EVM namespace
- **THEN** construction fails immediately, rather than producing observations attributed to the
  wrong chain family
