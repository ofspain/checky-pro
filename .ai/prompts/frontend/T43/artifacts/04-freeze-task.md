<!-- MODEL: Human Approval gate — Phase 4 (Freeze Task). The brief plus these decisions are frozen. -->

# frontend · T43 · Phase 4 — Freeze

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/02-task-implementation-brief.md` (amended) · `artifacts/03-design-challenge.md` (18 findings) |
| **Produces** | `artifacts/04-freeze-task.md` |

Every citation in the challenge was checked against source before disposition. **Fifteen of the eighteen are
accurate. Two are factually wrong about this repository, and one contradicts another finding in the same
review.** All three are recorded below, because each was load-bearing for a decision.

---

## 1 · Disposition of the challenge

| | finding | disposition |
|---|---|---|
| **F1** | OQ-T43-5 → neutral live-region announcement | **ACCEPTED** → **D1** |
| **F2** | four new primitives | **ACCEPTED** → **D2** |
| **F3** | no `type`/`kind` prop; two field primitives | **ACCEPTED** → **D2** |
| **F4** | the client needs a `SessionBoundary`; use a no-op one in the screen | **PART-ACCEPTED, option (b) not (a)** → **D4** |
| **F5** | local `useState`, no state library | **ACCEPTED** → **D9**, closes OQ-T43-4 |
| **F6** | code points via spread, advisory, non-blocking | **ACCEPTED except the helper's return type** → **D6**. *"Returns a boolean (`true` when length is 12)"* is the one shape that makes tasks 44, 45 and 46 restructure it — against **L12** |
| **F7** | focus the heading on mount; move focus to the status region after submit | **DECLINED, both halves** → **D5**. It contradicts **F15** in the same review |
| **F8** | minimal catalogue keys, advisory copy left to task 45 | **ACCEPTED** → **D7**, closes OQ-T43-3 |
| **F9** | `REGISTER_PATH`, attach in the public map, grow the route-order pin | **ACCEPTED** → **D10** |
| **F10** | grow the design-system instruments | **ACCEPTED, arithmetic verified** → **D11** |
| **F11** | three rules, forwarded refs, `useId` inside | **ACCEPTED** → **D12** |
| **F12** | assert `registerAccount` is called once | **DECLINED AS CITED** → **D8**. **There is no such method.** The generated client is `openapi-fetch`: the call is `api.POST('/accounts', { body })` and `registerAccount` is an operationId in the YAML only |
| **F13** | the password is transient, never persisted or logged | **ACCEPTED, instrument narrowed** → **D14** |
| **F14** | no branching, no delay | **ACCEPTED** → **D15** |
| **F15** | add the screen to T174's I9 focus guard | **ACCEPTED** → **D5** |
| **F16** | `Form` takes `onSubmit`; the button is `type="submit"` so Enter works | **ACCEPTED, and extended** → **D13** |
| **F17** | the code-point test strings | **ACCEPTED, with the 11 case re-aimed** → **D16** |
| **F18** | thirteen candidate mutants | **ACCEPTED, two re-aimed** → **D17** |

**The whole review missed one thing, and it is the biggest single consequence of F2.** See **D3**.

---

## 2 · Decisions

### D1 — OQ-T43-5: the form announces its own result and does not navigate

A pre-mounted `StatusRegion` (`role="status" aria-live="polite"`) is filled with a neutral catalogue message
after a successful submit. The screen does **not** navigate. `register/sent` stays a declared path with no
element, and task 47 owns both it and *"the acknowledgement is identical"*.

Reading (i) — navigating to `register/sent` — would ship a visitor to a blank document, which is worse than
leaving them on the form. Reading (iii) fails **L11**'s *"live regions for async results"* outright. The
region is **mounted from the start**, not conditionally rendered, because a live region inserted at the same
moment as its content is not reliably announced.

### D2 — Four new primitives, and the field layer is two of them

`Form` (`<form>`), `EmailField` (`<label>` + `<input type="email">`), `PasswordField` (`<label>` +
`<input type="password">`), `SubmitButton` (`<button type="submit">`). **9 → 13 primitives.**

- **`ActionButton` cannot be reused:** it sets `type="button"` inside and accepts no `type` (rule 2,
  `app/ui/ActionButton.tsx`). Verified.
- **One `TextField` with a `type` prop is forbidden for the same reason.** `type="email"` and
  `type="password"` *are* each field's contract — the second masks input and keeps it out of autofill
  heuristics; a consumer who could pass `type` could unmask a password field from outside. Two primitives,
  no `type`, no `kind`, no `variant`. They may share a private helper **inside `app/ui/`**, not an export.

### D3 — The *"exactly one element"* instrument must grow, and the challenge did not see it

`screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:348-355` asserts that **every primitive file renders
exactly one distinct lowercase tag** (`new Set(lowercaseJsxTags(source))`, length 1), with a single
documented exception: `TextLink`, which delegates to react-router's `Link` and renders **zero**.

**A labelled field is intrinsically two elements.** `EmailField` and `PasswordField` each render a `<label>`
and an `<input>`, so each file bears **two** distinct tags and **the case fails as written**. The three ways
out, and why one wins:

1. **Grow the instrument** with an explicit, named two-tag exception set — **chosen.** The exception is
   justified in the case itself: *a form control's label is part of the control's contract, not decoration,
   and `useId` can only link them from inside one component.*
2. **Split into `FieldLabel` + `EmailInput`** — rejected. The link between them is `htmlFor`/`id`, which
   would have to become props. That is *"the attribute that is its own contract"* exposed to a consumer,
   which rule 2 exists to forbid.
3. **Drop the `<label>` and use `aria-label` on the input** — rejected. One tag per file, but it gives the
   control an accessible name with **no visible text**, which WCAG 2.1 **3.3.2 (Labels or Instructions)** and
   this package's own *"WCAG AA"* (L11) both expect for a data-entry field.

The instrument grows by naming the two files and the reason, exactly as T42 grew `REQUIRED_UTILITIES` for
`Banner`. **A primitive that composes two elements is new in this package**, and phase 6 must say so in
`index.ts` rather than letting the exception list carry the whole explanation.

### D4 — `createPublicAuthApi()` in `api/client.ts`, and the brief's read-only list is deviated from deliberately

`createAuthApi(boundary, rateLimit?)` requires a `SessionBoundary`, and **nothing in production constructs
one** — `auth/cleanReauth.ts:13-16` says so in its own words, and `accessToken.renew()` always rejects
(OQ-T33-2). `POST /accounts` is in `AUTH_PUBLIC_OPERATIONS`, so for this screen the boundary's entire
behaviour is *do nothing*: no bearer attached, no 401 renewed.

**F4's option (a) — a no-op boundary written inside the screen — is declined.** It would put a second
implementation of a security-relevant interface in a public screen, where a reader would have to compare it
with the real one to see that it is inert.

**Chosen: a `createPublicAuthApi(rateLimit?)` factory beside the other two in `api/client.ts`**, installing
the rate-limit notice and **no boundary**, with a comment stating that a public-only client needs none and
why. This keeps the plumbing in the layer that owns it, preserves **R23**'s rate-limit copy, and gives tasks
47–54's public surfaces (verify, resend, reset) the same door — which is **L12**, phase additivity.

**This modifies a file the brief lists under *Files NOT to Modify*.** That list was written before the owner
closed OQ-T43-1, so the deviation is recorded here, at the gate, rather than discovered in phase 6. Nothing
else in `api/client.ts` changes; `createAuthApi` is untouched and its existing suites must stay green.

### D5 — The screen takes no focus, and joins T174's guard; F7 is declined in both halves

1. **No focus on mount.** `HomeScreen` moves focus to its `<h1>` because it is the destination of a
   **redirect** — a client-side arrival with no document load, where focus would otherwise sit on `<body>`
   and a screen reader would announce nothing. `/app/register` is reached by a link or a typed URL, which
   *is* a document load. **`RegisterScreen` is added to T174's phase-8 I9 guard**
   (`homeRouteRendersLandingScreenAndManagesFocus.test.tsx:422-454`), whose loop grows from three screens to
   four and asserts `document.activeElement` is `<body>` — which is **F15**, and F15 and F7 cannot both be
   satisfied. F15 is the one that matches the precedent it cites.
2. **No focus move into the status region after submit.** A `role="status" aria-live="polite"` region is
   announced **without** focus; moving focus into it is redundant, can double-announce, and takes the
   visitor's place away. The advisory check never disables the control (**D6**), so the button the visitor
   pressed is still mounted and focus already has somewhere to be. `FocusHolder` exists for the case where it
   does **not** — a control that disappears — and that case does not arise here.

### D6 — The length helper returns a count, not a verdict

`[...password].length` — the spread iterates code points, so twelve astral characters count as twelve, not
twenty-four. The helper **returns the number**. F6's *"returns a boolean (`true` when length is 12)"* is
declined: task 44 needs 128, task 45 needs *outside 12–128*, and task 46 needs the counting unit itself.
A `=== 12` predicate forces all three to restructure it, which **L12** forbids this task from doing to them.

It must not throw on an empty string, a lone surrogate, or a long paste, and the count is **advisory**: it
disables nothing, blocks nothing, and the submit happens regardless of what it returns (**AC4**).

### D7 — The catalogue key set is frozen at six, and every one of them is rendered by a case

`register.heading` · `register.emailLabel` · `register.passwordLabel` · `register.submit` ·
`register.submitting` · `register.success`.

**No advisory, rejection, strength or hint copy — OQ-T43-3 is closed this way.** Task 45 shows the guidance
and should choose its words. A key written here and never rendered is unused code of exactly the kind
`app/ui/index.ts` refuses to ship, so **`register.submitting` is only legitimate if a case measures it**,
which requires a transport the case can leave unresolved. Phase 10 owes that case; phase 6 should write the
screen so it is reachable.

### D8 — AC5's instrument is three-part, and `registerAccount` does not exist

The generated client is `openapi-fetch` (`libs/ts/api-client/src/index.ts:10-12`): `createAuthClient()`
returns `createClient<paths>`, whose call shape is **`api.POST('/accounts', { body })`**. `registerAccount`
is the contract's `operationId` and **is not a method on anything**. F12's assertion would fail to compile.

What holds AC5 instead, all three together:

1. **A source scan of the screen** — no `fetch(`, no `XMLHttpRequest`, no hand-built URL, no second import
   path to the backend; the import list is the allowlist, with a paired control that plants each forbidden
   pattern as a probe (T42's AC13 shape).
2. **The fake transport** — `vi.stubGlobal('fetch', …)` plus `installBrowserRequest()`, the T05 pattern
   already copied into `api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:54-80`: exactly one
   request, method `POST`, path `/accounts`, a JSON body carrying the typed email and password, and **no
   `Authorization` header** (the operation is public).
3. **The repository's L6 lint gate**, which already fails the build on a hand-written `fetch` anywhere in
   `src/`.

### D9 — Local `useState`; no state library (closes OQ-T43-4)

`email`, `password` and a submission status in `useState`. A one-shot public mutation has no cache to
invalidate and no state to share across screens, which is what L13's TanStack Query and Zustand exist for.
**Recorded as a decision, not a default**, because tasks 44–47 inherit it, and the first screen that needs
cross-screen server state should revisit L13 with a real consumer — the same discipline `app/ui/index.ts`
applied to a component library.

### D10 — The screen lives in `features/account/`, which the design's own tree says, and the challenge assumed `auth/`

`design.md:206` lists `src/features/account/` — *"register, verify, reset, sessions [P1] (R1–R20,
R29–R30)"*. **No `features/` directory exists yet**, so this task creates it: `features/account/RegisterScreen.tsx`
and `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`.

The existing screens are consistent with that tree rather than exceptions to it — sign-in, callback and
signed-out are `auth/` because they are the OIDC flow (L1–L3), and `HomeScreen` is `app/` because it is the
shell's landing. Registration is an **account feature**, and putting it in `auth/` would also sit oddly with
the module boundary that forbids the screen from importing `auth/` at all.

`REGISTER_PATH = 'register'` joins the path constants in `routes.tsx`; the screen attaches inside the
**existing** public `.map()` conditional. No path is added (**L14**, **AC8**).

### D11 — The instrument growth checklist, with the numbers to assert

| instrument | from | to |
|---|---|---|
| `PRIMITIVES` | 9 | **13** (`EmailField`, `Form`, `PasswordField`, `SubmitButton`, in sorted order — the list is asserted sorted) |
| lowercase-tag **bearer files** | 8 | **12** (`TextLink` still delegates, so it stays at zero) |
| *"exactly one element"* | one exception (`TextLink`, 0) | **plus a two-tag set** naming the two field files (**D3**) |
| `RENDER_SITES` | 7 | **8** (`features/account/RegisterScreen.tsx`) |
| the union pin | 16 | **21** (13 + 8, the two sets disjoint) |
| `REQUIRED_UTILITIES` | nine rows | **thirteen** — each new primitive's own tokens; `SubmitButton` carries `ActionButton`'s, and **both** are then pinned to the same tokens, which is the pair-pin lesson T42 learned the hard way |
| the ref-target map | nine | **thirteen**: `Form` → `FORM`, `EmailField` → `INPUT`, `PasswordField` → `INPUT`, `SubmitButton` → `BUTTON` (the ref points at the **control**, not the label) |
| the catalogue key set | five groups | **plus `register.*`**, the six keys of **D7** |
| `SCREENS_ATTACHED_SO_FAR` | 4 | **5**, in **route order**: `['sign-in', 'callback', 'register', 'signed-out', 'home']` |
| T174's I9 focus guard | three screens | **four** (**D5**) |
| `publicRouteSetIsExactlyTheDeclaredSet` | — | whatever its probe walk and element list assert about a public path that now has an element |

### D12 — Each new primitive: three rules, a forwarded ref, and `useId` inside

No `className`, no `style`, no `id`, no `htmlFor`, no `type`, no copy. `useId()` inside each field links its
label to its input. Every primitive forwards its ref to the element named in D11's map — the suite asserts
this for all of them (`:603-641`), and a primitive that drops `forwardRef` leaves that null.

Props, frozen: `Form { children, onSubmit }` · `EmailField { label, value, onChange, ref? }` ·
`PasswordField { label, value, onChange, ref? }` · `SubmitButton { children }`. `label` is a `ReactNode` the
**screen** passes from the catalogue — rule 3 is that a primitive carries no copy, not that it renders none.

### D13 — `Form` owns the submit event, including `preventDefault`

`Form` accepts `onSubmit` and calls `event.preventDefault()` **inside the primitive** before delegating.
A handler is behaviour the consumer owns, so it is not a rule-2 attribute; but *not reloading the page* is
the form element's contract, and in jsdom an unprevented submit is both unimplemented and noisy. With
`SubmitButton` rendering `type="submit"`, Enter in either field submits (**AC9**), which an `onClick` on a
`type="button"` control would not give.

### D14 — The security instrument, narrowed to what a case can actually hold

One case, three assertions: `sessionStorage.setItem` and `localStorage.setItem` are **never called** across
the whole journey (type, submit, resolve); the rendered DOM holds **no attribute** carrying the typed
password (`value=`/`defaultValue=` in the serialized HTML); and the screen's **source** reaches no `console`,
no storage API and no URL-building. F13's `console.log` **spy** is replaced by a source scan: a spy proves
one path did not log, a scan proves no path can.

### D15 — Nothing branches on the response, and nothing waits

The handler awaits the client's promise and sets the success status. It does not read the body, does not
inspect the status, does not retry, and introduces no delay (**R2**, **L4**, **AC11**). 202 is the only
documented outcome; a screen that handled others would be inventing differences R2 exists to remove.

### D16 — The frozen test inputs, with the 11 case re-aimed

`'a'.repeat(12)` and `'👍'.repeat(12)` (12 code points, 24 UTF-16 units) are both **accepted**. The empty
string must not crash the count.

F17's eleven-character case is **re-aimed**: this task asserts only that an 11-code-point password **still
reaches the transport**, which is **AC4**'s advisory claim. It asserts **nothing** about what is shown — task
45 owns the rejection and its copy, and a case here that pinned "no guidance appears" would pin the absence
of a feature 45 is commissioned to add.

### D17 — The harness plan (thirteenth copy), with two of F18's rows re-aimed

One row per AC at least, each predicting `own` / `inherited` / `unfalsifiable`:

`Form` dropped from the screen · `EmailField` renders `type="text"` · `PasswordField` renders `type="text"`
(**AC1**) · the count becomes `.length` (**AC3**) · the submit is blocked unless the count is 12 (**AC4**) ·
the call is replaced by a hand-written `fetch` (**AC5**) · a label is hardcoded in English (**AC6**) · the
screen renders a `<form>` tag of its own (**AC7**) · `register` added to `AUTHENTICATED_ROUTE_PATHS`
(**AC8**) · `SubmitButton` loses its focus ring and becomes `type="button"` (**AC9**) · the email is written
to `sessionStorage` (**AC10**) · the handler branches on the response status (**AC11**) · the status region
is rendered conditionally instead of pre-mounted (**D1**) · the screen focuses its own heading on mount
(**D5**) · `Form` stops calling `preventDefault` (**D13**) · each pinned list reverted one entry at a time
(**AC7**, **D11**).

**Re-aimed from F18:** *"Remove the 12-code-point check → AC2 fails"* cannot fail anything, because the check
is advisory and removing it changes no observable behaviour at the accepting end — it is this task's
**unfalsifiable** row, and naming it as such now is better than discovering it as a survivor. *"Make
`EmailField` render `<input type="text">`"* is kept, but the row that matters for rule 2 is a `type` **prop**
added to a field primitive, which `tsc` must reject.

---

## 3 · Blockers

**None.** OQ-T43-1 and OQ-T43-2 closed by the owner (2026-11-14); **OQ-T43-5 closed by D1**; **OQ-T43-3
closed by D7**; **OQ-T43-4 closed by D9**. `OQ-T33-2` closes for its **registration** half once this ships
and stays open for the authenticated half (tasks 5, 6, 30, 31, 33).

Carried, each with an owner: **OQ-T43-6** — the `features/` directory the design describes exists only for
`account/` after this task, and the other eleven feature folders arrive with their own phases · **OQ-T42-5**
(a public-path tab does not react to a sibling's sign-out — and note that `/app/register` **is** such a
path) · OQ-T42-3 · OQ-T40-1 · OQ-T173-4/5 · OQ-T174-3/5 · FU-T42-1 · FU-T37-2 · FU-T39-1 · task 38's Q15.

## 4 · What phase 6 must predict before it runs anything

T42's freeze predicted six instrument failures and **nineteen** fired. This task touches more pinned lists
than T42 did, so the prediction is made explicit: **D11's eleven rows are all expected to fail on the first
run**, plus the end-to-end build gate while `app/ui/index.ts`'s count and the two exhaustive `Record`s keyed
by the primitive union are incomplete. **A `Record<(typeof PRIMITIVES)[number], …>` with four new keys
missing is a `tsc` error, not a test failure** — which is how T42 discovered that `Banner` had to declare its
tokens and its ref target, and here it will be four at once.
