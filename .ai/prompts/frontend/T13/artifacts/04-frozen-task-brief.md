# frontend · T13 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **frozen; phases 5–13 may use nothing else** |

The phase 2 brief stands, amended by the decisions below. Where a decision contradicts it, the decision wins.
Several were settled by building the bundle and reading it, rather than by argument — those carry their evidence.

## Disposition of Kimi's phase 3 findings

| # | Finding | Disposition |
|---|---|---|
| 1 | host dependency gap blocks phase 6 | **Premise rejected with evidence** — D-0 |
| 2 | allowlist parser unspecified | **Accepted** — D-3 |
| 3 | scanner interface undefined, so the negative control may not test the real path | **Accepted; the best finding in the review** — D-2 |
| 4 | `process.env.NODE_ENV` false positive | **Accepted, and free** — D-4 (zero occurrences measured) |
| 5 | worker filename may be hashed | **Recommendation accepted**, premise settled empirically — D-5 |
| 6 | pin `envPrefix` | **Accepted** — D-6 |
| 7 | baseline `375/377` unverified and stale | **Premise rejected; recommendation accepted** — D-1 |
| 8 | absolute-URL exceptions not enumerated | **Accepted — and it exposes a false criterion of mine** — D-7 |
| 9 | sourcemaps only noted, not guarded | **Accepted** — D-8 |
| 10 | `import.meta.env.` not asserted absent from output | **Accepted** — D-9 |
| 11 | negative control lacks a real platform value | **Accepted** — D-10 |
| 12 | OQ-1 will resurface in T14+ | **Accepted as a tracking note** — D-11 |

Ten accepted, two premises rejected, and one of the accepted findings (8) overturns an acceptance criterion I
wrote in phase 2.

## Decisions

### D-0 — Finding 1's premise is false: the suite runs on this host.

The review states that `vite-plugin-pwa` is missing and a direct `vitest` run fails with `ERR_MODULE_NOT_FOUND`.
That was true when T12 began; **T12 resolved it in phase 6** (`ab9ec5c9`) using `npx -y pnpm@12.14.0`, which runs
the exact version `packageManager` pins without a global install. Verified immediately before writing this:

```
vitest run src/app/shellBuildsAndMounts.test.tsx   →  Tests  7 passed (7)
vite build --outDir <tmp>                          →  built, 3 files emitted
```

**There is no blocker.** The only residual host fact is that bare `pnpm` is still not on PATH, which is why T07's
AC2 case fails — unrelated to this task and unchanged.

### D-1 — The baseline is a rule, not a literal.

Finding 7's premise is wrong — **375/377 was measured**, not assumed, at the end of T12 phase 12 (371/373 predates
the four cases added from Kimi's phase-11 review). But its recommendation is better practice than my literal, so
AC9 becomes:

> **AC9.** Immediately before T13's own cases are judged, `vitest run` is recorded. The only failures are the two
> known environmental T07 cases (`oxlint` summary line; `spawn pnpm ENOENT`). After T13, the total rises by exactly
> the number of cases T13 adds, the same two failures remain, and no other case changes state. No T01–T12 file is
> modified.

Phase 6 records the real numbers.

### D-2 — One scanner, used by both the real assertion and the negative control.

This is the review's strongest point: a negative control that exercises a different code path from the real
assertion proves only that the helper works.

```ts
type BundleScan = {
  files: string[];          // every emitted file, relative, sorted
  envKeys: string[];        // every VITE_-shaped and process.env-shaped key found
  secrets: string[];        // every secret-shaped or known-real-value hit, with its file
  absoluteUrls: string[];   // every absolute http(s) URL found
  sourcemaps: string[];     // every *.map file
};

function scanBundle(treeRoot: string): BundleScan;
```

The real cases call `scanBundle(<temp build dir>)`. The negative control writes synthetic files into a second temp
directory and calls **the same function**. Every prohibition is expressed as an assertion over the returned record,
so there is exactly one implementation of "what counts as a hit".

`scanBundle` must assert its own non-emptiness: **a scan over zero files satisfies every prohibition vacuously**,
which is the trap phase 2 named and T12's AC7 was caught by. It therefore throws if `files` is empty.

### D-3 — The allowlist parser, specified.

Kimi's five steps are adopted as written:

1. read `spec/frontend/design.md`;
2. find the first line matching `/Environment keys/i`;
3. within the paragraph under it, extract every backtick-quoted token matching `/^VITE_[A-Z0-9_]+$/`;
4. assert the set equals exactly
   `{VITE_OIDC_ISSUER, VITE_OIDC_CLIENT_ID, VITE_OIDC_REDIRECT_URI, VITE_API_BASE_PATH}`;
5. fail if the count is not four, if a token does not match the shape, or if the heading is gone.

Set equality, not permissive extraction. One note for the implementer: §4c's heading still reads
"**Environment keys — PROPOSED, pending sign-off (Q6)**" although **Q6 is closed** (`package.md` §11, resolved
2026-10-10). The parser keys on `/Environment keys/i`, so the stale wording does not matter; the staleness is
recorded for the owner, and no spec file is edited.

### D-4 — Strict on `process.env`, which costs nothing.

Option A (strict). Measured over the real bundle: **zero `process.env.*` occurrences, zero `VITE_*` occurrences,
zero `import.meta.env` occurrences.** The `process.env.NODE_ENV` the review worried about does not survive Vite's
production build here, so the broad pattern has no false positive to manage. Any surviving `process.env.*` is a
failure, and if one ever appears the author must configure its replacement or justify an exception in the test.

### D-5 — Discover the worker's filename; do not name it.

The recommendation is adopted. The premise is also settled: the emitted name is exactly `serviceWorker.js`, no
hash (measured; the build prints it, and T12 asserts `SERVICE_WORKER_URL` against the built file). The test walks
the tree, so the worker is included whatever it is called, and one case asserts the tree contains `index.html`,
exactly one `assets/*.js`, and exactly one worker file — so a rename is noticed rather than silently skipped.

### D-6 — Pin `envPrefix: ['VITE_']`.

`vite.config.ts` states the prefix explicitly, with a comment citing R58. This turns AC3 into an assertion on a
literal rather than a dependence on Vite's default, and removes the possibility that a future default change
broadens it silently. This is the **only** production file T13 modifies, and it is a hardening, not a fix.

### D-7 — AC8 was wrong as I wrote it, and is replaced.

Phase 2's AC8 said "no emitted file contains an absolute `http(s)` URL naming a backend host", with exceptions
"enumerated explicitly … if any are found". The build contains eight distinct absolute URLs, so the criterion as
phrased would have failed on first run, and the "if any" was doing more work than it looked like. Measured:

| URL | Source | Verdict |
|---|---|---|
| `http://localhost` (×2) | **react-router 8.4.0 internals** — a fallback origin (`let r = "http://localhost"; e && (r = e.location.origin …)`) and `new URL("http://localhost")` as a base for parsing relative hrefs | vendor placeholder, never a fetch target |
| `http://www.w3.org/2000/svg`, `…/1999/xlink`, `…/1998/Math/MathML`, `…/XML/1998/namespace` | React's SVG/MathML attribute handling | XML namespace identifiers, not addresses |
| `https://react.dev/errors/`, `https://reactrouter.com/en/main/routers/picking-a-router.` | framework error-message links | documentation |
| `https://bit.ly/wb-precache` | Workbox precaching warning message | documentation |

So AC8 becomes a **set-equality assertion against an enumerated, justified allowlist**:

> **AC8.** `scanBundle().absoluteUrls`, reduced to distinct origins-plus-path prefixes, equals
> `KNOWN_ABSOLUTE_URLS` exactly. Each entry carries a one-line justification in the test. No entry may name a
> service of this platform (`auth`, `payment`, `notification`, `crypto`) or any host other than those listed. A new
> URL — from a new dependency, a plugin, or our own code — fails the case and must be justified before it is added.

This is stronger than the original intent, not weaker: our own source is already forbidden absolute URLs by T01
`:131`, so the artifact-level check exists precisely to catch what **vendors and plugins** bring in, and set
equality is what notices that.

### D-8 — No sourcemaps.

`scanBundle().sourcemaps` must be empty. Passes today (no `build.sourcemap`, zero `*.map` files measured) and
becomes a hard failure the moment someone enables them — which is the leak path phase 2 only noted. Cheap, so it
is in.

### D-9 — No `import.meta.env.` in the output.

Asserted as a literal absence over every emitted file, closing the mechanism rather than only catching the key
name. Zero occurrences measured.

### D-10 — The negative control includes a real platform value.

`checky-local-only` (the local stack's `POSTGRES_PASSWORD`, `infra/local/compose.yaml:29`) goes into the synthetic
bundle and must be reported, alongside the private-key block, the JWT, the `Bearer ey…` value, the `client_secret`
literal, an unlisted `VITE_` key and a `process.env` key. This proves the scanner is not purely shape-driven.

`test` (the stack's `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`) is **excluded** from the real-value scan: it
matches ordinary words and would fire on any bundle containing the word "test".

### D-11 — OQ-1 is tracked, not resolved.

T13 does not resolve the conflict between T01's source rule and §4c's allowlist. The frozen position: **the
allowlist is a ceiling, not a plan.** T13 asserts R58 over the artifact and licenses no key; T01 stays as it is;
the four keys remain legal but unused. The owner must settle it **before any task consumes a §4c key**, which is
an auth-phase concern (T14+). Carried into phase 13 as an owner item.

## Acceptance criteria, as frozen

AC1–AC7 stand as phase 2 wrote them, with AC3 strengthened by D-6 (literal `envPrefix`), AC6 by D-3 (set-equality
parser) and AC7 by D-2 (one scanner) and D-10 (a real value in the control). **AC8 is replaced** by D-7. **AC9 is
replaced** by D-1. Two criteria are added:

- **AC10** — no `*.map` file is emitted (D-8).
- **AC11** — no emitted file contains the literal `import.meta.env.` (D-9).

## Open Questions

- **No blockers.** Finding 1's blocker does not exist (D-0).
- **OQ-1** (T01 vs §4c) — owner, before T14+ consumes a key. Tracked, not resolved.
- **OQ-2** (what "a secret value" means) — resolved as the review suggests: mechanism closure first (D-4, D-6, D-9,
  AC4), shape-matching second, both through one scanner (D-2) proven by a negative control that includes a real
  platform value (D-10).
- **Recorded for the owner, no action here:** `design.md` §4c's heading still says the environment keys are
  "PROPOSED, pending sign-off (Q6)" although Q6 closed on 2026-10-10. Cosmetic, and no task may edit `spec/`.
