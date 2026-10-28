# frontend · T23 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

---

## Task

Make `state`, `nonce` and `code_verifier` **single-use**: they survive the journey to SAS and back in
`sessionStorage`, and a callback that consumes them deletes all three. Build
`stateSurvivesRedirectAndIsConsumedOnce` (`package.md:159` → R8, L16).

## Purpose

Task 15 writes the three items; tasks 17 to 21 read them. Nothing deletes them, and L16 says they are
"single-use and deleted at callback" — so today a consumed `state` stays readable for the rest of the tab's life.
What that costs is narrow but real: a callback URL that already succeeded still matches the stored `state`, so
the one defence R8 describes keeps answering *yes* to a question it has already answered. Single-use closes the
window.

T23 is also the task that **answers four inherited assertions** written by tasks 18, 19 and 22 to fail when it
lands — one of them says so by name. Those edits are the work, not collateral.

## Scope

**In**
- A deletion of the three items on the consumed path.
- The named test, including the replay case that proves single-use.
- Updating the inherited assertions in four existing test files, each with its reason restated.
- `STORAGE_ALLOWLIST`'s first `removeItem` row in `tokensAreNeverWrittenToAnyStorage.test.tsx`.

**Out**
- The post-login return target, L16's fourth item — **task 24** (T23-D6).
- Silent re-authentication, which will write these same three keys — **no task** (T23-D7).
- Deleting on any discard — **T23-D2**.
- A real full-page redirect, which jsdom cannot perform — **T23-D5**.
- Task 26's L16 inventory and R29's TTL; task 22's package-wide sweep, which T23 only amends.

## Business Rules

| ID | Line |
|---|---|
| **R8** | A missing or mismatched `state` discards, exchanges nothing, and returns to the sign-in start with neutral copy (`requirements.md:25-26`). |
| **R7** | A matching `state` leads to the exchange and the nonce check — the two consumers that decide when deletion is possible. |
| `agents.md:20` | `state` and `nonce` are validated on every callback. |
| `agents.md:80-81` | sessionStorage is the only sanctioned sensitive store, limited to L16. |

## Locked Decisions

**L16** (scoped) — the carve-out, and the one place the spec states the deletion obligation: "single-use and
deleted at callback". **L4** — one outcome for every failure. **L1** — the nonce check, the last consumer.
**L13** — no new dependency. **L9** — one origin, which is why `sessionStorage` survives the redirect at all.

## Frozen decisions

| ID | Decision |
|---|---|
| **T23-D1** | **One `consumeAuthorizeState()`, exported from `authorizeRequest.ts`, called by `CallbackScreen` after the nonce check passes.** The file that owns the key constant owns the deletion, so no key is spelled at a second site; one call site makes "all three or none" true by construction (AC5); and the three readers stay **pure**, which three existing unit tests assert and which deleting-in-place would break. |
| **T23-D2** | **No discard deletes.** A missing `state`, a mismatched `state`, a refused exchange, an unusable response and a bad nonce all leave the three in place. Reasons, in order: the callback route is public, so a deleting discard would let anyone who gets a visitor to open `/app/callback?state=x` destroy that tab's pending sign-in; one rule for all five failures keeps L4's shape; and the strict reading buys nothing, because the authorization code is single-use **server-side** — T20 measured a replayed code returning `400 invalid_grant` — so a retained verifier cannot redeem anything twice. This confirms the position tasks 18 and 19 pinned. |
| **T23-D3** | **Deletion is all-or-nothing and happens after every consumer has read.** The verifier is read inside the exchange, the nonce after it resolves, so the only safe point is after the nonce check. A partial set would leave a `state` with no verifier behind it — the condition `authorizeRequest.ts`'s own comment says it ordered its writes to avoid. |
| **T23-D4** | **The delete is guarded.** `removeItem` can throw for the same reason every read in this package is wrapped (blocked site data). A failed deletion must not change the visitor's outcome: a successful sign-in stays successful, and the sign-in is not re-routed because the cleanup failed. Nothing else in the package deletes, so this is the first guard of its kind. |
| **T23-D5** | **"Survives the full-page redirect" is proven structurally, and the limit is stated.** jsdom cannot unload and reload a document and `createMemoryRouter` never touches `window.location`. What the test shows is that the value is written by one module, read by another after a fresh mount, with no shared variable between them. Written into the test file as T22 wrote its IndexedDB limit. A real-browser check has no owning task. |
| **T23-D6** | **Three keys, by their constant.** L16 names four items; the post-login return target does not exist yet and is **task 24's**. The deletion names the three that exist via `AUTHORIZE_STORAGE_KEYS` rather than clearing the store, so it cannot pre-empt task 24 or step on R29's form input. |
| **T23-D7** | **Recorded, not handled: silent re-authentication is a second writer of these three keys** (`agents.md:30`, "PKCE again") and has no task. A renewal in a tab with a callback in flight would overwrite the three mid-flow. T23 leaves a stated problem for whoever builds it. |
| **T23-D8** | **The inherited assertions are updated, never deleted.** Each becomes the new stated position with its reason, in the same style it was written in — including `STORAGE_ALLOWLIST`'s row, whose comment demands a file and a why from exactly this task. |

## Dependencies

All built, none new: `AUTHORIZE_STORAGE_KEYS` and `buildAuthorizeRequest` (`auth/authorizeRequest.ts`),
`exchangeAuthorizationCode`, `idTokenNonceMatchesStoredValue`, `CallbackScreen` and the route graph,
`accessToken`. `Storage.prototype` spies in tests.

## Inputs

The callback query (`code`, `state`, `error`); the three stored items; a stubbed `fetch` answering
`/oauth2/token`.

## Outputs

No new export beyond `consumeAuthorizeState()`. No user-visible change: a consumed callback looks exactly as it
does today, and a discard is unchanged by requirement (L4).

## State Changes

**The three `sessionStorage` items are removed** on the consumed path, and only there. No other key, no
`localStorage`, no `clear()`.

## Files to Create

`frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.ts(x)` — the named test.

## Files to Modify

| File | Change |
|---|---|
| `auth/authorizeRequest.ts` | add `consumeAuthorizeState()`, guarded (T23-D1, T23-D4) |
| `auth/CallbackScreen.tsx` | call it after the nonce check; rewrite the doc comment at `:23-27`, which currently says "deleting all three is task 23's" |
| `auth/callbackValidatesNonce.test.tsx` | the purity case at `:563-575` — still passes under T23-D1, and its "fails by design when task 23 lands" comment is now answered |
| `auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | `:405-419` and `:1008-1025` — restate as T23-D2's frozen position |
| `auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | `:671-689` — the success path now deletes |
| `auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | `expectThreeAuthorizeWrites` at `:341`, `STORAGE_ALLOWLIST` at `:345-357`, the whole-store equality at `:545` |

## Files NOT to Modify

`spec/**`. `auth/codeExchange.ts` and `auth/idTokenNonce.ts` — they stay **pure readers** (T23-D1).
`auth/accessToken.ts` — T21's. `api/*` — tasks 5 and 6. `pwa/*` — R57's. `app/*`. `.oxlintrc.json` — T22-D12's
recorded reason stands.

## Acceptance Criteria

AC1-AC14 as extracted in Phase 1, with **AC4 now frozen as T23-D2** and **AC5 as T23-D3**:

**AC1** the three survive the journey, structurally (T23-D5) · **AC2** a consumed callback leaves none of the
three · **AC3** single-use proven by **replay**: the same URL a second time is discarded, exchanges nothing, same
neutral copy · **AC4** no discard deletes (T23-D2), over all five failure shapes · **AC5** all-or-nothing, never
a partial set · **AC6** deletion only after every consumer has read, asserted by order · **AC7** StrictMode: one
handling, one deletion, and the second pass does not turn a success into a discard · **AC8** a throwing
`removeItem` leaves the visitor's outcome unchanged · **AC9** nothing else touched, no key hand-spelled ·
**AC10** a new sign-in after a consumed callback works end to end · **AC11** no new string, no catalogue key, L4
copy identical · **AC12** every inherited assertion updated with its reason restated (T23-D8) · **AC13** suite
green with the one known environmental failure, **by exit code** · **AC14** no new dependency; no production
change outside the two `auth/` files.

## Required Tests

`stateSurvivesRedirectAndIsConsumedOnce` — all of the above. The cases that carry the task: the **replay** case
(AC3), the **StrictMode** case (AC7), the **throwing-delete** case (AC8), the **second sign-in** case (AC10), and
an **ordering** case for AC6 — which, like T22's AC14, may have to be structural, since a deletion that happened
too early and a deletion that happened at the right time can leave the same end state.

## Constraints

- **Security:** deletion must not become a denial of service. The public callback route is why T23-D2 exists.
- **Purity:** `codeExchange.ts` and `idTokenNonce.ts` keep no side effects; three unit tests assert it.
- **Idempotence:** the deletion may run against an already-empty store (a second mount, a renewal that
  overwrote), and must be a no-op rather than an error.
- **Null handling:** `removeItem` can throw; see T23-D4. A missing item is not an error.
- **L4:** nothing added to a failure path, so no copy can drift.
- **Thread-safety / transactions:** not applicable.
- **Vacuity, the standing risk in this package:** a case that asserts "the items are gone" passes if the flow
  never wrote them. Every such case needs a control proving all three were present first.
- **Known traps, measured:** `createMemoryRouter` writes neither `window.location` nor `history`; a
  `Storage.prototype` spy must be installed before the code under test runs; jsdom has no `navigator.locks`,
  `indexedDB` or `caches`; a comment-stripping scan collapses lines.

## Open Questions

**No blockers.** Phase 1's OQ-1 and OQ-2 are frozen as T23-D1 and T23-D2; OQ-3, OQ-4 and OQ-5 as T23-D5, T23-D7
and T23-D6.

One judgement recorded for the author: **T23-D2 means "single-use" is enforced only on the path that succeeded.**
A callback whose exchange failed can be retried with the same `state`. That is deliberate — the code behind it is
already spent server-side — but it is a narrower reading of "single-use" than the phrase alone suggests, and if
the owner wants the strict reading, it is a one-line change to the call site plus the five AC4 rows.

---

## Next — Phase 3 — design challenge (Kimi 2.7).
