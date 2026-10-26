# frontend · T19 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

---

## Task

**T19 — Nonce validated.** From `spec/frontend/tasks.md`, task 19, verbatim:

> **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.

---

## Purpose

The `nonce` is the one value that ties an **ID token** back to the authorization request this tab
made. `state` (T17, T18) ties the **callback URL** back to it; the two protect different hops.
A `state` check cannot detect a token that was obtained legitimately for a different login and
substituted into this one. The `nonce` is the only check that can.

It is also the one item in the L16 carve-out with a writer (`authorizeRequest.ts:83`) and **no
reader**. T19 is its first and only reader, which is why T17 and T18 both assert it is *not* read.

---

## Scope

### In

- Reading the stored `nonce` from `sessionStorage` under `AUTHORIZE_STORAGE_KEYS.nonce`, guarded.
- Comparing it, exactly, against the `nonce` claim of the ID token the exchange yields.
- Rejecting the sign-in when they differ, when the claim is absent, when it is not a string, or when
  nothing usable is stored — with an outcome indistinguishable from R8's discard (L4).
- Keeping no token anywhere when the check fails.
- Correcting T17's and T18's no-nonce-read assertions, which this task deliberately invalidates.

### Out

- **The token request itself** — its URL, parameters and the `code_verifier` are **task 20**.
- **Holding the access token** in a module variable — **task 21**.
- **Proving no token is written to any storage** — **task 22** (`tokensAreNeverWrittenToAnyStorage`).
- **Deleting the three L16 items** — **task 23**, which T18 planted a failing-by-design tripwire for.
- **RS256 signature verification against `/oauth2/jwks`** — not asked for by R7 or L1; see OQ-2.
- **Revoking a rejected token** — see OQ-3.
- Any change under `spec/`.

---

## Business Rules

- **R7** — the SPA SHALL validate `nonce` on a callback whose `state` matched. T19 implements **only
  that clause**; the exchange, the in-memory hold and the storage prohibition are tasks 20, 21, 22.
- **R8** — a missing or mismatched `state` is discarded without exchanging. **Complete as of T18.**
  It is T19's precondition: the nonce check runs only on an arrival R8 already admitted, and T19 must
  not weaken or reorder it.

---

## Locked Decisions

- **L1** — public OIDC client with PKCE; "`state` **and `nonce`** validated". The lock that names this task.
- **L3** — tokens in memory only; never storage, logs or analytics. T19 is the first task that can hold one.
- **L4** — enumeration-safe: a nonce failure is indistinguishable from every other sign-in failure.
- **L6** — backend calls go through the generated client. Binds T19 only if T19 performs the exchange (OQ-1, OQ-5).
- **L9** — single origin; no per-service or absolute URL.
- **L10** — no secret, no non-public environment key in the bundle.
- **L11** — catalogue-only strings, live region, managed focus, WCAG AA.
- **L13** — confirmed stack; **no JWT library is in it** (bears on OQ-2).
- **L14** — routes under `/app`; `/app/sign-in` is the discard target.
- **L16** — the sessionStorage carve-out. T19 **reads** the `nonce`; it writes, deletes and clears nothing.

---

## Dependencies

| Item | Where | State |
|---|---|---|
| `AUTHORIZE_STORAGE_KEYS.nonce` = `'themistra.auth.nonce'` | `src/auth/authorizeRequest.ts:37`, written `:83` | exists |
| `exchangeAuthorizationCode(grant): Promise<void>` | `src/auth/codeExchange.ts:18` | exists, deliberately empty |
| `setSignInNotice('discarded')` | `src/auth/signInNotice.ts` | exists |
| `signIn.discarded` copy | `src/i18n/catalogue.ts` | exists, **neutral enough to reuse unchanged** |
| `SIGN_IN_PATH`, `CALLBACK_PATH`, `APP_BASENAME` | `src/app/routes.tsx` | exist |
| `SessionTokens` interface | `src/api/sessionBoundary.ts:11` | declared, **no production implementation** (task 21) |
| ID-token claim contract | — | **does not exist**; `contracts/api/token-claims.md` is the access-token contract |
| `/oauth2/token` in `auth.yaml` | — | **absent by design** (`design.md:133`) |
| JWT decoding library | `frontend/package.json` | **none** |

**Measured against the running stack** (Phase 0 §3): the token response carries `id_token` (RS256)
and **no `refresh_token`**; the ID token carries `nonce` exactly as sent; **with no nonce sent, SAS
omits the claim entirely** rather than emitting `null` or `''`; and the ID token's claim set
(`auth_time`, `azp`, `sid`, no `nbf`) differs from the access token's — **the two are not
interchangeable**, and only one carries the nonce.

---

## Inputs

1. The **stored nonce** — a string from `sessionStorage`, or absent, or empty, or the read **throws**.
2. The **ID token's `nonce` claim** — a string, or absent, or a non-string JSON value.
3. The precondition from R8: a callback whose `state` already matched.

---

## Outputs

- **Accept** — the stored and claimed nonces are identical strings; the flow continues toward tasks 20–22.
- **Reject** — an outcome byte-for-byte identical to R8's discard: `signIn.discarded`, navigation to
  `/app/sign-in` with `REPLACE` and an empty query, no token retained, nothing naming the cause.

---

## State Changes

**None in persistent state.** T19 reads one `sessionStorage` item and writes, deletes and clears
nothing; all three L16 items survive. The only state touched is the existing in-memory sign-in
notice, reused unchanged.

---

## Files to Create

- `frontend/src/auth/callbackValidatesNonce.test.tsx` — the named test (`package.md:175`).
- A module holding the nonce decision and the claim read. **Naming and placement are Phase 5's.**

## Files to Modify

- `frontend/src/auth/CallbackScreen.tsx` — the check and its outcome.
- `frontend/src/auth/codeExchange.ts` — only so far as T19 needs an ID token to validate (OQ-1).
- `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — **its no-nonce-read
  assertions fail the moment T19 lands.** Correct them; do not delete them.

## Files NOT to Modify

Everything under `spec/` · `src/api/sessionBoundary.ts` · `src/pwa/**` · `src/app/routes.tsx` ·
`contracts/**` · `frontend/.oxlintrc.json` · `frontend/package.json` (no new dependency without an
ADR — L13) · `src/i18n/catalogue.ts` (T19 adds no string — AC13).

---

## Acceptance Criteria

| AC | Criterion |
|---|---|
| **AC1** | A matching `nonce` is accepted and the flow continues. |
| **AC2** | Any difference — one character, case, padding, prefix, suffix — is rejected. |
| **AC3** | An **absent** `nonce` claim is rejected. Measured as reachable, not hypothetical. |
| **AC4** | A present but non-string claim (`null`, number, object, array) is rejected, not coerced. |
| **AC5** | The comparison is exact: no trim, no case folding, no `startsWith`, no length compare, no `==`. |
| **AC6** | The comparison is bound to the **stored** value, proven over **at least two unrelated** stored values. |
| **AC7** | The read uses `AUTHORIZE_STORAGE_KEYS.nonce` — not `state`, not `code_verifier`, not a copied literal. |
| **AC8** | Nothing stored, or an empty string stored, is a rejection — never a vacuous pass. |
| **AC9** | A throwing `sessionStorage` read is treated as "nothing stored" and rejects without blanking the screen. |
| **AC10** | The rejection is **byte-for-byte indistinguishable** from R8's discard — text, path, history action, query. |
| **AC11** | A rejected sign-in retains **no token** anywhere: storage, module variable, URL or log. |
| **AC12** | T19 writes, deletes and clears nothing; all three L16 items survive. |
| **AC13** | No new user-visible string; `signIn.discarded` reused unchanged. |
| **AC14** | No absolute address, no per-service URL, no `import.meta.env` read. |
| **AC15** | R8 still holds: a bad `state` discards **before** any nonce work, and the order is asserted. |
| **AC16** | T17's and T18's assertions are **corrected, not deleted**, and still prove what they were written to prove. |
| **AC17** | The whole suite passes with the one known environmental failure and no other. |

---

## Required Tests

**Named test ·** `callbackValidatesNonce` → R7 (`package.md:175`). **One test is T19's whole
obligation.** `callbackValidatesNonceAndKeepsAccessTokenInMemory` appears in this task's generated
header but belongs to tasks 20–22; the header lists every R7 test in the package, not every test T19
owes.

**Equality ·** identical · one character different · case-flipped · leading and trailing whitespace
on each side independently · a prefix · a suffix · empty string · a realistic 43-character
`randomUrlSafeToken` value.

**Shape ·** claim absent · `null` · a number · an object · an array · a string equal to the `state`.

**Storage ·** nothing stored · empty string stored · `getItem` throws · the `state` value stored
under the nonce key · all three items present and unchanged afterwards.

**Ordering and isolation ·** a mismatched `state` discards before any nonce read · the
`code_verifier` is never read · StrictMode double-invocation yields one outcome.

**Indistinguishability ·** the rejection outcome compared byte-for-byte against T17's missing-state
outcome, as T18 did.

**Source scans, each with its own negative control ·** no `==` · no `.toLowerCase()` · no
`.startsWith` · no length comparison on the stored side · no token reaching storage, a URL or a log
· no literal `'themistra.auth.'`.

**Mutation ·** invert the comparison · compare against a constant · compare against the stored
`state` · drop the absent-claim check · remove the `try`/`catch` · delete an L16 item on success ·
move the nonce check above the `state` check.

---

## Constraints

**Security.** The nonce check is the **only** defence against a substituted ID token; a comparison
that can pass vacuously is worse than none, because it reads as protection. The comparison is exact
and bound to storage. On rejection, **no token survives in any form** (L3). The outcome discloses
nothing about which check failed (L4).

**Non-vacuity is a hard constraint, not a style note.** An assertion compared against the very
constant it tests has appeared **nine times** in this package; T18's variant — a constant every
fixture happened to share — survived **125 of 126 cases**. AC6 exists for that reason and must be
satisfied over unrelated values.

**Null and absence handling.** Four distinct absences must each be handled explicitly and must each
reject: no stored item · an empty stored string · a storage read that **throws** · an absent or
non-string claim. `sessionStorage.setItem` coerces to `DOMString`, so a non-string can never be read
*back* — but a claim parsed from a token is not bound by that.

**No error boundary exists in production source.** An unguarded throw blanks the screen at the end
of a sign-in. Every storage read in this codebase is guarded; T19's is too.

**Module boundaries.** T19 stays inside `src/auth/`. It does not touch `src/api/`, `src/pwa/`,
the route table, the catalogue, the contracts or the lint config, and adds no dependency (L13).

**Thread-safety / transactions.** Not applicable — single-threaded browser code, no persistence. The
analogous hazard is React StrictMode double-invocation, already handled by the `handled` latch in
`CallbackScreen.tsx` and asserted by T17 and T18.

**Performance.** One synchronous storage read and one string comparison. Not a concern.

---

## Open Questions

**Two blockers, both of which change the size of this task. Both are flagged for the Phase 4
approval gate; neither is decided here.**

**OQ-1 · Does T19 perform the exchange, or only own the decision?**
T19 validates a claim in a token that **task 20** fetches. **Recommended reading:** T19 owns the
decision and task 20 wires it to real transport — the only reading under which tasks 19 and 20 are
distinct units, consistent with the T17 seam precedent and with `CallbackScreen.tsx`'s own comment,
*"the `nonce` is task 19's, the `code_verifier` is task 20's"*. It also defers OQ-5 to task 20,
where `tasks.md` puts it.

**OQ-2 · Decode and compare, or verify the RS256 signature?**
**Recommended: decode and compare.** R7 says "validate `nonce`"; L1 says "`state` and `nonce`
validated"; neither mentions signatures, no JWKS handling appears anywhere in `spec/frontend/`, and
no JWT library is in the confirmed stack (L13). It is also the OIDC position for a token obtained
directly from the token endpoint over TLS (OIDC Core §3.1.3.7), which is exactly this case on one
origin (L9). Verification would mean JWKS fetching, caching, rotation and a crypto dependency —
materially larger, and **nothing in the spec asks for it**. If the owner wants it, it is its own task
with its own ADR, not a quiet widening of this one.

**Non-blocking, recorded ·** **OQ-3** what the visitor sees on a nonce failure (L4 answers the
visible half; revocation via `/oauth2/revoke` is unaddressed by the spec) · **OQ-4** the ID token has
no contract document; Phase 0's measurement is evidence, not a contract · **OQ-5** `/oauth2/token` is
not in `auth.yaml` and `fetch` is banned through every alias, so whoever first performs the real
exchange must resolve L6 — task 20's under the recommended reading of OQ-1.

---

## Next

Phase 3 — design challenge. **Kimi 2.7.**
