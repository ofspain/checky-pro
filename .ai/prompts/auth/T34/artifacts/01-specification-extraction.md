<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T34 · Phase 1 — Specification Extraction

## Business Rules

- **R48.** Where `contracts/api/token-claims.md` is authored, every access token shall contain
  *exactly* the claims listed in it, and no PII beyond `email_verified`. **See Open Questions —
  this wording, read literally, conflicts with confirmed real behavior (Phase 0).**

## Locked Decisions

- **L9 — Token claims contract.** Access-token claims are exactly: `iss`, `sub`, `aud`, `exp`,
  `iat`, `nbf`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`, `email_verified`. No email
  address or name is included in access tokens. `id_token` and `/userinfo` carry email/name
  separately via SAS defaults. This task's entire subject.

## Files involved

**Existing files to read (not modify):**
- `services/auth/src/main/java/com/themistra/auth/token/TokenClaimsCustomizer.java` — already read
  in full (Phase 0). Sets `roles`/`amr`/`acr`/`email_verified` for interactive grants; sets only
  `amr` for `client_credentials`.
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java` — already read in
  full. Manually assembles the complete L9 list for API-key-exchange tokens.
- `services/auth/src/main/java/com/themistra/auth/token/RegisteredClientSeeder.java` — already
  read. Confirms `client_credentials` is a real, actively-used grant for seeded service clients.
- `services/auth/docs/architecture/target-design.md` §6 — the design-level claim list, matches L9
  verbatim, equally silent on the per-grant-type variation.

**New file the spec expects:**
- `contracts/api/token-claims.md` (the task's sole deliverable, per `design.md`'s own file tree).

## Dependencies

None (pure documentation; no code, no config, no new library). The doc's *content* depends
entirely on accurately describing `TokenClaimsCustomizer`/`ApiKeyTokenIssuer`'s real behavior.

## Acceptance Criteria

- **AC1 (L9/R48).** `token-claims.md` exists and documents the claim list.
- **AC2 (R48's literal "exactly"/"every access token").** Pending Open Question 1 below — cannot
  be finalized until Phase 2/4 decides how to reconcile R48's uniform-claim-set wording with the
  confirmed three-shape reality.

## Tests required

No named test (`package.md` §8` has none for T34). Whether a new, unnamed test should exist (e.g.
proving the doc's claimed list matches the real code, mirroring T33's contract-verification
philosophy even without a named-test mandate) is itself an open question for Phase 2 — the task
statement's own wording ("Write `contracts/api/token-claims.md`") has no "add tests" clause, unlike
T33's explicit one.

## Open Questions

1. **Genuine blocker, not a design nicety.** R48 states, in EARS form, "every access token SHALL
   contain exactly the claims listed in [token-claims.md]." Phase 0 confirmed via direct source
   reading that `client_credentials`-grant tokens (a real, actively-seeded, production grant type)
   receive only `iss`/`sub`/`aud`/`exp`/`iat`/`jti`/`scope`/`client_id`/`amr` — never `roles`,
   `acr`, or `email_verified`. If `token-claims.md` documents L9's full 13-claim list as the
   universal contract, R48 is literally violated by every `client_credentials` token the moment
   the doc exists (R48's own condition, "where token-claims.md is authored," is exactly what this
   task does). This needs a human-gate decision on how the doc frames the claim set: (a) document
   one universal list and treat the `client_credentials` gap as a bug to flag separately
   (`client_credentials` tokens would need `TokenClaimsCustomizer` changed to also set the missing
   claims — production code change, arguably beyond this doc-only task's scope); (b) document
   per-grant-type variation explicitly in the doc itself, so R48's "exactly the claims listed in
   it" is satisfied because the doc *itself* lists the variation, not a single flat set; or (c)
   some other framing. Not deciding this silently — surfacing for Phase 2/4.
2. Whether a verification test is warranted despite no named-test mandate (see Tests required) —
   non-blocking, a Phase 2 design call either way.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
