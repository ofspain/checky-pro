## Purpose

How another service tells the crypto service what to watch for on a chain: what makes a watch
valid, what a retried registration does, and how a watch ends so that watching never outlives the
payment it exists for.

## ADDED Requirements

### Requirement: A watch declares what would satisfy it
A watch SHALL identify the chain, the recipient address, the token contract, the expected amount
in base units, and the instant it expires.

#### Scenario: A watch is registered for an invoice
- **WHEN** a caller registers a watch naming a configured chain, a recipient address, a token
  contract, an expected amount, and an expiry in the future
- **THEN** the watch is stored as active and its identifier is returned

#### Scenario: A field is missing
- **IF** any of chain, address, token contract, amount, or expiry is absent
- **THEN** registration is rejected, and no watch is stored

#### Scenario: The amount is not a positive base-unit integer
- **IF** the expected amount is zero, negative, or not an integer
- **THEN** registration is rejected

### Requirement: Registration is idempotent on the caller's reference
A caller SHALL supply its own reference for the watch. Registering again with the same reference
SHALL return the existing watch rather than create a second one.

#### Scenario: The caller retries after a timeout
- **WHEN** a caller registers the same reference twice
- **THEN** both calls return the same watch identifier, and one watch exists

#### Scenario: A retry that contradicts the original
- **IF** a reference is reused with different chain, address, token, or amount
- **THEN** the request is rejected as a conflict rather than silently returning the original or
  overwriting it

#### Scenario: Different references
- **WHEN** two watches are registered with different references and identical terms
- **THEN** both exist, because two invoices may legitimately await the same payment

### Requirement: Watches are registered only for configured chains
A watch naming a chain the service has no providers for SHALL be rejected.

#### Scenario: A watch for an unconfigured chain
- **IF** a caller names a chain that is not configured
- **THEN** registration is rejected, rather than accepting a watch nothing could ever observe

### Requirement: Addresses are validated for their chain's namespace
Recipient and token addresses SHALL be validated against the address form of the watch's chain
namespace before the watch is stored.

#### Scenario: A malformed EVM address
- **IF** a watch on an EVM chain carries an address that is not a valid EVM address
- **THEN** registration is rejected, rather than storing a watch that can never match

#### Scenario: An address from the wrong chain family
- **IF** a watch on an EVM chain carries an address in another chain's form
- **THEN** registration is rejected

#### Scenario: Address casing does not affect matching
- **WHEN** the same address is registered in checksummed and lowercase form under different
  references
- **THEN** both watches match the same on-chain address

### Requirement: Every watch expires
A watch SHALL carry an expiry, and SHALL NOT be registered with one in the past. An expired watch
SHALL NOT be treated as active.

#### Scenario: Expiry in the past
- **IF** a watch is registered with an expiry that has already passed
- **THEN** registration is rejected

#### Scenario: A watch reaches its expiry
- **WHEN** an active watch passes its expiry
- **THEN** it is no longer reported among active watches, so nothing continues paying provider
  calls on an invoice nobody paid

### Requirement: A watch can be cancelled
A caller SHALL be able to cancel a watch it registered, and cancelling an already-cancelled watch
SHALL succeed without error.

#### Scenario: A watch is cancelled
- **WHEN** a caller cancels an active watch
- **THEN** the watch is no longer active

#### Scenario: Cancelling twice
- **WHEN** a caller cancels the same watch twice
- **THEN** the second call succeeds, because a caller retrying a cancellation should not have to
  distinguish the two cases

#### Scenario: Cancelling an unknown watch
- **IF** a caller cancels a watch that does not exist
- **THEN** the request reports not found rather than reporting success

### Requirement: Active watches are retrievable
The system SHALL be able to report the active watches for a chain, so the watcher layer can learn
what to observe without the registering service telling it again.

#### Scenario: Active watches for a chain
- **WHEN** active watches exist for a configured chain
- **THEN** they are retrievable by chain, and expired and cancelled watches are excluded

#### Scenario: A chain with no active watches
- **WHEN** a chain has no active watches
- **THEN** an empty result is returned, which is not an error

### Requirement: Watch state survives restart
Watches SHALL be persisted, so that a restart does not lose what the service was asked to observe.

#### Scenario: The service restarts
- **WHEN** the service restarts after watches were registered
- **THEN** the active watches are still active and still retrievable

### Requirement: The database owns no business logic
The schema SHALL be created and evolved by versioned migrations, and SHALL contain no functions,
procedures, or triggers implementing behaviour.

#### Scenario: The schema is inspected
- **WHEN** the `chain` schema is inspected
- **THEN** it contains tables, constraints, and indexes only

#### Scenario: The application starts against the schema
- **WHEN** the service starts
- **THEN** migrations have been applied and the object mapping is validated against the schema
  rather than generating it
