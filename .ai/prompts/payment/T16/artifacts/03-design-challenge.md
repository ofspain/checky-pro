# payment · T16 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T16/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §O4, `spec/payment-service/agents.md` (L3, L6, L7, package-layout rules), `Invoice.java`, `Payment.java` (for field shapes).

---

## Executive summary

The brief is clean and internally consistent. Extracting `CanonicalJson` as a generic `common/` utility, keeping `ReceiptDigest`/`ReceiptPayload` in a new `receipt/` package, and converting values to canonical-safe types before serialization are all the right calls.

The main design challenge is defensive: `CanonicalJson` is advertised as reusable (T19's `LedgerService`), but the brief relies entirely on caller discipline to avoid passing types whose Jackson serialization is version-sensitive (`UUID`, `Instant`, `BigDecimal`, `Float`/`Double`, arrays, `Set`s). A runtime type-guard inside `CanonicalJson` closes that foot-gun without adding complexity. Otherwise the decisions are sound.

---

## Finding 1 (Medium) — `CanonicalJson` should reject unsupported value types rather than trust every caller

- **Issue:** The brief constrains `CanonicalJson.serialize(Map<String,Object>)` inputs to `String`, `Number`, `Boolean`, `null`, nested `Map<String,Object>`, or `List<Object>`, and says callers convert `UUID`/`Instant`/`BigDecimal` before calling. But `CanonicalJson` itself does not enforce this contract. If T19's `LedgerService` accidentally passes a `UUID`, `Instant`, `BigDecimal`, `Float`, `Double`, an array, or a `Set`, Jackson will serialize it in a way that may change across Jackson versions or depends on module registration, silently breaking digest reproducibility.
- **Impact:** Non-deterministic or version-sensitive digests for ledger entries (T19) or receipts, violating the entire purpose of canonicalization.
- **Evidence:**
  - `agents.md` L6 already bans `float`/`double`/`Float`/`Double` for money; `CanonicalJson` is not limited to money and should not silently accept them either.
  - `design.md` §O4 explicitly says the digest must be reproducible across services.
- **Recommendation:** Add a recursive type guard in `CanonicalJson.serialize` (and in the list/map recursion) that throws `IllegalArgumentException` for any value that is not one of: `String`, `Number` excluding `Float`/`Double`/`BigDecimal`/`BigInteger` (or include only the safe integer types), `Boolean`, `null`, `Map<String,Object>`, `List<Object>`. Document the supported types in the class Javadoc.
- **Confidence:** High.

---

## Finding 2 (Low) — The 12 O4 fields are not enumerated in the brief

- **Issue:** The brief references `design.md` §O4 for the exact receipt payload, but does not list the 12 fields or their Java types inside the brief itself. An implementer has to cross-reference the design doc for `ReceiptPayload`'s record components.
- **Impact:** Minor friction and a small risk of mis-typing one field (e.g., `tokenDecimals` as `String` instead of a number, or `amount` as a number instead of a string).
- **Recommendation:** Add an explicit table to the brief (or to Phase 5 implementation notes) listing each field and its `ReceiptPayload` type:
  | Field | Type | Canonical form |
  |---|---|---|
  | receiptUuid | UUID | String |
  | invoiceUuid | UUID | String |
  | paymentUuid | UUID | String |
  | merchantAccountUuid | UUID | String |
  | chain | String | String |
  | txHash | String | String |
  | tokenContractAddress | String | String |
  | tokenDecimals | short | JSON number |
  | amount | BigDecimal | decimal string |
  | payToAddress | String | String |
  | fromAddress | String | String or `null` |
  | finalizedAt | Instant | RFC3339 string |
- **Confidence:** Low.

---

## Finding 3 (Low) — No golden canonicalization test for the full receipt shape

- **Issue:** The required tests verify individual properties (key ordering, amount-as-string, tokenDecimals-as-number, null handling, nesting), but none constructs the full 12-field receipt map and asserts a known byte-for-byte canonical JSON string.
- **Impact:** A field-name typo or an unexpected Jackson quirk (e.g., escaping, numeric formatting) in the integrated receipt shape would only be caught later when T17's attestation tests fail against a real Crypto Service.
- **Recommendation:** Add a test that builds the exact 12-field map (in scrambled insertion order) and asserts the resulting canonical JSON string equals a hard-coded golden value. This also gives T17 a stable reference digest.
- **Confidence:** Low.

---

## Finding 4 (Low) — Unicode escaping assumption is implicit

- **Issue:** Jackson's default `ObjectMapper` escapes many non-ASCII characters as `\uXXXX`. For the receipt fields this is harmless (UUIDs, addresses, chain names, and RFC3339 timestamps are all ASCII), but `CanonicalJson` is described as reusable by T19, whose payload shape is not yet known.
- **Impact:** If a future ledger payload contains non-ASCII keys or values, the canonical byte stream may depend on Jackson's escaping defaults rather than raw UTF-8 bytes.
- **Recommendation:** Document in `CanonicalJson`'s Javadoc that it is intended for ASCII keys/values; if non-ASCII support becomes needed later, pin `JsonGenerator.Feature.ESCAPE_NON_ASCII` off explicitly and add a test. For T16, a comment is sufficient.
- **Confidence:** Low.

---

## Finding 5 (Low) — `ReceiptPayload` trusts the caller for non-null fields

- **Issue:** The brief follows `registerWatch`'s "trust the caller" stance and does not add null checks for the 11 non-null fields. A buggy T17 caller could construct a `ReceiptPayload` with a null `amount` or `finalizedAt`, and `ReceiptDigest` would serialize it as JSON `null`, producing a digest that is technically valid but semantically wrong.
- **Impact:** A malformed digest could be sent to `/attest` and later stored as a receipt.
- **Recommendation:** This is a defensible trade-off for T16 (the caller is responsible), but consider adding compact-constructor `Objects.requireNonNull` checks on every field except `fromAddress`. This makes invalid payloads fail at construction time rather than downstream. If the brief wants to keep the trust-caller stance, document it explicitly in `ReceiptPayload`'s Javadoc.
- **Confidence:** Low.

---

## Positive confirmations

- `CanonicalJson` belongs in `common/` as shared plumbing; `ReceiptDigest`/`ReceiptPayload` belong in a new `receipt/` feature package. This matches `agents.md` package-layout rules.
- `ReceiptPayload` is a plain record of primitives/value types with no `Invoice`/`Payment` import, so `ArchitectureTest`'s cross-module-entity rule is not violated and `FEATURE_MODULES` does not need widening.
- Converting `UUID`/`Instant`/`BigDecimal` to canonical-safe types inside `ReceiptDigest` is the right boundary; it keeps `CanonicalJson` free of Jackson modules and version-sensitive serializers.
- `tokenDecimals` as a bare JSON number and `amount` as a decimal string match O4 exactly and preserve L6.
- Null map values serialize as literal JSON `null` with Jackson's default behavior, satisfying "no key omitted."
- Recursive nested-map sorting with preserved list order is the correct generic design for T19 reuse.
- `SHA-256` + `HexFormat` is the right toolset and matches auth-service's precedent.
- Unit-only, no-Spring-context tests are appropriate for pure functions.

---

## Disposition summary

| Finding | Severity | Disposition |
|---|---|---|
| 1. Type guard in `CanonicalJson` | Medium | Add a recursive runtime check for unsupported types |
| 2. Enumerate O4 fields in brief | Low | Add a field/type/canonical-form table |
| 3. Golden full-receipt canonicalization test | Low | Add a byte-for-byte canonical JSON assertion |
| 4. Document Unicode/ASCII assumption | Low | Add a Javadoc comment |
| 5. `ReceiptPayload` null checks | Low | Decide explicitly and document; recommend adding `requireNonNull` |

---

## Recommended next step

Update the brief with the type-guard requirement and the O4 field table, then freeze and proceed to Phase 5 implementation. The type guard is the only change with real correctness implications; the rest are documentation/clarity improvements.
