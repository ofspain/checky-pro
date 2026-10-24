# frontend · T13 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

No code written in this phase. T13 is the last task of **Phase 1 — Foundation**; `tasks.md:23` opens the auth
section immediately after it.

## 1. Architecture summary

Unchanged from T12's reading, and the parts that matter for this task:

- **Modules.** `src/app/` (shell, router), `src/api/` (generated client, 401/429, cross-tab renewal), `src/i18n/`,
  `src/pwa/` (worker policy + the worker itself, registered as of T12), `src/main.tsx` (mount + register).
- **Build.** One `vite.config.ts` for dev, build and test. `base: '/app/'`, `plugins: [react(), VitePWA(...)]`.
  Output: `index.html`, `assets/index-<hash>.js`, `serviceWorker.js`.
- **Persistence / events / security.** No server-side anything. The security-relevant surface for T13 is entirely
  build-time: what ends up inside the files the browser downloads.
- **Configuration today.** There is **no `.env` file anywhere in `frontend/`**, no `define` block in
  `vite.config.ts`, and **no source names `VITE_`** — verified by grep. The SPA currently bakes in nothing.

## 2. Existing code this task touches

**Nothing is modified.** T13 is a verification task: it adds one test and, if the design calls for it, hardens
`vite.config.ts`. Files it must read:

| File | Why |
|---|---|
| `frontend/vite.config.ts` | the only place a value can be deliberately inlined (`define`, `envPrefix`) |
| `frontend/src/**` | the only place a value can be accidentally written |
| `frontend/index.html` | Vite substitutes `%VITE_*%` placeholders here too |
| `frontend/src/app/shellBuildsAndMounts.test.tsx` | **already asserts part of this rule**, at source level — see §5 |
| `frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts` | T12's build harness, the closest prior art |
| `infra/local/compose.yaml` | where the platform's real secret-shaped values live |

**New:** `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts` (or `src/build/`; placement is a phase-2/5
question — `design.md` §6's package map has no home for a build-level test).

## 3. Established patterns to follow

- **Assert against the real artifact.** T01 and T12 both run a real `vite build` into a `mkdtemp` directory and
  read what came out, rather than trusting config. T13's whole subject is the artifact, so this is the pattern.
- **Derive, never restate.** Every list in this package is derived from one source and guarded against spec drift
  (T09's eleven paths, T11's twelve prefixes, T12's two sentences). T13's allowlist of public env keys should come
  from `design.md` §4c the same way, with a drift guard.
- **Default-deny.** T11 states the cacheable set and negates it, so an unenumerated path is refused. The analogous
  shape here is to assert *what may appear* rather than enumerate secret patterns to look for — a denylist of
  secret shapes can only ever catch the shapes someone thought of.
- **Spec citation in-source**, acceptance criteria in test names (`(AC3)`), `it.each` tables over cases.
- **Mutation testing as a deliverable** (phase 10), with the standing lessons: commit tests first; mutate the
  guarded source, not the guard; and a guard against a condition the build cannot currently produce can still be
  exercised by feeding the condition to the matcher directly (T12's AC7 lesson).
- `expect.getState().testPath` locates the repo root, because jsdom gives `import.meta.url` a non-file URL.

## 4. Testing conventions

Vitest + jsdom; file named exactly after the test in `package.md` §8 → `bundleContainsNoSecretEnvKeys`. No
Testcontainers, no `Clock`, no ArchUnit. The comparable cases are T01's real build, T07's real `oxlint` subprocess
and T12's real build + emitted-manifest parse.

**One practical note:** this will be the **third** test file that runs a full `vite build` (T01, T12, T13). Each
build is ~2s here, so the cost is acceptable, but phase 5 should decide whether to build once per file or reuse a
`beforeAll` as T12 does.

## 5. Known gaps / unknowns

**A latent contradiction between T01 and the specification — the central thing for phases 1–3.**

- `design.md` §4c declares four public environment keys — `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
  `VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH` — "Public, baked at build time, never a secret". **Q6 is closed**
  (`package.md` §11: *"Resolved (2026-10-10): confirmed as proposed… final. Closed."*), so that allowlist is
  settled spec.
- R58 therefore permits those four keys in the bundle and forbids anything else.
- But **T01 `:130` asserts that no production source contains `import.meta` + `.env` at all**, over all of
  `src/**`. That is strictly stricter than R58: it forbids reading even an allowlisted key.

Today the two do not collide, because nothing reads any key — the issuer and friends are derived at runtime
instead. If a later auth task ever needs a build-time key, **T01's assertion will fail on a change R58 expressly
permits.** T13 is the task that owns this rule, so it should state which of the two is authoritative rather than
leave a trap for T14+. I cannot resolve it alone: it is a question about intent, logged for phase 1.

**What "a secret value" means is not defined anywhere.** `requirements.md:206` says "no secret value", `L10` says
"Only the public configuration keys listed in §4c are baked in", and `agents.md:83` says "Secrets and environment
values never enter the bundle. Only public configuration is baked in." None gives a test. Phase 2 must define it
operationally. What the repository offers to make it concrete:

- `infra/local/compose.yaml` holds the platform's real local values — `POSTGRES_PASSWORD: checky-local-only`,
  `AWS_ACCESS_KEY_ID: test`, `AWS_SECRET_ACCESS_KEY: test`. `checky-local-only` is distinctive enough to assert on;
  `test` is not, and asserting on it would produce a false positive against any word containing it.
- The **frontend container is given no secrets at all** (`compose.yaml:211-213`: only `CHOKIDAR_USEPOLLING` and
  `COREPACK_ENABLE_DOWNLOAD_PROMPT`), which is itself worth asserting as a property of the stack.

**The mechanisms by which a secret could actually enter a Vite bundle** — this is the part worth getting right,
and it is a design question for phase 2, not a gap:

1. a `VITE_`-prefixed variable holding a secret (Vite inlines these by design);
2. **`envPrefix` weakened** — `envPrefix: ''` would inline *every* environment variable of the build machine, which
   is the catastrophic case and a one-word edit;
3. a `define:` block in `vite.config.ts`, which inlines arbitrary values;
4. a committed `.env`, `.env.production` or `.env.local` that Vite loads;
5. a secret hardcoded in a source file;
6. a secret inside an imported asset or JSON module;
7. whole-object use of the env record (`{...import.meta.env}`), which pulls in every allowlisted key at once.

`vite.config.ts` today has no `define` and no `envPrefix`, so the default (`VITE_`) applies. **I do not know**
whether the author wants `envPrefix` pinned explicitly; that is a design choice for phase 2.

**Genuine unknowns — I do not know:**

- Where the test file belongs. `design.md` §6's package map has `app/`, `auth/`, `api/`, `features/`, `i18n/`,
  `pwa/` — none is "the build". T01's build test lives in `src/app/`, which is the only precedent.
- Whether `serviceWorker.js` and `index.html` are in scope as "the bundle", or only the JS chunks. R58 says "the
  production bundle"; the safe reading is every emitted file, and T12 showed the worker is a separate build with
  its own pipeline.
- Whether sourcemaps are in scope. None are emitted today (no `build.sourcemap`), but a sourcemap embeds original
  source text and would be the obvious leak path if ever enabled.

## References

`spec/frontend/tasks.md:22`; `requirements.md:206-207` (R58); `design.md:46` (L10), `:183-184` (§4c env keys),
`:196-220` (§6); `agents.md:20`, `:83`; `package.md:161` (§8 mapping), `§11 Q6` (closed);
`frontend/vite.config.ts`, `frontend/index.html`, `frontend/src/app/shellBuildsAndMounts.test.tsx:127-133`,
`frontend/src/pwa/serviceWorkerUpdateWaitsForReload.test.ts:530-560`, `infra/local/compose.yaml:206-213`.
