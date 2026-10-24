# frontend · T13 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` — **the only specification phases 5–13 may use** |

## Task

`tasks.md:22`, verbatim:

> **No secret in the bundle.** The production bundle contains no secret value and no non-public environment key.
> Verifies `bundleContainsNoSecretEnvKeys`. Cites R58, L10.

## Purpose

The bundle is the one artifact the SPA hands to anyone who asks. Everything else in this package controls what the
app *does*; this task controls what it *contains*. A baked client secret makes the public PKCE client a confidential
one that has published its credential (`agents.md:20`); a baked backend URL breaks the single origin (L9); a baked
environment value leaks the shape of the deployment to every visitor.

The task is the last of Phase 1 — Foundation, and it is the only one whose subject is the build output itself.

**It is a verification task.** Its deliverable is a test that would fail on a real leak, and the honest measure of
success is not that the test passes — today it must, since nothing is baked — but that **the scanner is shown to
catch a leak when one exists**. T12's AC7 established the precedent: a guard against a condition the build cannot
currently produce is only credible when the condition is fed to it directly.

## Scope

**In**

- One test file verifying the real production build: every emitted file, not a sampled one.
- A **default-deny** environment-key scan: any `VITE_`-shaped or `process.env`-shaped key found in the output that
  is not in `design.md` §4c's four-key allowlist is a failure.
- A **mechanism-based** secret check, closing each route by which Vite can inline a value, plus pattern matching as
  a secondary net.
- A negative control proving the scanner reports a key and a secret when they are present.
- A drift guard tying the allowlist to `design.md` §4c.
- Pinning `envPrefix` in `vite.config.ts` **if** phase 5 judges the default too implicit — a hardening, not a fix.

**Out**

- **Resolving OQ-1** (whether T01's stricter source rule or §4c's allowlist governs). This task asserts R58 over
  the artifact and licenses no key; the owner decides the rest.
- **Changing T01's source-level assertion**, or any other T01–T12 file.
- Sourcemap contents — none are emitted (no `build.sourcemap`). If they are ever enabled they become the obvious
  leak path, and that is a note for the owner, not work here.
- Secret *scanning of the repository* (git history, infra, services). The subject is the frontend bundle.
- Any runtime behaviour, route, screen, or API change.
- CI wiring. The test runs in the existing suite.

## Business Rules

| ID | Rule |
|---|---|
| **R58** | The production bundle contains no secret value and no environment key outside `design.md` §4c's list. Two obligations: the key half is decidable from a list, the secret half is not — hence the mechanism-based approach. |
| **`agents.md:83`** (LOCKED) | "Secrets and environment values never enter the bundle. Only public configuration is baked in." |
| **`agents.md:20`** (LOCKED) | No client secret in the bundle; the SPA is a public client using PKCE. The one secret category the spec names. |

## Locked Decisions

`L10` (scoped), the **§4c four-key allowlist** (Q6 closed, so final): `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
`VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH`; `L9` (single origin — no absolute backend URL in the artifact);
`L3`/`L16` (no token anywhere it could persist); `L13` (Vite, so Vite's inlining rules are the surface);
`L12` (additivity — nothing T01–T12 asserts may be weakened).

## Dependencies

- A real `vite build` into a `mkdtemp` directory — T01's and T12's pattern. **No new package.**
- `design.md` §4c, parsed for the allowlist (derive, never restate).
- `infra/local/compose.yaml` for the platform's real values, of which only distinctive ones are usable:
  `checky-local-only` yes; `test` no, because it matches ordinary words and would produce false positives.
- Node's `fs` to walk the output tree.

## Inputs

The emitted build tree; `vite.config.ts` as text; `design.md` §4c as text; the presence or absence of `.env*`
files. No runtime input.

## Outputs

None at runtime. The deliverable is the test and its verdict.

## State Changes

None. No storage, no network, no persistence. The test writes only to a temp directory it removes.

## Files to Create

| Path | Contents |
|---|---|
| `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts` *(placement to be confirmed in phase 5; `src/app/` is T01's precedent, as `design.md` §6 has no build package)* | the named test and its boundary cases |

## Files to Modify

| Path | Change |
|---|---|
| `frontend/vite.config.ts` | **only if** phase 5 decides to pin `envPrefix: ['VITE_']` explicitly. A comment-only change otherwise. Nothing else in the config is touched. |

Expected to be modified: **nothing else.** If the implementation finds it must edit another file, that is a finding
to record, not a step to take quietly — the T12 experience (a third T01 edit nobody predicted) is the reason this
is stated.

## Files NOT to Modify

- `frontend/src/app/shellBuildsAndMounts.test.tsx` — T01, including its source-level `import.meta.env` assertion,
  which OQ-1 is about and which this task does not resolve.
- Every other T01–T12 source and test file, `frontend/package.json`, `.oxlintrc.json`, `pnpm-lock.yaml`.
- Everything under `spec/`, `contracts/`, `services/`, `infra/`, `libs/`.

## Acceptance Criteria

| # | Criterion |
|---|---|
| **AC1** | Over a real production build, **every** emitted file — JS chunks, `index.html`, `serviceWorker.js`, CSS, any asset — contains no environment key outside the four-key allowlist. Files are discovered by walking the output tree, so a new asset type is covered without an edit. |
| **AC2** | No emitted file contains a distinctive real secret value from the platform's own configuration (`checky-local-only`), nor a value matching the secret shapes of AC5. |
| **AC3** | `vite.config.ts` declares no `define` block, and `envPrefix` is either absent (Vite's `VITE_` default) or exactly `['VITE_']`. An empty or broadened prefix fails. |
| **AC4** | No `.env`, `.env.local`, `.env.production` or `.env.*` file exists under `frontend/`. |
| **AC5** | No emitted file contains a private-key block (`-----BEGIN … PRIVATE KEY-----`), the literal `client_secret`, a `Bearer ey…` header value, a three-part JWT, or an AWS `AKIA`-prefixed access-key id. |
| **AC6** | The allowlist used by the test is `design.md` §4c's: a drift guard fails if the spec's four keys change, are reordered into a different set, or the section stops declaring them. |
| **AC7** | **Default-deny, and proven.** The scan reports any unexpected key rather than searching for known-bad names, and a negative control feeds it a synthetic bundle containing an unlisted `VITE_` key, a `process.env` key and a secret shape, asserting each is reported. Without this, a green AC1 proves only that the scanner found nothing. |
| **AC8** | No emitted file contains an absolute `http(s)` URL naming a backend host (L9). The known-good exceptions, if any are found in the real output, are enumerated explicitly with a reason rather than excluded by a loose pattern. |
| **AC9** | Suite green against the recorded baseline (**375/377**, both failures the known environmental T07 ones), and no T01–T12 file modified. |

## Required Tests

**Named:** `bundleContainsNoSecretEnvKeys` (`package.md:161`).

1. Walk the output tree; assert it is non-empty and that `index.html` and `serviceWorker.js` are both included —
   both sit outside `assets/`, so a JS-only glob would silently skip them (AC1).
2. Extract every `VITE_[A-Z0-9_]*` and `process.env.<KEY>` occurrence; assert the difference against the allowlist
   is empty (AC1, AC7).
3. Negative control over a synthetic bundle: an unlisted `VITE_` key, a `process.env` key, a private-key block and
   a JWT are each reported (AC7).
4. Real platform values absent; distinctive ones only (AC2).
5. Secret-shape patterns absent (AC5).
6. `vite.config.ts`: no `define`, `envPrefix` not weakened (AC3).
7. No `.env*` under `frontend/` (AC4).
8. Allowlist drift guard against `design.md` §4c (AC6).
9. Absolute-URL scan with any exception enumerated and justified (AC8).

Mutation pass (phase 10), carrying the standing lessons: commit tests before mutating; mutate the **guarded
source** (here: `vite.config.ts`, `design.md` §4c) rather than an assertion; and for each "cannot happen today"
guard, feed the condition to the matcher directly — which AC7 makes a first-class criterion rather than an
afterthought.

## Constraints

- **Security.** This is the task whose failure mode is a published credential. Prefer a false positive to a missed
  leak: where a pattern is ambiguous, fail and make the author justify an exception in the test, rather than
  loosening the pattern.
- **Default-deny over denylisting.** The key half must be a set difference against an allowlist. Only the secret
  half, which has no allowlist, falls back to patterns — and its real strength is AC3/AC4 closing the mechanisms,
  not the patterns.
- **Module boundaries.** A test-only addition. It imports no production module and no production module learns of
  it. It must not export helpers that production code could come to depend on.
- **Performance.** This is the **third** test file to run a full `vite build` (T01, T12, T13). One build per file,
  in a `beforeAll`, reused across cases — T12's shape. ~2s here; acceptable, and phase 5 states it explicitly.
- **Null handling.** The output tree must be asserted non-empty before any "contains no X" assertion runs: a scan
  over zero files passes every prohibition vacuously. This is the specific trap this task is most prone to, and
  the one T12's AC7 was caught by.
- **No thread-safety, transaction or money concerns.**

## Open Questions

Two, neither blocking phases 3–6.

- **OQ-1 — T01's source rule is stricter than R58's allowlist, and this task owns the rule.** §4c (Q6, closed)
  declares four public keys "baked at build time"; T01 `:130` forbids any source from naming `import.meta.env` at
  all. No conflict today, because the OIDC configuration is derived at runtime and nothing reads a key — but an
  auth task (T14+) that wants `VITE_OIDC_ISSUER` will fail T01 on a change R58 permits. **This brief assumes the
  allowlist is a ceiling, not a plan**: T13 asserts R58 over the artifact, licenses no key, and leaves T01
  untouched. The owner may instead relax T01 to permit the four keys, or amend §4c to record that they are unused.
- **OQ-2 — "a secret value" is asserted by the spec but never made decidable**, so this task supplies the
  definition: close the mechanisms by which Vite can inline a value (AC3, AC4, AC7), and treat shape-matching
  (AC5) as a secondary net rather than the primary instrument. Recorded so the owner knows the definition is this
  task's construction, not a reading of the spec.
