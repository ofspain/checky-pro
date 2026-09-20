## Why

`services/payment` drives a reorg-reversible verification state machine
(`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`) entirely from
`chain.tx.*` events that `services/crypto` has not yet defined. `contracts/events/`
currently holds only the auth schema, so the payment team is blocked on the wire
format rather than on our implementation. Publishing the contract first unblocks
them and lets both services be built against a fixed interface.

Defining it before writing adapters also forces the quorum, finality, and reorg
semantics to be settled while they are still cheap to change. Java models and the
TypeScript client are generated from `contracts/` in CI, so this shape is expensive
to revise once consumers exist.

## What Changes

- Add `contracts/events/chain/tx-lifecycle.v1.schema.json` — a single topic carrying
  an `eventType` discriminator over `seen | confirmed | finalized | reorged | held`.
  One topic per transaction guarantees ordering, which the reorg-reversible state
  machine depends on: `finalized` must never be observed before `confirmed`.
- Add `contracts/events/chain/provider-health.v1.schema.json` for
  `chain.provider.degraded` — a separate aggregate with its own partition key and
  no per-transaction ordering requirement.
- Identify chains by a **CAIP-2 style namespaced string**, not a numeric EVM chain ID.
  ARCHITECTURE.md §3.4 sets launch chains as **Tron + Ethereum**, and §6.2 gives Tron
  its own finality rule; a numeric `chainId` cannot express a non-EVM chain.
- Validate addresses per chain namespace: EIP-55 checksum for `eip155`, Base58 for
  `tron` (ARCHITECTURE.md §6.3). The schema enforces this conditionally rather than
  applying one address pattern to both.
- Carry a **deterministic** event key of the form `<chainId>:<txHash>:<eventType>`
  per ARCHITECTURE.md §3.4, so consumers dedupe without coordinating with us.
- Emit `eventType: held` when providers fail to reach 2-of-3 quorum, so that
  `services/payment` can hold the verification and ops can be alerted, rather than
  the disagreement vanishing into silence (ARCHITECTURE.md §6.1).
- Represent token amounts as base units (`amount` as a decimal string) plus
  `decimals`, matching what the chain reports. Consumers convert to `BigDecimal`
  themselves; the crypto service performs no lossy conversion.

Not **BREAKING** — both topics are new, and no consumer is deployed against them yet.
This is the last opportunity to change the shape without a coordinated migration.

## Non-goals

- **Solana and the L2s.** Base, Arbitrum, and Solana are "later" per ARCHITECTURE.md
  §3.4/§6.2. The CAIP-2 identifier accommodates them without a version bump, but their
  finality rules are not specified here.
- **Attestation.** `POST /attest` and the `kms:Sign` path are untouched by this change.
  Receipts are signed at finality by the crypto service on request; nothing on these
  events carries a signature.
- **The canonical token allowlist.** ARCHITECTURE.md §6.3 requires tokens be identified
  by contract address against a signed per-chain allowlist. These events carry the
  contract address; the allowlist itself is a separate change.
- **Address-poisoning detection** (§6.3) and **compliance screening** (§6.6). Both are
  verification-time concerns, not properties of a chain observation.
- **Implementation.** No adapter, watcher, quorum, or persistence code. This change
  produces schemas and their governing requirements only.
- **Topic provisioning, retention, and partition counts.** Infrastructure concerns,
  handled separately in `infra/stacks/`.

## Capabilities

### New Capabilities

- `chain/tx-events`: The published contract for blockchain transaction lifecycle
  observations — what the crypto service asserts about a transaction at each stage,
  what quorum and finality conditions must hold before each event is emitted, how a
  reorg retracts a prior assertion, and how a quorum failure is surfaced.
- `chain/provider-health`: The published contract for RPC provider degradation —
  when the crypto service declares a provider degraded and what consumers may infer
  about the trustworthiness of observations during that window.

### Modified Capabilities

None. `openspec/specs/` is currently empty; this is the first capability recorded.

## Impact

- **New files**: `contracts/events/chain/tx-lifecycle.v1.schema.json`,
  `contracts/events/chain/provider-health.v1.schema.json`.
- **CODEOWNERS**: `contracts/` is owner-reviewed (`@ofspain`). Per
  `contracts/README.md`, changes here trigger CI for every consumer.
- **Downstream**: `services/payment` consumes both topics. The event contract is the
  coupling point between two teams — agreement on this file is the deliverable.
  `held` is a new state payment must map into its verification machine.
- **Possible ARCHITECTURE.md amendment**: §3.4 specifies the dedupe key as
  `chain:txhash:eventtype`. That key is not unique across a reorg-and-reappear cycle
  (see `design.md` — Risks). Resolving it may require amending §3.4.
- **Generated code**: Java models and `libs/ts/api-client` are generated from
  `contracts/` in CI. Adding schemas adds generated types; nothing existing regenerates
  differently.
- **No service code changes.** `services/crypto` remains without source files after
  this change.
