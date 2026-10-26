# frontend · T19 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/07-self-review.md` (F1–F6), `artifacts/08-independent-review.md` (Kimi, I1–I10) |
| **Produces** | `artifacts/09-review-resolution.md` |

**End state ·** suite **695 / 696** (the one failure is the known environmental `oxlint` **AC4**) ·
`tsc --noEmit` clean · `oxlint` exit 0 · T18's file **134/134**.

---

## The two reviews agree

Kimi's I1, I2 and I3 are my F1, F3 and F4. **Independent confirmation of all three**, with no
contradiction and no premise I had to check and reject — the first time in this package that a Kimi
phase and my own self-review converged this cleanly. Its remaining seven are documentation or
scheduling notes, five of which I took.

| Kimi | Mine | Disposition |
|---|---|---|
| I1 · non-string input throws | **F1** | **FIXED** |
| I2 · `discard` can run twice | **F3** | **FIXED** |
| I3 · helper cannot build the `TextDecoder` case | **F4** | **FIXED** (option a) |
| I4 · two-segment rejection unexplained | — | **FIXED** (comment) |
| I6 · `NonceRejection` exported but unread | **F5** | **ACCEPTED** — keep, scan in Phase 10 |
| I7 · the ordering is not documented | — | **FIXED** (comment) |
| I9 · the `''` → `null` normalization is not mentioned | — | **FIXED** (comment) |
| I5 · `refresh_token` absence is comment-only | — | **CARRIED to task 20** |
| I8 · T18's mock now depends on a stored nonce | — | **ACCEPTED, no change** |
| I10 · the named test does not exist yet | — | **Phase 10**, by design |
| — | **F2** · storage unread on three paths | **FIXED** (comment) + Phase 10 scoping |
| — | **F6** · task number in the seam's message | **KEPT**, with reason |

---

## Fixed

### F1 / I1 — the signature lied, and now it does not. **The one that mattered.**

`decodePayload` called `idToken.split('.')` outside its `try`, so a non-string threw a `TypeError`
out of a function documented as never throwing. Reachable through task 20's real response: an
endpoint returns no `id_token` whenever `openid` is not among the authorized scopes.

```ts
function decodePayload(idToken: unknown): Record<string, unknown> | null {
  if (typeof idToken !== 'string') return null;
  const segments = idToken.split('.');
```

The parameter is now `unknown` on both the private decoder and the exported decision, because **the
value crosses a network boundary the compiler cannot see**. The doc comment was rewritten from
*"never throws"* to a list of what it rejects instead.

**Confirmed by measurement, not by reading.** The same probe that failed in Phase 7 with
`TypeError: Cannot read properties of undefined (reading 'split')` now passes for `undefined`,
`null`, `42`, `{}` and `[]`, each returning `{ ok: false, reason: 'malformed-token' }`.

Before this fix the visitor outcome was still correct — `.catch(discard)` rescued the `TypeError`.
That was the defect, not the mitigation: **it was right by accident**, and a later narrowing of that
`catch` would have turned a safe discard into a blank screen with nothing failing first.

### F3 / I2 — the two-argument form

```ts
exchangeAuthorizationCode({ code, state }).then((response) => { … }, discard);
```

A failure inside the fulfillment handler no longer flows into the rejection handler, so `discard`
cannot run twice. Both reviews independently recommended this form.

### F4 / I3 — the helper can now mint what the decoder exists to read. **Option (a).**

`idTokenWith` used `btoa(JSON.stringify(claims))`, and `btoa` throws above U+00FF — so the suite
**could not build a non-ASCII payload**, leaving `decodePayload`'s `TextDecoder` step unfalsifiable.
Both reviews agreed that untested robustness is worse than absent robustness.

I took option (a) — fix the helper — over (b) — delete `TextDecoder`. The helper now encodes through
`TextEncoder` and a manual base64url pass. **Verified end to end:** a payload carrying
`{ nonce: 'abc', name: 'Zoë Ωmega 日本' }` now round-trips through the production decoder and matches.
Phase 10 adds the case and the mutant that drops `TextDecoder`.

### F2, I4, I7, I9 — four comments, each stating a decision that was only implicit

- **F2** — the ordering: `sessionStorage` is deliberately never read on the `malformed-token`,
  `claim-missing` and `claim-not-a-string` paths. Phase 10 asserts the read **only** where the
  comparison is reached, the shape T18 used for `state`.
- **I4** — why three segments, as a security statement: a two-segment token is an unsecured JWT
  (`alg: none`) and accepting one would admit a payload nobody signed, **which is exactly the
  substitution the nonce exists to stop**; four segments is JWE, which this code cannot decrypt.
- **I7** — why the check sits inside the fulfillment and may not be hoisted: R8 requires a bad
  `state` to discard before any nonce work, so hoisting would read the stored nonce for callbacks
  that were never this tab's.
- **I9** — that `storedNonce` folds "missing" and "empty" into one `null`, so an empty store cannot
  match an empty claim by both being `''`.

---

## Accepted without a code change

**I6 / F5 — `NonceRejection` stays exported.** A boolean cannot distinguish "the token carried no
nonce" from "nothing was stored", so a regression collapsing those paths would be invisible to every
test. Kimi's strengthening is right and is taken: **Phase 10 carries a scan asserting no
`NonceRejection` value is concatenated, passed to `setSignInNotice` or rendered** (AC10, AC13). That
turns an unused export into a constrained one.

**I8 — T18's mock reading the stored nonce is a coupling, not a defect.** Kimi notes the success
paths now implicitly depend on the nonce being written. True, and the right place to catch a
`buildAuthorizeRequest` regression is **AC8** in the new suite, which the brief already requires.
Changing T18's mock to stop reading storage would make it less like a real authorization server, not
more.

**F6 — the seam's message keeps its task number.** Its value is being unmistakable in a stack trace,
which does not depend on the number. Phase 10 asserts that the seam rejects, never its wording.

---

## Carried

**I5 — the `refresh_token` absence is pinned only by a comment.** `codeExchange.ts` records that the
real response carries no `refresh_token`, which is the empirical basis for **L3**'s silent
re-authentication model. Kimi is right that a fact of that weight should be held by a test rather
than by prose. **Task 20 owns it**, since task 20 is the first task with a real response to assert
against. Logged here so it is not rediscovered there.

---

## Mutants

Not run this phase, and deliberately so. **`callbackValidatesNonce.test.tsx` does not exist yet**
(Kimi's I10 — by design; Phase 6's prompt forbids writing it), so every mutant of the fixed code
would survive for want of a test rather than for want of coverage, and the result would say nothing.

**Phase 10 runs them**, including the three these fixes created:

- `decodePayload` without the `typeof idToken !== 'string'` guard — must fail the F1 cases.
- `.then(onFulfilled, discard)` reverted to `.then(…).catch(discard)` — needs a case asserting one
  navigation, not two.
- `TextDecoder` replaced by a plain `atob` read — must fail the non-ASCII case, which is now
  buildable.

Tests are committed before any mutant runs: the harness reverts with `git checkout --`, which has
silently destroyed uncommitted cases twice in this package.

---

## Open Questions

**No new ones.** OQ-1 and OQ-2 closed at the gate; OQ-3 (revocation), OQ-4 (the ID token has no
contract document) and OQ-5 (L6 and `/oauth2/token`, task 20's) remain deferred with owners, and none
affected this phase.

---

## Next

Phase 10 — test generation. The file Kimi's I10 names, with the three new mutants above and the
Phase 6 **D-2** lesson applied throughout: **a test double that resolves the wrong shape can skip the
check entirely**, so every assertion needs a control proving it is reached.
