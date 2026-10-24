# frontend · T11 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## What was built

**`frontend/src/pwa/apiRequestPolicy.ts`** (new, 76 lines) — the D4 exports: `NETWORK_ONLY_PATH_PREFIXES` (the
twelve edge-map prefixes, `/api/v1` normalised to `/api`), `SHELL_DOCUMENT_PATHS`, `ASSET_PATH_PREFIX`,
`isCacheableRequest` and `isNetworkOnlyRequest`. The decision is default-deny as D1 settled: cacheable is the shell
document or a path under `/app/assets`, matched by segment; NetworkOnly is the exact negation, so an unenumerated
path can never fall through into a cache. The prefix list is published for task 12's matchers and for the drift
guard and is **not consulted by either predicate** — which is the whole point of D1.

**`frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts`** (new, 63 cases) — AC1–AC10, with
`simulateRequestRoute` standing in for Workbox's two request strategies, the spied fake cache and recorded key list
of T09/T10, and the D8 agreement cases that import T09's predicates to show the two policies compose.

## Verification

| Check | Result |
|---|---|
| `vitest run src/pwa` | **147/147** (T09 61 + T10 23 + T11 63) |
| `vitest run` (whole suite) | **295/296** |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

The one failure is the known environmental `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0`
prints no summary line), baselined in T07 both ways. Unchanged by this task and never reported as passing.

## Deviations from the plan

1. **The drift parser needed a tighter boundary than D7 specified, and failed first.** D7 said to take lines until
   the first that does not begin with `>`. The edge map sits inside a long blockquote that continues into the
   local-development paragraph, so that rule swept up extra backticked paths and the case failed on
   `toHaveLength(12)`. The parser now bounds the map explicitly: from `**Edge path map**` to its last entry, the
   `→ the SPA` arrow, asserting that boundary is found. Verified against the file: exactly the twelve prefixes plus
   `/app`, which the filter drops.
2. **`/login?error` carries a query string in the AC1 sub-path table**, which incidentally covers query-string
   independence for the request policy without a dedicated case (the predicates read `url.pathname`).
3. **The harness's cacheable branch writes on a miss** (`match`, then `put`). That models a cache-first strategy so
   the AC2 cases can observe a cache being used at all; which strategy the assets actually get — precache or runtime
   — is task 12's choice, and nothing here depends on it.
