## 1. Resolve the blocking design questions

- [ ] 1.1 Settle the dedupe key. ARCHITECTURE.md §3.4 specifies `chain:txhash:eventtype`, which
      collides across a `seen → reorged → seen` cycle (see design.md — Risks). Agree the
      block-hash-scoped form, then verify by writing the three-event sequence out by hand and
      confirming all three keys differ.
- [ ] 1.2 Amend ARCHITECTURE.md §3.4 to match whatever 1.1 decides. Verify the amended text and
      the schema agree, so the next reader is not left with two conflicting sources of truth.
- [ ] 1.3 Look up the registered CAIP-2 namespace and reference for Tron. Verify against the
      CAIP-2 registry rather than inventing a value.

## 2. Transaction lifecycle schema

- [ ] 2.1 Create `contracts/events/chain/tx-lifecycle.v1.schema.json` with the draft 2020-12
      `$schema`, an `$id` of `https://checky.pro/contracts/events/chain/tx-lifecycle.v1.schema.json`,
      and a `description` naming the publisher and the partition key, matching the structure of
      `contracts/events/auth/user-lifecycle.v1.schema.json`. Verify by diffing the two files'
      top-level keys — they should agree on everything but content.
- [ ] 2.2 Define the base fields required on every event: `eventKey` (the form settled in 1.1),
      `eventType` (enum `seen|confirmed|finalized|reorged|held`), `chainId` (namespaced string),
      `txHash`, `blockNumber`, `blockHash`, `occurredAt` (date-time). Set
      `additionalProperties: false`. Verify a payload carrying an unknown extra property fails.
- [ ] 2.3 Define the transfer fields: `fromAddress`, `toAddress`, `tokenAddress`, `amount`
      (string, digits only) and `decimals` (integer). Verify that `amount` as a JSON number is
      rejected and as a quoted string is accepted. Confirm no `symbol` field exists (§6.3).
- [ ] 2.4 Add stage-conditional requirements via `allOf`/`if`/`then`: `confirmed` requires
      `confirmationCount`; `reorged` requires `abandonedBlockHash`; `held` requires a conflict
      reason. Verify a `confirmed` without `confirmationCount` fails and a `seen` without it passes.
- [ ] 2.5 Add namespace-conditional address patterns: `eip155` chains require EVM hex form,
      Tron requires Base58 (§6.3). Verify a Tron-form address on an `eip155:1` event is rejected,
      and that each namespace accepts its own form.
- [ ] 2.6 Constrain `chainId` to the launch set plus a pattern permitting future namespaces.
      Verify `eip155:1` and the Tron identifier both validate, and that a bare numeric `1` fails.

## 3. Provider health schema

- [ ] 3.1 Create `contracts/events/chain/provider-health.v1.schema.json` following the same
      conventions, with `providerLabel`, `chainId`, `reason`, `degraded` (boolean), and
      `occurredAt`. Verify it validates against the JSON Schema meta-schema.
- [ ] 3.2 Resolve the open question in design.md on how recovery is represented, then encode the
      chosen form. Verify both a degradation and a recovery payload validate.
- [ ] 3.3 Assert no endpoint or credential field exists anywhere in either schema. Verify by
      grepping both files for `url`, `endpoint`, `key`, `token`, and `secret`.

## 4. Contract test fixtures

- [ ] 4.1 Add valid example payloads for each of the five `eventType` values on both launch
      chains, and for both provider health cases. Verify every fixture validates.
- [ ] 4.2 Add a fixture sequence for the reorg cycle — `seen` on block A, `reorged`, `seen` on
      block B. Verify all three validate and that their event keys are pairwise distinct, which
      is the regression test for 1.1.
- [ ] 4.3 Add invalid fixtures covering each rejection the specs call for: numeric amount,
      unknown property, `confirmed` without a confirmation count, `held` without a reason,
      mismatched address form, bare numeric chain ID. Verify each fails for the expected reason,
      not incidentally.
- [ ] 4.4 Wire fixture validation into CI so a schema edit that breaks an example fails the
      build. Verify by deliberately breaking one fixture and confirming CI goes red.

## 5. Generation and consumer sign-off

- [ ] 5.1 Run the `contracts/` generation pipeline and inspect the emitted Java model. Verify
      `amount` generates as `String` (not a numeric type) and that the `if`/`then` conditionals
      did not flatten every stage-specific field into an untyped optional.
- [ ] 5.2 Inspect the generated TypeScript in `libs/ts/api-client`. Verify `eventType` generates
      as a discriminated union rather than a bare string.
- [ ] 5.3 Walk the schema through with whoever owns `services/payment` and confirm it supports
      the `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED` transitions, the
      reorg-reversal path, and the new `held` state. Verify by their explicit agreement recorded
      on the PR — after their first deployment against this, changes become a coordinated migration.
- [ ] 5.4 Confirm SECURITY-THREAT-MODEL.md needs no update for this change, or PR the update.
      ARCHITECTURE.md §6.7 requires every feature to do one or the other. Verify the threat model
      still covers threat 1 (attacker controls one RPC provider) given the `held` semantics.
- [ ] 5.5 Open the PR for CODEOWNERS review on `contracts/`. Verify CI is green for every
      consumer the contract change triggers.
