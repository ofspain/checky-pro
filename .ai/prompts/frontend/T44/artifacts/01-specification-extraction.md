<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No design, no code. -->

# frontend · T44 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` · **Task** T44 — Registration accepts 128 characters |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | one sentence |
|---|---|
| **R1** | On submit the SPA checks that the password is **12 to 128 Unicode code points** as advisory guidance only, submits to `POST /accounts` through the generated client, and leaves the server as the authority. |
| **R2** | *(Applies to the screen, not to this task's claim.)* The same acknowledgement is shown whether or not the address exists, with no client-side branching or delay — already satisfied and already measured by T43's AC11. |

R1 is the only requirement this task proves anything new about: it is the **128** in *"12 to 128"*, and T43
proved only the 12.

## Locked Decisions

| ID | how it constrains **this** task |
|---|---|
| **L12** *phase additivity* | **The governing constraint.** T43 built the named suite as a table *"so task 44 adds the 128 end without restructuring"*. A change that required rewriting the table, the screen or the counter would mean T43's design was wrong, and under this task's own guardrails it gets **reported, not worked around**. |
| **L11** *WCAG AA, keyboard, live regions, catalogue-only strings* | The 128 end must not introduce a word. The `register` group is frozen at six keys (T43's D7) because **task 45** owns the advisory guidance copy; a message explaining the upper bound would be task 45's work done early. |
| **L6** *generated client only* | The long password must reach the transport through `createPublicAuthApi()` unchanged — no truncation, no re-encoding. |
| **L4** *enumeration-safe UI, identical timing* | No timer, no delay, no extra branch — a long password must not take a different path from a short one. |
| **L2** *interactive login is SAS-hosted* | No new credential surface. The three-row credential table in T16's scan stays at three rows, by file, shape and count. |
| **L3** *tokens in memory only* | Still no storage write; a long password must not be cached anywhere to compare against. |
| **L14** *route set under `/app`* | No route, no path, no ADR. |
| **agents.md** | Authoritative; `spec/` is never modified. |

## Files involved

**Existing, to extend**

| | |
|---|---|
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | the named suite (40 cases). Its `PASSWORDS` table has **no row at or above the upper bound** |

**Existing, to read only**

| | |
|---|---|
| `src/features/account/passwordLength.ts` | `countCodePoints`, `ADVISORY_MAXIMUM_CODE_POINTS = 128` — already declared and already asserted as a value by T43 |
| `src/features/account/RegisterScreen.tsx` | the submit-time count and the `aria-invalid` mark; **no change expected** |
| `src/app/ui/PasswordField.tsx`, `EmailField.tsx` | **neither sets `maxLength` or `minLength`** today |
| `contracts/api/auth.yaml` | `password: type: string`, no `minLength`, no `maxLength` |

**New files the spec expects:** **none.** If this task creates a production file, something has been
misread.

## Dependencies

| | |
|---|---|
| **Functions** | `countCodePoints(value: string): number`; `ADVISORY_MINIMUM_CODE_POINTS`, `ADVISORY_MAXIMUM_CODE_POINTS` |
| **Components** | `RegisterScreen`, and through it `Form`, `EmailField`, `PasswordField`, `SubmitButton`, `Screen`, `Heading`, `StatusRegion` |
| **Client** | `createPublicAuthApi()` → the generated `createAuthClient()`; operation `registerAccount`, `POST /accounts`, `security: []` |
| **Contract shape** | `RegisterAccountRequest` requires `[email, password]`; `email` is capped at 254, **`password` is uncapped** |
| **Test machinery** | the `PASSWORDS` table and its control; `installTransport` + `installBrowserRequest`; the prototype `value`-setter typing helper |
| **Config keys** | none |

## Acceptance Criteria

| AC | claim | R |
|---|---|---|
| **AC1** | A **128-code-point ASCII** password is accepted: the request goes, the body carries exactly what was typed, nothing is disabled, and the field is **not** marked `aria-invalid` | R1 |
| **AC2** | A **128-code-point astral** password — `'👍'.repeat(128)`, **512 UTF-16 units** — is accepted on the same terms, and `countCodePoints` reports 128 where `String.length` reports 512 | R1 |
| **AC3** | **129 code points is out of range**: marked `aria-invalid`, and the request still goes (advisory). Without this the claim *"128 is the maximum"* is unfalsifiable — a bound of `<= 999` satisfies AC1 and AC2. **Scope question below** | R1 |
| **AC4** | Neither field carries `maxLength` or `minLength`. A `maxLength={128}` would truncate at 128 **UTF-16 units** — 64 astral characters — **before the count ever ran**, so a valid password would be silently halved | R1 |
| **AC5** | Everything T43 proved still holds, **by extension and not by rewrite**: the table's control still shows the `inRange` column is load-bearing in both directions, and the whole suite stays green | L12 |

**No AC adds a word.** The upper bound is marked exactly as the lower bound is marked, and the explanation
is task 45's.

## Tests required

| | |
|---|---|
| **Named test** | `registrationAcceptsTwelveAndOneTwentyEightCodePoints` (`package.md:182`) — **extended**, not replaced. This is the second and last task that verifies it, so after this task its name is fully earned |
| **Boundary rows implied** | 128 ASCII (in), 128 astral = 512 UTF-16 units (in), 129 (out, pending the scope answer). The existing rows already cover the lower bound: 11 out, 12 in, 13 in, 12-astral in, 7-emoji out, empty out |
| **Structural** | the absence of `maxLength`/`minLength` on both field primitives (AC4) |
| **Harness** | a fresh copy of the 28-row harness, re-aimed: the rows that matter here are `countCodePoints` → `.length` (which must now die at the **upper** bound too), the bound itself changed from 128 to a larger number, and a `maxLength` attribute added to a field |
| **Not written here** | `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` (task 45) and `passwordLengthCountsCodePoints` (task 46) |

## Open Questions

**No `package.md` §11 question blocks this task** — Q1 through Q4 are all closed or moot, and none touches
registration.

Two scope questions for the phase 2 gate. Neither blocks extraction; both change what gets written.

| | |
|---|---|
| **OQ-T44-1 — does the 129 row belong to this task or to task 45?** Task 45 is *"a password of 11 or 129 code points **shows the advisory guidance**"* — the **copy**. T43's table already asserts `aria-invalid` for out-of-range rows **with no copy at all**, and its 11 row is the precedent: the lower bound's outside was pinned by the task that pinned its inside. **Recommendation: this task takes the 129 row**, marked and sent, with no sentence — otherwise its own claim is not falsifiable, and task 45 then adds the words to rows that already exist. |
| **OQ-T44-2 — is AC4 (`maxLength`'s absence) this task's or T43's omission?** T43 never set the attribute and never pinned its absence; the hazard is specific to the **upper** bound, because `maxLength` is counted in UTF-16 units and only a long password can be truncated by it. **Recommendation: this task takes it**, as the first task for which the attribute could do damage. |
