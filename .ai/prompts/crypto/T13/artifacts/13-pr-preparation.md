# crypto · T13 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T13
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T13/` artifacts).

## Commit title

```
crypto: add address-poisoning prefix/suffix detector (T13)
```

## Commit message

```
crypto: add address-poisoning prefix/suffix detector (T13)

Implement AddressPoisoningDetector.detectPoisoning: flags a candidate
address that shares a leading or trailing run of characters with a
previously-seen counterparty but differs overall (R17, L9). Pure,
stateless @Component - no persistence, no Watch dependency, no event
emission. The task statement's own end-to-end "propagate the flag onto
observations/events" framing is not satisfiable yet: no per-watch
counterparty history and no live observation/event pipeline exist in
this codebase (confirmed in Phase 0). That integration is deferred to
whichever future task has both - most plausibly the watcher layer.

Every EVM address begins with "0x" and every Tron mainnet address
begins with "T", so a flat 4-character prefix threshold would only
require 2 (EVM) or 3 (Tron) additional real matching characters beyond
that universal prefix - far noisier than a genuine resemblance implies,
especially for EVM's 16-symbol hex alphabet. Raised to 6 (restoring 4
real matching hex digits for EVM) without making the detector
chain-aware; a disclosed side effect is that Tron detection becomes
somewhat more conservative than the original per-chain intent. The
suffix threshold (4) is unchanged - suffixes have no analogous
universal-prefix problem.

Comparison is case-sensitive with no address-format normalization or
validation, matching AddressValidator/TokenValidator's own established
"exact string, caller normalizes" convention in this package. An exact
match to a previously-seen address is correctly treated as the same,
already-trusted counterparty reappearing, not poisoning - but that
per-entry short-circuit does not exempt the candidate from being
compared against OTHER, unrelated history entries; a candidate that
exactly matches one entry while genuinely resembling a different one is
still flagged via the second entry.

String.regionMatches was chosen over substring-based comparison because
it was confirmed by direct execution to return false (never throw) for
negative offsets or out-of-range lengths, giving short-address safety
for free with no manual bounds-checking - avoiding the kind of
uncaught-exception gap T12 found in a third-party library the hard way.

Test review (Phase 11) added 8 tests closing coverage gaps: mixed
history, suffix/prefix boundary lengths, an EVM-shaped suffix match,
length-mismatched candidate/previous pairs, and both parameters null
together. One reviewer-suggested expected result was corrected after
tracing the implementation rather than taken on faith: a candidate that
exactly matches one history entry while resembling a different one is
flagged (via the second entry), not exempted outright - the exact-match
short-circuit is per-entry, not global.

No production code changed past Phase 9's Javadoc addition; this task
required no schema migration and has no Docker dependency, consistent
with AddressValidator's (T12) own precedent.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/src/main/java/com/themistra/crypto/token/AddressPoisoningDetector.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/token/AddressPoisoningDetectorTest.java` — new (27 tests)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T13/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T13 adds the address-poisoning prefix/suffix detector R17/L9 require: a pure, stateless comparison
predicate over a caller-supplied counterparty history, with no persistence and no wired-in caller yet
(mirroring T11/T12's own precedent of shipping the algorithm ahead of its eventual integration point).
Its own review cycle raised the prefix-match threshold to avoid EVM/Tron's universal-prefix false-positive
noise (Phase 3/4), and its Phase 11 test review corrected a reviewer-suggested expected result after
tracing the implementation, rather than accepting it on faith — the same verify-don't-assume discipline
that caught real defects in T11 and T12.

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, no new warnings.
- `mvn -pl services/crypto test -Dtest=AddressPoisoningDetectorTest` — 27/27 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 399 tests, 391 passing, 8 errors, all
  `IllegalState: … Docker environment …` (the same pre-existing set carried unchanged from T12's own
  baseline — this task introduces no new persistence layer) — zero genuine failures, zero regressions.

## Specification references

- **Task:** T13 — Address-poisoning detection (`spec/crypto-service/tasks.md` #13).
- **Requirements:** R17 (`spec/crypto-service/requirements.md`).
- **Locked decisions:** L9 (`spec/crypto-service/design.md`) — address-poisoning flagging on
  prefix/suffix resemblance to a previously-seen counterparty.
- **Named tests:** `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task —
  `AddressPoisoningDetector` is a pure predicate with no HTTP endpoint and no event emission.
