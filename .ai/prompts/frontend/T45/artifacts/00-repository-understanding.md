<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code, no design, no requirements. -->

# frontend · T45 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` · **Task** T45 — Registration rejects out-of-range passwords |
| **Task statement** | *"A password of 11 or 129 code points shows the advisory guidance. Verifies `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints`. Cites R1."* |

## 1. Architecture summary

A React 19 + TypeScript SPA (`frontend/`), Vite 8, Vitest 5 + jsdom, react-router 8 under the `/app`
basename, Tailwind 4 CSS-first. There is **no persistence, no outbox and no event bus** in this package;
`agents.md`'s JPA/Flyway/Testcontainers clauses have no analogue here. Security is OIDC through a
SAS-hosted sign-in (L2) with tokens held **in memory only** (L3). Every HTTP call goes through the
generated client (L6). Every rendered element comes from the thirteen primitives in `src/app/ui` (T173's
three rules), and every word comes from `src/i18n/catalogue.ts` through `t()` (L11).

## 2. Existing code this task touches

| | exists? | what it is today |
|---|---|---|
| `features/account/RegisterScreen.tsx` | **exists** | holds `advisory: 'unchecked' \| 'in-range' \| 'out-of-range'`, computed at submit, and passes `aria-invalid` to the password field. **It renders no sentence about the result.** |
| `features/account/passwordLength.ts` | **exists** | `countCodePoints`, `ADVISORY_MINIMUM_CODE_POINTS = 12`, `ADVISORY_MAXIMUM_CODE_POINTS = 128` — a count and two numbers, never a verdict (T43's D6) |
| `app/ui/PasswordField.tsx` | **exists** | takes `label`, `value`, `onChange`, `'aria-invalid'?`. **It cannot carry or point at a message.** |
| `i18n/catalogue.ts` → `register` | **exists, six keys** | `heading`, `emailLabel`, `passwordLabel`, `submit`, `submitting`, `success` |
| the 11 and 129 rows | **exist** | T44 put 129 beside T43's 11; both assert `aria-invalid === 'true'` **and that the request is still sent**, with no copy |
| `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | **NEW** | `features/account/` holds three files and this is not one of them |

**Two comments in the existing code name this task by number**, and both say the same thing: the words are
task 45's to choose.

- `catalogue.ts:84-88` — *"There is no advisory, rejection, strength or hint copy here: task 45 owns the
  guidance shown when the 12-to-128 check fails, and it should choose those words rather than inherit them
  from the task that wrote the check."*
- `PasswordField.tsx` on its `aria-invalid` prop — *"The WORDS that explain it are task 45's… until then
  this is a state with no sentence, which is deliberate and incomplete rather than wrong."*

## 3. Established patterns to follow

| | |
|---|---|
| **Copy** | one catalogue group per screen, read with `t('group.key')`; a scan in the named suites forbids a spelled sentence in a screen's source |
| **Primitives** | a screen renders no element of its own. A sentence is `<Text>`; a heading is `<Heading>`; nothing else is permitted |
| **Rule 2** | a primitive never accepts the attribute that *is* its contract (`type`, `role`, `htmlFor`/`id`), but **may** accept a state the consumer computes — `ActionButton`'s `aria-disabled`, `PasswordField`'s `aria-invalid` |
| **The association precedent already exists** | `Text` takes an `id` prop **for exactly one reason — `aria-describedby`** — and `ActionButton` accepts `aria-describedby`. `SignInScreen` wires the two together: `<Text id="sign-in-explanation">` plus `aria-describedby="sign-in-explanation"` on the button, with a comment explaining that a keyboard user tabbing to the control would otherwise never hear the sentence. **The pattern exists on a BUTTON; no field uses it, and `PasswordField` does not accept it.** |
| **Live regions** | `StatusRegion` is a polite region that renders **unconditionally** and is filled later, because a region mounted with its text already in it announces nothing. The register screen already uses it for `submitting` / `success`. |
| **Advisory means advisory** | every row in T43's table reaches the transport, including the out-of-range ones. Nothing is disabled, nothing is blocked (R1) |
| **Tables over copied cases** | T43 built `PASSWORDS` as a table so T44 could add rows; T44 did, and the control now pins both bounds |

## 4. Testing conventions

Vitest + jsdom; one named suite per task named after `package.md` §8; cases named for the claim, not the
mechanism; a control beside every negative assertion; a mutation harness whose rows each predict
`own` / `inherited` / `unfalsifiable`, with canaries scored apart and BROKEN detected by a drop in the
total. **Tests are committed before the harness runs** — it reverts with `git checkout` + `git clean` over
`frontend/src/`. **And, new as of T44: the harness runs with the local Docker stack stopped** — the stack's
load makes the sign-in suites flake, which is FU-T42-1.

Two environmental facts that hold here: this host's `oxlint@1.87.0` prints no summary line, so one case of
`lintRejectsHandWrittenBackendFetch` fails before and after any change; and jsdom performs no interactive
constraint validation, so anything resting on native validation is unmeasurable in this suite.

## 5. Known gaps and unknowns

| | |
|---|---|
| **There is no way to attach a message to the password field today** | `PasswordField` accepts no `aria-describedby`, no `description`, no `error`. A sentence rendered beside it would be visible but **not programmatically associated**, which is the half of WCAG 2.1 3.3.1/3.3.3 that matters to a screen-reader user. Whether this task extends the primitive, adds a new one, or associates by some other route is **phase 1's and the design challenge's**, not phase 0's. |
| **Whether the guidance must be ANNOUNCED or merely associated** | The screen already owns a polite live region for `submitting`/`success`. A submit-time rejection could be announced there, or described on the field, or both — and the two have different failure modes: a live region read at the wrong moment, versus a description a visitor never hears because focus never reaches the field. **I do not know** which the owner intends. |
| **Whether one sentence covers both ends** | The task says *"a password of 11 or 129 code points shows the advisory guidance"* — one phrase, two boundaries. Whether that is one string naming the range or two strings naming each end is a copy decision this task owns, and the catalogue comment says it should choose. |
| **Whether the `register` group may grow** | T43 froze it at six keys *because* this task was coming. Growing it is expected; by how much is this task's call. |
| **The email field is not in scope** | R1 bounds the password. T43's own case asserts the email field is **not** marked, and nothing in the client judges an address. |
| **`/app/verify` renders nothing** | Found while rehearsing a demo, not by this task: the route is declared with no element until task 48. Out of scope here; noted so it is not rediscovered as a bug. |
