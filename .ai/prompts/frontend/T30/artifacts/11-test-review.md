<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) -->

# frontend · T30 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Scope

Reviewed `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` against the frozen acceptance criteria for T30, R20, L3, L6, L16, L18, and `agents.md` §Storage / §Enumeration safety.

## Summary

The suite is dense and well-hardened: 46 cases, 20 instruments with dirty-proofs, 28 mutants killed, and the Phase 8 findings are folded in (transport throws, 429 re-send wiring, `bearerAuth` contract declaration, internal error messages, the I6 race). The remaining gaps are narrow edge cases around browser redirect semantics, token-shape short-circuits, and bearer leakage that the existing tests do not explicitly drive.

## Recommendations

**Gap: `response.type === 'opaqueredirect'` is asserted in `readProfile.ts` but never exercised.**
· Why it matters: With `redirect: 'manual'`, some browsers return a `Response` whose `type` is `'opaqueredirect'` and whose `status` is `0`, not a 3xx. `readProfile.ts:118` handles this, but every AC12 test drives the code through either a 3xx status or `redirected: true`. Removing the `response.type === 'opaqueredirect'` clause would leave the suite green while breaking the no-session path in those browsers.
· Suggested test: Stub a `Response` with `type: 'opaqueredirect'` and `status: 0` (using `Object.defineProperty` as the other redirect stubs do), call `readProfile`, and assert it rejects with `failure: 'no-session'`.

**Gap: An empty-string token is not short-circuited and issues a `Bearer ` request.**
· Why it matters: `readProfile.ts:83` checks `tokens.current() === null`, so an empty string `''` passes the guard. The session boundary then sends `Authorization: Bearer ` (a malformed bearer), which triggers the 302/401 path. The outcome is safe, but AC12's strongest claim is "no request at all when the tab holds no token" — and an empty token is not a usable token. The current suite only tests `null`.
· Suggested test: Call `readProfile` with `tokens.current()` returning `''` and assert either that no request is issued and the failure is `no-session`, or document that empty-string tokens are treated as malformed and the request is intentional.

**Gap: The bearer token is not explicitly checked against console and surface leakage.**
· Why it matters: AC6/AC7 sweep for profile claim values, but the in-memory access token itself (`TOKEN`) is also sensitive (L3). A regression that logged the Authorization header or the token value would not be caught by the existing leak sweep, which only watches `PROFILE_VALUES`. The AC4 bearer test records `tab.calls[0].auth` but does not watch console or surfaces.
· Suggested test: In the bearer test, install the six-channel console watcher and run the surface sweep, then assert `TOKEN` appears on no surface and in no console channel.

**Gap: No test drives an invalid-JSON / parse-failure response.**
· Why it matters: AC8 covers HTTP error bodies and HTML login pages, but not the case where `/userinfo` answers `200 OK` with malformed JSON. `openapi-fetch` will set `data` to `undefined` in that case, and `readProfile.ts:126` collapses it to `unavailable`, but the suite does not pin it. A future change that tried to read `answer.error` or that threw a different error shape could slip through.
· Suggested test: Return a response with `content-type: application/json` and a body of `{ not json`, then assert the result is a `ReadProfileError` with `failure: 'unavailable'` and no parse detail surfaces.

**Gap: A followed redirect with a valid JSON body is not tested.**
· Why it matters: AC12 case 41 drives `redirected: true` with an HTML body. The code checks `response.redirected` before checking `response.ok`, but the suite does not prove that a followed redirect carrying a perfectly valid `UserInfoResponse` is still rejected as `no-session`. Without this, a regression that reordered the checks could parse a redirect response as a profile.
· Suggested test: Return `{ status: 200, redirected: true, body: FULL_PROFILE }` and assert `readProfile` rejects with `failure: 'no-session'`.

**Gap: `document.cookie` and `indexedDB` are not swept, and the omission is not stated.**
· Why it matters: T29 explicitly declared `document.cookie` and `indexedDB` out of reach because jsdom offers an empty cookie jar and no `indexedDB`. T30 reuses the same surface list but does not restate the declaration. Because a profile is PII, a later maintainer might assume the sweep is complete.
· Suggested test: No product test is needed, but add a comment in `surfaces()` — or a T30 declaration matching T29-D2 — naming the two surfaces as intentionally unswept due to jsdom limitations, so the scope of AC6 is unambiguous.

**Gap: No test asserts `ReadProfileError.cause` is absent.**
· Why it matters: Modern JavaScript errors often carry a `cause` property. AC8 already pins that the error cannot carry detail via its message, but a `cause` carrying the original `TypeError` or `DOMException` would be another leak path. The current catch block does not set `cause`, but no test documents or enforces that.
· Suggested test: After catching a `ReadProfileError` from any failure path, assert `'cause' in error` is `false` (or that `error.cause === undefined`).

**Gap: The `data === undefined` check does not explicitly cover `data === null`.**
· Why it matters: `readProfile.ts:126` checks `data === undefined`, but `openapi-fetch` could theoretically return `data: null` for a `null` JSON body. `typeof null.sub` evaluates to `'undefined'` in JavaScript, so the code is safe, but this non-obvious safety is not pinned. A future refactor might replace `typeof data.sub` with `data.sub` direct access and throw.
· Suggested test: Drive a response with `body: null` and assert the result is `ReadProfileError` with `failure: 'unavailable'` rather than a thrown `TypeError`.

---

**Phase 11 complete.** Eight recommendations returned; no production or test code rewritten.
