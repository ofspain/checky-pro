# crypto · T12 · Phase 10 — Test Generation

No production code changed in this phase. One new test file:
`services/crypto/src/test/java/com/themistra/crypto/token/AddressValidatorTest.java` (20 tests). All
test vectors are the ones computed and verified by direct execution against the real, pinned libraries
in Phase 5, plus the additional short/empty Tron vectors and the corrected zero-address vector from
Phase 9's review resolution.

## Test manifest

| Test | Verifies |
|---|---|
| `shouldValidateEip55ChecksumForEvmAddresses` | package.md §8 named test — AC1 |
| `rejectsAnAllLowercaseEvmAddress` | AC3, Amendment #1 (strict EIP-55) |
| `rejectsAnAllUppercaseEvmAddress` | AC3, Amendment #1 |
| `rejectsAMixedCaseEvmAddressWithACorruptedChecksum` | AC4 — the single most important EIP-55 negative case (Phase 3 Kimi Issue 3) |
| `rejectsAnUppercaseZeroXPrefix` | AC2, Amendment #4 (Phase 3 Kimi Issue 4) |
| `rejectsAWhitespacePaddedEvmAddress` | AC2, Amendment #7 (Phase 3 Kimi Issue 7) |
| `rejectsAnEvmAddressOfTheWrongLength` | AC2 |
| `rejectsAnEvmAddressWithANonHexCharacter` | AC2 |
| `rejectsANullEvmAddress` | AC9 |
| `acceptsTheAllZeroEvmAddress` | Phase 9 correction (Kimi Phase 8 Issue 7's premise was factually wrong — confirmed by direct execution the zero address is accepted, not rejected) |
| `shouldValidateBase58ChecksumForTronAddresses` | package.md §8 named test — AC5 |
| `rejectsATronAddressWithAnIllegalCharacter` | AC6 |
| `rejectsATronAddressWithAWrongChecksum` | AC6 |
| `rejectsABase58CheckValidStringWithTheWrongPrefixByte` | AC7 |
| `rejectsAnOversizedTronAddress` | AC8, Amendment #5 |
| `rejectsANullTronAddress` | AC9 |
| `rejectsAnEmptyTronAddress` | Phase 9 fix (self-review Finding 1 / Kimi Phase 8 Issues 1, 3, 4) — locks in the `NegativeArraySizeException` fix |
| `rejectsAOneCharacterTronAddress` | Phase 9 fix |
| `rejectsAFourCharacterTronAddress` | Phase 9 fix — the exact boundary of the confirmed library bug |
| `rejectsATronAddressJustBelowTheMinimumLength` | `MIN_TRON_ADDRESS_LENGTH` boundary |

No integration test — no persistence, no Docker dependency, consistent with Phase 0's own finding.

## Test results

- `mvn -pl services/crypto test -Dtest=AddressValidatorTest` — **20/20 passing**.
- `mvn -pl services/crypto -am test` (full module regression) — **365 tests, 357 passing, 8 errors**,
  all `IllegalState: … Docker environment …` (the same 8 pre-existing errors as T11's own baseline —
  this task introduces no new persistence layer, so no new Docker-gated test exists), zero genuine
  failures, zero regressions in any previously-passing test.
