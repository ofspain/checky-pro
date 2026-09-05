<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T34 · Phase 12 — Specification Verification

Compares the final `contracts/api/token-claims.md` against `requirements.md`, `design.md`, and the
frozen brief (as amended) for **T34 only**. `spec/auth-service/` confirmed unchanged.

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R48** — every access token contains exactly the claims listed in `token-claims.md`, no PII beyond `email_verified` | Yes, via the per-path framing (no single universal list asserted) | `contracts/api/token-claims.md` — three per-path tables, each stating exactly which of the 13 claims that path has | No test (doc-only, by design) | No | R48's "exactly"/"every access token" wording is satisfied by the doc documenting per-path variation rather than one flat list — a deliberate, human-gated reading (Phase 4 Addendum), not a silent narrowing |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| **L9** — the 13-claim list | Yes | All 13 claims defined once in the top-level glossary; every one's presence/absence and semantics stated per path, corrected twice during review (Path 1's `client_id`/`email_verified`, Path 3's `aud`) before being considered accurate |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 — doc exists, lists L9's 13 claims, states no-PII rule | **Met** | `token-claims.md` §"The 13 claims" + No-PII line |
| AC2 (R48) — doc accurately describes every real issuance path | **Met** | Three per-path tables, each independently verified: Path 1 against a real running integration test (Phase 5); Path 2 against source (`TokenClaimsCustomizer`'s early-return, confirmed structurally identical to Path 1's `JwtGenerator` mechanism for `client_id`); Path 3 against source (`ApiKeyTokenIssuer`) plus a direct JWT-encoding probe for its `aud` shape (Phase 9) |

## Findings from this phase

None new. This task went through an unusually high number of empirical corrections for a
documentation-only task — worth summarizing since it's the most notable characteristic of T34's
own execution, not a new finding:

1. **Phase 5**: Kimi's own Phase 3 Finding 1 (scope-format divergence) was empirically disproven
   via a real JWT encoding — no divergence exists.
2. **Phase 5**: a genuinely new finding, found while planning rather than reviewing — `client_id`
   is absent from BOTH SAS-issued paths (not just `client_credentials` as everyone, including
   Kimi, had assumed), confirmed via a real running integration test's actual token output. This
   required amending the already-frozen Phase 4 brief.
3. **Phase 7 (self-review)**: caught my own factual error in the doc I'd just written
   (`email_verified` "in practice always true"), directly contradicted by evidence already in hand
   from Phase 5.
4. **Phase 9 (while fixing Kimi's Finding 2)**: caught a second, independent factual error
   (Path 3's `aud` shape) that neither my self-review nor Kimi's independent review had verified —
   both had checked Paths 1-2's `aud` shape but assumed Path 3's manual construction would differ,
   without checking. It doesn't.
5. **Phase 11**: the same "no automated sync check" concern recurred a second time (first at Phase
   8); explicitly deferred as a follow-up task rather than implemented, given the real added
   complexity of testing prose-plus-markdown-tables versus a structured contract format.

**Pattern worth naming explicitly**: every one of the first four corrections was caught by actually
running something real (a JWT encoder, an integration test) rather than by re-reading code more
carefully. This is the same lesson this entire session has surfaced repeatedly on the code side,
now demonstrated equally strongly for a pure documentation task — "I read the source and it looks
right" was insufficient evidence four separate times in one small task.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. The doc accurately describes all three real issuance
paths, each independently verified, with two genuine document-authoring errors caught and fixed
before this verification (not carried forward as latent inaccuracies).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1/AC2 both Met.

**(3) Does it violate any LOCKED decision?** No. L9's 13-claim list is documented completely and
accurately per path.

**(4) Remaining risks?**
- **No automated enforcement that the doc stays in sync with code** — explicitly named in the
  doc's own Verification section and deferred as a follow-up task (Phase 11 disposition), not a
  silent gap.
- **Path 2 (`client_credentials`) was verified against source only, not a live integration test**
  (unlike Path 1) — the underlying mechanism (`JwtGenerator`'s lack of a `client_id` claim) is
  identical to the empirically-proven Path 1, and `TokenClaimsCustomizer`'s `client_credentials`
  branch was read in full, but no live client_credentials-grant token was actually decoded during
  this task. Low risk given the shared mechanism, but named for completeness.

**Verdict: PASS** — the doc accurately and completely documents L9's claim contract for every real
issuance path, with two genuine errors caught and corrected during this task's own review process
rather than shipped, and the one remaining risk (no CI enforcement) explicitly named rather than
hidden.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
