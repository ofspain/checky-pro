<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T34 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: document the real per-path access-token claims contract (T34)
```

## Commit message

```
auth: document the real per-path access-token claims contract (T34)

Writes contracts/api/token-claims.md, the doc L9/R48 require. The obvious
approach - list L9's 13 claims once as a universal contract - turned out to
be wrong: access tokens are minted along three genuinely different code
paths (interactive login, client_credentials service tokens, and API-key
exchange), and only the API-key path actually carries all 13. Interactive
and client_credentials tokens are both missing client_id, because Spring
Authorization Server's own JwtGenerator never adds that claim to any JWT it
mints, for any grant - a fact neither L9's own wording nor this service's
target-design doc had previously stated, found only by decoding a real,
actually-issued token from a real Docker-backed integration test rather
than trusting a source read.

Two more inaccuracies were caught and fixed inside this same task, before
merge: my own first draft claimed email_verified is "in practice always
true" for interactive tokens - directly contradicted by the same real token
just decoded (email_verified was false, because that test's authorization
request never asked for the email scope). Fixing a separate wire-shape gap
(aud's bare-string-vs-array shape, flagged for the interactive/
client_credentials paths) surfaced a second error nobody had checked: the
API-key path's aud was documented as a JSON array when it's actually the
same bare string every other path produces, once verified the same way.

One review finding (a space-separated vs. JSON-array divergence in the
scope claim between issuance paths) was independent review's own hypothesis
and did not hold up - a real JWT encoding through the exact pipeline both
paths use showed identical array shapes. Rejected with that evidence rather
than documented as a caveat.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Contracts**
- `contracts/api/token-claims.md` (new)

No production code changed. No `spec/` file touched. No migration. No test added — a deliberate,
human-gated scope decision (see Summary).

## Summary

Implements R48/L9: documents the exact access-token claim set, per issuance path rather than as one
universal list, since the universal framing both `L9` and `target-design.md` §6 use turns out not to
match reality for two of the three real issuance paths. No LOCKED decision is violated — L9's own
13-claim list is preserved verbatim as the canonical glossary; the per-path tables document which
subset of it each path actually carries, closing R48's "exactly the claims listed in it" at the
per-path level rather than asserting a single set every token would need to satisfy.

This task's most notable characteristic: for a documentation-only task, four separate factual
corrections were made during its own review process, and three of the four were caught only by
running something real (a JWT encoder or a live integration test), not by re-reading source more
carefully. A design-challenge finding calling the `scope` claim's wire format inconsistent between
paths was independently disproven the same way. The recurring lesson from this entire session's
code-side work — "prove it, don't just read it and reason about it" — held with equal force for a
pure Markdown deliverable.

One review suggestion (an automated test keeping the doc in sync with
`TokenClaimsCustomizer`/`ApiKeyTokenIssuer` as they evolve) recurred twice across review rounds and
was explicitly deferred as a follow-up task rather than implemented here — the doc itself names the
gap in its own Verification section. Building a reliable version of that check is harder than it
looks: unlike T33's structured `auth.yaml`, this doc is prose plus markdown tables, so a real
sync-check would need either fragile markdown parsing or a second hand-maintained expectation list,
a bigger design decision than this doc-only task's own scope.

## Testing performed

No shipped test exists for this task (frozen brief, gated decision, doc-only scope). Verification
was performed via four temporary probes across Phases 5, 7, and 9 — each written, run once against
real infrastructure, and reverted before the corresponding phase artifact was finalized:
1. A real JWT encoded through `NimbusJwtEncoder` with a `Set<String>` scope claim — proved the
   `scope` claim serializes as a JSON array (refuting Kimi's Phase 3 Finding 1).
2. A real, Docker-backed `SasLoginIntegrationTest` run, decoding an actually-issued interactive
   access token's complete claim set — the basis for Path 1's entire documented shape, and the
   evidence that later caught the `email_verified` self-review error.
3. A real JWT encoded through the same `NimbusJwtEncoder` mechanism `ApiKeyTokenIssuer` uses, with
   a one-element audience list — proved Path 3's `aud` is a bare string, not the array originally
   documented.
4. `git status`, run after every probe's cleanup, confirming no leftover scratch file in the final
   diff.

## Specification references

- **Task:** T34 — Token claims doc (`spec/auth-service/tasks.md`, task 34)
- **Requirements:** R48
- **LOCKED decisions:** L9
- **Named tests (`package.md` §8):** none scoped to this task

---

## Note for the reviewer: one file on this branch is T33's own, not T34's

`services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` shows as
changed in a naive `git diff` against T33's last commit, but the change is T33's own Phase 11
gap-closing work (already reviewed and documented in `T33/artifacts/10-test-generation.md`), which
simply landed in a commit chronologically after T33's own commit-message boundary — the same
git-hygiene quirk previously noted for T26/T27/T30/T32. Not part of this PR's own scope.

---

**Phase 13 complete — PR preparation written. T34 is ready for merge.**
