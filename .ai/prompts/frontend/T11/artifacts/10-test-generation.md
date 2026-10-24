# frontend · T11 · Phase 10 — Test Generation, Mutation and Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T11 — API routes are NetworkOnly |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## The named test

`frontend/src/pwa/serviceWorkerNeverCachesApiResponses.test.ts` — **74 cases**:

| AC | Cases | What is pinned |
|---|---|---|
| AC1 | 25 | each of the twelve prefixes bare, each with a sub-path, plus a table-coverage case |
| AC2 | 6 | the shell documents and two hashed assets are cacheable; a cache hit is served from cache |
| AC3 | 4 | the account, token, receipt and evidence bodies `agents.md:85` names |
| AC4 | 5 | four HTTP methods on an API path; a GET and a POST agree |
| AC5 | 14 | all twelve prefixes paired with their navigation classification, the table-order case, and `/connect/logout` |
| AC6 | 7 | default-deny, including `/default-ui.css`, `/api/v2/accounts` and `/api/health` |
| AC7 | 6 | `/application`, `/app2`, `/appfoo`, `/app/assetsx/evil.js`, `/app-assets/main.js`; the asset prefix itself |
| AC8 | 3 | the notification stream, with the SSE reason recorded |
| AC9 | 1 | the drift guard against `design.md`'s edge map, order included |
| AC10 | 2 | the negation identity over every path in the file; the purity scan |

`vitest run src/pwa`: **158/158** (T09 61 + T10 23 + T11 74). Whole suite **295/296** — the one failure is the known
environmental `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` prints no summary line;
baselined in T07 both ways). `tsc --noEmit` clean, `oxlint` reports no violations.

## Mutation pass

Eleven mutants, applied to real files, suite run, reverted with `git checkout --`; tree verified clean after each.
The test file was committed first (`324ba48e`), per T09's lesson.

| # | Mutant | Result | Failing ACs |
|---|---|---|---|
| M1 | drop `/notifications` from the list | **CAUGHT** — 3 | AC1, AC5, AC9 |
| M2 | invert the default: unknown paths become cacheable | **CAUGHT** — 11 | AC5, AC6, AC7, AC10 |
| M3 | decide by the prefix list instead of the cacheable set | **CAUGHT** — 10 | AC5, AC6, AC7 |
| M4 | loosen the asset match to `startsWith` | **CAUGHT** — 1 | AC7 |
| M5 | treat all of `/app` as cacheable | **CAUGHT** — 2 | AC5, AC7 |
| M6 | drop the segment boundary from the asset pattern | **CAUGHT** — 1 | AC7 |
| M7 | put `/api/v1` back in place of `/api` | **CAUGHT** — 3 | AC1, AC5, AC9 |
| M8 | the harness caches a NetworkOnly response | **CAUGHT** — 35 | AC1, AC3, AC4, AC8 |
| M9 | weaken the drift parser to ignore order | **SURVIVED** | — |
| M10 | nothing is cacheable at all | **CAUGHT** — 7 | AC2, AC7 |
| M9b | order-blind parser **and** a reordered list (paired) | **CAUGHT** — 2 | AC1, AC5 |

**10 of 11 caught. M9 survived, and it is reported as such rather than dressed up.**

### Why M9 survives, and why it is not a coverage gap
M9 weakens AC9's comparison from order-sensitive to order-blind without changing any behaviour, so nothing can fail:
it is a *guard* weakening, not a defect. The question that matters is whether anything else would notice a list
whose order actually changed — and M9b answers it: with the parser blinded **and** the list reordered, two other
cases still fail, AC1's sub-path table-coverage case and AC5's pairing-table order case. Order is therefore pinned
in three places, and AC9's order sensitivity is the redundant one. T09's M10 was the same construction and reached
the same conclusion.

### A correction to the phase 7 self-review
That review said AC2 is "the only real counterweight" to default-deny's weak rows. The mutants show it is sharper
than that, and in two directions:
- **inverting the default** (M2, M3) is caught by **AC6 and AC7**, the default-deny and boundary rows — not by AC2,
  which still passes because the shell and assets stay cacheable under those mutants;
- **over-restricting** (M10, nothing cacheable) is what **AC2** exists for, and it fails there as intended.

So the suite has two counterweights pulling opposite ways, and neither alone would do. The self-review named only
one of them.

## Coverage

`@vitest/coverage-v8` is not a workspace dependency (out of scope; the lockfile is under a supply-chain policy
check), so coverage is enumerated, as in T07–T10.

`apiRequestPolicy.ts` has three branch points, every side exercised: `segmentPrefixPattern`'s trailing-slash test
(`/app/assets` has none — the false side; T09's copy covers the true side, and this module's own list has no
trailing slashes, so that branch is **not** exercised here — see the note below); the `SHELL_DOCUMENT_PATHS`
membership check (true: AC2's three shell rows; false: everything else); and `ASSET_PATTERN.test` (true: AC2's two
asset rows and AC7's prefix case; false: AC1, AC6, AC7's negatives).

**One honestly unexercised branch:** `segmentPrefixPattern`'s `path.endsWith('/')` true-branch. This module only
ever calls it with `/app/assets`, so the trailing-slash strip never runs here. It is live code in T09's copy and
covered by T09's own cases, which is exactly the duplication cost the phase 7 self-review flagged (finding 2) and
which task 12 should collapse.
