# frontend · T05 · Phase 12 — Specification Verification

Date: 2026-10-22. Consumes Phases 0–11. It is verified against:
- `tasks.md` task 5;
- `requirements.md` R21 (and R22/R23 as boundaries);
- `design.md` L3, L6, L18 and §6;
- `package.md` §8 (`parallel401sTriggerExactlyOneRenewal` → R21);
- `agents.md` ("API access"; the token rules);
- the FROZEN brief, as amended at phase 9.

## Phase 11 gaps (Kimi), resolved before verification

Kimi raised 10 gaps: 8 were accepted, 1 was already covered and is now strengthened (gap 8), and 1 was rejected
(gap 7). Only the test file changed; there was no production change.

| Gap | Decision | Change (test file line) | Break caught |
|---|---|---|---|
| 1 A network failure on the first request | **ACCEPTED** | `:383`: the transport throws `TypeError('Failed to fetch')`. The call rejects with that exact error, `renew` isn't called, and the next call on the same client succeeds (the boundary is healthy). The internal `pending` map is private and isn't asserted directly. | — |
| 2 An abort during the renewal wait | **ACCEPTED** | `:403`: two calls 401 and one is aborted while the renewal is pending. The transport stub now rejects an aborted signal, as a browser does. The aborted call rejects `AbortError`, the other is re-sent with `Bearer new` and gets 200, and `renew` ×1. | — |
| 3 The N = 1 base case | **ACCEPTED** | `:150`: one 401 gives `renew` ×1, sends `['Bearer old', 'Bearer new']`, 200, and no failure. | — |
| 4 Privacy only on the success path | **ACCEPTED** | The helper `watchForTokenLeaks(...)` (storage, URL, console) now runs in the success case (`:119`), the failure case (`:250`) and the timeout case (`:310`). | — |
| 5 A synchronous throw from `renew()` | **ACCEPTED** | `:270`: a `renew` that throws synchronously gives 2 waiters 401, `renew` ×1 and the hook ×1 with that error. | **new break `sync`:** removing the `Promise.resolve().then` wrapper fails this case |
| 6 The re-send's bypass is only argued | **ACCEPTED (the stronger form)** | `:167`: a second middleware is added with `api.use(...)`. It sees exactly the two original requests (`['Bearer old', 'Bearer old']`), while the transport sees 4. That is direct proof that no middleware sees the re-send, which closes the phase 10 "argued, not demonstrated" note. | — |
| 7 Pin `onRequest` returning `undefined` for public ops | **REJECTED** | None. `undefined` and the same request are equivalent to openapi-fetch ("unchanged"). The contract is what is sent, and `:492` asserts that every public request has no `Authorization` and causes no renewal. Pinning the return value would test an implementation detail. | — |
| 8 Two sequential successful renewals | **Already covered; strengthened** | `:212`'s second wave is exactly that scenario (`renew` ×2, all 200). It now also asserts no failure was reported. | — |
| 9 A token in the `RenewalTimeoutError` message | **ACCEPTED** | `:310` asserts the message doesn't contain the token, and the leak check covers the console. | — |
| 10 `onResponse` without a record | **ACCEPTED** (Kimi marked it optional) | `:427`: `middleware.onResponse` is called directly with an unrecorded 401. It returns the same `Response`, and there is no renewal. | **new break `entry`:** removing the `!entry` guard fails this case |

## Final suite
- `parallel401sTriggerExactlyOneRenewal`: 20 cases, **23** results.
- `lintRejectsHandWrittenBackendFetch`: 32.
- `routesResolveUnderAppPrefixOnly`: 9.
- `shellBuildsAndMounts`: 7.
- `generatedClientCompiles`: 6.
- **77/77 passed.** `typecheck` exit 0; `lint`: 0 errors, 2 rules; `build` exit 0.

**Mutation breaks:** the 12 from phase 10 were re-run against the expanded suite and all are still caught, now with more
failing cases (for example, the shared-promise removal fails 9). Phase 11 added 2 (`sync`, `entry`), both caught.
**14 of 14 caught.** Each was applied to a backup and restored from it, and `git status` was clean apart from the
test file.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| tasks.md 5: a 401 triggers exactly one renewal attempt | Yes | `sessionBoundary.ts:78` (one shared `inFlight`) and `:115` onward (the 401 path) | `:119`, `:150`, `:188` | No | None |
| R21: at most one renewal per tab | Yes | one boundary per tab, shared by every client's middleware (`sessionBoundary.ts:23` doc, `client.ts:16`) | `:188` (shared vs separate) | No | The sibling-tab half is task 6, by task split |
| R21: share that renewal among parallel 401s | Yes | joining in `renew()`; `onRequest` waits for an in-flight renewal | `:119`, `:458` | No | None |
| R21: on failure, no error loop | Yes | the hook is scheduled once per renewal (`:72`); the waiter guard (`waitedOnFailedRenewal`); a re-send 401 is final | `:250`, `:270`, `:287`, `:310`, `:337`, `:353` | No | The re-auth transition itself is task 33 (R22), by task split |
| R21 (resolved Q5): renewal is silent re-auth, with no refresh token | Yes | `SessionTokens.renew()` takes no token; a null-token 401 renews | `:477` | No | The real `renew()` is in the auth tasks (14–23) |
| L18: one renewal per tab | Yes | as R21 | `:188` | No | None |
| L3: token memory-only; never stored, logged or in a URL | Yes | the token is used only in the `Authorization` header (`:101`, re-send `:130`); no storage or console APIs are used | `:119`, `:250`, `:310` (leak check) | No | None |
| L6 / agents.md: client boundary owns 401 renewal; generated client only | Yes | the middleware on `createAuthClient()`; the re-send through `options.fetch` (`:130`); no global `fetch` | the `lint` gate; `:167` | No | None |
| R23 boundary: no automatic 429 replay | Yes | a 429 is returned unchanged | `:444` (429) | No | None |
| Contract `security: []` operations are unauthenticated | Yes | `client.ts:5`; `sessionBoundary.ts:95` | `:492`, `:512` | No | Added at phase 4 (live probe: a bad bearer turns 400 into 401) |
| package.md §8 named test | Yes | `frontend/src/api/parallel401sTriggerExactlyOneRenewal.test.ts` | itself | No | None |
| No module-level mutable state; `libs/**`, `spec/**` and `contracts/**` untouched | Yes | the diff is `sessionBoundary.ts`, `client.ts` and the test | — | No | None |

## Principal-engineer review

1. **Is the task fully complete?** Yes.
   - However many protected calls 401 together, and on however many clients of the boundary, the tab renews once and
     re-sends each call once.
   - The failure path is bounded: one report per renewal, no loop, and a timeout.
2. **Does it satisfy every acceptance criterion?** Yes, AC1–AC8, each with a direct test. AC8 is the lint, typecheck and
   build gates.
3. **Does it violate any LOCKED decision?** No.
   - **L3:** no persistence or logging of tokens.
   - **L6:** generated client only.
   - **L18:** per-tab single flight.
   - **L12:** additive.
   - **agents.md:** honoured.
4. **Remaining risks:**
   - **Wiring:** production must create exactly one boundary per tab. That is done when the auth tasks supply the real
     `SessionTokens`.
   - **Cross-tab sharing** is task 6.
   - **Repeated renewals:** after a failure, each later wave renews again until task 33's hook clears the session (P7-2).
   - **Re-send limits:** the re-send forwards no openapi-fetch init extensions (none are used today), and its transport
     error goes to the caller, not `onError`.
   - **A timed-out `renew()` keeps running** in the auth module, because there is no cancellation in the interface.

**PASS:** a 401 burst produces exactly one renewal per tab, with every waiter re-sent once or given its 401 once. The
named test covers every acceptance criterion and catches 14 of 14 mutation breaks.
