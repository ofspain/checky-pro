# crypto · T13 · Phase 10 — Test Generation

No production code changed in this phase. One new test file:
`services/crypto/src/test/java/com/themistra/crypto/token/AddressPoisoningDetectorTest.java` (19
tests) — all 13 tests from the frozen brief's Required Tests section, plus the 4 additional tests noted
in Phase 9's review resolution.

## Test manifest

| Test | Verifies |
|---|---|
| `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` | package.md §8 named test — AC1 |
| `doesNotFlagAnUnrelatedCandidate` | AC2 |
| `doesNotFlagAnExactMatch` | AC3 |
| `returnsEmptyForANullPreviouslySeenAddressesCollection` | AC4 |
| `returnsEmptyForAnEmptyPreviouslySeenAddressesCollection` | AC4 |
| `returnsEmptyForANullCandidateAddress` | AC4 |
| `skipsANullElementInThePreviouslySeenAddressesCollection` | AC4 |
| `flagsAPrefixOnlyMatch` | AC1 |
| `flagsASuffixOnlyMatch` | AC1 |
| `flagsWhenBothPrefixAndSuffixMatch` | AC1 (Phase 9, Kimi Phase 8 Issue 6) |
| `flagsACaseDifferingAddressAsResemblingNotExactlyMatching` | AC5 |
| `doesNotFlagAtFiveMatchingLeadingCharactersButFlagsAtSix` | AC1 boundary |
| `doesNotFlagAtThreeMatchingTrailingCharactersButFlagsAtFour` | AC1 boundary |
| `doesNotFlagOrThrowForAShortCandidateAddress` | AC7 |
| `doesNotFlagOrThrowForAShortPreviouslySeenAddress` | AC7 |
| `doesNotFlagOrThrowForAnEmptyCandidateAddress` | AC7 (Phase 9, Kimi Phase 8 Issue 4) |
| `returnsEmptyWhenAllPreviouslySeenAddressesAreTooShortToMatch` | AC7 (Phase 9, Kimi Phase 8 Issue 7) |
| `returnsOneOfMultipleMatchingPreviouslySeenAddresses` | AC6 (Phase 9, Kimi Phase 8 Issue 5) |
| `doesNotFlagTwoEvmAddressesSharingOnlyTheZeroXPrefixAndTwoHexDigits` | AC8 — the Amendment #1 regression guard |

No integration test — no persistence, no Docker dependency, consistent with `AddressValidator`'s (T12)
own precedent.

## Test results

- `mvn -pl services/crypto test -Dtest=AddressPoisoningDetectorTest` — **19/19 passing**, including
  manual confirmation that the AC8 regression guard (candidate/previous sharing only `0x` + 2 hex
  digits) correctly returns empty under the raised 6-character prefix threshold.
- `mvn -pl services/crypto -am test` (full module regression) — **391 tests, 383 passing, 8 errors**,
  all `IllegalState: … Docker environment …` (the same pre-existing errors as T12's own baseline — this
  task introduces no persistence layer, so no new Docker-gated test exists), zero genuine failures,
  zero regressions in any previously-passing test.
