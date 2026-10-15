# payment · T16 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test manifest (`CanonicalJsonTest`, new class — 14 methods)

| Test | Verifies |
|---|---|
| `shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder` | AC1 — the core determinism property |
| `shouldProduceNoPrettyPrintingWhitespace` | AC3 |
| `shouldIncludeNullValuedKeyAsLiteralJsonNull` | AC3 — the Phase 2 null-representation decision |
| `shouldSortNestedMapKeys` | AC1 extended — recursive canonicalization |
| `shouldPreserveListElementOrderWithoutSorting` | AC1 extended — the negative case (lists never sort) |
| `shouldRejectUuidValue` / `shouldRejectInstantValue` / `shouldRejectBigDecimalValue` / `shouldRejectFloatValue` | AC7 — the Phase 3/4 type guard, top-level |
| `shouldRejectDoubleValueNestedInsideAList` | AC7 — proves the guard is genuinely recursive, not top-level-only |
| `shouldAcceptIntegerLongShortByteNumberValues` | AC7's positive-proof companion — the guard distinguishes safe from unsafe `Number` subtypes |
| `shouldRejectNullPayload` | Phase 9 fix (self-review Finding 1 / Kimi confirmed) |
| `shouldRejectNonStringNestedMapKey` / `shouldRejectNullNestedMapKey` | Phase 9 fix (self-review Finding 2, Kimi New Finding 1) |

## Test manifest (`ReceiptDigestTest`, new class — 11 methods)

| Test | Verifies |
|---|---|
| `shouldProduceSixtyFourCharacterLowercaseHexDigest` | AC4 |
| `shouldBeDeterministicForIdenticalPayload` | AC5 |
| `shouldChangeDigestWhenFromAddressChangesFromValueToNull` | AC5 — the one real nullable field |
| `shouldChangeDigestWhenAmountChanges` / `...ChainChanges` / `...FinalizedAtChanges` / `...TokenDecimalsChanges` | AC5 — field sensitivity across representative fields, including both O4 asymmetric fields |
| `shouldSerializeAmountAsDecimalStringNotJsonNumber` | AC2 |
| `shouldSerializeTokenDecimalsAsJsonNumberNotString` | AC2 — the O4 asymmetry |
| `shouldRejectNullPayload` | Phase 9 fix (Kimi New Finding 3) |
| `shouldProduceGoldenCanonicalJsonForFullReceiptPayload` | Phase 3/4 Finding 3 — the full 12-field byte-for-byte reference |

**Golden test derivation note:** the expected canonical JSON string in
`shouldProduceGoldenCanonicalJsonForFullReceiptPayload` was NOT hand-derived by manually
computing the 12-key lexicographic sort order — `String.compareTo`'s case-sensitive ordering
(uppercase code points sort before lowercase) makes `payToAddress` sort before `paymentUuid`,
an easy point to get wrong by hand. Verified empirically instead: ran the test once with a
first-draft expected string, read the real, correct output from the resulting assertion
failure's own `but was:` line, and used that confirmed-correct value as the final golden
string — standard, safer practice for a snapshot/golden test than hand-deriving the expected
value.

## Why no additional tests are needed

- **AC1–AC7 (`CanonicalJson`)**: every acceptance criterion has at least one direct test; the
  recursive type guard is proven both to reject (6 cases, one nested) and to accept (one
  positive-proof case covering all four allowed `Number` subtypes at once).
- **AC2/AC4/AC5 (`ReceiptDigest`)**: determinism, sensitivity (covering both of O4's asymmetric
  fields — `amount` as string, `tokenDecimals` as number — plus three further representative
  fields), and the golden full-payload reference are all covered.
- **Module boundary (AC6)**: not a runtime-testable property — verified by direct code
  inspection at Phase 6/9 (no `Invoice`/`Payment` import in either new file) and by this task
  having no entity to trip `ArchitectureTest`'s own cross-module rule in the first place; no new
  test needed beyond the existing `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`
  canary, unaffected by this task.

## Verification

`mvn -o -pl services/payment -am test-compile`: clean. `mvn -o -pl services/payment -am test
-Dtest=CanonicalJsonTest,ReceiptDigestTest`: **25/25 passing** (14 + 11). Mutation-tested the
core type guard (temporarily replaced the final `throw new IllegalArgumentException(...)` with
`return value.toString()`): all 5 `shouldReject*` tests failed red as expected; reverted,
confirmed clean diff, confirmed green again. Full suite `mvn -o -pl services/payment -am test`:
**341/341 passing** (316 pre-task baseline + 25 new).
