<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T34 · Phase 8 — Independent Code Review

Consumed `artifacts/07-self-review.md` and `contracts/api/token-claims.md`.
Cross-checked against `TokenClaimsCustomizer.java`, `ApiKeyTokenIssuer.java`, and
`RegisteredClientSeeder.java`. No production code to review — the task is documentation only.

---

## Finding 1 — `email_verified` "in practice always true" claim is factually wrong

**Evidence:** `token-claims.md` Path 1 table states `email_verified` is "`true` if and only if the
`email` OIDC scope was authorized" but then adds the parenthetical: "in practice this is always
`true` for any token that exists." This is contradicted by:

- The source code: `TokenClaimsCustomizer.java:61-62` sets `email_verified` to
  `context.getAuthorizedScopes().contains(OidcScopes.EMAIL)`. If the authorized scopes do not
  include `email`, the claim is `false`.
- The Phase 5 empirical probe cited in the self-review: a real interactive token decoded from
  `SasLoginIntegrationTest` showed `email_verified=false` because the test's authorization request
  only requested `scope=openid`.

**Recommendation:** Remove the "in practice always true" sentence entirely. Replace it with a
statement that the claim is `true` exactly when the `email` scope was authorized for that specific
token request, and that callers may therefore see either value depending on what the client
requested at `/oauth2/authorize`.

**Confidence:** High.

---

## Finding 2 — `aud` wire shape is not documented despite being ambiguous

**Evidence:** `token-claims.md` describes `aud` as "The intended audience — a client id in every
path" but does not state its JSON wire shape. The same Phase 5 probe that verified `scope` is a
JSON array also showed `aud=checky-spa` as a **bare string**, not `["checky-spa"]`. This happens
because:

- `ApiKeyTokenIssuer.java:76` constructs `aud` as `List.of(CLIENT_ID)`.
- SAS's `JwtGenerator` similarly constructs a single-element list for `aud`.
- Nimbus JWT serialization collapses a single-element audience list to a bare string per RFC 7519
  §4.1.3.

Since the doc already applies the same rigor to `scope` (explicitly calling out its JSON-array
shape), leaving `aud` unspecified is an inconsistency that could mislead a resource server into
expecting an array.

**Recommendation:** Add a wire-shape note to `aud`: a single string for every path today, because
every path constructs it as a one-element list and Nimbus serializes that as a bare string. If a
future path ever has multiple audiences, the doc should be updated.

**Confidence:** High.

---

## Finding 3 — The top-level "13 claims" table does not clearly indicate which claims are path-dependent

**Evidence:** The table lists all 13 claims with generic descriptions. While the path-specific
sections then enumerate subsets, a reader scanning only the top table could assume `roles`,
`acr`, and `email_verified` are always present. The prose below does say "three distinct issuance
paths exist, and each carries a different subset," but this is easy to miss.

**Recommendation:** Add a column to the top-level table indicating the presence/absence of each
claim per path (e.g., "Path 1", "Path 2", "Path 3" columns with checkmarks or "—"), or add a
sentence directly under the table summarizing which claims are omitted by which path.

**Confidence:** Low.

---

## Finding 4 — No CI-enforced check that the doc stays in sync with code

**Evidence:** The Verification section names the source files that are the ground truth, but there
is no automated test or build step that fails if those files change without a corresponding doc
update. `agents.md` says "Contract tests validate against `contracts/`"; T33 added such tests for
its contract files, but T34 is doc-only and has none.

**Recommendation:** This is acceptable for the task's literal scope, but add an explicit note in
the doc (or a follow-up task) that a future change to `TokenClaimsCustomizer` or `ApiKeyTokenIssuer`
should include a matching update to `token-claims.md`. Optionally, suggest a lightweight future test
that parses the two source files and asserts the documented claim sets remain consistent.

**Confidence:** Low.

---

## Non-Issues Confirmed

- **Three-path structure:** correctly distinguishes interactive, `client_credentials`, and API-key
  exchange.
- **Claim counts:** Path 1 = 12, Path 2 = 9, Path 3 = 13, matching the code.
- **`scope` shape:** correctly documented as a JSON array for all paths, consistent with the Phase 5
  empirical probe.
- **`client_id` absence from Paths 1–2:** correctly attributed to SAS's `JwtGenerator` not adding it.
- **No PII beyond `email_verified`:** accurately stated.
- **Path 3 `email_verified` hardcoded `false`:** correctly documented as not meaningful.
- **TTL:** correctly noted as 10 minutes for every path.
- **Verification section:** appropriately cites the ground-truth source files.

---

## Open Questions

1. Should the `aud` wire-shape note be added before this doc is merged, given that the same concern
   already drove an explicit `scope` shape note? (Finding 2.)
2. Is the factual error in Finding 1 significant enough to require a corrected doc before Phase 9,
   or can it be folded into the Phase 9 review-resolution? Given its high confidence, it should be
   fixed before merge.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human disposition) on approval.
