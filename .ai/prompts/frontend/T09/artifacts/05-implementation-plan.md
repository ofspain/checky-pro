# frontend · T09 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

### Step 1 — `frontend/src/pwa/navigationDenylist.ts`
Two exported constants, one derived `RegExp[]`, two predicates, per D3.

- `DENYLISTED_NAVIGATION_PATHS` — the eleven paths of `agents.md:88-89`, verbatim and in order, under a comment
  citing R57 and the file (AC8). `as const` so the array is readonly and the order is part of the type.
- `SHELL_NAVIGATION_PREFIX = '/app'` — the one prefix the shell may answer (L14). Not `/app/`, because D4 treats
  `/app` and `/app/` alike.
- A module-private `segmentPrefixPattern(path)`: strip one trailing `/`, escape the regex metacharacters that the
  list actually contains (`.` in `/.well-known/`, `-` in `/api-keys`, via a general escape), anchor with `^` and
  close with `(?:/|$)` (D4).
- `NAVIGATION_DENYLIST` — `DENYLISTED_NAVIGATION_PATHS.map(segmentPrefixPattern)`, the `RegExp[]` that task 12 hands
  to `navigateFallbackDenylist` / `new NavigationRoute(handler, { denylist })`.
- `isDenylistedNavigation(url, mode)` — `mode === 'navigate'` (D6) and some pattern matches `url.pathname`.
- `isShellNavigation(url, mode)` — `mode === 'navigate'`, **not** denylisted, and `url.pathname` matches the shell
  prefix pattern. The denylist is checked first so the two predicates can never both be true.

No `fetch`, `caches`, `self`, `import.meta.env` or absolute URL (AC9); nothing imported from T01–T08.

### Step 2 — `frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`
- Scaffolding as T08: `/// <reference types="node" />`, `readFile` from `node:fs/promises`, `join`/`dirname` from
  `node:path`, `repoRoot` from `expect.getState().testPath` (four `dirname`s — `src/pwa/x.test.ts` is the same depth
  as `src/api/x.test.tsx`), and the drift guard asserting the file was found before parsing it.
- `simulateNavigationFallback(path, mode, cache)` (D5): the Workbox `navigateFallback` semantics in ten lines —
  denylisted → return `undefined` without touching `cache`; shell → `await cache.match(SHELL)`; otherwise →
  `undefined`. `cache` is `{ match: vi.fn(), put: vi.fn() }`. A comment records that task 12 wires the real route
  against the same exports.
- Groups AC1–AC9 as the frozen brief lists them, with `it.each` over the eleven paths for AC1 and over the
  boundary table for AC4.

### Step 3 — Verify
`pnpm --filter frontend exec vitest run` (whole suite, for AC10), `tsc --noEmit`, `oxlint`. The known
environmental failure in `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` prints no summary
line) is baselined, not caused here, and is reported as environmental.

### Step 4 — Phase 10 mutation pass
The nine AC-M mutants, each applied to real source, suite run, reverted with `git checkout --`.

### Risks
- **jsdom `Request.mode`** is not reliably settable, so the predicates take the mode as a plain string argument
  rather than reading it off a `Request`. That is also what Workbox's `matchCallback` receives (`{ request }` →
  `request.mode`), so the production call site stays one property access.
- **Over-matching** is the real hazard; AC4's negative rows are the guard.
