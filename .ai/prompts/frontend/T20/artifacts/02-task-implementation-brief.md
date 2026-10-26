# frontend · T20 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

This brief is the **only** specification the implementation and review phases use.

---

## Task

**T20 — Code exchange sends the verifier.** The token request carries the stored `code_verifier`. Verifies
`callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7.

## Purpose

A callback that has survived both halves of R8 and carries an authorization code is worth nothing until the code is
exchanged for tokens. T19 built the check that the returned ID token belongs to this tab; T20 builds the exchange
that produces it. The verifier is the whole point: `checky-spa` is registered with
`ClientAuthenticationMethod.NONE`, so there is no client secret and **the stored `code_verifier` is the only proof
that the code is being redeemed by the same tab that requested it.** Send the wrong one, or a fresh one, and the
exchange is an unauthenticated code redemption.

## Scope

**In**

- Replacing the throwing seam in `codeExchange.ts` with a real `POST /oauth2/token`.
- Reading the stored `code_verifier` and putting it on the wire.
- The request's method, path, content type and form fields, including a `redirect_uri` identical to the one the
  authorize request sent.
- Consuming the response as data, and producing the ID token T19's check already consumes.
- Defining what happens on every failure of the exchange, with one visitor-visible outcome.
- The named test's verifier half, and keeping T18's and T19's suites honest against a real transport.

**Out**

- **Holding the access token anywhere** — task 21. T20 hands it back; it does not decide where it lives.
- **Proving no token reaches any storage** — task 22.
- **Deleting `state`, `nonce`, `code_verifier`** — task 23. All three must survive T20.
- The post-login return target and its allowlist — tasks 24 and 25.
- Silent renewal, cross-tab coherence, sign-out — R21, R22, later tasks.
- Any change to T19's nonce decision, or to R8's ordering.

## Business Rules

- **R7** (`requirements.md:23-24`) — exchange the code at `/oauth2/token` with the PKCE verifier. The `nonce`
  clause is built (T19); the in-memory clause is task 21.
- **R8** (`requirements.md:25-26`) — a missing or mismatched `state` completes **no exchange**. T20 must leave that
  property intact and newly falsifiable, because there is now a real transport to observe.

## Locked Decisions

- **L1** (`design.md:8-10`) — public client, PKCE, **no client secret**, exact-match redirect URI.
- **L3** (`design.md:13-20`) — tokens never reach storage, logs or analytics; no `refresh_token` is issued.
- **L4** (`design.md:21-26`) — the visitor may not learn which failure occurred.
- **L6** (`design.md:38-39`) — every backend call goes through a client generated from `contracts/api/*.yaml`.
  **See Open Questions: this is the task's one blocker.**
- **L9** (`design.md:44-45`) — single origin, relative paths, SAS endpoints included.
- **L10** (`design.md:46`) — no secret and no non-public environment key in the bundle.
- **L11** (`design.md:47-48`) — catalogue-only strings.
- **L13** (`design.md:51-52`) — confirmed stack; no new runtime dependency.
- **L14** (`design.md:53-56`) — SPA routes under `/app`; a backend path is never a SPA route.
- **L16** (`design.md:58-60`) — the sessionStorage carve-out; the three items are deleted at callback by **task 23**,
  not here.
- **L17** (`design.md:61-62`) — API routes are NetworkOnly; the token response is never cached.
- **L18** (`design.md:63-64`) — one renewal per tab; the exchange must be reusable by renewal, not callback-only.

## Dependencies

- `contracts/api/auth.yaml` — and specifically its exclusion: `:4-7` states the SAS protocol endpoints are
  **intentionally not documented** there.
- **Measured request** (`infra/local/bin/smoke:113-116`): `POST /oauth2/token`,
  `application/x-www-form-urlencoded`, fields `grant_type=authorization_code`, `code`, `redirect_uri`,
  `client_id`, `code_verifier`. No `Authorization` header.
- **Measured response** (T19 probes): `access_token`, `expires_in`, `id_token`, `scope`, `token_type`; **no
  `refresh_token`**; a replayed code gives `400 invalid_grant`.
- `src/auth/authorizeRequest.ts` — `CLIENT_ID` (`:26`), `AUTHORIZE_STORAGE_KEYS.codeVerifier` (`:38`), the private
  `redirectUri()` (`:59-62`).
- `src/api/sessionBoundary.ts:11-14` — `SessionTokens`, the shape task 21 will fill.
- `openapi-fetch@0.17.0` — `bodySerializer` per request and globally (`dist/index.d.ts:13-14,115`).
- `infra/local/edge/Caddyfile:12-16` — `/oauth2/*` is same-origin.
- **Config keys: none.** `src/auth` reads no `import.meta.env` and must keep reading none.

## Inputs

- The callback query's `code` and `state`, already trimmed and already admitted by R8's two checks.
- The stored `code_verifier`, read from `AUTHORIZE_STORAGE_KEYS.codeVerifier` at exchange time.
- `CLIENT_ID` and the derived redirect URI, both from `authorizeRequest.ts`.

## Outputs

- On success: the token response's usable fields, including the `id_token` T19's check consumes and the
  `access_token` task 21 will hold.
- On any failure: a rejection the call site routes to the single `discard` outcome.

## State Changes

**None in T20.** Nothing is written, deleted or cleared. The three L16 items are **read** and must survive both the
success and the failure path; deleting them is task 23. No module-level state is introduced — where the access
token lives is task 21's decision, and T20 pre-empting it would be work task 21 has to undo.

## Files to Create

- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the named test
  (`package.md:111`), verifier half.
- **Possibly a contract file and a generated client module** — determined by the blocker below.

## Files to Modify

- `frontend/src/auth/codeExchange.ts` — the seam becomes the real exchange.
- `frontend/src/auth/authorizeRequest.ts` — only if the redirect URI must be published rather than derived twice.
- `frontend/src/auth/CallbackScreen.tsx` — only if the call site's failure routing changes.
- `libs/ts/api-client/src/index.ts` and its `package.json` — only under option 1 of the blocker.
- `frontend/src/auth/callbackValidatesNonce.test.tsx`, `callbackWithMismatchedStateIsDiscarded.test.tsx` — only
  where a real transport changes what they may assert, and then **split, never rewritten** (T19-D6).

## Files NOT to Modify

- Anything under `spec/`.
- `libs/ts/api-client/src/generated/**` — generated; CI fails on drift.
- `src/auth/idTokenNonce.ts` — T19's decision is settled.
- `src/pwa/**` — the token response is already NetworkOnly by construction.
- `frontend/.oxlintrc.json` — weakening the L6 rule is not a way to satisfy L6.

## Acceptance Criteria

**AC1** The request carries the **stored** verifier, read by the published key — not fresh, not copied, not
in-memory from authorize. · **AC2** Proven over **at least two unrelated stored values**, so nothing passes against
a constant. · **AC3** `POST` to the relative path `/oauth2/token`; no absolute address. · **AC4** Form-encoded body
with `grant_type`, `code`, `redirect_uri`, `client_id`, `code_verifier`. · **AC5** `redirect_uri` is byte-identical
to the authorize request's, derived once rather than spelled twice. · **AC6** No client secret and no client
authentication of any kind. · **AC7** The call goes through the generated client; `lintRejectsHandWrittenBackendFetch`
still reports every banned form. · **AC8** The `code` is the callback query's, after R8 admitted it. · **AC9**
Nothing written, deleted or cleared; all three L16 items survive success **and** failure. · **AC10** No token
value or segment reaches storage, a URL, the DOM, the tab title or a log. · **AC11** Every failure —
`400 invalid_grant`, 5xx, network, unparseable body — produces the **same** visitor outcome as R8's discard: copy,
path, history action, query. · **AC12** A response missing `id_token`, or carrying a non-string one, is a rejection,
never an escaping throw. · **AC13** No `refresh_token` is read, stored or expected, and its absence is pinned by a
test rather than a comment. · **AC14** T19's nonce check still runs on the real response, in the same place, and
still cannot be hoisted above the state check. · **AC15** No new user-visible string; `signIn.discarded` reused
unchanged. · **AC16** No `import.meta.env`, no absolute address, no new dependency. · **AC17** The whole suite
passes with the one known environmental failure and no other, **verified by the run's exit code**, not only its
counts. · **AC18** The exchange is callable by something other than the callback screen, so silent renewal (L18)
can reuse it. · **AC19** `isNetworkOnlyRequest` still answers true for `/oauth2/token`.

## Required Tests

**Named:** `callbackValidatesNonceAndKeepsAccessTokenInMemory` (`package.md:111` → R7). Shared with task 21; T20
owns the verifier, the request shape, the failure outcome and the `refresh_token` absence.

- **Verifier** — stored and sent; **two unrelated stored values**; absent; empty; a storage read that **throws**.
- **Wire** — method, relative path, content type, every form field, and the absence of any client credential.
  Observed through the established `stubBrowserFetch` pattern (`generatedClientCompiles.test.ts:26-43`), which the
  L6 lint test's ALLOWED table explicitly sanctions (`lintRejectsHandWrittenBackendFetch.test.ts:58`).
- **Response** — the measured success shape; `400 invalid_grant`; 5xx; a network rejection; a 200 with a non-JSON
  body; a 200 with no `id_token`; a 200 whose `id_token` is not a string.
- **Ordering** — no exchange at all on a missing or mismatched `state`, now against a real transport.
- **Non-leakage** — no token part in either storage, the URL, the DOM, the tab title or the console.
- **Source scans** — no absolute address, no `import.meta.env`, no hand-spelled storage key, no `refresh_token`,
  no `console.`, no module-level mutable state in new code; each scan with a synthetic violation.
- **Controls** — every block carries one proving its assertion is reached, not merely satisfied.
- **Regression** — T19's 123 cases and T18's 134 keep passing; affected assertions **split, not rewritten**.

## Constraints

- **Security.** The verifier is the only client proof; it may reach the token request and nothing else — never a
  URL, a log, a React prop or an error message. No client secret may appear in the bundle (L1, L10). The response
  carries two tokens, and L3 means neither may be written anywhere. L4 means the visitor learns nothing about which
  step failed, so error detail must not travel into copy.
- **Module boundaries.** `src/auth` owns the flow; `libs/ts/api-client` owns transport; `src/api` owns the session
  boundary. T20 must not reach across these: no transport hand-written in `src/auth`, and no auth logic added to
  the client package beyond publishing a client.
- **Null handling.** Every value crossing the network boundary is `unknown` until checked — T19's F1 was exactly
  this mistake, a typed field trusted because the compiler could not see the wire. A missing or non-string
  `id_token` must reject, not throw. Storage reads must be wrapped: `sessionStorage` can **throw**, and there is
  **no error boundary anywhere in production source**.
- **Concurrency.** One callback, one exchange. The screen's `handled` latch already guarantees one attempt per
  mount, including under StrictMode; T20 must not add a second path that could fire concurrently.
- **Transactions.** None — there is no persistence in this package.
- **Performance.** One network round trip, no retry. A retry would re-send a code the server has already consumed
  (`400 invalid_grant`, measured) and would delay the visitor's single outcome.

## Open Questions

**BLOCKER · The token endpoint is in no contract, and L6 requires a client generated from one.**

`/oauth2/token` appears in no file under `contracts/`, and `auth.yaml:4-7` states the SAS protocol surface is
**intentionally** absent — a position `package.md` Q12(c) re-affirmed when it closed, treating that scope note as
authoritative. So this is not a gap to be fixed by editing `auth.yaml`; doing so would contradict a closed
decision. L6 names exactly one exception (the notification stream, R59/O7) and nothing else, so proceeding by
hand-writing the request would be a silent deviation — which the guardrails forbid.

The three exits, costed in `artifacts/01-specification-extraction.md` (OQ-1):

1. **A second contract file** plus a second generated module and a published client. Satisfies L6's own wording,
   since `contracts/api/*.yaml` is a glob; needs no ADR; leaves `auth.yaml`'s scope note intact.
2. **An ADR adding a second named exception to L6**, then a hand-written request with a permanent, visible lint
   suppression.
3. **Adding the path to `auth.yaml`** — mechanically cheapest, and contradicts that file's stated scope.

**This brief does not choose.** Phase 3 is the right place to attack the choice and Phase 4 freezes it as a
numbered decision. **Implementation cannot start until it is closed.**

**Non-blocking, carried for Phase 3:** where the access token goes, given that nothing constructs `SessionTokens`
outside tests and task 21 owns the answer (OQ-2); which suite pins the `refresh_token` absence against a live
server (OQ-3); whether anything beyond the discard happens on a failed exchange, which T19's own comment invites
(OQ-4).

---

## Next — Phase 3 — design challenge. **Kimi 2.7.**

---

## Addendum — the blocker is closed by the owner (2026-10-26)

**Exit 1 is chosen: a second contract file.** The owner closed the question above before Phase 3 opened, so the
blocker is no longer open and Phase 3 should challenge the chosen shape rather than re-run the choice.

What this settles, for Phase 4 to freeze as a numbered `T20-D` decision:

- `/oauth2/token` is described in a **new contract file** under `contracts/api/`, separate from `auth.yaml`.
  `auth.yaml`'s scope note (`auth.yaml:4-7`) stays exactly as written and is not edited, so `package.md` Q12(c)
  is not contradicted.
- The request is issued through a **generated client**, built from that file by the same `generate` pipeline that
  builds the existing one (`libs/ts/api-client/package.json`). L6 is satisfied **as written** — `contracts/api/*.yaml`
  is a glob — so there is **no ADR**, no second named exception to L6, and no lint suppression anywhere.
- The form-encoded body is expressible through the generated client without hand-written transport, via
  `openapi-fetch`'s `bodySerializer` (`openapi-fetch@0.17.0`, `dist/index.d.ts:13-14,115`), per-request rather
  than globally so the JSON default for every other call is untouched.

**Still for Phase 3 to settle** (none of them blocking): the new file's name and whether it carries only the token
endpoint; whether the generated module is published from `libs/ts/api-client` as a second export or a second
entry point; and the three non-blocking questions already listed above (OQ-2, OQ-3, OQ-4).
