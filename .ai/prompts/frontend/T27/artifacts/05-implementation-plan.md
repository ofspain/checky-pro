# frontend · T27 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN) |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code in this phase. Every file below traces to the frozen brief's Files sections.

---

## Files to create

- `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — the one authorized file.

## Files to modify

**None.** The brief anticipates no production change. If Phase 6 finds one necessary it is a finding for the
Phase 9 gate, not an edit.

## Files NOT to modify

As frozen: everything under `spec/`; the nine `src/auth/` production modules; `src/app/routes.tsx`;
`src/i18n/catalogue.ts`; `src/pwa/navigationDenylist.ts`; `package.json`; the neighbouring named tests;
anything under `services/auth/`.

---

## Measurements to take before writing a single case

The T26 lesson that cost the most was believing an instrument before watching it fail, and the T25 lesson was
that a case can be inert without anything being wrong in production. Five measurements come first, in a
scratch file, and each one decides a design below. **None of them is a deliverable; the file is deleted.**

| # | Question | Why the answer changes the plan |
|---|---|---|
| M1 | Can a jsdom `location` be stubbed such that one counter observes `assign`, `replace`, the `href` **setter** and `window.open`? | AC1 is "exactly one hand-over by any mechanism". If the `href` setter is not interceptable on this jsdom, the counter covers three mechanisms and the file says so rather than implying four. |
| M2 | What is `document.activeElement` after the callback's success navigation, under `createMemoryRouter`? | AC9 pins a measured value. `/app/home` has no element (T24-D11), so the likely answer is `document.body` — but "likely" is not a pin. |
| M3 | Does `strip()` leave `'account/mfa'` and the PKCE `code_challenge` hits as the **only** survivors of the identifier pattern? | The permitted-hit table must be exactly right or the scan is unmaintainable. Phase 4 measured this with grep; re-measure through the real `strip()`, because stripping a block comment removes its newlines. |
| M4 | Does the exchange's `fetch` stub let the **response body** be swept, i.e. is the parsed body reachable from the test, or only `idToken`/`accessToken`? | `codeExchange.ts:76` destructures only two fields out of the payload. AC7's response-body sweep must sweep what the SPA *received*, which is the stub's own body — the test owns it, so the sweep is over the fixture, and the claim becomes "nothing the SPA received reached a surface". That wording matters. |
| M5 | Does a second click actually reach `startSignIn` twice in jsdom, so the `inFlight` row is not inert? | T25's lesson: write the mutant that escapes first. If the second click never dispatches, the row proves nothing and must be driven differently. |

---

## The file's structure

One file, eleven `describe` blocks matching the eleven ACs, in the brief's order. Helpers first, then controls
interleaved with the claims they protect rather than collected at the end — T26's ordering, which put each
instrument's proof-of-failure next to its use so a reader cannot take a green row on trust.

### Module-level constants and why each is derived

| Name | Source | Why not a literal |
|---|---|---|
| `inventoryKeys()` | `AUTHORIZE_STORAGE_KEYS` + `RETURN_TARGET_KEY`, read **inside a function** | The import-cycle hazard T26 recorded: a module-scope read of a route-table-dependent binding yields `undefined` under Vite |
| `CATALOGUE` | `en` from `src/i18n/catalogue.ts` | AC6 scans every user-facing string; a copy of the strings here would pass after a rewording |
| `DENYLIST` | `DENYLISTED_NAVIGATION_PATHS` | AC8 cites the owning module rather than re-proving it |
| `AUTHORIZE_PARAMETER_NAMES` | **eight literals**, deliberately | AC10 and the F6 rejection: the names are RFC 6749 §4.1.1 / OIDC Core 3.1.2.1 wire spellings, and a derived set cannot catch an addition. The reasoning goes in a comment above the constant. |
| `FORBIDDEN_PACKAGES` | **literals**, enumerative and said so | AC3's manifest check. An enumerative list cannot prove absence of a package nobody thought of; the file states that, as T26 did for its surfaces. |
| `MFA_IDENTIFIER_PATTERN` | one regex, with the permitted-hit table beside it | AC3 |
| `PERMITTED_HITS` | a table of `{ file, hit, why }` | Every permitted hit carries its reason, so a future reader learns why rather than deleting the row |

### Public helper signatures

```
function handoverWatch(): Handover            // M1 decides its breadth; one counter, every mechanism
type Handover = { count(): number; targets(): string[]; restore(): void }

async function productionSources(): Promise<ScanTarget[]>   // T26's walker, comment-stripped, refuses an empty tree
type ScanTarget = { path: string; code: string }
function strip(source: string): string                      // T26's, unchanged

function manifest(): Promise<Record<string, string>>        // merged dependencies + devDependencies
function forbiddenPackagesIn(deps): string[]
function identifierHits(targets): Array<{ file: string; hit: string }>
function unexplainedHits(hits): Array<{ file: string; hit: string }>   // hits minus PERMITTED_HITS

type Fixture = { label: string; claims: Record<string, unknown>; body: Record<string, unknown> }
function fixtureWithFactors(label, extraClaims): Fixture    // builds a signed-shaped ID token from claims
async function arriveAt(url: string, fixture: Fixture): Promise<Arrival>
type Arrival = { container: HTMLElement; router: Router; unmount(): void }
function outcomeOf(arrival): Outcome                        // destination + text + stored keys + request shape
type Outcome = { destination: string; text: string; storedKeys: string[]; requestBody: string }

function catalogueStrings(): string[]                       // every leaf of `en`, flattened
function mfaShapedStringsIn(values: string[]): string[]
function sweepSurfaces(arrival, fixture): Array<{ surface: string; needle: string }>
```

`outcomeOf` is the single comparison unit for AC4, and that is the whole design of the symmetry claim: three
fixtures produce three `Outcome`s, and the assertion is that they are **deep-equal**. A per-field comparison
spread across three cases would let a field be forgotten; one struct cannot.

### Private helpers

`turn()` (T16's macrotask drain — the handler awaits real `crypto.subtle`), `button()`, `liveRegion()`,
`flattenCatalogue()`, `needlesFor(fixture)`, `freshStore()`, `restoreAll()` in `afterEach`.

### Entities / repositories / services

Not applicable — this is a frontend verification task with no persistence layer. The equivalents:

- **Modules under test:** `SignInScreen`, `CallbackScreen`.
- **Modules cited, not re-proved:** `authorizeRequest`, `codeExchange`, `idTokenNonce`, `accessToken`,
  `returnTarget`, `signInNotice`, `navigationDenylist`, `routes`, `catalogue`.
- **Platform surfaces stubbed:** `globalThis.location`, `globalThis.fetch`, `Storage.prototype`,
  `window.open`. Each installed before the code under test runs, each restored in `afterEach`, and AC11 holds
  the control that a spy installed late sees nothing.

---

## Execution order

Front-loaded the way the brief's risk sits: the instruments first, because an instrument that cannot fail
makes every case after it worthless, and the symmetry comparison last, because it depends on the fixture
builder being right.

1. **Measurements M1–M5** in a scratch file. Record each answer; delete the file. *(Nothing is written to the
   deliverable until these five are answered — the T26 and T25 lessons.)*
2. **Scaffolding**: the file header (the full "what this file cannot do" section, as T26's), imports,
   `afterEach` restoration, `turn()`, `arriveAt`/`unmount` using `arrival.unmount()` — never `mounted =
   undefined`, which was T26's own hygiene defect.
3. **Instrument 1 — the hand-over counter** (AC1, F1), with its two controls immediately: a late-installed spy
   seeing nothing, and a synthetic second navigation through a different mechanism being counted.
4. **Instrument 2 — the source scan** (AC2, AC3, F2, F3): walker, `strip`, identifier hits, permitted-hit
   table, forbidden imports; then the three synthetic controls (one-time-code input, `verifyTotp`, `otpauth`
   import) and the empty-tree refusal.
5. **Instrument 3 — the manifest check** (AC3, F2), with a synthetic manifest as its control.
6. **Instrument 4 — the catalogue scan** (AC6, F8), with a synthetic `signIn.enterYourCode` as its control,
   and the note recording why the one-member `SignInNotice` union is not separately asserted.
7. **The fixture builder and `outcomeOf`** — then, before any symmetry claim is believed, **the blindness
   control**: two fixtures differing in `nonce` must produce different `Outcome`s. A comparison that cannot
   fail is the vacuity trap this task is most exposed to.
8. **Symmetry** (AC4, F4): the three fixtures, deep-equal outcomes.
9. **The escape sweep** (AC7, F5): request body and URL, response body, decoded claims, router location, both
   stores — with a sentinel proving each surface is actually read, and **no exclusion list**, unlike T26's
   `nonce`.
10. **The uniform refusal** (AC6): `invalid_grant`, uniform notice, sign-in start, no MFA copy.
11. **Cold-tab resumption** (AC5) and **the authorize parameter set** (AC10).
12. **The announcement and the focus pin** (AC9, D-1), carrying task 135 in its failure message.
13. **`/login` denylisted** (AC8) — one assertion citing the owning module.
14. **Commit the file.** Then write `artifacts/06-mutation-harness.py` and commit it **before running it** —
    the harness reverts with `git checkout` and has destroyed uncommitted test work twice in this package.
15. **Run the harness.** Mutants: a one-time-code field on the sign-in screen; an MFA branch in the callback;
    MFA copy in the catalogue; an `acr_values` authorize parameter; a second hand-over through
    `location.href` (the mutant F1 exists for); an `amr`-conditional navigation; an MFA-shaped value written
    to a store. Each scored for whether **T27's own file** kills it or only an inherited case does.

## Tests required

The ten groups the frozen brief lists, one `describe` each, plus the controls folded in at steps 3–7 above.
Totals are not planned in advance: a case count is an outcome, not a target.

## Verification

`npx -y pnpm@12.14.0 exec vitest run src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`
from `frontend/`, then the whole suite. **The exit code is the verdict, not the pass count** — an unhandled
rejection prints below the all-passed line. One known environmental failure is expected and unrelated:
`src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`.

## Risks this plan is built around

1. **Vacuity is the dominant risk**, not incorrectness. A file whose subject is absence passes by default. Steps
   3–7 each pair an instrument with the violation that must break it, before the instrument is used.
2. **The symmetry comparison can be blind.** Step 7's `nonce` control is the only thing standing between
   "the outcomes match" and "the comparison does not look at anything".
3. **An MFA fixture must not become MFA-specific production support.** The fixtures carry claims the SPA
   ignores; no production module may learn their names. If a case needs production help, the case is wrong.
4. **AC9 could drift into building focus management.** It is a pin with a named owner (task 135), and the
   failure message must say so, or the next reader will "fix" it by editing `CallbackScreen`.

---

**Phase 5 complete.** Plan ready for Phase 6 implementation.
