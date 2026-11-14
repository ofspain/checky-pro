<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). The ONLY specification phases 5-13 use. -->

# frontend · T45 · Phase 2 — Task Implementation Brief

## Task

**T45 — Registration rejects out-of-range passwords.** *"A password of 11 or 129 code points shows the
advisory guidance. Verifies `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints`. Cites
R1."*

## Purpose

R1 asks for a check *"as advisory guidance only"*. Tasks 43 and 44 built the check and proved it at both
ends; **no task has yet rendered the guidance.** Today a visitor who types eleven characters and presses
the button sees a field outlined as invalid, a status line saying the account is being created, and **not
one word about what is wrong**. This task supplies the words, ties them to the field they are about, and
keeps the submit exactly as advisory as it was.

## Scope

**In** — one catalogue string; rendering it when the submit-time check says out of range; associating it
with the password field so assistive technology reads the two together; the new named suite; a harness.

**Out** — blocking, disabling, debouncing or truncating anything (R1) · validating the **email** (nothing
in the client judges an address) · moving focus on submit (T43 left that as OQ-T43-10 and it is a change to
the submit path, not to the guidance) · the acknowledgement screen (task 47) · the counter (task 46) · any
`maxLength`/`minLength` (T44's AC4 forbids it on every primitive).

## Business Rules

| ID | one line |
|---|---|
| **R1** | The password is checked against **12 to 128 Unicode code points** as **advisory guidance only**; the form submits either way and the server is the authority. |
| **R2** | *(not to be disturbed)* the acknowledgement is identical either way — the rejection path must not branch it. |

## Locked Decisions

| ID | binding effect |
|---|---|
| **L11** | Governing. Catalogue-only strings, WCAG **AA**, keyboard, live regions. An error text that is not programmatically associated with its control fails 3.3.1 in practice even when it is visible. |
| **L12** | Additive. The `advisory` state already exists and already distinguishes `out-of-range`; this task gives that state a sentence rather than reworking the state. |
| **L4** | No branch or delay that depends on the response; the guidance is about the typed value only. |
| **L6 / L2 / L3 / L14** | unchanged — same client, no credential surface, no storage, no route. |
| **T173 rules 1-3** | the screen renders no element of its own; the sentence is a `Text`; no primitive carries copy; no primitive accepts `className`. |
| **T44 AC4** | no primitive may gain `maxLength`/`minLength`. |

## Dependencies

`RegisterScreen`'s `advisory` state · `ADVISORY_MINIMUM_CODE_POINTS` / `ADVISORY_MAXIMUM_CODE_POINTS` ·
`Text` (has an `id` prop **for `aria-describedby`**) · `PasswordField` (has `aria-invalid`, **has no way to
point at a message**) · `StatusRegion` · `en.register` (six keys, frozen for this task) · T44's `PASSWORDS`
table and its control.

## Inputs

A typed password of any length, including astral characters; a typed email. Nothing from storage, the URL
or the environment.

## Outputs

Unchanged on the wire: `POST /accounts` with `{ email, password }`, **sent whether or not the guidance is
showing.** New on screen: one sentence, associated with the password field.

## State Changes

**None new.** `advisory` already moves `unchecked → in-range | out-of-range` at submit. This task reads it.

## Files to Create

| | |
|---|---|
| `src/features/account/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | the named suite |

## Files to Modify

| | |
|---|---|
| `src/features/account/RegisterScreen.tsx` | render the guidance for `out-of-range` |
| `src/i18n/catalogue.ts` | **one** new key in `register` (D-T45-2) |
| `src/app/ui/PasswordField.tsx` | expected — it must be able to point at the message (mechanism is phase 3's, constraint below) |
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | its AC6 case lists the reachable keys **by hand**; the seventh joins it |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | the element counts and pinned prop lists there are grown deliberately if the screen or a primitive changes shape |

## Files NOT to Modify

`spec/**` · `contracts/**` · `passwordLength.ts` · `routes.tsx` · `api/**` · any other task's named suite
beyond the one AC6 line above.

## Acceptance Criteria

| AC | claim |
|---|---|
| **AC1** | 11 code points → the guidance is shown after submit |
| **AC2** | 129 code points → **the same** guidance, word for word |
| **AC3** | a password inside the range → **no** guidance, with a control proving the query can find it when it is there |
| **AC4** | the guidance is **programmatically associated** with the password field, measured through the DOM |
| **AC5** | nothing is shown before the first submit, however long or short the typing |
| **AC6** | the request is **still sent** and nothing is disabled while the guidance is up |
| **AC7** | the sentence comes from the catalogue, the screen spells none of it, and the new key is reachable |

## Required Tests

The named suite, with: 11 · 12 · 128 · 129 · an **astral** out-of-range password · a typing-only case ·
a request-still-sent case · an association case · AC3's control. Plus a mutation harness whose rows include
the guidance suppressed, always shown, shown while typing, the association dropped, the copy hardcoded, and
the bound named wrongly. T44's suite must stay green in full.

## Constraints

- **Security / enumeration.** The guidance describes the **typed password**, never the account, never the
  address, never the response (L4, R2). No password value, length or hash may appear in any message.
- **Accessibility.** AA. The association is the requirement, not a nicety; a visible-only message is a
  defect under L11 even though jsdom will happily render it.
- **Module boundaries.** Screens render only through `src/app/ui`; copy only through `t()`.
- **Null handling.** The empty string is out of range and must produce the same guidance as 11 — the count
  is total and must stay so.
- **Performance / thread-safety / money / transactions.** Not applicable.

## Open Questions

**No blockers.** Three questions were raised at extraction; **all three are decided here** so no later
phase guesses, and all three are flagged for the phase 4 gate as cheap to overturn.

| | decision |
|---|---|
| **D-T45-1 — the guidance is DESCRIBED on the field, not ANNOUNCED in the live region.** `StatusRegion` says what just happened to the *request* (`submitting`, then `success`), and because R1 sends the request anyway, an announcement of the advisory would be **immediately overwritten by "Creating your account…"** — the live region is the one place this message cannot survive. `aria-invalid` plus a description is also the standard pairing for a field-level message, and WCAG 3.3.1 asks for the error to be *described in text*, not announced. **Residual risk, stated rather than hidden:** a screen-reader user who never moves focus back to the field may not hear it, because T43 deliberately does not move focus on submit (OQ-T43-10). Moving focus would be a change to the submit path and is out of scope here. |
| **D-T45-2 — ONE string naming the rule, not two naming each end.** R1 is one rule (*"12 to 128 Unicode code points"*), and one string keeps the screen from branching on *which* end failed — a second code path for a message that says the same thing. Guidance that names the rule reads as help; a message that diagnoses the input ("too short") reads as a verdict, which an *advisory* check has no standing to deliver. It also keeps the catalogue at seven keys. The string must name the bounds by **the constants**, not by two literals that can drift. |
| **D-T45-3 — the association mechanism is phase 3's**, with one constraint fixed here: it must be **measurable in the DOM** (an id the field actually points at), and the screen may not render a bare element to achieve it. The layer's own precedent is `Text`'s `id` prop plus `aria-describedby`, used today on a button and never yet on a field. |
