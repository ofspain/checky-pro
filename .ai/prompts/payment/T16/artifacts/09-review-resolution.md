# payment · T16 · Phase 9 — Review Resolution

**STATUS: resolved** — disposition approved via `AskUserQuestion`, applied below.

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1, confirmed by Kimi Phase 8 | `serialize(null)` silently returns the JSON literal `"null"` | **ACCEPTED** | Added `Objects.requireNonNull(payload, ...)` at the top of `CanonicalJson.serialize`, before `canonicalize` runs. |
| 2 | Self-review Finding 2, confirmed by Kimi Phase 8 | Non-`String` nested-map key throws `ClassCastException` instead of the documented `IllegalArgumentException` | **ACCEPTED** | Replaced the direct cast with an explicit `instanceof String` pattern-match check in the `Map` branch, throwing `IllegalArgumentException` naming the actual key type (or `"null"`) otherwise. |
| 3 | Kimi Phase 8 New Finding 1 | A `null` map key throws `NullPointerException` from `TreeMap`, not `IllegalArgumentException` | **ACCEPTED** | Closed by the same fix as row 2 — the `instanceof String` check also catches `null` (a `null` fails `instanceof` in Java), so a single check now closes both gaps. |
| 4 | Kimi Phase 8 New Finding 2 | The `CanonicalJson` Javadoc's claim about Jackson's default non-ASCII escaping is backwards | **ACCEPTED — independently verified before applying** | Extracted `jackson-core`'s own source (`JsonWriteFeature.java`) from the local Maven repository and confirmed directly: `ESCAPE_NON_ASCII(false)` — disabled by default. My own first-drafted Javadoc had this exactly backwards (claimed Jackson escapes non-ASCII by default; it actually emits raw UTF-8). Corrected the Javadoc to state the true default and cite the verification. |
| 5 | Kimi Phase 8 New Finding 3 | `ReceiptDigest.digest(null)` throws a raw, undocumented `NullPointerException` from deep inside `toCanonicalMap` | **ACCEPTED** | Added `Objects.requireNonNull(payload, ...)` at the top of `digest`, for the same "fail clearly, at the boundary" reasoning as row 1 — still an `NullPointerException`, but now thrown immediately with a clear message, not from an arbitrary field accessor three calls deep. |
| 6 | Kimi Phase 8 New Finding 4 | No T16-specific tests exist yet | **NOTED, no code change** | Accurate restatement of the already-planned Phase 10 deferral (confirmed in `06-implementation-notes.md`) — not a new finding requiring action at this phase. |

## Exact changes made

- **`CanonicalJson.serialize`** — added `Objects.requireNonNull(payload, "payload must not be
  null")` as its first statement (row 1).
- **`CanonicalJson.canonicalize`'s `Map` branch** — replaced `(String) entry.getKey()` with an
  explicit `if (!(key instanceof String stringKey)) throw new IllegalArgumentException(...)`
  check, closing both row 2 (wrong exception type for a non-`String` key) and row 3 (`null` key)
  with one change.
- **`CanonicalJson`'s class Javadoc** — corrected the ASCII-scope paragraph to state Jackson's
  real default (`ESCAPE_NON_ASCII` disabled, raw UTF-8 emitted), citing the direct source
  verification (row 4).
- **`ReceiptDigest.digest`** — added `Objects.requireNonNull(payload, "payload must not be
  null")` as its first statement (row 5).

## Verification

`mvn -o -pl services/payment -am compile`: clean. `mvn -o -pl services/payment -am test`:
**316/316 passing**, unchanged from Phase 6/7/8's own baseline (no new test methods added this
phase — Phase 10's own job, which now also needs to cover the four new/clarified behaviors:
null top-level payload, non-`String` nested key, `null` nested key, null `ReceiptPayload`).
