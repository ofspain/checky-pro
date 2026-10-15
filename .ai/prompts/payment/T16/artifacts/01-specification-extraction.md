# payment · T16 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R19** (partial — the digest-computation mechanism only; obtaining the signature and acting
  on the outcome is T17's own scope, same split as T15's own `attest` method). WHEN issuing a
  receipt, THEN the system SHALL compute the canonical receipt digest... This task implements
  the "compute the canonical receipt digest" half: `CanonicalJson.serialize(...)` +
  `ReceiptDigest`'s own `SHA-256` over it.

## Locked Decisions

- **L6.** Money is `NUMERIC`/`BigDecimal` only; no `float`/`double`/`Double`/`Float` on any
  money-carrying field or DTO. Directly constrains `ReceiptDigest`'s own payload construction:
  `amount` must enter the canonical map as a `String` (`BigDecimal.toPlainString()`), never a
  bare numeric Java type that could serialize as a JSON number.
- **L7** (context, not re-implemented here — task 19's own scope). `entry_hash =
  SHA-256(canonical_payload || prev_hash)`. This task builds the one shared `CanonicalJson`
  utility `LedgerService` will also use for its own canonical payload — the task statement's own
  explicit reason for building `CanonicalJson` as a standalone, reusable `common/` utility rather
  than a private helper inside `ReceiptDigest`.
- **L12.** Shared plumbing lives in `common`. `CanonicalJson` goes in `common/` per the task
  statement's own explicit instruction, consistent with L12.
- **L9** (context only — not this task's scope, T18's). Receipts are WORM, keyed by
  `receiptUuid`. Relevant here only because it confirms `receiptUuid` is a real, externally
  meaningful identifier (not an internal-only convenience) — reinforcing that `ReceiptDigest`'s
  own caller (T17) must generate and durably persist it, not treat it as disposable.

## Files involved

**New files the spec expects (none exist yet, confirmed at Phase 0):**
- `services/payment/src/main/java/com/themistra/payment/common/CanonicalJson.java` —
  `serialize(Map<String,Object>)`, sorted keys, UTF-8, no insignificant whitespace, no key
  omitted.
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptDigest.java` (new
  package `receipt`, per `design.md`'s own module diagram — this is the first file in that
  package) — computes the `SHA-256` hex digest of the canonical JSON over `design.md` O4's exact
  12-field payload.

**Existing files to read (sources of the payload's own fields), not modified:**
- `invoice/Invoice.java` — `merchantAccountUuid`, `chain`, `tokenContractAddress`,
  `tokenDecimals`, `amount`, `payToAddress`.
- `payment/Payment.java` — `paymentUuid`, `chain`, `txHash`, `fromAddress`,
  `tokenContractAddress`, `amount`, `finalizedAt`.

**Not touched:** `receipt/Receipt.java`/`ReceiptRepository` (T17), `ReceiptService` (T17),
`ReceiptStore`/S3 (T18), `LedgerService`/`LedgerEntry` (T19) — `CanonicalJson` is built generic
enough for T19 to reuse, but T19 itself is out of this task's scope entirely.

## Dependencies

- `java.security.MessageDigest` (`"SHA-256"`), `java.util.HexFormat` — the established
  cross-service pattern (confirmed via `auth-service`'s own `Hashing.sha256`, not imported
  directly per `agents.md`'s "never depend on another service's source," but its shape mirrored).
- Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) — already a transitive dependency via
  `spring-boot-starter-web`; the natural tool for the actual JSON-text production inside
  `CanonicalJson`, with keys pre-sorted (e.g. via a `TreeMap`) before serialization and
  `ObjectMapper`'s own default (non-pretty-printed) writer for "no insignificant whitespace."
- No new Maven dependency needed.

## Acceptance Criteria

- **AC1 (L12).** `CanonicalJson.serialize(Map<String,Object>)` exists in `common/`, is
  deterministic: the same logical key-value pairs, inserted in any order, produce byte-identical
  output.
- **AC2 (L6).** The serialized JSON never contains a bare numeric literal for any value supplied
  as a `BigDecimal`/`String` by the caller — `CanonicalJson` itself does no numeric coercion; it
  serializes whatever Java type the caller's map already holds (a `String` stays a JSON string).
  The actual L6 compliance burden is on `ReceiptDigest`'s own caller passing `amount` in as a
  `String`, not on `CanonicalJson` itself — `CanonicalJson` is type-shape-agnostic by design
  (same shape `LedgerService`, T19, will also need for its own, different payload).
- **AC3 (no insignificant whitespace, UTF-8, no key omitted).** The output contains no
  pretty-printing whitespace, is valid UTF-8, and every key present in the input map appears in
  the output — including a key whose value is `null` (resolved explicitly at Phase 2, not left
  ambiguous — see Phase 0's Open Question 1).
- **AC4 (R19 partial).** `ReceiptDigest` produces a `SHA-256` hex digest (64 lowercase hex
  characters) over `CanonicalJson.serialize(...)` applied to exactly `design.md` O4's 12-field
  payload shape, with `receiptUuid` accepted as a caller-supplied `UUID` parameter (no `Receipt`
  entity exists to read it from).
- **AC5 (determinism, the core property).** Calling `ReceiptDigest` twice with the identical
  logical payment/invoice/receipt data produces the identical digest, regardless of the order
  fields were assembled in by the caller.

## Tests required

No test is pre-mapped to T16 in `package.md` §8 (`shouldRequestKmsSignatureViaCryptoAttestEndpoint`,
R19, is T17's own named test for the full flow — same split as T15's own `attest` method had for
R19). Derived tests, following the "unit only, no Spring context" convention (Phase 0):

- A test proving `CanonicalJson.serialize` produces identical output for two maps built with
  keys inserted in different orders (the core determinism property, AC1).
- A test proving no pretty-printing whitespace appears in the output (AC3).
- A test proving every key is present in the output even when its value is `null` (AC3,
  pending Phase 2's resolution of Phase 0's Open Question 1 on exactly how `null` is
  represented).
- A test proving `ReceiptDigest` produces a 64-character lowercase hex string (AC4).
- A test proving `ReceiptDigest` is deterministic across two calls with identical input (AC5).
- A test proving `ReceiptDigest`'s output changes if any single field changes (a basic
  "sensitivity" check — not literally required by any AC, but the natural complement to AC5 that
  rules out a degenerate always-same-output implementation).
- A test proving `amount` enters the digest as a decimal string, not a numeric JSON token (AC2),
  mirroring `CryptoClientTest`'s own `shouldSendExpectedAmountAsDecimalStringNotJsonNumber`
  precedent for `registerWatch`.

## Open Questions

Both carried forward from Phase 0, confirmed not pre-answered anywhere in `package.md` §8/§11:

1. **How is a `null` value represented in the canonical output?** `design.md` O4 shows
   `fromAddress` as `"<address|null>"` (explicitly nullable) while the task statement says "no
   key omitted." These two statements are only in tension if "omitted" is read to include "the
   key is present but explicitly unset" — they are NOT in tension if "no key omitted" simply
   means every key that exists in the logical payload must appear in the output (including with
   an explicit JSON `null` value) — which is the natural, literal reading. Resolving this at
   Phase 2 as a design decision (not escalating further) — see that phase's own disposition.
2. **Does `CanonicalJson` need real recursive nested-`Map`/`List` canonicalization now, or can
   it defer to a flat-map-only implementation until a real nested payload exists?** Neither of
   this task's two known real consumers (O4's receipt payload, `ledger_entries.payload`) is
   confirmed to need nesting. Resolving at Phase 2.

Neither question blocks starting Phase 2 — both are implementation-shape decisions within this
task's own discretion to make and record, following the same pattern established for every
prior task's own Phase 1→2 handoff.
