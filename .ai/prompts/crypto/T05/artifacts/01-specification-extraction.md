# crypto · T05 · Phase 1 — Specification Extraction

## Business Rules

**No numbered requirement (R1–R28) is independently testable by this task's own deliverable.** T05
produces an interface, an enum, and a scripted test double — pure shape/plumbing that later tasks
implement business logic against. This mirrors T01's own precedent (a "skeleton" task with
self-referential acceptance criteria, no `R`-mapped named test). The requirements that *reference* the
shapes this task defines, for context only — none satisfiable or testable here:
- R1 (fetch a fact from N providers, 2-of-3 agreement) — the *reason* `ChainAdapter` exists as an
  interface at all (fan-out across instances), implemented by the quorum module (task 9).
- R6/R7 (Ethereum/Tron finality evaluation) — consumes whatever `FinalityStatus` this task defines,
  implemented by the finality module (task 14).
- R13 (token identity by contract address, never symbol) — constrains `TokenInfo`'s shape (see Locked
  Decisions), implemented/validated by the token module (task 11).
- R15/R16 (EIP-55/Base58Check address validation) — not this task's concern at all (address
  validation is a boundary concern, task 12), mentioned only because addresses flow through
  `ChainAdapter`'s method signatures.

## Locked Decisions

Derived directly from `design.md` §4a (task header states none were cited inline):

- **L4. Finality is a per-chain policy object, not a global constant.** Constrains `FinalityStatus`'s
  shape: it must carry enough raw per-chain state (e.g. block number, finalized-checkpoint/solidified
  status) for a *separate* `FinalityPolicy` (task 14) to evaluate against — it must NOT itself embed a
  precomputed `isFinal` boolean or a hardcoded confirmation-count field, which would bake the policy
  decision into the adapter layer instead of leaving it to the policy object.
- **L7. Token identity is `<chain, contractAddress>` only, never a symbol.** Constrains `TokenInfo`'s
  shape: `contractAddress` (paired with the adapter's own `chain()`) must be the identity; a `symbol`
  field, if present, must be documented as display-only, never used for identity/equality.
- **L14. Sidecars are translation-only, treated as one more provider answer under quorum.** Constrains
  `ChainAdapter` as an interface: it must be implementable by a sidecar-backed adapter (a thin
  translation shim) with no special-casing — nothing in this task should assume every implementation
  is a direct RPC client.
- **L15. Module boundaries — package-by-feature, shared plumbing only in `common`.** `adapter/` is its
  own feature module; `model/`, `eth/`, `tron/` are its sub-packages, not separate top-level modules.

Also relevant but explicitly **not** this task's job: **L1** (2-of-3 quorum) and **L11/L12** (KMS/
screening gating) are the reasons `ChainAdapter` and the fake need to support *disagreement*
scripting, but implementing quorum/screening logic itself is tasks 9/19, not T05.

## Files involved

**Existing — read/extend:** none. No file under `adapter/` (or anywhere referencing `Chain`/
`ChainAdapter`) exists yet; T03's `ProviderProperties`/`FinalityProperties` use a plain `String` for
chain identifiers today and are **not** touched by this task (see Open Questions).

**New — main scope (`services/crypto/src/main/java/com/themistra/crypto/adapter/`):**
- `ChainAdapter.java` — VERBATIM per design §4c, copy exactly.
- `Chain.java` — enum, `ETHEREUM`, `TRON` (design.md §2's launch scope; no other chain values).
- `model/TxResult.java`, `model/TokenInfo.java`, `model/FinalityStatus.java`, `model/Subscription.java`
  — no verbatim shape given anywhere in the spec; Phase 2 design work.
- `ObservationSink.java` — no verbatim shape given; consumed by `subscribeAddress`, real consumer is
  the watcher layer (task 16), not this task.

**New — test scope (`services/crypto/src/test/java/com/themistra/crypto/adapter/`):**
- `FakeChainAdapter.java` — the task statement's own words say "for tests," and every task in this
  single-module (`services/crypto`) shares one test-source root, so placing it under `src/test/java`
  (not `src/main/java`) makes it directly usable by every later task's test code (T06 onward) without
  any test-jar packaging. This is extraction from the task statement's own wording, not new design.

## Dependencies

None beyond `java.lang`/`java.util`/`java.time`. No Spring dependency (matches agents.md: "Unit (plain
JUnit, fixed `Clock`, scripted fake `ChainAdapter`s)" — this layer is plain Java). No config keys, no
contracts (`contracts/events/chain/*` and `contracts/api/crypto-internal.yaml` don't exist yet, task
23, and neither references these adapter-layer types even once built).

## Acceptance Criteria

Derived from the task statement's own three clauses, since no `R`-number maps directly:

- **AC1.** `ChainAdapter` matches design §4c's verbatim interface exactly (method names, signatures,
  return/param types) — same byte-for-byte-style rigor T02 applied to `V1__chain_baseline.sql`.
- **AC2.** `Chain` has exactly two values, `ETHEREUM` and `TRON` (design.md §2's launch scope — Base/
  Arbitrum/Solana are explicitly "(later)" per §4c's finality table, not this task).
- **AC3.** `FakeChainAdapter` implements `ChainAdapter` and can be scripted into all four named
  behaviors: **agree** (returns a consistent, expected answer), **disagree** (returns a different
  answer than another scripted instance would, for the same query), **lag** (returns a
  behind-current-head / not-yet-observed state), and **reorg** (returns a result that invalidates a
  previously-returned one for the same `txHash`).
- **AC4 (L7).** `TokenInfo`'s identity is `<chain, contractAddress>` — no method or equality
  implementation treats `symbol` as identity-bearing.
- **AC5 (L4).** `FinalityStatus` carries raw per-chain state only — no precomputed boolean/confirmation-
  count field that would embed a finality *decision* (as opposed to finality *data*) in the adapter
  layer.

## Tests required

No named test exists in `package.md` §8 for this task (confirmed: none of the 28 named tests
reference `ChainAdapter`/`Chain`/`FakeChainAdapter`). Self-referential tests, mirroring T01's own
precedent for a shape/plumbing task:
- A test proving `FakeChainAdapter` can actually be scripted into each of the four AC3 behaviors
  (agree/disagree/lag/reorg) — exact API shape is Phase 2 design work.
- Possibly a compile-time-only "shape" check that `ChainAdapter`'s method signatures match design
  §4c verbatim (mirrors `ChainBaselineMigrationIntegrationTest`'s byte-for-byte SQL diff technique,
  though for an interface this is more naturally a reflection-based test than a text diff).

**ArchUnit is explicitly out of scope for this task** — `tasks.md` task 25 ("ArchUnit/module
boundaries... enforce no cross-module entity imports (L15)") is a dedicated, later task. T05 should
not add ArchUnit rules for the new `adapter/` package; that's task 25's job once more modules exist to
enforce boundaries between.

## Open Questions

**Not genuine external blockers** — `package.md` §11's Q1–Q8 don't touch the adapter-interface layer
at all. Two scoping questions for Phase 2 to resolve by engineering judgment, not external input:

1. **Should `Chain` retrofit T03's `ProviderProperties.ChainProviders.chain`/`FinalityProperties.enabledChains`**
   (currently plain, regex-constrained `String` fields)? The task statement only says "Define...
   `Chain`," not "migrate existing config to use it." Retrofitting would touch already-shipped,
   already-tested T03 files not authorized by this task's own statement. Leaning toward **not**
   retrofitting (leave T03's config as-is; `Chain` is new, additive), but flagging for an explicit
   Phase 2 decision rather than assuming either way.
2. **Exact shapes of `TxResult`/`TokenInfo`/`FinalityStatus`/`Subscription`/`ObservationSink`** — no
   verbatim artifact exists for any of them; Phase 2 must design minimal-but-sufficient shapes that
   later tasks (6, 7, 8, 9, 11, 14, 16) can plausibly build against without this task guessing those
   tasks' own future requirements.
