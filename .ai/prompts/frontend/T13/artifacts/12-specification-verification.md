# frontend · T13 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T13 — No secret in the bundle |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact, the implementation, `spec/frontend/*` |
| **Produces** | `artifacts/12-specification-verification.md` |

## Phase 11 gaps (Kimi), resolved before verification

| Gap | Decision | Change |
|---|---|---|
| 1 `latin1` hardening has no exercised path | **ACCEPTED, and its premise then falsified by the result** | The suggested case was added at `:368` — a synthetic `assets/font.woff2` carrying `checky-local-only` between non-UTF-8 bytes — and **M12 still survived**. The case is worth keeping: it proves the scanner reads binary files rather than skipping or throwing on them. But it does not catch M12, and no case can. See "M12, the second survival" below; this is the one gap whose stated rationale turned out to be wrong, and the scanner's comment and artifacts 9 and 10 are corrected rather than left standing. |
| 2 AC4 has no mutation break | **ACCEPTED — the sharpest gap in the review** | `findEnvFiles(dir)` extracted; `:533` unit-tests it against a temp directory holding `.env`, `.env.local`, `.env.production` and two non-matching files, while `:529` keeps the real invariant over `frontend/`. **M15** was written for it and is caught. |
| 3 Parser not tested against a multi-line section | **ACCEPTED** | `allowlistFromSpecText(text)` extracted; `:475` feeds it a section with blank lines between bullet items and asserts all four keys survive. `:498` adds the closed-fail case (heading gone → empty, so the equality case fails rather than passes). **M16** caught. |
| 4 No secret inside a sourcemap body | **ACCEPTED** | `:363` now puts a `client_secret` literal inside the synthetic `.map` body and asserts it appears under **both** `sourcemaps` and `secrets`. **M17** (skip `.map` bodies when scanning for secrets) caught. |
| 5 No assertion that the `VITE_*` scrubbing happens | **ACCEPTED** | `:234` asserts the build env handed to the child carries no `VITE_`-prefixed key, and no `NODE_ENV`/`VITEST` inherited from Vitest. Cheap, as the review proposed, and it pins the setup that M9 showed is load-bearing. |
| 6 Production-mode check covers only the main chunk | **REJECTED — already closed in phase 9, verified in source** | `:249` iterates **every** emitted `.js` file, `serviceWorker.js` included, and asserts the production markers over each. The review is reading the phase-10 manifest's summary of the phase-6 case, not the phase-9 code. Checked at `file:line` before rejecting, per standing practice. |
| 7 The value-not-name limitation is in prose, not in the code | **ACCEPTED** | The limitation is now stated in the file header and again at `ENV_KEY_PATTERNS`, pointing at the mechanism-closure cases (`:516`, `:524`, `:529`, `:454`) as the real defence. `:421` makes the same point executable: the positive control proves Vite inlines the key's **value**, so a scan for key names cannot find a leaked secret. |

Six accepted, one rejected on verified evidence, and one accepted gap whose own premise the result then disproved.

## Mutation runs — 17 mutants, 16 caught

Fourteen from phase 10, three added for Kimi's gaps. Tests committed before the first mutant; each applied to real
source, run, reverted with `git checkout --`.

| # | Mutant | File | Result | Caught by |
|---|---|---|---|---|
| M1 | restore the URL path class that stopped at `?` | test | **CAUGHT** | the S1 regression case, and only it |
| M2 | stop scanning for the platform's real values | test | **CAUGHT** (3) | both positive-control cases + the negative control |
| M3 | `envPrefix: ['']` | `vite.config.ts` | **CAUGHT** — by Vite itself | the build fails; see phase 10 |
| M4 | remove the `envPrefix` pin | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |
| M5 | add a `define` inlining `http://auth:8080` | `vite.config.ts` | **CAUGHT** | the "no define block" case |
| M6 | let the scanner accept an empty tree | test | **CAUGHT** | the empty-tree case |
| M7 | add a fifth key to `design.md` §4c | **spec** | **CAUGHT** (2) | both allowlist cases |
| M8 | remove a key from `design.md` §4c | **spec** | **CAUGHT** (2) | both allowlist cases |
| M9 | inherit `NODE_ENV=test`, so a dev bundle is built | test | **CAUGHT** (3) | the production-build case + URL set equality |
| M10 | scan only `assets/` | test | **CAUGHT** (4) | four, incl. "includes files outside assets/" |
| M11 | emit sourcemaps | `vite.config.ts` | **CAUGHT** (7) | the sourcemap case and six others |
| M12 | read files as `utf8` instead of `latin1` | test | **SURVIVED** (twice) | — see below |
| M13 | broaden the prefix to `['VITE_', 'POSTGRES_']` | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |
| M14 | broaden the prefix to `['V']` | `vite.config.ts` | **CAUGHT** | the `envPrefix` case |
| **M15** | `findEnvFiles` matches only an exact `.env` | test | **CAUGHT** | `:533` (`.env.local`, `.env.production` missed) |
| **M16** | the allowlist parser stops at the first blank line | test | **CAUGHT** | `:475` (two of four keys lost) |
| **M17** | skip `.map` bodies when scanning for secrets | test | **CAUGHT** | `:363` |

`spec/frontend/design.md` was **byte-identical** after M7 and M8 were reverted (T08's precedent).

## M12, the second survival — and a correction to my own phase 9

M12 survived in phase 10. Kimi's gap 1 proposed the case that would catch it, the case was added (`:368`: a
synthetic `assets/font.woff2` holding `checky-local-only` between non-UTF-8 bytes), and **M12 survived again**.
That is a result, not a failure to try, so I measured the mechanism directly — five byte arrangements of an ASCII
needle, read back under each decode:

| Arrangement | `utf8` finds it | `latin1` finds it |
|---|---|---|
| adjacent invalid bytes | **yes** | yes |
| truncated 4-byte lead before it | **yes** | yes |
| lead byte immediately before it | **yes** | yes |
| needle split by a lead byte | **no** | **no** |
| needle stored as UTF-16LE | **no** | **no** |

Node's UTF-8 decoder **resynchronises**: an invalid byte becomes one replacement character and the following ASCII
bytes decode normally, so a contiguous ASCII needle survives either decode. And in the two arrangements where
detection genuinely fails, `latin1` fails identically.

So the justification I wrote for S5 in phase 9 — "a byte-preserving decode means a future binary asset cannot
swallow an ASCII secret behind replacement characters" — **is false**. `latin1` is still the right call: it is
byte-exact, allocates no replacement characters, and cannot be wrong. But it **removes no risk that exists** for
ASCII patterns, and M12 is unkillable for that reason rather than for want of a binary asset. The scanner's comment
(`:135-139`), artifact 9's S5 bullet and artifact 10's M12 section are all corrected to say this. The honest
residual limit — **a secret stored UTF-16, or interleaved with stray lead bytes, is invisible to any text scan** —
is now stated where a maintainer will read it instead of being implied to be solved.

This is the second time in this task that a claim of mine was settled by measurement against my own argument
(the first was D-7's absolute-URL criterion). Both are recorded rather than quietly edited.

## Traceability matrix

| Requirement / decision | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R58** (`requirements.md:206-207`) no secret value, no non-public key in the bundle | Yes | the scan is over the real emitted tree | `:442`, `:447`, `:508` | No | No |
| **L10** (`design.md:46`) only §4c's public keys are baked in | Yes | allowlist derived from the spec, set equality | `:465`, `:469`, `:475`, `:498` | No | No |
| **`agents.md:83`** secrets and environment values never enter the bundle | Yes | mechanism closure, not pattern matching | `:516`, `:524`, `:529`, `:454` | No | No |
| **`design.md:183-184` (§4c)** the four keys | Yes, as a **ceiling** | `EXPECTED_ALLOWLIST` + drift guard | `:465`, `:469` | No | **Declared — D-11:** nothing consumes a key; T01 still forbids reading one |
| **L9** no absolute backend URL in the artifact | Yes | set equality over 8 justified vendor URLs | `:555`, `:559`, `:570`, `:577` | No | No |
| **D-2** one scanner for the real and the synthetic bundle | Yes | `scanBundle(treeRoot)`, used by both | all of `:272`-`:385` | No | No |
| **D-4** strict on `process.env` | Yes | zero occurrences; any is a failure | `:323`, `:447` | No | No |
| **D-6** `envPrefix` pinned to `['VITE_']` | Yes | `vite.config.ts:12` | `:516` (M4/M13/M14) | No | No |
| **D-8** no sourcemaps | Yes | `sourcemaps` must be empty | `:547`, `:363` (M11, M17) | No | No |
| **D-9** no surviving `import.meta.env.` | Yes | literal absence over every file | `:454` | No | No |
| **D-10** a real platform value in the control | Yes | `checky-local-only` | `:327`, `:368`, `:431` | No | No |
| **D-5** the worker is discovered, not named | Yes | tree walk + a shape assertion | `:241`, `:264` | No | No |
| **L12** phase additivity | Yes | one new test, one hardened config line | suite 412/414 | No | No |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| AC1 no key outside the allowlist, every emitted file | **PASS** | `:442`, `:447`, `:264` (M10) |
| AC2 no real platform value, no secret shape | **PASS** | `:508`, `:327`, `:368` (M2) |
| AC3 no `define`; `envPrefix` exactly `['VITE_']` | **PASS** | `:516`, `:524` (M4, M5, M13, M14) |
| AC4 no `.env*` under `frontend/` | **PASS** | `:529` (invariant) + `:533` (detector, M15) |
| AC5 private key, `client_secret`, `Bearer ey…`, JWT, `AKIA` | **PASS** | `:327` (×5 shapes) |
| AC6 the allowlist is `design.md` §4c's, with a drift guard | **PASS** | `:465`, `:469`, `:475`, `:498` (M7, M8, M16) |
| AC7 default-deny, proven by a negative **and** a positive control | **PASS** | `:272`-`:385`, `:387`-`:439` (M6) |
| AC8 set equality over the enumerated vendor URLs | **PASS** | `:555`, `:559`, `:570`, `:577` (M1, M9) |
| AC9 suite green against the baseline, no T01–T12 edit | **PASS with the two known environmental failures** | 377 → **414**, delta exactly **37** |
| AC10 no `*.map` emitted | **PASS** | `:547`, `:363` (M11, M17) |
| AC11 no literal `import.meta.env.` in the output | **PASS** | `:454` |

### AC9, measured

| | |
|---|---|
| Baseline before T13 | **375/377** at the end of T12 phase 12 |
| `bundleContainsNoSecretEnvKeys.test.ts` | **37 cases** (27 phase 6, +4 phase 9, +1 phase 10, +5 phase 11) |
| `vitest run` now | **412/414** — 12 files, 1 failed |
| `tsc --noEmit` | clean |
| `oxlint` | no violations |

The two failures are the same two T07 cases as in T09–T12, both environmental, neither ever counted as passing:
`lintRejectsHandWrittenBackendFetch` **AC4** (this host's `oxlint@1.87.0` prints no summary line) and **AC2 end to
end** (`spawn pnpm ENOENT` — bare `pnpm` is not on PATH; T13 and T12 both build via `node_modules/.bin/vite`
directly, which is why only T07 is affected). `377 + 37 = 414`, the delta is exactly the cases added, and no other
case changed state.

**Files modified outside this task's own test:** one — `vite.config.ts:12`, the `envPrefix` pin (D-6). No T01–T12
test, no source module, no spec file.

## Coverage, honestly

Enumerated, not measured (`@vitest/coverage-v8` is not a workspace dependency). What this suite does **not** prove,
stated plainly:

1. **A secret whose value nobody can predict is undetectable by scanning.** Vite inlines a key's value, not its
   name, so `define: { X: '"<a real production secret>"' }` leaves nothing in the artifact to match. This is why the
   task's weight rests on **mechanism closure** — `envPrefix` pinned, no `define`, no `.env`, no surviving
   `import.meta.env.` — and only secondarily on shape matching. `:421` proves the premise; the header and
   `ENV_KEY_PATTERNS` say so in the code (Kimi 11.7).
2. **A secret stored UTF-16, or split by a stray lead byte, is invisible to either decode** — measured above. The
   `latin1` read is correct but buys nothing here, and M12 is unkillable for that reason.
3. **Nothing reads a §4c key today**, so the allowlist is exercised as a ceiling only. The first task to consume one
   will meet T01's source rule, which forbids it — **OQ-1, an owner decision, and T13 deliberately does not settle
   it** (D-11).
4. **One build, one host.** The production markers, the eight vendor URLs and the "zero environment keys" result are
   properties of these pinned dependency versions. A dependency bump can add a ninth URL; that fails AC8 by design,
   and the failure is the mechanism working.

## Verdict

**PASS.** R58 and L10 are verified over the real emitted artifact by one scanner shared with both a negative and a
positive control, 37 cases, **16 of 17 mutants caught**. The single survivor is explained by measurement rather
than asserted to be forward-looking, and the false justification I gave it in phase 9 is corrected in the code and
in both earlier artifacts. One declared deviation (D-11): the four-key allowlist is a ceiling no task consumes, and
**the owner must settle T01-vs-§4c before T14+ reads a key.**
