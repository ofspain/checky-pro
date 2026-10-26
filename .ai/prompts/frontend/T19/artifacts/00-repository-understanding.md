# frontend · T19 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

No code written. No design. Requirements extraction is Phase 1.

---

## 1 · Architecture summary

`frontend/` is a Vite + React 19 + TypeScript SPA in a pnpm workspace, mounted under the `/app`
basename (L14), served — like every backend route — through one Caddy origin at `localhost:5173`
(L9). The prompt template's headings for this phase are backend-shaped; three of them have **no
analogue here** and I am not going to invent one:

| Template heading | Frontend reality |
|---|---|
| Persistence (JPA/Flyway) | **None.** The only client-side storage is L16's `sessionStorage` carve-out, written by `authorizeRequest.ts`. |
| Events / outbox / idempotency | **None.** No broker. The nearest thing is `BroadcastChannel` cross-tab renewal (T06). |
| Resource-server security | **Inverted.** This is the *client*. It holds a bearer token (tasks 21–22) and sends it; it verifies nothing server-side. |

What does exist, and what T19 sits inside:

- **`src/auth/`** — the OIDC client. `pkce.ts` (T14), `authorizeRequest.ts` (T15), `SignInScreen.tsx`
  (T16), `CallbackScreen.tsx` (T17 + T18), `codeExchange.ts` (the seam), `signInNotice.ts`.
- **`src/api/`** — the generated-client boundary. `sessionBoundary.ts` holds the single-flight
  renewal (T05/T06) and declares `SessionTokens { current(): string | null; renew(): Promise<string> }`,
  **which nothing in production implements yet**. Task 21 supplies it.
- **`src/app/`** — `routes.tsx` (the route table, `APP_BASENAME`, `SIGN_IN_PATH`, `CALLBACK_PATH`),
  `mountShell.tsx`, `AppShell.tsx`.
- **`src/i18n/`** — `catalogue.ts` is the only place a user-visible string may live (L11).
- **`src/pwa/`** — hand-written service worker; the SAS paths bypass it (T09–T12, L17).

---

## 2 · Existing code this task touches

| | Exists | New in T19? |
|---|---|---|
| `AUTHORIZE_STORAGE_KEYS.nonce` — `'themistra.auth.nonce'` | **yes**, `authorizeRequest.ts:37`, written at `:83` | no — T19 is its **first reader** |
| `CallbackScreen.tsx` — the effect, the `discard()` path, the guarded storage read | **yes** (T17/T18) | T19 extends it |
| `codeExchange.ts` — `exchangeAuthorizationCode({ code, state }): Promise<void>` | **yes, deliberately empty** | T19 or T20 gives it a return value |
| `signInNotice.ts` — the in-memory `'discarded'` notice | **yes** | reused, not extended |
| `callbackWithMismatchedStateIsDiscarded.test.tsx` — 64 declarations, 134 cases | **yes** | T19's named test is a **different** one |
| An ID-token type, a JWT decoder, a claims module | **no** | **new** |
| `SessionTokens` implementation | **no** — interface only | **not T19's** (task 21) |

**The nonce is the one L16 item with a writer and no reader.** T17 and T18 both assert it is *not*
read; those assertions are a deliberate tripwire and **T19 is the task that crosses it**, exactly as
T18 crossed T17's. Expect T17/T18 cases to fail the moment the nonce read lands — that is the
tripwire working, not a regression, and it is the same pattern T18 planted for task 23.

**The named test is new.** `package.md:175` maps `callbackValidatesNonce → R7`, and `package.md:111`
separately maps `callbackValidatesNonceAndKeepsAccessTokenInMemory → R7` (tasks 20–22). T19 verifies
**only the first**. They are two names, not one; the generated header for this task lists four names
because it lists every R7 test in the package.

---

## 3 · Measured facts about the ID token

I did not assume the shape of the thing this task validates. A fresh account was registered,
verified from Mailpit, and signed in through the real stack; the `/oauth2/token` response was read
directly. Scripts: `…/scratchpad/t19-probe.py` and `…/scratchpad/t19-probe2.py`.

**(a) The token response carries an `id_token`.** Keys: `access_token`, `expires_in`, `id_token`,
`scope`, `token_type`. **No `refresh_token`** — consistent with L3's resolved Q5 note.

**(b) The `id_token` carries `nonce`, equal to what was sent.** Header `{"kid":"dev-62616133","alg":"RS256"}`.
Claims:

```
aud  checky-spa      iss  http://localhost:5173    sub  <account uuid>
azp  checky-spa      iat/exp (30-minute life)      jti  <uuid>
auth_time <epoch>    sid  <opaque>                 nonce  <exactly what authorize sent>
```

**(c) When no `nonce` is sent at authorize, SAS omits the claim entirely** — it does not emit `null`
or `''`. Measured: a second flow with the parameter dropped returned HTTP 200 with an `id_token`
carrying no `nonce` key. **An absent claim is therefore a reachable shape, and the one a forged or
substituted token is most likely to have.** The SPA always sends a nonce, so absent means "not the
token this flow asked for".

**(d) The `id_token` claim set differs from the access token's.** It has `auth_time`, `azp`, `sid`
and **no `nbf`**; the access token has `nbf`, `roles`, `scope`, `amr`, `acr`, `email_verified` and no
`nonce`. **The two are not interchangeable**, which matters because the nonce lives in only one of
them.

**(e) A replayed authorization code is refused** — HTTP 400 `{"error":"invalid_grant"}`.

**(f) Two documented facts measured false, neither T19's to fix.**
`contracts/api/token-claims.md` states that email and name "live in the `id_token`/`/userinfo`
response for interactive logins only". On this stack, with `openid profile email` authorized, the
`id_token` carries **neither**, and `GET /userinfo` returns **`{"sub":"…"}` and nothing else**.
Separately, `/.well-known/openid-configuration` advertises `scopes_supported: ["openid"]` while the
client is registered for three. Logged for whoever owns that document; see Open Questions.

---

## 4 · Established patterns to follow

**Comments carry the reasoning.** Every module in `src/auth/` opens with a paragraph saying *why*,
names the requirement or lock, and says which task owns what comes next. `CallbackScreen.tsx:95`
goes as far as "Do not fold these into the inequality to tidy it." T19's code is expected to read the
same way.

**Storage is read defensively.** `sessionStorage.getItem` can **throw**, not merely return `null`
(blocked site data), and there is **no error boundary anywhere in production source**. T18 wraps its
read in `try`/`catch` and treats a throw as "nothing stored", which is also the safe security
outcome. T15 does the same on the sign-in screen. T19's nonce read inherits this precedent.

**Values are derived, never configured.** No module in `src/auth/` reads `import.meta.env`; the
shell's own suite forbids any source naming the compile-time environment object or an absolute
address (L9, L10, R58).

**Strings live only in `catalogue.ts`** (L11), and discard copy is neutral by requirement (L4): it
names no cause. T18 added no string and compared its outcome byte-for-byte with T17's.

**One behaviour per task, with a real seam.** `codeExchange.ts` exists *only* so "did not exchange"
is a testable fact. Its doc comment warns that the grant type will grow and that callers must use
`objectContaining`.

### Testing conventions

- **Vitest 5.0.5 + jsdom 30.1.3**, one `test: { environment: 'jsdom' }` block in `vite.config.ts`.
  No separate vitest config, no setup file, no Testcontainers, no ArchUnit — those are backend.
- **The test file is named after the named test**, one file per `package.md` §8 name.
- **Source scans substitute for ArchUnit**, and **every scan carries its own negative control**:
  T17's indentation scan is the *only* thing that catches a hoisted module-scope read, so T18
  extracted it and fed it a synthetic violation.
- **Mutation testing is the acceptance bar**, not an extra. T18 killed 17. The harness reverts with
  `git checkout --`, so **tests are committed before any mutant runs**.
- **Every assertion that could pass vacuously carries a positive control.** The reason is a defect
  class that has now appeared nine times — an assertion compared against the very constant it is
  testing. T18's variant: comparing against a different constant *every fixture happened to share*,
  which survived **125 of 126 cases**.
- Run: **`npx -y pnpm@12.14.0`**; bare `pnpm` is not on this host.
- Suite at the start of T19: **695 passed / 1 failed (696)**. The failure is environmental —
  `lintRejectsHandWrittenBackendFetch` **AC4**, where this host's `oxlint@1.87.0` prints no summary
  line. It is **not** the `pnpm` failure (that is AC2 of the same file, and it passes under the
  `npx` invocation).

---

## 5 · Known gaps and unknowns

**G1 · The ID token has no contract document.** `contracts/api/token-claims.md` is explicitly the
**access token** claims contract. Nothing in `contracts/` or `spec/frontend/` states the `id_token`'s
shape. §3 above is measurement against one running stack, not a contract. **I do not know** whether
the claim set is guaranteed, or merely what Spring Authorization Server 1.5.1 happens to emit.

**G2 · `/oauth2/token` is not in `auth.yaml` and `fetch` is banned.** `design.md:133` lists the OIDC
endpoints as "owned by the auth service, **not in `auth.yaml`**", so the generated client (L6) has no
method for the token endpoint — and `.oxlintrc.json` bans `fetch` and `XMLHttpRequest` through
**every** global alias, with L6's one named exception being the notification stream (R59, O7), not
this. **Whoever first performs the real exchange hits this.** Whether that is T19 or task 20 is a
Phase 1/2 question; that it is unresolved is a fact now.

**G3 · T19 validates a claim in a token that nothing yet fetches.** Task 19 validates the ID token's
nonce; **task 20** is "Code exchange sends the verifier". The ordering means the thing to validate
does not arrive until the next task. **I do not know** which of the two reasonable readings the
author intends, and will not pick one here:
(i) T19 owns the *decision* as a pure, fully testable unit and task 20 wires it to the real response; or
(ii) T19 performs enough of the exchange to obtain an ID token, which overlaps task 20.
Reading (i) matches the one-behaviour-per-task discipline and T17's seam precedent, and would defer
G2. **This is the task's central open question and belongs in Phase 1.**

**G4 · Signature verification is not in scope and must not be assumed to be.** R7 says "validate
`nonce`". It does not say verify the RS256 signature against `/oauth2/jwks`. OIDC's own rationale is
that a confidential, TLS-protected code exchange with the issuer makes the ID token trustworthy
without client-side signature checking, which is why no JWT library is in `frontend/package.json`.
**I do not know** whether the author intends T19 to decode-and-compare or to verify
cryptographically. The second is materially larger. **Open question for Phase 1.**

**G5 · What "the exchange is rejected" means for the visitor is unstated.** R8's discard path has
defined copy, a path and a history action. A nonce failure happens *after* a successful token
response, so a token now exists and must not be kept. **I do not know** whether it reuses R8's
`'discarded'` notice or needs its own, nor whether revocation is expected. The L4 default is that it
is indistinguishable from any other failure.

**G6 · Nothing implements `SessionTokens`.** The boundary built in T05/T06 is waiting on task 21. If
T19 produces a validated token, **there is nowhere to put it** until then. T19 must not invent a
holder — that is task 21, and task 22 forbids writing it to any storage.

**G7 · The T17/T18 no-nonce-read assertions will fail when T19 lands.** Deliberate. They must be
updated with the same care T18 took, not deleted.

**G8 · `design.md` §4c carries stale text**, carried forward from T16/T17/T18 and still unfixed: the
SPA route set is headed "PROPOSED … pending author confirmation (Q6)" though the routes are built,
and `design.md:129` says auth T19 "is not built" while L5 in the same file says it is. Cosmetic,
gates nothing, **not mine to edit** — `spec/` is never modified.

---

## Open questions for Phase 1

**OQ-1 · G3** — does T19 own the decision as a pure unit (task 20 wires it), or does it perform the exchange?
**OQ-2 · G4** — decode-and-compare, or verify the RS256 signature against `/oauth2/jwks`?
**OQ-3 · G5** — what the visitor sees when the nonce fails, and whether the token is revoked.
**OQ-4 · G1** — should the measured ID-token shape be written down anywhere, given `token-claims.md` covers only the access token?
**OQ-5 · G2** — how any OIDC endpoint call satisfies L6, which the generated client cannot cover.

**Carried, gating nothing ·** `token-claims.md`'s email/name claim, measured false in §3(f) ·
`design.md` §4c's stale heading and its "T19 is not built" line · `package.md:172`'s
`serviceWorkerUpdateWaitsForReload → R57` mapping · T16's OQ-1 (unreviewed copy) · T12's OQ-1 ·
the phase 13 template's stale `Claude Opus 4.8` trailer · Kimi's T18 Gap 6 (CI-enforce the suite total).

---

## Next

Phase 1 — specification extraction. **OQ-1 and OQ-2 are the two that change the size of this task**
and must be settled there.
