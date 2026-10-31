# frontend · T29 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` + `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |

**T29 is a verification-only task: the test file *is* the deliverable.** There is no production code to
generate tests for, so this phase is a manifest rather than a fresh body of work — every case named below is
already written, committed and green. What Phase 10 adds is the mapping: each of the **53 cases** to the
acceptance criterion and requirement ID it discharges, and each of the **22 mutants** to the instrument that
kills it.

`frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` — **1730 lines, 53 cases,
thirteen `describe` blocks, 32 of them controls.** No production file is touched.

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **53 / 53**, exit 0 |
| Whole suite | **1395 passed / 1 failed (1396)** — `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental |
| Mutation | **22 / 22 killed, no survivors**, all by this file |
| Production diff | empty |

---

## The manifest

Controls are marked **C**. A control proves an instrument can report DIRTY; it is not a claim about the
product. The ratio — 32 controls to 21 claims — is the shape a verification-only task takes, and AC11 is the
clause that makes it mandatory (T22-D14: an absence file whose instruments are never shown to fire is
vacuous).

### AC2 precondition — the five fixtures are genuinely five · R30, L4 · F1, F2

This block runs **first**, deliberately. Everything after it compares five arrivals and asserts they are
indistinguishable; five fixtures that were secretly one fixture would satisfy all of it while proving
nothing. That is Kimi F2's objection, and the ordering is the answer.

| # | Case | Discharges |
|---|---|---|
| 1 | names five states, with five distinct descriptions and five distinct URIs | F1 |
| 2 | each fixture's own state word appears in its own description and URI and in no other's | F2 — makes a leak **attributable**, not merely detectable |
| 3 | every fixture carries all three error fields onto the arrival URL | the fixtures reach the SPA boundary at all |

### AC1 — every account state ends in the one discard closure · R10, R30, R8

| # | Case | Discharges |
|---|---|---|
| 4 | routes each state to the sign-in start with the discarded copy and no token | AC1 in full: `signIn.discarded`, `/app/sign-in`, `REPLACE`, empty `search`/`hash`, no token |
| 5 | never exchanges a code, because an error response carries none | RFC 6749 §4.1.2.1 |
| 6 | shows the sentence the catalogue owns, not a literal written here | R10 — the copy is the catalogue's, so a catalogue change cannot pass silently |

### AC2 — five account states, one outcome · R30, L4 · F1, F2

| # | Case | Discharges |
|---|---|---|
| 7 | produces an identical struct for all five | AC2 |
| 8 | **C** the comparison fails on a successful callback, so it is not blind | AC11 |
| 9 | **C** the struct comparison alone would MISS a query string kept by all five | **Kimi I7** — pins the absolute `search === ''` against deletion-as-redundant |
| 10 | **C** the struct is not a shell of empty fields | AC11 — a struct of five `undefined`s compares equal too |

### AC3 — the state, its description and its URI do not survive the discard · R30, L4 · F2

| # | Case | Discharges |
|---|---|---|
| 11 | leaves none of the three on any surface, for any of the five states | AC3 — six surfaces × six console channels × five states × three needles |
| 12 | **C** every swept surface is actually read, proven with a sentinel on each | AC11 — a surface added without a sentinel is a surface nobody has proved is read (T28's I1/S1 lesson) |
| 13 | **C** the router-location surface is read, proven on a router parked at the callback URL | AC11 |
| 14 | the discard has already navigated by the time the render returns, so the location never holds the query | a **measured fact about production**, kept as its own case (Phase 6 §1) |
| 15 | **C** every console channel is watched, not only the ones the flow happens to use | AC11 |
| 16 | **C** a watcher installed after the arrival sees nothing written before it | AC11 — the sweep's ordering is load-bearing |

### AC4 — no failure screen carries a class or an inline style · R30 · F6

A **pin for task 173**, not a claim that styling will never exist (**T29-D3**).

| # | Case | Discharges |
|---|---|---|
| 17 | renders no class and no style attribute, for any of the five states | AC4 |
| 18 | **C** a class keyed on the cause is caught | AC11 · F6 |
| 19 | **C** an inline style is caught too, and the walker reaches the root itself | AC11 — `querySelectorAll('*')` excludes the root; a `style` on the container would be invisible |

### AC5 — the sentence is rendered with the clock at zero · L4, `agents.md:44` · F3

**This is the clause T29 exists for.** No earlier file in the package asserts that the SPA's own rendering is
untimed by cause, and a delay keyed on the failure code is the one way the five outcomes could stay
structurally identical while still being distinguishable to a watching user.

| # | Case | Discharges |
|---|---|---|
| 20 | renders exactly the discarded sentence at clock zero, for every account state | AC5 · F3 |
| 21 | lands on the sign-in start at clock zero as well, for every state, so no redirect is deferred | **Kimi I2** — `discard()` sets the notice and navigates in one closure, so a `setTimeout(navigate)` would leave the sentence at zero and defer only the navigation |
| 22 | **C** a sentence rendered inside a timer is ABSENT at zero and present once the clock moves | AC11 · **F9** — proves the fake clock is installed and the assertion can fail |

### AC6 — no production module holds an unexplained timer · L4, R21 · F4, F5

Scoped to the **whole tree with an allowlist**, per F5, rather than to a file list, per F4 — a timer in a
helper imported by the identity path would escape a file-scoped scan.

| # | Case | Discharges |
|---|---|---|
| 23 | finds no timer outside the three permitted modules | AC6 · F5 |
| 24 | the permitted rows describe exactly the timers that exist, with no slack | the allowlist is an **upper and lower bound**; a stale row leaves budget behind |
| 25 | **C** a timer in an auth module is reported | AC11 |
| 26 | **C** the accounting admits a permitted count and no more | AC11 |

### AC7 — no production module names an account state · R30, L4

| # | Case | Discharges |
|---|---|---|
| 27 | finds no globally-forbidden state word in the comment-stripped tree | AC7 |
| 28 | **C** the scan flags each forbidden state word on its own | AC11 — per-word, so a single live alternative cannot carry a dead list |
| 29 | **C** the scan stays SILENT on the words the package legitimately uses | `unknown`, `missing`, `pending`, `closed`, bare `disabled` (L11's `aria-disabled`) |
| 30 | **C** a state word inside a URL STRING survives stripping, which it did not before | **Kimi I3** — the regression guard for the stripper defect |
| 31 | **C** comments are still removed, including ones that contain a quote | **Kimi I3** — where a literal-aware scanner regresses |
| 32 | **C** comment stripping is what makes this scan survivable at all | AC11 |
| 33 | **C** a scan over an empty tree is refused rather than reported clean | AC11 — the deepest vacuity |

### AC8 — no user-facing string names an account state · R30, L4, Q14 · F7

Two grammars and two tiers. Values are prose (word-bounded); keys are identifiers (unbounded). The globally
forbidden words are forbidden anywhere; the sign-in-scoped words only on `signIn.*` and `callback.*`.

| # | Case | Discharges |
|---|---|---|
| 34 | scans every string in the catalogue and finds none | AC8 |
| 35 | **C** a state sentence is flagged by its VALUE | AC11 |
| 36 | **C** a state key is flagged by its NAME even when its value is innocent | AC11 |
| 37 | **C** a key that names the CONCEPT is flagged even when its value is uniform | **Kimi I5** — `signIn.accountState` is a slot waiting to be filled |
| 38 | **C** the sign-in-scoped vocabulary is forbidden on a sign-in key and PERMITTED elsewhere | **Kimi I4**, the split from one direction |
| 39 | **C** the globally-forbidden vocabulary is forbidden EVERYWHERE, not only on a sign-in key | the split from the **other** direction — without this, the tiering is unproven |
| 40 | **C** a permitted-copy row admits exactly the key it names | AC11 |
| 41 | **C** an array-valued entry is flattened, so a list of bullets cannot hide a state word | AC11 |
| 42 | **C** an empty catalogue is refused rather than reported clean | AC11 |

### AC9 — nothing gates a failed sign-in on an account state or a role · `agents.md:42-43`

Instrument inherited from T28, **re-run rather than rewritten**, then widened by Kimi I1.

| # | Case | Discharges |
|---|---|---|
| 43 | finds no account-state-shaped or role-shaped comparison in production source | AC9 |
| 44 | **C** the scan flags a gate on each of the five states | AC11 |
| 45 | **C** the scan flags the gate shapes Kimi I1 named | **Kimi I1** — `account.state ===`, `account.status ===`, `user.isLocked`, `switch (accountState)`, `AccountState.Locked` |
| 46 | **C** the state and status alternatives key on the RECEIVER, not on the property alone | **Kimi I1 refused in form** — a bare `\.status\s*[=!]==` fires on `rateLimitNotice.ts` and `sessionBoundary.ts`, which R23 and R21 require to read an HTTP status |
| 47 | **C** the role variants Kimi I6 listed were ALREADY caught, and the suggested fix was refused | **Kimi I6 rejected** — records the measurement so the finding is not re-opened |
| 48 | **C** the scan stays SILENT on CallbackScreen's own OAuth state comparisons | the OIDC `state` parameter is not an account state; without this the scan would fail on L16's own code |

### AC10 — two failure sentences, exactly six `signIn` keys · R10, L4

| # | Case | Discharges |
|---|---|---|
| 49 | pins the signIn key set to exactly the six that exist, so a seventh fails | AC10 |
| 50 | keeps the failed and discarded sentences distinct, and neither names a state | R10 — uniform is not the same as absent |
| 51 | **C** the key-set pin fails when a seventh key arrives, whatever it is called | AC11 |

### AC11 — every instrument has been shown to report dirty · T22-D14

| # | Case | Discharges |
|---|---|---|
| 52 | names one dirtying violation per instrument | the roll-up: thirteen rows, one per instrument, each cross-referenced to the control that proves it |
| 53 | refuses a scan that reads nothing, through the one guard every walker calls | the shared guard, asserted once rather than per-walker |

---

## Mutation manifest

22 mutants, **22 killed, all by this file**, none inherited-only. Measured against a **guarded** baseline of
`1 failed of 1396` — see Phase 9 for why the harness now asserts its own floor.

| Family | Mutants | Targets | Result |
|---|---|---|---|
| The copy names a state | MC01–MC04 | AC7, AC8, AC10 | 4 / 4 |
| The redirect diverges | MC05–MC06 | AC1, AC2 | 2 / 2 |
| The treatment diverges | MC07 | AC4 | 1 / 1 |
| **The timing diverges** | **MC08–MC11** | **AC5** | **4 / 4** |
| Gate, timer, title leak | MC12–MC14 | AC9, AC6, AC3 | 3 / 3 |
| Written to escape | MC15 | AC5 | 1 / 1 |
| Live region removed | MC16 | AC5 | 1 / 1 |
| The encoding trio | MC03, MC17, MC18 | AC3 | — |
| The Phase 9 instruments | MC19–MC22 | Kimi I3, I1, I2, I5 | 4 / 4 |

Three deserve naming:

- **MC11 is the subtlest mutant in the package**: `setTimeout(discard, 0)`. It renders identically on a real
  clock and differs only under a fake one held at zero. If MC11 ever survives, the timing clause has stopped
  being load-bearing.
- **MC15 is written to escape**, in T26's, T27's and T28's tradition: a delay that fires only for an error
  code no test drives. It is killed by the **enumeration** of the five states, not by any single case.
- **MC18 isolates the encoding normalisation**, and getting to it took two wrong claims of mine. MC03 is
  killed by AC1's absolute `search === ''`, and MC17 by the raw needle plus `storedKeys`, so neither proves
  `encodings()` works. MC18 leaks **one** fixture's description, percent-encoded, to `window.name`, and was
  measured to survive all 46 pre-Phase-9 cases with the decode bypassed. It is the only mutant that does.

## Conventions

`agents.md`'s JUnit/Testcontainers clauses are backend clauses and do not apply: this is a Vitest + jsdom
file in `frontend/`, run through `npx -y pnpm@12.14.0 exec vitest`. The frontend conventions that do apply
are observed — no production import is mocked, the catalogue is the source of all asserted copy, the clock is
`vi.useFakeTimers()` with `vi.useRealTimers()` in `afterEach` **after** the unmount block, and no case asserts
on a `path:line`, only on files.

## Open items, unchanged

**OQ1** stands and is not T29's: SAS's own `/login` renders no `mfaCode` field while the backend requires it
in the same POST. T29 cannot reach that page — `oidcRoundTripEndToEnd` (task 134) has no suite and no owning
task (**T29-D1**). The three token states remain task 59's (**T29-D2**); the visual pin remains task 173's
(**T29-D3**); the Phase 8 fixes landed in Phase 9 rather than Phase 10 (**T29-D4**).

---

**Phase 10 complete.** 53 cases mapped to eleven acceptance criteria, 22 mutants mapped to nine families, no
production change. **Phase 11 is Kimi 2.7's independent test review.**
