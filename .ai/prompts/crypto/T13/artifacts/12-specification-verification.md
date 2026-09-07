# crypto · T13 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Source | Satisfied by | Tests |
|---|---|---|---|
| R17 — flag a candidate resembling (prefix/suffix) a previously-seen counterparty but differing from it | package.md §5 | `AddressPoisoningDetector.detectPoisoning` | `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity`, `flagsAPrefixOnlyMatch`, `flagsASuffixOnlyMatch`, `flagsWhenBothPrefixAndSuffixMatch` |
| L9 — pure prefix/suffix comparison, flag propagation deferred to a future integration point | package.md §7 | Class-level Javadoc explicitly scopes out `Watch`/persistence/event-emission; no such dependency added | N/A (design/scope, not a runtime behavior) |
| AC1 — flags on prefix and/or suffix match while genuinely differing | Phase 1 | `detectPoisoning` prefix/suffix `regionMatches` checks + exact-match `continue` | `flagsAPrefixOnlyMatch`, `flagsASuffixOnlyMatch`, `flagsWhenBothPrefixAndSuffixMatch`, boundary tests, `flagsALookAlikeEvenWhenAnotherHistoryEntryIsAnExactMatch` |
| AC2 — no flag for an unrelated candidate | Phase 1 | Loop falls through to `Optional.empty()` when no entry matches | `doesNotFlagAnUnrelatedCandidate` |
| AC3 — no persistence/query of counterparty history; caller-supplied only | Phase 1 | `Collection<String> previouslySeenAddresses` parameter, no injected repository | N/A (structural — confirmed by class having zero persistence dependencies) |
| AC4 — no modification to `chain.observations`, no event emission | Phase 1 | No such write path exists anywhere in the class | N/A (structural — confirmed by class having zero such dependencies) |
| AC5 — case-sensitive, unnormalized comparison | Phase 2 | `String.equals`/`regionMatches` used directly, no case-folding | `flagsACaseDifferingAddressAsResemblingNotExactlyMatching` |
| AC6 — arbitrary match among multiple resembling entries | Phase 2/9 | First match encountered while iterating is returned | `returnsOneOfMultipleMatchingPreviouslySeenAddresses` |
| AC7 — null/empty/too-short inputs never throw, return empty | Phase 2 | Leading null guard + `regionMatches`' verified no-throw behavior for out-of-range offsets/lengths | `returnsEmptyForANullPreviouslySeenAddressesCollection`, `returnsEmptyForAnEmptyPreviouslySeenAddressesCollection`, `returnsEmptyForANullCandidateAddress`, `skipsANullElementInThePreviouslySeenAddressesCollection`, `doesNotFlagOrThrowForAShortCandidateAddress`, `doesNotFlagOrThrowForAShortPreviouslySeenAddress`, `doesNotFlagOrThrowForAnEmptyCandidateAddress`, `returnsEmptyWhenAllPreviouslySeenAddressesAreTooShortToMatch`, `returnsEmptyWhenBothParametersAreNull` |
| AC8 — raised prefix threshold does not make realistic EVM addresses noisy false positives (Amendment #1) | Phase 3/4 | `PREFIX_MATCH_LENGTH = 6` | `doesNotFlagTwoEvmAddressesSharingOnlyTheZeroXPrefixAndTwoHexDigits`, `doesNotFlagAtFiveMatchingLeadingCharactersButFlagsAtSix` |
| Phase 11 coverage gaps (Kimi) | Phase 11 | New/enhanced tests, see Phase 11 summary | `flagsWhenOnlyOneAddressInHistoryMatches`, `flagsWhenCandidateIsExactlyTheSuffixLengthAndMatchesTheSuffixOfAPreviousAddress`, `flagsViaSuffixWhenPreviousAddressIsTooShortForPrefixMatch`, `flagsAnEvmShapedAddressWithASuffixMatch`, `flagsAPrefixMatchRegardlessOfLengthDifference`, `flagsASuffixMatchRegardlessOfLengthDifference`, `flagsALookAlikeEvenWhenAnotherHistoryEntryIsAnExactMatch` (corrected expectation), `returnsEmptyWhenBothParametersAreNull` |

## Verification

- `mvn -pl services/crypto test -Dtest=AddressPoisoningDetectorTest` — **27/27 passing** (19 from Phase
  10 + 8 from Phase 11).
- `mvn -pl services/crypto -am test` (full module regression) — **399 tests, 391 passing, 8 errors**, all
  pre-existing `IllegalState: … Docker environment …` errors (same baseline as T12/T13 Phase 10 — this
  task introduces no persistence layer), zero genuine failures, zero regressions.
- No production code changed in Phase 11 (test-only). `AddressPoisoningDetector.java` is unchanged since
  Phase 9's Javadoc addition.
- No spec file (`spec/`) modified at any point in this task.

## Verdict

**PASS.** All acceptance criteria (AC1-AC8) and both business requirements (R17, L9) are traced to a
concrete implementation and test. Scope boundaries (AC3, AC4) are satisfied structurally by omission —
confirmed by inspection, not merely asserted. No open findings remain from Phases 3, 8, or 11.
