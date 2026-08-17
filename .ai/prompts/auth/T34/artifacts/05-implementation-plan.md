<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T34 · Phase 5 — Implementation Plan

No code — one Markdown file. This plan is the exact content outline, with every per-path value
already verified against source and (for the interactive path) a real, running integration test,
so Phase 6 writes the doc directly from this table rather than re-deriving anything.

## Files to create

- `contracts/api/token-claims.md` — the task's sole deliverable.

## Files to modify / Public methods / Private methods / Entities / Repositories / Services

None — pure documentation, no code in any of these categories.

## Content outline (Phase 4 Addendum's revised D1, restated as exact per-path data)

### Section 1 — The 13 L9 claims (defined once)

`iss`, `sub`, `aud`, `exp`, `iat`, `nbf`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`,
`email_verified` — one line each stating what the claim means in this service's context.

### Section 2 — Three per-path tables

**Interactive (`authorization_code` / `refresh_token`) — 12 of 13, missing `client_id`:**

| Claim | Value / semantics |
|---|---|
| `iss` | This service's issuer URI (SAS default) |
| `sub` | Account UUID (`context.getPrincipal().getName()`) |
| `aud` | The client id (`checky-spa` for the SPA) — SAS default, `JwtGenerator.java`: `.audience(Collections.singletonList(registeredClient.getClientId()))` |
| `exp`/`iat`/`nbf` | SAS defaults; 10-minute TTL (`RegisteredClientSeeder.ACCESS_TOKEN_TTL`) |
| `jti` | Random UUID, SAS default |
| `scope` | JSON array of authorized scopes (verified empirically: `NimbusJwtEncoder` serializes `Set<String>` as a JSON array, e.g. `["openid"]` — not a space-separated string) |
| `roles` | Effective roles, resolved fresh per issuance (`RoleService.resolveEffectiveRoles`), template-expanded, flattened array |
| `amr` | `["pwd"]`, or `["pwd","otp"]` if TOTP/recovery-code was used this login |
| `acr` | `urn:themistra:acr:pwd` or `urn:themistra:acr:otp` |
| `email_verified` | `true` iff the `email` OIDC scope was authorized this request |
| `client_id` | **Absent.** SAS's `JwtGenerator` never adds this claim to any JWT it mints, for any grant type (confirmed via source; no other SAS component adds it either). |

**`client_credentials` — 9 of 13:**

| Claim | Value / semantics |
|---|---|
| `iss`/`exp`/`iat`/`nbf`/`jti` | Same SAS defaults as above |
| `sub` | The service client's own client id (SAS's own client_credentials convention — the authenticated client is the "principal") |
| `aud` | Same client id (SAS default, identical mechanism to the interactive path) |
| `scope` | JSON array of the client's configured scopes |
| `amr` | `["client_secret"]` |
| `roles` / `acr` / `email_verified` | **Absent** — `TokenClaimsCustomizer.customizeAccessToken` returns immediately after setting `amr` for this grant type; there is no user/session for these three to describe |
| `client_id` | **Absent**, same reason as the interactive path (SAS-wide, not grant-specific) |

**API-key exchange (`POST /api-keys/token`) — 13 of 13, the only complete path:**

| Claim | Value / semantics |
|---|---|
| `iss` | Same issuer, read from config directly (no SAS grant, so `ApiKeyTokenIssuer` sources it itself) |
| `sub` | Account UUID owning the exchanged key |
| `aud` | `["checky-api-key"]` — a fixed literal; no real `RegisteredClient` exists for this path (frozen brief D2, cited in `ApiKeyTokenIssuer`'s own Javadoc) |
| `exp`/`iat`/`nbf`/`jti` | Assembled manually by `ApiKeyTokenIssuer`, same semantics as SAS defaults |
| `scope` | The exchanged key's own `scopes` column, echoed verbatim as a JSON array |
| `roles` | Effective roles, resolved fresh (same `RoleService` call as the interactive path) |
| `amr` | `["api_key"]` |
| `acr` | `urn:themistra:acr:api_key` |
| `email_verified` | **Always `false`**, hardcoded — API-key exchange has no email-verification concept at all; this is not a real assertion about the account's email state |
| `client_id` | `"checky-api-key"` — the only path where this claim is actually present, because `ApiKeyTokenIssuer` sets it explicitly (no SAS grant to omit it from) |

### Section 3 — TTL

10 minutes, all paths (`RegisteredClientSeeder.ACCESS_TOKEN_TTL`, `ApiKeyProperties.tokenTtlMinutes()`
— confirm both configs agree at Phase 6 write-time, not assumed).

### Section 4 — Verification note

Names `TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`, and (for the `client_id` omission)
Spring Authorization Server 1.5.1's `JwtGenerator.java` as ground truth. States this doc was
verified against a real, running integration test's actual token output (`SasLoginIntegrationTest`),
not source-reading alone, for the interactive path specifically — the one path most likely to be
assumed "the normal case" by a future reader.

### Section 5 — No-PII statement

One line: no email address or name in any access token; `email_verified` is the only
identity-adjacent signal, and even it carries different meaning per path (Section 2).

## Unit / integration tests required

None (frozen brief: doc-only task, no named test, Verification note substitutes for a test).

## Execution order

1. Write Section 1 (the flat 13-claim glossary) — no path-specific content, lowest risk.
2. Write the interactive path's table (Section 2a) — already fully empirically verified.
3. Write the `client_credentials` table (Section 2b) — source-verified, not re-run empirically
   (the mechanism, `JwtGenerator`'s lack of a `client_id` claim, is identical to the already-proven
   interactive path; re-deriving via a live client_credentials test would only reconfirm behavior
   this task doesn't need to independently re-verify).
4. Write the API-key exchange table (Section 2c) — directly transcribed from `ApiKeyTokenIssuer.java`'s
   own explicit `.claim(...)` calls, already read in full at Phase 0.
5. Write TTL, Verification note, No-PII sections.
6. Final read-through cross-checking every stated value against this plan's own table one more time
   before considering the doc done.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
