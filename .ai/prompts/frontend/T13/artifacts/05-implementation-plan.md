# frontend · T13 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Every file traces to the frozen brief's Files sections.

## Two decisions the brief left to this phase

**1. The test file lives at `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts`.** `design.md` §6's package
map has no "build" package, and `src/app/` is the established home for a build-level test (T01's
`shellBuildsAndMounts.test.tsx` runs the only other full build outside `src/pwa/`). Putting it in `src/pwa/` would
be wrong — the worker is one of the files it scans, not its subject.

**2. One build, in a `beforeAll`, reused by every case** — T12's shape. This is the third file in the suite to run
a full `vite build`; at roughly 2s each that is acceptable, and the alternative (a build per case) would multiply
it by the number of cases for no gain, since none of them mutate the output.

The negative control gets its **own** temp directory of hand-written files, never the real build's — so a bug in
the control can never corrupt the real assertion.

## Files to create

| # | Path | Purpose |
|---|---|---|
| 1 | `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts` | `scanBundle` + the named test's cases |

## Files to modify

| # | Path | Change |
|---|---|---|
| 2 | `frontend/vite.config.ts` | add `envPrefix: ['VITE_']` with a comment citing R58 (D-6). Nothing else. |

Nothing else is expected to change. If the implementation finds it must touch another file, that is a finding to
record, not a step to take quietly — T12's unpredicted third T01 edit is why this is stated twice.

## The scanner (D-2)

`scanBundle` is **test-local**: not exported from a production module, so nothing in `src/` can come to depend on
it (the frozen brief's module-boundary constraint).

```ts
type BundleScan = {
  files: string[];
  envKeys: string[];
  secrets: Array<{ file: string; hit: string }>;
  absoluteUrls: string[];
  sourcemaps: string[];
};

function scanBundle(treeRoot: string): Promise<BundleScan>;
```

Internals, all private to the file:

- `walk(dir)` — recursive, returns every file path relative to the root. Sorted, for stable assertions.
- `ENV_KEY_PATTERNS` — `/VITE_[A-Z0-9_]+/g` and `/process\.env\.([A-Za-z0-9_]+)/g`.
- `SECRET_PATTERNS` — private-key block, `client_secret`, `Bearer ey…`, three-part JWT, `AKIA[0-9A-Z]{16}`.
- `REAL_VALUES` — `['checky-local-only']`. `test` is deliberately excluded (D-10): it matches ordinary words.
- `ABSOLUTE_URL_PATTERN` — `/https?:\/\/[A-Za-z0-9._:-]+(?:\/[A-Za-z0-9._\-/]*)?/g`, reduced to distinct values.
- **It throws if `files` is empty.** A scan over nothing satisfies every prohibition, which is the trap this task
  is most prone to.

Files are read as UTF-8 text. Every file the build currently emits is text; a future binary asset would still be
read, and a decode artefact cannot create a false *negative* — only noise, which a failing case would surface.

## Cases, in the order they will be written

| Group | Cases | AC |
|---|---|---|
| **The tree** | the scan is non-empty; it contains `index.html`, exactly one `assets/*.js`, exactly one worker file; the worker's name is discovered, not assumed | AC1, D-5 |
| **Env keys, default-deny** | `envKeys` minus the §4c allowlist is empty; and today `envKeys` is empty outright, asserted separately so the stricter present state is recorded without being mistaken for the rule | AC1, AC7 |
| **Allowlist drift** | the five-step parser over `design.md`; set equality against the four keys; fails on a count other than four, a malformed token, or a missing heading | AC6 |
| **Negative control, same scanner** | a synthetic tree containing an unlisted `VITE_` key, a `process.env` key, a private-key block, a JWT, a `Bearer ey…`, `client_secret`, `checky-local-only`, an absolute backend URL and a `.map` file — each asserted to be **reported**; plus `scanBundle` throwing on an empty tree | **AC7**, D-2, D-10 |
| **Secrets** | `secrets` is empty over the real build | AC2, AC5 |
| **Config** | no `define` in `vite.config.ts`; `envPrefix` is exactly `['VITE_']` | AC3 |
| **No env files** | no `.env*` under `frontend/` | AC4 |
| **Absolute URLs** | `absoluteUrls` reduced to distinct entries equals `KNOWN_ABSOLUTE_URLS`; each entry justified in a comment; none names `auth`, `payment`, `notification` or `crypto` | AC8 |
| **Sourcemaps** | `sourcemaps` is empty | AC10 |
| **Vite-shaped access** | no emitted file contains the literal `import.meta.env.` | AC11 |

`KNOWN_ABSOLUTE_URLS` is written from the measured output (frozen brief D-7): react-router's two `http://localhost`
placeholders, four W3C namespace URIs, two framework documentation links, one Workbox documentation link. Each gets
its one-line reason.

## Entities · repositories · services used

None. No persistence, no repositories, no services — the backend-shaped headings of the phase prompt do not apply.
The only "dependency" is the Vite build itself, invoked the way T01 and T12 invoke it.

## Execution order

1. **Pin `envPrefix`** in `vite.config.ts` and rebuild, confirming the output is byte-for-byte equivalent in
   content (the prefix is already the default, so nothing should change). If anything *does* change, stop and
   record why.
2. **Write `scanBundle` and the tree cases**, and run them against the real build. These establish that the
   scanner sees what the build emits before any prohibition is asserted.
3. **Write the negative control next, not last.** It is the criterion the task turns on (AC7), and writing it
   second means every prohibition that follows is added to a scanner already known to detect its category.
4. **Add the prohibition cases** — env keys, secrets, absolute URLs, sourcemaps, `import.meta.env.` — each one
   paired with the control entry that proves it can fail.
5. **Add the config, `.env` and drift-guard cases**, which read sources rather than the build.
6. **Run** `tsc --noEmit`, `oxlint`, the file alone, then the whole suite; record the before/after counts for
   AC9's rule.

Step 3's placement is the one deliberate departure from writing cases in acceptance-criterion order. T12 taught
that a prohibition written before its control reads as passing when it may be asserting nothing.
