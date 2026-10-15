# payment · T16 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

No code in this artifact — planning only.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/common/CanonicalJson.java`
2. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptPayload.java`
3. `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptDigest.java`

## Files to modify

None.

## Public methods (signatures)

**`CanonicalJson`** (final class, private constructor, static methods only):
```java
public static String serialize(Map<String, Object> payload)
```
Recursively validates and canonicalizes; throws `IllegalArgumentException` for any unsupported
value type at any nesting depth (Phase 3/4 Finding 1).

**`ReceiptPayload`** (record, `receipt` package, no entity import):
```java
public record ReceiptPayload(
        UUID receiptUuid, UUID invoiceUuid, UUID paymentUuid, UUID merchantAccountUuid,
        String chain, String txHash, String tokenContractAddress, short tokenDecimals,
        BigDecimal amount, String payToAddress, String fromAddress, Instant finalizedAt) {
    public ReceiptPayload {
        Objects.requireNonNull(receiptUuid, "receiptUuid must not be null");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        Objects.requireNonNull(paymentUuid, "paymentUuid must not be null");
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        Objects.requireNonNull(chain, "chain must not be null");
        Objects.requireNonNull(txHash, "txHash must not be null");
        Objects.requireNonNull(tokenContractAddress, "tokenContractAddress must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(payToAddress, "payToAddress must not be null");
        Objects.requireNonNull(finalizedAt, "finalizedAt must not be null");
        // fromAddress and tokenDecimals (primitive short) are deliberately not checked.
    }
}
```

**`ReceiptDigest`** (final class, private constructor, static methods only — a pure function,
no injected dependency, mirrors `CanonicalJson`'s own shape):
```java
public static String digest(ReceiptPayload payload)
```
Returns a 64-character lowercase hex `SHA-256` string.

## Private methods

- `CanonicalJson`: a private static recursive helper, e.g.
  `canonicalize(Object value)` → `Object` — converts any `Map<?,?>` value into a sorted
  `TreeMap<String,Object>` (recursing into each entry's own value), converts any `List<?>` value
  into a new `List<Object>` with each element recursively canonicalized (order preserved,
  values validated/converted, never sorted), and validates every scalar leaf is one of the
  allowed types (`String`/`Boolean`/`null`/`Short`/`Integer`/`Long`/`Byte`), throwing
  `IllegalArgumentException` otherwise. The top-level `serialize` method calls this on the
  input map, then hands the canonical structure to one internal, bare `ObjectMapper` (no
  `JavaTimeModule`, no custom UUID handling — none needed, since nothing reaching the writer is
  ever a `UUID`/`Instant`/`BigDecimal` by the time it's been through this helper) for the
  actual JSON-text production via its default (non-pretty-printing) writer.
- `ReceiptDigest`: a private static helper, e.g. `toCanonicalMap(ReceiptPayload payload)` →
  `Map<String,Object>` — builds the `LinkedHashMap`/`Map.of`-style map with all 12 O4 keys,
  converting `UUID`→`toString()`, `BigDecimal`→`toPlainString()`, `Instant`→`toString()`,
  `tokenDecimals`→boxed `Short` (kept as a number, not stringified — the one O4 asymmetry),
  `fromAddress` passed through as-is (already `String`, possibly `null`). A private helper
  keeps `digest(...)` itself a 2-line method: build the map, hash `CanonicalJson.serialize(map)`.
- `ReceiptDigest`: a private static `sha256(String canonicalJson)` → `String` helper —
  `MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex(...)` over the UTF-8 bytes,
  mirroring `auth-service`'s own `Hashing.sha256` shape (not imported — a new, independent
  implementation per `agents.md`'s "never depend on another service's source").

## Entities used

None. (`Invoice`/`Payment` are referenced only in documentation/Javadoc as the eventual source
of a `ReceiptPayload`'s own field values — never imported or constructed here.)

## Repositories used

None.

## Services used

None — both classes are static utilities with no Spring-managed dependency.

## Unit/integration tests required

All unit, no Spring context, no Testcontainers (both classes are pure functions):

**`CanonicalJsonTest`:**
1. `shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder` (AC1).
2. `shouldProduceNoPrettyPrintingWhitespace` (AC3).
3. `shouldIncludeNullValuedKeyAsLiteralJsonNull` (AC3).
4. `shouldSortNestedMapKeys` (AC1, recursion).
5. `shouldPreserveListElementOrderWithoutSorting` (AC1, recursion, negative case re: sorting).
6. `shouldRejectUuidValue`, `shouldRejectInstantValue`, `shouldRejectBigDecimalValue`,
   `shouldRejectFloatValue`, `shouldRejectDoubleValue` — each asserts
   `IllegalArgumentException` (AC7, Phase 3/4 Finding 1); at least one of these nested inside a
   `Map`/`List` value to prove the guard is genuinely recursive, not top-level-only.
7. `shouldAcceptIntegerLongShortByteNumberValues` — a positive-proof companion to item 6, so the
   type guard is proven to actually distinguish safe from unsafe `Number` subtypes, not just
   reject everything.

**`ReceiptDigestTest`:**
8. `shouldProduceSixtyFourCharacterLowercaseHexDigest` (AC4).
9. `shouldBeDeterministicForIdenticalPayload` (AC5).
10. `shouldChangeDigestWhenFromAddressChangesFromValueToNull` (AC5, exercises the real nullable
    field via the real payload type, not a synthetic map).
11. `shouldChangeDigestWhenAnySingleFieldChanges` — parameterized or one method per remaining
    field (at minimum `amount`, `chain`, `finalizedAt`, `tokenDecimals` — the two asymmetric
    fields plus two representative others) proving genuine sensitivity, not just the one
    nullable-field case above.
12. `shouldSerializeAmountAsDecimalStringNotJsonNumber` (AC2) — inspect the canonical JSON
    `ReceiptDigest`'s own internal map produces (via a package-private seam, or by asserting on
    `CanonicalJson.serialize` called with the same map `toCanonicalMap` would build — exact
    mechanism is an implementation-phase choice, not fixed here).
13. `shouldSerializeTokenDecimalsAsJsonNumberNotString` (AC2, the O4 asymmetry, same mechanism
    as item 12).
14. `shouldProduceGoldenCanonicalJsonForFullReceiptPayload` (Phase 3/4 Finding 3) — construct the
    exact O4 12-field shape with a fixed, hard-coded set of values, assert the resulting
    canonical JSON string equals a hard-coded expected literal, scrambling the *construction*
    order (not applicable to a record — all 12 constructor arguments are positional; the
    "scrambled order" property is already proven by `CanonicalJsonTest` item 1 at the
    `CanonicalJson` layer directly, so this test's own job is narrower: prove the full,
    realistic 12-field payload's own canonical output is exactly right, byte-for-byte).

## Execution order

1. `CanonicalJson` — no dependency on anything else in this plan.
2. `CanonicalJsonTest` — depends on 1; written immediately after to prove the type guard
   (Finding 1) actually fails before moving on, per this session's own established
   mutation-testing discipline.
3. `ReceiptPayload` — no dependency on `CanonicalJson` itself (a plain record).
4. `ReceiptDigest` — depends on 1 and 3.
5. `ReceiptDigestTest` — depends on 4.

Steps 1–2 and steps 3–5 have no dependency on each other and could be done in either order;
listed `CanonicalJson` first only because `ReceiptDigest` depends on it, not the reverse.

**Note on Phase 6 scope:** per the standard "write tests in Phase 10, not Phase 6" rule (this
task is NOT test-only), the actual test files above are written at Phase 10 — Phase 6 writes
only the three production files (1/3/4) and this plan's own private-method design. Listed here
in full regardless, per this phase's own instructions ("every planned file must trace to the
frozen brief's own Files sections" — the brief's Required Tests section already names these,
so Phase 10 has a complete, pre-agreed list to implement against, not a fresh design exercise).

## Traceability check

Every file above traces directly to the frozen brief's own "Files to Create" list (Phase 4) — no
file is added that the brief does not authorize. No file is modified (the brief's own "Files to
Modify" is empty) and no file in "Files NOT to Modify" is touched.
