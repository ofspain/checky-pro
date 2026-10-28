# frontend · T22 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief against the spec, the standing rules, and the package's existing tests. Findings only — accepted items are folded into the frozen brief by the human gate.

---

## F1 · The authorization `code` in the URL is left as "out of scope" without a recorded handoff

**Severity.** Medium.

**Evidence.**
- The task statement (task 22) says "No token is written to ... a URL" (`spec/frontend/tasks.md`).
- T22-D3 says the authorization `code` in the callback URL is out of scope because it is "a one-time credential rather than a token" and SAS put it there.
- `agents.md:79` forbids the SPA *placing* a token in a URL, but is silent on removing one placed by the authorization server.
- Task 24 owns the post-login return target and navigation, but no task is visibly assigned to remove the callback query before the visitor lands on the next screen.

**Recommended brief amendment.** Either:
- Record T22-D3 as a handoff to task 24 with an explicit assertion that task 24 must remove the callback query (`code`, `state`, `error`, `error_description`) from the URL before the post-login screen renders, and add a cross-reference in task 24's brief; or
- Narrow T22's scope statement from "No token is written to ... a URL" to "The SPA places no token in a URL", matching `agents.md:79` exactly, and remove the URL from the runtime sweep's claimed coverage.

A sweep task that silently excludes the most visible credential in the URL risks a future reader assuming the URL is clean.

---

## F2 · T22-D1 understates the limitation for dependency-introduced leaks

**Severity.** Medium.

**Evidence.**
- T22-D1 says the sweep does not build the bundle because `bundleContainsNoSecretEnvKeys` already owns the build-time instrument and because "a second full build doubles that cost for a class of leak ... that the source scan and the runtime sweep together cover."
- The source scan (AC9) reads `frontend/src/**` only.
- A runtime dependency could write a token to `localStorage` or call `console.log` inside its minified distribution code; neither the source scan over `frontend/src` nor the runtime sweep (which exercises only the tested flow) would necessarily catch it.
- The runtime sweep catches leaks during the flow it drives, but not leaks triggered by import side effects, unexercised error paths, or third-party telemetry.

**Recommended brief amendment.** Sharpen T22-D1's limitation statement:
- The source scan covers the repository's own production code under `frontend/src`.
- The runtime sweep covers the tested sign-in/callback flow.
- Dependency-introduced leaks at runtime are not covered by either instrument; they remain the responsibility of `bundleContainsNoSecretEnvKeys` and dependency review, not this task.

This prevents the frozen brief from implying coverage it does not provide.

---

## F3 · `history.state` and `window.name` are not named in the sweep surfaces

**Severity.** Low.

**Evidence.**
- AC5 checks `location.href`, `search`, `hash`, `document.title` and the rendered DOM for token values.
- `history.state` is a persistent, same-origin storage surface that survives navigation. `window.name` persists across navigations and is readable by any page in the tab.
- The screen uses `navigate(..., { replace: true })` with no state today, but a future change could pass state without updating the URL.
- Neither surface is mentioned in the brief, `agents.md`, or the existing test plans.

**Recommended brief amendment.** Add T22-D8: explicitly state whether `history.state` and `window.name` are in scope. If in scope, add them to the runtime sweep and the source scan. If out of scope, record the exclusion so the sweep's coverage is explicit.

---

## F4 · `sendBeacon` and analytics-style transports are not covered by the source scan

**Severity.** Low.

**Evidence.**
- `agents.md:82` forbids sending tokens to analytics.
- AC9's source scan forbids `localStorage`, `indexedDB`, `document.cookie`, and `console.*`.
- `navigator.sendBeacon`, `fetch` with an analytics URL, and `Image.src` are all ways to exfiltrate a token without using the forbidden storage APIs. The L6 lint rule bans hand-written backend `fetch`, but analytics endpoints are often third-party and might bypass that rule's intent.
- The runtime sweep checks console channels but not network exfiltration.

**Recommended brief amendment.** Add `navigator.sendBeacon`, `new Image`, and `new Beacon` (or a generic `/\bsendBeacon\b|new\s+Image\b|new\s+Beacon\b/`) to the package-wide source scan. Include a synthetic violation proving the scan catches one. This closes the analytics-shaped hole without adding a runtime dependency.

---

## F5 · AC9's "no `setItem` outside `authorizeRequest.ts`" should also cover `removeItem` and `clear`

**Severity.** Low.

**Evidence.**
- AC7 asserts no `removeItem` and no `clear` in a successful flow, but only in the runtime sweep.
- AC9's source scan forbids `setItem` outside `authorizeRequest.ts` but does not mention `removeItem` or `clear`.
- Task 23 owns deleting the three L16 items at callback. A module outside task 23 calling `removeItem` or `clear` would either pre-empt task 23 or could be used to hide a leak.

**Recommended brief amendment.** Extend AC9 to forbid `removeItem` and `clear` outside the file/task that owns deletion (`auth/authorizeRequest.ts` is the only writer; task 23 will be the only deleter). This is a structural companion to the runtime assertion in AC7.

---

## F6 · The definition of "token" does not explicitly include the `Bearer` prefix

**Severity.** Low.

**Evidence.**
- T22-D2 defines "a token" as "the access token, the ID token — whole, each of its three segments, and any decoded claim value — and any `refresh_token` should one ever appear."
- The boundary forms `Authorization: Bearer <token>` (`sessionBoundary.ts:89`). A leak that wrote the header value to storage would contain the word `Bearer` plus the token.
- Searching only for token values and segments could miss a leaked `Bearer <token>` string if the token value itself is not separately present.

**Recommended brief amendment.** Add the literal `Bearer ` (with a trailing space) to the list of token-related strings the sweep searches for, or clarify that the token value search implicitly covers any value prefixed with `Bearer `. A synthetic violation that writes `Bearer ACCESS-TOKEN-X` to storage should be caught.

---

## Summary

F1 and F2 are the most important: they prevent the brief from implying coverage or responsibility that it does not actually take. F3–F6 are smaller coverage holes in the sweep surfaces. None requires a production code change, but each should be frozen as a decision before Phase 4.
