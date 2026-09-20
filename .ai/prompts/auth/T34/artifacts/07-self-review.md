<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T34 · Phase 7 — Self Review

Reviewed `contracts/api/token-claims.md` against the frozen brief (as amended) and the actual
evidence gathered in Phases 0/3/5. Thread-safety, transaction boundaries, money types, and
idempotency don't apply to a Markdown file — N/A rather than silently skipped.

## Finding 1 — Path 1's `email_verified` note is factually wrong, contradicted by my own Phase 5 evidence

**Severity:** High

**Evidence:** `token-claims.md`, Path 1 table, `email_verified` row: "reflects scope
authorization, not literally 'has this account verified its email' ... in practice this is always
`true` for any token that exists." This is false. The Phase 5 empirical probe
(`SasLoginIntegrationTest`, real token dump, already recorded in this task's own artifacts)
produced: `{..., email_verified=false, scope=[openid], ...}` — the test's own authorization request
only requests `scope=openid` (`SasLoginIntegrationTest.java:586`,
`.queryParam("scope", "openid")`), never requesting the `email` scope at all. So the one real,
concrete example this task has in hand directly disproves the "in practice always true" claim I
wrote — a genuine, currently-issued interactive token has `email_verified=false`.

**Recommendation:** Remove the "in practice always true" sentence. State only the accurate,
unconditional rule: `email_verified` reflects whether the `email` scope was authorized *for that
specific request*, with no claim about what's typical in practice (which the doc has no
authoritative basis to assert — that depends on what the SPA client actually requests at
`/oauth2/authorize` time, which this doc-only task's evidence doesn't establish either way).

## Finding 2 — `aud`'s wire shape (single string vs. JSON array) is documented for `scope` but not for `aud`, despite the same evidence being available

**Severity:** Medium

**Evidence:** The doc explicitly documents `scope`'s wire shape ("a JSON array of strings ... not a
space-separated string") specifically to close Kimi's Phase 3 Finding 1. The same Phase 5 probe
that proved this also showed `aud=checky-spa` — a **bare string**, not a single-element array,
even though both `JwtGenerator.java` (`Collections.singletonList(...)`) and `ApiKeyTokenIssuer`
(`List.of(CLIENT_ID)`) construct `aud` as a one-element `List`. Nimbus's JWT serialization
collapses a single-element audience list to a bare string (standard RFC 7519 §4.1.3 behavior). The
doc's `aud` row says only "The intended audience — a client id in every path," with no wire-shape
note, an inconsistency given `scope` got one specifically because this task's own review process
flagged wire-shape ambiguity as a real interoperability concern.

**Recommendation:** Add a wire-shape note to `aud`'s row (or the Section 1 glossary entry): a
single string today for every path, since every path constructs it as a one-element list and
Nimbus collapses that to a bare string — not a JSON array, matching the same rigor already applied
to `scope`.

## Correctness — verified, not merely inspected

- Every other claim value in the doc (interactive `amr`/`acr`/`roles`, `client_credentials`'s
  9-claim set, API-key exchange's 13-claim set, the `client_id` omission from both SAS-issued
  paths) traces directly to source already read in full, or to the Phase 5 empirical probe. No
  other discrepancy found between the doc's prose and the underlying evidence.
- The TTL cross-check (10 minutes, both configs) was independently re-verified at Phase 6
  write-time per the plan's own execution order, not just carried over from an earlier phase.

## No findings on

Enumeration-safety/secret-handling (the doc correctly frames `email_verified`'s hardcoded-`false`
API-key behavior and the raw-token exception elsewhere are both already-decided, accurately
described, not new leaks); readability/complexity (three parallel per-path tables with identical
column structure, easy to compare); module boundaries (N/A, no code).

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
