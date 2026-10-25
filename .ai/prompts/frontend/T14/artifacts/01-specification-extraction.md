# frontend · T14 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | Sentence | Which half is T14's |
|---|---|---|
| **R6** (`requirements.md:21-22`) | When a visitor chooses sign-in, the SPA starts the OIDC authorization code flow **with PKCE** and renders no password field. | **The PKCE half only.** The no-password-field half is T16 (`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`). |
| **R20** (`requirements.md:66-67`) | Profile from `/userinfo` with scopes `openid profile email`; account status from `GET /accounts/me`. | **Not T14's.** Listed only because T15's test maps to it (`package.md:174`), which is how the T14/T15 boundary resolves — see below. |

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L1** (`design.md:8-10`) | Public OIDC client, **authorization code with PKCE**, no client secret in the bundle. T14 implements the PKCE primitive this rests on. The `state`/`nonce` and redirect-URI clauses belong to T15/T17/T18. |
| **L16** (`design.md:58-60`) | **The only place the verifier may be stored is sessionStorage**, as one of `state`, `nonce`, `code_verifier` and the post-login return target — single-use, deleted at callback. This is the entire carve-out. |
| **L3** (`design.md:13`) | Tokens in memory only. The verifier is not a token, but it is the credential that redeems one, so the same spirit applies and L16 is the operative rule. |
| **L13** (`design.md:50-51`) | The confirmed stack names **no OIDC library**. PKCE is hand-written; adding a library would extend a LOCKED decision and needs the owner. |
| **L14** (`design.md:52-55`) | `callback` and `sign-in` already exist as declared route paths (`routes.tsx:8-18`). T14 attaches no screen and adds no route. |
| **L12** (`design.md:49`) | Phase additivity — T14 adds files, replaces nothing. |
| `agents.md:19-20` | Authorization code with PKCE, no client secret in the bundle, exact-match redirect URIs. |
| `agents.md:79-81` | Never in a URL, never in `localStorage`, logs or analytics; sessionStorage only for L16's items. **The challenge goes in the authorize URL by design; the verifier must never appear in one.** |

## Files involved

**To read, not modify:**

| File | Why |
|---|---|
| `src/api/sessionBoundary.ts:7-14` | `SessionTokens.renew()` is silent re-authentication, i.e. **a second PKCE exchange**. Whatever T14 publishes is consumed on the renewal path too, not only at sign-in. |
| `src/api/crossTabRenewal.ts:4-7` | `LocalSession extends SessionTokens` — the same point. |
| `src/app/routes.tsx:6-18` | `sign-in` and `callback` already declared. |
| `services/.../RegisteredClientSeeder.java:53-60` | `requireProofKey(true)`: the server rejects a missing challenge but **accepts `plain`** (phase 0, §5). |

**New (the spec expects these):**

| File | Basis |
|---|---|
| `frontend/src/auth/<module>.ts` | `design.md:203` plans `src/auth/` as "OIDC client, session owner, guards". T14 is its first file. |
| `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts` | `package.md:173` maps the name to R6. |

The module's exact name is a phase-2/5 decision, not a spec fact.

## Dependencies

| Kind | Item | Status |
|---|---|---|
| Platform API | `crypto.getRandomValues` | **present and working** under this jsdom (phase 0) |
| Platform API | `crypto.subtle.digest('SHA-256', …)` | **present and working**; `SHA-256('abc')` → 32 bytes, `0xba…` |
| Platform API | `TextEncoder`, `sessionStorage`, `btoa` | present |
| Config keys | **none** | a verifier needs randomness, not configuration — which is why the OQ-1 env-key conflict does not block T14 |
| Contracts | **none** | `/oauth2/authorize` and `/oauth2/token` are SAS endpoints, deliberately not in `auth.yaml` (`design.md:133-135`) |
| npm dependency | **none added** | hand-written per L13 |
| Normative external source | **RFC 7636** §4.1 (verifier), §4.2 (challenge), **Appendix B** (test vector) | not in the repository; see AC3 |

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| **AC1** | A generated verifier's length is within **43–128** inclusive, and **every** character is drawn from the RFC 7636 unreserved set `A–Z a–z 0–9 - . _ ~`. Asserted over many generated verifiers, not one. | R6, L1 |
| **AC2** | The verifier comes from a **cryptographic** source: the implementation calls `crypto.getRandomValues` and never `Math.random`, `Date.now`, or a counter. Two successive verifiers differ, and over many generations there are no duplicates. | R6, L1 |
| **AC3** | The challenge equals `base64url(SHA-256(ASCII(verifier)))` **without padding**, proven against **RFC 7636 Appendix B's published vector**: verifier `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → challenge `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`. *(Verified computationally in this phase, not quoted from memory.)* | R6, L1 |
| **AC4** | The challenge is **base64url, not base64**: it contains no `=`, `+` or `/`. A standard-base64 implementation fails this even when its digest is correct. | R6 |
| **AC5** | The derivation is genuinely **S256**, not "a hash": a verifier differing in one character yields a wholly different challenge, and the challenge is never the verifier itself (which is what `plain` would send). This is the criterion the backend does **not** enforce (phase 0 §5). | R6, L1 |
| **AC6** | **The verifier does not leak.** Nothing in the module writes it to `localStorage`, to a URL, or to `console`; the only sanctioned store is sessionStorage under L16. Whether T14 *performs* that storage is a phase-2 scope decision — the prohibition holds either way. | L16, L3, `agents.md:79-81` |
| **AC7** | The character distribution shows **no modulo bias** — each character of the chosen alphabet appears with frequency consistent with uniform, within a stated tolerance, over a large sample. | R6 (entropy intent of RFC 7636 §4.1) |
| **AC8** | Suite green against the recorded baseline (**412/414**, both failures the known environmental T07 pair); the total rises by exactly the cases T14 adds; **no T01–T13 file modified**. | L12 |

AC7's form depends on the generation scheme, which phase 2 decides (see Open Questions).

## Tests required

**Named test (`package.md:173`):** `pkceVerifierMeetsRfc7636` → R6. One file,
`frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts`.

**Boundary cases the ACs imply:**

- length exactly 43 and exactly 128 accepted; 42 and 129 rejected by the published validator, if one is published;
- a verifier containing a character outside the unreserved set (`+`, `/`, `=`, a space, a non-ASCII code point) is rejected;
- the Appendix B vector, as the one assertion that cannot be satisfied by a wrong hash;
- a one-character change in the verifier changes the challenge (AC5);
- challenge contains none of `=`, `+`, `/` (AC4);
- a large sample: all distinct (AC2), all valid (AC1), per-character frequency within tolerance (AC7);
- `Math.random` is not named in the module's source, and `crypto.getRandomValues` is (AC2, source-level, the T12/T13 pattern);
- the module names no storage API other than the L16-sanctioned one, and writes no URL (AC6).

**Not T14's tests:** `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField` (T16),
`authorizeRequestsExactlyTheDeclaredScopes` (T15), `oidcRoundTripEndToEnd` (`package.md:254`, an end-to-end test
mapped to L1, owned by no Phase-1a unit task).

## Open Questions

**No blockers.** Nothing in `package.md` §11 gates this task: Q6 (environment keys and the route set) is **closed**
(2026-10-10), and Q1/Q2/Q3/Q5/Q13/Q15 concern MFA, payments, notifications, refresh tokens and revocation.

1. **The T14/T15 boundary — resolved here, recorded for phase 2.** T14's statement ends "…and its S256 challenge is
   sent on authorize", which overlaps T15's "the authorize request carries … `code_challenge_method=S256`".
   `package.md` §8 settles it: `pkceVerifierMeetsRfc7636` → **R6** (`:173`) and
   `authorizeRequestsExactlyTheDeclaredScopes` → **R20** (`:174`), and each test is named after its subject. So
   **T14 owns generating the verifier and deriving the challenge, and proving the derivation is S256; T15 owns the
   assembled authorize request**. T14 publishes the pair and builds no URL. Flagged rather than assumed silently,
   because guessing wrong costs a duplicated or orphaned assertion.
2. **The generation scheme — a genuine phase-2 design decision.** Carried from phase 0, now with the arithmetic:
   - **base64url of 32 random bytes** — exactly 43 characters, ~256 bits, **bias-free** (64 divides 256), and it is
     what the auth service's own test helper does (`SasLoginIntegrationTest.java:649-662`). But it never emits `.`
     or `~`, so AC1's "characters from the unreserved set" would be satisfied over a 64-character subset.
   - **the full 66-character set** — 43 characters give ~259.9 bits, but `byte % 66` is **biased**: `256 % 66 = 58`,
     so 58 of 66 characters are over-represented by a factor of 4/3. Rejection sampling fixes it; a naive
     implementation will not.
   Both satisfy R6 as written. Phase 2 must choose and say why, because **AC1 and AC7 mean different things under
   each**.
3. **What the drift guard anchors to — resolved.** Every prior task guards a sentence in `spec/frontend/*`, but
   43–128, the unreserved set and S256 are **RFC 7636's**, and the spec states them only in `tasks.md`'s task line.
   The better anchor is the RFC's own **Appendix B test vector** (AC3): it is external, immutable, needs no spec
   file, and a wrong implementation cannot satisfy it by accident. The task line is additionally cheap to guard and
   phase 2 may choose to.
4. **Carried, not blocking: OQ-1 (T01's `import.meta.env` ban vs §4c's four public keys).** T14 needs no
   configuration key, so it proceeds. **The next auth task that wants `VITE_OIDC_ISSUER`,
   `VITE_OIDC_CLIENT_ID` or `VITE_OIDC_REDIRECT_URI` will hit T01 `:130`** — which forbids any source under
   `src/**` from naming `import.meta.env` at all, although R58 and §4c expressly permit those three. **Owner
   decision, needed before T15/T16.**
5. **Recorded, no action: the backend accepts `plain`.** `requireProofKey(true)`
   (`RegisteredClientSeeder.java:60`) makes a challenge mandatory, but `S256` appears nowhere in the auth service's
   main source and Spring Authorization Server has no switch to forbid `plain`. **The S256 requirement is the SPA's
   alone to keep**, which is why AC5 exists as its own criterion rather than being folded into AC3.
