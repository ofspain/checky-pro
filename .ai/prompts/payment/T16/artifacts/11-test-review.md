# payment · T16 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T16/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `CanonicalJsonTest.java`, `ReceiptDigestTest.java`.

---

## Executive summary

- Both new test classes are present and pass: `CanonicalJsonTest` **14/14**, `ReceiptDigestTest` **11/11**.
- Full payment suite is **341/341 green**.
- The test set thoroughly covers canonicalization determinism, whitespace/null handling, nested sorting, list-order preservation, the recursive type guard, the 12-field receipt payload, and the two asymmetric O4 fields (`amount` as string, `tokenDecimals` as number).
- The main remaining gap is that the `ReceiptDigest` canonical-JSON assertions go through a test helper that mirrors `ReceiptDigest.toCanonicalMap`, so they do not directly guard against that private method drifting. A golden digest assertion would close that loop.

---

## Test manifest verification

### `CanonicalJsonTest` (14 methods)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder` | ✅ | AC1 — key-order determinism |
| `shouldProduceNoPrettyPrintingWhitespace` | ✅ | AC3 — compact output |
| `shouldIncludeNullValuedKeyAsLiteralJsonNull` | ✅ | AC3 — null value preserved |
| `shouldSortNestedMapKeys` | ✅ | AC1 — recursive map sorting |
| `shouldPreserveListElementOrderWithoutSorting` | ✅ | AC1 — list order not sorted |
| `shouldRejectUuidValue` | ✅ | AC7 — type guard rejects UUID |
| `shouldRejectInstantValue` | ✅ | AC7 — type guard rejects Instant |
| `shouldRejectBigDecimalValue` | ✅ | AC7 — type guard rejects BigDecimal |
| `shouldRejectFloatValue` | ✅ | AC7 — type guard rejects Float |
| `shouldRejectDoubleValueNestedInsideAList` | ✅ | AC7 — recursive guard |
| `shouldAcceptIntegerLongShortByteNumberValues` | ✅ | AC7 — allowed Number subtypes |
| `shouldRejectNullPayload` | ✅ | Phase 9 fix — null top-level payload |
| `shouldRejectNonStringNestedMapKey` | ✅ | Phase 9 fix — non-String nested key |
| `shouldRejectNullNestedMapKey` | ✅ | Phase 9 fix — null nested key |

### `ReceiptDigestTest` (11 methods)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldProduceSixtyFourCharacterLowercaseHexDigest` | ✅ | AC4 — digest format |
| `shouldBeDeterministicForIdenticalPayload` | ✅ | AC5 — determinism |
| `shouldChangeDigestWhenFromAddressChangesFromValueToNull` | ✅ | AC5 — nullable field sensitivity |
| `shouldChangeDigestWhenAmountChanges` | ✅ | AC5 — amount sensitivity |
| `shouldChangeDigestWhenChainChanges` | ✅ | AC5 — chain sensitivity |
| `shouldChangeDigestWhenFinalizedAtChanges` | ✅ | AC5 — finalizedAt sensitivity |
| `shouldChangeDigestWhenTokenDecimalsChanges` | ✅ | AC5 — tokenDecimals sensitivity |
| `shouldSerializeAmountAsDecimalStringNotJsonNumber` | ✅ | AC2 — amount as string |
| `shouldSerializeTokenDecimalsAsJsonNumberNotString` | ✅ | AC2 — tokenDecimals as number |
| `shouldRejectNullPayload` | ✅ | Phase 9 fix — null payload guard |
| `shouldProduceGoldenCanonicalJsonForFullReceiptPayload` | ✅ | Phase 3/4 Finding 3 — full 12-field reference |

---

## Gap 1 (Medium) — `ReceiptDigest` canonical-JSON assertions are indirect

- **Why it matters:** `shouldSerializeAmountAsDecimalStringNotJsonNumber`, `shouldSerializeTokenDecimalsAsJsonNumberNotString`, and `shouldProduceGoldenCanonicalJsonForFullReceiptPayload` all use the private test helper `canonicalJsonFor`, which manually rebuilds the same map that `ReceiptDigest.toCanonicalMap` builds. If `toCanonicalMap` drifts (e.g., a future refactor changes field order, formatting, or accidentally drops a field), the helper would also need to change for the test to catch it — the tests are not reading the actual canonical JSON that `ReceiptDigest` feeds into `SHA-256`.
- **Impact:** A regression in `ReceiptDigest.toCanonicalMap` could go undetected as long as the helper is updated in lock-step. The digest-sensitivity tests would still pass because they compare digests of different payloads, not a known digest value.
- **Suggested test:** Add a golden digest test:
  1. Compute `ReceiptDigest.digest(samplePayload())`.
  2. Independently compute `SHA-256(goldenCanonicalJson.getBytes(UTF_8))` using the same golden JSON string already asserted in `shouldProduceGoldenCanonicalJsonForFullReceiptPayload`.
  3. Assert the two hex strings are equal.
  This ties `ReceiptDigest`'s private map construction directly to the canonical JSON reference without exposing `toCanonicalMap`.
- **Confidence:** Medium.

---

## Gap 2 (Low) — No exact digest value is pinned

- **Why it matters:** Related to Gap 1. Even without a helper-drift concern, an exact known digest is a strong regression guard. A digest is the actual contract with the Crypto Service (`receiptDigestSha256`); the canonical JSON is an intermediate artifact.
- **Suggested test:** Add `shouldProduceExpectedDigestForSamplePayload` asserting `ReceiptDigest.digest(samplePayload()).equals("<known-hex>")`, where the known value is derived from the golden canonical JSON. This can be combined with Gap 1's recommendation.
- **Confidence:** Low.

---

## Gap 3 (Low) — `ReceiptDigest` sensitivity tests do not assert *direction* of change

- **Why it matters:** Every sensitivity test asserts `isNotEqualTo`, which proves the digest changes but not whether it changes in a way that could accidentally collide with another payload. SHA-256 collisions are practically impossible, so this is not a real correctness issue.
- **Suggested test:** None required; `isNotEqualTo` is sufficient for the stated AC.
- **Confidence:** Low.

---

## Gap 4 (Low) — `CanonicalJsonTest` does not exercise nested map inside a list

- **Why it matters:** The recursive canonicalization decision covers maps inside maps (`shouldSortNestedMapKeys`) and scalars inside lists (`shouldPreserveListElementOrderWithoutSorting`), but not a map inside a list. This is the only remaining combination in the supported type grammar.
- **Suggested test:** Add `shouldSortMapKeysInsideAList`:
  ```java
  payload.put("items", List.of(Map.of("z", 1, "a", 2)));
  // assert output contains {"items":[{"a":2,"z":1}]}
  ```
- **Confidence:** Low.

---

## Gap 5 (Low) — `CanonicalJsonTest` does not exercise `Boolean` or empty collections

- **Why it matters:** `Boolean` is an allowed type; empty maps and empty lists are valid JSON values. The current tests never use them.
- **Suggested test:** Add a single test covering `true`, `false`, empty map, and empty list serialization.
- **Confidence:** Low.

---

## Positive confirmations

- The recursive type guard is tested both positively (all four allowed integer Number subtypes) and negatively (UUID, Instant, BigDecimal, Float, nested Double).
- The null-payload and invalid-nested-key fixes from Phase 9 each have dedicated tests.
- The golden canonical JSON test for the full 12-field receipt payload captures the real lexicographic order (including the non-obvious `payToAddress` before `paymentUuid` due to uppercase `A`).
- `amount` and `tokenDecimals` serialization are asserted separately, directly addressing O4's deliberate asymmetry.
- The `fromAddress` null-sensitivity test is the one real nullable-field case from O4.
- Tests are unit-only with no Spring context, matching the pure-function nature of the code.
- `ReceiptPayload`'s compact-constructor null guards are exercised implicitly by every successful construction.
- Full suite passes: **341/341**.

---

## Build context

- `CanonicalJsonTest`: **14/14 passing**.
- `ReceiptDigestTest`: **11/11 passing**.
- Full `mvn -pl services/payment -am test`: **341/341 passing**.

---

## Recommended follow-up order

1. Add a golden digest test that ties `ReceiptDigest.digest` directly to the golden canonical JSON (Gaps 1 and 2).
2. Optionally add `shouldSortMapKeysInsideAList` (Gap 4).
3. Optionally add a test for `Boolean` and empty collections (Gap 5).
