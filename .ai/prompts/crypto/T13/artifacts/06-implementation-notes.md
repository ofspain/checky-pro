# crypto · T13 · Phase 6 — Implementation Notes

## What changed

One new file — exactly as planned (Phase 5), no file the frozen brief did not authorize was touched.

- **`AddressPoisoningDetector.java`** — `@Component`, one method: `detectPoisoning(String, Collection<String>)`
  returning `Optional<String>`. Two named constants (`PREFIX_MATCH_LENGTH = 6`, `SUFFIX_MATCH_LENGTH = 4`,
  Amendment #1), a defensive iteration over the caller-supplied history (skipping `null` entries and
  exact matches), and two private helpers built on `String.regionMatches` — chosen specifically for its
  verified-safe behavior on short strings/negative offsets (no manual bounds-checking needed).

## Mapping to plan and acceptance criteria

| AC | Satisfied by |
|---|---|
| AC1 (prefix ≥6 or suffix ≥4 match → flagged) | `hasMatchingPrefix`/`hasMatchingSuffix`, either sufficient (`||`) |
| AC2 (no resemblance → empty) | The loop completes with no match, falls through to `Optional.empty()` |
| AC3 (exact match → not flagged) | The `candidateAddress.equals(previous)` check in the loop's `continue` condition |
| AC4 (null/empty safety) | The leading `null` checks on both parameters, plus the `previous == null` skip inside the loop |
| AC5 (case-sensitive, no normalization) | `String.equals`/`regionMatches` are both inherently case-sensitive; no `.toLowerCase()`/similar anywhere |
| AC6 (arbitrary match among multiple) | The loop returns on the first match found, by construction |
| AC7 (short-address safety) | `regionMatches`'s own verified-by-execution safe handling of negative offsets/out-of-range lengths |
| AC8 (EVM-prefix-noise fix) | `PREFIX_MATCH_LENGTH = 6`, not 4 |

## Deviations forced by reality

None. `mvn -pl services/crypto compile` succeeded on the first attempt, with zero warnings. The
`String.regionMatches` safety properties this implementation relies on had already been confirmed via
direct execution (Phase 5) before this phase, so no surprise surfaced during coding itself.
