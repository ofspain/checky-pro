# crypto · T13 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-05. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Address-poisoning detector. Implement prefix/suffix similarity flagging against previously seen
counterparties for the same watch (L9, R17). Propagate the flag onto observations/events.

## Purpose

Defends against a real, well-known on-chain social-engineering attack: an attacker sends a look-alike
address (matching the first/last several characters a wallet UI displays) hoping a user later copies it
by mistake instead of a real, previously-used counterparty address.

## Scope

**In:**
- **`AddressPoisoningDetector`** — stateless `@Component` (mirrors `AddressValidator`'s T12 precedent).
  One method:
  - **`Optional<String> detectPoisoning(String candidateAddress, Collection<String> previouslySeenAddresses)`**
    — returns the specific previously-seen address the candidate resembles-but-differs-from, or empty.
  - **Amendment #1 (Kimi Issue 1, High — corrects the original design): asymmetric match-length
    thresholds — `PREFIX_MATCH_LENGTH = 6`, `SUFFIX_MATCH_LENGTH = 4`.** Every EVM address begins with
    the literal `0x`, and every Tron mainnet address begins with `T` — a flat 4-character *prefix*
    threshold with no normalization would only require 2 additional matching hex digits for EVM (a
    1-in-256 collision chance, far noisier than the intended ~1-in-65536 signal a genuine 4-hex-digit
    match implies), while barely denting Tron's much larger 58-symbol alphabet. Raising the **prefix**
    threshold to 6 restores the originally-intended 4 real matching hex digits for EVM (`0x` + 4 more)
    without making the detector chain-aware. The **suffix** threshold stays at 4 — addresses' trailing
    characters carry no chain-mandated shared prefix, so the original noise concern never applied there;
    tightening it too would be an unjustified, unrequested change.
  - **A candidate that exactly equals a previously-seen address is never flagged** (R17's "but differs"
    clause).
  - **An empty or `null` `previouslySeenAddresses`, or a `null` `candidateAddress`, returns
    `Optional.empty()`.**
  - **Amendment #6 (Kimi Issue 6): a `null` element inside `previouslySeenAddresses` is skipped, not
    treated as an error** — the collection is iterated defensively; a stray `null` entry does not throw
    and does not itself ever match.
  - **Amendment #3 (Kimi Issue 3): addresses shorter than the relevant threshold can never trigger a
    match and are handled safely, no exception.** Implemented via `String.regionMatches(int, String,
    int, int)`, which is documented to return `false` (not throw) when the compared region would
    extend beyond either string's own length — chosen specifically over a `substring`-based comparison,
    which would throw `StringIndexOutOfBoundsException` for a candidate or history address shorter than
    the threshold.
  - **Amendment #2 (Kimi Issue 2, documentation only): comparison is case-sensitive, with no address-
    format normalization of any kind — this is unchanged from the Phase 2 draft, consistent with
    `AddressValidator`/`TokenValidator`'s own twice-established "exact string, caller normalizes"
    convention in this same `token/` package.** Made explicit: **a legitimate, previously-seen
    counterparty reappearing in a different casing (e.g., lowercase vs. checksummed) will NOT be
    recognized as an exact match by this detector and may be flagged as resembling-but-differing** —
    this is the caller's responsibility to avoid by normalizing both the candidate and the history
    collection to one canonical casing before calling `detectPoisoning`, not a defect in this class.
  - **Amendment #7 (Kimi Issue 7, documentation only): if multiple previously-seen addresses resemble
    the candidate, the specific one returned is intentionally arbitrary** — consumers must treat the
    result as a boolean "poisoning suspected" signal, not a ranked or deterministic "closest match."
  - **Amendment #8 (Kimi Issue 8, documentation only): this detector performs no address-format
    validation of its own.** A structurally malformed candidate that happens to share enough characters
    with a real previous address will still be flagged. Callers wanting to exclude structurally invalid
    candidates from consideration should run them through `AddressValidator` (T12) first — this detector
    does not do so itself.
  - **Amendment #9 (Kimi Issue 9): both thresholds are named constants** (`PREFIX_MATCH_LENGTH`,
    `SUFFIX_MATCH_LENGTH`) with a comment recording the wallet-truncation rationale; a future task may
    externalize either as configuration if operational experience demands a different value — not done
    now, since nothing indicates that need yet.
- **Amendment #4 (Kimi Issue 4, documentation only): explicit follow-up dependency noted.** L9's own
  "flag it on the observation so it propagates downstream" is not fully satisfied by this task alone —
  the most plausible future integration point is the watcher layer (task 16, the first task with both
  real per-watch counterparty history and a live observation/event pipeline). That future task must call
  `detectPoisoning` and attach its result to `chain.observations` and emitted events for L9 to be fully
  enforced end-to-end. This mirrors T08's own Amendment #10 and T11's `QuorumOutcome`-mapping deferral,
  both stated explicitly for analogous reasons.

**Out:**
- Any `Watch`/`WatchRepository` dependency or persistence of counterparty history.
- Any modification to `chain.observations` or `chain.watches` (both frozen, T02/T08).
- Any actual event emission or observation annotation.
- Address-format normalization or validation of any kind.
- A general edit-distance/fuzzy-similarity algorithm or new library dependency.
- Chain-aware prefix-stripping (considered as an alternative fix to Amendment #1, not adopted — the
  asymmetric-threshold fix achieves the same correction while keeping the detector chain-agnostic).

## Business Rules

- **R17.** A candidate address matching the prefix (≥6 characters) and/or suffix (≥4 characters) of a
  previously-seen address, while differing from it, is flagged.

## Locked Decisions

- **L9.** Address-poisoning flagging on prefix/suffix resemblance — implemented as the comparison
  primitive above; "propagates downstream" is explicitly deferred (Amendment #4).

## Dependencies

None — no new external library, no persistence, no `Clock`, no outbox.

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
- `token/AddressValidator.java`/`TokenValidator.java`/`TokenAllowlist.java` (T11/T12).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R17, L9, Amendment #1).** A candidate sharing ≥6 identical leading characters, or ≥4 identical
  trailing characters, with some address in `previouslySeenAddresses`, while differing from that address
  overall, returns that address.
- **AC2 (R17).** A candidate resembling no address in `previouslySeenAddresses` returns empty.
- **AC3 (R17's "but differs" clause).** An exact match to a previously-seen address returns empty.
- **AC4 (null/empty safety).** `null`/empty `previouslySeenAddresses`, a `null` `candidateAddress`, or a
  `null` element inside the collection, all return empty without throwing (Amendment #6).
- **AC5 (case-sensitivity, documented, Amendment #2).** Comparison is case-sensitive; no normalization.
- **AC6 (multiple matches, documented, Amendment #7).** Any one of several resembling addresses may be
  returned; the choice is intentionally arbitrary.
- **AC7 (short-address safety, Amendment #3).** A candidate or history address shorter than the
  relevant threshold never matches and never throws.
- **AC8 (EVM-prefix-noise fix, Amendment #1).** Two EVM addresses sharing only `0x` plus 2 hex digits
  (4 total leading characters) are NOT flagged — the raised 6-character prefix threshold requires 4 real
  matching hex digits beyond the universal `0x`.

## Required Tests

- `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (package.md §8, named) — AC1.
- A test asserting no flag for a candidate with no resemblance to any previously-seen address (AC2).
- A test asserting no flag for an exact match to a previously-seen address (AC3).
- A test asserting `null` `previouslySeenAddresses` returns empty (AC4).
- A test asserting an empty `previouslySeenAddresses` returns empty (AC4).
- A test asserting a `null` `candidateAddress` returns empty (AC4).
- A test asserting a `null` element inside a non-null `previouslySeenAddresses` is skipped safely (AC4).
- A test asserting a prefix-only match (≥6 leading, different trailing) is flagged (AC1).
- A test asserting a suffix-only match (≥4 trailing, different leading) is flagged (AC1).
- A test asserting a case-differing "same" address (identical characters, different case) is flagged as
  resembling-but-differing, not recognized as an exact match (AC5).
- A test asserting exactly 5 matching leading characters does NOT flag, and exactly 6 does (AC1
  boundary).
- A test asserting exactly 3 matching trailing characters does NOT flag, and exactly 4 does (AC1
  boundary).
- A test asserting a candidate or history address shorter than the relevant threshold does not throw and
  is never flagged (AC7).
- A test asserting two EVM-shaped addresses sharing only `0x` + 2 hex digits (4 total leading
  characters) are NOT flagged (AC8 — the specific regression guard for the Amendment #1 fix).

## Constraints

- **Performance:** linear scan of `previouslySeenAddresses` — no concern at any realistic size.
- **Security:** this task's entire purpose is a security-relevant flag; no secret introduced or handled.
- **Thread-safety:** `AddressPoisoningDetector` holds no mutable state; trivially thread-safe.
- **Module boundaries:** no import from `adapter/`, `observation/`, `provider/`, or `quorum/`.
- **Null handling:** returns `Optional.empty()` for every null/empty/malformed input rather than
  throwing (AC4, AC7).

## Open Questions

No blockers. All 9 Phase 3 findings are resolved above.
