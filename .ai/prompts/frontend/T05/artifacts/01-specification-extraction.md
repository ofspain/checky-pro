# frontend · T05 · Phase 1 — Specification Extraction

Consumes `00-repository-understanding.md`. Date: 2026-10-22.

## Business Rules

- **R21 (the share in scope):** "WHEN an API call returns 401, THEN the client boundary SHALL run at most one renewal per
  tab, SHALL share that renewal among parallel 401s and sibling tabs, and on failure SHALL route to clean re-auth without
  an error loop." The renewal mechanism is silent re-authentication via `/oauth2/authorize` on the SAS session cookie,
  not a refresh-token grant.
  - **T05's share is the per-tab half:** at most one renewal per tab, shared by every parallel 401 in that tab.
  - **The sibling-tab half is task 6** (`siblingTabsShareOneRenewal`, R21, L18).
  - **Failure:** T05 makes sure a failed renewal ends in a single reported failure, with no retry or renewal loop.
    The re-auth transition and the "no half-session" screen state belong to task 33 (R22).
- **Context only, not implemented here:**
  - **R22** (clean re-auth on failure; sign-out propagates) is task 33 and the sign-out tasks.
  - **R23** (429: no automatic replay of non-idempotent POSTs) is tasks 7–8. It bounds the T05 design: T05 must not
    introduce any automatic 429 retry.

## Locked Decisions

- **L18 [ALL]:** "At most one renewal runs per tab. Tabs share renewal and sign-out through BroadcastChannel or a Web
  Locks leader (R21, R22)." T05 implements the per-tab clause. The cross-tab clause is task 6.
- **L3 [ALL]:** the access token lives in memory only. Renewal is silent re-authentication. No refresh token exists, so
  there is no refresh-token grant. T05 must not store or log a token, or put one in a URL.
- **L6 [ALL] / `agents.md` "API access":** every backend call goes through `libs/ts/api-client`. "The client boundary
  owns single-flight 401 renewal … Screens do not reimplement them." The re-sent request must also go through the
  client's transport. T04's lint forbids global `fetch` in `frontend/src`.
- **L9 [ALL]:** single origin and relative paths. They are unchanged by T05.
- **L10 [ALL]:** no env or secret in the bundle.
- **L12 [ALL]:** extend, don't rewrite. `libs/ts/api-client` (generated) is consumed, not edited.
- **L13 [ALL]:** TypeScript and Vitest.

## Files involved

- **Read / consume:**
  - `libs/ts/api-client/src/index.ts` (`createAuthClient`, the `paths` types);
  - `openapi-fetch@0.17.0` (middleware API);
  - `frontend/.oxlintrc.json` (the new code must lint clean);
  - `frontend/src/api/generatedClientCompiles.test.ts` (the browser-`Request` shim pattern).
- **New, expected by `design.md` §6:**
  - the client-boundary module under `frontend/src/api/` ("generated client wrapper, 401/429");
  - the named test `frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts`.
- **Not in T05:**
  - `frontend/src/auth/` (the OIDC client, the token holder and the real silent re-auth; tasks 14–23, 33);
  - the cross-tab channel (task 6);
  - 429 handling (tasks 7–8).

## Dependencies

- **`@themistra/api-client`:** the `createAuthClient()` client and its `use()` middleware.
- **From the future auth module,** consumed through an interface, because neither exists yet:
  - the current in-memory access token;
  - a renewal operation (silent re-auth) that yields a fresh token or fails.
- **Contract:** `auth.yaml` declares `bearerAuth` (`components.securitySchemes`). The live stack returns `401` with
  `WWW-Authenticate: Bearer` (Phase 0).
- **No config keys and no env.**
- **No new runtime dependency is implied.** Single-flight needs only a shared in-flight promise.

## Acceptance Criteria

| # | Criterion | Source |
|---|---|---|
| AC1 | Any number of API calls that return 401 at about the same time (in flight together) in one tab trigger **exactly one** renewal attempt. | R21, L18, task 5 |
| AC2 | Every one of those calls waits on that same renewal, and none starts its own. | R21 ("share that renewal among parallel 401s") |
| AC3 | A 401 that arrives after the shared renewal has settled starts a new, single renewal. A settled renewal is not reused forever, but each wave still triggers exactly one. | R21 ("at most one renewal" per 401 episode); inferred boundary |
| AC4 | When renewal fails, every waiting call gets the failure. No call triggers a second renewal for that failure, and there is no retry loop. The failure is surfaced through one hook that task 33 (R22) will use to route to clean re-auth. | R21 ("without an error loop"), R22 boundary |
| AC5 | A request that succeeds, or fails with any status other than 401, never triggers renewal. | R21 (trigger is 401) |
| AC6 | Every request goes out with the current in-memory access token as `Authorization: Bearer`. The token is never written to storage or a URL, and never logged. | L3, `agents.md` |
| AC7 | Renewal and any re-send go through the generated client's transport; no hand-written `fetch` (T04 lint stays green). | L6 |

## Tests required

- **Named:** `parallel401sTriggerExactlyOneRenewal` (`package.md` §8, mapped to R21).
- **Implied boundaries:**
  - N parallel 401s lead to one renewal, and all of them observe its outcome;
  - a later, second wave leads to a second, single renewal;
  - a failed renewal goes to every waiter, with no second renewal and no loop;
  - a 200 or a non-401 error leads to no renewal;
  - a request made after renewal carries the new token;
  - no token appears in storage, the URL or the console;
  - no global `fetch` is referenced (covered by the T04 lint over `src`).

## Open Questions

None are blockers: `package.md` §11 has no item on 401 handling, and Q5 is resolved. Left as Phase 2 defaults:

1. **Re-send after a successful renewal.** R21 doesn't say whether the 401'd request is retried. Proposed default: re-send
   it exactly once with the new token. A 401 means the server rejected the request at authentication, before handling
   it, so replaying it is not the duplicate-side-effect case that R23 guards against for 429. A second 401 on the
   re-send is returned to the caller with no further renewal. That gives "exactly one renewal" and no loop.
2. **The renewal seam.** Proposed default: T05 defines a small `TokenSource`-style interface (current token, plus
   `renew()`), and the client boundary takes it as a parameter. The auth tasks supply the real implementation. T05 ships
   no stub that pretends to renew.
3. **Which clients are covered.** Proposed default: a middleware applicable to any `openapi-fetch` client, used for
   `createAuthClient()` now. The payments and notifications clients reuse it when they are generated.
4. **The failure hook.** Proposed default: renewal failure is delivered once per failed renewal to a callback supplied by
   the caller. The waiters receive the original 401 response. Task 33 wires that callback to clean re-auth.
