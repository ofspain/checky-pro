# frontend · T24 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. The design is sound: one module owns the target, validation happens on read, and the allowlist is derived from the route table. The findings are about incomplete inherited-assertion coverage, allowlist derivation details, and a throw-safety contract that must be explicit.

---

## F1 · The brief under-lists the inherited assertions T24 breaks in T22 and T23

**Severity.** High.

**Evidence.**
- T24 adds navigation to `CallbackScreen.tsx`'s success path. After success the router will no longer be on `/app/callback`.
- T23 cases that assert the success path ends on `/app/callback`:
  - `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:388` — `removes all three once the token is held`
  - `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:569` — `still signs the visitor in, and leaves all three, when every removal throws`
  - `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:617` — StrictMode case
- T22 cases that assert the callback URL remains in the router after success:
  - `tokensAreNeverWrittenToAnyStorage.test.tsx:635-636` — the AC5 control that `routerLocation` contains `code=THE-CODE` and `state=...`
  - `tokensAreNeverWrittenToAnyStorage.test.tsx:640` — `historyState` is `'null'` (still true, but the reason changes)
- The brief's Files to Modify lists `stateSurvivesRedirectAndIsConsumedOnce.test.tsx` only for the key-set pin message, and `tokensAreNeverWrittenToAnyStorage.test.tsx` only for the allowlist and the "four files" case.

**Recommended brief amendment.** Expand Files to Modify for both files:
- `stateSurvivesRedirectAndIsConsumedOnce.test.tsx`: update every success-path case that asserts `CALLBACK_ROUTE` to assert the destination returned by `takeReturnTarget()` (home when no target, the target when one is remembered). Restate the reason: T24's navigation is the new end of the success path.
- `tokensAreNeverWrittenToAnyStorage.test.tsx`: update the AC5 URL case so its control proves the callback query was present **before** navigation, or remove the "contains code/state" control and replace it with a T24-owned assertion that navigation strips the query. The sweep itself should still run after the flow settles on the post-login route.

**Confidence.** High — these assertions will fail when T24 lands unless they are updated.

---

## F2 · The allowlist derivation must explicitly exclude parameterized routes

**Severity.** Medium.

**Evidence.**
- `app/routes.tsx:32-44`: `AUTHENTICATED_ROUTE_PATHS` includes `'invoices/:id'` and `'receipts/:id'`.
- T24-D5 says exact-path matching excludes these two.
- The brief says the allowlist is "derived from the route table" but does not specify the derivation code.

**Recommended brief amendment.** State the derivation rule explicitly: the allowlist is the set of paths in `AUTHENTICATED_ROUTE_PATHS` that contain no `:` segment, plus `HOME_PATH`. Record that parameterized routes are excluded and why (T24-D5). Add a test case proving `invoices/:id` is not honoured and that adding a non-parameterized route to `AUTHENTICATED_ROUTE_PATHS` makes it legal without a second edit.

**Confidence.** Medium — the intent is clear, but the mechanism is not.

---

## F3 · The validator's behaviour for paths that include the basename is undefined

**Severity.** Medium.

**Evidence.**
- `takeReturnTarget()` will be called with a raw string from storage.
- A future caller (or a hand-edited store) could write `/app/home` instead of `home`.
- The allowlist contains paths relative to `APP_BASENAME` (e.g., `'home'`).
- The brief does not say whether `/app/home` is normalised, rejected, or treated as a non-matching string.

**Recommended brief amendment.** Decide and freeze:
- **Option A (strict):** reject any stored value that contains `/` other than a single internal separator (e.g., `account/sessions` is allowed, `/app/home` and `//evil` are rejected). This keeps the validator simple and pushes callers to store relative paths.
- **Option B (lenient):** strip a leading `APP_BASENAME` and then match against the allowlist.

Either is acceptable, but the choice must be explicit because it affects whether `rememberReturnTarget('/app/home')` round-trips to `home` or falls back.

**Confidence.** Medium — undefined behaviour in a security boundary.

---

## F4 · `takeReturnTarget()` must guarantee it never throws

**Severity.** Medium.

**Evidence.**
- `CallbackScreen.tsx:132-170` uses `exchangeAuthorizationCode(...).then(onFulfilled, discard)`. A synchronous throw inside `onFulfilled` is **not** caught by the second argument.
- `takeReturnTarget()` will be called inside `onFulfilled`, after `consumeAuthorizeState()`.
- The brief says the function guards storage reads and never returns null, but it does not explicitly say it catches all internal errors.
- The validator (e.g., decoding a malformed URL, building the allowlist set) could throw.

**Recommended brief amendment.** Add a constraint: `takeReturnTarget()` is a total function — any internal error (storage throw, malformed stored value, unexpected input) results in the home path. Every code path must be wrapped so the caller cannot throw. Include a test case that corrupts the stored value (e.g., `sessionStorage.setItem(RETURN_TARGET_KEY, '{}')`) and asserts the result is home without throwing.

**Confidence.** High — an uncaught throw here would blank the screen at the end of a successful sign-in.

---

## F5 · A throwing `removeItem` after a successful read leaves the target in storage

**Severity.** Low.

**Evidence.**
- The brief says "read and delete in one call, so it cannot be deleted before it is read" (AC7).
- If the read succeeds and the `removeItem` throws, the validated path is still returned and navigation happens.
- The item is therefore not deleted, violating L16's "single-use and deleted at callback" for that tab.

**Recommended brief amendment.** Record this as a known consequence, following the same pattern as T23-D4: the visitor's outcome is unchanged, and the next `rememberReturnTarget` call (at the start of the next sign-in) overwrites the stale value. A test case should assert the navigation still happens and the item remains when `removeItem` throws.

**Confidence.** Low — same shape as T23's guard, and the realistic failure denies all storage operations.

---

## F6 · T25's rejection families need the validator to be centralized

**Severity.** Low.

**Evidence.**
- T24 builds `returnTarget.ts` and its validator.
- T25 adds rows for absolute URLs, protocol-relative URLs, `javascript:`, backslash paths, traversal, encoded traversal, prefix matches, public routes, nonexistent routes, parameterized routes, and query/fragment targets.

**Recommended brief amendment.** Ensure `returnTarget.ts` exports a single validation helper (e.g., `isAllowlistedTarget(path): boolean`) that T25's cases can import and exercise directly, so T25 does not duplicate the allowlist construction. The brief already partitions the file; exporting the predicate makes that partition clean.

**Confidence.** Low — architectural convenience, not a behaviour defect.

---

## F7 · The named test file is shared with T25 but T24 does not pre-empt its rows

**Severity.** Low.

**Evidence.**
- Required Tests says T24 writes the file and leaves T25's rejection table "empty-but-stated".
- If the empty section is not clearly marked, T25 may have to guess where to add rows.

**Recommended brief amendment.** Require the file to contain a placeholder `describe('a target outside the allowlist falls back to home (T25)', () => { it.todo('absolute URL'); it.todo('protocol-relative URL'); ... })` so the partition is visible in the source and T25's scope is obvious.

**Confidence.** Low — documentation and scope clarity.

---

## F8 · No decision on whether `rememberReturnTarget` guards its input type

**Severity.** Low.

**Evidence.**
- `rememberReturnTarget` is the write side and is not the trust boundary.
- A future caller passing a non-string would be coerced to string by `sessionStorage.setItem`.
- The read-side validator must handle whatever string results.

**Recommended brief amendment.** Either specify that `rememberReturnTarget(target: string)` types the input as `string` (so TypeScript catches non-string callers), or document that the function accepts `unknown` and stringifies it before storage. The former is simpler and keeps the stored shape predictable.

**Confidence.** Low — API contract detail.

---

## Summary

F1 is the most urgent: T24's navigation will break existing tests that assert the success path stays on `/app/callback`, and the brief must list those updates explicitly. F2-F4 are design details that need to be frozen before implementation. F5-F8 are smaller scope, API, and documentation items.
