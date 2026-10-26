# frontend · T19 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 (assembling the decision packet; the gate itself is the owner's) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` (Kimi 2.7) |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

Downstream phases may not renegotiate this brief.

---

## Disposition of Kimi's Phase 3 — 12 findings, 11 accepted, 1 accepted in part, 0 rejected

Unusual, and worth saying plainly: this was a strong review of a brief I wrote, and Finding 1 landed
a fair hit. **Both `file:line` citations were checked against source before acceptance** — the house
rule exists because Kimi's premises have been wrong before. `agents.md:45` does read *"The SPA cannot
control server response timing. The timing requirement applies to the SPA's own rendering only."*
The T18 test at `:647-663` is as described.

| # | Finding | Disposition |
|---|---|---|
| 1 | OQ-1 labelled blocking while the brief assumes reading (i) | **ACCEPTED** → T19-D1 |
| 2 | the seam returns `Promise<void>` and cannot yield an ID token | **ACCEPTED** → T19-D2, T19-D3 |
| 3 | no JWT decoder specified | **ACCEPTED** → T19-D4 |
| 4 | AC11 "no token anywhere" crosses the task 20/21 boundary | **ACCEPTED** → AC11 narrowed |
| 5 | "a string equal to the `state`" is ambiguous | **ACCEPTED** → shapes restated |
| 6 | AC6 not strong enough after T18's lesson | **ACCEPTED** → AC6 reworded |
| 7 | malformed-ID-token handling undefined | **ACCEPTED** → T19-D5, AC18 |
| 8 | the T17/T18 assertion update is underspecified | **ACCEPTED, with a correction** → T19-D6 |
| 9 | `SessionTokens` is the wrong abstraction for an ID token | **ACCEPTED IN PART** |
| 10 | AC10's timing claim ignores the network round trip | **ACCEPTED** → AC10 caveat |
| 11 | missing a "delete on failure" mutant | **ACCEPTED** → two mutants added |
| 12 | scope-creep risk toward `iss`/`aud` validation | **ACCEPTED** → T19-D7 |

**Finding 8, the correction.** Kimi recommends rewriting the assertion to *"reads state and nonce once
the state matched"*. That would be wrong as stated: the test is an `it.each` over **two** rows, `'a
match'` and `'a mismatch'`, and **only the match row breaks**. On a mismatch the screen still must
not read the nonce — the arrival is discarded before any nonce work. The correction splits the rows
rather than rewriting both. See T19-D6.

**Finding 9, accepted in part.** `SessionTokens` is removed from §Dependencies — Kimi is right that
an ID token is not a bearer token and the two have different lifecycles. It was listed to mark a
boundary, and §Files NOT to Modify does that job better. The boundary statement stays; the dependency
row goes.

---

## Decisions taken at this gate

### T19-D1 · T19 owns the decision; task 20 owns the transport. **OQ-1 is CLOSED, not deferred.**

Kimi is right that the brief argued for reading (i) while calling it blocking. Committing now:
**T19 validates the nonce and decides the outcome. Task 20 performs the real exchange.** Grounds:
it is the only reading under which tasks 19 and 20 are distinct units; `tasks.md` task 20 is
explicitly "Code exchange sends the verifier"; and `CallbackScreen.tsx`'s own doc comment already
says *"the `nonce` is task 19's, the `code_verifier` is task 20's"*. **OQ-5 (L6 and the ungenerated
`/oauth2/token`) therefore belongs to task 20**, which is where `tasks.md` puts it.

### T19-D2 · The seam becomes `Promise<{ idToken: string }>`, and its body **rejects** until task 20

Kimi's Finding 2 is the sharpest in the review: under reading (i) something must yield an ID token,
and `Promise<void>` cannot. Of Kimi's three options, **A** is taken — the seam carries the ID token —
with one addition Kimi did not raise: **the empty body must reject, not resolve.** A body that
resolved would have to fabricate a token, which is a lie in production source; T17's seam is honest
precisely because it does nothing and claims nothing. Rejecting is the truthful statement that the
exchange is not built.

```ts
export type TokenResponse = { idToken: string };   // grows in task 20
export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<TokenResponse>;
```

The type is still not to be depended on exactly — task 20 adds at least the access token. Tests keep
using `objectContaining`, per the seam's existing doc comment.

### T19-D3 · The consequence of D-2 on the half-built flow is accepted and recorded

`CallbackScreen.tsx:105` already routes a rejection to `discard()` (T17's F1 fix). So once the seam
rejects, **a good callback stops holding on "Checking your sign-in…" and returns to sign-in with the
neutral notice.** T17 deliberately chose the holding screen as "the honest state of a half-built
flow"; D-2 changes that to a neutral failure. **This is accepted, not hidden:** the flow genuinely
cannot complete before task 20, and bouncing says so more truthfully than holding forever. **It
reverts the moment task 20 lands real transport.** T17's and T18's success-path cases that assert the
visitor stays on the callback screen will need updating for this, under AC16.

### T19-D4 · Decode with `atob` + `JSON.parse`, payload only. **OQ-2 is CLOSED.**

**"Validate `nonce`" means decode and compare, not verify.** R7 and L1 say "validate `nonce`" and
never mention signatures; no JWKS handling appears anywhere in `spec/frontend/`; and OIDC Core
§3.1.3.7 permits a client to rely on the TLS channel for a token fetched **directly from the token
endpoint**, which is this case on one origin (L9).

Kimi's Finding 3 is right that this still needs a decoder, and that L13 forbids a *library*, not
base64url decoding. **No dependency is added.** The decode is inline: split on `.`, take segment 2,
convert base64url to base64 (`-`→`+`, `_`→`/`, pad to a multiple of four), `atob`, `JSON.parse`.
**Only the payload is decoded. The signature is neither read nor verified.** Anything that fails
along the way is a rejection (T19-D5), never a throw.

### T19-D5 · A malformed ID token is a **rejection**, not an exception

Not three segments, a segment that is not valid base64url, a payload that is not JSON, or a payload
that is not a JSON object → the sign-in is rejected on the same path as a nonce mismatch. Consistent
with AC9's throwing-read handling and with L4. **There is no error boundary in production source**,
so an escaping throw blanks the screen at the end of a sign-in.

### T19-D6 · Exactly how T17's and T18's assertions are corrected

The test at `callbackWithMismatchedStateIsDiscarded.test.tsx:647-663` is
`it.each([['a match', MATCHING_STATE], ['a mismatch', 'a-different-stored-value']])` named
*"reads the state key and no other once the comparison is reached: %s"*.

- **The mismatch row keeps its current assertions unchanged** — on a discard the nonce is still never
  read. This is load-bearing and must not be weakened.
- **The match row splits off** and becomes: reads `state` **and** `nonce`, and **still never reads
  `codeVerifier`** (task 20's). The verifier assertion is the part that must survive intact.
- The comment above it, which currently reads *"The nonce is task 19's and the verifier task 20's"*,
  is updated to say the nonce is now read and why the verifier still is not.
- Any T17/T18 case asserting the visitor **remains** on the callback screen after a good arrival is
  updated for T19-D3, with a comment naming task 20 as what reverts it.

**Corrected, never deleted.** An assertion deleted to make a build green is how a tripwire becomes
folklore.

### T19-D7 · `nonce` is the **only** claim T19 validates

No `iss`, no `aud`, no `exp`, no `azp`, no `auth_time`, no `sid`. Kimi's Finding 12 is a fair
scope-creep guard. Validating `iss` would also pull in an issuer value, which AC14 forbids reading
from the environment. Anything beyond `nonce` is task 20's or a later task's.

---

## Task

**T19 — Nonce validated.** The ID token's `nonce` must equal the stored value, or the sign-in is
rejected.

## Purpose

The `nonce` is the one value tying an **ID token** back to the authorization request this tab made.
`state` (T17, T18) ties the **callback URL** back to it; the two protect different hops. A `state`
check cannot detect a token obtained legitimately for a different login and substituted into this
one. The `nonce` is the only check that can.

It is also the one L16 item with a writer (`authorizeRequest.ts:83`) and no reader. **T19 is its
first and only reader**, which is why T17 and T18 both assert it is not read.

---

## Scope

### In

- Reading the stored `nonce` under `AUTHORIZE_STORAGE_KEYS.nonce`, guarded against a throwing read.
- Decoding the ID token's payload (T19-D4) and reading its `nonce` claim.
- Comparing the two **exactly**, and rejecting on any difference, on an absent or non-string claim,
  on a malformed token, or when nothing usable is stored.
- A rejection outcome indistinguishable from R8's discard (L4), retaining no token.
- Widening the seam to carry the ID token (T19-D2) and accepting the behaviour change (T19-D3).
- Correcting T17's and T18's affected assertions (T19-D6).

### Out

- **The token request** — URL, parameters, `code_verifier` — **task 20**, which also owns **OQ-5**.
- **Holding the access token** in a module variable — **task 21**.
- **Proving no token reaches any storage** — **task 22** (`tokensAreNeverWrittenToAnyStorage`).
- **Deleting the three L16 items** — **task 23** (T18 left a failing-by-design tripwire for it).
- **RS256 signature verification / JWKS** — excluded by T19-D4.
- **Any claim but `nonce`** — excluded by T19-D7.
- **Revoking a rejected token** — OQ-3, deferred below.
- Any change under `spec/`.

---

## Business Rules

- **R7** — validate `nonce` on a callback whose `state` matched. T19 implements **only that clause**.
- **R8** — complete as of T18. T19's **precondition**: the nonce check runs only on an arrival R8
  admitted, and T19 must not weaken or reorder it.

## Locked Decisions

**L1** (`state` *and* `nonce` validated) · **L3** (tokens in memory only) · **L4**
(indistinguishable outcomes) · **L6** (generated client — binds task 20, not T19, per T19-D1) ·
**L9** (single origin) · **L10** (no secret or non-public env key) · **L11** (catalogue strings, live
region, WCAG AA) · **L13** (confirmed stack — **no dependency added**, per T19-D4) · **L14** (routes
under `/app`) · **L16** (the sessionStorage carve-out — T19 **reads** the nonce and writes, deletes
and clears nothing).

---

## Dependencies

| Item | Where | State |
|---|---|---|
| `AUTHORIZE_STORAGE_KEYS.nonce` = `'themistra.auth.nonce'` | `src/auth/authorizeRequest.ts:37`, written `:83` | exists |
| `exchangeAuthorizationCode` | `src/auth/codeExchange.ts:18` | exists; **widened by T19-D2** |
| `setSignInNotice('discarded')` | `src/auth/signInNotice.ts` | exists |
| `signIn.discarded` copy | `src/i18n/catalogue.ts` | exists, **reused unchanged** |
| `SIGN_IN_PATH`, `CALLBACK_PATH`, `APP_BASENAME` | `src/app/routes.tsx` | exist |
| `atob`, `JSON.parse` | platform | available in browsers and jsdom |
| ID-token claim contract | — | **does not exist** (OQ-4) |

*`SessionTokens` removed per Finding 9 — T19 neither implements nor consumes it.*

**Measured against the running stack** (Phase 0 §3, scripts in the session scratchpad): the response
carries `id_token` (RS256) and **no `refresh_token`**; the ID token carries `nonce` exactly as sent;
**with no nonce sent, SAS omits the claim entirely** rather than emitting `null` or `''`; and the ID
token's claims (`auth_time`, `azp`, `sid`, **no `nbf`**) differ from the access token's — the two are
**not interchangeable**, and only one carries the nonce.

## Inputs

1. The **stored nonce** — a string, or absent, or empty, or the read **throws**.
2. The **ID token** — a JWT string whose `nonce` claim may be a string, absent, or a non-string JSON
   value; or a string that is not a well-formed JWT at all.
3. R8's precondition: a callback whose `state` already matched.

## Outputs

- **Accept** — identical strings; the flow continues toward tasks 20–22.
- **Reject** — byte-for-byte identical to R8's discard: `signIn.discarded`, `/app/sign-in`,
  `REPLACE`, empty query, no token retained, nothing naming the cause.

## State Changes

**None persistent.** One `sessionStorage` read; no write, delete or clear; all three L16 items
survive. The only state touched is the existing in-memory sign-in notice, reused unchanged.

---

## Files to Create

- `frontend/src/auth/callbackValidatesNonce.test.tsx` — the named test (`package.md:175`).
- One module for the decode and the comparison. **Naming and placement are Phase 5's.**

## Files to Modify

- `frontend/src/auth/CallbackScreen.tsx` — the check and its outcome.
- `frontend/src/auth/codeExchange.ts` — the widened seam and its rejecting body (T19-D2).
- `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — per **T19-D6**.

## Files NOT to Modify

Everything under `spec/` · `src/api/**` (incl. `sessionBoundary.ts`) · `src/pwa/**` ·
`src/app/routes.tsx` · `contracts/**` · `frontend/.oxlintrc.json` · `frontend/package.json` ·
`src/i18n/catalogue.ts`.

---

## Acceptance Criteria — final

| AC | Criterion |
|---|---|
| **AC1** | A matching `nonce` is accepted and the flow continues past the check. |
| **AC2** | Any difference — one character, case, padding, prefix, suffix — is rejected. |
| **AC3** | An **absent** `nonce` claim is rejected. Measured as reachable, not hypothetical. |
| **AC4** | A present but non-string claim (`null`, number, object, array, boolean) is rejected, not coerced. |
| **AC5** | The comparison is exact: no trim, no case folding, no `startsWith`, no length compare, no `==`. |
| **AC6** | The comparison is bound to the **stored** value, proven over at least two unrelated stored values, **where the matching case does not reuse one hardcoded literal across all success-path fixtures.** |
| **AC7** | The read uses `AUTHORIZE_STORAGE_KEYS.nonce` — not `state`, not `codeVerifier`, not a copied literal. |
| **AC8** | Nothing stored, or an empty string stored, is a rejection — never a vacuous pass. |
| **AC9** | A throwing `sessionStorage` read is treated as "nothing stored" and rejects without blanking the screen. |
| **AC10** | The rejection is **byte-for-byte indistinguishable** from R8's discard — text, path, history action, query. **Visible outcome only: network timing is outside the SPA's control and is excluded by `agents.md:45`.** |
| **AC11** | **No token reaching T19's code path is retained anywhere T19 controls** — not storage, not a module variable, not a URL, not a log. Task 20's transport and task 21's hold answer for their own. |
| **AC12** | T19 writes, deletes and clears nothing; all three L16 items survive **on both the accept and the reject path**. |
| **AC13** | No new user-visible string; `signIn.discarded` reused unchanged. |
| **AC14** | No absolute address, no per-service URL, no `import.meta.env` read, **no new dependency**. |
| **AC15** | R8 still holds: a missing or mismatched `state` discards **before** any nonce read, asserted not assumed. |
| **AC16** | T17's and T18's affected assertions are corrected per **T19-D6** — split, not rewritten; never deleted. |
| **AC17** | The whole suite passes with the one known environmental failure and **no other**. |
| **AC18** | A **malformed** ID token — not three segments, bad base64url, non-JSON payload, non-object payload — is rejected on the same path, never as an escaping throw. |
| **AC19** | Only the payload is decoded. The signature is neither read nor verified, and **no claim but `nonce`** is validated (T19-D7). |

---

## Required Tests

**Named test ·** `callbackValidatesNonce` → R7 (`package.md:175`). **One test is T19's whole
obligation.** `callbackValidatesNonceAndKeepsAccessTokenInMemory` appears in this task's generated
header but belongs to tasks 20–22; the header lists every R7 test in the package.

**Equality ·** identical · one character different · case-flipped · leading and trailing whitespace
on each side independently · a prefix · a suffix · empty string · a realistic 43-character
`randomUrlSafeToken` value.

**Claim shape ·** absent · `null` · a number · a boolean · an object · an array · **a claim equal to
the URL `state` but not to the stored nonce** · **the `state` value written under the nonce key**
(these two replace the brief's ambiguous "a string equal to the `state`", per Finding 5).

**Token shape (AC18) ·** one segment · two segments · four segments · a payload segment that is not
base64url · a payload that is valid base64 but not JSON · a payload that is JSON but not an object
(`"a string"`, `42`, `null`, `[]`) · an empty string.

**Storage ·** nothing stored · empty string stored · `getItem` throws · all three items present and
unchanged **after both an accept and a reject**.

**Ordering and isolation ·** a mismatched `state` discards before any nonce read · `codeVerifier`
never read · StrictMode double-invocation yields one outcome.

**Indistinguishability ·** the rejection compared byte-for-byte against T17's missing-state outcome,
as T18 did.

**Source scans, each with its own negative control ·** no `==` · no `.toLowerCase()` · no
`.startsWith` · no length comparison on the stored side · no token reaching storage, a URL or a log
· no literal `'themistra.auth.'` · **no signature verification and no claim name but `nonce`**.

**Mutation ·** invert the comparison · compare against a constant · compare against the stored
`state` · drop the absent-claim check · drop the non-string check · remove the `try`/`catch` ·
remove the malformed-token guard · **delete an L16 item on nonce failure** · **clear `sessionStorage`
on nonce failure** (both added per Finding 11) · delete an L16 item on success · move the nonce check
above the `state` check.

---

## Constraints

**Security.** The nonce check is the **only** defence against a substituted ID token. A comparison
that can pass vacuously is worse than none, because it reads as protection. Exact, and bound to
storage. On rejection, no token T19 touched survives (L3). The outcome discloses nothing about which
check failed (L4).

**Non-vacuity is a hard constraint.** An assertion compared against the very constant it tests has
appeared **nine times** in this package; T18's variant — a constant every fixture happened to share
— survived **125 of 126 cases**. AC6 carries Kimi's strengthened wording for exactly that reason.

**Five distinct failure modes must each reject explicitly** — no stored item · an empty stored string
· a storage read that throws · an absent or non-string claim · a malformed token.
`sessionStorage.setItem` coerces to `DOMString`, so a non-string can never be read *back* from
storage; **a claim parsed out of a token is not bound by that**, which is why AC4 is separate.

**No error boundary exists in production source.** An escaping throw blanks the screen at the end of
a sign-in. Every storage read in this codebase is guarded; T19's decode is too.

**Module boundaries.** T19 stays in `src/auth/`. No `src/api/`, no `src/pwa/`, no route table, no
catalogue, no contract, no lint config, **no dependency** (L13).

**Thread-safety / transactions.** Not applicable. The analogous hazard is StrictMode
double-invocation, already handled by the `handled` latch and asserted by T17 and T18.

**Performance.** One storage read, one base64 decode, one `JSON.parse`, one string comparison.

---

## Open Questions

**No blockers. OQ-1 and OQ-2 are CLOSED at this gate** (T19-D1, T19-D4).

**Deferred, each with an owner ·**
**OQ-3** — whether a rejected token is revoked via `/oauth2/revoke`. L4 answers the visible half;
revocation is unaddressed by `spec/frontend/`. **Owner: the author.** Not blocking — AC11 is the
security property, and revocation would be an addition to it.
**OQ-4** — the ID token has no contract document; `token-claims.md` is the access-token contract.
Phase 0's measurement is evidence, not a contract. **Owner: whoever owns `contracts/`.**
**OQ-5** — `/oauth2/token` is not in `auth.yaml` and `fetch` is banned through every global alias, so
L6 must be resolved before the real exchange. **Owner: task 20**, per T19-D1.

**Carried, gating nothing ·** `token-claims.md`'s email/name claim, measured false in Phase 0 §3(f),
and `/userinfo` returning only `sub` · discovery advertising `scopes_supported: ["openid"]` for a
three-scope client · `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its
"auth T19 is not built" line · `package.md:172`'s `serviceWorkerUpdateWaitsForReload → R57` mapping ·
T16's OQ-1 and T12's OQ-1 · the phase 13 template's stale `Claude Opus 4.8` trailer · Kimi's T18
Gap 6 (CI-enforce the suite total and confirm the `oxlint` AC4 failure there).

---

**STATUS: FROZEN.** Phase 5 plans the implementation against this brief and may not renegotiate it.
