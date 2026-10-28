# frontend · T22 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed the Phase 6 measurement notes and the 23 production files under `frontend/src` with fresh eyes. The production code is clean: no token is written to any storage, URL, or log. The measurement methodology, however, has the weaknesses the self-review identified and a few additional ones. Findings are against the methodology and the committed artifact, not the production code.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of source.

---

## I1 · The success case searched for an ID token that never entered the system

**Issue.** Phase 6 Probe A built the real ID token from the freshly generated nonce (`sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.nonce)`), but the leak sweep searched for the module constant `ID_TOKEN`, which was built from `STORED_NONCE`. Those are different strings. The "ID token absent from every surface" claim for the success case was therefore vacuous for the whole token and for the payload segment.

**Evidence.**
- `06-implementation-notes.md` lines 21-34.
- The other two JWT segments happened to match because `tokenWith` reused the same header and signature, so the finding was partially masked.

**Recommendation.** Concur with the self-review's disposition: Phase 10 must derive every searched token value from the response body the stub actually returned, never from a file-scoped constant. Add a control that deliberately fails if the searched value does not equal `response.idToken` / `response.accessToken`.

**Confidence.** High — the self-review already proved the mismatch, and the fix is mechanical.

---

## I2 · Probe B's failure matrix never produced a token to lose

**Issue.** Every failure-path row in Probe B sent the default `tokensBody()`, whose `id_token` carries `STORED_NONCE`, while the real authorize step wrote a fresh random nonce. Even rows that reached the nonce check could not have produced a held token, so `held: null` was over-determined. A row that fails twice measures neither failure cleanly.

**Evidence.**
- `06-implementation-notes.md` lines 51-61.

**Recommendation.** Phase 10 must send a valid ID token (matching the stored nonce) for rows whose failure is elsewhere: refused code, 5xx, dropped connection, unusable response. Only the "bad nonce" row should send a mismatched ID token. This isolates the failure under test.

**Confidence.** High — the self-review identified the same issue.

---

## I3 · `document.title`, `history.state` and `window.name` are empty-box assertions

**Issue.** jsdom starts these surfaces empty and nothing in the package writes to them. Asserting "no token here" is true by construction, not by measurement.

**Evidence.**
- `06-implementation-notes.md` lines 102-110.
- `CallbackScreen.tsx` uses `navigate(..., { replace: true })` with no state, and `SignInScreen.tsx` does not set `document.title` or `window.name`.

**Recommendation.** Phase 10 must write a sentinel into each surface before the flow and assert the sweep reads it back, then assert the token is absent. Document that this proves instrument reachability, not that the flow would have written there.

**Confidence.** High — the self-review identified the same issue.

---

## I4 · The DOM sweep read only the mounted container

**Issue.** The probe's `report()` searched `container.innerHTML`. A React portal, a `document.body`-level node, or an attribute on the document element would be missed.

**Evidence.**
- `06-implementation-notes.md` lines 74-80.
- No portal is used today, but the sweep should not depend on that remaining true.

**Recommendation.** Phase 10 should read `document.documentElement.outerHTML` (whole document, attributes included) and assert the sweep reads a known sentinel placed outside the mounted container.

**Confidence.** Medium — the self-review identified the same issue.

---

## I5 · Four acceptance-criteria areas were not measured

**Issue.** The following remain unverified in Phase 6 and must land in Phase 10:

| Not measured | AC |
|---|---|
| Throwing store (`storeEntries` calls `key()` and `getItem()` unguarded) | AC11 |
| Synthetic `Bearer <token>` leak control | AC15 |
| Two sign-ins in one tab, no residue from the first | — |
| Sweep after `clearAccessToken()` | — |

**Evidence.**
- `06-implementation-notes.md` lines 82-93.

**Recommendation.** Add these cases to Phase 10. The two-sign-in case is particularly important because `authorizeRequest.ts` overwrites the three L16 items; a stale token from the first sign-in must not survive in any surface into the second. The `clearAccessToken()` sweep documents that clearing leaves no residue.

**Confidence.** Medium — coverage gaps, not behaviour defects.

---

## I6 · The source scan can miss aliased storage/console references

**Issue.** The scan reads comment-stripped text. Shapes like `globalThis['localStorage']`, `'session' + 'Storage'`, or `const c = console; c.log(token)` can bypass a naive pattern. The self-review F6 notes this, but does not mention the `console` alias case.

**Evidence.**
- `06-implementation-notes.md` lines 95-103.
- `sessionBoundary.ts:35` uses `this.name = 'RenewalTimeoutError'`, which shows why a simple `/\.name\s*=/` pattern would false-positive.

**Recommendation.** Add `globalThis.console` and `window.console` to the forbidden patterns (or forbid any assignment to an identifier named `console` from a global alias). Include a synthetic-violation row that uses `globalThis['console'].log(token)` and assert the scan catches it. Document the scan's limits in the file header exactly as T22-D1 and F6 propose.

**Confidence.** Low — current production code has no such alias, but the scan is the durable guard.

---

## I7 · The `document.cookie` runtime sweep is limited by jsdom

**Issue.** jsdom's cookie jar does not fully emulate a real browser's cookie handling (HttpOnly, `Set-Cookie` from `fetch`, path/domain scoping). The runtime sweep can only assert that `document.cookie` gains no token value the test can see. It cannot prove a cookie was not set by a response header or an HttpOnly cookie.

**Evidence.**
- The frozen brief §7 already records that "a real cookie jar beyond `document.cookie`" is not proven.
- No API response in the sign-in flow sets a cookie today, but the sweep cannot observe that.

**Recommendation.** Ensure Phase 10's file header repeats the brief §7 limitation verbatim: the cookie assertion is a runtime sweep of `document.cookie`, not proof against response-header cookies. This prevents a future reader from over-reading the green checkmark.

**Confidence.** Low — the brief already acknowledges it; this is a reminder to carry it into the test file header.

---

## I8 · The source scan allowlist must be a table, not just a regex

**Issue.** T22-D11 requires `setItem` only in `auth/authorizeRequest.ts` and `removeItem`/`clear` nowhere today. If task 23 adds deletion without updating the allowlist, the scan will fail with a cryptic regex mismatch rather than a clear message.

**Evidence.**
- `04-frozen-task-brief.md` lines 51-52.

**Recommendation.** Implement the allowlist as a structured table in the test file — file path → permitted methods → reason — and assert each permitted call site against that table. The failure message should tell the reader which file violated which entry and direct them to update the table. This makes the allowlist maintainable.

**Confidence.** Medium — maintainability, not a behaviour defect.

---

## I9 · No committed artifact yet proves the package-wide claim

**Issue.** Phase 6 was a throwaway probe; `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts` does not exist in the repository. Until Phase 10 commits the test, the T22 claim is documented intent, not a reproducible instrument.

**Evidence.**
- `glob` for `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts` returns no file.
- `06-implementation-notes.md` lines 11-13: "Five throwaway probes ... run, read, and deleted."

**Recommendation.** This is expected given the brief's T22-D7 and the phase sequence, but it should be recorded in the Phase 8 artifact so the human gate knows the only deliverable is the methodology review. Phase 10 is the commit point.

**Confidence.** High — the absence is objective, but it is the planned phase boundary, not a defect.

---

## Summary

The production source is consistent with the "no token in any storage" requirement: only `authorizeRequest.ts` writes to `sessionStorage`, and it writes exactly the three L16 items; no production file calls `console`, `localStorage`, `document.cookie`, `indexedDB`, `caches`, `sendBeacon`, `new Image`, or writes to `window.name`. The findings are all about making the Phase 10 verification instrument honest: fix the ID-token search value (I1), isolate failure-path causes (I2), prove instrument reachability (I3, I4), close the remaining coverage gaps (I5), tighten the source-scan guard against aliases (I6), and state the cookie/jsdom limitation clearly (I7). I8 is a maintainability suggestion for the allowlist, and I9 is a phase-boundary observation.
