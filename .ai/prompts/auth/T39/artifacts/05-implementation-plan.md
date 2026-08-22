<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T39 · Phase 5 — Implementation Plan

Documentation-only task. No files to create; one file to modify (`auth-decisions.md`, append-only).

## Files to create

None.

## Files to modify

- `services/auth/docs/architecture/auth-decisions.md` — append four new entries after D-025.

## New entries planned (D-026 through D-029)

| ID | Title | Resolves | Verdict shape |
|---|---|---|---|
| D-026 | Rate-limit thresholds: 10/5/30 per minute, MFA folded into the login bucket | O2 | Reference influence: None (reference had no rate limiting at all) |
| D-027 | Session device-label source: still open | O3 (recorded as unresolved, not resolved) | No verdict — explicitly not a decision, a documented gap |
| D-028 | Login page: default Spring Security form, no custom template | O4 | Reference influence: None |
| D-029 | Recovery-code hashing: SHA-256 | O5 | Reference influence: None (reference has no MFA) |

Each entry (except D-027) follows the document's exact existing structure: Context · Alternatives ·
Selected · Trade-offs · Impact · Reference influence. D-027 is deliberately structured differently
(Context · Current state · Why it's still open · What would resolve it) since it records a
non-decision, not a decision — matching the frozen brief's own reasoning for why fabricating a
resolution would be dishonest.

## Public / private methods

None — no code.

## Entities / Repositories / Services used

None.

## Tests required

None.

## Execution order

1. Draft D-026 (O2) — cite `application.properties:104-106`, `RateLimitFilter.java:23-26`,
   cross-reference D-013.
2. Draft D-027 (O3) — cite `ReuseDetectingAuthorizationService.java:95`, `design.md` §4b O3's three
   named options, state none was chosen.
3. Draft D-028 (O4) — cite absence of any template under `src/main/resources`,
   `TotpAuthenticationProvider`'s `mfaCode` form-field handling.
4. Draft D-029 (O5) — cite `RecoveryCode.java:15-17,39-41`, `MfaService.java:139`.
5. Confirm Q1 needs no edit (D-025 already covers it) — state this explicitly in the Phase 6
   implementation notes, not silently.
6. Append all four entries to `auth-decisions.md` in ID order, immediately after D-025.
7. Re-read the full file after editing to confirm no formatting drift from the existing 25 entries.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
