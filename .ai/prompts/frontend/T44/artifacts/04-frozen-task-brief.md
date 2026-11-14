<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). Downstream phases may not renegotiate this. -->

# frontend · T44 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Task** | T44 — Registration accepts 128 characters |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Approval** | Owner's standing instruction to run the pipeline to the next Kimi gate. All ten phase 3 findings dispositioned below; two scope decisions (D-T44-1, D-T44-2) confirmed. |

---

## 0. A correction this gate makes before anything is frozen

**`'👍'.repeat(128)` is 256 UTF-16 units, not 512.** A thumbs-up is one code point and **two** units, which
T43's own row states correctly (*"12 astral code points, which are 24 UTF-16 units"*, `:239` — 12 × 2 = 24).
Phases 0, 1 and 2 said 512 in seven places; the factor was wrong, not the direction. Those three artifacts
are corrected in place, and the error is recorded here rather than quietly fixed, because of how it
travelled: **phase 3 repeated 512 four times without checking it.** A number no phase measured passed a
grounding phase, an extraction, a brief and an adversarial review intact. The measurement that settles it is
below, and the frozen ACs use **256**.

```
$ node -e "…jsdom…"   (frontend/node_modules/jsdom)
maxLength prop        : 128
value.length after set: 256     ← '👍'.repeat(128) through HTMLInputElement.prototype's value setter
code points           : 128
hasAttribute maxlength: true
```

The same run settles the other open physical question — see F4.

## 1. Disposition of the phase 3 findings

| F | severity | disposition |
|---|---|---|
| **F1** three rows, table shape frozen | Blocker/Major | **ACCEPTED.** Citation verified: the table is `:233-246` and its header at `:229` says *"TASK 44 ADDS ROWS AND NOTHING ELSE"*. One refinement: that header names only **two** rows (`'a'.repeat(128)` in, `'a'.repeat(129)` out). The astral row is a **third**, and it is in scope because it is the only row that can fail in the direction this bound fails in — T43 wrote the header before anyone had looked at which way the bug points at 128. |
| **F2** a dedicated astral DOM case | Major/Blocker | **ACCEPTED, with the number corrected to 256.** Citations verified (`it.each` at `:250`, the 12-astral DOM case at `:276-286`). The new case is that case's mirror at the other bound. |
| **F3** the control must pin the upper bound too | Major | **ACCEPTED in substance; citation corrected.** The control is at **`:337-344`**, not `:315-322` (which is inside the totality case). Verified at `:342-343`: it pins a row on `ADVISORY_MINIMUM_CODE_POINTS` and nothing on the maximum, so the control as it stands cannot see a maximum silently widened to 999. |
| **F4** pin `maxLength`/`minLength` absence | Major/Blocker | **The pin is ACCEPTED. Its stated rationale is REJECTED as false — and the truth makes the pin more necessary, not less.** F4 says *"the table's body-equality assertion would catch this"*. It would not: **`maxLength` constrains user editing, not a programmatic value set**, and every case in this suite sets the value through `HTMLInputElement.prototype`'s setter. The measurement above is the proof — with `maxlength=128` on the element, a 256-unit value went in and 256 units came back. So a `maxLength={128}` mutant is **invisible to every behavioural case in the file**, and the structural pin is the only instrument that can kill it. Citation verified: the field-pair exception block is at `:405-417` and `withoutComments` exists at `:171` with its own control at `:388`. |
| **F5** re-aim the harness | Major | **ACCEPTED**, mutant list frozen in §5 below, each with its predicted killer. |
| **F6** no production change; a needed one is a T43 finding | Major | **ACCEPTED** and promoted into the Scope section verbatim. |
| **F7** the 129 row carries no copy | Minor/Major | **ACCEPTED.** It confirms D-T44-1. T43's `:270-274` is the precedent: out-of-range rows assert `aria-invalid` and the comment there already says *"task 45 owns that copy, and it should choose its own words rather than inherit the words of the task that wrote the check."* |
| **F8** literals stay deterministic and non-secret | Minor | **ACCEPTED**, no change — `'a'.repeat(n)` and `'👍'.repeat(n)`. |
| **F9** don't duplicate task 46's claim | Minor | **ACCEPTED, and already satisfied in source.** Citation verified exactly: `:325` asserts `countCodePoints('👍'.repeat(128)) === 128`, inside a case whose comment at `:310-324` already draws the line — *"The 128 assertion is a count, not an acceptance: task 44 owns whether a 128-code-point password is accepted, and adds a row to the table above for it."* AC2 therefore asserts through the **DOM value**, which is a different claim from the helper's. |
| **F10** T43's non-length behaviour carries over | Minor | **ACCEPTED.** No mutant targets the rejection path, focus, or double submit except where it touches the bound. |

**Nothing is rejected outright.** One rationale is rejected (F4's), one citation corrected (F3's), one number
corrected everywhere (F2's), and one scope refinement added (F1's third row).

## 2. Scope (final)

**In** — the three upper-bound rows; the astral DOM case; the control's upper-bound pin; the
`maxLength`/`minLength` absence pin on both field primitives, by **two** instruments; a re-aimed mutation
harness.

**Out** — guidance **copy** for an out-of-range password (**task 45**); the general counting claim
(**task 46**); blocking, disabling, debouncing, trimming, NFC-normalising or truncating anything; double
submit (OQ-T43-11), `autoComplete` (OQ-T43-7), rejection messaging (OQ-T43-9); any restructuring of T43's
table, screen or counter (**L12**).

**F6, verbatim and binding:** *"If a production change appears necessary, pause and file a finding against
T43; do not modify production source to make the tests pass."*

## 3. Business rules and locked decisions

Unchanged from the phase 2 brief: **R1** is the only requirement in scope; **L12** governs, with **L11**,
**L6**, **L4**, **L2**, **L3**, **L14** and T43's **D6** binding as recorded there.

## 4. Acceptance Criteria (final)

| AC | claim | instrument |
|---|---|---|
| **AC1** | A **128-code-point ASCII** password is accepted: one request through the generated client, body carrying exactly the 128 characters typed, nothing disabled, **not** marked `aria-invalid` | new `PASSWORDS` row, existing `it.each` |
| **AC2** | A **128-code-point astral** password (`'👍'.repeat(128)`) is accepted on the same terms, and **the DOM input's `value.length` is 256 while `countCodePoints` of that same value is 128** — the `.length` bug's false **reject**, the direction T43 could not measure | new `PASSWORDS` row **and** a dedicated DOM case mirroring `:276-286` |
| **AC3** | **129 code points is out of range**: marked `aria-invalid` **and the request is still sent**. Without it, *"128 is the maximum"* is unfalsifiable — `<= 999` satisfies AC1 and AC2 | new `PASSWORDS` row |
| **AC4** | Neither `EmailField` nor `PasswordField` carries `maxLength` or `minLength` — pinned **structurally** (a `withoutComments` source scan over both primitive files) **and** on the rendered element (`hasAttribute('maxlength')` is false on the live field). Measured rationale: `maxLength` truncates **user editing only**, in **UTF-16 units**, so in production it would halve a valid astral password to 64 characters before the handler ran, while in this suite it is invisible to every behavioural case | design-system suite + named suite |
| **AC5** | T43's claims still hold **by extension, not rewrite** (L12): the table control now pins **both** bounds and a row beyond the maximum; whole suite green; `tsc` 0; `oxlint` 0 | existing control, extended |

## 5. Frozen mutant list (phase 7 may add, may not remove)

| mutant | predicted killer | class |
|---|---|---|
| `countCodePoints(password)` → `password.length` | the **128-astral row** (false reject) as well as T43's 7-emoji row | own + inherited |
| `ADVISORY_MAXIMUM_CODE_POINTS` 128 → 999 | the **129 row**, and the control's new "a row sits on the maximum" assertion | own |
| drop `&& length <= ADVISORY_MAXIMUM_CODE_POINTS` | the **129 row** | own |
| `<= ADVISORY_MAXIMUM_CODE_POINTS` → `<` | the **128 ASCII row** (the bound itself) | own |
| add `maxLength={128}` to `PasswordField` | **AC4's two pins only** — no behavioural case can see it (measured, §0) | own |
| drop the control's new upper-bound assertion | the control's own canary | own |
| T43's TR01–TR28 | inherited unchanged | inherited |

## 6. Files

**Create:** none.
**Modify:** `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`;
`src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (AC4's structural half).
**Do not modify:** `spec/**`, `contracts/**`, `src/i18n/catalogue.ts`, `src/app/ui/*.tsx`,
`src/features/account/RegisterScreen.tsx`, `src/features/account/passwordLength.ts`,
`src/api/generated/**`, any other task's named suite.

## 7. Open Questions

**No blockers.** D-T44-1 (the 129 row is this task's) and D-T44-2 (the `maxLength` pin is this task's) are
**confirmed**, the second now on measured rather than argued grounds. T43's open questions stay with their
owner and are untouched here.

**FROZEN.** Phase 5 plans against this document and nothing else.
