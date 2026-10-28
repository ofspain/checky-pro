# frontend · T23 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business Rules

| ID | One sentence |
|---|---|
| **R8** (scoped) | If the callback `state` is missing or does not match, the SPA discards the attempt, completes no exchange, and returns the visitor to the sign-in start with neutral copy (`requirements.md:25-26`). |
| **R7** (adjacent, unavoidable) | A matching `state` leads to the nonce check and the exchange (`requirements.md:23-24`) — which is what consumes the other two items, and therefore what decides when they can be deleted. |
| **R29** (boundary) | No tokens, passwords, recovery codes, API keys or PII in any storage; non-sensitive form input only, with a TTL. Task 26 owns the inventory. |

**R8 does not mention deletion.** The deletion obligation comes from the task statement and from L16's own
wording, and `agents.md` adds the sharpest line:

- **`agents.md:20`** — "`state` and `nonce` are validated on every callback."
- **`agents.md:80-81`** — sessionStorage is the only sanctioned sensitive store, limited to the L16 list;
  nothing else is persisted client-side.
- **`agents.md:30`** — silent re-authentication re-opens `/oauth2/authorize` "(hidden iframe or a background
  navigation, **PKCE again**)". Nothing implements it, but it means a second writer of these three keys is
  planned, which bears on how a deletion is written. See OQ-4.

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L16** (scoped) | `design.md:55-57` — sessionStorage may hold, and only hold, (a) `state`, `nonce`, `code_verifier` and **the post-login return target**, **"single-use and deleted at callback"**, and (b) non-sensitive mid-task form input with a 15-minute TTL. **This is the only place in the spec that states the deletion obligation at all**, and it names four items while only three exist today (OQ-5). |
| **L4** | One outcome for every failure; no copy that distinguishes causes. Anything added to a failure path goes in the shared `discard` closure. |
| **L1** | The nonce check (T19), which is the last consumer of a stored item and therefore the earliest moment all three can go. |
| **L13** | No new dependency, no state library. |
| **L9** | One origin; the redirect to SAS and back is a same-origin full-page navigation, which is why `sessionStorage` survives it. |

## Files involved

**Existing, to read and change:**

| File | What it holds for T23 |
|---|---|
| `auth/authorizeRequest.ts:71-96` | the only writer; `AUTHORIZE_STORAGE_KEYS` lives here, so a deletion helper placed here keeps every key spelling in one file |
| `auth/CallbackScreen.tsx:38-152` | the latch, the `state` read at `:105`, the shared `discard` at `:69-79`, the nonce check at `:136`, the retention at `:149`; its doc comment at `:23-27` says "deleting all three is task 23's" and must be rewritten |
| `auth/codeExchange.ts:56-62` | reads `code_verifier` **inside** the exchange, after the state check |
| `auth/idTokenNonce.ts:85-99` | reads `nonce` **after** the exchange resolves — the last consumer |

**Existing, to update because they pinned today's behaviour deliberately** (the full list is in Phase 0 §2):
`callbackValidatesNonce.test.tsx:563-575`, `callbackWithMismatchedStateIsDiscarded.test.tsx:413-419` and
`:1008-1025`, `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:678-689`, and
`tokensAreNeverWrittenToAnyStorage.test.tsx:341` (`expectThreeAuthorizeWrites`, used by 11 cases), `:345-357`
(`STORAGE_ALLOWLIST`) and `:545` (whole-store equality).

**New:** `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.ts(x)` — the named test
(`package.md:159` → R8).

## Dependencies

No new ones. `AUTHORIZE_STORAGE_KEYS`, `buildAuthorizeRequest`, `exchangeAuthorizationCode`,
`idTokenNonceMatchesStoredValue`, `CallbackScreen` and the route graph — all built. `Storage.prototype` spies for
the write/delete surface.

## Acceptance Criteria

Mapped to R8 and L16. **AC4 and AC5 are the two that need a frozen decision before they can be written**, and
both are in Open Questions.

| AC | Criterion |
|---|---|
| **AC1** | The three items **survive the journey**: written by `buildAuthorizeRequest` in one module, read in another after a fresh mount with no shared variable between them. The jsdom limit is stated, not implied — a real full-page unload cannot be reproduced here (OQ-3). |
| **AC2** | A **consumed** callback — state matched, exchange succeeded, nonce matched — leaves **none** of the three in `sessionStorage`. |
| **AC3** | **Single-use, proven by replay:** the same callback URL arriving a second time after a success is discarded, completes no exchange, and leaves the visitor at the sign-in start with the same neutral copy. |
| **AC4** | A **discarded** callback leaves the three in place — the position tasks 18 and 19 already pinned, with its reason: the callback route is public, so a discard that deleted would let anyone who can open `/app/callback?state=x` in a visitor's browser destroy that tab's pending sign-in. **Needs freezing (OQ-2).** |
| **AC5** | Deletion is **all-or-nothing**: no path leaves one or two of the three behind. A partial set is worse than either extreme, because the next callback would find a state with no verifier — the exact condition `authorizeRequest.ts`'s own comment says it ordered its writes to avoid. |
| **AC6** | Deletion happens only **after every item has been consumed** — the verifier by the exchange, the nonce by the check. Asserted by order, not only by outcome. |
| **AC7** | **StrictMode**: the effect runs twice, one callback is handled, the deletion happens **once**, and the second pass does not turn a success into a discard by reading an emptied store. |
| **AC8** | A **throwing** `removeItem` (blocked site data) does not change the visitor's outcome: a successful sign-in stays successful. Every read in the package is guarded for this reason and there is no precedent for a guarded delete. |
| **AC9** | Nothing else is touched: no other `sessionStorage` key, `localStorage` untouched, no `clear()`, and no key hand-spelled anywhere (`AUTHORIZE_STORAGE_KEYS` only). |
| **AC10** | After a consumed callback, a **new sign-in** writes a fresh three and the flow works end to end — deletion does not poison the next attempt. |
| **AC11** | No new user-visible string and no catalogue key; the discard copy stays identical across causes (L4). |
| **AC12** | Every inherited assertion is **updated with its reason restated**, not deleted. A case that was pinned "so task 23 inherits a stated position rather than a surprise" is answered in kind, including the `STORAGE_ALLOWLIST` row that must now name a file and a why. |
| **AC13** | The suite passes with the one known environmental failure and no other, **by exit code**. |
| **AC14** | No new dependency, no production change outside the four `auth/` files named above. |

## Tests required

**The named test:** `stateSurvivesRedirectAndIsConsumedOnce` → R8 (`package.md:159`). New file, and the task's
own deliverable.

**Named tests in the prompt header that are not T23's:** `callbackWithMismatchedStateIsDiscarded` is **task 18's**
(built) — T23 edits two of its cases under AC12 but does not own it.

**Boundary tests implied:** the replay case (AC3) driven twice through the real screen; a StrictMode case (AC7); a
throwing-delete case (AC8); the second-sign-in case (AC10); and a case proving the three readers still see their
item at the moment they read it, which is what AC6 is really about.

## Open Questions

**OQ-1 — Where does the deletion live?** Three candidates:

| Option | For | Against |
|---|---|---|
| **(a) one `consumeAuthorizeState()` in `authorizeRequest.ts`, called by the screen after the nonce check** | all key spellings stay in the one file that owns them; one call site; all-or-nothing by construction (AC5); the three readers stay pure, so `callbackValidatesNonce.test.tsx`'s unit case keeps passing | the screen must call it on exactly one path, so the latch and the ordering carry the weight |
| **(b) each reader deletes its own item as it consumes it** | locality — each item dies where it is used; naturally after consumption | three delete sites, three guards, and a partial set the moment any one path fails (AC5 fails); it also turns `exchangeAuthorizationCode` and `idTokenNonceMatchesStoredValue` into functions with side effects, breaking three existing unit tests that assert they are pure |
| **(c) the screen deletes inline with three `removeItem` calls** | no new export | re-spells or re-imports keys at a second site; nothing stops a future path from deleting two of three |

**Recommendation: (a).** It is the only option that makes AC5 true by construction and keeps the readers pure.

**OQ-2 — Does a *failed* consumption delete?** The task statement says "deleted at callback" without
distinguishing. Tasks 18 and 19 already pinned *no* for a missing or mismatched state, with the public-route
reason. The remaining question is the middle case: **state matched but the exchange was refused, or the nonce did
not match.**

- Deleting there makes "single-use" strictest: one callback, one attempt, no retry.
- Not deleting there keeps a transient failure (a dropped connection, a 5xx) recoverable — the visitor can be
  returned to the callback by SAS without starting over — and keeps one rule for all four failures (L4's spirit).
- **Recommendation: do not delete on any discard.** One rule, the inherited position, and the stricter reading
  buys little: the code is single-use **server-side** (measured in T20: a replayed code gets `400
  invalid_grant`), so a retained verifier cannot redeem anything twice. Not a blocker; Phase 2 must freeze it.

**OQ-3 — Can "survives the full-page redirect" be proven here?** No, not literally: `createMemoryRouter` never
touches `window.location`, and jsdom cannot unload and reload a document. What is provable is that the write and
the read are in different modules with no shared state, and that the value crosses a fresh mount. **The limit
must be written into the test file**, as T22 did for IndexedDB. A real-browser check has **no owning task**.

**OQ-4 — Silent re-authentication will write these same three keys** (`agents.md:30`, "PKCE again"), and it has
no task. A renewal running in a tab with a sign-in callback in flight would overwrite the three items mid-flow,
and whichever deletion T23 writes will run against whatever the renewal left. Out of scope, recorded so the task
that builds renewal inherits a stated problem rather than a surprise.

**OQ-5 — L16 names four items; three exist.** The post-login return target is task 24's. A deletion written as
"these three keys" is honest today and must be revisited by task 24; one written as "everything this flow
stored" would pre-empt it. **Recommendation:** delete the three that exist, by their constant, and record the
handoff.

**No blockers.** OQ-1 and OQ-2 change what is built and both have a recommendation; Phase 2 freezes them.

---

## Next — Phase 2 — task implementation brief.
