# crypto · T13 · Phase 1 — Specification Extraction

## Business Rules

- **R17.** If a payer address closely resembles (matching prefix and/or suffix) a previously seen
  counterparty for the same watch but differs, the system flags address poisoning on the observation so
  it propagates to downstream events and views.

## Locked Decisions

- **L9.** Address-poisoning flagging — when a payer address closely resembles (prefix/suffix match) a
  previously seen counterparty but differs, flag it on the observation so it propagates downstream.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — confirmed (Phase 0) to
  contain no table or column anywhere tracking "previously seen counterparty addresses per watch," and
  no flag column on `observations`. Neither is modified by this task (see Scope, Phase 2).
- `services/crypto/src/main/java/com/themistra/crypto/token/AddressValidator.java` (T12, just shipped)
  — sibling `token/` package class, pattern precedent only (pure, stateless, no-persistence
  `@Component` predicate with no real caller yet).

**New, per design.md §6 (`token/` package, already established by T11/T12):**
- `token/AddressPoisoningDetector.java` — the only file design.md names for this task.

**Explicitly NOT in this task's own scope (confirmed by Phase 0's sequencing finding):**
- `Watch`/`WatchRepository` — task 15, does not exist yet.
- Any modification to `chain.observations` or `chain.watches` (both frozen, T02/T08).
- Any actual event emission (`chain.tx.*` events are task 17, not yet built).

## Dependencies

- No new external library — confirmed (Phase 1) no string-similarity library (e.g., Apache Commons
  Text) exists in this module's `pom.xml`, and R17's own wording ("matching prefix and/or suffix") is a
  literal character-comparison operation, not a general edit-distance/fuzzy-similarity algorithm, so
  none is needed.
- No persistence, no `Clock`, no outbox — pending Phase 2's scope decision (see Open Questions), but
  the strong precedent (T09 `QuorumEvaluator`, T12 `AddressValidator`) is a pure, stateless function.

## Acceptance Criteria

- **AC1 (R17, L9).** Given a candidate address and a collection of previously-seen addresses, the
  detector flags the candidate as poisoning-suspicious if it matches the prefix and/or suffix (exact
  definition of "matching," including length and case-sensitivity, is a Phase 2 decision) of any
  previously-seen address in that collection, while being a genuinely different address overall (an
  exact match to a previously-seen address is not poisoning — it is the same, already-trusted address
  reappearing).
- **AC2 (R17).** A candidate address that does not resemble any previously-seen address is not flagged.
- **AC3 (scope, Phase 0 finding).** This task does not persist, query, or otherwise manage "previously
  seen counterparties for a watch" itself — that collection is supplied by the caller. No `Watch`
  dependency, no new schema.
- **AC4 (scope, Phase 0 finding).** This task does not modify `chain.observations` or emit any event —
  "propagate the flag onto observations/events" (the task statement's own end-to-end framing) is left to
  whichever future task actually has both the real per-watch counterparty history and a live
  observation/event pipeline to attach the flag to (most plausibly the watcher layer, task 16).

## Tests required

- `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (package.md §8, named) — AC1.
- A test asserting an address with no resemblance to any previously-seen address is not flagged (AC2).
- A test asserting an EXACT match to a previously-seen address is not flagged as poisoning (AC1's own
  "but differs" clause — R17's literal wording).
- A test asserting an empty/no-history collection never flags anything (a new watch's first-ever
  counterparty can never be "poisoning" against nothing).

## Open Questions

No blockers cited in `package.md` §11 apply to this task. Two items are genuine gaps the spec's author
never addressed anywhere, requiring an implementer-proposed resolution (Phase 2, subject to Kimi
challenge + human sign-off), matching the precedent T08-T12 already set for similarly under-specified
areas:

- **No numeric definition anywhere for "prefix/suffix similarity"** — how many leading/trailing
  characters must match, whether matching is case-sensitive, and whether both prefix AND suffix must
  match or either alone suffices (R17's own "and/or" wording suggests either alone is sufficient, but
  the match *length* is undefined).
- **Whether this task's scope is the pure comparison algorithm only, or a fuller feature including
  persistence of per-watch counterparty history and actual propagation onto observations/events** — the
  task statement's own literal wording describes the latter, but no data source or integration point for
  any part of that currently exists (Phase 0 finding); the strong, consistent precedent from every prior
  task favors scoping to the algorithm alone.
