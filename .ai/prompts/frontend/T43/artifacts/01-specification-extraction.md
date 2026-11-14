<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). No design, no code. -->

# frontend · T43 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim, `spec/frontend/tasks.md:77`):**
> **Registration accepts 12 characters.** A 12-character password is accepted client-side as advisory.
> Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1. Blocked on 173.

---

## Business Rules

| ID | |
|---|---|
| **R1** (`requirements.md:9-11`) | WHEN a visitor submits the registration form, the SPA SHALL check that the password is **12 to 128 Unicode code points as advisory guidance only**, and SHALL **submit to `POST /accounts` through the generated client**. *"The server is the authority."* Three obligations in one sentence: a form, an advisory check, and a submit. This task owns the **acceptance** end of the check; tasks 44–46 own 128, the rejection and the counting unit |
| **R2** (`requirements.md:12-13`) | The acknowledgement is identical whether or not the email exists, with no client-side branching or delay. **Task 47's**, and it constrains this task only negatively: nothing here may branch on the response |
| **R58 / L10** | Only the public configuration keys of §4c are baked into the bundle. Registration adds no key |
| **R29 / L16** | Nothing is stored client-side beyond the L16 carve-out. A registration form stores **nothing** — not a draft, not an email, not a password |
| **R30 / L4** | Enumeration safety on every public surface. The registration screen is public, so its copy, redirects, styling and timing may not differ by account state |

## Locked Decisions

| ID | how it binds this task |
|---|---|
| **L14** | `register` is **already a declared public path** (`design.md:138`, `routes.tsx`'s `PUBLIC_ROUTE_PATHS`). Attaching a screen to it adds **no** route. *"A new public route … requires an ADR"* — this task needs none, which is the point |
| **L11** | WCAG AA, keyboard operation, managed focus, **live regions for async results**, catalogue-only strings. A form field needs a programmatic label; an advisory message needs to be announced |
| **L6** | Generated client only. The submit, if in scope, goes through `createAuthApi` — enforced by a lint rule *and* by `api/lintRejectsHandWrittenBackendFetch.test.ts` |
| **L13** | The confirmed stack names **TanStack Query** and **Zustand** — and after 42 tasks **neither is installed** (`frontend/package.json` lists neither). Any submit state this task needs is the first occasion either would be used, or the first documented reason not to |
| **L12** | Phase additivity: this screen must be something tasks 44–47 extend, not replace |
| **L3 / L2** | Tokens in memory only; interactive login is SAS-hosted. **Registration is not login**: `POST /accounts` is a public operation (`api/client.ts:6-12`), returns no token, and starts no session |
| **L4** | above, under R30 |
| **L16** | the storage list is closed; registration adds nothing to it |
| **Q7** (resolved 2026-10-10) | *"Tailwind CSS + headless/unstyled components (e.g. Radix UI) for accessible primitives (dialogs, dropdowns, etc.). Fully custom look, no fighting a pre-themed library."* This is the authority T173 built `app/ui/` under, and the authority under which a **field** layer gets built |
| **`app/ui/index.ts` rules 1–3** | no `className`/`style` on a primitive; the contract attribute is set inside; no copy in a primitive |
| **T173 §6e** | no lowercase JSX tag in a render site; the primitive list's size is pinned and *"a ninth must be added deliberately"* |
| **`agents.md`** | catalogue-only copy; keyboard-operable; managed focus; live region for async results |

## Files involved

**Read / extend (existing)**

| file | why |
|---|---|
| `app/routes.tsx` | attach the screen inside the existing public `.map()` conditional — no new path |
| `app/ui/index.ts` + `app/ui/*` | the nine primitives, and the header paragraph that asks the first form-field consumer to read it |
| `i18n/catalogue.ts` | a new group for the screen's copy |
| `api/client.ts` | `createAuthApi`, `AUTH_PUBLIC_OPERATIONS` (already names `POST /accounts`) |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` · `app/routesResolveUnderAppPrefixOnly.test.tsx` · `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` · `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the pinned lists this task grows (phase 0 §4) |
| `auth/SignInScreen.tsx` · `app/HomeScreen.tsx` | the two models for a screen, and the first is the one that proves **no password field belongs on the sign-in screen** — scoped to that component's own source (`signInStarts…:187-189`), so a registration field does not trip it |

**New (the spec expects, this task or a later one)**

- a registration screen component — **no `account/` directory exists**
- the named suite `registrationAcceptsTwelveAndOneTwentyEightCodePoints`
- a field layer: at least one new primitive, or Q7's headless library with its first real consumer
- a code-point counting helper, if the count is not inline

## Dependencies

`react`, `react-router` (the route element), `react-i18next` · `app/ui/` · `i18n/catalogue.ts` ·
`api/client.ts` → `@themistra/api-client`'s `registerAccount` (`POST /accounts`, `security: []`) ·
`contracts/api/auth.yaml`'s `RegisterAccountRequest` — **`required: [email, password]`**, `email` with
`format: email` and `maxLength: 254`, `password` a bare `type: string` with **no bounds in the schema** ·
`RegistrationAcknowledgement` on a **202, always** · **no config key, no new npm dependency implied by the
spec itself**.

## Acceptance Criteria

| | criterion | requirement |
|---|---|---|
| **AC1** | `/app/register` renders a registration form: an email field and a password field, each with a programmatic label, and one submit control | **R1**, L11, L14 |
| **AC2** | A password of **exactly 12 code points** is **accepted** — no advisory message, nothing blocking the submit | **R1** |
| **AC3** | The count is of **Unicode code points**, not UTF-16 units, at the one boundary this task can see: a 12-code-point password made of astral characters (24 UTF-16 units) is accepted too | **R1** (task 46 owns the general claim) |
| **AC4** | The check is **advisory only**: it is a client-side convenience, the request still goes to the server, and nothing here treats the client as the authority | **R1** |
| **AC5** | The submit goes through the **generated client** (`POST /accounts`), with no hand-written `fetch`, no URL built by hand, and no second path to the backend | **R1**, **L6** |
| **AC6** | Every string on the screen comes from the catalogue, asserted against the entry **and** as literal English; no raw key reaches the DOM | **L11**, `agents.md` |
| **AC7** | The screen renders through `app/ui` primitives only and bears **no lowercase JSX tag**; the pinned lists name it and its new primitives | **T173 §6e** |
| **AC8** | The screen is reachable **without a session** and adds no route: `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` are unchanged in content, spelling and order | **L14**, R28 |
| **AC9** | Keyboard-operable end to end: each field reachable and labelled, the submit reachable, a visible focus ring on every interactive element | **L11** |
| **AC10** | Nothing is written to any storage and no identity is read: no draft, no remembered email, no token read | **R29**, **L16**, **L3** |
| **AC11** | Nothing branches on the response, and no delay is introduced | **R2**, **L4** |

## Tests required

**Named (`package.md` §8)**

| | |
|---|---|
| `registrationAcceptsTwelveAndOneTwentyEightCodePoints` (`package.md:182`) | **this task writes it.** It does not exist in `src/`. Task 44 extends it for 128, so the file must be built to hold both ends from the start |
| `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` (`:105`) | **task 45's.** Not written here |
| `passwordLengthCountsCodePoints` (`:183`) | **task 46's.** Not written here — but AC3 keeps this task from making it unpassable |

**Boundary and implied**

- exactly 12 accepted · **11 is task 45's**, so the lower boundary is only approached from above here
- 12 code points of astral characters (24 UTF-16 units) accepted — the one code-point case in scope
- an empty password, and an empty email: **I do not know** whose task owns required-ness; nothing in R1 says
- the submit reaching the generated client exactly once
- no storage write, no identity read, no response branching
- the inherited pins: primitive list, lowercase-tag bearers, render sites, the union,
  `SCREENS_ATTACHED_SO_FAR` (in **route order** — `register` sits third, after `callback`), the catalogue key
  set, and `homeRoute…`'s *"no OTHER screen takes focus"* guard, which loops over three screens today

## Open Questions

1. **OQ-T43-1 — does this task build the form, or only the rule?** `tasks.md` has no *"build the registration
   form"* task: 43–46 are the length rule, 47 is the acknowledgement. R1 names a form, a check **and** a
   submit in one sentence, and the check cannot be measured without something to type into. Three readings:
   (a) the whole form including the `POST /accounts` submit — this task becomes the **first caller of the
   data path** (OQ-T33-2), as T42 was of the sign-out path; (b) the form and the check, with the submit
   stubbed and deferred to 47; (c) the check as a pure function, with the form deferred — which would leave
   `registrationAccepts…` measuring a helper rather than the behaviour its name claims. **Phase 3's first
   target, and an owner question if phase 3 cannot settle it.**
2. **OQ-T43-2 — how is the field layer built?** `app/ui/index.ts:7-12` asks the first form-field consumer to
   decide deliberately, and names Q7's headless library as the option. Three readings: new primitives of our
   own (`Form`, `TextField`, `FieldError`); a headless library installed **with this real consumer**, as Q7
   and the header invite; or lowercase tags in the screen as the first exception to §6e. The second changes
   `package.json`, which no task so far has done for a runtime dependency. **Phase 3's second target.**
3. **OQ-T43-3 — is any advisory *copy* in scope?** Task 45 owns the rejection. A screen that only ever
   accepts may need no guidance string, yet a form with no guidance to show when the rule fails is half a
   feature and task 45 would then be writing copy for a check written here. **I do not know** which the
   owner prefers; naming it now so phase 4 freezes it rather than discovering it.
4. **OQ-T43-4 — L13's TanStack Query and Zustand are still not installed.** A submit has pending, success
   and failure states. Either this task introduces the stack's state library for them, or it records why a
   single `useState` is enough and leaves the stack's promise unfulfilled for the 43rd task running. Not a
   blocker; a decision that should be made once, out loud.
5. **Not blockers, carried from the package:** **Q5** (refresh tokens) and **Q12** (auth contract additions)
   are closed or irrelevant here; `POST /accounts` is fully specified in `auth.yaml`. **No `package.md` §11
   question blocks this task** — which is itself worth stating, because 38 is still blocked on Q15.
