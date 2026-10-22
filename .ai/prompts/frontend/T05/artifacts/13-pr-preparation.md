# frontend · T05 · Phase 13 — PR / Commit Preparation

Gate: the Phase 12 verdict is **PASS**.

Branch note: work lands on `spec/service-specs-and-ai-framework` and is pushed after each phase. For `main`, merge or
squash the branch's final tree. The repository's standing attribution (`Claude Opus 5.5`) is used, as in T01–T04.

## Commit title
`frontend: session boundary renews once per tab on 401 (T05)`

## Commit message
```
frontend: session boundary renews once per tab on 401 (T05)

The client boundary now owns 401 handling (R21, L18), so screens never do:

- createSessionBoundary(tokens, onRenewalFailed): one per tab. Its single
  in-flight renewal is shared by every client's middleware. Each renewal
  is bounded by RENEWAL_TIMEOUT_MS (30 s). On failure the hook runs once,
  scheduled so a throwing hook can't affect waiters.
- boundary.middleware(publicOperations): attaches the in-memory bearer
  (L3), holds new requests while a renewal is in flight, and on a 401
  joins or starts the one renewal, then re-sends the request once with the
  new token through the client's own transport (L6). A 401 on the re-send
  is final, and a request that waited on a failed renewal doesn't start
  another.
- createAuthApi(boundary) with AUTH_PUBLIC_OPERATIONS, the 5 security: []
  operations of auth.yaml, sent with no bearer and never renewed. The
  live stack showed a stale bearer turning their 400 into a 401.
- SessionTokens is the seam to the auth module; renewal is silent
  re-auth on the SAS cookie (needs no prior token; the reload case).
- parallel401sTriggerExactlyOneRenewal (20 cases, 23 results).

Cross-tab sharing (task 6), 429 handling (tasks 7-8) and the re-auth
screen (task 33) build on this boundary.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed
- `frontend/src/api/sessionBoundary.ts` (new).
- `frontend/src/api/client.ts` (new).
- `frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts` (new).

There are no dependency, `libs/`, `spec/`, `contracts/`, `services/`, `infra/` or CI changes.

## Summary
The fifth frontend task adds the piece every signed-in screen will rely on. When the session's access token expires and
several requests fail at once, the app quietly renews the session a single time, then retries each request once. If
renewal can't succeed, every request gets its original "unauthorised" answer, and the failure is reported once for
the later re-auth screen. Public pages such as registration and password reset never send a token, so an expired one
can't break them. Nothing on screen changes yet.

## Testing performed
In the `infra/local` `frontend` container:
- `pnpm -C frontend test`: **77/77** (23 new).
- `typecheck`, `lint` (0 errors, 2 rules) and `build`: clean.

Live-stack probes through the edge (`localhost:5173`):
- every backend family returns 401 `WWW-Authenticate: Bearer`;
- the 5 public auth operations return 400 with no bearer and 401 with a bad bearer;
- an API 401 does not replace an existing SAS `JSESSIONID`.

Mutation breaks: **14, each caught**. Each was applied to a backup and restored from it.
- the shared promise removed;
- the in-flight slot never cleared;
- the boundary made per client;
- the hook called per waiter;
- renewal on 403;
- a bearer sent to public operations;
- the timeout removed;
- the waited-on-failure guard removed;
- the token comparison removed;
- no wait in `onRequest`;
- the re-send keeping the old token;
- a public operation dropped;
- the synchronous-throw wrapper removed;
- the missing-record guard removed.

## Specification references
- **Task:** `spec/frontend/tasks.md` task 5, "Single-flight 401".
- **Requirement:** R21, plus R22 and R23 as boundaries.
- **Locked decisions:** L18 (single-flight renewal), L3 (tokens in memory, silent re-auth), L6 (generated client only).
- **Standing rule:** `agents.md`, "API access" (the client boundary owns 401 renewal).
- **Named test:** `package.md` §8, `parallel401sTriggerExactlyOneRenewal` → R21.
