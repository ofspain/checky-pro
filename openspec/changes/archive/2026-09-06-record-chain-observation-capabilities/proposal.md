## Why

`services/crypto` now contains a working quorum read layer and an EVM chain adapter, verified
against Ethereum mainnet, but `openspec/specs/` is empty. The behaviour those components
guarantee is recorded nowhere except in the code and its tests.

That is the drift OpenSpec exists to prevent, and it is cheapest to correct now, while it is two
capabilities rather than ten. Recording them also gives every later change — watchers, reorg
handling, attestation — an existing spec to modify rather than a blank page.

## What Changes

Nothing in the code. This change records behaviour that already exists and passes tests, so that
the living spec describes the service as built.

- Records how facts are established from disagreeing, failing, and absent providers.
- Records what an EVM observation is: an ERC-20 transfer decoded from a receipt log, with the
  rejection rules that stop a malformed log becoming a plausible wrong payment.

## Non-goals

- **Any code change.** If a spec written here does not match the code, the spec is wrong and gets
  corrected; the code is the reference for this change only, and only because it is already
  tested and reviewed.
- **The watcher layer, persistence, publishing, and attestation.** None exist yet. They are
  separate changes and must be proposed before they are built, not after.
- **Tron.** No Tron adapter exists to record.

## Capabilities

### New Capabilities

- `chain/quorum-reads`: How the service decides that something is true — provider agreement,
  what counts as a vote, and what is retained so a past decision can be defended.
- `chain/evm-observation`: What a single EVM provider reports about a watched transfer, and the
  conditions under which it reports nothing rather than something wrong.

### Modified Capabilities

None. `openspec/specs/` is currently empty.

## Impact

- **No files under `services/` change.** Documentation only.
- Establishes the `chain/*` capability namespace that subsequent changes will modify.
- After archive, `openspec/specs/chain/quorum-reads/` and `openspec/specs/chain/evm-observation/`
  become the living record, and later changes deliver deltas against them.
