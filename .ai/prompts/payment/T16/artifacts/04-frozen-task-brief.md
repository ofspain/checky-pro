# payment · T16 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Disposition of Phase 3 (Kimi) findings

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | Medium — `CanonicalJson` should reject unsupported value types rather than trust every caller | **ACCEPTED** | Real foot-gun given the utility's own explicit reusability goal (T19). A caller passing a raw `UUID`/`Instant`/`BigDecimal`/`Float`/`Double` would silently depend on Jackson's own version-sensitive defaults — exactly the risk the whole canonicalization design exists to avoid. Added a recursive runtime type guard (see Outputs below). |
| 2 | Low — enumerate the 12 O4 fields in the brief | **ACCEPTED** | Cheap, removes a cross-reference step. Table added below. |
| 3 | Low — no golden full-receipt canonicalization test | **ACCEPTED** | Genuinely valuable — locks in the exact canonical byte shape as a stable reference, guards against any future Jackson escaping/formatting drift. Added to Required Tests. |
| 4 | Low — document the ASCII/Unicode-escaping assumption | **ACCEPTED** | Cheap, honest about a real (currently harmless) scope limit. Added as a Javadoc note. |
| 5 | Low — `ReceiptPayload` should validate its 11 non-null fields | **ACCEPTED** | Consistent with the precedent this session already established for `AttestationResult.Signed` (T15, Phase 9) — fail at construction time, not downstream. Added compact-constructor `Objects.requireNonNull` on every field except `fromAddress`. |

No finding was rejected.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

- **`CanonicalJson.serialize`'s own recursive canonicalization now validates every value**
  (flat or nested) is one of: `String`, `Boolean`, `null`, `Short`/`Integer`/`Long`/`Byte`
  (safe integer `Number` types only — explicitly excludes `Float`/`Double`/`BigDecimal`/
  `BigInteger`, mirroring L6's own anti-floating-point spirit beyond just money fields), a
  nested `Map<String,Object>`, or a `List<Object>`. Anything else throws
  `IllegalArgumentException` naming the offending key/type. This is a new, required behavior
  for `CanonicalJson` itself, not left to caller discipline.
- **`CanonicalJson`'s own Javadoc** documents that it targets ASCII keys/values (ESCAPE_NON_ASCII
  is Jackson's own default, unexamined here) — a disclosed scope limit, not a silent gap.
- **`ReceiptPayload`** gains a compact constructor validating `receiptUuid`, `invoiceUuid`,
  `paymentUuid`, `merchantAccountUuid`, `chain`, `txHash`, `tokenContractAddress`,
  `tokenDecimals`*, `amount`, `payToAddress`, `finalizedAt` are all non-null
  (`Objects.requireNonNull`). `fromAddress` is the one field explicitly allowed to be `null`
  (O4's own `"<address|null>"`). *`tokenDecimals` is a primitive `short`, which cannot itself be
  `null` — no check needed for that field; listed here only for completeness against the full
  O4 field set.
- **Required Tests gains one new test**: a golden full-12-field canonicalization test —
  construct the exact O4 shape (scrambled insertion order) and assert the resulting canonical
  JSON string equals a hard-coded expected value.

## O4 field reference (Kimi Finding 2)

| Field | `ReceiptPayload` type | Canonical form |
|---|---|---|
| `receiptUuid` | `UUID` | String |
| `invoiceUuid` | `UUID` | String |
| `paymentUuid` | `UUID` | String |
| `merchantAccountUuid` | `UUID` | String |
| `chain` | `String` | String |
| `txHash` | `String` | String |
| `tokenContractAddress` | `String` | String |
| `tokenDecimals` | `short` | JSON number (the one O4 asymmetry) |
| `amount` | `BigDecimal` | decimal string |
| `payToAddress` | `String` | String |
| `fromAddress` | `String`, nullable | String or JSON `null` |
| `finalizedAt` | `Instant` | RFC3339 string |

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged. **Files to Create/Modify/NOT-Modify:** unchanged from Phase 2.

**Acceptance Criteria** (AC1–AC6 unchanged; new AC7 added):
- AC7 (new, Finding 1): `CanonicalJson.serialize` throws `IllegalArgumentException` for any
  unsupported value type, recursively, at any nesting depth.

## Open Questions

None remaining.
