<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T34 · Phase 0 — Repository Understanding

## 1. Architecture summary

`auth-service` issues JWT access tokens via Spring Authorization Server (SAS) 1.5.1, RS256-signed,
10-minute TTL. Claim customization happens in one of two independent code paths depending on how
the token was issued — there is no single, unified "claims assembly" function:

1. **`token/TokenClaimsCustomizer.java`** — a SAS `OAuth2TokenCustomizer<JwtEncodingContext>` bean,
   invoked automatically by SAS's own `JwtGenerator` for every SAS-issued access token (i.e.
   `authorization_code`, `refresh_token`, and `client_credentials` grants against a real
   `RegisteredClient`). It only ever *adds* claims on top of whatever SAS's own defaults already
   populate (`iss`, `sub`, `aud`, `exp`, `iat`, `jti`, `scope`, `client_id` are SAS framework
   defaults, not set by this class).
2. **`apikey/ApiKeyTokenIssuer.java`** — a completely separate, manual `JwtClaimsSet.builder()`
   assembly for `POST /api-keys/token` exchanges. Its own Javadoc explains why: no SAS grant runs
   for a key exchange (no `RegisteredClient`, no `Authentication` principal), so
   `TokenClaimsCustomizer` structurally cannot fire here — this was a gate-approved deviation at
   T25's own frozen brief (D1), not an oversight.

## 2. Existing code this task touches (read-only — this is a documentation task)

- `token/TokenClaimsCustomizer.java` — read in full. See finding below: only sets `amr` for
  `client_credentials` grants; sets `roles`/`amr`/`acr`/`email_verified` for interactive
  (`authorization_code`/`refresh_token`) grants.
- `apikey/ApiKeyTokenIssuer.java` — read in full. Manually sets the **complete** L9 claim list
  (`iss`, `sub`, `aud`, `iat`, `nbf`, `exp`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`,
  `email_verified` — `email_verified` hardcoded `false` always, since API-key exchange has no email
  concept at all).
- `token/RegisteredClientSeeder.java` — read in full. Confirms `client_credentials` is a **real,
  actively-seeded, production grant type** (service-to-service clients), not hypothetical — so the
  reduced claim set it produces (below) is live behavior, not a dead code path.
- `spec/auth-service/design.md` L9 and `services/auth/docs/architecture/target-design.md` §6 — both
  already read; both state one flat, universal 13-claim list with no per-grant-type qualification.

## 3. Established patterns to follow

- **Documentation-as-contract**: `contracts/` files are hand-authored/verified against real code
  (established firmly by T33, the immediately preceding task) — this task follows the exact same
  posture, just for a Markdown doc instead of an OpenAPI/JSON-Schema file. No existing precedent
  for a *contract test* validating a Markdown doc's prose against runtime claims (T33's
  `AuthOpenApiContractTest`/event contract tests all validate structured YAML/JSON against
  structured Java shapes — a claims *list in prose* is a different kind of artifact).
- `contracts/api/` currently contains `auth.yaml` (T33) and (per `design.md`'s own file tree)
  `token-claims.md` is the next file expected in that same directory.

## 4. Testing conventions

No named test exists for this task (`package.md` §8 has no entry mapping to T34 — confirmed via
the task's own Phase 0 header). Whether ANY test is warranted (e.g., a plain-JUnit test parsing
`token-claims.md` and cross-checking its claimed list against `TokenClaimsCustomizer`'s/
`ApiKeyTokenIssuer`'s actual `.claim(...)` calls via reflection or string-matching) is a genuine
open question for Phase 1/2 — this task's own statement says only "Write
`contracts/api/token-claims.md`," with no "add tests" clause (unlike T33's explicit "Add contract
tests" instruction).

## 5. Known gaps / unknowns

**The single most important finding this phase: L9's own text and `target-design.md` §6 both
describe ONE universal 13-claim set, but the actual runtime behavior has (at least) three distinct
claim shapes depending on how the token was issued:**

| Issuance path | Grant type | Gets `roles`/`acr`/`email_verified`? | `amr` value |
|---|---|---|---|
| Interactive login (SPA) | `authorization_code` / `refresh_token` | Yes (all three) | `["pwd"]` or `["pwd","otp"]` |
| Service-to-service | `client_credentials` | **No — none of the three** | `["client_secret"]` |
| API-key exchange | N/A (no SAS grant at all) | Yes (all three; `email_verified` hardcoded `false`) | `["api_key"]` |

This is directly observable from `TokenClaimsCustomizer.customizeAccessToken`'s own early-return
for `client_credentials` (only `.claim("amr", ...)` is set; the method returns before reaching the
`roles`/`acr`/`email_verified` lines that only execute for the interactive branch). Neither `L9`
nor `target-design.md` §6 mentions this distinction — both read as if every access token carries
every listed claim unconditionally. I do not know whether this is: (a) an intentional design
choice never written down (a machine-to-machine token conceptually has no "user" to have
roles/email-verification about, so omitting them is arguably correct), or (b) a genuine drift
between the spec's own claim contract and the implementation that this documentation task is
supposed to surface, not paper over. This is squarely Phase 1/2's question to resolve — flagging
here rather than silently picking an interpretation.

No other gaps identified — `iss`/`sub`/`aud`/`exp`/`iat`/`nbf`/`jti`/`scope`/`client_id` are
confirmed present (either via SAS defaults or `ApiKeyTokenIssuer`'s explicit assembly) across every
path checked.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
