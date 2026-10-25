# frontend · T14 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T14 — PKCE verifier |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Build the PKCE primitive for the SPA's OIDC client: generate an RFC 7636 code verifier, derive its S256 challenge,
and publish both for the authorize request that T15 assembles. First file under `frontend/src/auth/`.

## Purpose

The SPA is a **public** OIDC client — no client secret in the bundle (L1, `agents.md:19-20`) — so PKCE is the only
thing binding an authorization code to the client that requested it. Every sign-in and **every silent renewal**
(`sessionBoundary.ts:7-14`: `renew()` re-opens `/oauth2/authorize`) runs through this primitive. The backend sets
`requireProofKey(true)` but names `S256` nowhere in its main source, so **a `plain` challenge would be accepted**:
the strength of the exchange is decided here and nowhere else.

## Scope

**In**
- Generating a code verifier that meets RFC 7636 §4.1.
- Deriving its S256 challenge per RFC 7636 §4.2, base64url-encoded without padding.
- A published validator for the verifier's shape (length and character set), so the rule is testable against
  invalid input and not only against our own output.
- Publishing the pair, and the `S256` method name as a constant, for T15 to consume.

**Out**
- Assembling or sending the authorize request — **T15** (`authorizeRequestsExactlyTheDeclaredScopes`).
- `state` and `nonce` — T15/T17/T18.
- Persisting the verifier in sessionStorage — the owner is the task that writes the L16 set as a unit, alongside
  `state`, `nonce` and the return target. T14 publishes a value; it stores nothing.
- Any screen, route or UI — T16 owns the sign-in screen.
- The token exchange — T20.
- Any npm dependency, any configuration key.

## Business Rules

- **R6** (`requirements.md:21-22`) — the SPA starts the authorization code flow **with PKCE**. (The no-password-field
  half is T16.)
- **R20** (`requirements.md:66-67`) — not this task's; named only because `package.md:174` maps T15's test to it,
  which is how the T14/T15 boundary was resolved.

## Locked Decisions

- **L1** (`design.md:8-10`) — public client, authorization code with PKCE, no client secret in the bundle.
- **L16** (`design.md:58-60`) — sessionStorage may hold `code_verifier` and only the listed items. T14 writes
  nothing, so the carve-out is a prohibition here, not an instruction.
- **L3** (`design.md:13`) — tokens in memory only; the verifier is the credential that redeems one.
- **L13** (`design.md:50-51`) — the confirmed stack names no OIDC library; PKCE is hand-written.
- **L12** (`design.md:49`) — additive: new files only.
- **L14** (`design.md:52-55`) — no route added; `sign-in` and `callback` already exist (`routes.tsx:8-18`).
- `agents.md:79-81` — never in a URL, `localStorage`, logs or analytics. The **challenge** goes in the authorize URL
  by design; the **verifier** must never appear in one.

## Dependencies

| Kind | Item |
|---|---|
| Platform | `crypto.getRandomValues`, `crypto.subtle.digest('SHA-256', …)`, `TextEncoder` — all verified present and working under this jsdom (phase 0) |
| npm | **none added** |
| Config keys | **none** — which is why OQ-1 (T01 vs §4c) does not block this task |
| Contracts | **none** — `/oauth2/authorize` and `/oauth2/token` are SAS endpoints, deliberately outside `auth.yaml` (`design.md:133-135`) |
| External normative | RFC 7636 §4.1, §4.2, Appendix B |

## Inputs

None. The verifier is generated from the platform CSPRNG; nothing is read from configuration, storage, the URL or
the network.

## Outputs

- A code verifier: a string of **43** characters from the RFC 7636 unreserved set.
- Its S256 challenge: `base64url(SHA-256(ASCII(verifier)))`, unpadded.
- The method name `S256`, as a published constant so T15 cannot spell it as a literal.
- A validator answering whether a given string is a well-formed verifier.

Challenge derivation is asynchronous (`crypto.subtle.digest` returns a promise).

## State Changes

**None.** No storage is written, no module-level mutable state is kept, nothing is cached. Every call returns a
fresh value.

## Files to Create

- `frontend/src/auth/pkce.ts` — the module.
- `frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts` — the named test (`package.md:173`).

## Files to Modify

**None.**

## Files NOT to Modify

`frontend/src/api/**`, `frontend/src/app/**`, `frontend/src/pwa/**`, `frontend/src/i18n/**`,
`frontend/src/main.tsx`, `frontend/vite.config.ts`, `frontend/.oxlintrc.json`, `frontend/package.json`,
`spec/**`, `contracts/**`, `services/**`, `infra/**`, and every T01–T13 test.

## Acceptance Criteria

Carried from phase 1, unchanged in substance:

- **AC1** — a generated verifier's length is within 43–128 inclusive and every character is from
  `A–Z a–z 0–9 - . _ ~`; asserted over many generated verifiers.
- **AC2** — the verifier comes from `crypto.getRandomValues`; the module names no `Math.random`, `Date.now` or
  counter; successive verifiers differ and a large sample holds no duplicate.
- **AC3** — the challenge equals `base64url(SHA-256(ASCII(verifier)))` unpadded, proven against **RFC 7636
  Appendix B**: `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`.
- **AC4** — the challenge contains no `=`, `+` or `/`: base64url, not base64.
- **AC5** — the derivation is genuinely S256: a one-character change in the verifier changes the challenge
  entirely, and the challenge is never equal to the verifier (which is what `plain` sends). **The criterion the
  backend does not enforce.**
- **AC6** — the verifier does not leak: the module writes no `localStorage`, no URL, no `console`, and names no
  storage API.
- **AC7** — no modulo bias: character frequency over a large sample is consistent with uniform within a stated
  tolerance.
- **AC8** — suite green against the **412/414** baseline (both failures the known environmental T07 pair), total up
  by exactly the cases added, no T01–T13 file modified.

## Required Tests

One file, `pkceVerifierMeetsRfc7636.test.ts`, covering:

1. the Appendix B vector (AC3) — the one case a wrong hash cannot pass;
2. challenge has no `=`, `+`, `/` (AC4);
3. a one-character verifier change changes the challenge; challenge ≠ verifier (AC5);
4. a large sample: all valid, all distinct, per-character frequency within tolerance (AC1, AC2, AC7);
5. **the validator against deliberately invalid input** — lengths 42 and 129 rejected, 43 and 128 accepted, and
   strings containing `+`, `/`, `=`, a space, a non-ASCII code point rejected (AC1, and see Constraints);
6. a source-level scan: the module names `crypto.getRandomValues`, and names none of `Math.random`, `localStorage`,
   `document.cookie`, `console`, `http` (AC2, AC6);
7. the method constant is exactly `S256` (AC5).

## Constraints

**Generation scheme — 32 random bytes, base64url-encoded without padding, giving exactly 43 characters.** Chosen
over drawing from the full 66-character set because it is **bias-free by construction** (64 divides 256), it is
what the auth service's own reference helper does (`SasLoginIntegrationTest.java:649-662`), and it is the smaller
amount of code to get wrong. Drawing from all 66 characters via `byte % 66` is **biased** — `256 % 66 = 58`, so 58
of the 66 characters are over-represented by 4/3 — and fixing that needs rejection sampling, which buys two extra
characters (`.` and `~`) and no security. 32 bytes is ~256 bits, matching RFC 7636 §4.1's entropy intent.

**The cost of that choice, and how it is paid.** base64url never emits `.` or `~`, so an AC1 assertion that reads
"every character is from the unreserved set" would be satisfied over a 64-character subset and could never fail —
the same vacuity that bit T12's AC7 and T13's AC8. It is paid the way both of those were: **the character-set rule
is asserted against the published validator fed deliberately invalid input**, not only against our own output.
Required test 5 is that payment and is not optional.

**Security.** `crypto.getRandomValues` only; no `Math.random` anywhere. The verifier never enters a URL, log,
`localStorage`, analytics or an error message. The challenge is the only half that may appear in a URL.

**Module boundaries.** `src/auth/` imports nothing from `src/app/`, `src/pwa/` or `src/features/`. The module is
pure apart from the two platform crypto calls; it reads no DOM, no storage and no configuration.

**Null handling.** The validator accepts an arbitrary `string` and returns a boolean — it never throws. Challenge
derivation rejects a verifier that fails validation rather than hashing it.

**Lint.** `.oxlintrc.json` has no `overrides` and runs exactly 2 rules (T07's AC5 asserts both), so no exemption is
available. The module names neither `fetch` nor `XMLHttpRequest`, which it has no reason to.

**Thread-safety / transactions.** Not applicable; no shared state, no I/O.

## Open Questions

**No blockers.**

- **For phase 3 to challenge, not a blocker:** the generation scheme above. Both schemes satisfy R6 as written; the
  brief states a choice with its reasoning so the review has something specific to attack. If 43 fixed characters is
  judged too narrow, the alternative is a length drawn from 43–128 with rejection sampling over all 66 characters —
  more code, no more entropy, and AC7 becomes the assertion that catches it being done wrong.
- **Carried, owner, not T14's:** **OQ-1** — T01 `:130` forbids any source under `src/**` from naming
  `import.meta.env`, while `design.md` §4c and R58 expressly permit `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID` and
  `VITE_OIDC_REDIRECT_URI`. T14 needs no key and proceeds. **T15 or T16 will need one, and will fail a T01
  assertion on a change the spec permits.** The owner must settle it before then.
- **Recorded, no action:** the backend accepts `plain` (`RegisteredClientSeeder.java:60` requires a challenge;
  `S256` is absent from its main source). AC5 exists as its own criterion because of this.
