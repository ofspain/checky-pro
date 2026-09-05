# crypto · T13 · Phase 2 — Task Implementation Brief (TIB)

## Task

Address-poisoning detector. Implement prefix/suffix similarity flagging against previously seen
counterparties for the same watch (L9, R17). Propagate the flag onto observations/events.

## Purpose

Defends against a real, well-known on-chain social-engineering attack: an attacker sends a look-alike
address (matching the first/last several characters a wallet UI displays) hoping a user later copies it
by mistake instead of a real, previously-used counterparty address. This task builds the comparison
primitive that makes such a look-alike detectable.

## Scope

**In:**
- **`AddressPoisoningDetector`** — a stateless `@Component` (mirrors `AddressValidator`'s own T12
  precedent exactly: no persistence, no injected dependency, but a proper Spring bean for eventual
  injection at a future call site). One method:
  - **`Optional<String> detectPoisoning(String candidateAddress, Collection<String> previouslySeenAddresses)`**
    — returns the specific previously-seen address the candidate resembles-but-differs-from (empty if
    no such address exists). Returning *which* address matched (not a bare `boolean`) mirrors
    `TokenValidator.validate`'s own `Optional<TokenAllowlist>` precedent (T11) — a future caller
    building an actual ops-facing flag/alert will want the specific address that triggered it, the same
    reasoning that shaped `HeldFactAlerter`'s own "give context" design (T09).
  - **Resolves Phase 1 Open Question #1 (no numeric definition given anywhere): "matching prefix and/or
    suffix" is defined as at least 4 identical leading characters OR at least 4 identical trailing
    characters** (comparison is case-sensitive, literal, chain-agnostic — no address-format
    normalization of any kind, matching `AddressValidator`/`TokenValidator`'s own established
    "exact string, caller's responsibility to normalize" convention in this same `token/` package). 4
    is chosen because common wallet/explorer UIs truncate addresses for display as roughly
    `0x1234...abcd` — a look-alike attack only needs to fool that truncated view, so a *shorter*
    required match length flags *more* candidates as suspicious (favoring false positives, which a
    human can dismiss, over false negatives, which cannot be undone once funds are sent) — an
    implementer's own justified, reviewable default, not a value taken from the spec.
  - **A candidate that exactly equals a previously-seen address is never flagged** (R17's own "but
    differs" clause) — the same, already-trusted counterparty transacting again is not poisoning.
  - **An empty or `null` `previouslySeenAddresses` collection, or a `null` `candidateAddress`, returns
    `Optional.empty()`** — nothing to compare against, or nothing to compare, is not an error condition
    for this predicate (mirrors `AddressValidator`'s own "return a value, never throw" philosophy from
    T12, adapted for this method's own null-tolerant semantics — a candidate address on a brand-new
    watch, with no history yet, can never be flagged, by definition).
- **Resolves Phase 1 Open Question #2 (scope): this task is the pure comparison algorithm only.**
  **Confirmed (Phase 0): `Watch` does not exist yet (task 15, two tasks later), no schema anywhere
  tracks "previously seen counterparties per watch," and `chain.observations` has no flag column.**
  This task does not persist, query, or manage counterparty history itself (the caller supplies the
  `previouslySeenAddresses` collection); it does not modify `chain.observations` or `chain.watches`
  (both frozen); it does not emit or annotate any event. "Propagate the flag onto observations/events"
  — the task statement's own end-to-end framing — is explicitly deferred to whichever future task first
  has both real per-watch counterparty history and a live observation/event pipeline to attach the flag
  to (most plausibly the watcher layer, task 16, mirroring the exact deferral pattern T08's own
  Amendment #10 and T11's `QuorumOutcome`-mapping deferral already established for analogous
  end-to-end claims this early in the build-out).

**Out:**
- Any `Watch`/`WatchRepository` dependency or persistence of counterparty history.
- Any modification to `chain.observations` or `chain.watches` (both frozen, T02/T08).
- Any actual event emission or observation annotation.
- Address-format normalization (case-folding, checksum handling) — a chain-aware concern this pure,
  chain-agnostic comparator does not attempt, consistent with `AddressValidator`/`TokenValidator`'s own
  established scope boundaries.
- A general edit-distance/fuzzy-similarity algorithm — R17's own wording ("matching prefix and/or
  suffix") is a literal character-comparison operation; no new library dependency is introduced (none
  exists in this module's `pom.xml` for general string similarity, and none is needed).

## Business Rules

- **R17.** A candidate address matching the prefix and/or suffix (per the resolved 4-character
  definition above) of a previously-seen address, while differing from it, is flagged.

## Locked Decisions

- **L9.** Address-poisoning flagging on prefix/suffix resemblance to a previously-seen counterparty —
  implemented as the comparison primitive above; "flag it on the observation so it propagates
  downstream" is explicitly deferred (see Scope).

## Dependencies

- None — no new external library, no persistence, no `Clock`, no outbox.

## Inputs

- `(candidateAddress, previouslySeenAddresses)` — from whatever future caller first has real per-watch
  counterparty history to supply. No such caller exists in this task's own scope.

## Outputs

- `Optional<String>` — the specific previously-seen address the candidate resembles-but-differs-from,
  or empty.

## State Changes

None — a pure function.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/token/AddressPoisoningDetector.java`

## Files to Modify

None expected.

## Files NOT to Modify

- `chain.observations`/`chain.watches` schema (T02/T08, frozen).
- `token/AddressValidator.java`/`TokenValidator.java`/`TokenAllowlist.java` (T11/T12) — pattern
  precedent only, not modified.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R17, L9).** A candidate address sharing at least 4 identical leading characters, or at least 4
  identical trailing characters, with some address in `previouslySeenAddresses`, while differing from
  that address overall, returns that address (flagged).
- **AC2 (R17).** A candidate address resembling no address in `previouslySeenAddresses` returns empty.
- **AC3 (R17's "but differs" clause).** A candidate address that exactly equals an address in
  `previouslySeenAddresses` returns empty (not flagged) for that address, even though it trivially
  "matches" prefix and suffix in full.
- **AC4 (null/empty safety).** A `null` or empty `previouslySeenAddresses`, or a `null`
  `candidateAddress`, returns empty without throwing.
- **AC5 (case-sensitivity, documented).** Comparison is case-sensitive and performs no address-format
  normalization.
- **AC6 (multiple matches).** If more than one previously-seen address resembles the candidate, any one
  of them may be returned (this task does not specify or need a particular selection order, since a
  single flagged instance is sufficient to raise the concern).

## Required Tests

- `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (package.md §8, named) — AC1.
- A test asserting no flag for a candidate with no resemblance to any previously-seen address (AC2).
- A test asserting no flag for a candidate that exactly matches a previously-seen address (AC3).
- A test asserting a `null` `previouslySeenAddresses` collection returns empty (AC4).
- A test asserting an empty `previouslySeenAddresses` collection returns empty (AC4).
- A test asserting a `null` `candidateAddress` returns empty (AC4).
- A test asserting a prefix-only match (matching leading characters, different trailing characters) is
  flagged (AC1, the "and/or" — prefix alone suffices).
- A test asserting a suffix-only match (matching trailing characters, different leading characters) is
  flagged (AC1, suffix alone suffices).
- A test asserting a case-differing candidate (same characters, different case) is NOT treated as an
  exact match — it is flagged as resembling-but-differing, since comparison is case-sensitive (AC5).

## Constraints

- **Performance:** the comparison scans the given `previouslySeenAddresses` collection linearly — no
  concern at any realistic per-watch counterparty-history size.
- **Security:** this task's entire purpose is a security-relevant flag; no secret is introduced or
  handled.
- **Thread-safety:** `AddressPoisoningDetector` holds no mutable state; trivially thread-safe.
- **Module boundaries:** no import from `adapter/`, `observation/`, `provider/`, or `quorum/`.
- **Null handling:** both a `null` `candidateAddress` and a `null`/empty `previouslySeenAddresses`
  return `Optional.empty()` rather than throwing (AC4) — a deliberate fit for a "nothing to flag yet"
  predicate, matching `AddressValidator`'s own null-tolerant philosophy (T12) over the
  `Objects.requireNonNull`-and-throw discipline used by this service's stateful collaborator classes.

## Open Questions

No blockers. Both Phase 1 open items (the numeric prefix/suffix match-length definition; the task's own
scope boundary) are resolved above as implementer-proposed decisions, ready for Phase 3 (Kimi)
challenge.
