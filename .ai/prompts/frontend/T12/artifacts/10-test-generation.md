# frontend · T12 · Phase 10 — Test Generation, Mutation & Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T12 — Prompt-to-reload update policy |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## Where the suite stands

| | |
|---|---|
| `serviceWorkerUpdateWaitsForReload.test.ts` | **57 cases** (47 in phase 6, +9 in phase 9, +1 here) |
| `vitest run src/pwa` | **224/224** — T09 61, T10 23, T11 83, T12 57 |
| `vitest run` (whole suite) | **371/373** — the two known environmental T07 failures, nothing else |
| `tsc --noEmit`, `oxlint` | clean; `.oxlintrc.json` untouched |

## The mutation pass: 17 mutants, 17 caught

Tests were committed before the first mutant, per T09's lesson — the harness reverts with `git checkout --`, which
would discard uncommitted work. Each mutant was applied to real source, run against `src/pwa` **and** T01's build
test, then reverted.

| # | Mutant | File | Result | Caught by |
|---|---|---|---|---|
| **M1** | restore the two-path notification that S1 removed | `registerServiceWorker.ts` | **CAUGHT** (3) | the S1 case, plus the S3 and S4 cases |
| **M2** | drop the dedupe guard, so one build is reported repeatedly | `registerServiceWorker.ts` | **CAUGHT** | "reports one waiting build once" |
| **M3** | change the SPA `base` | `vite.config.ts` | **CAUGHT** (3) | the S7 guard and two T01 cases — **but not the AC7 case it was aimed at**; see below |
| **M4** | drop the navigation denylist entirely | `serviceWorker.ts` | **CAUGHT** | the AC6 identity case |
| **M5** | pass a *copy* of the denylist | `serviceWorker.ts` | **CAUGHT** | the AC6 identity case |
| **M6** | make the worker activate itself (`install` → `skipWaiting`) | `serviceWorker.ts` | **CAUGHT** (2) | the source scan **and** the emitted-bundle scan |
| **M7** | register the navigation fallback before the precache | `serviceWorker.ts` | **CAUGHT** (2) | the ordering case and T01's build |
| **M8** | drop the `NetworkOnly` default | `serviceWorker.ts` | **CAUGHT** (2) | the default-handler case |
| **M9** | widen scope by dropping the trailing slash | `registerServiceWorker.ts` | **CAUGHT** (3) | the Kimi-8.1 case, the S7 guard, the scope case |
| **M10** | break registration idempotence (`??=` → `=`) | `registerServiceWorker.ts` | **CAUGHT** (2) | "registers once" and the S2 case |
| **M11** | let one listener's failure escape | `registerServiceWorker.ts` | **CAUGHT** | the throwing-listener case |
| **M12** | let the plugin emit a web app manifest | `vite.config.ts` | **CAUGHT** (3) | this task's case, T01's, and the cacheable-precache case |
| **M13** | remove the registration call from the entry | `main.tsx` | **CAUGHT** | T01's entry-shape assertion — the one this task edited |
| **M14** | soften `agents.md`'s standing rule | `spec/frontend/agents.md` | **CAUGHT** | the drift guard |
| **M15** | soften `package.md` §10 | `spec/frontend/package.md` | **CAUGHT** | the drift guard |
| **M16** | match the raw precache entry instead of the resolved one | the AC7 matcher | **CAUGHT** | only the synthetic-entry case |
| **M17** | invert the AC7 matcher | the AC7 matcher | **CAUGHT** (2) | both AC7 cases |

Both spec files were **byte-identical** after revert (`git status` clean), following T08's precedent.

## M3 found a real weakness in my own test, and one case was added

M3 was caught — but **not by the case it was aimed at**. The AC7 precache case resolved manifest entries against a
hardcoded `${ORIGIN}/app/`, so it could not fail whatever `base` became; the mutant was caught by the S7 guard and
by T01 instead. Phase 6 had already recorded that AC7's first half "guards a future change rather than catching one
today"; the mutation pass showed that was *more* true than written — the case was not merely unable to fail today,
it was unable to fail at all.

The rule is now a named matcher, `denylistedPrecacheUrls(urls, base)`, exercised two ways:

1. against the **real** manifest, which must come back clean;
2. against entries **no build emits** — `'../login'`, `'../api/v1/invoices'`, `'/oauth2/authorize'` — which must all
   be flagged, plus the two real entry shapes, which must not.

The second case also pins *why* resolution matters: `NAVIGATION_DENYLIST.some((p) => p.test('../login'))` is
asserted to be `false`, so the test states that matching raw entries would pass vacuously rather than leaving a
reader to work it out. M16 (drop the resolution) is caught **only** by this case, which is the proof the first case
alone was vacuous. M17 (invert the matcher) is caught by both.

This is the T11 lesson applied one level deeper: a guard against a condition the build cannot produce can still be
tested, by feeding the guard the condition directly.

## What each acceptance criterion now rests on

| AC | Pinned by | Mutant proof |
|---|---|---|
| AC1 no `skipWaiting`/`clients.claim` | source scan over 4 files + emitted worker and app chunks | M6 |
| AC2 no self-activating config | `strategies: 'injectManifest'`, absence of `registerType`/`skipWaiting`/`clientsClaim` | M6, M12 |
| AC3 a new build waits | 8 lifecycle cases; 3 orderings each asserting exactly one notification | M1, M2 |
| AC4 one worker-wide policy | one registration site, one `__WB_MANIFEST` declaration, one `filename`, idempotent register | M10, M13 |
| AC5 scope | explicit `{ scope }`, trailing-slash semantics, no denylist pattern matches it | M9 |
| AC6 derived from T09/T11 | denylist by **identity**; shell URL from `SHELL_DOCUMENT_URL`; `NetworkOnly` default; route ordering | M4, M5, M7, M8 |
| AC7 precache, both halves | the matcher above, real + synthetic; shell present; `/app/signed-out` absent and still a shell navigation | M3, M16, M17 |
| AC8 hygiene | no env key, no absolute URL, no storage in either new file; precache entries all `isCacheableRequest` | M12 |
| AC9 no new copy | no `i18next`, no `t(` in either new file | — |
| AC10 drift | `agents.md:91` and `package.md:320`, whitespace-collapsed | M14, M15 |
| AC11 baseline | 371/373, T01 green after its three edits | M13 |

## Coverage, honestly

- **AC9 has no mutant.** Introducing a string is not a mutation of existing behaviour; the assertion is a
  prohibition. Stated rather than padded with a mutant that would prove nothing.
- **The waiting lifecycle is simulated, not observed.** `FakeRegistration`/`FakeWorker` drive `updatefound` and
  `statechange` as the spec describes them. What no jsdom test can show is a **real** browser refusing to promote a
  waiting worker, which is the heart of OQ-1. That needs Playwright (L13) and belongs with the prompt UI, not here.
- **`SERVICE_WORKER_URL` is still a literal**, guarded rather than derived, because deriving it would mean naming
  an env key (R58). M3 and M9 show the guard works.
- **`registerServiceWorker`'s `controller` is never asserted to change**, because nothing in this task can change
  it; the case asserts it is *unchanged*, which is the whole claim.
- **The duplicated `segmentPrefixPattern`** remains uncollapsed — permitted but not required by the frozen brief.
  It is now the only carried-forward item from T09–T11 still open.

## Carried into phase 12

Nothing new. Phase 12 should record: 17/17 mutants caught, the M3 finding and the case it produced, and that AC7's
first half is a tested guard rather than a live catch.
