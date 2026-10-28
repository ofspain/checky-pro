# frontend · T23 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 (human approval gate) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**This is the only specification the implementation and review phases use.** All eight Phase 3 findings accepted;
**F5 with its mechanism sharpened and F6 with its premise corrected**. Decisions **T23-D1..D13** are frozen.

---

## 1 · Task and purpose

Make `state`, `nonce` and `code_verifier` single-use: they survive the journey to SAS and back in
`sessionStorage`, and a callback that consumes them deletes all three. Build
`stateSurvivesRedirectAndIsConsumedOnce` (`package.md:159` → R8, L16).

Nothing in the package deletes anything today, so a consumed `state` stays readable for the rest of the tab's
life and R8's one defence keeps answering a question it has already answered. T23 closes that window — and
answers **eight assertions** that tasks 18, 19 and 22 wrote to fail when it lands, three of them naming this task
in a comment.

## 2 · Disposition of the Phase 3 findings

| Finding | Disposition |
|---|---|
| **F1** don't rewrite `callbackValidatesNonce`'s purity case to expect deletion | **Accepted.** The case **passes unchanged** under T23-D1 — the pure check still touches no storage. Only its comment changes, to say the deletion is the screen's after the check. **T23-D9.** |
| **F2** `expectThreeAuthorizeWrites` cannot serve both paths | **Accepted.** Split into two named helpers rather than a boolean: `expectAuthorizeWritesOnly` (failure paths, zero removals) and `expectAuthorizeStateConsumed` (the success path, three removals, same keys, same order). **T23-D10.** |
| **F3** T22's AC7 wording contradicts T23's success path | **Accepted**, with one procedural correction: T22's frozen brief is a **historical record** and is not rewritten. The amended criterion is recorded **here**, and T22's test is updated to match. **T23-D11.** |
| **F4** the StrictMode case assumes the `handled` ref survives | **Accepted as a control.** The premise is sound — React re-runs the *effect* on the same instance, so the ref persists — but T23 makes the second pass observable for the first time, so AC7 asserts the route is **not** the discard route and the deletion ran **once**. |
| **F5** all-or-nothing versus guarded | **Accepted, mechanism sharpened.** See below. |
| **F6** AC6 needs a concrete mechanism | **Accepted, premise corrected.** AC6 is **behavioural, not structural**. See below. |
| **F7** `consumeAuthorizeState()` needs a narrow documented contract | **Accepted. T23-D12.** |
| **F8** vacuity control at AC level | **Accepted. T23-D13**: every consumed-path and replay case asserts all three items are present immediately before the deletion point. |

### F5, sharpened

Kimi is right that D3 and D4 can be read as contradicting, and the fix is to say what "all-or-nothing" governs.
`sessionStorage` has no transaction, so strict atomicity is **unavailable** and must not be promised.

**What is guaranteed (T23-D3, restated):** there is **one call site** that removes **all three** keys, so no code
path removes a subset by design. Each `removeItem` sits in its **own** guard, which is the arrangement most
likely to complete all three: the realistic failure is a denied store, which fails all three uniformly and
therefore leaves all three in place. A context that threw for one key and not the others could leave a partial
set, and **that is stated rather than denied** — the test asserts the behaviour under a store that throws for
every call, which is the failure that actually happens.

### F6, corrected

The Phase 2 brief hedged that AC6's ordering "may have to be structural, since a deletion that happened too early
and a deletion that happened at the right time can leave the same end state." **That is wrong, and T22's AC14 is
not the right analogy.** Each consumer *needs* its item: a deletion placed before the exchange leaves
`codeExchange.ts:59` reading `null`, which makes `exchangeAuthorizationCode` reject, which discards — so the
success case **fails outright**. The order is observable through behaviour.

**So AC6 is behavioural**, proven two ways: the success case is the control, and a mutant that moves the call to
the state check must die. A `getItem`/`removeItem` ordering assertion is added as well, because it names the
property directly and costs one case.

## 3 · Frozen decisions

| ID | Decision |
|---|---|
| **T23-D1** | **One `consumeAuthorizeState()`, exported from `authorizeRequest.ts`, called by `CallbackScreen` after the nonce check passes.** The file owning the key constant owns the deletion, so no key is spelled twice; one call site removes all three; the three readers stay **pure**, which three existing unit tests assert. |
| **T23-D2** | **No discard deletes** — missing `state`, mismatched `state`, refused exchange, unusable response, bad nonce. The callback route is public, so a deleting discard would let anyone who gets a visitor to open `/app/callback?state=x` destroy that tab's pending sign-in; one rule for all five keeps L4's shape; and the strict reading buys nothing, because T20 measured a replayed code returning `400 invalid_grant` server-side. Confirms the position tasks 18 and 19 pinned. |
| **T23-D3** | *(sharpened per F5)* **One call site removes all three keys; each `removeItem` is individually guarded.** `sessionStorage` offers no transaction, so atomicity is best-effort and said to be: a denied store fails all three uniformly and leaves all three; the test pins that case. |
| **T23-D4** | **The delete is guarded** — the package's first. A failed cleanup must not change the visitor's outcome: a successful sign-in stays successful and is not re-routed because a `removeItem` threw. |
| **T23-D5** | **"Survives the full-page redirect" is proven structurally, and the limit is written in the test file.** jsdom cannot unload a document and `createMemoryRouter` never touches `window.location`; what is shown is a value written in one module and read in another after a fresh mount with no shared variable. A real-browser check has no owning task. |
| **T23-D6** | **Three keys, by their constant.** L16 names four items; the post-login return target is **task 24's**. The deletion names the three that exist via `AUTHORIZE_STORAGE_KEYS` and never clears the store, so it cannot pre-empt task 24 or touch R29's form input. |
| **T23-D7** | **Recorded, not handled:** silent re-authentication is a second writer of these keys (`agents.md:30`, "PKCE again") and has no task. |
| **T23-D8** | **Inherited assertions are updated, never deleted**, each with its reason restated in the style it was written in. |
| **T23-D9** | *(F1)* `callbackValidatesNonce.test.tsx:563-577` **keeps its assertions** — the pure check still touches no storage. Its comment is rewritten to say so and to name the screen as the deleter. A case that was pinned to fail "the moment task 23 lands" is answered by explaining why it does not. |
| **T23-D10** | *(F2)* T22's `expectThreeAuthorizeWrites` splits into **`expectAuthorizeWritesOnly`** (nine failure rows: three writes, **zero** removals, no `clear`) and **`expectAuthorizeStateConsumed`** (the success path: three writes, then **three** removals, the same three keys in the same order, no `clear`). Two names rather than a flag, so a reader sees which path a case is on. |
| **T23-D11** | *(F3)* **T22's AC7 is amended, and the amendment lives here.** New wording: *"exactly three `setItem` calls, all from the authorize step, none carrying a token; on the consumed path exactly three matching `removeItem` calls and no `clear`; on every failure path no `removeItem` and no `clear`."* T22's own artifacts are not rewritten — they are the record of what was true at T22. |
| **T23-D12** | *(F7)* `consumeAuthorizeState()`'s documented contract: **removes exactly the three single-use L16 items named by `AUTHORIZE_STORAGE_KEYS`; idempotent; never throws; never clears the store; never touches another key.** Written so a future reader cannot mistake it for a general cleanup helper. |
| **T23-D13** | *(F8)* **Vacuity control at AC level:** every consumed-path and replay case asserts all three items are **present** immediately before the deletion point. "The items are gone" is otherwise satisfied by a flow that never wrote them. |

## 4 · Scope

**In:** the deletion on the consumed path; the named test including the replay case; the eight inherited
assertions across four files; `STORAGE_ALLOWLIST`'s first `removeItem` row.

**Out:** the return target (task 24, T23-D6); silent re-authentication (no task, T23-D7); deleting on a discard
(T23-D2); a real full-page redirect (T23-D5); task 26's L16 inventory and R29's TTL; `.oxlintrc.json`
(T22-D12's reason stands).

## 5 · Files

**Create:** `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`.

**Modify:** `auth/authorizeRequest.ts` (add `consumeAuthorizeState`, T23-D12's contract);
`auth/CallbackScreen.tsx` (call it after the nonce check; rewrite the doc comment that currently says "deleting
all three is task 23's"); `auth/callbackValidatesNonce.test.tsx` (comment only, T23-D9);
`auth/callbackWithMismatchedStateIsDiscarded.test.tsx` (`:405-419`, `:1008-1025`);
`auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` (`:671-689`);
`auth/tokensAreNeverWrittenToAnyStorage.test.tsx` (the helper split, `STORAGE_ALLOWLIST`, the whole-store
equality at `:545`).

**NOT to modify:** `spec/**`; `auth/codeExchange.ts` and `auth/idTokenNonce.ts` — **pure readers** (T23-D1);
`auth/accessToken.ts`; `api/*`; `pwa/*`; `app/*`; `.oxlintrc.json`; any T22 artifact (T23-D11).

## 6 · Acceptance criteria

**AC1** the three survive the journey, structurally (T23-D5) · **AC2** a consumed callback leaves none of the
three, with the T23-D13 presence control · **AC3** single-use proven by **replay**: the same URL a second time is
discarded, exchanges nothing, same neutral copy · **AC4** no discard deletes (T23-D2), over all five failure
shapes · **AC5** one call site removes all three; a store that throws for every call leaves all three (T23-D3) ·
**AC6** deletion only after every consumer has read — **behavioural** (F6): the success case is the control, a
`getItem`/`removeItem` ordering case names it, and a mutant moving the call earlier must die · **AC7**
StrictMode: one handling, **one** deletion, and the final route is **not** the discard route (F4) · **AC8** a
throwing `removeItem` leaves the visitor's outcome unchanged (T23-D4) · **AC9** nothing else touched, no `clear`,
no key hand-spelled · **AC10** a new sign-in after a consumed callback works end to end · **AC11** no new string,
no catalogue key, L4 copy identical · **AC12** every inherited assertion updated with its reason restated
(T23-D8, D9, D10, D11) · **AC13** suite green with the one known environmental failure, **by exit code** ·
**AC14** no new dependency; no production change outside `authorizeRequest.ts` and `CallbackScreen.tsx`.

## 7 · Constraints

Deletion must not become a denial of service (T23-D2). `codeExchange.ts` and `idTokenNonce.ts` keep no side
effects. The deletion is idempotent and may run against an already-empty store. `removeItem` can throw. Nothing
is added to a failure path, so no copy can drift (L4). **Vacuity is the standing risk** — T23-D13 is its AC-level
answer. Measured traps: `createMemoryRouter` writes neither `window.location` nor `history`; a
`Storage.prototype` spy must be installed before the code under test runs; jsdom has no `navigator.locks`,
`indexedDB` or `caches`; comment stripping collapses lines.

## 8 · One thing the owner should know

**T23-D2 means "single-use" is enforced only on the path that succeeded.** A callback whose exchange failed can
be retried with the same `state`. That is deliberate and argued above, but it is narrower than the phrase alone
suggests. If the strict reading is wanted, it is a one-line move of the call site plus the five AC4 rows.

## 9 · No blockers

---

## Next — Phase 5 — implementation plan.
