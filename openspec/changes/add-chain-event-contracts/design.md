## Context

See `proposal.md` — Why. Constraints that shape the approach:

- `contracts/README.md` makes this directory the single source of truth: Java models and
  `libs/ts/api-client` are **generated** from it in CI and never hand-written. Services never
  depend on each other's source; they meet here.
- ARCHITECTURE.md §3.4 sets launch chains as **Tron + Ethereum**, names the adapter interface
  (`getTx`, `getTokenInfo`, `subscribeAddress`, `getFinalityStatus`), and specifies a
  deterministic dedupe key. §6.1 sets 2-of-3 quorum over commercially independent providers.
  §6.2 gives each chain its own finality rule. §6.3 requires tokens be identified by contract
  address and addresses validated per chain family. This change transcribes those decisions
  into a wire contract; it does not reopen them.
- The only existing precedent is `contracts/events/auth/user-lifecycle.v1.schema.json`:
  JSON Schema draft 2020-12, `$id` under `https://checky.pro/contracts/events/...`,
  `additionalProperties: false`, a `description` naming the publisher and the partition key,
  and one file covering several event types.
- Neither `services/crypto` nor `services/payment` has source files yet. Nothing constrains
  this contract from below or above. This is the only moment where the shape is free.
- Requirements are in `specs/chain/tx-events/spec.md` and `specs/chain/provider-health/spec.md`.

## Goals / Non-Goals

**Goals:**

- Two schema files that CI can generate from without further interpretation.
- A shape that accommodates Base, Arbitrum, and Solana without a breaking version bump.
- Enough constraint in the schema itself (enums, patterns, conditional requirements) that a
  malformed event fails at the contract boundary rather than inside a consumer.

**Non-Goals:**

- Kafka topic configuration, partition counts, retention. Those live in `infra/stacks/`.
- Avro. The existing precedent is JSON Schema; introducing a second format for one domain
  would fragment the generation pipeline for no stated benefit.
- A shared envelope refactor across auth and chain. Worth doing eventually, but it would
  modify a published auth contract and belongs in its own change.

## Decisions

**One topic with an `eventType` discriminator, not five topics.**
The verification state machine in `services/payment` is reorg-reversible, so it must never see
`finalized` before `confirmed`, or a `reorged` for a transaction it has not yet seen. Kafka
guarantees ordering only within a partition of one topic. Separate topics would push
reconciliation of out-of-order arrivals onto every consumer. Partitioning one topic by
transaction hash gives the ordering guarantee for free. Alternative considered: one topic per
event type with consumer-side sequencing on block number — rejected because it makes correctness
of the money path depend on each consumer implementing reconciliation correctly.

**CAIP-2 style namespaced chain identifier, not a numeric `chainId`.**
ARCHITECTURE.md §3.4 launches on Tron and Ethereum. Tron has no EVM chain ID, so a numeric
field cannot express half the launch set. A `<namespace>:<reference>` string covers `eip155:1`
today, Tron alongside it, and Base, Arbitrum, and Solana later without a version bump.
Alternative considered: numeric `chainId` with a separate `chainFamily` enum — rejected as two
fields encoding one fact, with the failure mode that they can disagree.

**`held` as a lifecycle event type, not silence.**
§6.1 requires that quorum disagreement produce a `HELD` verification with ops alerted, never
auto-resolved in the user's favour. A contract that simply omits an event on disagreement gives
`services/payment` no way to distinguish "providers disagree" from "nothing has happened yet" —
the two states demand opposite responses. Putting `held` on the transaction topic keeps it in
the per-transaction ordering guarantee, which matters because `held` must be seen relative to
the stages around it. Alternative considered: a separate disagreement topic — rejected because
it loses ordering against the very events it qualifies.

**`amount` as a base-unit string plus `decimals`, not a decimal number.**
JSON numbers are IEEE 754 doubles in most parsers; a `uint256` token amount does not survive
that. A string preserves the chain's integer truth exactly and lets `services/payment` construct
a `BigDecimal` on its own terms. Alternative considered: a pre-converted decimal string like
`1.50` — rejected because it moves the decimals lookup into the crypto service, where a wrong
answer silently corrupts a money value rather than failing loudly.

**Conditional required fields and address patterns via `allOf`/`if`/`then`.**
Two conditions are encoded this way. First, stage-specific fields: `confirmed` requires
`confirmationCount`, `reorged` requires the abandoned block hash, `held` requires a conflict
reason. Second, per-namespace address validation: `eip155` events require EVM hex addresses,
Tron events require Base58 (§6.3). Making these fields merely optional would let a `confirmed`
with no confirmation count, or a Tron address on an Ethereum event, pass validation.
Alternative considered: one flat schema with everything optional — rejected because it validates
events that are meaningless.

## Risks / Trade-offs

- **§3.4's dedupe key is not unique across a reorg cycle** → ARCHITECTURE.md specifies
  `chain:txhash:eventtype`. But the legitimate sequence `seen(block A) → reorged → seen(block B)`
  produces two distinct observations sharing the key `chain:txhash:seen`. A consumer deduping
  strictly on that key discards the second `seen`, and the payment state machine never
  re-enters `SEEN` — a silent stall on exactly the path the reorg machinery exists to handle.
  Mitigation: include the block hash in the key for block-scoped event types. This deviates
  from §3.4 as literally written and should be settled with an ARCHITECTURE.md amendment
  rather than a quiet divergence in the schema. **This is the item most worth review attention.**
- **Single topic couples throughput of all chains and stages** → Partitioning by transaction
  hash means parallelism scales with partition count, not topic count. Revisit only if a
  measured bottleneck appears; splitting later is a breaking change.
- **`if`/`then` conditionals are harder to generate clean Java from** → Some generators flatten
  conditionals into all-optional fields, discarding exactly the constraint we added them for.
  Task 4 verifies generation output before this is committed rather than discovering it in a
  consumer's CI.
- **No shared event envelope** → `eventKey` and `occurredAt` are defined per schema here and
  will drift from auth's conventions over time. Mitigated by matching auth's `occurredAt`
  naming and `date-time` format exactly, so a later envelope extraction is mechanical.
- **`held` adds a state payment must handle** → It is a new obligation on a team that has not
  written its state machine yet, which is the cheapest possible moment to impose it. The
  alternative — discovering during an incident that disagreements were invisible — is worse.

## Migration Plan

Additive only — both topics are new and no consumer is deployed. No rollback beyond reverting
the commit. The meaningful gates are CODEOWNERS review on `contracts/`, confirmation from
whoever owns `services/payment` that the shape supports their state machine including `held`
and the reorg-reversal path, and a decision on the dedupe key question above.

## Open Questions

- The exact registered CAIP-2 namespace and reference for Tron. It must be taken from the
  CAIP-2 registry rather than invented, but it does not change the field's shape, the specs,
  or the task breakdown — only the value written into the enum and the fixtures.
- Whether `chain.provider.degraded` recovery is a distinct `eventType` value or the same event
  with a `degraded: false` flag. Both satisfy the spec; the choice is cosmetic.
