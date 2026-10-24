# frontend · T13 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | One sentence | How it binds |
|---|---|---|
| **R58** (`requirements.md:206-207`) | When the production bundle is built, it contains no secret value and no environment key outside the public configuration list in `design.md` §4c. | The rule under test. Two distinct obligations — *no secret*, and *no key outside the allowlist* — which need different instruments: the second is decidable from a list, the first is not. |
| **R54** (`:197-201`) | A capability link exposes no PII and is not cached. | Not this task, but the same family: nothing identifying may leak into something downloadable. Out of scope (Q11). |

Unlike T12, this task **does** have a requirement, it is cited by `tasks.md:22`, and `package.md:161` maps the
named test to it correctly.

## Locked Decisions

| ID | What it constrains |
|---|---|
| **L10** (`design.md:46`) | The scoped decision: "Secrets stay out of the bundle. Only the public configuration keys listed in §4c are baked in (R58)." |
| **§4c allowlist** (`design.md:183-184`) | Exactly four keys: `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH` — "Public, baked at build time, never a secret". **Q6 closed 2026-10-10**, so this is final, not proposed. |
| **`agents.md:83`** | "Secrets and environment values never enter the bundle. Only public configuration is baked in." Note the wording is *stricter* than §4c's: "environment values never enter". |
| **`agents.md:20`** | No client secret in the bundle — the SPA is a public OIDC client with PKCE. The one secret category named explicitly anywhere. |
| **L3 / L16 / `agents.md:79-82`** | Tokens never in storage, logs or a URL; no PII persisted client-side. A token baked into a bundle would violate these as well as R58. |
| **L9** | Single origin; no per-service URLs. Bears on T13 because a baked absolute backend URL is the most likely non-secret leak, and T01 already forbids it in source. |
| **L13** | Vite is the bundler, so Vite's own inlining rules define the attack surface. |
| **L12** | Phase additivity — T13 must not weaken anything T01–T12 assert. |

## Files involved

**To read, not modify:**

| File | Why |
|---|---|
| `frontend/vite.config.ts` | `define` and `envPrefix` are the two switches that decide what gets inlined; today neither is set, so Vite's default `VITE_` prefix applies |
| `frontend/index.html` | Vite substitutes `%VITE_*%` here too, which a JS-only scan would miss |
| `frontend/src/**` | where a secret would be hardcoded |
| `frontend/src/app/shellBuildsAndMounts.test.tsx:127-133` | T01's source-level half of this rule |

**New:** `bundleContainsNoSecretEnvKeys` as a test file; placement is a phase-5 decision (`design.md` §6 has no
"build" package, and T01's precedent puts build tests in `src/app/`).

**Possibly modified, as a hardening rather than a fix:** `frontend/vite.config.ts`, if phase 2 decides `envPrefix`
should be pinned explicitly rather than left to the default.

## Dependencies

- **Build:** a real `vite build` into a temp directory (T01 and T12's pattern). No new package.
- **Config keys:** the four §4c keys, as an **allowlist**, read from the spec rather than restated.
- **Contracts:** none.
- **Platform facts the test can use:** `infra/local/compose.yaml` gives the frontend container exactly two
  environment variables, neither secret (`:211-213`); the stack's real secret-shaped values are
  `POSTGRES_PASSWORD: checky-local-only`, `AWS_ACCESS_KEY_ID: test`, `AWS_SECRET_ACCESS_KEY: test` (`:21-29`).

## Acceptance Criteria

| # | Criterion | Traces to |
|---|---|---|
| **AC1** | No emitted file — JS chunks, `index.html`, `serviceWorker.js`, and any other asset — contains an environment key outside the four-key §4c allowlist. Asserted over the real build output, every file, not only the main chunk. | R58, L10 |
| **AC2** | No emitted file contains a value the platform treats as a secret. Phase 2 must define this operationally; at minimum the distinctive real values from the local stack must be absent. | R58, `agents.md:83` |
| **AC3** | `vite.config.ts` has no `define` block, and `envPrefix` is not weakened. `envPrefix: ''` would inline **every** variable of the build machine — the catastrophic case, and a one-word edit. | L10, R58 |
| **AC4** | No `.env`, `.env.production`, `.env.local` or similar file exists under `frontend/`, since Vite would load it and inline its `VITE_` keys. | R58 |
| **AC5** | No emitted file contains a bearer token, JWT, private key block, or OAuth client secret — the categories `agents.md:20` and `:79-82` name. | `agents.md:20`, L3 |
| **AC6** | The allowlist in the test is the one in `design.md` §4c: a drift guard fails if the spec's list changes or the four keys are edited. | R58 (the list is the rule) |
| **AC7** | The rule is **default-deny**: the test fails on *any* `VITE_`-shaped or `process.env`-shaped key it finds that is not allowlisted, rather than searching for a fixed set of bad names. | pattern from T11 |
| **AC8** | No absolute `http(s)` URL naming a backend host appears in the bundle (L9's single origin, and the most likely non-secret leak). T01 asserts this in source; AC8 asserts it in the artifact. | L9, R58 |
| **AC9** | The suite is green against the recorded baseline (**375/377**), and no T01–T12 file is modified. | L12 |

## Tests required

**Named test (`package.md:161`):** `bundleContainsNoSecretEnvKeys`.

Boundary tests implied:

1. **Every emitted file**, found by walking the output directory, not an assumed list — so a new asset type is
   covered automatically (AC1, AC2, AC5, AC8).
2. **Default-deny scan**: extract every `VITE_[A-Z0-9_]*` and `process.env.X` occurrence from the output and
   assert the set difference against the allowlist is empty (AC1, AC7).
3. **Negative control**: the scanner must be shown to *find* a key when one is present, otherwise a green result
   proves only that the scanner found nothing. This is T12's AC7 lesson — feed the matcher a synthetic bundle
   containing `VITE_SOMETHING_SECRET` and assert it is reported.
4. **Real secret values** from the local stack asserted absent, choosing only distinctive ones (`checky-local-only`
   yes; `test` no, since it matches ordinary words) (AC2).
5. **Secret-shape patterns**: `-----BEGIN … PRIVATE KEY-----`, `client_secret`, `Bearer ey…`, a JWT's three-part
   shape, AWS `AKIA`-prefixed ids (AC5).
6. **Config assertions**: no `define`, `envPrefix` intact, with a drift guard on the four-key list (AC3, AC6).
7. **No `.env*` file** under `frontend/` (AC4).
8. **`index.html` and `serviceWorker.js` specifically**, since both are outside `assets/` and a JS-only glob would
   skip them (AC1).

## Open Questions

- **OQ-1 — T01's source rule is stricter than R58, and T13 owns the rule. Needs an answer; does not block
  phases 2–5.**
  `design.md` §4c (Q6, **closed**) says the four `VITE_*` keys are public and "baked at build time". R58 permits
  them. But **T01 `:130` asserts no production source contains `import.meta` + `.env` at all**, over all of
  `src/**`. So the codebase forbids reading even an allowlisted key.

  Today there is no conflict: nothing reads a key, because the OIDC configuration is derived at runtime instead.
  The conflict arrives the moment an auth task (T14+) wants `VITE_OIDC_ISSUER` — **T01 will fail on a change R58
  expressly permits**, and whoever hits it will not know which rule is authoritative.

  Three readings, for the owner:
  1. *(recommended, and what T13 will assert)* **the allowlist is the ceiling, not a plan** — runtime derivation
     stays, T01's stricter source rule stands, and T13 asserts R58 over the artifact without licensing any key.
     The four keys remain legal but unused.
  2. T01's assertion is relaxed to permit the four allowlisted keys, matching §4c's "baked at build time" literally.
  3. `design.md` §4c is amended to record that the keys are not used. (Requires a spec change, which no task may
     make.)

  Reading 1 keeps both documents true and leaves the decision reversible; readings 2 and 3 are the owner's to make.

- **OQ-2 — "a secret value" has no definition in the spec, so T13 must supply the test's definition.** `R58`,
  `L10` and `agents.md:83` all assert it without making it decidable. A denylist of patterns catches only the
  shapes someone imagined. Phase 2 will propose the **mechanism-based** approach instead — enumerate the ways Vite
  can inline a value and close each — with pattern-matching as a secondary net. Not a blocker; it is this task's
  design work, but it is worth the owner knowing that "no secret" is being operationalised rather than read off
  the spec.

- **Not blockers.** No `package.md` §11 question gates this task; Q6, the only one that touches it, is closed. No
  contract is involved. The two known environmental T07 failures are unrelated.

## References

`tasks.md:22`; `requirements.md:206-207` (R58); `design.md:46` (L10), `:183-184` (§4c), `:196-220` (§6);
`agents.md:20`, `:79-83`; `package.md:161` (§8), §11 Q6 (closed);
`frontend/src/app/shellBuildsAndMounts.test.tsx:127-133`; `frontend/vite.config.ts`;
`infra/local/compose.yaml:21-29`, `:206-213`.
