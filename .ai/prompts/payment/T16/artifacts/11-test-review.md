# payment · T16 · Phase 11 — Test Review (self-produced)

Artifact: `.ai/prompts/payment/T16/artifacts/11-test-review.md`
Consumed: `artifacts/10-test-generation.md`, `CanonicalJsonTest.java`, `ReceiptDigestTest.java`.

No Kimi artifact landed for this phase; produced directly, held to the same adversarial standard
against this task's own Phase 10 work (mirrors T01's own identical precedent, [[kimi-handoff-wait-discipline]]).

---

## Executive summary

- All 25 Phase 10 tests pass; full suite 341/341.
- Re-reading `CanonicalJson`'s own type guard against its test coverage found three real
  type-coverage gaps: `Boolean` (an explicitly *supported* type) is never positively tested;
  `BigInteger` and a raw array (both explicitly *rejected* types, per the class's own Javadoc and
  Kimi's own Phase 8 "positive confirmations") are never tested at all — meaning Kimi's own Phase
  8 claim that arrays are rejected was asserted, not verified.
- A narrower gap: only 4 of `ReceiptPayload`'s 12 fields have a dedicated digest-sensitivity
  test; none of the 4 UUID fields or 3 remaining `String` fields do.

---

## Gap 1 (Medium) — `Boolean` is a supported type with zero positive test coverage

- **Why it matters:** `CanonicalJson.canonicalize`'s own first branch explicitly allows
  `Boolean` through unchanged (`value instanceof Boolean`), and the class's own Javadoc names it
  as a supported type. No test in `CanonicalJsonTest` ever passes a `Boolean` value and asserts
  the output — a regression that accidentally dropped `Boolean` from the allowed-type list (or
  serialized it incorrectly, e.g. as a quoted string) would not be caught.
- **Suggested test:** `shouldAcceptBooleanValue` — serialize a map with a `Boolean.TRUE` value,
  assert the output contains the bare literal `true` (no quotes).
- **Confidence:** High — a real, direct gap against the class's own documented contract.

---

## Gap 2 (Medium) — `BigInteger` is named as explicitly rejected but never tested

- **Why it matters:** The class's own Javadoc explicitly lists `BigInteger` alongside `Float`/
  `Double`/`BigDecimal` as a type the guard must reject. No test exercises this — unlike the
  other three rejected types, each of which has its own `shouldReject*Value` test.
- **Suggested test:** `shouldRejectBigIntegerValue`.
- **Confidence:** High.

---

## Gap 3 (Medium) — a raw array is never tested, despite being claimed as rejected

- **Why it matters:** Phase 8's own "positive confirmations" section asserted "[the type guard]
  rejects ... arrays, and other unsupported types" — but no test in this file actually passes an
  array value. Tracing `canonicalize`'s own logic confirms an array (not a `List`) falls through
  every `instanceof` check to the final `throw`, so the *code* is correct — but that claim had
  never been empirically verified by a test, only asserted in review prose.
- **Suggested test:** `shouldRejectArrayValue` — pass a raw `int[]` or `String[]` as a map value,
  assert `IllegalArgumentException`.
- **Confidence:** High.

---

## Gap 4 (Low) — only 4 of 12 `ReceiptPayload` fields have a dedicated sensitivity test

- **Why it matters:** `shouldChangeDigestWhenAnySingleFieldChanges` (Phase 5's plan) was
  implemented as four separate methods covering `fromAddress`, `amount`, `chain`,
  `finalizedAt`, and `tokenDecimals` — five fields, not all twelve. None of the four `UUID`
  fields (`receiptUuid`, `invoiceUuid`, `paymentUuid`, `merchantAccountUuid`) or the three
  remaining `String` fields (`txHash`, `tokenContractAddress`, `payToAddress`) has its own
  "changing this field changes the digest" test. The golden full-payload test would likely catch
  a gross mapping bug (e.g. a swapped field) but not a narrower sensitivity regression (e.g. a
  field accidentally hardcoded instead of read from the payload).
- **Suggested test:** At minimum, one additional test for `receiptUuid` (the one field with no
  entity source at all, generated fresh by the caller every time — arguably the highest-value
  single field to prove sensitivity for, since a bug silently ignoring it would be the easiest to
  introduce and the hardest to notice).
- **Confidence:** Low — the golden test already provides significant incidental protection; this
  is a diminishing-returns improvement, not a correctness gap.

---

## Positive confirmations

- All 7 previously-claimed-rejected types (`UUID`, `Instant`, `BigDecimal`, `Float`, `Double`)
  that DO have tests are correctly covered, including one nested-in-a-list case.
- The 4 safe `Number` subtypes (`Short`/`Integer`/`Long`/`Byte`) are positively tested together.
- `null` payload, non-`String` nested key, and `null` nested key (the Phase 9 fixes) are all
  directly tested.
- The golden full-receipt test's own expected string was verified empirically (not hand-derived)
  before being hard-coded — confirmed by re-reading `10-test-generation.md`'s own disclosed
  derivation note.
- Determinism, no-pretty-print, and nested-key-sorting are all directly tested.
- Full suite: 341/341.

---

## Disposition

Gaps 1–3 accepted and fixed directly (self-produced phase, same discipline as a Kimi-found gap —
real, cheap, closes a genuine contract-vs-test mismatch). Gap 4 accepted narrowly: adding one
`receiptUuid` sensitivity test, not all seven remaining fields, given the already-strong
incidental protection from the golden test and diminishing returns past the highest-value case.
