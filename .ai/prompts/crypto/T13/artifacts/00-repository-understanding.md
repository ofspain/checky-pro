# crypto · T13 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`), package-by-feature under
`com.themistra.crypto`, owning the `chain` Postgres schema exclusively. This task's own task statement
("flag address poisoning against previously seen counterparties **for the same watch**... propagate the
flag onto observations/events") describes an end-to-end feature spanning at least three integration
points — a per-watch history of counterparty addresses, the `Observation` entity (T08), and downstream
Kafka events (`chain.tx.*`, task 17, not yet built) — but, per Phase 0's own finding below, none of
those integration points currently exist or are within this task's own likely scope; only the
comparison algorithm itself (`AddressPoisoningDetector`, design.md §6's own name for this task) is
plausibly this task's actual deliverable.

## 2. Existing code this task touches

**A significant sequencing/scope tension, confirmed by rereading `tasks.md`'s own ordered list:**
**`Watch` does not exist yet.** Task 15 ("Watch registration API") is the task that implements
`WatchService`/`WatchController` and "Persist `Watch` and its `ChainCursor`" — task 15 comes **two tasks
after this one**. T13's own task statement says "previously seen counterparties for the **same
watch**," but no `Watch` entity, repository, or Java representation of "a watch" exists anywhere in this
codebase yet. The `chain.watches` table itself does exist (`V1__chain_baseline.sql:7-14`, T02, frozen):
`id, watch_id (UUID), invoice_uuid, chain, address` (the *watched*/merchant address, not a payer/
counterparty address), `token_contract_address, expected_amount, status, expires_at, created_at,
unregistered_at` — but no column or sibling table anywhere records "counterparty addresses seen so far
for this watch." This confirms there is currently no data source this task could query even if it
wanted to look up "previously seen counterparties" itself.

**No `observations` column exists for an address-poisoning flag.** `chain.observations`
(`V1__chain_baseline.sql:24-33`, T08, frozen) has `id, chain, tx_hash, provider, fact_type,
raw_response (JSONB), s3_snapshot_key, observed_at` — no flag/boolean column of any kind. "Propagate the
flag onto observations" (this task's own wording) would require either a new migration adding a column
to an already-frozen, already-shipped table (a real, disruptive schema change) or storing the flag
inside `raw_response`'s own opaque JSON (which `Observation`/T08 documents as the caller-serialized
*verbatim* provider payload — folding a locally-computed flag into it would break that "verbatim"
guarantee). Neither looks like something this task should attempt without explicit, deliberate
justification.

**No structured schema exists anywhere for "seen counterparty addresses per watch."** Confirmed by
listing every `CREATE TABLE` in `V1__chain_baseline.sql`: `watches, observations, quorum_decisions,
provider_health, chain_cursors, token_allowlist, screening_results, attestations, outbox, shedlock` —
none of these track per-watch counterparty-address history.

**Already exists, consumed but not modified (conceptually relevant, precedent-only):**
- `token/AddressValidator.java` (T12, just shipped) — a sibling class in the same `token/` package,
  itself a pure, stateless, no-persistence `@Component` predicate with no real caller yet. Directly
  relevant precedent: T12 already established that a `token/`-package validator class can be built as a
  pure function with zero data-source dependency, deferring all wiring to a future task.
- `quorum/QuorumEvaluator.java` (T09) — the strongest precedent in this codebase for "pure comparison
  logic, no persistence, no caller, wiring deferred," built specifically because a fuller feature (2-of-3
  quorum) was too early to wire end-to-end at the time.

**New in this task (per design.md §6 `token/` package map):**
- `token/AddressPoisoningDetector.java` — the only file design.md names for this task.

## 3. Established patterns to follow

- **"Pure primitive first, integration deferred" is this codebase's dominant pattern** for exactly this
  kind of situation (a feature described end-to-end in prose, but whose actual data sources/callers
  don't exist yet) — T08's `Observation`/`ObservationSnapshotStore` had no real caller until T09; T09's
  `QuorumEvaluator` had none until quorum-adjacent wiring; T11's `TokenValidator`'s `UNKNOWN_TOKEN`→
  `QuorumOutcome` mapping was explicitly deferred to an unnamed future task; T12's `AddressValidator` has
  no caller and no boundary-enforcement wiring yet either. T13's own likely shape — a comparison
  function taking a candidate address and a caller-supplied collection of previously-seen addresses,
  returning a flag — fits this pattern precisely, with "for the same watch" and "propagate onto
  observations/events" being the *future* task's job (most plausibly the watcher layer, task 16, the
  first task that will actually observe real transactions and have real per-watch counterparty history
  to compare against, and would need its own new persistence for that history at that point).
- **No unrequested schema changes to already-frozen tables** — `observations` (T08) and `watches` (T02)
  are both already-shipped, frozen tables; modifying either to add new columns is a significant,
  disruptive change this task's own narrow statement does not clearly authorize (see Known gaps below).
- **Library-verification-over-memory discipline** — not obviously needed here (no external library like
  web3j/trident is implicated for a simple string-similarity comparison), but worth confirming in Phase
  1/2 that no existing utility already implements this (e.g., Apache Commons Text's Levenshtein/
  similarity utilities) before hand-rolling one, if such a dependency happens to already exist in this
  module (not yet checked in this phase).

## 4. Testing conventions

- Plain JUnit 5 — likely no mocks needed if the detector is a pure function of its inputs (mirrors
  `QuorumEvaluatorTest`'s and the brand-new `AddressValidatorTest`'s own "no collaborators" precedent).
- No Testcontainers/Docker dependency expected, if this task's scope is confirmed to be the pure
  algorithm only (mirrors T12's own no-persistence scope).
- Named test convention: `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (package.md §8) is
  written verbatim as a test method name, per every prior task's own convention.

## 5. Known gaps / unknowns

- **No numeric definition anywhere in the spec for "prefix/suffix similarity."** Neither R17 nor L9 nor
  package.md say how many leading/trailing characters must match, whether matching is case-sensitive,
  whether both prefix AND suffix must match or either alone suffices ("matching prefix **and/or**
  suffix" per R17's own wording — the "and/or" is at least explicit that either alone is sufficient, but
  the *length* of "matching" is undefined). This is a genuine, spec-author-unaddressed gap requiring an
  implementer-proposed resolution (Phase 2, subject to Kimi challenge + human sign-off), matching the
  precedent T08-T11 already set for similarly under-specified numeric thresholds.
- **Whether this task is scoped to the pure comparison algorithm only, or is expected to also build new
  persistence for "previously seen counterparties per watch," is not settled by the task statement's own
  wording** — its literal text ("flag address poisoning against previously seen counterparties for the
  same watch... propagate the flag onto observations/events") reads as a full end-to-end feature, but no
  data source or integration point for any part of that currently exists, and `Watch` itself is two
  tasks away from existing. Phase 1/2 must resolve this explicitly, most plausibly by scoping this task
  to the algorithm alone (mirroring every prior task's own "build the primitive, defer wiring" pattern)
  and stating clearly what is deferred and to where.
- **Whether an existing string-similarity library already exists as a dependency in this module** — not
  yet checked in this phase; Phase 1/2 should confirm before deciding whether to hand-roll a
  prefix/suffix comparison (likely trivial either way, given the algorithm's expected simplicity) or
  reuse something already present.
