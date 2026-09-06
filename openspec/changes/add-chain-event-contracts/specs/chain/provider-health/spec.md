## Purpose

Defines the published contract for RPC provider degradation — when the crypto service declares
a provider unhealthy, and what consumers may infer about the trustworthiness and completeness
of chain observations while a degradation is in effect.

## ADDED Requirements

### Requirement: Provider degradation is published
The system SHALL publish a `chain.provider.degraded` event when an RPC provider stops meeting
its health criteria, identifying the provider and the chain it serves, so that operators and
consumers can attribute reduced confidence to a known cause.

#### Scenario: Provider becomes unreachable
- **WHEN** a provider fails its health criteria for a configured interval
- **THEN** the system emits `chain.provider.degraded` naming the provider, the chain ID, the
  reason, and the time the degradation was detected

#### Scenario: Provider recovers
- **WHEN** a previously degraded provider resumes meeting its health criteria
- **THEN** the system emits an event marking it recovered, so consumers are not left assuming
  an indefinite degradation

### Requirement: Provider identity is opaque and non-sensitive
The event SHALL identify a provider by a stable opaque label and SHALL NOT disclose endpoint
URLs, API keys, or any other credential.

#### Scenario: Degradation event inspected
- **WHEN** a `chain.provider.degraded` event is inspected
- **THEN** the provider is identified by a label such as `evm-provider-a` and no endpoint or
  credential appears anywhere in the payload

### Requirement: Degradation does not weaken quorum
Publication of a degradation event SHALL NOT relax the 2-of-3 quorum required for transaction
lifecycle assertions.

#### Scenario: One provider degraded, two healthy
- **WHEN** one provider is degraded and the remaining two agree on a fact
- **THEN** quorum is satisfied and transaction lifecycle events continue to be published normally

#### Scenario: Two providers degraded
- **IF** two of three providers are degraded
- **THEN** quorum cannot be reached, and the system SHALL publish no transaction lifecycle
  events for that chain until quorum is restored

### Requirement: Degradation events carry no transaction ordering guarantee
The provider health topic SHALL be independent of the transaction lifecycle topic, and consumers
SHALL NOT infer any ordering between a degradation event and any transaction lifecycle event.

#### Scenario: Consumer correlates the two topics
- **WHEN** a consumer reads both topics
- **THEN** it can attribute a gap in observations to a degradation window by timestamp, but
  no ordering between the two topics is guaranteed
