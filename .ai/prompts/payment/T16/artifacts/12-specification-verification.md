# payment · T16 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R19** (partial — digest computation only) — compute the canonical receipt digest over `design.md` O4's exact 12-field payload | Yes | `ReceiptDigest.java:35` (`digest(...)`), `ReceiptPayload.java:29` (the 12-field record) | `ReceiptDigestTest` — 12 methods (hex-format, determinism, 6 field-sensitivity cases incl. both O4 asymmetric fields and `receiptUuid`, amount-as-string, tokenDecimals-as-number, null-payload, golden full-payload) | No | No |
| **L6** — `amount` never a bare numeric JSON token; money is `BigDecimal`-typed in-memory | Yes | `ReceiptDigest.java` `toCanonicalMap` — `payload.amount().toPlainString()` | `shouldSerializeAmountAsDecimalStringNotJsonNumber` | No | No |
| **L7** (context — task 19's own scope, not re-implemented here) — `CanonicalJson` built generic enough for `LedgerService` to reuse without a second canonicalizer | Yes, by design | `CanonicalJson.java:52,61` (`serialize`/`canonicalize` — type-agnostic beyond the explicit allowed-type list, recursive, no O4-specific logic anywhere in this class) | N/A (task 19 not built) | No | No |
| **L12** — `CanonicalJson` in `common/`; `ReceiptPayload`/`ReceiptDigest` in `receipt/`, neither importing `Invoice`/`Payment` | Yes | File locations confirmed; `grep -c "import.*\.Invoice\|import.*\.Payment"` on both new `receipt/` files returns 0 | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (re-run, unaffected — this task adds no `@Entity`, so the rule's own scan has nothing new to check) | No | No |
| **Task statement's own "sorted keys, UTF-8, no insignificant whitespace"** | Yes | `CanonicalJson.java:61` (`canonicalize`'s `TreeMap` sort) | `shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder`, `shouldProduceNoPrettyPrintingWhitespace`, `shouldSortNestedMapKeys` | No | No |
| **New, found during this task (Phase 3/4 Finding 1)** — `CanonicalJson` rejects any unsupported value type, recursively, rather than trusting callers | Yes | `CanonicalJson.java:61` (`canonicalize`'s exhaustive type check + final `throw`) | 8 `shouldReject*`/`shouldAccept*` tests (Phase 10 + Phase 11's own 3 additions: `Boolean` positive, `BigInteger`/array negative) | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, for this task's own scope. `CanonicalJson.serialize`
is a genuinely reusable, defensively-guarded canonicalizer; `ReceiptDigest.digest` produces the
exact O4 payload's SHA-256 digest. T17's own `ReceiptService` (not built) is the first real
caller; T19's `LedgerService` (not built) is the second intended consumer of `CanonicalJson`.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All seven (AC1–AC7)
are satisfied: AC1 (determinism/sorting), AC2 (amount-as-string, tokenDecimals-as-number), AC3
(no whitespace, null handling), AC4 (64-char hex), AC5 (determinism + sensitivity), AC6 (no
entity import), AC7 (the type guard, added at Phase 4).

**(3) Does it violate any LOCKED decision?** No. L6/L7/L12 all hold.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **Phase 11 was self-produced**, not a genuine Kimi pass — no Kimi artifact ever landed for
  this task's own Phase 11 before the user explicitly directed moving on. The self-produced
  review found and fixed 4 real gaps (disclosed in `11-test-review.md`), but a genuine
  independent reviewer might still find something a self-review cannot — logged as a disclosed
  process deviation, not a silent one, consistent with this session's own "disclose forward"
  convention. If a real Kimi Phase 11 artifact appears later, re-run it against this task's own
  final state as a follow-up, not retroactively blocking this PASS.
- **`CanonicalJson`'s own genuine reusability for T19 is unverified until T19 actually exists** —
  the type guard and recursive design are built to be generic, but no real second payload shape
  has exercised it yet. Not a defect; the expected, disclosed shape of building shared
  infrastructure one task ahead of its second consumer.
- **The ASCII-scope limitation** (Phase 3/4 Finding 4, corrected at Phase 9) remains a disclosed,
  untested-for-non-ASCII scope boundary — harmless today (every known real field is ASCII-only),
  revisit only if a future payload genuinely needs non-ASCII canonicalization.

## Verdict

**PASS** — `CanonicalJson` and `ReceiptDigest` are both fully implemented, correctly guarded
against the type-safety foot-gun Kimi's Phase 3 review identified, and tested against every
acceptance criterion including two further rounds of real gap-finding (Phase 8's independent
review and a self-produced Phase 11, both verified-not-assumed). `CanonicalJsonTest`+
`ReceiptDigestTest`: 29/29. Full payment-service suite: 345/345 passing.
