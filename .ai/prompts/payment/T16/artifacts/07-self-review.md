# payment · T16 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Findings only — no fixes applied here (Phase 9's job).

---

## Finding 1 (Low) — `serialize(null)` silently produces the string `"null"` instead of failing

- **Issue:** `serialize`'s own top-level call is `canonicalize(payload)`. If `payload` itself is
  `null` (not a `null` *value* inside a well-formed map, but the whole map reference), the
  `value == null` branch returns `null` unchanged, and `OBJECT_MAPPER.writeValueAsString(null)`
  happily produces the 4-character JSON literal `null` as a `String` — no exception.
- **Evidence:** `CanonicalJson.java:48-54` (`serialize`), `:57-61` (the `canonicalize` null
  branch).
- **Impact:** A caller bug that passes `null` for the entire payload (as opposed to a map with a
  `null` value, which is the deliberately-supported case) would silently produce a "valid-looking"
  4-byte digest input instead of failing loudly — directly against the spirit of Finding 1's own
  design goal (Phase 3/4): don't trust callers, fail clearly on a contract violation.
- **Recommendation:** Add an explicit `Objects.requireNonNull(payload, "payload must not be
  null")` at the top of `serialize` itself, before calling `canonicalize`.

---

## Finding 2 (Low) — a nested `Map` with a non-`String` key throws `ClassCastException`, not `IllegalArgumentException`

- **Issue:** `canonicalize`'s `Map` branch does `(String) entry.getKey()`. The top-level
  `serialize(Map<String, Object> payload)` signature is compiler-checked to have `String` keys,
  but a *nested* map value is typed `Object` at runtime (generics are erased), so nothing
  prevents a caller from putting a `Map<Integer, Object>` as a nested value. That cast would
  throw a plain `ClassCastException`, not the clear, documented `IllegalArgumentException` every
  other unsupported-type case in this class produces (Phase 3/4 Finding 1's own explicit design
  goal).
- **Evidence:** `CanonicalJson.java:62-66` (the `Map` branch's own key cast).
- **Impact:** Low — no real caller in this codebase today constructs a non-`String`-keyed nested
  map (confirmed: `ReceiptDigest.toCanonicalMap` only ever builds flat `String`-keyed maps). But
  this class is explicitly declared reusable (T19), and an inconsistent exception type for one
  specific flavor of "caller passed an unsupported shape" is a minor but real inconsistency in
  an otherwise carefully-guarded contract.
- **Recommendation:** Validate `entry.getKey() instanceof String` explicitly before the cast,
  throwing the same `IllegalArgumentException` style as every other rejected-type case, naming
  the actual key's own type in the message.

---

## Positive confirmations

- The recursive type guard (Phase 3/4 Finding 1) correctly rejects `UUID`/`Instant`/
  `BigDecimal`/`Float`/`Double`/`BigInteger` at any nesting depth — confirmed by re-reading the
  `canonicalize` method's own exhaustive `if`/`throw` structure; there is no code path that lets
  an unrecognized type reach the `ObjectMapper` unchecked.
- `TreeMap`'s own natural `String` ordering (not a `Collator`) is locale-independent —
  confirmed no risk of a locale-dependent sort order silently changing the canonical output
  across environments.
- `ReceiptPayload`'s compact constructor correctly leaves `fromAddress` unchecked and checks
  every other field — matches O4's own explicit nullability for exactly that one field.
- `ReceiptDigest.toCanonicalMap`'s `tokenDecimals` handling (boxed `Short`, not stringified) is
  correct and matches O4's own one deliberate asymmetry.
- No monetary value is ever handed to `CanonicalJson` as anything other than a `String`
  (`BigDecimal.toPlainString()`) — L6 compliance confirmed at the one call site that matters.
- `ObjectMapper` is never reconfigured after construction — thread-safety holds as documented.
- No secret, no PII beyond what the payment/invoice record already carries is touched or logged.
