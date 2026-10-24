# frontend · T11 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact, the implementation, `spec/frontend/*` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 gaps (Kimi), resolved before verification

| Gap | Decision | Change |
|---|---|---|
| 1 No drift guard against `agents.md:90` | **ACCEPTED** (my own phase 7 raised it too) | `:304` guards both sentences — "API routes use NetworkOnly." and the sensitive-bodies rule. Both wrap across lines in the file, so whitespace is collapsed before matching: a rewrap must not fail the guard, a softened rule must. Proven by M13b/M13c below. |
| 2 Trailing-slash branch unexercised | **PREMISE CORRECTED, row added anyway** | `segmentPrefixPattern`'s `endsWith('/')` branch depends on the **prefix constant** (`ASSET_PATH_PREFIX = '/app/assets'`), not on the request path, so the suggested `/app/assets/` row does not reach it. The row is added at `:115` as a genuine boundary case, and the coverage note below stays accurate: that branch is still unexercised in this module. |
| 3 + 8 No cache-hit case for the shell | **ACCEPTED, merged** | `:140`: `/app/index.html` and `/app/` both served from cache on a hit, with no `put`. |
| 4 No assertion that the write uses the read's key | **ACCEPTED — a real hole** | `:115` now asserts `put` is called with the same pathname `match` received, and that the recorded keys are exactly `[path, path]`. A worker that keyed the write differently would miss forever and re-fetch every time, and would have passed before. **Mutant M12 was written for it and is caught by 10 cases.** |
| 5 No query/hash on cacheable paths | **ACCEPTED** | `:130`: `/app/index.html?state=s1`, `/app/assets/main-abc123.js#chunk` and `?v=2` are cacheable and keyed by pathname alone. **M11 (deciding on `url.href`) was written for it: caught by 15 cases.** |
| 6 No per-prefix near-miss rows | **REJECTED, with the reason** | Under default-deny the predicates **never consult** `NETWORK_ONLY_PATH_PREFIXES` — `isCacheableRequest` only knows the shell and `/app/assets`. So a boundary error in that list cannot change a single decision in this module: `/accountsxyz` and `/accounts/me` are both NetworkOnly either way. Those boundaries first matter in **task 12**, which builds matchers from the list. Adding twelve rows would grow the count without adding signal. |
| 7 No non-GET on a cacheable path | **ACCEPTED** | `:151`: `POST /app/assets/main-abc123.js` is still cacheable, closing the hole where a harness could consult the method for cacheable paths only. |

## Mutation runs (13 on code and harness, 3 on the guarded sources)

| # | Mutant | Result | Failing ACs |
|---|---|---|---|
| M1 | drop `/notifications` from the list | **CAUGHT** — 3 | AC1, AC5, AC9 |
| M2 | invert the default: unknown paths cacheable | **CAUGHT** — 11 | AC5, AC6, AC7, AC10 |
| M3 | decide by the prefix list, not the cacheable set | **CAUGHT** — 10 | AC5, AC6, AC7 |
| M4 | loosen the asset match to `startsWith` | **CAUGHT** — 1 | AC7 |
| M5 | treat all of `/app` as cacheable | **CAUGHT** — 2 | AC5, AC7 |
| M6 | drop the segment boundary | **CAUGHT** — 1 | AC7 |
| M7 | put `/api/v1` back in place of `/api` | **CAUGHT** — 3 | AC1, AC5, AC9 |
| M8 | harness caches a NetworkOnly response | **CAUGHT** — 35 | AC1, AC3, AC4, AC8 |
| M9 | weaken the drift parser to ignore order | **SURVIVED** (guard weakening) | — |
| M10 | nothing is cacheable at all | **CAUGHT** — 15 | AC2, AC4, AC7 |
| M11 | decide cacheability on `url.href` | **CAUGHT** — 15 | AC2, AC4, AC7 |
| M12 | harness writes under a different key than it read | **CAUGHT** — 10 | AC2 |
| M13 | weaken the `agents.md` guard | **SURVIVED** (guard weakening) | — |
| M9b | order-blind parser **and** a reordered list | **CAUGHT** — 2 | AC1, AC5 |
| M13b | soften `agents.md` to "API routes may be cached" | **CAUGHT** — exactly 1 | AC9 |
| M13c | delete the sensitive-bodies rule from `agents.md` | **CAUGHT** — exactly 1 | AC9 |

**11 of 13 code/harness mutants caught; the 2 survivors are both guard weakenings**, which by construction change no
behaviour and so cannot fail anything. For a drift guard the meaningful mutant belongs on **the thing guarded**, and
all three of those are caught: reordering the list (M9b) fails two other cases, and both edits to `agents.md`
(M13b, M13c) fail the new guard and nothing else. `spec/frontend/agents.md` was verified **byte-identical** after
each revert, following T08's contract-mutation precedent.

## Traceability matrix

| Requirement / decision | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **`agents.md:90`** API routes use NetworkOnly | Yes | `apiRequestPolicy.ts:71-73` (negation of a narrow cacheable set) | `:97` (×12), `:104` (×12), `:169` (×4), `:273` (×3) | No | No |
| **`agents.md:84-85`** account, token, receipt, evidence never cached | Yes | same decision; nothing in the module can write a cache | `:169` (the four by name), `:304` (the rule's own sentence) | No | No |
| **`design.md:194` (§5)** cache holds static shell and assets only | Yes | `:40-43`, `:66-69` | `:115` (×6), `:130` (×3), `:140` (×2), `:259` (×5) | No | No |
| **R57 / L17** the two policies compose | Yes | two modules, disjoint questions | `:214`, `:218` (×12), `:226`, `:233` | No | No |
| **D1** default-deny for anything unenumerated | Yes, structurally | `isNetworkOnlyRequest = !isCacheableRequest` | `:244` (×7), `:315` (negation identity over 20+ paths) | No | No |
| **D3** `/api` covers future versions | Yes | `:35` | `:244` (`/api/v2/accounts`, `/api/health`), `:282` (normalisation in the guard) | No | No |
| **`design.md` §4c (2)** the prefix list is the edge map's | Yes | `:24-37` | `:282` (parsed, order included) | No | No |
| **L3 / L16** nothing sensitive persisted client-side | Yes, by construction | the module holds no cache reference | `:333` (purity scan) | **Runtime enforcement is task 12's** | **Yes — D10, declared** |
| **L14** `/app` is the SPA's | Yes | `:40-43` | `:259`, `:267` | No | No |
| **L12** phase additivity | Yes | two new files | suite 314/316 | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 twelve prefixes and sub-paths NetworkOnly | **PASS** | `:97`, `:104`, `:110` |
| AC2 shell and assets cacheable, keyed by pathname, hit and miss | **PASS** | `:115`, `:130`, `:140`, `:151`, `:158` |
| AC3 the four sensitive body kinds | **PASS** | `:169` |
| AC4 method independence | **PASS** | `:180`, `:185`, `:151` |
| AC5 composition with T09 across all twelve | **PASS** | `:214`, `:218`, `:226`, `:233` |
| AC6 default-deny | **PASS** | `:244` |
| AC7 segment boundaries on the cacheable side | **PASS** | `:259`, `:267` |
| AC8 the notification stream | **PASS** | `:273` |
| AC9 drift against the edge map **and** the standing rule | **PASS** | `:282`, `:304` |
| AC10 negation identity; purity | **PASS** | `:315`, `:333` |
| AC11 suite green, no T01–T10 edit | **PASS with two environmental failures** | 314/316 |

## The two failing tests, stated plainly

Both are in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` (T07), both environmental, both predating this task,
and **neither has ever been counted as passing**:

1. **AC4** expects `oxlint` to print `Found 0 warnings and 0 errors`; this host's `oxlint@1.87.0` prints no summary
   line at all. Verified in T07 with all changes stashed and against a deliberately introduced real `fetch`
   violation.
2. **AC2 end to end** shells out to `pnpm run build`: `spawn pnpm ENOENT`. **`pnpm` is not installed on this host**
   (`packageManager: pnpm@12.14.0`, `~/Library/pnpm/store` exists but the binary does not; only `npm` is on PATH).

An earlier reading in this task said 315/316, counting AC2 as passing. Re-run with the environment inspected, the
honest figure is **314/316**: the suite total is unchanged and no T11 case is involved, but the AC2 case fails on any
host without `pnpm`, so it is now stated as a second known failure rather than a one-off. **This is a live
constraint on task 12, which must add `vite-plugin-pwa` to `package.json` (L13) — an install this host cannot
perform.** See phase 13.

## Coverage, honestly

Enumerated rather than measured (`@vitest/coverage-v8` is not a workspace dependency). Three branch points, and one
side of one of them is **not** exercised here: `segmentPrefixPattern`'s trailing-slash strip, because this module's
only call passes `/app/assets`. It is live and covered in T09's copy. That is the duplication cost phase 7 recorded
and task 12 should collapse — stated rather than papered over, and Kimi's suggested fix for it does not reach the
branch (see gap 2 above).

## Verdict

**PASS.** `agents.md:90` and `:84-85` are implemented as a default-deny policy whose cacheable set is exactly the
shell document and `/app/assets`, with 83 cases, 11 of 13 code mutants caught and both survivors explained and
closed by mutating the guarded sources instead. One declared deviation: a pure policy cannot prevent a cache write,
so **task 12 must wire a true `NetworkOnly` strategy and must import `NETWORK_ONLY_PATH_PREFIXES` rather than
restate it**.
