# frontend · T11 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/13-pr-preparation.md` |

## Commit title

```
frontend T11: API routes are NetworkOnly
```

## Commit message

```
T09 and T10 both return false for anything that is not a navigation, by
design, so this task owns the whole request side. The rule is sharper
than staleness: Cache Storage is client-side storage, and agents.md:85
names the bodies that must never enter it -- account, token, receipt,
evidence -- so an API response in a cache breaks L3 and L16, not just
freshness.

src/pwa/apiRequestPolicy.ts states the rule the way design.md §5 does,
positively: the cache holds "static shell and assets only". Cacheable is
the narrow case -- the shell document and /app/assets, matched by
segment -- and isNetworkOnlyRequest is its exact negation, so a path
nobody enumerated can never fall through into a cache. A thirteenth
backend prefix added at the edge is NetworkOnly without an edit here.
The twelve-prefix list is published for task 12's matchers and for the
drift guard, and the decision never consults it; /api/v1 is normalised
to /api so a future /api/v2 is already covered.

src/pwa/serviceWorkerNeverCachesApiResponses.test.ts, 83 cases. AC5
imports T09's predicates to show the two policies compose across all
twelve prefixes -- including that R57 covers /connect only at
/connect/logout and never mentions /notifications, which is why that one
is this task's business. AC9 guards both the edge map in design.md and
the standing sentences in agents.md.

13 code and harness mutants, 11 caught. The two survivors are guard
weakenings, which change no behaviour; the properties they guard are
pinned by mutating the guarded sources instead -- reordering the list
fails two other cases, and softening or deleting either agents.md rule
fails the new guard and nothing else.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/pwa/apiRequestPolicy.ts` | **new**, 76 lines |
| `frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts` | **new**, 83 cases |
| `.ai/prompts/frontend/T11/artifacts/*` | the fourteen phase artifacts |

No existing file modified. `navigationDenylist.ts`, the T09/T10 tests, every T01–T08 file, `vite.config.ts`,
`.oxlintrc.json`, `spec/**`, `contracts/**`, `services/**` and `infra/**` are untouched.

## Testing performed

| Check | Result |
|---|---|
| `vitest run src/pwa` | **167/167** (T09 61 + T10 23 + T11 83) |
| `vitest run` (whole suite) | **314/316** |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |
| Mutation pass | **13 code/harness mutants, 11 caught**; 2 guard weakenings, closed by 3 mutants on the guarded sources |
| `spec/frontend/agents.md` | **byte-identical** after each spec mutation was reverted |

Both failures are in `lintRejectsHandWrittenBackendFetch` (T07), environmental, and unaffected by this task: **AC4**,
because this host's `oxlint@1.87.0` prints no summary line (baselined in T07 both ways), and **AC2 end to end**,
which shells out to `pnpm run build` and gets `spawn pnpm ENOENT` — `pnpm` is not installed on this host at all.
An earlier reading in this task said 315/316, counting AC2 as passing; 314/316 is the honest figure.

## Carried forward — task 12 now owns four obligations

1. **Import, do not restate.** Task 12 must build its Workbox matchers from `NETWORK_ONLY_PATH_PREFIXES` (T11) and
   `NAVIGATION_DENYLIST` (T09). Both lists are load-bearing for a task that has not been written, and each is
   defended by a single drift guard.
2. **Wire a true `NetworkOnly` strategy** that neither reads nor writes the cache. A pure policy cannot prevent a
   cache write; T11 proves the classification, not the plumbing (D10).
3. **Guard the precache manifest, both halves** (from T09 and T10): no URL in it may match `NAVIGATION_DENYLIST`,
   **and** the navigation route must still answer `/app/signed-out` from the shell. The two pull in opposite
   directions, which is why both halves are named.
4. **Collapse the duplicated helper.** `segmentPrefixPattern` now exists in `navigationDenylist.ts` (private) and in
   `apiRequestPolicy.ts` (a copy). Task 12 legitimately opens the first file, so it is the moment to share one
   implementation — which also closes the one branch T11's suite does not exercise.

**And a new blocker for task 12, found while verifying this task's own numbers.** Task 12 must add `vite-plugin-pwa`
to `frontend/package.json` (L13 names it, with Workbox, as the confirmed stack), and neither it nor any `workbox-*`
package is in `pnpm-lock.yaml` or the local store. The registry is reachable, but **`pnpm` is not installed on this
host**, and `npm install` in a pnpm workspace would write the wrong lockfile. Task 12 cannot reach its own phase 6
until that is resolved — owner input, not a design choice.

A second constraint, from T07's AC5: `.oxlintrc.json` is asserted to have **no `overrides` key** and the lint run is
asserted to execute **exactly 2 rules**. So task 12 cannot carve out a lint exemption for the worker; either the
worker never names `fetch`/`XMLHttpRequest` in a restricted form (Workbox's own call lives in `node_modules`, which
is not linted), or T07's frozen assertions must be re-opened. This supersedes the earlier assumption that task 12
would simply add an `.oxlintrc.json` override.

Plus, unchanged from T10: the real 302 redirect chain and cross-tab cache isolation need integration coverage, not
jsdom; `bin/smoke` already exercises the former outside the SPA.

## Specification references

`agents.md:84-85` (sensitive bodies), `:88-89` (navigation denylist), `:90` (NetworkOnly); R57
(`requirements.md:203-205`); `design.md:61-62` (L17), `:158-160` (§4c (2) edge path map), `:194` (§5), `:139` (L14),
L9, L3, L16, L12, L6; `infra/local/edge/Caddyfile:12-26`.
