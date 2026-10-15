# payment · T16 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Add `CanonicalJson.serialize(Map<String,Object>)` in `common/` (sorted keys, UTF-8, no
insignificant whitespace, no key omitted) and `ReceiptDigest` in a new `receipt/` package,
computing the `SHA-256` hex digest of the canonical JSON over `design.md` O4's exact 12-field
receipt payload.

## Purpose

This is the mechanism `ReceiptDigest` needs to produce the exact bytes that get signed (R19,
T17's own later call to `CryptoClient.attest`) and the exact mechanism `LedgerService` (T19)
will reuse for its own, different canonical payload — the task statement's own explicit reason
for extracting `CanonicalJson` as a standalone `common/` utility rather than a private helper
buried inside `ReceiptDigest`.

## Scope

**In:** `CanonicalJson.serialize(...)`; `ReceiptDigest`'s own digest method over O4's field set;
a plain `ReceiptPayload` record (no entity reference) carrying those 12 fields.

**Out:** `Receipt`/`ReceiptRepository` (T17), `ReceiptService`/the full attest-and-decide flow
(T17), S3 storage (T18), `LedgerService`/`LedgerEntry` (T19 — `CanonicalJson` must be generic
enough for it to reuse, but T19 itself is not built here), the PDF rendering (T17/O4's own PDF
half).

## Business Rules

- **R19** (partial — digest computation only).

## Locked Decisions

- **L6**: `amount` enters the canonical payload as a decimal string (`BigDecimal.toPlainString()`),
  never a bare numeric JSON token.
- **L7** (context): `CanonicalJson` is the one utility this task and T19 both build on.
- **L12**: `CanonicalJson` lives in `common/`; `ReceiptDigest`/`ReceiptPayload` live in a new
  `receipt/` package, which — critically — **may not import `Invoice` or `Payment` directly**
  (both are `@Entity` classes in different feature modules; `ArchitectureTest`'s own
  `shouldPreventCrossModuleEntityImports` rule already bans exactly this). `ReceiptPayload` is
  therefore a plain record of primitive/value types only; T17's own `ReceiptService` (also in
  `receipt/`, out of this task's scope) is responsible for reading the needed fields off
  `Invoice`/`Payment` via their own getters and constructing a `ReceiptPayload` from the result —
  the same "read primitives across a module boundary, never pass the entity" pattern already
  established by `PaymentService.findInvoiceIdByChainAndTxHash` (T13).

## Dependencies

- `java.security.MessageDigest`/`java.util.HexFormat` (mirrors `auth-service`'s own `Hashing`
  shape, not imported directly).
- A bare `com.fasterxml.jackson.databind.ObjectMapper` instance owned by `CanonicalJson` itself
  (not Spring's autoconfigured bean — `CanonicalJson` is a static utility with no Spring context
  dependency, consistent with the Phase 0 testing-convention finding). **Deliberately no
  `JavaTimeModule`/UUID-specific Jackson configuration is registered at all** — see Outputs below
  for why.

## Inputs

`CanonicalJson.serialize(Map<String,Object>)` — the map's values are constrained by this task's
own design decision (below) to exactly `String`, `Number` (boxed primitive), `Boolean`, `null`,
nested `Map<String,Object>`, or `List<Object>` — never a `UUID`, `Instant`, or `BigDecimal`
directly. `ReceiptDigest`'s own public method takes a `ReceiptPayload` record (12 fields, typed
`UUID`/`String`/`short`/`BigDecimal`/`Instant` as naturally fits each field — type safety for
*callers*, converted to the canonical-safe types above only at the boundary where
`ReceiptDigest` builds the map it hands to `CanonicalJson`).

## Outputs

- **`CanonicalJson.serialize(...)` returns a `String`** — the canonical JSON text, ready for
  `.getBytes(StandardCharsets.UTF_8)`.
- **Null-value decision (resolves Phase 1 Open Question 1):** a `null` value is represented as a
  literal JSON `null`, with its key still present — the natural, literal reading of "no key
  omitted," and also Jackson's own default behavior for a `Map` value (a default-configured
  `ObjectMapper` does NOT omit null map entries; only an explicit `@JsonInclude(NON_NULL)`-style
  setting would, and none is added here) — the simplest implementation and the correct one agree.
- **Recursive nesting decision (resolves Phase 1 Open Question 2):** `CanonicalJson` DOES
  recursively canonicalize nested `Map`/`List` values now, not deferred — sorting a nested map's
  keys is a few extra lines over a flat-only implementation (recurse, converting each nested
  `Map` into a `TreeMap` before handing the whole structure to Jackson's writer; `List` elements
  are canonicalized in place, each element recursively processed if it is itself a `Map`, but the
  list's own element *order* is preserved — only map keys are sorted, never list order). Given
  this utility is explicitly declared shared/reusable (the task statement's own stated reason for
  extracting it) and `LedgerService`'s (T19) own payload shape is not yet pinned, building the
  genuinely generic version now avoids a forced rewrite later for a trivial amount of extra code.
- **Type-conversion boundary decision (new, not from Phase 1 — found while designing this
  phase):** `ReceiptDigest` converts every `ReceiptPayload` field to a `CanonicalJson`-safe type
  before building the map — `UUID.toString()`, `Instant.toString()` (produces RFC3339/ISO-8601,
  e.g. `2026-07-13T01:00:00Z`), `BigDecimal.toPlainString()` for `amount` — **except
  `tokenDecimals`, which O4's own pinned JSON example shows as a bare integer (`"tokenDecimals":
  6`, no quotes), unlike every other field, which O4 shows quoted.** This is a deliberate,
  easy-to-miss asymmetry in O4's own text: `tokenDecimals` enters the map as a boxed `Short`
  (or `Integer`), not a `String`, so it serializes as a JSON number — correct per O4, and not an
  L6 violation (L6 governs *money*-carrying fields; `tokenDecimals` is a small integer count, not
  an amount). This boundary conversion is why `CanonicalJson` itself never needs `UUID`/
  `Instant`/`BigDecimal`-specific Jackson modules: by the time anything reaches
  `CanonicalJson.serialize`, every value is already one of the plain canonical-safe types — a
  deliberate robustness choice, so a digest's own byte-for-byte reproducibility never depends on
  Jackson's own date/UUID-module defaults (which could change across a Jackson version bump).

## State Changes

None. Both new classes are pure functions; no persistence, no entity, no migration.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/CanonicalJson.java`.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptPayload.java` (plain
  record, no entity import).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptDigest.java`.

## Files to Modify

None — both new classes have no existing caller yet (T17 is the first real caller, out of
scope here).

## Files NOT to Modify

`Invoice.java`, `Payment.java` (read-only reference for field shapes, never edited),
`ArchitectureTest.java` (this task adds a new top-level package, `receipt` — but adds no entity,
so `FEATURE_MODULES` does not need widening; confirmed: `ReceiptPayload`/`ReceiptDigest` are
plain classes, not `@Entity`s, so `shouldPreventCrossModuleEntityImports`'s own fail-fast check
is not triggered by this task at all), anything outside `services/payment`.

## Acceptance Criteria

- **AC1.** `CanonicalJson.serialize` is deterministic under key-insertion-order permutation.
- **AC2.** `amount` and every other explicitly-string-typed O4 field serializes as a JSON
  string; `tokenDecimals` serializes as a bare JSON number (O4's own one deliberate asymmetry).
- **AC3.** No pretty-printing whitespace; every key present, including a `null`-valued one, with
  a literal JSON `null`.
- **AC4.** `ReceiptDigest` returns a 64-character lowercase hex `SHA-256` digest over exactly
  O4's 12-field shape, with `receiptUuid` supplied by the caller (no entity source exists).
- **AC5.** `ReceiptDigest` is deterministic and sensitive to every field (changing any one field
  changes the digest).
- **AC6 (new, module boundary).** Neither `ReceiptPayload` nor `ReceiptDigest` imports `Invoice`
  or `Payment`.

## Required Tests

Unit only, no Spring context (mirrors `CryptoClientTest`'s own precedent for a similarly pure
class):
1. Key-order-independence (AC1).
2. `amount`-as-string / `tokenDecimals`-as-number (AC2).
3. No pretty-printing whitespace (AC3).
4. `null` value present as literal `null`, key not omitted (AC3).
5. Nested `Map` keys are sorted (the recursive-canonicalization design decision, AC1 extended).
6. `List` element order is preserved, not sorted (same decision, negative case).
7. `ReceiptDigest` produces a 64-char lowercase hex string (AC4).
8. `ReceiptDigest` is deterministic across two calls with identical `ReceiptPayload` values (AC5).
9. `ReceiptDigest` changes if any single `ReceiptPayload` field changes — at minimum
   `fromAddress` going from a value to `null` (AC5, also exercises the null-handling path from a
   real field, not a synthetic test map).

## Constraints

- **Transaction:** none — both classes are pure functions, never called inside a transaction
  themselves (any transactional behavior belongs to T17's own caller).
- **Thread-safety:** both classes are stateless; `CanonicalJson`'s own `ObjectMapper` instance is
  thread-safe by Jackson's own documented contract once configured and not mutated after
  construction.
- **Module boundaries:** `ReceiptPayload`/`ReceiptDigest` take no entity parameter (AC6 above).
- **Null handling:** `ReceiptPayload.fromAddress` may legitimately be `null` (O4's own explicit
  `"<address|null>"`); every other field is non-null by construction (the caller, T17, only
  constructs a `ReceiptPayload` once it has a real `FINALIZED` payment with all other fields
  populated) — no defensive null-check needed on the other 11 fields within this task's own
  scope, mirrring `registerWatch`'s own "trust the caller" stance.
- **Security:** no secret, no PII beyond what's already part of the payment/invoice record
  itself; nothing new logged.
- **Performance:** not a concern — a 12-field flat map, computed once per receipt, far from any
  realistic hot path.

## Open Questions

No blockers. Both Phase 1 questions are resolved directly above (Outputs) with reasoning
recorded for Phase 3 to stress-test.
