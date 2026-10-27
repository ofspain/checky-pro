# frontend · T21 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

Two files: one created, one modified. No file outside the frozen brief's authorization.

---

## What changed

### `frontend/src/auth/accessToken.ts` — new

One module-level `let held: string | null = null`, and nothing else. The doc comment leads with the fact that
**this file is the one place in the package where a module-level `let` is the requirement rather than a smell** —
T19's and T20's suites scan their own production files for exactly that pattern and fail on a hit, so a reader who
does not find the justification written here will either delete the variable or copy it somewhere it does not
belong. It also records the consequence nobody should have to rediscover: the token lives as long as the module
instance, so **a reload ends the session**, and that is the point rather than a defect — silent re-authentication
on the SAS session cookie is what recovers from it.

`accessToken` is a single object literal annotated `LocalSession`, **imported** from `../api/crossTabRenewal`
rather than restated, following `sessionBoundary.ts:4`'s precedent of deriving a type from its owner so the two
cannot drift.

| Member | Behaviour |
|---|---|
| `current()` | the held token or `null`; `null` is the ordinary state, not an error (R22) |
| `adopt(token)` | replaces unconditionally, last writer wins (T21-D4) |
| `renew()` | **the seam** — `Promise.reject` with one fixed message (T21-D1) |

Plus `retainAccessToken(token)` and `clearAccessToken()` (T21-D3).

**`renew()` uses `Promise.reject` rather than `throw` in an `async` function.** The plan flagged this as a shape
question to settle by reading the compiler rather than guessing; the compiler accepted either, and `Promise.reject`
was chosen on behaviour: the rejection is the function's value in every caller's hands, including one that forgets
to await, and there is no synchronous throw for the boundary to miss.

**`retainAccessToken` and `adopt` do the same write under two names, deliberately.** `adopt` is the cross-tab
contract's word; the callback screen is not adopting a sibling's token, and a reader following the sign-in path
should not have to translate. Phase 7 should re-examine this rather than treat it as settled — it is the one
judgement call in the file.

**Nothing validates the string.** `codeExchange.ts:74-81` already refused a response whose `access_token` was
missing, empty or not a string. A second opinion here would add no information and a second place to get it wrong.

### `frontend/src/auth/CallbackScreen.tsx` — modified

**Retention sits on the far side of the nonce check**, behind an early return:

```
if (!idTokenNonceMatchesStoredValue(response.idToken).ok) {
  discard();
  return;
}
retainAccessToken(response.accessToken);
```

The previous single-line form (`if (!…ok) discard();`) could not carry a following statement safely — it fell
through. The early return is what keeps the two from being reordered by accident, and the comment says why it
matters: a token retained before the check is a token this tab holds for a login it did not start, which is the
substitution T19 exists to stop and which is invisible afterwards, since a held token looks identical whoever it
was minted for.

**`clearAccessToken()` goes inside the `discard` closure**, not at its five call sites — that closure exists so
the five failure paths cannot drift apart. It is also the only thing that covers the stale-token case: a tab that
has already signed in is holding a token, and a second attempt in that tab that is then discarded would otherwise
leave the old one held, which R22 forbids. On paths that never reached an exchange the call is a no-op, which is
why `clearAccessToken` is idempotent.

## Deviations

**None.** The plan's execution order was followed exactly this time, including the step that mattered — typechecking
the holder alone, before touching the screen, to learn from the compiler whether the literal really satisfies
`LocalSession`. It does.

## Verification

A throwaway probe wired the **real** holder into the **real** consumers, then was deleted; Phase 10 writes the
suite that keeps these facts. All three passed first time:

1. **Through the real boundary and the real generated client**, a request carried `Authorization: Bearer TOKEN-ONE`
   — `createAuthApi(createSessionBoundary(accessToken, …))` with `fetch` stubbed. This is AC5's claim, proven by
   use rather than by a type assertion.
2. **On a 401**, `renew()` rejected, the caller received its original 401, and `onRenewalFailed` ran **exactly
   once** with the fixed message `Silent re-authentication is not built; this tab cannot renew its session.` The
   seam is a defined outcome in the built boundary, not a crash (AC16).
3. **`shareRenewalAcrossTabs(accessToken)`** accepted the holder, reported `current()` as the held token, and
   reflected `adopt()` immediately (AC6).

Suites, before the new tests exist:

- `callbackWithMismatchedStateIsDiscarded`, `callbackValidatesNonce`,
  `callbackValidatesNonceAndKeepsAccessTokenInMemory`, `parallel401sTriggerExactlyOneRenewal` and
  `siblingTabsShareOneRenewal`: **371 passed of 371**. The last two build fakes of the very interface T21 now
  implements and needed **no change at all**, which is the cheapest available evidence that the real holder is
  shaped the way tasks 5 and 6 assumed.
- Whole suite: **892 passed / 1 failed of 893** — the known environmental `oxlint` AC4 case at `:113` and **no
  other**, read from the failure list. `vitest` exit **1**, that one failure, **no `Errors` line**, so no
  unhandled rejection.
- `tsc --noEmit` clean. `oxlint` exit **0** — the module-level `let` trips no rule, so the ban T19 and T20 enforce
  on *their* files is a test-level scan, not a lint rule, and this file is correctly outside it.

**The suite total did not move** (892 before, 892 after). T21 added production code and no tests yet; Phase 10
adds the in-memory half.

## Open questions

**None.** The frozen brief's §6 still records the two pieces of work the specification never assigned — silent
re-authentication and the composition root — and neither gates this task.

---

## Next — Phase 7 — self-review.
