# frontend · T10 · Phase 10 — Test Generation, Mutation and Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## The named test

`frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts` — **14 cases**:

| AC | Cases | What is pinned |
|---|---|---|
| AC1 | 1 | the full end-session URL is declined; cache never read or written |
| AC2 | 1 | across all six URL shapes the cache is never touched and the recorded key list stays empty |
| AC3 | 2 | `/app/signed-out` and `/app/signed-out?state=s1` are answered with the shell |
| AC4 | 6 | the six D2 shapes: all parameters, none, `id_token_hint` only, trailing slash, encoded redirect, foreign-origin redirect |
| AC5 | 1 | the two-step flow in order; `match` called exactly once, with the shell key; no key holds `?` or the token |
| AC6 | 1 | three repeats decline identically (statelessness, **not** cross-tab isolation — see phase 9) |
| AC7 | 2 | `/connect/logout` still in the denylist and in `agents.md`; `signed-out` still public under `/app` |

`vitest run src/pwa`: **75/75** (T09's 61 + T10's 14). Whole suite **223/224**, the one failure being the known
environmental `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` prints no summary line;
baselined in T07). `tsc --noEmit` clean, `oxlint` reports no violations.

## Mutation pass

Applied to real files, suite run, reverted with `git checkout --`; tree verified clean afterwards. The test file was
committed first (`d76693e0`), because the harness reverts tracked files — the lesson from T09.

| # | Mutant | Target | Result |
|---|---|---|---|
| M1 | remove `/connect/logout` from the list | source | **CAUGHT** — 12 cases, 8 of them T10's |
| M2 | decide on `url.href` instead of `url.pathname` | source | **CAUGHT** — 38 cases, 7 T10's |
| M3 | decide on `url.search` instead of `url.pathname` | source | **CAUGHT** — 38 cases, 7 T10's |
| M4 | the shell answers every navigation | source | **CAUGHT** — 30 cases, 1 T10's (AC1) |
| M5 | the shell matches `/app` exactly, not its sub-paths | source | **CAUGHT** — 10 cases, 3 T10's (AC3) |
| M6 | harness reads the cache before the denylist check | test | **CAUGHT** — 10 cases, all T10's |
| M7 | harness keys the cache by full URL | test | **CAUGHT** — exactly 1 — AC5 |

**7 mutants, 7 caught, 0 survived.**

M1 is the one phase 9 promised to confirm rather than assume: the self-review noted that the harness returns
`undefined` both for a denylisted navigation and for an unrecognised path, so AC1's and AC4's harness results alone
could not catch a dropped denylist entry. It is caught 8 times in T10 — by the explicit
`isDenylistedNavigation(...) === true` assertions and by AC7's membership check — so the mitigation is real and not
assumed.

M7 is caught by exactly one case, AC5's key assertion, which is the narrower form of Kimi's phase-3 finding 7 that
the gate accepted (D7). Without that assertion a worker could key its cache by full URL and nothing would notice.

M2 and M3 are the mutants that matter most for this task specifically: deciding on `url.href` or `url.search` is
exactly how a plausible refactor would start letting the query string — **which carries the ID token** — influence
the cache decision. Each is caught by 38 cases across both files.

## Coverage

`@vitest/coverage-v8` is not a workspace dependency (adding one is out of scope; the lockfile is under a
supply-chain policy check), so coverage is enumerated, as in T07–T09. T10 adds no production code, so there is no
new source to cover: every branch of `navigationDenylist.ts` was already enumerated in T09 phase 10, and this file
re-exercises `isDenylistedNavigation`'s match branch, `isShellNavigation`'s denylist-wins and pattern-match
branches, through 14 cases over seven URL shapes.

## Verified against the running platform

Worth recording because this task is about a real flow, not only a unit: on 2026-10-24 the full local stack was
rebuilt and `infra/local/bin/smoke` printed **SMOKE PASSED**, its last step being *"sign out through end-session"* —
the live `/connect/logout` round trip whose worker behaviour this task pins. Discovery through the edge confirms the
endpoints the test file names: issuer `http://localhost:5173`, authorize `/oauth2/authorize`, end-session
`/connect/logout`.
