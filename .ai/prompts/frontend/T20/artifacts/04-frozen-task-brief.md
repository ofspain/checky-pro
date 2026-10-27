# frontend · T20 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN.** Downstream phases may not renegotiate this brief. Where this document and the Phase 2 brief
differ, **this one governs**.

**Task statement (verbatim from `spec/frontend/tasks.md`, task 20):**
> **Code exchange sends the verifier.** The token request carries the stored `code_verifier`. Verifies
> `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7.

---

## 1 · Frozen decisions

The Phase 2 blocker is closed. These are the numbered decisions; Phase 3's accepted amendments are folded in.

**T20-D1 · The token endpoint is contracted, not excepted.** The owner chose Exit 1 on 2026-10-26: `/oauth2/token`
is described in a contract under `contracts/api/` and reached through a generated client. L6 (`design.md:38-39`) is
satisfied **as written** — `contracts/api/*.yaml` is a glob — so there is no second named exception to L6, no lint
suppression, and `auth.yaml` is not edited. Exits 2 and 3 are closed and may not be revisited downstream.

**T20-D2 · The contract file is `contracts/api/oidc.yaml`, and it carries `/oauth2/token` only.** No other SAS path
is added; adding one later is a separate decision, not a drive-by. `auth.yaml:4-7`'s scope note stays exactly as
written, so `package.md` Q12(c) is not contradicted — the SAS surface remains absent *from `auth.yaml`*, which is
what that note says. On ownership, the repository is explicit and needs no guess: `.github/CODEOWNERS` maps
`/contracts/` to `@ofspain`, so this file requires owner review like every other contract. `contracts/README.md`'s
"OpenAPI specs per service" is not violated — SAS *is* the auth service's protocol surface; what is new is only
that the frontend is the first consumer to need it typed.

**T20-D3 · The generated module is `libs/ts/api-client/src/generated/oidc.ts`, published as `createOidcClient()`
from the existing single entry point.** `libs/ts/api-client/package.json`'s `exports` map keeps its one `.` entry;
no subpath export is added. `src/index.ts` gains the factory beside `createAuthClient`, built the same way
(`createClient<paths>({ baseUrl: '' })` — relative paths, no base URL, no env key: L9, L10). Two clients, one
import path, one package.

**T20-D4 · The `generate` script covers both contracts, and the drift test follows it.** `package.json:9` becomes
two `openapi-typescript` invocations (not a glob — the test asserts the documented command verbatim, and a glob
would hide which contract produced which file). `generatedClientCompiles.test.ts:69`, which asserts
`pkg.scripts.generate` equals the single-contract string exactly, is updated to the new exact string, and a
byte-for-byte drift case is added for `oidc.ts` mirroring the one at `:62-81`.

**The `SAS_PREFIXES` assertion is NOT loosened.** It reads `generatedPath` (`:18`), which is `auth.ts` alone, so
`oidc.ts` is outside its reach and it will not fail when the new file lands. Scoping it "to `auth.ts` only" would
be a no-op at best and, done carelessly, would delete a live guard. See the Phase 3 ledger, finding 4.

**T20-D5 · No custom `bodySerializer`. The request sets the content type and the stock serializer does the rest.**
This **corrects the Phase 2 addendum**, which assumed a per-request `bodySerializer` was needed. Measured in the
installed build: `openapi-fetch@0.17.0`'s `defaultBodySerializer` (`dist/index.mjs:476-487`) returns
`new URLSearchParams(body).toString()` when the request's `Content-Type` header is form-urlencoded, and
`dist/index.mjs:59-67` merges an explicitly-passed header **over** the lowest-priority `application/json` default.
So the call passes `headers: { 'Content-Type': 'application/x-www-form-urlencoded' }` and a plain object body, and
no transport is hand-written and no serializer is overridden.

**The header value must be exactly `application/x-www-form-urlencoded`.** The library compares with `===`
(`dist/index.mjs:482`), so a charset parameter, different case or stray space silently falls through to
`JSON.stringify` and a JSON content type — a body SAS will reject with no useful signal. Phase 10 pins this with a
case that asserts the serialized body, not merely the header.

**T20-D6 · The contract declares a form-encoded request and a JSON response, and defines no `refresh_token`.**
Request body content type `application/x-www-form-urlencoded` with the five fields; success response
`application/json` with `access_token`, `expires_in`, `id_token`, `scope`, `token_type`. The absence of
`refresh_token` from the schema is one of the three ways AC13 is pinned, and it is the one a reader sees first.

**T20-D7 · `redirectUri()` is exported from `authorizeRequest.ts` and used by both call sites.** Phase 2's
conditional ("only if the redirect URI must be published") is removed. The authorize request and the token request
must send byte-identical values against SAS's exact-match registration (L1); deriving the same string twice in two
modules is a divergence waiting for the first change to either. `authorizeRequest.ts` moves to Files to Modify
unconditionally.

**T20-D8 · `AuthorizationCodeGrant` keeps `{ code, state }`; `state` never reaches the wire.** The token endpoint
has no `state` parameter, and the exchange does **not** re-validate `state` — R8 is the call site's duty and stays
there (AC14). The field is kept because T17's and T18's suites observe the call's shape to prove a discarded
callback completes no exchange; narrowing the type would rewrite three suites to buy nothing. Silent renewal (L18)
supplies the stored value it already holds. The body carrying exactly five fields (AC4) is what stops the kept
field from drifting onto the wire.

**T20-D9 · Every failure becomes one opaque rejection.** SAS's token endpoint does **not** answer in RFC 9457
`application/problem+json` — it returns OAuth2 error responses (`400 invalid_grant` with `error` and
`error_description`), while `agents.md:66-67` assumes problem+json across the UI. The exchange therefore normalizes
every failure — 4xx, 5xx, network rejection, unparseable body, missing or non-string tokens — into a single
rejection carrying **no server-supplied detail**. `error_description` reaches no copy, no log, no console, and not
the rejection's own message. L4 is the reason: a visitor who can tell `invalid_grant` from a 5xx can enumerate.

**T20-D10 · No ADR.** `agents.md:104` requires one for *material design changes*, and T20-D1 is explicitly not a
change to L6 — it satisfies it. The precedent in `docs/adr/` supports this: `0003` and `0004` are both *narrow
exceptions* to a locked rule, which is the shape Exit 2 would have had and the owner did not choose. The numbered
decisions above are the design record; Phase 13 carries them into the PR.

---

## 2 · Phase 3 ledger — every finding, decided

| # | Finding | Decision |
|---|---|---|
| 1 | Contract name/scope/ownership undefined | **Accepted** → T20-D2. Ownership taken from `.github/CODEOWNERS`, not assumed. |
| 2 | Publication shape unresolved | **Accepted** → T20-D3, plus the lint ALLOWED entry below. |
| 3 | `generate` script change is mandatory, not optional | **Accepted** → T20-D4; `package.json` moves to Files to Modify. |
| 4 | `generatedClientCompiles.test.ts` must change | **Accepted, mechanism corrected** → T20-D4. |
| 5 | `redirect_uri` derivation under-specified | **Accepted** → T20-D7. |
| 6 | Token errors are not RFC 9457 | **Accepted** → T20-D9, AC20. |
| 7 | `access_token` validation missing from AC12 | **Accepted** → AC12 extended. |
| 8 | `refresh_token` absence has no pinning strategy | **Accepted** → AC13 names three mechanisms. |
| 9 | `bodySerializer` assumption untested | **Accepted, and the assumption itself corrected** → T20-D5, AC22. |
| 10 | Verifier storage read must be wrapped | **Accepted** → AC21. |
| 11 | Renewal reuse of the exchange hand-waved | **Accepted** → T20-D8. |
| 12 | Response content type not stated | **Accepted** → T20-D6. |
| 13 | Design record thin; suggests an ADR | **Accepted in part** → numbered decisions yes (§1); ADR no (T20-D10). |

**Finding 4, corrected.** Kimi's severity (High) and conclusion (the file must be modified) are right; its stated
failure mechanism is not. It predicts the `SAS_PREFIXES` assertion will fail "as soon as the new contract lands"
and asks for it to be scoped to `auth.ts`. That assertion iterates `pathKeys` parsed from `generatedPath`
(`:108`, `:111-115`), and `generatedPath` is `libs/ts/api-client/src/generated/auth.ts` (`:18`) — it never reads
`oidc.ts`, so it neither fails nor needs scoping. The assertion that **will** fail is `:69`,
`expect(pkg.scripts.generate).toBe('openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts')`,
the moment finding 3's change lands. Both findings therefore land on the same line, and the fix is T20-D4.

**Finding 2, scope note.** `createOidcClient` is added to the L6 lint test's `ALLOWED` table
(`lintRejectsHandWrittenBackendFetch.test.ts:52-55`) so the sanctioned client is named, not inferred. This is
documentary rather than corrective: the entries exist to prove oxlint does *not* flag code that merely mentions
`fetch`, and a `createOidcClient().POST(...)` call mentions none. `frontend/.oxlintrc.json` is still NOT modified.

**Finding 13, the rejected half.** Writing an ADR would record a design change that is deliberately not being made,
and would re-open in `docs/adr/` the question the owner closed. Rejected on that ground alone, not on cost.

---

## 3 · Scope

Unchanged from Phase 2 §Scope, with one addition to **In**: authoring `contracts/api/oidc.yaml` and publishing the
generated client from it (T20-D1 through T20-D6). **Out** is unchanged and binding — holding the access token is
task 21, proving no token reaches storage is task 22, deleting the three stored items is task 23.

## 4 · Files

**Create**
- `contracts/api/oidc.yaml` — the token endpoint only (T20-D2). **CODEOWNERS review required.**
- `libs/ts/api-client/src/generated/oidc.ts` — generated, never hand-edited.
- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the named test
  (`package.md:111`), verifier half.

**Modify**
- `frontend/src/auth/codeExchange.ts` — the seam becomes the real exchange.
- `frontend/src/auth/authorizeRequest.ts` — export `redirectUri()` (T20-D7). Unconditional.
- `libs/ts/api-client/src/index.ts` — add `createOidcClient()` (T20-D3). Unconditional.
- `libs/ts/api-client/package.json` — the `generate` script (T20-D4). Unconditional.
- `frontend/src/api/generatedClientCompiles.test.ts` — `:69`'s exact string, plus a drift case for `oidc.ts`
  (T20-D4). Unconditional.
- `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` — one `ALLOWED` entry (finding 2).
- `frontend/src/auth/CallbackScreen.tsx` — only if the call site's failure routing changes.
- `frontend/src/auth/callbackValidatesNonce.test.tsx`, `callbackWithMismatchedStateIsDiscarded.test.tsx` — only
  where a real transport changes what they may assert, and then **split, never rewritten** (T19-D6).

**NOT to modify**
- Anything under `spec/`. · `contracts/api/auth.yaml` — its scope note is load-bearing (T20-D2).
- `libs/ts/api-client/src/generated/auth.ts` — generated; CI fails on drift.
- `frontend/src/auth/idTokenNonce.ts` — T19's decision is settled.
- `frontend/src/pwa/**` — the token response is already NetworkOnly by construction (AC19).
- `frontend/.oxlintrc.json` — weakening the L6 rule is not a way to satisfy L6.
- `docs/adr/**` (T20-D10).

## 5 · Acceptance criteria

AC1–AC19 carry over from Phase 2 §Acceptance Criteria unchanged except as stated here.

- **AC4 — amended.** The form-encoded body carries **exactly** `grant_type`, `code`, `redirect_uri`, `client_id`,
  `code_verifier` and **no sixth field** — `state` among the absent (T20-D8). Asserted on the serialized body.
- **AC5 — amended.** `redirect_uri` is byte-identical to the authorize request's **because both read one exported
  helper** (T20-D7); a test proves one source, not two agreeing strings.
- **AC12 — amended.** A response missing `id_token` **or `access_token`**, or carrying a non-string either, is a
  rejection, never an escaping throw.
- **AC13 — amended.** The absence of `refresh_token` is pinned three ways: the contract schema defines none
  (T20-D6); a stubbed 200 that *does* carry one proves it reaches neither the returned value nor anything else;
  and a source scan finds the literal in no production file. A comment is not a pin.
- **AC20 — new.** Every failure normalizes to one opaque rejection (T20-D9). No `error`, `error_description`,
  status text or response body reaches copy, the console, a log, the DOM, or the rejection's message. Proven with
  a stubbed `400 invalid_grant` whose `error_description` is a distinctive string, then scanned for everywhere.
- **AC21 — new.** The verifier read is wrapped: `sessionStorage` can **throw**, and there is no error boundary in
  production source. A throwing read becomes the same rejection as any other failure, mutates no storage, and
  does not escape.
- **AC22 — new.** The client package itself proves form encoding: a case in `generatedClientCompiles.test.ts`
  drives `createOidcClient()` with a form-encoded request and asserts method, relative path, the exact
  `Content-Type`, and the serialized body — independent of any auth logic (T20-D5).
- **AC23 — new.** `oidc.ts` reproduces byte-for-byte from `oidc.yaml` under the documented `generate` command, and
  `auth.ts` still reproduces from `auth.yaml` (T20-D4). The `SAS_PREFIXES` guard on `auth.ts` still passes
  unmodified.

## 6 · Required tests, constraints

Phase 2 §Required Tests and §Constraints carry over in full, extended by AC20–AC23 above. The wire is observed
through the established `stubBrowserFetch` pattern (`generatedClientCompiles.test.ts:26-44`), which the L6 lint
test's `ALLOWED` table explicitly sanctions (`lintRejectsHandWrittenBackendFetch.test.ts:56`). Every block carries
a control proving its assertion is reached. T19's 123 cases and T18's 134 keep passing, and AC17 stands: the run's
**exit code** is the verdict, not its counts.

## 7 · Open questions — all resolved or deferred with an owner

- **OQ-1 · The L6 blocker.** **RESOLVED** by the owner, 2026-10-26 → T20-D1.
- **OQ-2 · Where the access token lives.** **DEFERRED to task 21**, which owns it by `tasks.md`. T20 hands it
  back and introduces no module-level state (Phase 2 §State Changes). There is still no composition root; task 21
  inherits that, and it is recorded in Phase 0 §Gaps.
- **OQ-3 · Pinning `refresh_token` absence against a *live* server.** **DEFERRED, owner: the author.** AC13 pins
  it three ways against the contract, a stub and the source. None of those would notice SAS changing its mind in
  production; that needs a smoke-level check, which is outside a frontend unit suite.
- **OQ-4 · Whether anything beyond the discard happens on a failed exchange.** **RESOLVED: no.** T20-D9 and AC11
  — one outcome, by L4. `CallbackScreen.tsx:112-115`'s invitation to "route it somewhere more specific" is
  declined, and Phase 6 should amend that comment to say so.

**Scope, files and acceptance criteria are final.**

---

## Next — Phase 5 — implementation plan.
