<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). Downstream phases may not renegotiate this. -->

# frontend · T45 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

| | |
|---|---|
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Approval** | Owner's standing instruction to run to the next Kimi gate. Twelve findings dispositioned; D-T45-1/2/3 confirmed, two of them now with design detail. |

## 0. Two corrections this gate makes before freezing

**F2 says i18next interpolation is "already used". It is not.** `grep '{{' catalogue.ts` returns nothing:
there are **no interpolated strings anywhere in this package**. What exists is *configuration* —
`i18n.ts` initialises with `interpolation: { escapeValue: false }` — so the mechanism is available and has
never been exercised. **T45 ships the first interpolated string in the application**, and that is a first
worth naming because it changes what a copy assertion means (see §2).

**F8's citation is wrong.** The reachable-keys list is at
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:515-516`, not `:402-405`. The finding itself
is right and is accepted.

## 1. Disposition

| F | severity | disposition |
|---|---|---|
| **F1** `PasswordField` gains `'aria-describedby'?: string` | Blocker/Major | **ACCEPTED, and it breaks no new ground.** `ActionButton.tsx:22` already accepts exactly this prop, and `Text.tsx:11` already carries `id` *"for one reason: `aria-describedby`"*. The direction is the one rule 2 permits — the screen owns the relationship, the field renders it. Verified: `PasswordField.tsx:42-56` is the input block, and it still renders two elements, so the design-system exception table is unchanged. |
| **F2** name the bounds by the constants, via interpolation | Major/Blocker | **ACCEPTED, with the correction above.** Key `register.passwordGuidance`, variables `min` and `max`, interpolated in the screen from `ADVISORY_MINIMUM_CODE_POINTS` / `ADVISORY_MAXIMUM_CODE_POINTS`. Hardcoding "12 to 128" in the catalogue is forbidden: D-T45-2 requires the sentence to name *the constants*, and a literal would drift silently when a bound moves. |
| **F3** a description, not an announcement | Major | **ACCEPTED** — confirms D-T45-1, including that the implementation must **not** move focus. |
| **F4** AC3 needs a control | Major | **ACCEPTED**, with the shape fixed: the *same* query in both directions, by a stable id. Two different selectors would be two instruments, and only one of them would be under test. |
| **F5** nothing before the first submit | Major | **ACCEPTED.** The case types without submitting and asserts absence. |
| **F6** the request still goes | Major | **ACCEPTED**, asserted independently in the new suite rather than inherited. |
| **F7** reuse or duplicate T44's helpers | Major | **DECIDED: duplicate.** Every named suite in this package is self-contained, and the three or four helpers needed are a few lines each. This is the **third** sighting of FU-T43-2 (no shared test-util module exists, so matchers and harness helpers are copied), and the third sighting is recorded rather than acted on, because extracting a module mid-task would touch files this brief does not authorise. |
| **F8** add the key to the reachable list | Major | **ACCEPTED**, citation corrected to `:515-516`. |
| **F9** design-system suite needs no change | Minor/Major | **ACCEPTED**, verified: `RENDER_SITES` already names `RegisterScreen.tsx` (`:101`), no per-screen primitive count exists, and `<Text>` is already a primitive. **Optional pin declined** — a prop-type pin on `aria-describedby` would restate what AC4 measures end to end. |
| **F10** six harness rows | Major | **ACCEPTED** and frozen in §3. |
| **F11** an empty-string row | Minor/Major | **ACCEPTED.** It costs one row and closes the null-handling constraint explicitly; the count is total and zero is out of range. |
| **F12** the copy must not diagnose | Minor | **ACCEPTED, WITH A MANDATORY SHAPE.** The assertion must use **word boundaries and a two-direction control**, not `toContain`. `'long'` is a substring of *belong* and *along*; `'short'` of *shortly*. This package has been bitten by substring matching twice (T173's `'Password'` in `PasswordField`, then the same trap hours later in T43's own scan) and a third would be self-inflicted. |

## 2. The interpolation trap, named before anyone hits it

T43's AC6 case asserts, for four keys, that the rendered DOM **contains the catalogue value verbatim**
(`:493-498`). The new key's catalogue value will be a **template** — `…{{min}}…{{max}}…` — so adding it to
that list would compare rendered text against `{{min}}` and fail.

**The rule this freeze sets:** the guidance is asserted in the **new** suite, against the *interpolated*
result, with the numbers checked against the constants — never against the raw catalogue string. The only
change to T43's suite is the one line F8 names: `'register.passwordGuidance'` joins the **reachable-keys**
list at `:515-516`, which checks the screen's *source* for the key and is unaffected by interpolation.

The other half of T43's AC6 — the loop over `Object.values(en.register)` asserting the screen spells no
sentence — widens itself and needs no edit. It will test the template, which the screen will not contain.

## 3. Frozen mutant list

| mutant | predicted killer |
|---|---|
| the `out-of-range` guard deleted, so guidance never renders | AC1, AC2 |
| guidance rendered unconditionally | AC3 and its control |
| guidance rendered whenever `advisory !== 'unchecked'` | AC5 (typing shows nothing) |
| `aria-describedby` dropped from the field | AC4 only |
| the sentence spelled in the screen instead of `t()` | AC7's source scan |
| the catalogue's `{{min}}`/`{{max}}` replaced by literals `12`/`128` | AC7's constant-equality assertion **only** — the rendered text is identical, so nothing else can see it |
| *(canary)* a case asserting the guidance for an in-range password | the suite itself |

The literal-bounds row is this task's TR32: it is the one mutant whose output is **byte-identical on
screen**, so only an assertion tying the rendered numbers to the constants can kill it.

## 4. Acceptance Criteria (final)

AC1–AC7 stand exactly as the phase 2 brief states them, with AC7 extended by F12: the guidance **names the
two bounds, carries no diagnostic verdict**, and comes from the catalogue.

## 5. Files

**Create:** the named suite.
**Modify:** `RegisterScreen.tsx` · `catalogue.ts` (one key) · `PasswordField.tsx` (one prop) ·
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` (**one line**, the reachable-keys list).
**Do not modify:** `spec/**` · `contracts/**` · `passwordLength.ts` · `routes.tsx` · `api/**` ·
`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (F9 — unless implementation proves otherwise,
which would be a finding).

## 6. Open Questions

**No blockers.** D-T45-1 (described, not announced), D-T45-2 (one string naming the rule, bounds from the
constants) and D-T45-3 (association measurable in the DOM; mechanism now settled as the `aria-describedby`
prop) are **confirmed**. FU-T43-2 is recorded for the third time, for the owner.

**FROZEN.** Phase 5 plans against this document and nothing else.
