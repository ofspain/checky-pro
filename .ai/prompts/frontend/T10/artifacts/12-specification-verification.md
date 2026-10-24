# frontend · T10 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact, the implementation, `spec/frontend/*` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 gaps (Kimi), resolved before verification

| Gap | Decision | Change |
|---|---|---|
| 1 No fragment on the end-session URL | **ACCEPTED** | A seventh shape in the shared table — `?id_token_hint=…#section` — so it drives AC2 and AC4 both. A browser never sends the fragment, but a decision that read `url.hash` would classify differently. |
| 2 No fragment on the post-logout target | **ACCEPTED** | `:135` adds `/app/signed-out#reset` to the AC3 row set. |
| 3 No repeated shell answer | **ACCEPTED** | `:185`, the mirror of AC6 for the other half of the flow: two navigations to `/app/signed-out?state=s1` both return the shell, `match` is called twice with the shell key both times, `put` never. |
| 4 The shell key is not checked for `?`/`state` | **REJECTED — already covered, and more strictly** | AC5 asserts `expect(keys).toEqual([SHELL])`, **exact array equality** over every key the cache was asked about. That is strictly stronger than "contains neither `?` nor `state`": any other key, with or without a query, fails. The substring checks that follow it are belt-and-braces, not the guard. |
| 5 No negative for a path that only looks like the endpoint | **ACCEPTED** | `:198`: `/connect/logoutx`, `/connect/logout-extra` and `/connect/logoutsomething` are neither denylisted nor shell. Kimi's reasoning is the right one — a regression that loosened the segment rule *only* for this path would break R19 while every other denylisted path still passed. |
| 6 No guard against the token in a failure message | **REJECTED** | Kimi marks it optional and meta: it would test the source for a realistic JWT. The `fake.` prefix and the absent signature satisfy the constraint by inspection. |
| 7 No non-navigation case for `/connect/logout` | **ACCEPTED in narrow form** | T09's AC6 already loops four non-navigate modes over all eleven bare paths, `/connect/logout` included (`sasNavigationBypassesServiceWorker.test.ts:199`), so the bare path was covered. `:210` adds what was not: the **real end-session URL, token in the query**, in three non-navigate modes. |
| 8 No real 302 redirect chain | **CARRIED FORWARD** | jsdom has no `FetchEvent`; recorded in the file header and in phase 13 as a task 12 / Playwright requirement. |

## Mutation runs (10, against the final 23-case file)

| Mutant | Caught by (total / T10's) |
|---|---|
| M1 remove `/connect/logout` from the list | 13 / 9 |
| M2 decide on `url.href` | 39 / 8 |
| M3 decide on `url.search` | 39 / 8 |
| M4 the shell answers every navigation | 33 / 4 |
| M5 the shell matches `/app` exactly | 12 / 5 |
| M6 harness reads the cache before the denylist check | 14 / 14 |
| M7 harness keys the cache by full URL | 2 / 2 |
| M8 `startsWith` instead of a segment boundary | 7 / **3** |
| M9 drop the navigate-mode check | 7 / **3** |
| M10 harness answers the shell only once (stateful) | 4 / **4** |

**10 applied, 10 caught, 0 survived.** M8, M9 and M10 were written *for* the three accepted gaps, so that the new
rows have to earn their place rather than pad the count: M8 is caught by the `/connect/logoutx` rows, M9 by the new
non-navigate case, M10 by the repeated-shell case. Each was applied to the real file, run, reverted with
`git checkout --`, and the tree was verified clean; the test file was committed first (`4c2b0fff`), per T09's lesson
about the harness reverting tracked files.

## Traceability matrix

| Requirement / decision | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R57** `/connect/logout` is never answered from cache | Yes, by T09's policy | `navigationDenylist.ts:17-29`, `:55-58` | `:107`, `:119`, `:145` (×7 shapes) | No | No |
| **R57** …nor with the shell | Yes | `navigationDenylist.ts:65-69` — denylist before shell | `:107` (asserts `isShellNavigation` false) | No | No |
| **R19** the end-session navigation is served by SAS, not the SPA shell | Yes | the policy declines it; the live flow passes `bin/smoke`'s sign-out step | `:107`, `:145`, `:152` | No | No |
| **R19** the post-logout target renders | Yes | `/app/signed-out` is a public route, answered by the shell | `:135` (×3), `:185`, `:232` | No | No |
| **L3 / design.md §5** the ID token never leaves memory | Yes, by construction | nothing is cached; the module holds no cache reference | `:119` (cache untouched across all seven shapes, key list empty) | No | No |
| **R22 / L18** sign-out propagates to every tab | **Partly, and only in the narrow sense phase 9 allows** | the decision is stateless | `:173` (three repeats), `:185` | **Cross-tab cache isolation is NOT proven here** — tasks 11/12 and T06 own it | **Declared** |
| **R57 / task 11 boundary** the policy is navigation-only | Yes | `navigationDenylist.ts:56`, `:66` | `:210` (real URL, three modes) | No | No |
| **R57 verbatim** the path is `agents.md`'s | Yes | `DENYLISTED_NAVIGATION_PATHS` | `:221` (denylist + `agents.md` block), `:198` (segment rule) | No | No |
| **L14** the SPA owns `/app` | Yes | `routes.tsx:4`, `:16` | `:232` (typed import, D3) | No | No |
| **L12** phase additivity | Yes | one new file; no production change at all | suite 232/233 | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 the full end-session navigation is declined | **PASS** | `:107` |
| AC2 the ID token never reaches a cache | **PASS** | `:119` |
| AC3 the post-logout target is answered by the shell | **PASS** | `:135`, `:185` |
| AC4 shape invariance (now seven shapes) + the negative rows | **PASS** | `:145`, `:198` |
| AC5 the two-step flow, shell key only | **PASS** | `:152` |
| AC6 repetition; navigation-only | **PASS** | `:173`, `:210` |
| AC7 drift against `agents.md` and `routes.tsx` | **PASS** | `:221`, `:232` |
| AC8 suite green, no T01–T09 edit | **PASS with one environmental failure** | 232/233 |

## The one failing test, stated plainly

`src/api/lintRejectsHandWrittenBackendFetch.test.ts` AC4 expects `oxlint` to print
`Found 0 warnings and 0 errors`. This host's `oxlint@1.87.0` prints no summary line at all — verified in T07 with
changes stashed and against a deliberately introduced real `fetch` violation. Environmental, predates this task,
unaffected by it, and **not counted as passing anywhere in these artifacts**.

## Verified against the running platform

The full local stack was rebuilt on 2026-10-24 and `infra/local/bin/smoke` printed **SMOKE PASSED**, its final step
being *"sign out through end-session"* — the live `/connect/logout` round trip this task's policy governs. Discovery
through the edge returns the endpoints the test file names: issuer `http://localhost:5173`, authorize
`/oauth2/authorize`, end-session `/connect/logout`.

## Verdict

**PASS.** R57's `/connect/logout` clause and R19's closing sentence are pinned by 23 cases over seven end-session
URL shapes, with 10 of 10 mutants caught and **no production change** — the declared shape of the task (D1).
Two declared limits: R22's cross-tab guarantee is credited only as statelessness, and the real 302 redirect chain
cannot be exercised in jsdom. Both are carried to task 12.
