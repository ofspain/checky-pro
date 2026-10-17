# payment · T22 · Phase 9 — Review Resolution

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

Approved by the human via `AskUserQuestion`, 2026-10-17 ("Approve as proposed").

## Disposition of Kimi's 7 Phase 8 findings

| # | Finding | Disposition | Exact change made |
|---|---|---|---|
| 1 | CSV escaping omits a bare `\r` | **ACCEPTED** | `ExportController.csvField` now also quotes a value containing `\r`, not just `\n`. |
| 2 | `Accept` header CSV detection is a naive substring match | **ACCEPTED, with a regression caught and fixed during application — see note below** | `wantsCsv` now parses with `MediaType.parseMediaTypes(accept)` and checks `CSV.equalsTypeAndSubtype(candidate)` with `q != 0`, instead of `accept.toLowerCase().contains("text/csv")`. |
| 3 | No committed tests exercise the export module | **ACKNOWLEDGED, no code change** | Already the standing plan — test generation is Phase 10's job, not Phase 6/7/8/9's. Nothing to apply here. |
| 4 | New `MissingServletRequestParameterException` handler is untested | **ACKNOWLEDGED, no code change** | Same reason as #3 — covered by Phase 10's test plan (already listed in `artifacts/05-implementation-plan.md` test #11/#13 scope). |
| 5 | Native-query result mapping is position-based and fragile | **ACCEPTED (the lighter of the two offered fixes)** | Added a comment pinning the SQL's column order to `toRow(Object[])`'s own ordering, at both ends, rather than switching to `@SqlResultSetMapping`/`@ConstructorResult` — the heavier option would add more machinery than this single reporting query needs. |
| 6 | CSV response lacks `Content-Disposition` | **ACCEPTED** | Added `Content-Disposition: attachment; filename="transactions-<fromEpochSeconds>-<toEpochSecond>.csv"` to the CSV response branch. |
| 7 | CSV body fully materialized in memory | **ACKNOWLEDGED, no code change** | Already covered by Phase 4's own rejected Finding 8 (no row cap/pagination, disclosed accepted risk) — this finding names a concrete amplification of that same already-accepted risk, not a new ask. Revisit only if that Phase 4 disposition itself is revisited. |

No finding was rejected outright; three were acknowledged without a code change (already scoped to
a later phase, or already covered by an earlier accepted risk), consistent with the rule against
refactoring or expanding scope beyond what was asked.

## A regression caught and fixed while applying Finding 2 — disclosed, not silent

Kimi's own Finding 2 recommended `candidate.isCompatibleWith(CSV)`. Implementing it literally and
testing it (a throwaway reflection-based scratch probe on `wantsCsv`, written, run, and deleted
before this commit) showed `Accept: */*` resolving to CSV — because a wildcard is, by definition,
compatible with everything. This directly contradicts the Phase 4 frozen brief's own explicit
text: *"anything else, including absent/`*/*`/`application/json` → JSON (the default)."*
Corrected to `CSV.equalsTypeAndSubtype(candidate)` (exact type/subtype match, not mere
compatibility) before committing — confirmed via the same scratch probe that `*/*` now correctly
resolves to JSON, while `text/csv`, `text/csv;q=0.5`, and a list containing `text/csv` alongside
other types still correctly resolve to CSV, `text/csv;q=0` correctly resolves to JSON, and a
malformed `Accept` value falls back to JSON rather than throwing `InvalidMediaTypeException`
unhandled (a second gap the literal `MediaType.parseMediaTypes` call would have introduced, also
caught and wrapped in a try/catch before committing).

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- A throwaway reflection-based scratch test on `ExportController.wantsCsv` (written, run,
  deleted): confirmed `format=csv` → true, `Accept: text/csv` → true, `Accept: application/json`
  → false, no header → false, `Accept: text/csv;q=0` → false, `Accept:
  application/json,text/csv;q=0.5` → true, a malformed `Accept` value → false (no exception),
  `Accept: */*` → false (the regression fix).
- `mvn -o -pl services/payment -am test` (full suite, Docker up — restarted mid-phase after the
  daemon itself had stopped since the last phase; confirmed healthy before re-running):
  **420/420 passing**, no regression from any of the four applied fixes.
