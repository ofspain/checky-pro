# frontend · T19 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

**End state ·** suite **695 passed / 1 failed (696)** — the one failure is the known environmental
`lintRejectsHandWrittenBackendFetch` **AC4** · `tsc --noEmit` clean · `oxlint` clean · T18's file
**134/134**.

---

## What changed

### Created · `frontend/src/auth/idTokenNonce.ts` (118 lines)

The decision, as one exported function over a private decode and a private guarded read.

```ts
export type NonceRejection =
  | 'malformed-token' | 'claim-missing' | 'claim-not-a-string' | 'nothing-stored' | 'mismatch';
export type NonceResult = { ok: true } | { ok: false; reason: NonceRejection };
export function idTokenNonceMatchesStoredValue(idToken: string): NonceResult;
```

- `decodePayload` requires **exactly three segments**, converts base64url to base64, `atob`s, decodes
  the bytes through `TextDecoder` and `JSON.parse`s them, and requires a non-null, non-array object.
  **Returns `null` on every failure** — nothing throws out of it (AC18, T19-D5). Only segment 1 is
  touched; the signature is never read (AC19).
- `storedNonce` reads `AUTHORIZE_STORAGE_KEYS.nonce` inside `try`/`catch`, treats a throw as absent
  (AC9), and normalizes `''` to `null` so the caller has one absence rather than two (AC8).
- The comparison is `stored === claimed` on two strings — no trim, no case folding, no prefix or
  length test (AC5). The **supplied** side is not trimmed either, unlike T17's `state`: query padding
  is a client-side artefact, but whitespace inside a JSON claim was put there by whoever minted the
  token, which is exactly who this check does not trust.

**`TextDecoder` rather than `atob` alone** is a small decision worth naming: `atob` yields one
character per byte, so any non-ASCII claim would be silently mangled. The nonce itself is base64url,
but a mangled payload can also throw inside `JSON.parse`, and a token is better rejected for what it
is than for how it was read.

### Created · nothing else

`callbackValidatesNonce.test.tsx` is **Phase 10's**, per this phase's prompt.

### Modified · `frontend/src/auth/codeExchange.ts`

Widened to `Promise<TokenResponse>` where `TokenResponse = { idToken: string }` (T19-D2), and the
body now **throws** rather than silently resolving. The doc comment records the measured response
shape and why rejecting is the honest placeholder: a body that resolved would have to fabricate a
token, and a check proven against a fabricated value is proven against something that will never
exist.

### Modified · `frontend/src/auth/CallbackScreen.tsx`

```ts
exchangeAuthorizationCode({ code, state })
  .then((response) => {
    if (!idTokenNonceMatchesStoredValue(response.idToken).ok) discard();
  })
  .catch(discard);
```

Four different faults — missing `state`, mismatched `state`, a refused exchange, a bad `nonce` — now
share **one** `discard` closure rather than four copies, which is what makes AC10 hold by
construction instead of by a copied string. The rejection reason is deliberately dropped at the call
site; L4 forbids any of it reaching the visitor. The module doc comment was rewritten to say the
screen now reads two stored items and why the verifier is still not one of them.

### Modified · `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx`

Per **T19-D6** and AC16, plus one correction the brief did not anticipate (below): `spyOnExchange`
now resolves a real `{ idToken }` minted by a new `idTokenWith(claims)` helper, carrying **whatever
nonce is stored at the moment of the call** — which is what a real authorization server does. Two
success-path fixtures gained a nonce seed, and the `it.each` over `['a match', 'a mismatch']` was
**split into two tests**.

---

## Mapping to the acceptance criteria

| AC | Where |
|---|---|
| AC1, AC2, AC5 | `idTokenNonce.ts` — `stored === claimed` |
| AC3 | `!('nonce' in payload)` → `'claim-missing'` |
| AC4 | `typeof claimed !== 'string'` → `'claim-not-a-string'` |
| AC6 | the comparison takes **both** sides as arguments; no literal in the module |
| AC7 | `AUTHORIZE_STORAGE_KEYS.nonce`, imported, never spelled |
| AC8, AC9 | `storedNonce()` — `try`/`catch`, `''` → `null` |
| AC10 | the shared `discard` closure; no reason rendered |
| AC11 | the token is a parameter and a local; nothing retains it |
| AC12 | no `setItem`, `removeItem` or `clear` anywhere in the new or changed source |
| AC13 | `catalogue.ts` untouched |
| AC14 | no URL, no `import.meta.env`, no dependency added |
| AC15 | the check sits **after** T18's comparison, inside the seam's `.then` |
| AC16 | the T19-D6 split |
| AC17 | 695/696, the one known failure |
| AC18 | `decodePayload` returns `null` on every failure path |
| AC19 | only segment 1 decoded; only `nonce` read |

**AC1–AC19 are asserted in Phase 10**, not here. This phase is the code.

---

## Deviations, flagged rather than hidden

### D-1 · The plan predicted a non-compiling intermediate state. There was none.

Phase 5 step 2 warned that widening the seam would break `tsc` until step 3 wired the caller.
**It did not.** TypeScript lets a caller ignore a resolved value, so `.catch(discard)` on a
`Promise<TokenResponse>` type-checks exactly as it did on `Promise<void>`. The prediction was wrong
in a harmless direction, and is recorded because the plan told Phase 6 to expect it.

### D-2 · The tripwire did not fire on its own. **This is the finding of the phase.**

T17 and T18 both assert the nonce is never read, and the brief treated those assertions as a
tripwire that T19 would trip immediately. **Step 4 ran the suite before touching any test and the
nonce assertions passed.** Three cases failed, none of them the nonce ones.

The reason: `spyOnExchange` resolved `undefined`, honest while the seam returned `Promise<void>`. So
`response.idToken` threw a `TypeError` **before** `idTokenNonceMatchesStoredValue` was ever called —
no nonce read, assertion satisfied, and three success-path cases failing for a reason that looked
like T19-D3 but was not.

**A tripwire that depends on a test double can be silently defused by that double.** The nonce
assertion was only reached once the mock was corrected to resolve a realistic token — and then it
failed, exactly as designed. Had I corrected the three obvious failures without asking why the nonce
cases passed, T19 would have shipped with its central assertion still green against code that never
ran, which is the same vacuity class this package has now hit nine times.

**Recorded counts, honestly, in the order they happened:** 3 failures before the mock was corrected,
4 after, 0 at the end.

### D-3 · Two success-path fixtures needed a nonce seed, which T19-D6 did not list.

T19-D6 specified splitting the match/mismatch rows and updating the cases that assert the visitor
stays on the callback screen. It did not anticipate that `'and the reached callback stays put'` and
**both** AC5 flip rows would need a stored nonce. They seed only the `state`, so the corrected mock
minted a token with no `nonce` claim and the arrivals discarded as `'claim-missing'` — the task
working correctly against a fixture that no longer described a real flow. Each gained
`seedNonceAndVerifier()` with a comment saying why.

### D-4 · T19-D3's predicted visible change is real, and is now covered by an existing test.

With the seam rejecting, a completed sign-in returns to `/app/sign-in` with the neutral notice
instead of holding on the callback screen. T17's `returns the visitor to the start when the exchange
rejects (F1)` already covers that path and passes unchanged. Task 20 reverts it by returning a real
response.

---

## Open Questions

**No new ones.** OQ-1 and OQ-2 were closed at the gate; OQ-3 (revocation), OQ-4 (no ID-token
contract) and OQ-5 (L6 and `/oauth2/token`, task 20's) remain deferred with owners and none of them
affected this phase.

One thing Phase 7 should look at hardest: **D-2 means the new suite must prove its own assertions are
reached**, not merely green. Every nonce assertion in `callbackValidatesNonce.test.tsx` needs a
control showing it can fail — a test double that resolves the wrong shape is now a demonstrated way
for this exact check to be skipped entirely.

---

## Next

Phase 7 — self-review.
