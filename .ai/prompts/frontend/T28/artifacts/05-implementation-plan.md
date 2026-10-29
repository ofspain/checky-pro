# frontend · T28 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Every file below traces to the frozen brief's Files sections.

---

## Files to create

- `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` — the one
  authorized file.

## Files to modify

**None.** The brief anticipates no production change. `CallbackScreen.tsx:101-104` already exists and already
cites task 28. If Phase 6 finds a change necessary it is a finding for the Phase 9 gate.

## Files NOT to modify

As frozen: everything under `spec/`; the nine `src/auth/` production modules; `src/app/routes.tsx`;
`src/i18n/catalogue.ts`; `src/pwa/navigationDenylist.ts`; `package.json`; the neighbouring named tests;
anything under `services/auth/`.

---

## What Phase 4 already measured, so Phase 6 does not re-measure it

Unusually for this package, the costly measurements were taken while disposing of Kimi's findings. Recorded
here so Phase 6 starts from them rather than repeating them:

| Fact | Consequence for the plan |
|---|---|
| `error=` with no `state`, a wrong `state` and a matching `state` all reach `/app/sign-in` with `historyAction` `REPLACE` | The error branch precedes the state check; Required Test 2 pins that ordering |
| After the discard, `location.search` is `""` and the description is absent from the location object, document and both stores | AC4 is satisfiable as drafted, scoped to *after* the discard |
| T27's MFA pattern does **not** match `"two-factor authentication"` | The content scan must add `two-factor`; this is the hole F3 found |
| A bare `state ===` scan has 3 legitimate hits (two OAuth comparisons, one Workbox lifecycle) | The gating scan keys on account-state-shaped names and never on `state` |
| `en.signIn` has exactly six keys: `heading`, `explanation`, `action`, `starting`, `failed`, `discarded` | AC8's key-set pin is an exact six |
| 24 production files; none has an enrollment-shaped path | The path scan needs a synthetic control to be worth anything |
| `accountState`, `.mfaEnrolled`, `requiresMfa`, `role ===`, `enroll`, `two-factor` — all zero hits | Every new pattern is safe to add; none fires on existing code |

## Measurements still to take in Phase 6

| # | Question | Why it changes the plan |
|---|---|---|
| M1 | Is `console` spyable across all four channels (`log`, `info`, `warn`, `error`) before the screen mounts, and does the real flow write to any of them? | AC4 sweeps "every console channel". If the flow logs nothing, the sweep needs a sentinel or it is unfalsifiable. |
| M2 | What exactly does the **refused exchange** path render, and does it differ from the `error=` path in any `Outcome` field? | AC2's whole claim. If any field differs, that is a production finding for the Phase 9 gate, not a test to bend. |
| M3 | Does a **successful** callback differ from a discard in every `Outcome` field, or only some? | The blindness control. T27 learned that `text` is empty on success — here the discard renders copy, so the control should be strong, but "should" is not measured. |
| M4 | Is `takeSignInNotice()` readable from the test after the discard without consuming it out from under the screen? | AC2 includes `notice` in the struct. `takeSignInNotice` **clears** on read (`signInNotice.ts:22-26`), so reading it in the test may destroy what `SignInScreen` is about to read. Order matters. |

**M4 is the one with a real trap in it.** The notice is single-read by design. If the test takes it, the
sign-in screen's own `useState(() => takeSignInNotice())` initialiser gets `null` and the rendered copy
changes — so a naive `Outcome` that reads the notice would *alter* the text field it also measures. The likely
resolution: measure the notice **through the rendered document** (the screen has already consumed it and
rendered `signIn.discarded`) rather than by calling the taker, and say so in a comment.

---

## The file's structure

One file, eleven `describe` blocks matching the eleven ACs, in the brief's order. Helpers first; each control
interleaved with the claim it protects rather than collected at the end — T26's and T27's ordering.

### Module-level constants, and why each is derived

| Name | Source | Why not a literal |
|---|---|---|
| `CATALOGUE` | `en` from `src/i18n/catalogue.ts` | AC8 scans every string and pins the key set; a copy here would pass after a rewording |
| `SIGN_IN_KEYS` | `Object.keys(en.signIn)` | AC8's pin is derived, so a seventh key fails rather than needing the test edited |
| `DENYLIST` | `DENYLISTED_NAVIGATION_PATHS` | AC10 cites the owning module |
| `PKCE_KEYS` | `AUTHORIZE_STORAGE_KEYS` | the seeded items and the `storedKeys` field |
| `ENROLLMENT_SOURCE` | a pattern string, with the rejected words named in a comment | AC5; the comment is what stops `required`/`please` being "helpfully" re-added |
| `ACCOUNT_STATE_NAMES` | a pattern string, deliberately excluding the bare word `state` | AC7, with the three legitimate hits named |
| `ERROR_CODES` | a table of `{ code, why }` | AC3 |

### Public helper signatures

```
function strip(source: string): string                          // T26/T27's, unchanged
async function productionSources(): Promise<ScanTarget[]>       // comment-stripped, refuses an empty tree
async function productionPaths(): Promise<string[]>             // F5's instrument: paths, not contents
function hitsOf(targets, pattern): Hit[]
function unexplainedHits(hits): string[]                        // counted against PERMITTED_HITS
function catalogueStrings(): Array<{ key: string; value: string }>
function enrollmentShapedCopy(entries): string[]                // two grammars, as T27
function watchConsole(): ConsoleWatch                           // all four channels, installed before mount
async function arriveAt(query, answer): Promise<Arrival>
async function outcomeOf(arrival): Promise<Outcome>
```

```
type Outcome = {
  destination: string;      // router.state.location.pathname
  historyAction: string;    // F4 — measured REPLACE on every failure path
  search: string;           // F2 — must be '' after the discard
  text: string;             // document.body.textContent — non-empty here, unlike T27
  storedKeys: string[];
  liveRegions: number;
  tokenHeld: boolean;
};
```

`outcomeOf` is the single comparison unit for AC2, and that is the design of the whole claim: four failure
kinds produce four `Outcome`s and the assertion is that they are **deep-equal**. A per-field comparison spread
across cases lets a field be forgotten, and the forgotten field is where a refusal-specific branch would show.
**The notice is read through `text`, not by calling `takeSignInNotice()`** — see M4.

### Private helpers

`turn()`, `seedPkceItems()`, `signInButton()`, `tokenEndpointRefuses()`, `tokenEndpointAnswers(fixture)`,
`tokenWith(claims)`, `restoreAll()` in `afterEach`, `arrival.unmount()` — never `mounted = undefined`.

### Entities / repositories / services

Not applicable — a frontend verification task with no persistence layer.

- **Modules under test:** `CallbackScreen`, `SignInScreen`.
- **Modules cited, not re-proved:** `signInNotice`, `authorizeRequest`, `codeExchange`, `idTokenNonce`,
  `accessToken`, `returnTarget`, `navigationDenylist`, `routes`, `catalogue`.
- **Platform surfaces stubbed:** `globalThis.location`, `globalThis.fetch`, `Storage.prototype`, all four
  `console` channels. Each installed before the code under test runs, each restored in `afterEach`, and AC11
  holds the control that a spy installed late sees nothing.

---

## Execution order

Instruments first, because an instrument that cannot fail makes every case after it worthless; the equivalence
comparison last, because it depends on the arrival harness and on M2/M3/M4 being answered.

1. **Measurements M1–M4** in a scratch file. Record each answer; delete the file. **M4 before any `Outcome`
   field is written**, since it decides how the notice is read at all.
2. **Scaffolding**: the file header (the full "what this file cannot do" section), imports, `afterEach`
   restoration, `turn()`, `arriveAt`/`arrival.unmount()`, `seedPkceItems()`.
3. **Instrument 1 — the content scan** (AC5), with the permitted-hit table and its synthetic enrollment
   sentence — using **Kimi's own example**, *"Your account needs two-factor authentication set up before you
   can sign in"*, since it is the sentence that would have escaped the package before T28.
4. **Instrument 2 — the path scan** (AC6), with `auth/EnrollMfaScreen.tsx` as its synthetic control.
5. **Instrument 3 — the gating scan** (AC7), with the role and account-state controls **and** the case
   proving it stays silent on `CallbackScreen`'s own OAuth `state` comparisons. That last case is the point of
   the instrument's design and must not be dropped.
6. **Instrument 4 — the catalogue scan and the key-set pin** (AC8, AC9), with a synthetic
   `signIn.mfaRequired`.
7. **The arrival harness and `outcomeOf`** — then, before any equivalence claim is believed, **the blindness
   control**: a *successful* callback must produce a different `Outcome`. A comparison that cannot fail is the
   vacuity trap this task is most exposed to.
8. **AC1** the refusal discarded; **AC2** equivalence across the four kinds; **AC3** the code/description/URI
   table.
9. **AC4** the escape sweep, with a sentinel per surface and the late-console control.
10. **AC10** `/login` denylisted — one assertion citing the owning module.
11. **AC11** the instrument roll-up, marked a signpost rather than evidence (T27's Gap 6 lesson, applied from
    the start this time).
12. **Commit the file.** Then write `artifacts/06-mutation-harness.py` and commit it **before running it** —
    the harness reverts with `git checkout -- src/` and the test file lives under `src/auth/`.
13. **Run the harness.** The eight families the brief names, including F8's `error_description` leak and the
    escape mutant (a branch firing only for a code no test uses).

## Tests required

The twelve groups the frozen brief lists, plus the controls folded in at steps 3–7. Totals are not planned in
advance: a case count is an outcome, not a target.

## Verification

`npx -y pnpm@12.14.0 exec vitest run src/auth/sasRefusal…test.tsx` from `frontend/`, then the whole suite.
**The exit code is the verdict, not the pass count.** One known environmental failure is expected and
unrelated: `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`. Suite baseline at T27 close: 1295 passed
/ 1 failed.

## Risks this plan is built around

1. **Vacuity, again the dominant risk.** Silence passes by default. Steps 3–7 each pair an instrument with the
   violation that must break it, before the instrument is used.
2. **M4's single-read notice.** The most likely way to write a quietly-wrong test here is to measure the
   notice by taking it, which changes the screen under measurement.
3. **A scan that fires on the OAuth `state` checks.** Measured as a real collision; step 5's third case exists
   to keep the fix from regressing.
4. **An over-broad pattern forbidding permitted copy.** `required`, `incomplete` and `please` were rejected at
   the gate for exactly this; the constant's comment records it so they are not re-added.
5. **AC2 could find a production difference.** If the four failure kinds do *not* produce an identical
   `Outcome`, that is an R10 finding for the Phase 9 gate — not a struct to loosen.

---

**Phase 5 complete.** Plan ready for Phase 6 implementation.
