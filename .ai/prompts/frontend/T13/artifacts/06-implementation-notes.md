# frontend · T13 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

## Result

| Check | Result |
|---|---|
| `bundleContainsNoSecretEnvKeys.test.ts` | **27/27** |
| `vitest run` (whole suite) | **402/404** — 377 before + 27 added; the same two environmental T07 failures |
| `tsc --noEmit` | clean |
| `oxlint` | exit 0, no violations |
| `vite build` | clean; output byte-identical after pinning `envPrefix` |

AC9's rule is satisfied exactly: the pre-task count was **375/377** (recorded before the first line was written),
the post-task count is **402/404**, the delta is precisely the 27 new cases, and no other case changed state.

## What was built

| File | |
|---|---|
| `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts` | **new**, 27 cases, with `scanBundle` as the single definition of "what counts as a hit" |
| `frontend/vite.config.ts` | `envPrefix: ['VITE_']` pinned, with a comment citing R58 — the only production change |

Nothing else was modified. In particular **no T01–T12 file was touched**, which was the one thing phase 5 flagged
twice after T12's unpredicted third T01 edit.

## The finding that mattered: the test was scanning the wrong artifact

AC8 — set equality over absolute URLs, which Kimi's finding 8 turned from my loose "no absolute URLs, exceptions if
any" into a real assertion — failed on the first run. Not because of an unexpected URL, but because of **dozens** of
them: `react.dev/link/warning-keys`, `react.dev/warnings/version-mismatch`, `reactjs.org/link/react-polyfills` and
more. Those come from **React's development build**. The bundle under test was not a production bundle at all.

Two separate causes, found in sequence:

1. **Vite's JS API is not the CLI.** The plan had the build run through `build()` from `vite`, copying T01. Invoked
   that way the output keeps React's development machinery. Switched to shelling out to
   `node_modules/.bin/vite build`, which is what `pnpm build` runs — T07's precedent for exercising a real binary.
2. **The subprocess inherited `NODE_ENV=test`.** Vitest sets it, `execFile` passes the environment on, and
   `vite build` respects an explicit `NODE_ENV` instead of applying its production default. So the CLI build was
   *also* a development bundle. Fixed by deleting `NODE_ENV`, `VITEST` and `VITEST_MODE` from the child
   environment, leaving what a shell running `pnpm build` would give.

**This is the whole point of the task, and it was nearly missed.** A scan of a development bundle would have passed
every secret and key assertion in this file while verifying an artifact that never ships. The set-equality
criterion is the only reason it surfaced: a pattern-based "no backend URLs" check would have found none of the
react.dev links objectionable and reported success.

A new case now makes the artifact's identity a tested property rather than an assumption:

> `is a production build, not a development one carrying dev-only code` — the main chunk must contain no
> `react.dev/(link|warnings)` reference and no `process.env.NODE_ENV`.

So a future change to how the build is invoked cannot quietly swap what is being scanned.

## Carried forward: T01 asserts against a development bundle

`shellBuildsAndMounts.test.tsx` builds through Vite's JS API from inside vitest, so **its build output is a
development bundle** for the same two reasons above.

Nothing T01 claims is wrong — its assertions are about module-script paths under `/app/assets`, the presence of
`serviceWorker.js`, the absence of `sw.js` and `manifest.webmanifest`, and the absence of `skipWaiting` /
`clients.claim` in emitted chunks. All of those hold identically in either mode, and none depends on production
minification. But T12's phase-6 note that the forbidden calls were "verified against the real build output" is
**overstated for the T01 half**: T12's own `src/pwa` case shells out to the CLI and is unaffected, while T01's
companion assertion ran against the development artifact.

`shellBuildsAndMounts.test.tsx` is on this task's not-to-modify list, so it is recorded rather than changed. The
cheap fix, for whoever picks it up, is the same four lines used here.

## Decisions taken as the plan specified

- **The test lives in `frontend/src/app/`** — `design.md` §6 has no build package, and `src/app/` is T01's
  precedent. `src/pwa/` would have been wrong: the worker is one of the files scanned, not the subject.
- **One build in `beforeAll`**, reused by every case; the negative control writes its own separate temp tree, so a
  bug in the control cannot touch the real assertion.
- **The negative control was written second, not last**, as the plan required. Every prohibition added afterwards
  went onto a scanner already known to detect its category. Six categories are proven reportable: an unlisted
  `VITE_` key, a `process.env` key, a private-key block, `client_secret`, a bearer token, a JWT, an AWS key id, the
  real platform value `checky-local-only`, a platform-service URL, and a sourcemap — plus `scanBundle` **throwing**
  on an empty tree, which is the vacuity trap the frozen brief named.
- **`envPrefix: ['VITE_']` pinned**, and the rebuild produced an identical content hash (`index-CXk-vgQh.js`
  before and after), confirming it only states the existing default.
- **`KNOWN_ABSOLUTE_URLS` has eight entries**, each with a one-line reason: react-router's two `http://localhost`
  placeholders (a fallback origin, and a base for parsing relative hrefs), four W3C namespace identifiers from
  React's SVG/MathML handling, and three documentation links from library warning messages. One case additionally
  asserts no entry names `auth`, `payment`, `notification`, `crypto`, `postgres` or `localstack`.

## Confirmations

- **Zero environment keys in the bundle** — asserted twice, deliberately: once as "nothing outside the §4c
  allowlist" (the rule) and once as "nothing at all" (today's stricter reality, since the OIDC configuration is
  derived at runtime). The second case carries a comment pointing at OQ-1 so the two are not confused.
- **Zero `import.meta.env.`**, zero sourcemaps, no `define` block, no `.env*` file under `frontend/`.
- **The allowlist is parsed from `design.md` §4c** by the frozen brief's five steps and asserted by set equality
  against the four keys; the parser keys on `/Environment keys/i`, so §4c's stale "PROPOSED, pending sign-off"
  wording does not matter.
- **The scanner is test-local** — not exported from any production module, so nothing in `src/` can come to depend
  on it.

## Open, unchanged

**OQ-1** stands: `design.md` §4c declares four public keys and R58 permits them, while T01 `:130` forbids any
source from naming the environment object at all. No conflict today — nothing reads a key. The owner must settle
it before an auth task (T14+) consumes one. T13's position, as frozen: the allowlist is a ceiling, not a plan.
