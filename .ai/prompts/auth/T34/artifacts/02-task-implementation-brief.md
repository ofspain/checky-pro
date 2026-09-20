<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T34 · Phase 2 — Task Implementation Brief

## Task

Write `contracts/api/token-claims.md`, documenting the exact access-token claim set from L9.

## Purpose

Give resource servers (and future integrators) a single, authoritative, versioned reference for
exactly what an auth-service-issued access token contains — satisfying R48's own framing that
`token-claims.md` becomes the contract every access token must conform to.

## Scope

**In:** authoring `contracts/api/token-claims.md` only.

**Out:** any change to `TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`, or any other
production code; any change to `RegisteredClientSeeder.java`'s grant-type configuration. This is a
documentation-only task per its own literal wording ("Write `contracts/api/token-claims.md`").

## Business Rules

- **R48.** Once authored, every access token shall contain exactly the claims listed in the doc,
  no PII beyond `email_verified`. **Tentative resolution (D1 below) — Phase 3/4 to confirm.**

## Locked Decisions

- **L9.** The 13-claim list this task documents, verbatim.

## Dependencies

None (no code, no config, no library).

## Inputs

None.

## Outputs

`contracts/api/token-claims.md` (new file).

## State Changes

None.

## Files to Create

- `contracts/api/token-claims.md`.

## Files to Modify

None.

## Files NOT to Modify

- `token/TokenClaimsCustomizer.java`, `apikey/ApiKeyTokenIssuer.java`,
  `token/RegisteredClientSeeder.java` — all read-only inputs to this doc, not touched by it.
- Any `spec/` file.

## Acceptance Criteria

- **AC1.** `token-claims.md` exists, lists L9's 13 claims, and states the no-PII-beyond-
  `email_verified` rule.
- **AC2 (R48, tentative — D1).** The doc's own claim-set description is accurate for **every**
  real issuance path, not just the interactive one — satisfying R48's "exactly the claims listed
  in it" by having the doc *itself* state the real, per-grant-type variation (D1), rather than
  asserting one universal list that `client_credentials` tokens don't actually meet.

## This Task's Own Design Decision (D1, tentative — Phase 3/4 to confirm)

**D1.** Resolve Phase 1's Open Question 1 by documenting L9's 13 claims as the **full/canonical
set** (issued for interactive SPA logins and API-key exchanges), with an explicit, clearly-labeled
subsection noting that `client_credentials`-grant tokens (service-to-service clients) omit
`roles`, `acr`, and `email_verified` — naming `TokenClaimsCustomizer`'s early-return for that grant
type as the reason, not hiding it as an inconsistency. Rejected alternative: treating the
client_credentials gap as a bug and proposing a `TokenClaimsCustomizer` fix — rejected because (a)
it's a production-code change squarely outside this task's own literal, doc-only scope, and (b) no
evidence was found that service-to-service tokens actually need `roles`/`acr`/`email_verified` (a
machine client has no user to have roles about) — closing the gap might not even be correct
behavior, just undocumented behavior, and this task's job is accurate documentation, not silent
behavioral judgment calls.

## Required Tests

None named (`package.md` §8` has none for T34). No new test proposed — matches the task's own
literal scope (a doc-only task, unlike T33's explicit "add contract tests" instruction). Whether a
future task should add a verification test cross-checking this doc against
`TokenClaimsCustomizer`/`ApiKeyTokenIssuer` is left as a note in the doc's own "Verification" note,
not built here.

## Constraints

- **Performance / Thread-safety / Transaction / Module boundaries / Null handling:** N/A — pure
  documentation, no code.
- **Security:** the doc itself must not leak anything beyond what's already true of the tokens
  (e.g. must not imply any claim carries PII beyond `email_verified`, matching L9/R48 exactly).

## Open Questions

**Blocker, carried from Phase 1, tentatively resolved as D1 above — needs explicit Phase 4
confirmation before the brief freezes.** No other blockers.

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
