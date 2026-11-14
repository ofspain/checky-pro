<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No design, no implementation. -->

# frontend · T45 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` · **Task** T45 — Registration rejects out-of-range passwords |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

| ID | one sentence |
|---|---|
| **R1** | The SPA checks the password against **12 to 128 Unicode code points** *"as advisory guidance only"*, submits to `POST /accounts` through the generated client either way, and leaves the server as the authority. |

R1 is the only requirement in scope, and **this task is the clause nobody has rendered yet**: tasks 43 and
44 built the *check* at both ends; the words *"advisory guidance"* have never appeared on screen. A visitor
whose password is 11 code points sees a field marked invalid and **is told nothing**.

R2 (identical acknowledgement) is adjacent and must not be disturbed: whatever this task renders on the
rejection path must not create a second path through the acknowledgement.

## Locked Decisions

| ID | how it constrains **this** task |
|---|---|
| **L11** *WCAG AA, keyboard, live regions, catalogue-only strings* | **The governing constraint.** This task is almost entirely copy and association: every word comes from the catalogue through `t()`, and a message about a field that is not programmatically tied to that field is not accessible — it is a sentence a screen-reader user may never hear. |
| **L12** *phase additivity* | The screen, the counter and T44's table are extended, not restructured. The `advisory` state machine already distinguishes `out-of-range`; this task gives that state a sentence. |
| **L4** *enumeration-safe, identical timing* | The guidance is about the **password the visitor typed**, never about the address or whether it is registered. No new branch may depend on the response. |
| **L6** *generated client only* | Unchanged — the request still goes, out of range or not. |
| **L2 / L3 / L14** | No new credential surface, no storage, no route. |
| **T173 rule 1** | No primitive accepts `className`/`style`, so the guidance cannot be styled by the screen. |
| **T173 rule 2** | A primitive never accepts the attribute that *is* its contract, but **may** accept a state the consumer computes. Which side of that line `aria-describedby` on a field falls is **the design question of this task** (see Open Questions). |
| **T173 rule 3** | No primitive carries copy — the sentence is passed in by the screen. |
| **T44's AC4** | No primitive may carry `maxLength`/`minLength`. A guidance message must not be accompanied by a native cap "for convenience". |

## Files involved

**New**

| | |
|---|---|
| `src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | the named suite, `package.md` §8 |

**Existing, to modify**

| | |
|---|---|
| `src/features/account/RegisterScreen.tsx` | render the guidance when `advisory === 'out-of-range'` |
| `src/i18n/catalogue.ts` | the `register` group grows past the six keys T43 froze *for this task* |
| `src/app/ui/PasswordField.tsx` | **probably** — it accepts no way to point at a message today (phase 0, §5) |
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | its AC6 case names the six reachable keys **in a hardcoded list**; a seventh key must join it. Its other half, which forbids a spelled sentence, iterates `Object.values(en.register)` and widens itself |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | if a primitive gains a prop or the screen gains an element, the pinned lists and element counts there must be grown deliberately |

**Read-only:** `passwordLength.ts`, `contracts/api/auth.yaml`.

## Dependencies

| | |
|---|---|
| **State** | `RegisterScreen`'s `advisory: 'unchecked' \| 'in-range' \| 'out-of-range'`, already computed at submit time |
| **Numbers** | `ADVISORY_MINIMUM_CODE_POINTS`, `ADVISORY_MAXIMUM_CODE_POINTS` — if the copy names the bounds, it must name *these*, not two literals |
| **Primitives** | `Text` (takes an `id` **for `aria-describedby`**), `PasswordField` (takes `aria-invalid`), `StatusRegion` (a polite region, mounted unconditionally) |
| **Precedent** | `SignInScreen` wires `<Text id="sign-in-explanation">` to a button's `aria-describedby`; **no field in this codebase does this** |
| **Rows already written** | T43's 11 and 7-emoji; T44's 129 — each asserts `aria-invalid` **and that the request is still sent** |
| **Catalogue** | `en.register`, six keys, with a comment reserving the guidance copy for this task |

## Acceptance Criteria

| AC | claim | R |
|---|---|---|
| **AC1** | A password of **11 code points** shows the advisory guidance after submit | R1 |
| **AC2** | A password of **129 code points** shows the **same** guidance | R1 |
| **AC3** | A password **inside** the range shows **no** guidance — the negative without which "shows the guidance" is satisfied by always showing it | R1 |
| **AC4** | The guidance is **programmatically associated with the password field**, not merely rendered near it | L11 |
| **AC5** | The guidance appears **only after a submit**, never while typing — the check is submit-time (T43's existing claim, which this task must not break) | R1 |
| **AC6** | **The request is still sent** while the guidance is shown, and nothing is disabled — the advisory clause, now with words beside it | R1 |
| **AC7** | Every word comes from the catalogue; the screen spells none of it, and the new key is reachable | L11 |

## Tests required

| | |
|---|---|
| **Named test** | `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` — a **new** suite |
| **Boundaries** | 11 (out) · 12 (in, no guidance) · 128 (in, no guidance) · 129 (out) — the two bounds and the two steps across them |
| **Astral** | an out-of-range astral password, because this package's counting bug points a different way at each end |
| **Negative with a control** | AC3 is a negative assertion; it needs a positive control proving the query that finds the guidance can find it when it is there |
| **Association** | the guidance's `id` is referenced by the field, measured through the DOM rather than asserted in prose |
| **Inherited, must stay green** | T44's named suite in full — especially the rows asserting the request is sent for out-of-range passwords |
| **Harness** | a fresh copy, re-aimed: the guidance suppressed, shown always, shown while typing, the association dropped, the copy hardcoded, the wrong bound named |

## Open Questions

**None blocks extraction. Three must be answered before implementation, and two are genuinely the owner's.**

| | |
|---|---|
| **OQ-T45-1 — how is the guidance tied to the field?** `PasswordField` accepts no `aria-describedby`. The layer's own precedent (`Text` + `id` + a button's `aria-describedby`) exists but has never been applied to a field. Adding the prop is a **rule 2** judgement: it is an association, not a state, yet `Text.id` exists for precisely this purpose. **For the design challenge**, with a recommendation at the freeze — not for the owner. |
| **OQ-T45-2 — is the guidance ANNOUNCED or merely described?** The screen already owns a polite `StatusRegion` used for `submitting`/`success`. A rejection could be announced there, described on the field, or both. The failure modes differ: a live region can announce at the wrong moment; a description can go unheard if focus never reaches the field. **The owner's call** — it is a product decision about how loudly a client-side advisory speaks. |
| **OQ-T45-3 — one sentence or two?** *"A password of 11 or 129 code points shows the advisory guidance"* names one guidance for two ends. One string naming the range is simpler and is what R1's own wording suggests; two strings can say *"too short"* and *"too long"*. **The owner's call**, since it is the first user-facing copy decision this package has had to make that is not a label. |
