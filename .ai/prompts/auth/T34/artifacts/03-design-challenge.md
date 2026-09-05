<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T34 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md`, `spec/auth-service/design.md` (L9),
`TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`, and
`RegisteredClientSeeder.java`. No conflicts with `agents.md` standing rules.

---

## Finding 1 · The `scope` claim format is inconsistent between issuance paths

**Severity:** High

**Evidence:**

- `ApiKeyTokenIssuer.java:81` sets `.claim("scope", List.copyOf(scopes))` — a JSON array of
  strings.
- `TokenClaimsCustomizer.java` does not override `scope` for interactive or `client_credentials`
  grants, so Spring Authorization Server's default applies. SAS's default `JwtGenerator` emits
  `scope` as a **space-separated string** of the authorized scopes.

This means two access tokens issued by the same service can expose the same conceptual claim in
two different shapes. A resource server that expects one shape will fail to parse the other. If
`token-claims.md` documents only "`scope` — authorized scopes" without capturing this divergence,
it fails R48's "exactly" requirement and creates an interoperability trap.

**Recommended brief amendment:** Require the doc to explicitly state the `scope` claim shape per
issuance path: array for API-key-exchange tokens, space-separated string for SAS-issued
authorization_code/refresh_token/client_credentials tokens. If the long-term intent is one shape,
file a follow-up task to unify them; this task must document reality, not hide the inconsistency.

---

## Finding 2 · `sub` and `aud` semantics differ materially across grant types

**Severity:** Medium

**Evidence:**

- Interactive (`authorization_code`/`refresh_token`): `sub` is the account UUID
  (`context.getPrincipal().getName()`); `aud` is set by SAS (typically the client id or audience
  configured in `RegisteredClient`).
- `client_credentials`: `sub` is the service client's client id; `aud` is set by SAS.
- API-key exchange: `sub` is the account UUID; `aud` is hardcoded to `["checky-api-key"]`
  (`ApiKeyTokenIssuer.java:76`).

L9 lists `sub` and `aud` as canonical claims but does not describe these semantic differences. A
resource server relying on `sub` as "always an account UUID" will misinterpret service-to-service
client_credentials tokens.

**Recommended brief amendment:** Document the principal/audience semantics per issuance path in
`token-claims.md`.

---

## Finding 3 · L9's "exactly 13 claims" wording conflicts with the client_credentials exception

**Severity:** Medium

**Evidence:** `design.md` L9 says: "Access-token claims are exactly: `iss`, `sub`, `aud`, `exp`,
`iat`, `nbf`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`, `email_verified`." The word
"exactly" suggests every token contains all 13. D1 correctly observes that `client_credentials`
tokens omit `roles`, `acr`, and `email_verified`. This is not a code defect, but the doc must
reconcile the locked wording with reality.

**Recommended brief amendment:** Structure `token-claims.md` with:
- A "Canonical claim set (13 claims)" section for interactive/API-key tokens.
- A clearly-labeled "`client_credentials` service-token subset" section listing the 10 claims
  actually present and explicitly naming the 3 omitted claims and why.

This preserves L9 while making the per-grant-type variation transparent.

---

## Finding 4 · Per-grant-type `amr`/`acr` values are not mentioned in the brief

**Severity:** Medium

**Evidence:**

- `TokenClaimsCustomizer.java:44`: `client_credentials` → `amr = ["client_secret"]`.
- `TokenClaimsCustomizer.java:59`: interactive without OTP → `amr = ["pwd"]`, `acr = "urn:themistra:acr:pwd"`.
- `TokenClaimsCustomizer.java:59-60`: interactive with OTP → `amr = ["pwd", "otp"]`,
  `acr = "urn:themistra:acr:otp"`.
- `ApiKeyTokenIssuer.java:36-37`: API-key exchange → `amr = ["api_key"]`,
  `acr = "urn:themistra:acr:api_key"`.

These values are part of the contract resource servers must handle. The brief only notes the
omissions for `client_credentials`, not the actual values for any path.

**Recommended brief amendment:** Require the doc to enumerate the exact `amr`/`acr` values for
each issuance path.

---

## Finding 5 · `email_verified` semantics differ and should be explicit

**Severity:** Low

**Evidence:**

- Interactive: `email_verified` reflects whether the `email` scope was authorized
  (`context.getAuthorizedScopes().contains(OidcScopes.EMAIL)`).
- API-key exchange: hardcoded to `false` (`ApiKeyTokenIssuer.java:86`).
- `client_credentials`: omitted entirely.

**Recommended brief amendment:** Document the `email_verified` semantics per path so resource
servers do not assume it always indicates the user's email verification status.

---

## Finding 6 · No verification test means the doc can drift from code

**Severity:** Low

**Evidence:** The brief states "No new test proposed — matches the task's own literal scope."
While the task is doc-only, `agents.md` says "Contract tests validate against `contracts/`" and
T33 added tests for its contract files. A future change to `TokenClaimsCustomizer` or
`ApiKeyTokenIssuer` could silently invalidate `token-claims.md`.

**Recommended brief amendment:** Add a note in the doc's "Verification" section pointing to the
specific source files (`TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`) that are the ground
truth, and suggest (not require) a future task add a lightweight test that asserts those files'
claim sets remain in sync with the doc.

---

## Non-Issues Confirmed

- **No production code changes:** the brief correctly limits scope to authoring the markdown file.
- **L9 claim list:** the 13 canonical claims (`iss`, `sub`, `aud`, `exp`, `iat`, `nbf`, `jti`,
  `scope`, `roles`, `client_id`, `amr`, `acr`, `email_verified`) are accurately captured.
- **No PII beyond `email_verified`:** aligns with `agents.md` and L9; the doc must preserve this.
- **`client_credentials` omission rationale:** D1's reasoning (service-to-service tokens have no
  user to attach `roles`/`acr`/`email_verified` to) is sound.
- **File location:** `contracts/api/token-claims.md` matches the task statement and existing
  `contracts/README.md` structure.

---

## Open Questions

1. Is the `scope` claim-format inconsistency between API-key tokens and SAS-issued tokens a known,
   accepted divergence that the doc should preserve, or should it be flagged for unification in a
   follow-up task? (Finding 1.)
2. Should `token-claims.md` include the access-token TTL (10 minutes) even though it is not one of
   L9's 13 claims? It is already stated in `agents.md` and `target-design.md`.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human approval / brief fold) on approval.
