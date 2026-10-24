# frontend · T09 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T09 — Denylisted navigations bypass the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

**`frontend/src/pwa/navigationDenylist.ts`** (new, 69 lines) — the D3 exports:
`DENYLISTED_NAVIGATION_PATHS` (the eleven paths of `agents.md:88-89`, verbatim, in order, `as const`),
`SHELL_NAVIGATION_PREFIX` (`'/app'`), `NAVIGATION_DENYLIST` (derived `RegExp[]` for Workbox's
`navigateFallbackDenylist` / `NavigationRoute`), and the two pure predicates `isDenylistedNavigation` and
`isShellNavigation`. Matching is the D4 segment rule: one trailing `/` stripped, metacharacters escaped,
`^…(?:/|$)`. Both predicates return `false` for a non-navigation (D6), and the denylist is evaluated before the
shell so the two can never both be true.

**`frontend/src/pwa/sasNavigationBypassesServiceWorker.test.ts`** (new, 55 cases) — one group per AC, with the
D5 `simulateNavigationFallback` harness over a spied fake cache, `it.each` tables for AC1 and AC4, and the D7
drift guard parsing the paths out of `spec/frontend/agents.md`.

No existing file was modified. No package was added.

## Verification

| Check | Result |
|---|---|
| `vitest run src/pwa` | **55/55 pass** |
| `vitest run` (whole frontend suite) | **203/204 pass**, 1 failure: `lintRejectsHandWrittenBackendFetch` AC4 |
| `tsc --noEmit` | clean |
| `oxlint` | clean (no violations reported) |

The one failure is the **known environmental** one, unchanged by this task: this host's `oxlint@1.87.0` prints no
`Found 0 warnings and 0 errors` summary line, so that test's AC4 string assertion cannot pass here. It was baselined
both ways in T07 (it fails identically with all task changes stashed, and against a deliberately introduced real
`fetch` violation). It is not reported as passing anywhere in this task's artifacts.

## Deviations from the plan

1. **AC7's pattern assertion was rewritten from source inspection to behaviour.** The planned check
   (`pattern.source.endsWith('(?:/|$)')`) failed: V8 renders a `RegExp`'s `source` with `/` escaped, so the real
   source is `^\/api(?:\/|$)`. Asserting the source string would have pinned an engine detail; the case now asserts
   each pattern's behaviour instead — it matches its own path and `path + '/sub'`, and rejects `path + 'x'` and
   `'/app' + path`. That is a stronger guard and is what AC4's mutants actually have to break.
2. **AC9 strips comments before the banned-global scan.** The module's own prose legitimately contains the words
   "cache" and "request", so the scan runs over the source with block comments and whole-line comments removed.
   Trailing comments are not stripped (see Phase 7 finding 5).
3. **`mode` is typed `string`, not `RequestMode`.** Deliberate: jsdom does not let a test set `Request.mode`
   reliably, and Workbox's `matchCallback` hands over `request.mode`, which widens to `string` at the call site
   anyway. An unrecognised mode falls to `false` — the safe direction.
