<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) -->

# frontend · T36 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Findings

### F1 — Holding the `id_token` in `auth/accessToken.ts` conflates two different tokens
· **Severity:** **Major/Blocker**
· **Evidence:** `accessToken.ts:1-24` is named and documented as "the tab's access token" and "the one place in this package where a module-level `let` is the requirement rather than a smell." T21's suite pins `let held: string | null = null;` there. The brief proposes adding a second `let` for the `id_token`.
· **Assessment:** The access token and the ID token have different lifecycles, different consumers, and different security properties. The access token is used for API calls; the ID token is used only for the end-session hint. Putting both in `accessToken.ts` makes the module responsible for two things and forces T36 to edit a closed task's module despite the "Files NOT to Modify" rule. A separate `auth/idToken.ts` module would keep the concerns separated, avoid editing T21's file, and still give sign-out one module to ask (T35 could add it to the inventory).
· **Recommended brief amendment:** Either move the `id_token` to its own module (`auth/idToken.ts`) with `retainIdToken`, `clearIdToken`, and `currentIdToken`, or rename `accessToken.ts` to something like `sessionTokens.ts` and update its doc comment to reflect that it now holds both tokens. If the brief keeps the second `let` in `accessToken.ts`, it must explicitly justify why violating the module's single-responsibility claim is better than a new file.

### F2 — The read-before-clear ordering for `id_token` is subtle and unprotected
· **Severity:** **Major**
· **Evidence:** The brief says `signOut()` clears the `id_token` (State Changes) and that `signOut()` completes before the hand-over (AC1). The end-session URL needs `id_token_hint`, so `endSession()` must read the token before calling `signOut()`.
· **Assessment:** This creates a fragile temporal coupling: `endSession()` must read the `id_token`, then call `signOut()`, then build the URL. A future refactor that moves `signOut()` earlier would silently omit the hint. There is no primitive that both returns and clears the token atomically, so the window between read and clear is human-maintained.
· **Recommended brief amendment:** Add a `takeIdToken()` primitive (or `takeIdentityToken()`) that returns the held token and clears it in one operation. `endSession()` calls it first, uses the result for the URL, then calls the rest of `signOut()`. This makes the ordering impossible to get wrong and gives AC5 a direct instrument.

### F3 — AC8's "id_token reaches no storage surface" conflicts with the URL containing it
· **Severity:** **Major**
· **Evidence:** AC8 says "The retained `id_token` reaches no storage surface, no console channel and no analytics call." AC5 says `id_token_hint` carries the retained `id_token`. The hand-over is a full-page navigation to `/connect/logout?id_token_hint=...`.
· **Assessment:** The `id_token` does not reach storage, console, or analytics, but it does reach the URL, which is a surface. AC8's wording could be read as forbidding the URL too, which would contradict AC5. The sweep must be carefully scoped to exclude the query parameter it is supposed to contain.
· **Recommended brief amendment:** Rewrite AC8 to say "The retained `id_token` reaches no storage surface, no console channel and no analytics call; the only place it appears is the `id_token_hint` query parameter of the end-session URL." Add a test that asserts the id_token appears exactly once and only in that parameter.

### F4 — The "registered allowlisted default" comparison is underspecified
· **Severity:** **Major**
· **Evidence:** AC4 says `post_logout_redirect_uri` "is derived from the route table" and is "equal to the registered allowlisted value." The brief cites `SasLoginIntegrationTest:519-527` as the proven URL shape.
· **Assessment:** The frontend has no access to the backend's registered allowlist at runtime. "Equal to the registered allowlisted value" can only mean (a) the test hard-codes the expected value and compares, or (b) the test derives the value and trusts the backend. Neither is a runtime check. The wording implies a verification that cannot be performed in the SPA.
· **Recommended brief amendment:** Change AC4 to "`post_logout_redirect_uri` is derived exactly as `${origin}${de-slashed basename}/signed-out`, matching the shape the backend registers." Drop the claim about equality to the registered value, or make it a backend integration test concern.

### F5 — Counting "exactly one hand-over" across four navigation APIs is brittle
· **Severity:** **Major**
· **Evidence:** AC2 says "Exactly one hand-over, counted across `assign`, `replace`, the `href` setter and `window.open`." The module must use `globalThis.location` for the full-page navigation.
· **Assessment:** In jsdom, intercepting the `href` setter on `location` is not as straightforward as stubbing `assign`/`replace`. The test harness needs to cover all four paths, and a bug in the harness could make a real double-navigation pass. Also, `window.open` is not a realistic hand-over mechanism for this flow.
· **Recommended brief amendment:** Narrow AC2 to the mechanisms the module actually uses (`assign` and `replace`), and add a source scan forbidding the others. Keep a control that proves the watcher catches a second call to the chosen mechanism.

### F6 — No `state` parameter removes correlation without a security reason
· **Severity:** **Minor/Major**
· **Evidence:** OQ-T36-2 freezes no `state` parameter because verifying it would require storage, and L16's `state` entry is for authorize state. The backend test includes `state`.
· **Assessment:** The security argument is weak. A `state` parameter for logout correlation does not need to reuse the authorize state key; it could be a one-time nonce stored in memory or even omitted if SAS doesn't require verification. The brief says OIDC makes it optional, but the real reason seems to be avoiding storage complexity. Without state, the `/app/signed-out` screen cannot verify the logout originated from this tab.
· **Recommended brief amendment:** Either (a) keep the decision but justify it as "SAS does not require it and the allowlisted redirect is sufficient," or (b) add an in-memory `state` nonce that `endSession()` generates and the signed-out screen verifies (task 37). If option (a), document that the allowlisted URI is the security control, not state.

### F7 — The end-session URL builder should not be reachable from multiple modules
· **Severity:** **Minor/Major**
· **Evidence:** AC10 says "Only sign-out ends the SAS session: no other production module builds that URL or reaches the endpoint."
· **Assessment:** This is a good rule, but it needs an enforcement mechanism. The brief does not specify how to scan for it. The PWA tests already build end-session URLs for denylist testing, so the scan must distinguish production from test code.
· **Recommended brief amendment:** Add a source scan that asserts `/connect/logout` appears in exactly one production module (`endSession.ts`) and in no other production file. Document that test files are excluded.

### F8 — The `id_token` retention modifies `CallbackScreen.tsx`, a closed task
· **Severity:** **Minor/Major**
· **Evidence:** The brief proposes adding one line to `CallbackScreen.tsx` to retain the `id_token` beside `retainAccessToken`.
· **Assessment:** `CallbackScreen.tsx` is owned by tasks 17-20. Adding a line changes its behavior. While the change is small and on the far side of the nonce check, it still edits a closed task. The T19/T20 tests may need updating to assert the id_token is retained.
· **Recommended brief amendment:** If the brief proceeds with this edit, identify which T19/T20 tests need to assert the new retention. Alternatively, move the retention into a separate module imported by `CallbackScreen.tsx`, so the closed file's change is just an import and a function call.

### F9 — T35's AC7 strengthening edits a closed task's test file
· **Severity:** **Minor**
· **Evidence:** AC9 says "T35's AC7 is strengthened to pin declaration counts for *cleared* files as well as excused ones." T36 appends to `signOutClearsMemoryAndEndsSasSession.test.tsx`.
· **Assessment:** This is the planned extension point, but it means T36 changes T35's test logic. The change is justified, but it should be clearly scoped so future readers know T35's original intent is preserved.
· **Recommended brief amendment:** In the test file, mark the T36-added AC7 logic with a comment citing this task, and ensure the original T35 cases remain unchanged.

### F10 — No production caller exists (OQ-T36-5)
· **Severity:** **Blocker** (process)
· **Evidence:** OQ-T36-5 says nothing in the SPA can trigger sign-out, so T36 joins seven dormant tasks.
· **Assessment:** As with T33/T35, this means the behavior is built but unreachable. The brief acknowledges it but does not resolve it.
· **Recommended brief amendment:** Identify the task that will own the sign-out affordance, or record explicitly that AC1-AC12 are verified only by direct invocation until that task exists.

### F11 — The `id_token` could be retained before the nonce check if the line moves
· **Severity:** **Minor**
· **Evidence:** The brief says the retention line goes "on the far side of the nonce check, beside `retainAccessToken`."
· **Assessment:** This is the correct placement. A source scan could pin it: assert that `retainIdToken` (or equivalent) appears only after `idTokenNonceMatchesStoredValue` in `CallbackScreen.tsx`.
· **Recommended brief amendment:** Add a source-order test in the T19/T20 suite or the T36 suite that asserts the retention happens after the nonce check.

### F12 — What happens if `endSession()` is called twice?
· **Severity:** **Minor**
· **Evidence:** The brief does not mention idempotence for `endSession()`.
· **Assessment:** `signOut()` is idempotent, but a second `endSession()` call after the first navigation has started would attempt another navigation. Since the first navigation leaves the page, a second call is unlikely in practice, but tests should cover it.
· **Recommended brief amendment:** Add a test that a second `endSession()` does not perform a second hand-over, or document that the function is not expected to be called twice.

### F13 — The `id_token` is not cleared if the browser navigation is blocked
· **Severity:** **Minor**
· **Evidence:** `endSession()` calls `signOut()` before navigating. `signOut()` clears the id_token.
· **Assessment:** This is correct: the token is cleared regardless of whether the navigation succeeds. But if a popup blocker prevents `window.open` (if used), the token is still cleared. Good.
· **Recommended brief amendment:** None.

### F14 — The full-page navigation uses `globalThis.location`, conflicting with T33/T35 scans
· **Severity:** **Minor**
· **Evidence:** T33 and T35 source scans forbid `globalThis.location`/`globalThis.history`. T36 requires it.
· **Assessment:** The brief notes this explicitly in Constraints, which is good. But the T33/T35 scans scan all production files; they may flag `endSession.ts` unless they are scoped to exclude it or the scan pattern is module-specific.
· **Recommended brief amendment:** Ensure T33/T35 scans are scoped to the modules they own, or add an explicit exclusion for `endSession.ts` in those scans with a justification citing R57.

### F15 — The end-session URL path `/connect/logout` is hard-coded
· **Severity:** **Minor**
· **Evidence:** The brief says the URL is `/connect/logout` on the SPA's own origin.
· **Assessment:** This is a backend-defined path. Hard-coding it in the SPA is acceptable if it is published as a constant, but a second spelling in the PWA denylist already exists. The brief should ensure both use the same constant.
· **Recommended brief amendment:** Publish `END_SESSION_PATH = '/connect/logout'` from one module and reference it in both `endSession.ts` and the PWA denylist tests.

---

## Summary

The brief correctly identifies the hand-over as a full-page navigation and the need to retain/clear the `id_token`. The largest design risks are **F1** (whether `accessToken.ts` should hold the `id_token`), **F2** (the fragile read-before-clear ordering), **F3** (AC8's conflict with the URL), **F4** (the underspecified allowlist comparison), and **F5** (the four-mechanism hand-over count). The dormant-caller problem (**F10**) remains unaddressed, as with T33 and T35.

**Phase 3 complete.** Fifteen findings returned; no code or contract changed.
