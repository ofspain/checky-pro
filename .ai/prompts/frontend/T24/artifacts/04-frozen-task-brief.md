# frontend · T24 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Every cited `file:line` in Kimi's challenge was checked against source before this gate decided anything.
**All eight findings accepted**, one of them (F1) **expanded**, because its list was incomplete and its line
numbers for the named test pointed at neighbouring cases. Two findings the challenge did not raise are added, one
of which changes production behaviour.

---

## 1 · Phase 3 disposition

| Finding | Decision |
|---|---|
| **F1** the brief under-lists the inherited assertions T24 breaks | **Accepted and EXPANDED.** The T22 citation (`:635-636`) is exact; the three named-test citations point at neighbouring cases, and the real inventory is **larger and spans five files, not two**. See §2. |
| **F2** the derivation must exclude parameterised routes explicitly | **Accepted.** The rule is frozen in §3 as code-level prose. **T24-D5** already owned the exclusion; this names the mechanism. |
| **F3** behaviour for a value carrying the basename is undefined | **Accepted, and resolved without a special rule** — Kimi's Option A in effect. The allowlist **is** the rule: a set of router-relative paths, matched by membership. `/app/home` is simply not a member, so it falls back. No stripping, no normalising, no second code path. **T24-D8.** |
| **F4** `takeReturnTarget()` must never throw | **Accepted, High.** The mechanism is confirmed in source: `CallbackScreen.tsx:132` is `exchangeAuthorizationCode(...).then(onFulfilled, discard)`, and a **synchronous throw inside `onFulfilled` is not caught by the second argument** — T21 measured exactly that. It becomes a total function. **T24-D10.** |
| **F5** a throwing `removeItem` after a successful read leaves the target behind | **Accepted**, recorded in T23-D4's shape: the visitor's outcome does not change, and the next `rememberReturnTarget` overwrites it. **T24-D9.** |
| **F6** export the predicate so T25 does not rebuild the allowlist | **Accepted.** `isAllowlistedTarget(target: string): boolean` is exported alongside the two functions. |
| **F7** make the partition visible in the source | **Accepted.** T25's block exists with `it.todo` rows, one per rejection family, so its scope is readable in the file rather than only in an artifact. |
| **F8** type the capture's input | **Accepted.** `rememberReturnTarget(target: string): void`. The read side still treats what it finds as untrusted, because a store is not a type system. |

**Rejected: none.**

## 2 · The inherited-assertion inventory (F1, expanded)

T24 adds a navigation to the successful path, so **every case that asserts where a successful callback ends** is
now false. Measured by searching the suite, not inferred from the challenge:

| File | Case | What breaks |
|---|---|---|
| `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:420` | removes all three once the token is held | `CALLBACK_ROUTE` |
| `…:624` | still signs the visitor in when every removal throws | `CALLBACK_ROUTE` |
| `…:672` | StrictMode: handles once, deletes once | `CALLBACK_ROUTE` ("the second pass did not discard") |
| `callbackValidatesNonce.test.tsx:593` | **holds on the callback screen when the nonce agrees** | the route **and the case's name** — plus `en.callback.working`, which is gone once the screen unmounts |
| `…:671` | reaches the decision through the exchange | "the control: it really did hold" |
| `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:746` | completes the exchange when admitted | route + `en.callback.working` |
| `…:825` | fires exactly one request under StrictMode | route + `querySelectorAll('main')` |
| `…:838-845` | **survives the screen unmounting while the exchange is in flight** | see **T24-D7** — this one is a production decision, not a test update |
| `callbackWithMismatchedStateIsDiscarded.test.tsx:351` | and the reached callback stays put | route + `en.callback.working` |
| `…:970` | the same url exchanges once the stored value agrees | route |
| `tokensAreNeverWrittenToAnyStorage.test.tsx:635-636` | the AC5 URL control | `routerLocation` no longer contains `code=`/`state=` after navigation — **the control must move to before the navigation, not be deleted**, or T22's URL sweep becomes inert |

**The definitive list is re-measured at Phase 6 by running the suite with the production change in place and
before any test is edited**, exactly as T23 did. This table is the plan; the suite is the authority.

## 3 · Frozen decisions

| ID | Decision |
|---|---|
| **T24-D1** | *(owner)* **T24 builds the whole mechanism** — capture, storage, allowlist, validation, navigation. T41 is later left a one-line call. |
| **T24-D2** | *(owner)* **The allowlist is derived from the route table and matched as exact paths.** No second list, no pattern matching. |
| **T24-D3** | **Validation happens on read, once.** The read is the trust boundary; validating on write as well would be two code paths for one decision, and the write is not where the risk is. |
| **T24-D4** | **The target is deleted by the module that owns its reader**, not by `consumeAuthorizeState`. Both are "single-use and deleted at callback" (L16), but the three PKCE items are deleted because the *exchange* consumed them and the target because the *navigation* read it — different consumers, different moments. T23's key-set pin stays at three and its message is rewritten to say this. |
| **T24-D5** | *(declared limit)* **Parameterised routes are excluded**, so `invoices/:id` and `receipts/:id` are not honoured — the two routes **notification emails link to**. A visitor who clicks an invoice email, signs in, and should return to that invoice lands on home. Handoff, with its price: pattern matching for two routes plus traversal and encoded-separator handling. |
| **T24-D6** | A target carrying a **query or fragment** is not honoured. No current caller needs one; honouring one means deciding what may appear in a query. |
| **T24-D7** | **NEW — a visitor who has left the callback screen is not navigated.** `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:838-845` already states this intent in words — *"a visitor who leaves the callback screen mid-exchange gets no navigation yanked out from under them"* — and T24 is the first task that could violate it. The effect gains a cleanup that marks the mount as gone; the token is still held and the three items still consumed, because those are module-level facts the visitor's absence does not change. **That inherited case must then pass UNCHANGED**, which is how the decision is verified. |
| **T24-D8** | *(F3)* **The stored form is a router-relative path with a leading slash** — `/home`, `/account/sessions` — matched by **set membership** against `AUTHENTICATED_ROUTE_PATHS` entries containing no `:`, each prefixed with `/`. `navigate()` is basename-relative (T18 measured that a target without a leading slash resolves against the current route and lands on the catch-all silently), so this is the form the screen can hand straight to `navigate`. `/app/home` is not a member and falls back. **`rememberReturnTarget`'s documented contract is this form**, and T41 must strip the basename; Phase 6 probes what `useLocation()` yields under a basename so that handoff is accurate rather than assumed. |
| **T24-D9** | *(F5)* A `removeItem` that throws after a successful read leaves the target in storage. The visitor's outcome is unchanged and the next `rememberReturnTarget` overwrites it. Guarded per operation, T23-D4's shape. |
| **T24-D10** | *(F4)* **`takeReturnTarget()` is a total function.** Any internal failure — a throwing store, a corrupted value, an unexpected input — yields the home path. No code path may throw, because a throw inside the exchange's fulfilment handler is not caught by `discard` and would blank the page at the end of a successful sign-in. |
| **T24-D11** | **NEW — declared consequence: `/app/home` renders nothing.** The route is registered with no element, and **no task in the 142-task list builds a home screen**. So T24's fallback lands the visitor on a blank route. The navigation is still the verifiable claim (Phase 1 OQ-3), and no test may assert more than that. This is the **fifth unowned piece** in the package, beside the composition root, silent re-authentication, and the real-browser check. |

## 4 · Scope — final

**In.** `auth/returnTarget.ts`: the storage key, `rememberReturnTarget(target: string)`,
`takeReturnTarget(): string`, `isAllowlistedTarget(target: string)`, and the derived allowlist. One navigation in
`CallbackScreen`'s successful path, with the T24-D7 cleanup. `HOME_PATH` exported from `routes.tsx`. The named
test, T24's half. The eleven inherited assertions in §2.

**Out.** The producer (T41). Query and fragment targets (T24-D6). Parameterised routes (T24-D5). A home screen
(T24-D11). Sign-out's `post_logout_redirect_uri` (task 36, server-allowlisted). Silent re-authentication. T25's
rejection rows — written as `it.todo` only.

## 5 · Files — final

**Create:** `frontend/src/auth/returnTarget.ts`;
`frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx`.

**Modify:** `auth/CallbackScreen.tsx` (navigate + the T24-D7 cleanup); `app/routes.tsx` (`HOME_PATH`);
`auth/tokensAreNeverWrittenToAnyStorage.test.tsx` (allowlist row, the "four files" case → five, the AC5 URL
control); `auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` (three route assertions + the pin's message);
`auth/callbackValidatesNonce.test.tsx` (two cases, one renamed); `auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx`
(three cases; the unmount case must pass unchanged);
`auth/callbackWithMismatchedStateIsDiscarded.test.tsx` (two cases).

**NOT to modify:** `spec/**`; `auth/codeExchange.ts`, `auth/idTokenNonce.ts` (pure readers, T23-D1);
`auth/accessToken.ts`; `auth/authorizeRequest.ts` (T24-D4); `api/*`; `pwa/*`; `.oxlintrc.json` (T22-D12).

## 6 · Acceptance criteria — final

AC1–AC13 as Phase 2 wrote them, plus:

**AC14** a visitor who left the callback screen is not navigated, and the inherited case that says so passes
unchanged (T24-D7) · **AC15** `takeReturnTarget()` never throws, proven against a throwing store and a corrupted
stored value (T24-D10) · **AC16** a `removeItem` that throws still navigates, and the item remains (T24-D9) ·
**AC17** the derivation excludes `:`-bearing paths, and adding a plain path to `AUTHENTICATED_ROUTE_PATHS` makes it
legal with no second edit (F2).

## 7 · Required tests — final

T24's half as Phase 2 partitioned it, plus: a corrupted stored value; a throwing `removeItem`; the unmount case;
the derivation case in both directions (a `:` path is not legal, an added plain path is). T25's rejection families
are `it.todo` rows. Every "landed at X" case asserts the destination **and** that the other destination was
reachable from the same harness.

## 8 · Open questions

**No blockers.** Phase 1's OQ-1 and OQ-2 were decided by the owner. OQ-3 is **T24-D11**. OQ-4
(`oidcRoundTripEndToEnd`, no Playwright suite, no owning task) is unchanged and belongs to the package, not this
task.

**Two things the owner should know before Phase 6**, neither of which blocks it: **T24-D5** costs the
notification-email deep links, and **T24-D11** means the destination this task navigates to is a blank page until
some future task builds `/app/home`.

## Next — Phase 5 — implementation plan.
