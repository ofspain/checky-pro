# payment · T16 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T16/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `CanonicalJson.java`, `ReceiptPayload.java`, `ReceiptDigest.java`, `spec/payment-service/design.md` §O4.

---

## Executive summary

- T16 production code is implemented and compiles. `CanonicalJson`, `ReceiptPayload`, and `ReceiptDigest` all match the approved design: sorted keys, recursive type guard, `amount` as string, `tokenDecimals` as number, no entity imports.
- The full suite is **316/316 green**.
- The two self-review findings are both real and should be fixed: `serialize(null)` silently returns the JSON literal `"null"`, and non-`String` nested-map keys throw `ClassCastException` instead of the class's own `IllegalArgumentException`.
- No T16-specific tests exist yet; they are deferred to Phase 10 per the task pipeline.

---

## Confirming self-review Finding 1 (Low) — `serialize(null)` returns `"null"`

- **Independent assessment:** Confirmed. `CanonicalJson.serialize(null)` calls `canonicalize(null)`, which returns `null`, and `OBJECT_MAPPER.writeValueAsString(null)` returns the four-character string `"null"`. This is almost certainly not what a caller wants.
- **Impact:** A buggy caller that passes a null payload gets a digest over `"null"` instead of an exception, violating the "fail clearly on contract violation" goal.
- **Recommendation:** Add `Objects.requireNonNull(payload, "payload must not be null")` at the start of `serialize`, before `canonicalize` is called.
- **Confidence:** High.

---

## Confirming self-review Finding 2 (Low) — Non-`String` nested-map keys throw `ClassCastException`

- **Independent assessment:** Confirmed. The `Map` branch casts `entry.getKey()` directly to `String`. The top-level signature is type-safe, but nested maps are `Object` at runtime, so `Map<Integer, Object>` would throw `ClassCastException`.
- **Impact:** Inconsistent exception type for one specific contract violation; callers expecting `IllegalArgumentException` from the type guard would see a different exception.
- **Recommendation:** Replace the cast with an explicit `instanceof String` check and throw `IllegalArgumentException` with a message naming the actual key type.
- **Confidence:** High.

---

## New Finding 1 (Low) — `serialize` does not reject a map with a `null` key

- **Issue:** `canonicalize` constructs `new TreeMap<>()` from the input map. `TreeMap` does not permit `null` keys and throws `NullPointerException` at insertion time. This is another case where the exception type is inconsistent with the documented `IllegalArgumentException` contract.
- **Impact:** A caller passing a map with a `null` key gets an NPE rather than a clear contract-violation exception.
- **Recommendation:** Validate keys for non-null before inserting into the `TreeMap`, throwing `IllegalArgumentException` if a null key is found. This can be combined with the `instanceof String` check from self-review Finding 2.
- **Confidence:** Low.

---

## New Finding 2 (Low) — Javadoc claim about non-ASCII escaping may be inaccurate

- **Issue:** The `CanonicalJson` Javadoc states that Jackson's default `ObjectMapper` "escapes non-ASCII characters as backslash-u-prefixed hex sequences." Jackson's default behavior is actually to emit raw UTF-8 for non-ASCII characters unless `JsonGenerator.Feature.ESCAPE_NON_ASCII` is enabled. The documented behavior would only occur if that feature were explicitly turned on, which it is not.
- **Impact:** The Javadoc is misleading. In practice this is harmless because every real payload field is ASCII-only.
- **Recommendation:** Reword the Javadoc to say "This class is intended for ASCII-only keys and values; non-ASCII handling follows Jackson's default UTF-8 output, and raw UTF-8 canonicalization would require an explicit `ESCAPE_NON_ASCII` decision if ever needed."
- **Confidence:** Low.

---

## New Finding 3 (Low) — `ReceiptDigest.digest(null)` is not guarded

- **Issue:** `ReceiptDigest.digest(null)` dereferences `payload.receiptUuid()` in `toCanonicalMap`, producing a plain `NullPointerException`. While the brief's trust-the-caller stance is consistent with `CryptoClient`/`ReceiptPayload`, the `CanonicalJson` utility itself is now guarded; having the adjacent `ReceiptDigest` method throw a raw NPE is a small inconsistency.
- **Impact:** Low — a null payload is a caller bug, and it will fail; the only question is the exception type.
- **Recommendation:** Add `Objects.requireNonNull(payload, "payload must not be null")` at the top of `ReceiptDigest.digest`, or document that null is not allowed. This mirrors the proposed fix for `CanonicalJson.serialize(null)`.
- **Confidence:** Low.

---

## New Finding 4 (Low) — No T16-specific tests yet

- **Issue:** There are no test classes for `CanonicalJson` or `ReceiptDigest`. The implementation notes correctly note that tests are deferred to Phase 10 per the standard pipeline rule, but this means the two findings above and the core digest behavior are currently unverified in CI.
- **Impact:** No regression protection for T16 logic yet.
- **Recommendation:** Proceed to Phase 10 and add the tests listed in the frozen brief plus coverage for the two self-review findings (null top-level payload, non-String nested key, null nested key).
- **Confidence:** Medium (not a code defect, but a coverage gap).

---

## Positive confirmations

- `CanonicalJson` is a stateless static utility in `common/`, matching the shared-plumbing rule.
- `ReceiptPayload` and `ReceiptDigest` live in the new `receipt/` package and import no entities from `invoice` or `payment`.
- The recursive type guard rejects `UUID`, `Instant`, `BigDecimal`, `Float`, `Double`, `BigInteger`, arrays, and other unsupported types with `IllegalArgumentException`.
- `TreeMap` provides locale-independent lexicographic key sorting.
- `amount` is converted to a decimal string via `BigDecimal.toPlainString()`; `tokenDecimals` is passed as a boxed `Short` and serializes as a JSON number, matching O4.
- `ReceiptPayload`'s compact constructor checks every field except `fromAddress` for null, matching O4's explicit nullability.
- `ReceiptDigest` uses `MessageDigest.getInstance("SHA-256")` and `HexFormat.of().formatHex(...)`, producing lowercase hex.
- `CanonicalJson` output has no pretty-printing whitespace and preserves null values as literal JSON `null`.
- Full suite passes: **316/316**.

---

## Build context

- `mvn -pl services/payment -am test`: **316/316 passing**.
- No new T16-specific tests are present yet (deferred to Phase 10).

---

## Recommended follow-up order

1. Add `Objects.requireNonNull(payload)` to `CanonicalJson.serialize` (self-review Finding 1).
2. Add explicit key validation (`instanceof String`, non-null) in `CanonicalJson.canonicalize` (self-review Finding 2 + New Finding 1).
3. Correct the non-ASCII escaping Javadoc (New Finding 2).
4. Add `Objects.requireNonNull(payload)` to `ReceiptDigest.digest` or document the null contract (New Finding 3).
5. In Phase 10, write the deferred tests, including cases for null payload, non-String nested key, and null nested key.
