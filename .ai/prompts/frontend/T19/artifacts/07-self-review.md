# frontend · T19 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T19 — Nonce validated |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md`, the Phase 6 source |
| **Produces** | `artifacts/07-self-review.md` |

Reviewed the Phase 6 implementation against the frozen brief, `agents.md` and this package's own
history of defects. **Six findings, one of them reachable and confirmed by measurement.** No code was
changed in this phase; fixes land in Phase 9.

---

## F1 · `idTokenNonceMatchesStoredValue` **throws** on a non-string input, and its own doc comment says it does not

**Severity · High.** This is the one finding I would not ship past.

`decodePayload` opens with `idToken.split('.')`, which is **outside** the `try`. So a non-string
argument throws a `TypeError` straight out of a function whose doc comment reads *"Reads exactly one
stored item and never throws."* **Measured, not inferred** — a probe asserting `not.toThrow()` failed
with `TypeError: Cannot read properties of undefined (reading 'split')`.

**Is it reachable?** Yes, through the one route that matters: task 20 will build `TokenResponse` from
a network response. A token endpoint that returns no `id_token` — which happens whenever `openid` is
not among the authorized scopes, and this stack's discovery document advertises
`scopes_supported: ["openid"]` for a client registered with three — yields `response.idToken ===
undefined`. The compiler cannot catch it, because the type is asserted at a boundary the compiler
does not see.

**Today the visitor outcome is still correct**, because `.catch(discard)` catches the `TypeError`
and discards. That is the problem: **it is right by accident.** The designed path returns
`'malformed-token'`; the actual path throws and is rescued by a handler written for network failures.
A later refactor that narrowed that `.catch` would turn a safe discard into a blank screen, and
nothing would fail first.

**Recommendation ·** widen the parameter to `unknown` and reject a non-string as `'malformed-token'`
before anything else. The signature then tells the truth about a value that crosses a network
boundary, and AC18's guarantee holds for the input as well as for its contents.

---

## F2 · Storage is never read when the token is malformed or its claim is bad

**Severity · Medium.** `decodePayload` and both claim checks run **before** `storedNonce()`, so on
the `'malformed-token'`, `'claim-missing'` and `'claim-not-a-string'` paths **`sessionStorage` is
never touched.**

This is right — reading storage to compare against a token already known to be unusable is pointless
work — but **nothing says so**, and the consequence is not obvious. A Phase 10 test written as "the
nonce key is read on every rejection path" would fail on three of five, and the natural reaction to
that failure is to reorder the function rather than to fix the test.

**Recommendation ·** state the ordering and its reason in the doc comment, and have Phase 10 assert
the read **only** on the paths that reach the comparison — the same shape T18 used for `state`.

---

## F3 · A throwing `discard()` inside `.then` would run `discard()` twice

**Severity · Low-Medium.** The call site is:

```ts
exchangeAuthorizationCode({ code, state })
  .then((response) => { if (!idTokenNonceMatchesStoredValue(response.idToken).ok) discard(); })
  .catch(discard);
```

If `discard()` throws inside the `.then` — `navigate` or `setSignInNotice` failing — the rejection
flows into `.catch(discard)` and the visitor is navigated twice. Both navigations are `replace` to
the same path, so the visible result is the same today, and neither call is expected to throw.

It is still a latent double-invocation on a screen that exists **because** double-invocation is a
hazard — the `handled` latch is there for StrictMode for exactly this reason.

**Recommendation ·** either attach the handler as `.then(onFulfilled, discard)`, which does not route
the fulfilled handler's own failure into the rejection handler, or assert the single call in Phase 10.
The two-argument form is the smaller change and removes the case entirely.

---

## F4 · The new test helper cannot produce the input `TextDecoder` exists to handle

**Severity · Medium.** `idTokenWith` mints a payload with `btoa(JSON.stringify(claims))`. **`btoa`
throws on any code point above U+00FF**, so the helper cannot build a token with a non-ASCII claim —
which is precisely the input `decodePayload`'s `TextDecoder` step was written for.

So the production code handles a case the suite cannot reach, and the `TextDecoder` line is currently
unfalsifiable: replacing it with a plain `atob` read would pass every test in the file.

**That is the vacuity pattern this package has now hit nine times**, in a new place — not an
assertion against its own constant, but a *code path with no fixture that can exercise it*.

**Recommendation ·** Phase 10 mints the non-ASCII case with `TextEncoder` and a manual base64url
encode, and runs a mutant that drops `TextDecoder`. If the case cannot be built cleanly, the honest
alternative is to **remove** `TextDecoder` and state that only ASCII claims are decoded — unused
robustness that cannot be tested is worse than absent robustness, because it reads as protection.

---

## F5 · `NonceRejection` is exported, but no production code reads `.reason`

**Severity · Low.** Production reads only `.ok`. The five reason values exist so the tests can tell
the rejection modes apart, and the doc comment says so.

I think this is right and I want to keep it: a boolean cannot distinguish "the token carried no
nonce" from "nothing was stored", so a regression collapsing those paths would be invisible. But an
exported type that production never consumes is the kind of thing a reviewer should challenge, so I
am recording the justification rather than waiting to be asked.

**Recommendation ·** keep it, and have Phase 10 carry a scan asserting **no `NonceRejection` value
reaches the DOM or the catalogue** (AC10, AC13). That turns the export from unused surface into a
constrained one.

---

## F6 · The seam's error message names a task number

**Severity · Low.** `'The authorization-code exchange is task 20; there is no transport behind this
seam yet.'` It is accurate today and never shown to a visitor.

Raised only because **T18 had to correct a comment of mine that named the wrong task** (T18-D9), and
a task number in a string is a fact that rots. The message's value is that it is unmistakable in a
stack trace, which does not depend on the number.

**Recommendation ·** keep the message; let Phase 10 assert the seam rejects, not its wording.

---

## Non-findings, checked and sound

- **The comparison is exact** — `stored === claimed`, no trim, no case folding, no prefix or length
  test. The supplied side is deliberately **not** trimmed, unlike T17's `state`, and the reason is in
  the comment.
- **`''` normalizes to `null`** in `storedNonce`, so an empty stored value cannot match an empty
  claim.
- **`Array.isArray` and `payload === null`** are both excluded, so `typeof payload === 'object'`
  cannot admit an array or `null`.
- **Exactly three segments** are required; two-segment (unsecured JWT) and four-segment (JWE) shapes
  are rejected.
- **Only segment 1 is touched.** The signature is never read and no claim but `nonce` is looked at
  (AC19).
- **Nothing is written, removed or cleared** anywhere in the new or changed source (AC12).
- **One `discard` closure serves all four faults**, so the L4 indistinguishability holds by
  construction rather than by four copied strings (AC10).
- **The catalogue and `package.json` are untouched** (AC13, AC14).
- **D-2 from Phase 6 stands as the phase's most important lesson** and is not a defect in the
  shipped code: the tripwire was defused by a test double, and is now armed.

---

## Summary

| # | Finding | Severity | Phase 9 action |
|---|---|---|---|
| F1 | a non-string input throws, contradicting the doc comment | **High** | widen to `unknown`, reject as `malformed-token` |
| F2 | storage unread on three rejection paths, undocumented | Medium | document the ordering; scope the Phase 10 assertion |
| F4 | the test helper cannot build the input `TextDecoder` handles | Medium | mint it with `TextEncoder`, or remove `TextDecoder` |
| F3 | a throwing `discard()` would run twice | Low-Medium | use `.then(onFulfilled, discard)` |
| F5 | `NonceRejection` exported but unread in production | Low | keep; add the no-leak scan |
| F6 | a task number in the seam's error message | Low | keep; assert behaviour, not wording |

**F1 is the one that matters.** It is reachable through task 20's real network response, it is
currently safe only because a handler written for a different purpose happens to catch it, and the
function's own documentation asserts the opposite.

---

## Next

Phase 8 — independent review. **Kimi 2.7.**
