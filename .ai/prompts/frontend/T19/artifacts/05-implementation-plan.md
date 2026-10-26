# frontend · T19 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**STATUS: FROZEN**) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code. Every file below traces to the frozen brief's Files sections; nothing is added beyond them.

The prompt's headings are backend-shaped. **Entities, repositories, services, migrations and
integration tests have no analogue here** — there is no persistence, no DAO layer and no server in
this task. Those headings are answered as "none" with the reason, not filled with invented structure.

---

## Files to create

| File | Authorized by | Purpose |
|---|---|---|
| `frontend/src/auth/idTokenNonce.ts` | brief §Files to Create, "one module for the decode and the comparison" | the payload decode (T19-D4), the malformed-token guard (T19-D5) and the exact comparison |
| `frontend/src/auth/callbackValidatesNonce.test.tsx` | brief §Files to Create | the named test (`package.md:175`) |

**Why one module rather than two.** The decode and the comparison are one decision — "is this token's
nonce the one we stored" — and splitting them would let a caller decode without comparing, which is
the shape of the vacuity this pipeline keeps catching. The module exports the decision; the decode
stays private to it.

**Why `src/auth/` and not `src/api/`.** The brief's §Files NOT to Modify forbids `src/api/**`, and the
module is OIDC client logic, not a transport concern.

## Files to modify

| File | Change | Authorized by |
|---|---|---|
| `frontend/src/auth/codeExchange.ts` | widen to `Promise<TokenResponse>`; body rejects | **T19-D2** |
| `frontend/src/auth/CallbackScreen.tsx` | await the seam, read the stored nonce, call the decision, reject on failure | brief §Files to Modify |
| `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | split the match/mismatch rows; update success-path cases for T19-D3 | **T19-D6**, AC16 |

Nothing else. In particular **not** `src/i18n/catalogue.ts` (AC13), `frontend/package.json` (AC14),
`src/app/routes.tsx`, `src/api/**`, `contracts/**` or anything under `spec/`.

---

## Public methods (signatures)

### `src/auth/idTokenNonce.ts`

```ts
/** Why the nonce check failed. Never shown to a visitor (L4) - it exists so the tests can tell the
 *  five rejection modes apart, which a single boolean cannot. */
export type NonceRejection =
  | 'malformed-token'      // AC18: not three segments, bad base64url, non-JSON or non-object payload
  | 'claim-missing'        // AC3: measured reachable - SAS omits the claim when none was sent
  | 'claim-not-a-string'   // AC4: present but null, number, boolean, object or array
  | 'nothing-stored'       // AC8, AC9: absent, empty, or the read threw
  | 'mismatch';            // AC2

export type NonceResult = { ok: true } | { ok: false; reason: NonceRejection };

/**
 * Decides whether an ID token belongs to the authorization request this tab made.
 * Reads exactly one stored item, guarded. Returns - never throws (AC18).
 */
export function idTokenNonceMatchesStoredValue(idToken: string): NonceResult;
```

**On the discriminated return instead of a boolean.** A boolean cannot distinguish "rejected because
the claim was absent" from "rejected because storage was empty", so a mutant that collapsed two
rejection paths would survive. The reason is **never rendered** — AC10 and AC13 forbid it — and a
source scan asserts no `NonceRejection` value reaches the DOM or the catalogue.

### `src/auth/codeExchange.ts`

```ts
/** The token endpoint's response, as much of it as the SPA uses. GROWS in task 20 (at least the
 *  access token). Callers and tests must not depend on its exact shape - use objectContaining. */
export type TokenResponse = { idToken: string };

export async function exchangeAuthorizationCode(_grant: AuthorizationCodeGrant): Promise<TokenResponse>;
```

The existing `AuthorizationCodeGrant` is unchanged — its `code_verifier` is task 20's.

## Private methods

All private to `idTokenNonce.ts`:

- `decodePayload(idToken: string): Record<string, unknown> | null` — splits on `.`, requires exactly
  three segments, converts base64url to base64 (`-`→`+`, `_`→`/`, pads to a multiple of four),
  `atob`, `JSON.parse`, and requires a non-null plain object. **Returns `null` on any failure**, so no
  throw escapes (T19-D5). **Only segment 1 is touched — the signature is never read** (AC19).
- `storedNonce(): string | null` — `sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)` inside
  `try`/`catch`, a throw returning `null` (AC9), and an empty string normalized to `null` (AC8).

Private to `CallbackScreen.tsx`: no new function. The existing `discard()` is reused unchanged, which
is what makes AC10 hold by construction rather than by a copied string.

---

## Entities · Repositories · Services · Migrations

**None, and not for lack of looking.** This is browser code: no database, no ORM, no DAO, no service
layer, no schema. The only persistence-shaped thing is the L16 `sessionStorage` carve-out, read once
and never written. The prompt's "front-load schema/migration" step has nothing to front-load.

---

## Tests required

### Unit / component — `callbackValidatesNonce.test.tsx`

Driven through `CallbackScreen` with a mocked seam, the way T17 and T18 drive theirs, so the test
exercises the wiring and not only a pure function. Blocks, each mapped to the frozen ACs:

1. **Accept** — AC1, AC6. Two unrelated stored values **plus** a real 43-character
   `randomUrlSafeToken`; **no single hardcoded literal shared by every success fixture.**
2. **Mismatch** — AC2, AC5. One character, case, whitespace each side, prefix, suffix, empty.
3. **Claim shape** — AC3, AC4. Absent, `null`, number, boolean, object, array; a claim equal to the
   URL `state` but not the stored nonce; the `state` value written under the nonce key.
4. **Token shape** — AC18. One, two and four segments; non-base64url payload; base64 that is not
   JSON; JSON that is not an object; empty string.
5. **Storage** — AC7, AC8, AC9, AC12. Nothing stored; empty stored; `getItem` throws; the right key
   is read; all three items survive **after an accept and after a reject**.
6. **Indistinguishability** — AC10. Byte-for-byte against T17's missing-state outcome on text,
   pathname, search and history action; no `NonceRejection` value anywhere in the DOM.
7. **No token retained** — AC11. Storage spies silent; no token substring in storage, URL or
   `console`.
8. **Ordering** — AC15. A mismatched `state` discards with **no nonce read**; `codeVerifier` never
   read; StrictMode yields one outcome.
9. **Source scans, each with a negative control** — AC5, AC14, AC19. No `==`, no `.toLowerCase()`,
   no `.startsWith`, no length compare; no absolute URL, no `import.meta.env`; no literal
   `'themistra.auth.'`; **no JWKS, no signature verification, no claim name but `nonce`.** Each scan
   is also fed a synthetic violating source and asserted to fail — a scan is sometimes the only
   catcher of its class, which is why T18 extracted T17's.
10. **Catalogue** — AC13. The `signIn` and `callback` key sets are unchanged.

### Regression — `callbackWithMismatchedStateIsDiscarded.test.tsx`

Per **T19-D6**, precisely:
- the `'a mismatch'` row **keeps** its assertions — on a discard the nonce is still never read;
- the `'a match'` row splits off: reads `state` **and** `nonce`, **never** `codeVerifier`;
- the comment above it is updated;
- success-path cases asserting the visitor **stays** on the callback screen are updated for T19-D3,
  each with a comment naming task 20 as what reverts it.

### Integration tests

**None.** There is no server component and no Testcontainers in this stack. The equivalent evidence
is the live-stack measurement already in Phase 0 §3, repeated at Phase 12 as T17 and T18 did.

### Mutants to run (the acceptance bar, not an extra)

Invert the comparison · compare against a constant · compare against the stored `state` · read the
wrong key · drop the absent-claim check · drop the non-string check · remove the `try`/`catch` ·
remove the malformed-token guard · accept a two-segment token · **delete an L16 item on nonce
failure** · **clear `sessionStorage` on nonce failure** · delete an L16 item on success · move the
nonce check above the `state` check · make the seam resolve instead of reject.

**Tests are committed before any mutant runs** — the harness reverts with `git checkout --`, which
has silently destroyed uncommitted cases twice (19 in T14, 5 in T15).

---

## Execution order

The prompt's order (schema → dao → service → api → tests) maps onto this task as **pure decision →
seam → wiring → regression → evidence**. Schema and DAO steps are empty, as above.

1. **`idTokenNonce.ts`** — the decode, the guarded read, the comparison. Pure, no React, no
   transport; the one piece everything else depends on.
2. **`codeExchange.ts`** — widen to `TokenResponse`, body rejects (T19-D2). Second because the
   wiring needs the type. **`tsc` will fail here until step 3** — expected, since
   `CallbackScreen.tsx` still ignores the return value.
3. **`CallbackScreen.tsx`** — await the seam, call the decision, route a failure to the existing
   `discard()`. Compiles again at the end of this step.
4. **Run the existing suite and record exactly which cases break.** T17's and T18's tripwires fire
   here, and the list is evidence for Phase 6 and AC16 — **captured before anything is edited**, so
   the record is what actually happened rather than what was expected. T18's D11 predicted one case
   would change and eight did; this step is what stops that becoming a surprise.
5. **`callbackWithMismatchedStateIsDiscarded.test.tsx`** — the T19-D6 corrections, against step 4's
   list.
6. **`callbackValidatesNonce.test.tsx`** — blocks 1–10 above, each with its control.
7. **Commit**, then run the mutants, then record survivors with their reasons.
8. **Full suite · `tsc --noEmit` · `oxlint`** — via `npx -y pnpm@12.14.0`. The expected end state is
   the current total plus T19's cases, with the **one** known environmental failure
   (`lintRejectsHandWrittenBackendFetch` **AC4**, the missing `oxlint` summary line) and no other.
9. **Live-stack check** — a real sign-in reaching the callback, then the same arrival with one
   character of the ID token's nonce changed, producing the opposite outcome. Phase 0's probe scripts
   already obtain a real `id_token`, so this is an extension of work already done.

**Step 4 before step 5 is deliberate.** Editing the old tests first would let a genuine regression
hide inside a tripwire correction.

---

## Open Questions

**No blockers.** The frozen brief closed OQ-1 and OQ-2. OQ-3 (revocation), OQ-4 (no ID-token
contract) and OQ-5 (L6 and `/oauth2/token`, task 20's) remain deferred with owners, and none gates
this plan.

One thing Phase 6 should expect rather than discover: **step 2 leaves the tree non-compiling until
step 3.** That is the cost of widening a seam and its caller in separate steps, and it is cheaper
than editing both at once with no type checking in between.

---

## Next

Phase 6 — implementation, against the frozen brief and this plan.
