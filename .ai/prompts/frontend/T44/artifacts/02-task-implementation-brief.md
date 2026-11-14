<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). This is the ONLY specification phases 5-13 use. -->

# frontend · T44 · Phase 2 — Task Implementation Brief

## Task

**T44 — Registration accepts 128 characters.** *"A 128-character password is accepted client-side as
advisory. Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1."*

T43 built the registration screen, the code-point counter and the named suite, and proved **the 12**. This
task proves **the 128**. It is the second and last task against that test name.

## Purpose

R1's advisory bound is *"12 to 128 Unicode code points"*. `RegisterScreen.tsx:60` already evaluates
`length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS`, so **the upper half of
the bound is already implemented and entirely unmeasured**: the suite's `PASSWORDS` table tops out at 13
code points, and deleting `&& length <= ADVISORY_MAXIMUM_CODE_POINTS` today breaks nothing. The purpose of
this task is to make that clause falsifiable, in the unit R1 names.

The upper bound is not a mirror of the lower one. At 12, a `.length` implementation **over**-counts an
astral password *into* range (false accept); at 128, 128 astral code points are **512 UTF-16 units**, so the
same bug **rejects a valid password** (false reject). The bound also has a hazard the lower bound does not:
an HTML `maxLength` attribute counts UTF-16 units and truncates **before any handler runs**.

## Scope

**In**

- Extend `registrationAcceptsTwelveAndOneTwentyEightCodePoints` with the upper end of the bound: **128 ASCII
  (in), 128 astral = 512 UTF-16 units (in), 129 (out)**.
- Pin that **neither field primitive carries `maxLength` or `minLength`** (today neither does; the whole
  `src/` tree contains no occurrence of either word).
- A mutation harness re-aimed at the upper bound.

**Out**

- Any guidance **copy** for a password outside the range — **task 45** owns it
  (`registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints`), and the `register` catalogue group
  stays at six keys.
- The general claim that the counter counts code points — **task 46** (`passwordLengthCountsCodePoints`).
- Blocking, disabling, debouncing, trimming, normalising (NFC) or truncating anything. R1 is advisory and the
  server is the authority.
- Double-submit guarding (OQ-T43-11), `autoComplete` (OQ-T43-7), rejection messaging (OQ-T43-9), and every
  other open question T43 left to the owner.
- Restructuring T43's table, screen or counter — **L12 forbids it**; if the 128 end cannot be added as rows,
  that is a finding to report, not a refactor to perform.

## Business Rules

| ID | one line |
|---|---|
| **R1** | The password is checked as **advisory** against **12 to 128 Unicode code points**, the form submits through the generated client either way, and the server is the authority. |

No other requirement is in scope. R2's identical-acknowledgement rule is already satisfied and already
measured by T43; this task must not disturb it.

## Locked Decisions

| ID | binding effect here |
|---|---|
| **L12** | Phase additivity. T43 built the suite as a table *"so task 44 adds the 128 end without restructuring"*. Rows are added; the table's shape, the screen and `passwordLength.ts` are left alone. |
| **L11** | WCAG AA, keyboard, live regions, **catalogue-only strings**. The 128 end adds **no word**: it is marked exactly as the 12 end is marked, with `aria-invalid` and nothing else. |
| **L6** | Generated client only. A 512-UTF-16-unit password must reach the transport through `createPublicAuthApi()` byte-for-byte as typed. |
| **L4** | Enumeration-safe, identical timing. A long password takes the same path as a short one — no extra branch, no delay. |
| **L2** | Interactive login stays SAS-hosted. No new credential surface; T16's credential table stays at three rows. |
| **L3** | Tokens in memory only. No storage write, and no cached copy of the password. |
| **L14** | Route set under `/app`. No route, no ADR. |
| **D6** (T43) | `countCodePoints` returns **the count, not a verdict**, precisely so this task can use it for 128 without rewriting it. |

## Dependencies

| | |
|---|---|
| **Production, read-only** | `countCodePoints`, `ADVISORY_MINIMUM_CODE_POINTS`, `ADVISORY_MAXIMUM_CODE_POINTS` (`features/account/passwordLength.ts`); `RegisterScreen`; `Form`, `EmailField`, `PasswordField`, `SubmitButton`, `Screen`, `Heading`, `StatusRegion`, `Text`, `TextLink` |
| **Client** | `createPublicAuthApi()` → generated `createAuthClient()`; operation `registerAccount`, `POST /accounts`, `security: []` |
| **Contract** | `contracts/api/auth.yaml` — `RegisterAccountRequest` requires `[email, password]`; `email` is capped at 254, **`password` is `type: string` with no `minLength` and no `maxLength`**, which is why the client check is advisory by construction |
| **Test machinery (T43's, reused)** | the `PASSWORDS` table and its control; `installTransport` + `installBrowserRequest`; the prototype `value`-setter `type()` helper; the `Storage.prototype` spy |
| **Config keys** | none |

## Inputs

A typed password of any length, from the empty string upward, including astral characters and lone
surrogates; a typed email. Nothing is read from storage, the URL or the environment.

## Outputs

`POST /accounts` with `{ email, password }` — the password exactly as typed — and the acknowledgement that
T43 already renders. **No new output.**

## State Changes

**None beyond T43's.** The screen's existing `status` and `advisory` state machine is unchanged; no storage,
no cookie, no token, no route change.

## Files to Create

**None.** This is a measurement task. If implementation finds it needs a new production file, that is a
deviation to report at phase 9, not to make.

## Files to Modify

| file | change |
|---|---|
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | add the three `PASSWORDS` rows and the cases that only the upper bound can fail |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` *(expected, if AC4 lands there)* | pin the absence of `maxLength`/`minLength` on the two field primitives, beside the existing field-pair exception |

Production source is expected to be **untouched**. A change to `RegisterScreen.tsx` or `passwordLength.ts`
would mean the upper bound was mis-implemented by T43, which is a finding.

## Files NOT to Modify

`spec/**` (never) · `contracts/**` · `src/i18n/catalogue.ts` (task 45 owns the guidance copy) ·
`src/app/ui/*.tsx` **except** to add nothing — the primitives are correct as they stand ·
`src/api/generated/**` · any other task's named suite, except the one instrument AC4 needs.

## Acceptance Criteria

| AC | claim | R |
|---|---|---|
| **AC1** | A **128-code-point ASCII** password is accepted: the request goes through the generated client, the body carries exactly the 128 characters typed, nothing is disabled, and the field is **not** marked `aria-invalid`. |
| **AC2** | A **128-code-point astral** password (`'👍'.repeat(128)`, **512 UTF-16 units**) is accepted on the same terms, and the count reports **128** where `String.length` reports **512** — the `.length` bug's false **reject**. | R1 |
| **AC3** | **129 code points is out of range**: the field is marked `aria-invalid` **and the request is still sent**, because R1 is advisory. Without this row the claim *"128 is the maximum"* is unfalsifiable — a bound of `<= 999` satisfies AC1 and AC2. | R1 |
| **AC4** | Neither `EmailField` nor `PasswordField` carries `maxLength` or `minLength`. `maxLength={128}` counts **UTF-16 units**, so it would truncate a valid astral password to **64 characters before the handler ran**, and the advisory check would then see a value the user never typed. | R1 |
| **AC5** | Everything T43 proved still holds **by extension, not by rewrite** (L12): the table's control still shows the `inRange` column is load-bearing in both directions, the whole suite stays green, `tsc` exits 0, `oxlint` exits 0. | L12 |

AC1 carries R1 as well. No AC introduces a word, a delay, a guard or a route.

## Required Tests

| | |
|---|---|
| **Named** | `registrationAcceptsTwelveAndOneTwentyEightCodePoints` — **extended**. After this task its name is fully earned. |
| **Rows** | 128 ASCII (in) · 128 astral / 512 units (in) · 129 ASCII (out) — joining T43's 12, 13, 12-astral, 11, 7-emoji, empty |
| **Structural** | the `maxLength`/`minLength` absence pin (AC4) |
| **Harness** | a fresh copy of T43's 28-row harness, re-aimed. The rows that must now behave differently: `countCodePoints` → `.length` (must die at the **upper** bound as well as the lower), `ADVISORY_MAXIMUM_CODE_POINTS` 128 → a larger number, the `<=` comparison deleted, and a `maxLength={128}` added to `PasswordField`. Every row predicts own / inherited / unfalsifiable, a BROKEN mutant is **not** a kill, and the baseline is asserted. |
| **Not written here** | task 45's rejection-guidance test, task 46's counting test |
| **Commit discipline** | tests are committed **before** the harness runs — `revert()` is `git checkout --` + `git clean -fq` over `frontend/src/` and `frontend/vite.config.ts`. |

## Constraints

- **Performance.** `countCodePoints` is one pass over the string; a 128-code-point password is not a
  performance case, and no memoisation, debounce or `useMemo` is to be added for it.
- **Security.** The password is never logged, never stored, never put in a URL, never written to any storage,
  and never compared against a client-side list. No new credential surface (L2); the advisory check must not
  become a gate (R1). Long test passwords are literal repeats of `'a'` and `'👍'` — no realistic-looking
  secret appears anywhere in the tree.
- **Enumeration.** No branch that a long password takes and a short one does not (L4, R2).
- **Module boundaries.** Screens render only through `src/app/ui` primitives; HTTP goes only through the
  generated client (L6); the counter stays in `features/account/passwordLength.ts`.
- **Null handling.** `countCodePoints` is total — empty string is 0, a lone surrogate is one code point, and
  it never throws, because a client-side count that could throw would be a gate.
- **Thread-safety / transactions.** Not applicable (single-threaded browser, no transaction).
- **Type safety.** `tsc --noEmit` exits 0; no `any`, no `as` on a DOM node that a narrowing guard can prove.

## Open Questions

**No blockers.** Two scope questions were raised in phase 1 and are **decided here**, so that no later phase
has to guess. Both are recorded for the phase 4 approval gate.

| | decision |
|---|---|
| **D-T44-1 — the 129 row belongs to this task.** Task 45 is *"shows the advisory **guidance**"* — the copy. The **mark** is not the copy: T43's own 11 row asserts `aria-invalid` for an out-of-range password with no sentence at all, and the lower bound's outside was pinned by the task that pinned its inside. This task therefore takes 129 as *marked and still sent, with no words*, and task 45 later adds the sentence to a row that already exists. The alternative leaves this task's central claim unfalsifiable. |
| **D-T44-2 — the `maxLength` absence pin belongs to this task.** T43 neither set the attribute nor pinned its absence, and the hazard is specific to this end of the bound: only a long password can be truncated, and `maxLength`'s unit (UTF-16) is the very unit R1 refuses. This task is the first for which the attribute could do damage, so it is the task that pins it. |
