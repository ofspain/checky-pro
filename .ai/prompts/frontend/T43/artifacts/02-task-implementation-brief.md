<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). The ONLY spec the later phases use. -->

# frontend · T43 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` · **Consumes** `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

> **AMENDED 2026-11-14, before the Phase 3 gate.** The owner closed both blockers:
> **OQ-T43-1 → reading (a)** — this task builds the form, the advisory check **and** the real
> `POST /accounts` submit through the generated client, which makes it the **first caller of the dormant
> data path** (`createAuthApi`, OQ-T33-2), as T42 was of the sign-out path.
> **OQ-T43-2 → our own primitives** — a field layer written like the nine, no new runtime dependency.
> Every clause the amendment touches is marked **(owner, OQ-T43-1)** or **(owner, OQ-T43-2)**. Nothing
> frozen was deleted: the brief had not reached the Phase 4 gate. One consequence of (a) is a **new**
> question the amendment creates, **OQ-T43-5**, and it is Phase 3's target.

## Task

**T43 — Registration accepts 12 characters.** *"A 12-character password is accepted client-side as advisory.
Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1. Blocked on 173."*
(`spec/frontend/tasks.md:77`; 173 closed PASS 2026-11-12.)

## Purpose

`/app/register` is a declared public route (L14) that renders an empty document. R1 requires a registration
form whose password check is **advisory** — 12 to 128 Unicode code points, with the server as the authority.
This task is the **acceptance** end of that check, and therefore the first task that has to make the form
exist. Tasks 44 (128), 45 (rejection) and 46 (code points, not UTF-16) extend what is built here, and 47 adds
the acknowledgement, so this brief's job is to leave them something to extend.

It is also the point `app/ui/index.ts:7-12` wrote a note for: *"No screen in this application renders … a
form field … The first screen that needs one … should install it with a real consumer, and should start by
reading this paragraph."* T173 guessed MFA enrollment; registration arrived first.

## Scope

**In**

- a registration screen attached to the **existing** `register` path, with an email field, a password field,
  each programmatically labelled, and one submit control
- the advisory length check at its **accepting** boundary: exactly 12 **code points** passes, and passes for
  astral characters too (24 UTF-16 units, 12 code points)
- **(owner, OQ-T43-1)** the submit: one `POST /accounts` through `createAuthApi`, the **first production
  caller** the generated auth client has ever had
- **(owner, OQ-T43-2)** the field layer, as **new primitives of our own** in `app/ui/`, written under the
  same three rules as the nine — no `className`/`style`, the contract attribute set inside, no copy
- the catalogue group for the screen's copy
- growing the inherited pinned lists that name screens, primitives, render sites and catalogue keys
- the named suite `registrationAcceptsTwelveAndOneTwentyEightCodePoints`, built to hold task 44's end too

**Out**

- the **rejection** path and its advisory message (task 45) · the **128** case (task 44) · the general
  code-point claim (task 46) · the acknowledgement screen and `register/sent` (task 47)
- verification, resend, reset, change-password, breach copy (tasks 48–57)
- any change to `PUBLIC_ROUTE_PATHS` or `AUTHENTICATED_ROUTE_PATHS` (L14 — an ADR, which this task does not need)
- `RequireSession`, `AuthenticatedLayout`, `AppShell`, `mountShell`, the sign-in/callback/signed-out/home screens
- anything under `spec/` · `phase1ScreensPassAccessibilityChecks` (commissioned by no task, OQ-T173-4/OQ-T174-3)

## Business Rules

| ID | |
|---|---|
| **R1** | A submitted registration form is checked client-side for a password of 12–128 Unicode code points **as advisory guidance only**, and submitted to `POST /accounts` **through the generated client**. The server is the authority |
| **R2** | The acknowledgement is identical for existing and new emails, with no client-side branching or delay — **task 47's**, and binding here only as a prohibition: nothing branches on the response |
| **R30 / L4** | Enumeration safety on a public surface: copy, redirects, styling and timing identical across account states |
| **R29 / L16** | Nothing stored client-side beyond the L16 carve-out — no draft, no remembered email, no password |
| **R58 / L10** | No new configuration key reaches the bundle |

## Locked Decisions

**L14** (the path exists; adding one needs an ADR) · **L11** (WCAG AA, keyboard, managed focus, live region
for async results, catalogue-only strings) · **L6** (generated client only) · **L13** (confirmed stack —
TanStack Query and Zustand are named and still uninstalled, **OQ-T43-4**) · **L12** (phase additivity: 44–47
extend this) · **L3 / L2** (registration is not login; it returns no token and starts no session) ·
**L4** · **L16** · **L10** · **Q7** (resolved: Tailwind + headless/unstyled primitives, fully custom look) ·
`app/ui/index.ts` **rules 1–3** · **T173 §6e** (no lowercase JSX tag in a render site; the primitive count is
pinned and a new primitive is added deliberately) · `agents.md`'s catalogue and accessibility rules.

## Dependencies

`react` · `react-router` (the route element only) · `react-i18next` · `app/ui/` (nine primitives) ·
`i18n/catalogue.ts` · **(owner, OQ-T43-1)** `api/client.ts` → `createAuthApi`, and `AUTH_PUBLIC_OPERATIONS`, which **already
names `POST /accounts`** — so the call carries **no bearer** and a 401 on it is never renewed · `@themistra/api-client`'s `registerAccount` · `contracts/api/auth.yaml` —
`RegisterAccountRequest` **`required: [email, password]`**, `email` `format: email` `maxLength: 254`,
`password` a bare `type: string` with **no bounds**, and a **202 always**. **No new config key.** Any new npm
dependency is **excluded** by the owner's answer to OQ-T43-2.

## Inputs

What the visitor types: an email and a password. Nothing else — no props, no query parameter, no storage read.

## Outputs

Rendered DOM, and **(owner, OQ-T43-1)** exactly one `POST /accounts` request through the generated client
per submission — no second path to the backend, no retry of its own.

## State Changes

**No persistent state.** No storage write, no module-level state, no catalogue key removed or renamed, no
path added. The form's own transient state lives for the life of the screen (**OQ-T43-4** decides whose).

## Files to Create

- the registration screen component — **no `account/` directory exists**; `auth/` and `app/` are the two
  precedents (`auth/SignInScreen.tsx`, `app/HomeScreen.tsx`)
- `src/.../registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` — the named suite, which **does not
  exist**: the three registration named tests appear only in `package.md` and `tasks.md`
- **(owner, OQ-T43-2)** the new primitive file(s) in `app/ui/` — a labelled text field at minimum, and
  whatever holds the form element; written like the nine, exported from the barrel, added to the pinned list
  deliberately
- a code-point length helper, if the count is not inline (task 46 will own its general claim)

## Files to Modify

| file | change |
|---|---|
| `app/routes.tsx` | attach the screen inside the **existing** public `.map()` conditional. No new path |
| `app/ui/index.ts` | export any new primitive; the pinned list of nine grows deliberately |
| `i18n/catalogue.ts` | one new group |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `PRIMITIVES` (9), lowercase-tag bearers (8), `RENDER_SITES` (7), the union (16), `REQUIRED_UTILITIES`, the ref-target map, the catalogue key set |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` — four entries, **in route order**, so `register` sits **third**, after `callback` |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | the probe walk and whatever it pins about public screens |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the union pin, and the *"no OTHER screen takes focus"* guard, which loops over three screens today |

## Files NOT to Modify

`auth/RequireSession.tsx` · `app/AuthenticatedLayout.tsx` · `app/AppShell.tsx` · `app/mountShell.tsx` ·
`auth/SignInScreen.tsx` · `auth/CallbackScreen.tsx` · `auth/SignedOutScreen.tsx` · `app/HomeScreen.tsx` ·
`auth/accessToken.ts` · `auth/endSession.ts` · `auth/signOut.ts` · `api/client.ts` (read only — `POST
/accounts` is already public) · `app/styles.css`'s `@theme` block (a new token is T173's ground, not this
task's) · anything under `spec/` · any other task's named suite **except** the four pinned-list files above.

## Acceptance Criteria

| | criterion | rule |
|---|---|---|
| **AC1** | `/app/register` renders a form with an email field, a password field, each with a programmatic label, and one submit control | R1, L11, L14 |
| **AC2** | A password of **exactly 12 code points** is accepted: no advisory message, nothing blocking submission | **R1** |
| **AC3** | The count is of **code points**: a 12-code-point astral-character password (24 UTF-16 units) is accepted on the same terms | **R1** |
| **AC4** | The check is advisory: it gates nothing the server decides, and the request is still made | **R1** |
| **AC5** | The submit goes through the generated client (`POST /accounts`), with no hand-written `fetch`, no hand-built URL, and exactly one path to the backend | R1, **L6** |
| **AC6** | Every string comes from the catalogue, asserted against the entry **and** as literal English; no raw key in the DOM | **L11** |
| **AC7** | The screen renders through `app/ui` primitives only and bears no lowercase JSX tag; the pinned lists name it and any new primitive | **T173 §6e** |
| **AC8** | No route is added: both declared sets are unchanged in content, spelling and order, and the screen needs no session | **L14**, R28 |
| **AC9** | Keyboard-operable: both fields and the control reachable and labelled, with the focus ring T173 pins on every interactive element | **L11** |
| **AC10** | No storage write, no identity read, no token read | R29, **L16**, L3 |
| **AC11** | Nothing branches on the response and no delay is introduced | R2, **L4** |

## Required Tests

- **`registrationAcceptsTwelveAndOneTwentyEightCodePoints`** — written here for the 12 end, structured so
  task 44 adds the 128 end without restructuring. Covers AC1–AC11.
- **Boundary cases in scope:** exactly 12 accepted · 12 code points of astral characters accepted · the
  submit reaching the generated client exactly once · no storage write · no response branching.
- **Out of scope and not to be written here:** 11 and 129 (task 45), 128 (task 44), the general code-point
  claim (task 46). Whether an **empty** field is required-ness or validation is **unowned by any task** —
  do not invent a rule.
- **Inherited instruments to grow, not rewrite:** the four pinned-list files in *Files to Modify*.
- **A mutation harness** (thirteenth copy) with at least one row per AC, each predicting
  `own` / `inherited` / `unfalsifiable`; tests committed before it runs.

## Constraints

- **Security.** No password in any storage, any log, any URL, any analytics, or any DOM attribute that
  survives the keystroke. No token read. The request goes over the generated client with **no bearer**
  (`POST /accounts` is in `AUTH_PUBLIC_OPERATIONS`), and a 401 on it is never renewed.
- **Enumeration safety.** Nothing on this screen may differ by whether the email exists — which, for this
  task, means nothing may branch on the response at all (R2 is task 47's to satisfy positively).
- **Module boundaries.** The screen may import from `app/ui`, `react`, `react-router`, `react-i18next`,
  `i18n/catalogue`, and — if OQ-T43-1 resolves to include the submit — `api/client`. It must **not** import
  from `auth/`: registration is not login (L2), reads no token (L3), and starts no session.
- **Null handling.** Every field starts empty and an empty field is not an error this task defines. The
  check must not throw on an empty string, a lone surrogate, or a 10 000-character paste.
- **Performance / thread-safety / transactions.** Not applicable to a browser form with no persistence; the
  only cost worth naming is that a code-point count must not be O(n²) on a long paste.
- **Phase additivity (L12).** Tasks 44–47 extend this screen. It must not take a shape that forces them to
  restructure it, and it must not claim the acknowledgement route.

## Open Questions

**OQ-T43-1 is CLOSED by the owner (2026-11-14): reading (a)** — form, check and submit. **OQ-T43-2 is CLOSED
by the owner: our own primitives**, no new runtime dependency. With them, **OQ-T33-2** — *"nothing calls
`createAuthApi`, `readAccount` or `readProfile`, so the whole authenticated data path is dormant"* — closes
for its **registration** half. Its authenticated half does **not**: tasks 5, 6, 30, 31 and 33 stay dormant,
and this task does not touch them.

1. **OQ-T43-5 — what does a SUCCESSFUL submit do, when task 47 owns the acknowledgement?** Created by the
   owner's answer, not inherited. `POST /accounts` always returns **202** (`auth.yaml`, *"regardless of
   whether the email was already registered"*), task 47 owns *"Registration acknowledgement is identical"*
   and `register/sent` is a declared path with no element. So this task can submit and has nowhere
   sanctioned to land. Readings: **(i)** navigate to `register/sent`, which renders an empty document today —
   a visitor would see a blank page, which is worse than the form; **(ii)** announce a neutral result in a
   `StatusRegion` on the form itself, satisfying L11's *"live regions for async results"* with copy task 47
   may then supersede; **(iii)** leave the submit's result invisible, which fails L11 outright. **Phase 3's
   target**, and the one question that must be settled before phase 4 freezes.
2. **OQ-T43-3 — is advisory *copy* in scope?** Task 45 owns the rejection. **Standing recommendation, for
   phase 4 to freeze:** no guidance string here. A key written but never rendered is unused code of exactly
   the kind `app/ui/index.ts` refuses to ship, and 45 should choose the words it shows. **Not a blocker.**
3. **OQ-T43-4 — L13 names TanStack Query and Zustand; neither is installed after 42 tasks**, and the owner's
   answer to OQ-T43-2 makes it clear no dependency is being added for this task. A one-shot public mutation
   has no cache to invalidate and no state to share across screens, which is what both libraries exist for,
   so local state is the likely answer — **but it should be recorded as a decision, not a default**, because
   the next form inherits it. **Not a blocker.**
