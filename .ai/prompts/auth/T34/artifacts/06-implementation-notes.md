<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T34 · Phase 6 — Implementation Notes

## What changed

One file: `contracts/api/token-claims.md` (new). No production code touched, confirmed via
`git status` — matches the frozen brief's Files to Create exactly.

## Mapping to acceptance criteria

- **AC1** — the doc exists with all sections from the Phase 4 Addendum's revised structure: the
  13-claim glossary, three per-path tables (no "canonical" framing, per femi's Phase 5 gate
  decision), TTL, Verification note, No-PII statement.
- **AC2 (R48)** — the doc's own framing is accurate for every real issuance path: it explicitly
  states which of the 13 claims each path has and doesn't, rather than asserting one universal set
  that any path would fail to satisfy.

## Deviations from the plan

None. Phase 5's implementation plan was followed exactly, including the execution order (flat
glossary → interactive table → client_credentials table → API-key table → TTL/verification/no-PII).
The one substantive correction (the `client_id` omission from both SAS-issued paths) was already
made during Phase 5 planning, with the frozen brief itself amended at that point — this phase
executed the corrected plan, it did not discover anything new.

## Verification performed

- Every claim value in the doc traces to a specific line already read in full during Phase 0/3/5
  (`TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`) or to the Phase 5 empirical/source
  verification (`SasLoginIntegrationTest`'s real token dump; `JwtGenerator.java`'s absence of any
  `client_id` claim-setting code).
- Cross-checked the TTL claim specifically at write-time (per Phase 5's own execution-order note,
  step 5): confirmed `themistra.auth.api-key.token-ttl-minutes` defaults to `10` in
  `application.properties`, matching `RegisteredClientSeeder.ACCESS_TOKEN_TTL` (10 minutes) exactly
  — stated in the doc as independently-configured-but-currently-equal, not silently assumed equal.
- `git status` confirms only the one authorized file was created; no scratch files, no production
  code changes.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
