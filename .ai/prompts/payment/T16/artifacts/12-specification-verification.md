# payment · T16 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

**Updated (2026-10-15)** after the real Kimi Phase 11 artifact landed, superseding an earlier,
self-produced Phase 11 pass that had briefly stood in its place and was reverted on explicit
user instruction (`11-test-review.md` deleted, then replaced by the genuine artifact). This
revision reflects the real Kimi findings and the fixes applied for them.

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R19** (partial — digest computation only) — compute the canonical receipt digest over `design.md` O4's exact 12-field payload | Yes | `ReceiptDigest.java:35` (`digest(...)`), `ReceiptPayload.java:29` (the 12-field record) | `ReceiptDigestTest` — 13 methods (hex-format, determinism, 6 field-sensitivity cases incl. both O4 asymmetric fields and `receiptUuid`, amount-as-string, tokenDecimals-as-number, null-payload, golden full-payload, and a Phase 11 Kimi-added independent-hash tie from the real digest to the golden JSON) | No | No |
| **L6** — `amount` never a bare numeric JSON token; money is `BigDecimal`-typed in-memory | Yes | `ReceiptDigest.java` `toCanonicalMap` — `payload.amount().toPlainString()` | `shouldSerializeAmountAsDecimalStringNotJsonNumber` | No | No |
| **L7** (context — task 19's own scope, not re-implemented here) — `CanonicalJson` built generic enough for `LedgerService` to reuse without a second canonicalizer | Yes, by design | `CanonicalJson.java:52,61` (`serialize`/`canonicalize` — type-agnostic beyond the explicit allowed-type list, recursive, no O4-specific logic anywhere in this class) | N/A (task 19 not built) | No | No |
| **L12** — `CanonicalJson` in `common/`; `ReceiptPayload`/`ReceiptDigest` in `receipt/`, neither importing `Invoice`/`Payment` | Yes | File locations confirmed; `grep -c "import.*\.Invoice\|import.*\.Payment"` on both new `receipt/` files returns 0 | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (re-run, unaffected — this task adds no `@Entity`, so the rule's own scan has nothing new to check) | No | No |
| **Task statement's own "sorted keys, UTF-8, no insignificant whitespace"** | Yes | `CanonicalJson.java:61` (`canonicalize`'s `TreeMap` sort) | `shouldProduceIdenticalOutputRegardlessOfKeyInsertionOrder`, `shouldProduceNoPrettyPrintingWhitespace`, `shouldSortNestedMapKeys`, `shouldSortMapKeysInsideAList` (Phase 11, Kimi Gap 4 — the one remaining nesting combination) | No | No |
| **New, found during this task (Phase 3/4 Finding 1)** — `CanonicalJson` rejects any unsupported value type, recursively, rather than trusting callers | Yes | `CanonicalJson.java:61` (`canonicalize`'s exhaustive type check + final `throw`) | 11 `shouldReject*`/`shouldAccept*` tests, plus `shouldSerializeEmptyMapAndEmptyListValues` (Phase 11, Kimi Gap 5) | No | No |

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
- **Phase 11 is now a genuine Kimi pass**, closing the earlier process gap: a self-produced
  pass had briefly stood in this slot after a vague "move on" was misread as authorization to
  fill a Kimi-reserved phase; the user corrected this explicitly, the self-produced artifact was
  deleted, and the real Kimi review (which ran against the Phase 10 test state, 14+11 methods)
  found 5 of its own findings — one Medium (`ReceiptDigest`'s canonical-JSON assertions only
  ever going through a test-only helper that independently mirrors `toCanonicalMap`, so a real
  drift there could go undetected) and four Low. All actionable findings applied; one
  (`Boolean` coverage, part of Kimi's own Gap 5) turned out to already be closed by the kept
  code from the earlier self-produced pass, since Kimi's review predated that code existing in
  the state it reviewed — confirmed, not assumed, before dispositioning.
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
acceptance criterion including two further rounds of genuine, independent gap-finding (Phase 8
and Phase 11, both real Kimi passes, both verified-not-assumed before applying). The Phase 11
fix most worth noting: `ReceiptDigest.digest(...)`'s real output is now tied directly to the
golden canonical JSON reference via an independently-computed `SHA-256` call, not just through
a parallel test helper — mutation-verified (a simulated dropped field in `toCanonicalMap` was
caught). `CanonicalJsonTest`+`ReceiptDigestTest`: 32/32. Full payment-service suite: 348/348
passing.
