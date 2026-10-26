# frontend · T19 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 19):**
> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

No design. No implementation.

---

## Business Rules

| ID | Sentence | Which part is T19's |
|---|---|---|
| **R7** | WHEN the SAS login returns with a matching `state`, the SPA SHALL **validate `nonce`**, exchange the code at `/oauth2/token` with the PKCE verifier, and hold the access token in memory only. | **only "validate `nonce`"** — the exchange is task 20, the in-memory hold is task 21, and "never written to storage" is task 22. R7 is split across four tasks. |
| **R8** | IF the callback `state` is missing or does not match, discard without exchanging and return to the sign-in start with neutral copy. | **not T19's to implement** — complete as of T18. It is T19's *precondition*: the nonce check happens only on an arrival R8 already let through, and T19 must not weaken it. |

R7's "WHEN … returns with a **matching** `state`" is the hinge. T17 and T18 built that condition;
T19 is the first task that runs **after** it holds.

---

## Locked Decisions

The header says "none cited inline — derive them from `design.md` §4a". Derived:

| ID | What it binds in T19 |
|---|---|
| **L1** | "`state` **and `nonce`** validated" — the only lock that names this task's subject. Public client, PKCE, no client secret. |
| **L3** | Tokens in memory only, never in storage, logs or analytics. T19 is the first task that can *hold* an ID token even momentarily, so it is the first that can violate this. |
| **L4** | Enumeration-safe UI. A nonce failure must be **indistinguishable** from every other sign-in failure: same copy, same redirect, same styling, same timing. The visitor is never told which check failed. |
| **L6** | Every backend call goes through `libs/ts/api-client`. Constrains T19 only if T19 performs the exchange — see **OQ-1** and **OQ-5**. |
| **L9** | Single origin. No per-service URL, no absolute address. |
| **L10** | No secret and no non-public environment key in the bundle. |
| **L11** | Accessibility and i18n: catalogue-only strings, live regions for async results, managed focus across redirect steps. |
| **L13** | Confirmed stack — nothing new may be added to it casually. **No JWT library is present**, which bears directly on OQ-2. |
| **L14** | Routes under `/app`. The discard target `/app/sign-in` is already in the table. |
| **L16** | The sessionStorage carve-out: `state`, `nonce`, `code_verifier`, return target — **single-use and deleted at callback**. T19 is the `nonce`'s **first and only reader**. Deleting is task 23's. |

**L16 is the lock with the sharpest edge here.** It says the items are "single-use and deleted at
callback", but `tasks.md` assigns the deletion to **task 23**. T17 and T18 both resolved this the
same way — read, do not delete — and T18 planted a failing-by-design tripwire for task 23. T19
inherits that resolution; it does not revisit it.

---

## Files involved

### Existing, to read

| File | Why |
|---|---|
| `src/auth/authorizeRequest.ts` | `AUTHORIZE_STORAGE_KEYS.nonce` (`:37`), written at `:83` from `randomUrlSafeToken()`. T19 is its first reader. |
| `src/auth/CallbackScreen.tsx` | The effect T19 extends. Its own doc comment already assigns the nonce to this task: *"the `nonce` is task 19's, the `code_verifier` is task 20's, and deleting all three is task 23's."* |
| `src/auth/codeExchange.ts` | The seam. Returns `Promise<void>` today; an ID token has to come from somewhere. |
| `src/auth/signInNotice.ts` | The in-memory `'discarded'` notice. |
| `src/i18n/catalogue.ts` | `signIn.discarded` already reads *"Your sign-in did not complete. Try again."* — written for R8 and **neutral enough to cover a nonce failure unchanged** (L4). |
| `src/app/routes.tsx` | `SIGN_IN_PATH`, `CALLBACK_PATH`, `APP_BASENAME`. |

### Existing, to extend

- `src/auth/CallbackScreen.tsx` — the nonce check and its outcome.
- `src/auth/codeExchange.ts` — if the seam must yield an ID token for T19 to validate.
- `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — **its no-nonce-read assertions will
  fail the moment T19 lands.** That is T17/T18's tripwire firing as designed, and updating it is
  part of this task.

### New, expected by the spec

- **`src/auth/callbackValidatesNonce.test.tsx`** — `package.md:175` names the test; the house
  convention is one file per named test.
- **A module that decides nonce equality**, and whatever minimal structure reads the claim. Phase 2
  names it; **Phase 1 does not design it.**

### Explicitly NOT T19's

`SessionTokens` implementation (task 21) · any storage write of a token (task 22 forbids it) ·
deletion of the three L16 items (task 23) · the token request's parameters (task 20).

---

## Dependencies

| Kind | Item | State |
|---|---|---|
| Constant | `AUTHORIZE_STORAGE_KEYS.nonce` = `'themistra.auth.nonce'` | exists |
| Browser API | `sessionStorage.getItem` | exists — **can throw**, guarded everywhere in this codebase |
| Seam | `exchangeAuthorizationCode` | exists, deliberately empty, `Promise<void>` |
| Interface | `SessionTokens` (`src/api/sessionBoundary.ts:11`) | declared, **no production implementation** |
| Catalogue | `signIn.discarded`, `callback.heading`, `callback.working` | exist |
| Contract | **ID-token claim set** | **does not exist.** `contracts/api/token-claims.md` is the *access token* contract and says so in its title. Phase 0 measured the real shape; that is evidence, not a contract. |
| Contract | `/oauth2/token` | **not in `auth.yaml`** — `design.md:133` lists the OIDC endpoints as SAS-owned and outside it |
| Config key | none | `src/auth/` reads no `import.meta.env`; the shell's suite forbids it (L10, R58) |
| Library | a JWT decoder | **none in `frontend/package.json`** (L13) |

**Measured, not assumed** (Phase 0 §3, against the running stack): the token response carries
`id_token` (RS256) and **no `refresh_token`**; the `id_token` carries `nonce` exactly as sent; and
**when no nonce is sent at authorize, SAS omits the claim entirely** rather than emitting `null` or
`''`. The ID token's claim set (`auth_time`, `azp`, `sid`, no `nbf`) differs from the access
token's, so **the two are not interchangeable** and only one of them carries the nonce.

---

## Acceptance Criteria

Scoped to T19 and mapped to requirement IDs. Phase 2 may refine wording; it may not widen scope.

| AC | Criterion | Maps to |
|---|---|---|
| **AC1** | An ID token whose `nonce` equals the stored value is accepted, and the flow continues. | R7, L1 |
| **AC2** | An ID token whose `nonce` differs by **any** amount — one character, case, padding, prefix, suffix — is rejected. | R7, L1 |
| **AC3** | An ID token with **no `nonce` claim at all** is rejected. Phase 0 measured this as a reachable shape, not a hypothetical. | R7, L1 |
| **AC4** | A `nonce` claim that is present but not a string (`null`, number, object, array) is rejected rather than coerced. | R7 |
| **AC5** | The comparison is **exact**: no trim, no case folding, no prefix or length comparison, no `==`. | R7, L1 |
| **AC6** | The comparison is bound to the **stored** value, not to any constant. It must fail when storage holds a different value, over **at least two unrelated stored values**. | R7, L16 |
| **AC7** | The stored value is read from `AUTHORIZE_STORAGE_KEYS.nonce` — not the `state`, not the `code_verifier`, not a copied string literal. | L16 |
| **AC8** | No stored value, or an empty one, is a rejection — never a vacuous pass. | R7, L16 |
| **AC9** | A `sessionStorage` read that **throws** is treated as "nothing stored" and rejects, without blanking the screen. There is no error boundary in production source. | L4, L11 |
| **AC10** | A rejection is **indistinguishable** from R8's discard: same copy, same path, same `REPLACE`, same empty query, nothing naming the cause. | **L4** |
| **AC11** | A rejected sign-in leaves **no token anywhere** — not in storage, not in a module variable, not in a URL, not in a log. | **L3**, R7 |
| **AC12** | T19 writes, deletes and clears **nothing** in `sessionStorage`. All three items survive. Deletion is task 23's. | L16 |
| **AC13** | T19 adds **no new user-visible string**; `signIn.discarded` is reused unchanged. | L4, L11 |
| **AC14** | T19 adds no absolute address, no per-service URL and no `import.meta.env` read. | L9, L10 |
| **AC15** | R8 still holds unchanged: a missing or mismatched `state` is still discarded **before** any nonce work, and the order is asserted, not assumed. | **R8** |
| **AC16** | T17's and T18's updated no-nonce-read assertions are **corrected, not deleted**, and still prove what they were written to prove about `state` and `code_verifier`. | R8, L16 |
| **AC17** | The whole suite still passes, with the one known environmental failure and **no other**. | regression |

**Non-vacuity is a criterion, not a nicety.** Nine times in this package an assertion has been
compared against the very constant it was testing; T18's variant survived **125 of 126 cases**.
**AC6 exists because of that** and must be satisfied over unrelated values, not over one literal
every fixture shares.

---

## Tests required

### Named test (`package.md` §8)

**`callbackValidatesNonce` → R7** (`package.md:175`). This is T19's whole obligation.

`callbackValidatesNonceAndKeepsAccessTokenInMemory` (`package.md:111`) is **also** R7's and is named
in this task's generated header, but `tasks.md` assigns it to **tasks 20–22**. The header lists every
R7 test in the package, not every test T19 owes. T19 owes **one**. `tokensAreNeverWrittenToAnyStorage`
(task 22) and `returnTargetOutsideAllowlistFallsBackToHome` are likewise not T19's.

### Boundary tests implied

**Equality** — identical; one character different; case-flipped; leading and trailing whitespace on
each side independently; a prefix of the stored value; the stored value plus a suffix; the empty
string; a realistic 43-character `randomUrlSafeToken` value.

**Shape** — claim absent; claim `null`; claim a number; claim an object or array; claim a string that
looks like the state.

**Storage** — nothing stored; empty string stored; `getItem` throws; the `state` value stored under
the nonce key (proves the right key is read); all three items present and unchanged afterwards.

**Ordering and isolation** — a mismatched `state` still discards before any nonce read (AC15); the
`code_verifier` is never read (task 20's); StrictMode double-invocation produces one outcome.

**Indistinguishability** — the nonce-failure outcome compared **byte-for-byte** with T17's
missing-state outcome on rendered text, pathname, search string and history action, the way T18 did.

**Source scans, each with its own negative control** — no `==`, no `.toLowerCase()`, no
`.startsWith`, no length comparison on the stored side; no token value reaching storage, a URL or a
log (L3); no literal `'themistra.auth.'` string. A scan is sometimes the only catcher of its defect
class, so it needs a control proving it can fail.

**Mutation** — the bar set by T17 and T18. At minimum: invert the comparison; compare against a
constant; compare against the stored `state`; drop the absent-claim check; remove the `try`/`catch`;
delete an L16 item on success; move the nonce check above the `state` check.

---

## Open Questions

**None of the five below is listed in `package.md` §11.** §11's Q1–Q9 are all closed or moot, and Q5
(refresh-token issuance) touches R18/R22, not this task. These are new and specific to T19.

### OQ-1 · Does T19 perform the exchange, or only own the decision? **(blocking — it sets the size)**

T19 validates a claim in a token that **task 20** is the task that fetches. Two readings:

**(i) T19 owns the decision; task 20 wires it to the real response.** The exchange seam yields an ID
token, T19 compares its `nonce` against the stored value and decides the outcome, and task 20
replaces the empty seam with real transport. This matches the one-behaviour-per-task discipline, the
T17 seam precedent (`codeExchange.ts` exists *only* so "did not exchange" is testable), and the
`CallbackScreen.tsx` doc comment that already reads *"the `nonce` is task 19's, the `code_verifier`
is task 20's"*. It also defers **OQ-5** to task 20, where `tasks.md` puts it.

**(ii) T19 performs enough of the exchange to obtain an ID token.** Honest about where the token
comes from, but it implements task 20's stated behaviour one task early and collides with L6 now.

**Recommendation: (i).** It is the only reading under which tasks 19 and 20 are distinct units, and
it is what the existing code already says. **Flagged for the Phase 4 approval gate** rather than
decided here, because it determines the task's size.

### OQ-2 · Decode and compare, or verify the RS256 signature? **(blocking — it sets the size)**

R7 says "validate `nonce`". L1 says "`state` and `nonce` validated". **Neither mentions signature
verification**, no JWKS handling appears anywhere in `spec/frontend/`, and **no JWT library is in the
confirmed stack** (L13) — all three consistent with decode-and-compare.

That is also the OIDC position: a client that obtains the token **directly from the token endpoint
over TLS** may rely on that channel instead of verifying the signature itself (OIDC Core §3.1.3.7).
The SPA's exchange is exactly that case, on one origin (L9).

**Recommendation: decode and compare.** Verifying would mean fetching `/oauth2/jwks`, caching keys,
handling rotation and adding a crypto dependency to a locked stack — materially larger, and
**nothing in the spec asks for it**. **Flagged for Phase 4.** If the owner wants verification, it is
its own task with its own ADR, not a quiet widening of this one.

### OQ-3 · What does the visitor see when the nonce fails, and is the token revoked?

A nonce failure happens **after** a successful token response, so a real token exists at the moment
of rejection — unlike R8, where nothing was ever fetched. The spec does not say what happens next.

**The L4 default answers the visible half:** indistinguishable from every other failure, so
`signIn.discarded` unchanged. **It does not answer revocation.** `/oauth2/revoke` exists
(`design.md:134`) and `spec/frontend/` never mentions calling it. **I do not know** whether the
author expects a rejected token to be revoked. **Not blocking** — AC11's "no token is kept anywhere"
is the security property, and revocation would be an addition to it.

### OQ-4 · Should the ID token's shape be written down?

`contracts/api/token-claims.md` documents the **access token** only. Phase 0 measured the ID token
against a running stack, but measurement is not a contract — a future SAS upgrade could change the
claim set with nothing failing first. **Not blocking**, and plausibly not this task's to author.

### OQ-5 · How does any OIDC endpoint call satisfy L6?

`/oauth2/token` is **not in `auth.yaml`**, so the generated client has no method for it, and
`.oxlintrc.json` bans `fetch` and `XMLHttpRequest` through **every** global alias. L6's single named
exception is the notification stream (R59, O7), not this. Whoever first performs the real exchange
must resolve it. **Under OQ-1 reading (i) this is task 20's**, and is recorded here so it is not
discovered there.

---

## Next

Phase 2 — task implementation brief. **OQ-1 and OQ-2 carry into it and into the Phase 4 freeze.**
Phase 3 is **Kimi's** design challenge.
