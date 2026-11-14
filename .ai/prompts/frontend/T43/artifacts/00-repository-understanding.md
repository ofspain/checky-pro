<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). No code written. -->

# frontend · T43 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Spec section** | Phase 1 — Auth & account: **registration and reset** (a new section; 42 closed the routes section) |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Read:** `spec/frontend/{package,requirements,design,tasks,agents}.md` · `contracts/api/auth.yaml` ·
`frontend/src/**` (the tree, not every file) · `.ai/prompts/frontend/T42/artifacts/*` for the inherited
instrument list.

> **The one thing to know before anything else.** `app/ui/index.ts:7-12` says, in the layer's own header:
> *"No screen in this application renders a dialog, dropdown, popover, menu, tooltip, tab set, **form field**
> or table … so installing one now would ship unused code. **The first screen that needs one (MFA enrollment,
> R11–R13, is the likely first) should install it with a real consumer, and should start by reading this
> paragraph."*** Task 43 is that screen, two tasks earlier than T173 guessed. Its guess about *which* screen
> was wrong; its instruction is addressed to whoever arrived first, and that is this task.

---

## 1 · Architecture summary

A React 19 + TypeScript SPA under `frontend/`, Vite 8, Vitest 5 on jsdom, react-router 8 with the `/app`
basename. There is **no persistence layer, no outbox, no event bus and no JPA/Flyway here** — those sections
of the phase template describe the Spring services and do not apply.

| layer | what is there |
|---|---|
| **routing** | `app/routes.tsx` — one table, two declared path sets (`PUBLIC_ROUTE_PATHS` 9, `AUTHENTICATED_ROUTE_PATHS` 11), an index route, a catch-all, the T40 gate (`RequireSession`, pathless) and, as of T42, the authenticated layout nested inside it |
| **design system** | `app/ui/` — **nine** primitives behind one barrel, Tailwind 4 CSS-first with tokens in a single `@theme` block in `app/styles.css`. Three rules, each enforced by a type: no `className`/`style`, the contract attribute is set inside, no copy in a primitive |
| **copy** | `i18n/catalogue.ts` — six groups (`rateLimit`, `signIn`, `callback`, `signedOut`, `home`, `shell`). **No registration group exists** |
| **backend access** | `api/client.ts` — `createAuthApi(boundary, rateLimit?)` wrapping the generated `@themistra/api-client`, with a session boundary and a rate-limit notice as middleware. **`POST /accounts` is already declared public** (`AUTH_PUBLIC_OPERATIONS`, `api/client.ts:6-12`): sent with no bearer, never renewed on 401 |
| **auth** | `auth/` — PKCE authorize, callback, nonce/state, in-memory `accessToken`, `signOut`, `endSession`, cross-tab sign-out and renewal, the sign-in and signed-out screens |
| **security posture** | tokens live in memory only; `sessionStorage` holds exactly the L16 items; no secret reaches the bundle (`app/bundleContainsNoSecretEnvKeys.test.ts`); hand-written `fetch`/`XMLHttpRequest` is a **lint error** (L6, `.oxlintrc.json`) |

## 2 · Existing code this task touches

**Exists already:**

| | |
|---|---|
| `app/routes.tsx` | **`'register'` is already a declared public path** (`PUBLIC_ROUTE_PATHS`, with `'register/sent'` beside it) and it has **no element** — it renders an empty document today, exactly as the ten authenticated paths did before T42 |
| `api/client.ts` | the generated client factory and the public-operation set naming `POST /accounts` |
| `contracts/api/auth.yaml` | `POST /accounts` → `operationId: registerAccount`, `security: []`, body `RegisterAccountRequest` = **`required: [email, password]`**, `email` with `format: email` and `maxLength: 254`, `password` a bare `type: string` with **no length bounds in the contract** — the 12–128 rule lives in R1 and on the server, not in the schema. Response is always **202** with a `RegistrationAcknowledgement`, *"regardless of whether the email was already registered (enumeration-safe)"* |
| `app/ui/` | `Surface`, `Screen`, `Heading`, `Text`, `StatusRegion`, `FocusHolder`, `ActionButton`, `TextLink`, `Banner` |
| `i18n/catalogue.ts`, `i18n/i18n.ts` | the catalogue and its provider |

**Does not exist (new in this task or a later one):**

- **Any registration screen, form, field or submit.** There is no `account/` directory and no file whose name
  contains "register" anywhere under `frontend/src`.
- **The three named tests.** `registrationAcceptsTwelveAndOneTwentyEightCodePoints`,
  `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` and `passwordLengthCountsCodePoints`
  appear **only in `spec/frontend/package.md` and `tasks.md`** — no file in `src/` carries any of those names.
  Unlike T40/T41/T42, this task is **not** extending an existing suite; it writes the first one.
- **Any form primitive.** No `<form>`, `<input>`, `<label>`, `<fieldset>` or `<textarea>` is rendered anywhere
  in the bundle. The only lowercase tags in the tree are the nine inside `app/ui/`.
- **Any code-point counting helper.** No `codePointAt`, `Intl.Segmenter` or `[...string].length` utility.
- **Any client-side validation of anything.** This would be the first advisory check in the SPA.

**The first caller problem, again.** `createAuthApi` has no production caller (**OQ-T33-2**, inherited from
T33/T36) — `readAccount` and `readProfile` are built and dormant. If this task's scope includes R1's
*"SHALL submit to `POST /accounts` through the generated client"*, it is the first caller of the data path,
exactly as T42 was the first caller of the sign-out path. **Whether the submit is in scope is a Phase 1
question**: tasks 43–46 are all about the *length check*, task 47 owns the acknowledgement screen, and no
task in `tasks.md` says in words *"build the registration form"*.

## 3 · Established patterns to follow

1. **A screen is a component + a route element + catalogue keys.** `auth/SignInScreen.tsx` (T16) and
   `app/HomeScreen.tsx` (T174) are the two models; the attach happens in the existing `.map()` conditional in
   `app/routes.tsx`, never by adding a path (L14).
2. **Every word comes from the catalogue** through `t()`, asserted both against the catalogue entry and as
   literal English (`agents.md`, L11). A new screen means a new catalogue group.
3. **No screen renders an HTML element** (T173 §6e). A registration form therefore needs **new primitives**,
   or T173's documented escape hatch — *"a ninth must be added deliberately"* — paid again. This is the
   decision that will dominate Phases 1–4, and `index.ts`'s own header asks for it to be read first.
4. **Backend calls go through the generated client** and nothing else: L6 is a lint rule, and
   `api/lintRejectsHandWrittenBackendFetch.test.ts` runs the real lint script in the suite.
5. **Enumeration safety is structural, not cosmetic** (L4, R2, R30): one acknowledgement, one failure copy,
   no client-side branching, no timing difference.
6. **Managed focus and a live region** for async results (L11); `StatusRegion` (`role="status"
   aria-live="polite"`) and `FocusHolder` (`tabIndex={-1}`) exist for exactly that.
7. **Flat config, never YAML** for Spring — not applicable here; the frontend's config is
   `.oxlintrc.json`, `vite.config.ts` and `tsconfig`.

## 4 · Testing conventions

- **Vitest + jsdom, one named suite per `package.md` §8 entry**, file named after the test. Unit, integration
  and contract tests in the backend sense do not exist here; **Testcontainers, a fixed `Clock` and ArchUnit
  do not apply** — their equivalents are: real route tables rather than probes where possible, real
  `BroadcastChannel`/`sessionStorage`, source scans for structural claims, and one end-to-end case that runs
  a real `pnpm run build`.
- **The run recipe is `npx -y pnpm@12.14.0 exec vitest run`** — bare `pnpm` is not on this host — and **the
  exit code is the verdict, not the pass count**.
- **One known environmental failure**, every run: `api/lintRejectsHandWrittenBackendFetch.test.ts`'s case
  *"passes today's frontend/src through the lint script…"*, because this host's `oxlint@1.87.0` prints no
  summary line. 1 of 1887 cases. The same file holds the repository's only end-to-end build gate.
- **A mutation harness per task** (`.ai/prompts/frontend/T<n>/artifacts/07-mutation-harness.py`, now twelve
  copies). It reverts with `git checkout` + `git clean` over `frontend/src/`, so **tests are committed before
  it runs**. Every row predicts `own` / `inherited` / `unfalsifiable`; a BROKEN row is not a kill.
- **Instruments this task will almost certainly fire** — named now so Phase 4 can predict rather than
  discover (T42's freeze predicted six and nineteen fired):
  `screensRenderThroughDesignSystemPrimitivesOnly` (`PRIMITIVES` 9, lowercase-tag bearers 8, `RENDER_SITES`
  7, the union 16, `REQUIRED_UTILITIES`, the ref-target map, the catalogue key set) ·
  `routesResolveUnderAppPrefixOnly` (`SCREENS_ATTACHED_SO_FAR`, four entries, **in route order** — `register`
  would sit third) · `publicRouteSetIsExactlyTheDeclaredSet` (the probe walk and the element list) ·
  `homeRouteRendersLandingScreenAndManagesFocus` (the union pin, and its *"no OTHER screen takes focus"*
  guard, which loops over three screens and would need a fourth) · `sessionStorageHoldsOnlyTheL16Items` (if
  the form remembers anything, which it must not) · `api/generatedClientCompiles.test.ts` and the build gate.

## 5 · Known gaps and unknowns

1. **Does this task build the registration form, or only the length rule?** `tasks.md:77` says *"A
   12-character password is accepted client-side as advisory"*, which presupposes a form; no task says
   *"build the form"*. Tasks 44–46 extend the same rule and 47 adds the acknowledgement screen, so the form
   must appear here — but its **extent** (email field? submit? the `POST /accounts` call? the 202 path?) is a
   Phase 1 scoping question and may be an owner question.
2. **How is the field layer built?** Three readings, all live: new primitives of our own (`Form`, `Field`,
   `FieldError`), a headless library installed *with its first real consumer* as `index.ts` invites, or the
   screen rendering lowercase tags as the first exception to §6e, *"whose entire value is that it has none"*
   (T42's phase 3, F2). **Phase 3's target.**
3. **What is the advisory guidance's copy, and does task 43 need it at all?** Task 45 owns the *rejection*;
   43 owns *acceptance*. A screen that can accept without ever showing guidance may need no guidance copy —
   or the guidance may have to exist to be shown later. **I do not know** which the owner wants.
4. **Code points vs UTF-16 is task 46's**, but an implementation written here fixes the counting method for
   all four tasks. `'a'.repeat(12)` is twelve either way; a 12-code-point string of astral characters is 24
   UTF-16 units. Writing `.length` here would make 43 and 44 pass and 46 fail by construction.
5. **Is `register/sent` task 47's?** It is a declared path with no element, and `tasks.md:81` describes the
   acknowledgement without naming the path. **I do not know**; out of scope here either way.
6. **No contract bound on `password`.** The schema is a bare `type: string`, so the 12–128 rule exists only in
   R1 and on the server. Nothing in `contracts/` can hold this task's claim — the instrument must be a case.
7. **`autoComplete`, `inputMode`, password managers, and whether the field is `type="password"` at all** —
   nothing in the spec package says. **I do not know.**
8. **FU-T42-1 is still open**: a latent order-dependent flake in T16's `signInStarts…` suite, sighted twice
   and never reproduced. If it appears mid-harness it is this, not the new task.
