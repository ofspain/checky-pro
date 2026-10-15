# payment · T20 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Findings only — no fixes applied here (Phase 9's job), mirroring the established convention.

---

## Finding 1 (Low) — a corrupted `payload`/`prevHash` with a null value throws, rather than returning a typed `Broken`

- **Issue:** `LedgerEntry.payload`/`prevHash` are both `@Column(nullable = false)` at the DB
  level, so this can only happen via a direct, raw-SQL bypass of the application entirely (the
  exact same category of access this task's own threat model already assumes — an insider with
  database-owner privileges, not `payment_app`). If such tampering ever set either column to
  `NULL` directly, `CanonicalJson.serialize(null)` (or the subsequent `.equals(...)` call on a
  null `prevHash`) would throw a `NullPointerException`, not return a typed `Broken` result —
  inconsistent with every other failure mode this class handles via the sealed result type.
- **Evidence:** `LedgerVerifier.java` — no null-guard before `CanonicalJson.serialize(current
  .getPayload())` or `current.getPrevHash().equals(...)`.
- **Impact:** Low. The scenario requires the identical raw-SQL-bypass access this task's own
  named test already simulates; an uncaught exception still correctly signals "something is
  deeply wrong" to any real caller, just not via the same typed channel the other two failure
  modes use.
- **Recommendation:** Could wrap the per-entry check in a try/catch converting any unexpected
  exception to `Broken(seq, "entry could not be verified: " + e.getMessage())` — judged not
  worth the added complexity for a scenario that already requires defeating the DB's own `NOT
  NULL` constraint directly; disclosed here rather than silently accepted.

---

## Positive confirmations

- The positional (not `seq`-arithmetic) prior-entry lookup correctly handles **both** directions
  of tampering with the identical mechanism, confirmed by direct reasoning, not just the one
  scenario tested empirically this phase: a harmless `seq` gap (a rolled-back transaction) never
  produces a false `Broken`, **and** an insider directly deleting an existing, previously-
  correctly-chained middle row (true tampering) correctly *does* produce a real `Broken` — the
  same code path serves both cases correctly because it only ever reasons about `prevHash`/
  `entryHash` values, never `seq` numbering.
- A sealed sibling-fabrication limitation is inherent to any hash-chain scheme, not a gap this
  task introduces: an attacker with full database-owner access could in principle fabricate an
  entirely new, internally self-consistent chain from scratch. `LedgerVerifier`'s own job — and
  R22's own stated scope — is detecting *alteration* of existing records, not defending against
  a adversary who has already defeated every access control and is willing to recompute an
  entire replacement history; this is consistent with `SECURITY-THREAT-MODEL.md`'s own "insider
  alters a historical verification" framing (alteration, not full fabrication).
- `CanonicalJson.serialize(...)` is reused exactly as `LedgerService.append` already uses it —
  no second canonicalizer, confirmed by direct inspection of the import list and call site.
- No monetary value, secret, or PII is logged anywhere in this task's own code (it logs nothing
  at all — a pure computation with no side effects beyond its own return value).
- The full suite (405/405, Docker up) passes unchanged — no regression, confirmed by the
  scratch-test verification already performed this phase against real data, not merely asserted.
