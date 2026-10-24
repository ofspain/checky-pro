# frontend · T10 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

### Step 1 — the one file
`frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`, following T09's conventions:
`repoFile()` over `expect.getState().testPath` for the one on-disk read, a spied fake cache
(`{ match: vi.fn(), put: vi.fn() }`) rebuilt in `beforeEach`, and `it.each` tables where the cases are a list.

Contents in order:
1. A header comment per D4/D5: this harness is a copy of T09's, both stand in for Workbox's navigation fallback,
   task 12 replaces them, and the second call of the two-step case stands in for the browser following SAS's 302.
2. `END_SESSION_PATH = '/connect/logout'`, `SIGNED_OUT_PATH = '/app/signed-out'`, a synthetic
   `ID_TOKEN` (three dot-separated base64url-looking segments, obviously fake, prefixed `fake.`), and the six D2
   variants as a named table.
3. `simulateNavigationFallback` — the D4 copy, plus a `keys` array recording every argument the fake cache is called
   with, so AC2 and AC5's "no key contains `?`, `id_token_hint` or the token" is asserted over the whole file rather
   than per case.
4. Groups AC1–AC7.

### Step 2 — the assertions that carry the task
- **AC2** is the task-specific one: for every variant, `cache.match` and `cache.put` are never called at all, and
  the recorded key list stays empty. A token cannot reach a cache that was never touched, and asserting both the
  call count and the key contents means a mutant that starts touching the cache fails on the stronger assertion.
- **AC5** runs the two navigations in sequence against one cache and asserts `match` was called exactly once, with
  the shell constant, after the end-session navigation returned nothing.
- **AC7** imports `DENYLISTED_NAVIGATION_PATHS` from T09's module and `APP_BASENAME`/`PUBLIC_ROUTE_PATHS` from
  `../app/routes` (D3), and reads `agents.md` for the `/connect/logout` half.

### Step 3 — verify
`vitest run src/pwa` (both files), then the whole suite for AC8, `tsc --noEmit`, `oxlint`. The known environmental
`lintRejectsHandWrittenBackendFetch` AC4 failure is baselined and reported as environmental, never as passing.

### Step 4 — commit before the mutation pass
Learned in T09: the harness reverts with `git checkout -- <files>`, which discards uncommitted test changes. The six
AC-M mutants run only after the test file is committed.

### Risks
- **A vacuous pass.** Every AC1/AC4 case asserts "declined", which is also what an unknown path returns. AC3 and
  AC5 are the guard: if the shell matcher were broken, `/app/signed-out` would fail, and AC5 pins that the two
  branches differ within one sequence.
- **Importing `../app/routes`** pulls `react-router` into this test file. It is already a dependency and the import
  is side-effect-free (D3), but if it proves slow or noisy under jsdom the fallback is a typed import of the
  constant only, which is what the plan already does.
