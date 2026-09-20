<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T34 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN (amended during Phase 5 planning — see Addendum at end)**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
6 findings). All verified against actual source — Finding 1 specifically verified via a real JWT
encoded through SAS's own `NimbusJwtEncoder`/`JwtGenerator` pipeline, not merely re-read from
source, since it made a specific empirical claim about wire-format serialization. femi decided the
two items with genuine weight via human gate; the remaining findings are folded in directly as
doc-content requirements.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | `scope` claim format allegedly differs between SAS-issued (space-separated string) and API-key-issued (JSON array) tokens | High | **REJECTED, femi's gate decision — factually incorrect.** Encoded a real JWT via `NimbusJwtEncoder` with a `Set<String>` scope claim (the exact object type `JwtGenerator.java:125`'s `context.getAuthorizedScopes()` produces): resulting payload was `"scope":["read","merchant.api"]` — a JSON array, identical in shape to `ApiKeyTokenIssuer`'s own `List.copyOf(scopes)`. No divergence exists. Not documented as a "known inconsistency" since there isn't one. |
| 2 | `sub`/`aud` semantics differ materially across grant types, undocumented | Medium | **Accepted, folded in.** Doc must state: `sub` = account UUID (interactive/API-key) or client id (`client_credentials`); `aud` = the client id in every SAS-issued path (`JwtGenerator.java`: `.audience(Collections.singletonList(registeredClient.getClientId()))`, confirmed via direct source read) or the hardcoded `checky-api-key` literal for API-key exchange. |
| 3 | L9's "exactly 13 claims" wording conflicts with the `client_credentials` exception | Medium | **Accepted — independently confirms Phase 2's own tentative D1.** Kimi arrived at the same structural resolution unprompted (canonical 13-claim section + a clearly-labeled `client_credentials` subset section). D1 is now doubly-confirmed, not merely proposed. |
| 4 | Per-grant-type `amr`/`acr` values not specified | Medium | **Accepted, folded in.** Doc must enumerate exact values per path (4 distinct combinations, all read directly from source in Phase 0/3). |
| 5 | `email_verified` semantics differ per path (real value / hardcoded `false` / omitted) | Low | **Accepted, folded in.** |
| 6 | No verification test risks doc/code drift | Low | **Accepted, folded in as documentation, not a new test** (matches Phase 2's own tentative plan) — a "Verification" section naming the two ground-truth source files, with a suggestion (not requirement) for a future lightweight sync test. |
| Kimi OQ2 | Should the 10-minute access-token TTL be included even though it's not one of L9's 13 claims? | — | **Resolved, femi's gate decision: yes, include it** — useful context for a resource-server reader, doesn't blur what L9 itself locks (TTL is separately sourced from `design.md` §6/`target-design.md` §6, cited as such). |

## Task

Write `contracts/api/token-claims.md`, documenting L9's exact access-token claim set — including
the real per-grant-type variation Phase 0/3 confirmed, not a single flattened list that would be
inaccurate for `client_credentials` tokens.

## Purpose

Unchanged from Phase 2.

## Scope

**In:** authoring `contracts/api/token-claims.md` only, with the structure below.

**Out:** any production code change (confirmed no fix needed — Finding 1's premise was false, and
Finding 3's `client_credentials` omission is accepted as intentional/undocumented behavior, not a
bug this task fixes).

## Business Rules

- **R48.** Satisfied via D1's structure: the doc's own "exactly the claims listed in it" becomes
  true per-section (canonical section for interactive/API-key tokens; a separate, explicit subset
  section for `client_credentials`), rather than one universal claim asserted for all tokens.

## Locked Decisions

- **L9.** The 13-claim canonical list, documented verbatim for interactive and API-key-exchange
  tokens; the `client_credentials` subset explicitly named as an exception with its own rationale.

## Required Doc Structure (D1, final)

1. **Canonical claim set (13 claims)** — full L9 list, applies to interactive
   (`authorization_code`/`refresh_token`) and API-key-exchange tokens.
2. **Per-claim semantics table** — `sub`/`aud`/`amr`/`acr`/`email_verified`/`scope` values and
   meaning per issuance path (3 paths: interactive, `client_credentials`, API-key exchange),
   closing Findings 2, 4, 5.
3. **`client_credentials` subset** — explicitly lists the 10 claims present and the 3 omitted
   (`roles`, `acr`, `email_verified`), with the rationale (a machine client has no user for those
   claims to describe), closing Finding 3.
4. **Access-token TTL** — 10 minutes, cited from `design.md` §6/`target-design.md` §6 (not one of
   L9's 13 claims, included for reader context per the OQ2 gate decision).
5. **Verification note** — names `TokenClaimsCustomizer.java` and `ApiKeyTokenIssuer.java` as this
   doc's ground truth, closing Finding 6.
6. **No-PII statement** — no email/name beyond `email_verified`, per L9/R48.

## Files to Create

- `contracts/api/token-claims.md`.

## Files to Modify / NOT to Modify

Unchanged from Phase 2 — no production code touched.

## Acceptance Criteria

- **AC1.** Doc exists with all 6 sections above.
- **AC2 (R48).** Doc's own claim-set framing is accurate for every real issuance path (verified
  against source in Phases 0/3/4, not assumed).

## Required Tests

None (unchanged from Phase 2 — doc-only task, no named test, verification note substitutes for a
missing test per Finding 6's disposition).

## Constraints

Unchanged from Phase 2.

## Open Questions

No blockers remaining. Both of Kimi's own Open Questions are resolved above.

---

## Addendum (during Phase 5 planning) — `client_id` is missing from BOTH SAS-issued paths

While gathering exact per-path values for Phase 5, ran a real integration test
(`SasLoginIntegrationTest`, real Docker-backed SAS authorization_code flow) and dumped the complete
claim set of an actually-issued interactive access token via a temporary probe (added, run once,
reverted — `git status` confirmed clean afterward): `{sub, aud, acr, nbf, email_verified, scope,
roles, amr, iss, exp, iat, jti}` — **12 claims, `client_id` entirely absent.**

Confirmed via source (`spring-security-oauth2-authorization-server-1.5.1`'s `JwtGenerator.java`,
sources jar): it never adds a `client_id` claim for **any** grant type — confirmed by direct grep,
no match anywhere in the file. `TokenClaimsCustomizer` doesn't add one either (already fully read
in Phase 0). This means:

- **Interactive tokens**: 12 claims (missing `client_id`) — not 13 as Phase 4's original D1 assumed.
- **`client_credentials` tokens**: 9 claims (missing `client_id`, `roles`, `acr`, `email_verified`)
  — not 10 as Kimi's Phase 3 Finding 3 assumed (Finding 3 didn't independently verify `client_id`'s
  presence, only reasoned from the `TokenClaimsCustomizer` code path it read).
- **API-key-exchange tokens**: still the only path with the full 13 (`ApiKeyTokenIssuer` sets
  `client_id` explicitly — confirmed correct in Phase 0/3, unaffected by this finding).

**femi's gate decision:** drop the "canonical 13-claim section + `client_credentials` subset"
two-tier framing entirely (superseding the disposition of Kimi Finding 3 above). Document each of
the three issuance paths with its own explicit claim table instead — no single path is labeled
"canonical," avoiding the false implication that any one path is the normal case others deviate
from. Required Doc Structure below is revised accordingly.

## Required Doc Structure (D1, REVISED — supersedes the version above)

1. **The 13 claims L9 names** — one list, defined once (name + one-line meaning each), not tied to
   any single issuance path.
2. **Three per-path claim tables**, each listing exactly which of the 13 are present, plus each
   present claim's actual value/semantics for that path:
   - **Interactive** (`authorization_code`/`refresh_token`): 12 claims — all except `client_id`.
   - **`client_credentials`**: 9 claims — `iss`/`sub`/`aud`/`exp`/`iat`/`nbf`/`jti`/`scope`/`amr`
     only; `roles`/`acr`/`email_verified`/`client_id` all absent, with the rationale (a machine
     client has no user for the first three to describe; SAS itself never adds `client_id` to any
     JWT it mints, for any grant).
   - **API-key exchange**: all 13 — the only path where every L9 claim is actually present,
     because `ApiKeyTokenIssuer` assembles the full set manually (no real SAS grant runs at all).
3. **Access-token TTL** — 10 minutes, cited from `design.md` §6/`target-design.md` §6.
4. **Verification note** — names `TokenClaimsCustomizer.java` and `ApiKeyTokenIssuer.java` as
   ground truth for the interactive/`client_credentials` and API-key-exchange paths respectively,
   and `JwtGenerator.java` (Spring Authorization Server 1.5.1) as the reason `client_id` is absent
   from both SAS-issued paths.
5. **No-PII statement** — no email/name beyond `email_verified`, per L9/R48.

---

**Phase 4 re-confirmed FROZEN with the above addendum.** Proceed to Phase 5 (Implementation Plan).
