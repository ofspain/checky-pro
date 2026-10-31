<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) -->

# frontend · T30 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Findings

**Issue: Transport-level failures thrown by `api.GET` escape as raw errors, not `ReadProfileError`.**
· Evidence: `frontend/src/profile/readProfile.ts:77-81` awaits `api.GET('/userinfo', { redirect: 'manual' })` without a `try/catch`. `openapi-fetch` resolves for HTTP error statuses but **rejects** if the underlying `fetch` throws (network error, CORS failure, DNS resolution, aborted connection). A raw `TypeError` or `DOMException` would propagate to the caller with a stack trace and browser-specific detail, violating AC8's "no detail, no stack, no cause" rule.
· Recommendation: Wrap the `api.GET` call in a `try/catch` and convert any thrown exception into `new ReadProfileError('unavailable')`. Add a test where the stubbed `fetch` rejects with a `TypeError` and assert the caught error is a `ReadProfileError` whose message/stack contains none of the original detail.
· Confidence: **High**

**Issue: `Profile` is hand-written rather than derived from the generated contract type.**
· Evidence: `frontend/src/profile/readProfile.ts:22-31` defines `Profile` inline. Phase 4 F10 froze: "`Profile` derived from the generated `UserInfoResponse`". The hand-written type happens to match `contracts/api/oidc.yaml`'s `UserInfoResponse` today, but a contract change (e.g., adding `middle_name` or changing `picture` to an object) will not produce a compile error in `readProfile`; it will silently drift.
· Recommendation: Derive `Profile` from the generated client, e.g. `export type Profile = components['schemas']['UserInfoResponse']`, imported from `@themistra/api-client`. If the generated export path is awkward, add a static compile-time assertion (e.g., `type _Assert = AssertEqual<Profile, UserInfoResponse>`) so a drift fails `tsc --noEmit`.
· Confidence: **Medium**

**Issue: `ReadProfileError` messages are hard-coded English strings, creating an L11 trap for the first screen.**
· Evidence: `frontend/src/profile/readProfile.ts:53-57` maps each failure to a fixed English sentence (`'No session.'`, `'Not authorized.'`, `'Profile unavailable.'`). `agents.md` L11 / `design.md` L11 require every user-facing string to live in the catalogue. T30-D3 says no screen consumes the profile yet, but the first consumer could render `error.message` directly, and nothing in the code or tests forbids it.
· Recommendation: Either (a) add a comment and a T30 test documenting that `ReadProfileError.message` is internal and must not be rendered, or (b) replace the fixed strings with catalogue-key lookups now and return `{ failure, messageKey }`. Option (a) is smaller and keeps T30 in scope.
· Confidence: **Medium**

**Issue: `/userinfo` is described as authenticated in prose but declares no `security` requirement in the contract.**
· Evidence: `contracts/api/oidc.yaml:51-96` documents `/userinfo` as carrying a bearer and renewing on 401, but the operation block has no `security:` stanza. OpenAPI 3 treats an operation with no `security` as unauthenticated unless global `security` is defined, and `oidc.yaml` defines none. The generated client therefore offers no compile-time signal that this operation needs a bearer.
· Recommendation: Add `security: - bearerAuth: []` to the `/userinfo` GET operation and define `bearerAuth` under `components/securitySchemes` (or reference the existing scheme if `openapi-typescript` resolves external `$ref`s). If referencing auth.yaml is impractical, document the omission as a contract limitation in the file description.
· Confidence: **Medium**

**Issue: No test exercises a 429 through `createOidcApi`, so the first authenticated OIDC operation's rate-limit path is unproven in this file.**
· Evidence: `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` tests 401/403 renewal (AC4) but never drives a 429. `createOidcApi` mirrors `createAuthApi` and registers the rate-limit middleware first, yet R23's "fixed generic copy with no countdown" is only as reliable as the wiring here.
· Recommendation: Add one T30 case: wire `createOidcApi` with a real `createRateLimitNotice()` instance, return 429 from the stubbed `/userinfo`, and assert `rateLimitNotice.isShown()` becomes `true`. This proves the OIDC client factory preserves R23's behavior for authenticated protocol calls.
· Confidence: **Medium**

**Issue: The short-circuit `tokens.current() === null` is racy against concurrent token clearance.**
· Evidence: `frontend/src/profile/readProfile.ts:75` checks the token synchronously, then `api.GET` asynchronously reaches the session boundary's `onRequest`, which reads `tokens.current()` again. Between the two reads the token can be cleared by cross-tab renewal failure, sign-out, or a concurrent `clearAccessToken()`. The first check passes, the second sees `null`, and a bearer-less request is sent — exactly the 302 side-effect AC12 exists to prevent.
· Recommendation: Either document this race as acceptable because `redirect: 'manual'` and the 3xx check make the outcome a no-session error anyway, or move the "no token, no request" guard into the boundary's `onRequest` so the decision and the request construction happen at one point. If left as-is, add a T30 test that stubs `tokens.current()` to return a token on the first call and `null` thereafter, and assert no request is issued.
· Confidence: **Low / Medium** (outcome is safe; the side-effect window is the concern)

**Issue: AC5's decode-pattern list is enumerative and will miss a JWT library that does not name its function `jwtDecode`/`decodeJwt` or use `atob`.**
· Evidence: `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:198` defines `TOKEN_DECODE = '\batob\b|\bjwt[Dd]ecode\b|\bdecodeJwt\b|split\([\'"]\.[\'"]\)'`. An import such as `import { jwtVerify } from 'jose'` or `import { decode } from 'some-jwt-lib'` would not match the pattern, and the claim scan would have no decoding file to scope itself to.
· Recommendation: The implementation notes (S3) already acknowledge this as a deliberate trade. Make the residual risk explicit in AC5 with a comment and a control: add a synthetic case using a plausible alternate decode helper name (e.g., `jwtVerify`, `decode`) and assert the scan flags it, so a widener is needed the day such a library enters the bundle.
· Confidence: **Low** (deliberate trade, but the control does not yet exist)

**Issue: The contract documents only a 302, but `readProfile` treats any 3xx as no-session.**
· Evidence: `contracts/api/oidc.yaml:79-88` declares a single `'302'` response. `frontend/src/profile/readProfile.ts:85` checks `response.status >= 300 && response.status < 400`. If SAS ever returns 303 or 307, the code is correct but the contract understates what it handles; conversely, if the contract is meant to be precise, the code is broader than documented.
· Recommendation: Either document the response as a generic 3xx redirect in the contract, or narrow `readProfile` to the status codes SAS is measured to emit (302, and 303/307 if measured). Add a one-line note explaining the choice.
· Confidence: **Low**

---

**Phase 8 complete.** Eight findings returned; no production or test code rewritten.
