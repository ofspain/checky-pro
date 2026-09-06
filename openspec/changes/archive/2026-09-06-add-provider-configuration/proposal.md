## Why

The service boots with a working `QuorumReader` and **zero chain adapters**. `EvmChainAdapter`
exists and reads real mainnet data, but nothing constructs one from configuration, so the quorum
layer can only be handed providers by a test. Every remaining piece of this service — watchers,
observation logging, publishing, attestation — is blocked behind this.

It is also the point where §6.1's "three commercially independent providers" stops being a
sentence in a document and becomes something the deployment either satisfies or does not.

## What Changes

- Providers are declared per chain in configuration: a non-sensitive label plus an endpoint,
  resolved from the environment so no endpoint or key is ever written into a config file (D-010).
- A registry hands the quorum layer the adapters for a chain, so callers name a chain rather than
  assembling providers themselves.
- **Startup fails** when a chain has fewer providers than the quorum threshold requires. A
  deployment that cannot satisfy §6.1 must not accept traffic and later attest on thin evidence.
- Health reporting distinguishes "configured and reachable" from "configured", so an operator can
  see a chain running on the bare minimum before it drops below quorum.

## Non-goals

- **Provider health monitoring and `chain.provider.degraded`.** Continuous health checking, the
  degraded/recovered lifecycle, and its published event are their own change. This one establishes
  what a provider *is* and whether enough exist at startup.
- **WebSocket subscriptions.** Configuration carries an optional subscription endpoint for the
  watcher layer to use later, but nothing subscribes here.
- **Secret management mechanics.** Endpoints arrive as environment variables; how External Secrets
  Operator and Secrets Manager populate them is infrastructure (§8), not this service.
- **Tron.** No Tron adapter exists. The configuration shape is chain-agnostic so one slots in
  without a rework.
- **Rate limiting, retry, and backoff against providers.** Real concerns, but they belong with the
  watcher's call pattern, where the traffic actually is.

## Capabilities

### New Capabilities

- `chain/provider-configuration`: How providers are declared, how a chain's adapters are resolved
  from that declaration, and what the service does when a deployment cannot meet its own quorum
  requirement.

### Modified Capabilities

None. `chain/quorum-reads` and `chain/evm-observation` are unchanged — this change supplies their
inputs rather than altering their behaviour.

## Impact

- **New:** provider properties, an adapter registry, and startup validation in `services/crypto`.
- **Configuration:** new `themistra.crypto.chains.*` properties. A deployment that sets none gets
  a service with no chains, which is a valid state for a service that has not been given work yet
  — but a chain declared with too few providers is a startup failure.
- **Deployment:** three endpoint URLs per chain become a deployment prerequisite. Until real
  provider accounts exist, public endpoints satisfy the shape but not §6.1's independence
  requirement, and should not be used beyond development.
- **No contract or event change.** Nothing published changes.
