# auth-service — Access Token Claims Contract

This document is the contract referenced by L9 (`spec/auth-service/design.md`) and R48
(`spec/auth-service/requirements.md`): the exact claim set an `auth-service`-issued access token
carries. There is no single universal shape — **three distinct issuance paths exist, and each
carries a different subset of the 13 claims listed below.** No single path is "the normal case";
each is documented on equal footing so a resource server never assumes a claim is present just
because another path happens to include it.

## The 13 claims

**Presence by path** (Kimi Phase 8 Finding 3 — a reader must not assume a claim is present just
because it's in this table; see the three per-path sections below for the authoritative shape):

| Claim | Meaning | Path 1 (interactive) | Path 2 (`client_credentials`) | Path 3 (API-key) |
|---|---|---|---|---|
| `iss` | The issuer URI of this auth service. | ✓ | ✓ | ✓ |
| `sub` | The token's principal — an account UUID, or a client id, depending on issuance path. | ✓ | ✓ | ✓ |
| `aud` | The intended audience — a client id, serialized as a **bare JSON string** in every path today (each path constructs a one-element list; Nimbus's JWT serialization collapses a single audience to a string per RFC 7519 §4.1.3, not `["..."]` — verified against the same real token used to verify `scope`'s shape). | ✓ | ✓ | ✓ |
| `exp` | Expiry time (Unix seconds). | ✓ | ✓ | ✓ |
| `iat` | Issued-at time (Unix seconds). | ✓ | ✓ | ✓ |
| `nbf` | Not-before time (Unix seconds) — always equal to `iat` in this service. | ✓ | ✓ | ✓ |
| `jti` | A random, per-token UUID. | ✓ | ✓ | ✓ |
| `scope` | The authorized OAuth2 scopes, as a **JSON array of strings** (verified against a real, running token — see Verification below; not a space-separated string). | ✓ | ✓ | ✓ |
| `roles` | This service's effective, template-expanded RBAC role names, as a flat JSON array of strings. | ✓ | — | ✓ |
| `client_id` | The client the token was issued for. | — | — | ✓ |
| `amr` | Authentication Methods Reference — how the caller authenticated, as a JSON array of strings. | ✓ | ✓ | ✓ |
| `acr` | Authentication Context Class Reference — a single URN summarizing the authentication strength achieved. | ✓ | — | ✓ |
| `email_verified` | Whether the account's email is verified — semantics vary sharply by path (see below). | ✓ | — | ✓ |

**No PII beyond `email_verified`.** No access token issued by this service ever contains an email
address or a name; those live in the `id_token`/`/userinfo` response for interactive logins only.

---

## Path 1 — Interactive login (`authorization_code` / `refresh_token` grants)

**12 of 13 claims. Missing: `client_id`.**

| Claim | Value / semantics |
|---|---|
| `iss` | This service's configured issuer URI (Spring Authorization Server default). |
| `sub` | The account UUID. |
| `aud` | The client id (e.g. `checky-spa` for the first-party SPA), as a bare string — SAS default. |
| `exp` / `iat` / `nbf` | SAS defaults; 10-minute access-token TTL. |
| `jti` | Random UUID, SAS default. |
| `scope` | JSON array of the scopes authorized for this login (e.g. `["openid"]`). |
| `roles` | Effective roles, resolved fresh on every token issuance (never cached) — a role-template edit affects only future tokens. |
| `amr` | `["pwd"]`, or `["pwd","otp"]` if TOTP/recovery-code verification was completed this login. |
| `acr` | `urn:themistra:acr:pwd`, or `urn:themistra:acr:otp` if MFA was completed. |
| `email_verified` | `true` if and only if the `email` OIDC scope was authorized for this specific request — reflects scope authorization, not literally "has this account verified its email." Callers may see either value depending on what the client requested at `/oauth2/authorize`: a real, currently-issued token requesting only `scope=openid` (no `email` scope) carries `email_verified=false` — confirmed directly against a real token, not assumed. |
| `client_id` | **Absent.** Spring Authorization Server's `JwtGenerator` (1.5.1) never adds a `client_id` claim to any JWT it mints, for any grant type — confirmed directly in its source. Nothing in this service adds one back for this path. |

## Path 2 — Service-to-service (`client_credentials` grant)

**9 of 13 claims. Missing: `client_id`, `roles`, `acr`, `email_verified`.**

| Claim | Value / semantics |
|---|---|
| `iss` / `exp` / `iat` / `nbf` / `jti` | Same Spring Authorization Server defaults as Path 1. |
| `sub` | The service client's own client id — for `client_credentials`, the authenticated client itself is the token's principal. |
| `aud` | The same client id, as a bare string (identical default mechanism to Path 1). |
| `scope` | JSON array of the scopes configured for that service client. |
| `amr` | `["client_secret"]`. |
| `roles` | **Absent.** A service client has no RBAC roles of its own — there is no account to resolve them against. |
| `acr` | **Absent.** No authentication-context-strength concept applies to a client-secret-authenticated machine client the way it does to a human login. |
| `email_verified` | **Absent.** No email concept applies to a service client at all. |
| `client_id` | **Absent**, for the same service-wide reason as Path 1 — this is not specific to this grant type. |

## Path 3 — API-key exchange (`POST /api-keys/token`)

**13 of 13 claims — the only path where every L9 claim is present.**

No Spring Authorization Server grant runs for a key exchange at all (no `RegisteredClient`, no
`Authentication` principal exists for it) — this service assembles the complete claim set itself
for this one path, which is why it alone reaches all 13.

| Claim | Value / semantics |
|---|---|
| `iss` | The same configured issuer URI, read directly from configuration (no SAS grant to source it from). |
| `sub` | The account UUID that owns the exchanged API key. |
| `aud` | `"checky-api-key"` — a fixed literal, as a bare string (verified directly: `NimbusJwtEncoder` collapses this path's single-element audience list the same way it does for Paths 1-2). No real `RegisteredClient` is seeded for API-key exchanges, so both `aud` and `client_id` name this synthetic, unbacked client identifier directly. |
| `exp` / `iat` / `nbf` / `jti` | Assembled manually with the same semantics as the SAS-default paths; 10-minute TTL (independently configured, confirmed equal to Paths 1/2's default). |
| `scope` | The exchanged key's own `scopes` column, echoed verbatim as a JSON array — never widened or narrowed. |
| `roles` | Effective roles, resolved fresh on every exchange (same underlying role-resolution call as Path 1). |
| `amr` | `["api_key"]`. |
| `acr` | `urn:themistra:acr:api_key`. |
| `email_verified` | **Always `false`, hardcoded.** This is not a real assertion about the account's actual email-verification state — API-key exchange has no email-verification concept to report on at all. Do not treat this claim as meaningful for tokens issued via this path. |
| `client_id` | `"checky-api-key"` — the one path where this claim is actually present, precisely because there is no SAS grant machinery to omit it from. |

---

## Access-token TTL

10 minutes for every path (Path 1/2: `RegisteredClientSeeder`'s configured access-token
time-to-live; Path 3: `themistra.auth.api-key.token-ttl-minutes`, independently configured but
defaulting to the same 10 minutes). TTL is not one of L9's 13 claims — it constrains `exp`'s
distance from `iat`, not a claim of its own — included here for a resource server's convenience.

## Verification

This document's claims are ground-truthed against:
- `services/auth/src/main/java/com/themistra/auth/token/TokenClaimsCustomizer.java` (Paths 1-2).
- `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java` (Path 3).
- Spring Authorization Server 1.5.1's `JwtGenerator.java` (the reason `client_id` is absent from
  Paths 1-2 — verified directly in that library's own source, not assumed).

Path 1's complete claim set was additionally verified against a real, running integration test
(`SasLoginIntegrationTest`, a genuine Docker-backed `authorization_code` flow) that decoded an
actually-issued token and printed its full claim set, rather than relying on source-reading alone
— the path most likely to be assumed "the normal, complete case" by a future reader, and the one
where that assumption would be wrong. The `aud`/`scope` wire-shape claims above were each verified
directly by encoding a real JWT through the same `NimbusJwtEncoder` mechanism every path uses.

A future change to any of the three files above should update this document in the same change —
there is no automated test enforcing this today (this task is documentation-only); a lightweight
future test parsing both source files and asserting their claim sets stay consistent with this
document would close that gap if it becomes worth the cost.
