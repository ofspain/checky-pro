# frontend · T12 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

# Verdict: **PASS**

61 cases in this task's file, **20 of 20 mutants caught**, suite **375/377** with only the two known environmental
T07 failures, `tsc` and `oxlint` clean, `.oxlintrc.json` untouched, both spec files byte-identical after the
mutation pass.

## Kimi's phase 11 gaps: four applied, three already closed, one behaviour change rejected with reasons

| # | Gap | Disposition |
|---|---|---|
| 1 | `main.tsx` missing from the forbidden-call source scan | **Already closed** — premise stale |
| 2 | no case for a second sequential update notifying again | **Behaviour change rejected; case added pinning the opposite** |
| 3 | no case for `updatefound` with null `installing` | **Already closed** — premise stale |
| 4 | no case for failed-registration memoisation | **Already closed** — premise stale |
| 5 | no React-effect-shaped unsubscribe case | **Applied** |
| 6 | no case that the worker is not precached | **Applied** |
| 7 | no case for `onUpdateWaiting` when registration is `undefined` | **Applied, premise corrected** |
| 8 | no mutant for AC9 | **Applied — and it exposed a real hole** |

### Gaps 1, 3 and 4 were fixed in phase 9, before the review was written

All three were my own phase-7 findings (S6, S4, S2), applied in phase 9 and listed there. The review appears to
have been written against phase 10's *manifest* rather than the test file. Verified in source:

- gap 1 → `serviceWorkerUpdateWaitsForReload.test.ts:114` — the `it.each` table reads
  `['src/pwa/serviceWorker.ts', 'src/pwa/registerServiceWorker.ts', 'src/main.tsx', 'vite.config.ts']`.
- gap 3 → `:222`, "does not drop an update whose installing worker is already gone (S4)".
- gap 4 → `:360`, "makes one attempt per page load and does not retry a failure (S2)".

Recorded rather than silently skipped, per the standing rule that a cited finding is checked at its `file:line`
before it is accepted or rejected.

### Gap 7's premise was also stale, but the case was worth adding

The review says `onUpdateWaiting` "calls `registerServiceWorker()` and checks `registration?.waiting`". It did —
until phase 9 removed exactly that, which was finding S3. There is no longer a registration to resolve to
`undefined`. The *underlying* worry is still legitimate: subscribing must be harmless where service workers do not
exist. Added with the premise corrected in the case's own comment.

### Gap 2 — rejected as a behaviour change, and the opposite is now pinned

The review asks for a second update to notify a second time. **"An update is waiting" is a state, not an event.**
It becomes true when a build arrives and stays true until a load applies it — the only thing that can, since
`skipWaiting` is forbidden. A second build replacing the first does not change that state, so a prompt already on
screen has nothing new to say, and a counter would double-count one fact.

The alternative also costs something concrete: re-announcing would re-raise a notice the user has already
dismissed, every time a deploy lands, with no new action available to them.

A case now states this explicitly (`:244-268`) and covers the part that genuinely matters — a subscriber arriving
*after* the second build still learns the state immediately. M20 (clear the flag after notifying) is caught by it
and by two others.

### Gap 8 was the most valuable finding, because the assertion really was weak

I had argued in phase 10 that AC9 needed no mutant. That was wrong, and the mutant proves it: AC9 asserted only

```ts
expect(source).not.toContain('i18next');
expect(source).not.toMatch(/\bt\(/);
```

so `export const UPDATE_NOTICE = 'A new version is available';` would have passed both — no `i18next`, no `t(`.
**AC9 was testing for the absence of a translation call, not the absence of copy.** It now also asserts that no
string literal in either module's code contains a space, which separates a sentence from a path or a module
specifier. M18 introduces exactly that string and is caught.

This is the second time in this task that a "no mutant needed" claim of mine turned out to hide a weak assertion —
the first was AC7's vacuous precache case in phase 10. Both were found by taking the mutant seriously rather than
arguing it away.

## Traceability

| Requirement / decision | Implemented | Where | Tested | Deviation | Blocked |
|---|---|---|---|---|---|
| **`agents.md:91`** prompt-to-reload, no `skipWaiting`/`clients.claim` | Yes | `serviceWorker.ts`, `registerServiceWorker.ts` — no such call exists | AC1, AC2, AC3 + M1, M2, M6, M20 | No | No |
| **`package.md:320`** one worker-wide policy | Yes | one worker, one registration site, one manifest declaration | AC4 + M10, M13 | No | No |
| **`package.md:321-323`** forced update for rollback | **No — out of scope** | — | — | **Declared**, frozen brief D-8 | Unowned by any task |
| **L17** worker boundaries | Yes | `NavigationRoute` denylist by identity; `NetworkOnly` default | AC6 + M4, M5, M7, M8 | No | No |
| **R57** denylisted navigations | Yes (first time live) | T09's list, imported | AC6, AC7 + M4, M5 | No | No |
| **L13** `vite-plugin-pwa` (Workbox) | Yes | `injectManifest`, pinned 2.0.0 / 7.4.1 | AC2 + M12 | No | No |
| **L14 / L9** SPA under `/app`, single origin | Yes | scope `/app/`, stated not inherited | AC5 + M3, M9 | No | No |
| **L10 / R58** no secrets or env keys | Yes | neither new file names one | AC8 + T01's `src/**` scan | No | No |
| **L11** accessibility and i18n | Yes, vacuously | no user-facing copy is introduced | AC9 + M18 | No | prompt UI out of scope (D-8) |
| **L3 / L16** client-side storage | Yes | no storage API touched; precache is shell + assets only | AC8 + M12 | No | No |
| **L12** phase additivity | Yes | T01 edited in three places, all justified | AC11 + M13 | No | No |
| **`design.md:194`** cache holds static shell and assets only | Yes | every precache entry satisfies `isCacheableRequest` | AC7 + M12 | No | No |
| **`package.md:172`** named test | Yes | `serviceWorkerUpdateWaitsForReload.test.ts`, 61 cases | — | **mapped to R57, which does not describe it** (phase 1 OQ-1) | No |

## Acceptance criteria

| AC | Result | Evidence |
|---|---|---|
| AC1 no `skipWaiting`/`clients.claim`, source and shipped bundle | **PASS** | `:114`, `:549` + M6 |
| AC2 no self-activating configuration | **PASS** | `:137` + M6, M12 |
| AC3 a new build waits | **PASS** | 12 lifecycle cases + M1, M2, M20 |
| AC4 one worker-wide policy | **PASS** | `:341`, `:401` + M10, M13 |
| AC5 scope confined to the SPA | **PASS** | `:363`, `:377`, `:390` + M3, M9 |
| AC6 derived from T09/T11, by identity | **PASS** | `:495`-`:518` + M4, M5, M7, M8 |
| AC7 precache, both halves | **PASS** | real manifest + synthetic matcher + M3, M16, M17 |
| AC8 hygiene | **PASS** | `:613` + M12 |
| AC9 no user-facing copy | **PASS** (assertion strengthened this phase) | `:625` + M18 |
| AC10 drift guards | **PASS** | `:645` + M14, M15 |
| AC11 baseline, minimal edits elsewhere | **PASS with two environmental failures** | 375/377 |

## The two failing tests, unchanged from the recorded baseline

Both in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` (T07), both environmental, both predating this task,
neither ever counted as passing:

1. **AC4** — this host's `oxlint@1.87.0` prints no summary line, so `Found 0 warnings and 0 errors` never appears.
2. **AC2 end to end** — shells out to `pnpm run build`: `spawn pnpm ENOENT`. `pnpm` is not installed on this host;
   T12's own install used `npx -y pnpm@12.14.0`, which does not put a `pnpm` binary on PATH, so this is unchanged.

The suite went 314/316 → **375/377**: 61 new cases, same two failures, nothing else moved.

## Honest limits

- **The waiting lifecycle is simulated.** `FakeRegistration`/`FakeWorker` drive `updatefound` and `statechange` as
  the spec describes. No jsdom test can show a real browser refusing to promote a waiting worker — the heart of
  OQ-1 — which needs Playwright (L13) and belongs with the prompt UI.
- **Gap 6's assertion is a guard, not a live catch.** The plugin does not put the worker in its own manifest, and
  nothing in this task could make it. Unlike AC7's first half, I did not find a way to exercise it directly, so it
  is reported as a guard rather than dressed up as a tested rule.
- **`SERVICE_WORKER_URL` is a guarded literal, not derived** — deriving it would mean naming an env key (R58).
- **`segmentPrefixPattern` is still duplicated** between `navigationDenylist.ts` and `apiRequestPolicy.ts`.
  Permitted but not required by the frozen brief; now the only T09–T11 carry-forward left open.

## Open question for the owner, not a blocker

**OQ-1** stands as written in the frozen brief: `tasks.md:21`'s "waits for a user reload" is not achievable by a
refresh, and the only mechanism that would make it so is forbidden by `agents.md:91`. This task's code is identical
under every reading of it and obeys `agents.md`. What a future prompt may honestly promise the user — "applies next
time you open the app", a reload button that may not work, or an ADR permitting a user-initiated `skipWaiting` — is
an owner decision needed before the prompt UI is built.
