# frontend · T13 · Phase 10 — Test Generation, Mutation & Coverage

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

## Where the suite stands

| | |
|---|---|
| `bundleContainsNoSecretEnvKeys.test.ts` | **32 cases** (27 in phase 6, +4 in phase 9, +1 here) |
| `vitest run` (whole suite) | **407/409** — 377 before the task, +32; the same two environmental T07 failures |
| `tsc --noEmit`, `oxlint` | clean |

## One case added before mutating

The platform-host rule was asserted only as an **absence** over the real bundle, where no such URL exists — so it
could never fail, and S2's broadened host-label match was entirely unexercised. The same expression is now applied
to the negative control, which contains `http://auth:8080/oauth2/token`, and to a labelled form
(`https://auth.checky.example/...`) that no bundle produces. The rule is now known to work rather than known to
pass.

## The mutation pass: 14 mutants, 13 caught

Tests committed before the first mutant (T09's lesson). Each applied to real source, run, reverted with
`git checkout --`.

| # | Mutant | File | Result | Caught by |
|---|---|---|---|---|
| **M1** | restore the URL path class that stopped at `?` | test | **CAUGHT** | the S1 regression case — and only it |
| **M2** | stop scanning for the platform's real values | test | **CAUGHT** (3) | both positive-control cases and the negative control |
| **M3** | `envPrefix: ['']` | `vite.config.ts` | **CAUGHT** — *by Vite itself*; see below | the build fails |
| **M4** | remove the `envPrefix` pin | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |
| **M5** | add a `define` block inlining `http://auth:8080` | `vite.config.ts` | **CAUGHT** | the "no define block" case |
| **M6** | let the scanner accept an empty tree | test | **CAUGHT** | the empty-tree case |
| **M7** | add a fifth key to `design.md` §4c | **spec** | **CAUGHT** (2) | both allowlist cases |
| **M8** | remove a key from `design.md` §4c | **spec** | **CAUGHT** (2) | both allowlist cases |
| **M9** | inherit `NODE_ENV=test`, so a dev bundle is built | test | **CAUGHT** (3) | the production-build case + URL set equality |
| **M10** | scan only `assets/`, skipping `index.html` and the worker | test | **CAUGHT** (4) | four cases, incl. "includes files outside assets/" |
| **M11** | emit sourcemaps | `vite.config.ts` | **CAUGHT** (7) | the sourcemap case and six others |
| **M12** | read files as `utf8` instead of `latin1` | test | **SURVIVED** | — (see below) |
| **M13** | broaden the prefix to `['VITE_', 'POSTGRES_']` | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |
| **M14** | broaden the prefix to `['V']` | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |

`spec/frontend/design.md` was **byte-identical** after M7 and M8 were reverted (T08's precedent).

## M3 corrects the frozen brief: Vite refuses the catastrophic edit itself

The frozen brief (D-6) and phase 2 both called `envPrefix: ''` "the catastrophic case, and a one-word edit". It is
not reachable. Vite 8.3.5 rejects it at config resolution:

```
Error: envPrefix option contains value '', which could lead unexpected exposure of sensitive information.
  at resolveEnvPrefix (vite/dist/node/chunks/node.js:6134)
```

So M3 is caught, but by the bundler rather than by this task. That made the mutant uninformative about **our**
guard, so M13 and M14 were added with values Vite *does* accept — `['VITE_', 'POSTGRES_']` and `['V']`. Both are
caught by the `envPrefix` case. **That is where the real risk lives**, and it is now the case's demonstrated
reach rather than its claimed one.

## M11 shows concretely why the sourcemap guard matters

Enabling `build.sourcemap` failed **seven** cases, not just the sourcemap one — including "contains no environment
key at all" and the URL set equality. Sourcemaps embed original source text, so dependency sources referencing
`process.env` and documentation URLs reappear in the artifact. The guard the frozen brief accepted from Kimi's
finding 9 as "cheap, so it is in" turns out to be load-bearing: a sourcemap is not a side file, it is a second
copy of the source shipped alongside the bundle.

## M12 survived, and why that is the honest result

Reading files as `utf8` instead of byte-preserving `latin1` (S5's fix) changes nothing today, because **every
emitted file is text**.

Following T11's precedent, this is reported as **survived** rather than dressed up.

**Corrected in phase 12 (2026-10-25).** The reason given here for keeping the change — that it "protects against a
future binary asset swallowing an ASCII secret behind replacement characters" — is **wrong**. Kimi's phase-11 gap 1
proposed exactly the case that would prove it, the case was added, and **M12 survived a second time**. Measuring the
five byte arrangements directly showed why: Node's UTF-8 decoder resynchronises, so a contiguous ASCII needle
survives either decode, and where detection does fail `latin1` fails identically. The corrected statement is in
phase 12 and in the scanner's own comment; this section is left in place with the error marked rather than
rewritten, because the surviving mutant is what exposed it.

## What each acceptance criterion rests on

| AC | Pinned by | Mutant proof |
|---|---|---|
| AC1 no key outside the allowlist | key scan over every emitted file, default-deny | M10 |
| AC2 no secret value | shape patterns + the platform's real values | M2 |
| AC3 mechanisms closed | `envPrefix` pinned, no `define` | M4, M5, M13, M14 |
| AC4 no `.env*` | directory listing of `frontend/` | — (see coverage) |
| AC5 secret shapes | five patterns, all proven reportable by the control | M2 |
| AC6 allowlist is the spec's | five-step parser, set equality | **M7, M8** |
| AC7 default-deny, proven | one scanner; ten reportable categories; the positive control | M1, M2, M6 |
| AC8 absolute URLs by set equality | eight justified entries; no query string; platform hosts | M1, M9, M10 |
| AC10 no sourcemap | `scanBundle().sourcemaps` | **M11** |
| AC11 no `import.meta.env.` | literal absence over every file | M11 (indirectly) |
| AC9 baseline | 377 → 409, delta exactly 32 | — |

## Coverage, honestly

- **AC4 has no mutant.** Creating a `.env` file to prove the case fires would mean committing one, which is the
  thing being prohibited; and a temp-file version would not exercise the real assertion, which reads
  `frontend/`'s own directory listing. Stated rather than faked.
- **M12 survived** (above).
- **The positive control's finding stands as the task's main limitation**, and it is a limitation of R58 as a
  testable rule, not of this implementation: Vite inlines a key's **value**, not its name, so a key-name scan
  cannot see that class of leak. The real-value scan caught the demonstration only because `checky-local-only` is
  a value this repository knows. **For a genuine production secret, no scan of the artifact could find it** —
  which is why the mechanism closure (nothing reads the env object at all, per T01; `envPrefix` pinned; no
  `define`; no `.env`) is the actual defence and AC3 is the criterion that matters most.
- **The absolute-URL allowlist is tied to today's dependency set.** A dependency upgrade that changes a
  documentation URL will fail AC8 and need a human to re-justify the entry. That is the intended cost of set
  equality over a loose pattern.
- **No binary-asset, multi-chunk or code-split case exists**, because the build produces a single chunk. A future
  `manualChunks` or lazy route would add files; the scan walks the tree, so they are covered automatically, but
  the *shape* assertion ("exactly one `assets/*.js`") would need updating and is deliberately strict so that it
  fails loudly rather than silently widening.

## Carried into phase 12

Record: 14 mutants, 13 caught; M3's correction of the frozen brief's "catastrophic one-word edit" claim; M11's
demonstration that the sourcemap guard is load-bearing; M12 survived as a forward-looking hardening; and the
positive control's finding about values-not-names, which bounds what this task can promise.
