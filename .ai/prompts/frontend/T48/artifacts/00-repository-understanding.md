<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code written. -->

# frontend · T48 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state (`verifyFromLinkShowsSuccessState`, cites **R3**) |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

---

## 1 · Architecture summary

A Vite + React + TypeScript SPA, mobile-first, served from one origin behind the edge, mounted entirely
under the `/app` basename (LOCKED, `agents.md` "SPA routes").

| Layer | Where | What it is |
|---|---|---|
| **Shell & routing** | `src/app/routes.tsx` | One route table. `PUBLIC_ROUTE_PATHS` and `AUTHENTICATED_ROUTE_PATHS` are the declared sets; elements are attached by matching a **path constant**, never a literal. Authenticated routes sit behind one pathless layout gate (`RequireSession`, T40). |
| **Design system** | `src/app/ui/` | **12 primitives** behind one barrel (`index.ts`): `ActionButton`, `Banner`, `EmailField`, `FocusHolder`, `Form`, `Heading`, `PasswordField`, `Screen`, `StatusRegion`, `SubmitButton`, `Surface`, `Text`, `TextLink`. Tailwind 4, CSS-first. **`className` and `style` are absent from the primitives' types**, enforced end-to-end. |
| **API** | `src/api/client.ts` | Generated clients only (`@themistra/api-client`). Hand-written `fetch` to a backend is forbidden by lint rule and by an end-to-end gate (`lintRejectsHandWrittenBackendFetch.test.ts`). Two factories matter here: `createAuthApi(boundary, …)` for authenticated calls and **`createPublicAuthApi()`** for the `security: []` operations, which deliberately has **no session boundary** — no bearer, no renewal on 401. |
| **Session** | `src/api/sessionBoundary.ts`, `accessToken.ts` | Access token in memory only; silent re-authentication, no refresh token (Q5, resolved empirically). Irrelevant to this task: the verification link carries no session. |
| **i18n** | `src/i18n/catalogue.ts` | Every user-facing string resolves from one catalogue (**L11**). A screen rendering a literal, or server-supplied copy, breaks the lock. |
| **Persistence / events / outbox** | — | **None.** This is a browser client; the prompt's generic questions about JPA, Flyway, outbox and resource-server security do not apply. Nothing in this task persists anything — see §4 and AC-shaped notes below. |

## 2 · Existing code this task touches

### Already exists

| Thing | Evidence | State |
|---|---|---|
| The `verify` route | `routes.tsx:54` — `'verify'` in `PUBLIC_ROUTE_PATHS` | **Declared since task 16, carries no element.** The demo rehearsal found this screen blank. |
| The backend endpoint | `contracts/api/auth.yaml:46-60`; `services/auth/…/AccountController.java:85-90` | `POST /accounts/verify-email`, `security: []`, body `{ token: string }` **required**, response **`204` and nothing else declared**. |
| The operation in the client | `src/api/client.ts:8` — in `AUTH_PUBLIC_OPERATIONS` | Already declared public; the call shape is already exercised at `parallel401sTriggerExactlyOneRenewal.test.ts:499`. |
| **The link format** | `services/notification/…/SecretLeakEndToEndIntegrationTest.java:199` asserts the email body contains `https://app.example.test/app/verify?token=<raw token>` | **Authoritative, and measured by a backend test rather than inferred.** The token is a **query parameter named `token`**. |
| That spelling, already assumed in the SPA | `routesResolveUnderAppPrefixOnly.test.tsx:134,137`; `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:1277`; `unauthenticatedRoutesRedirectToSignIn.test.tsx:323` | Three suites already arrive at `verify?token=abc`, and one asserts **the session gate does not touch it**. |
| A control written *for this task in advance* | `emailVerifiedClaimIsNotAccountState.test.ts:456` — "the rule is SILENT on the verification LINK flow, which is tasks 48-51" | T31 proved the `email_verified` scan stays quiet on a link-flow file, so that instrument will not fire falsely here. |
| The act-on-arrival pattern | `src/auth/CallbackScreen.tsx` | The only existing screen that reads query params and calls the backend on mount: `useSearchParams()` at `:44`, a StrictMode-guarded `useEffect` at `:68`, an `onScreen` ref so a double invocation cannot navigate twice, `StatusRegion` for the working announcement at `:218`. **This is the template.** |
| The acknowledgement pattern | `src/features/account/RegisterSentScreen.tsx` (T47) | A static screen that moves focus to its heading on mount. Relevant if the success state is a screen rather than a state. |

### New

- A screen for `verify` (name unsettled — Phase 2/3's call).
- `verify.*` catalogue keys. **Checked: none are reserved.** `grep 'verify:'` in `catalogue.ts` returns nothing, so unlike T47's `register.success` there is no prior reservation to honour or spend.
- The named suite `verifyFromLinkShowsSuccessState` (`package.md:107`).

## 3 · Established patterns to follow

1. **Attach by constant.** `routes.tsx` matches a path constant (`if (path === REGISTER_SENT_PATH) return { path, element: … }`). A literal in the attach branch would break L14's pins.
2. **Public calls go through `createPublicAuthApi()`**, which has no session boundary. Using `createAuthApi` here would attach a bearer the visitor does not have and arm renewal on a 401.
3. **Catalogue-only copy**, every rendered key measured by a case (**D7**).
4. **Managed focus** (L11). Screens reached by arrival move focus to their heading; `Heading` is already focusable (`tabIndex={-1}`, T174) and primitives take a `ref`.
5. **StrictMode double invocation is a real hazard**, not a theoretical one — `CallbackScreen.tsx:46` carries a comment saying a guard's absence once made it "never navigate at all, in development only, while every other assertion still passed."
6. **Count and membership pins move deliberately.** A new render site touches, at minimum: `RENDER_SITES` and its `toHaveLength(9)` and union `toBe(22)` in `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:86,267,305`; the attached-screen list in `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:711`; `routesResolveUnderAppPrefixOnly.test.tsx`; the "N of the eight have no element" count in `unauthenticatedRoutesRedirectToSignIn.test.tsx:292` (**four today; three after this task**).

## 4 · Testing conventions

The prompt's generic conventions (plain JUnit, fixed `Clock`, Testcontainers, ArchUnit) are **backend** conventions and do not apply. The frontend's are:

- **Vitest + jsdom**, real `createRoot` renders inside `act`, driven through the **real route table** rather than by mounting a component directly wherever the claim is about arrival.
- **One named suite per task**, filename = the name in `package.md` §8.
- **A mutation harness per task** (`artifacts/07-mutation-harness.py`): it reverts with `git checkout`, so **tests must be committed before it runs**; it must assert its own baseline; a canary row proves an instrument is not inert.
- Transport is stubbed by replacing `fetch`; the request body is read **inside** the handler (`request.clone().text()`), because a body can be consumed only once.
- `type()` into a controlled input goes through `Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set` with the descriptor asserted — T47 proved a direct `input.value =` helper can type nothing and still pass every case.
- `pnpm` is installed by hand (`pnpm@12.14.0`; Node 26 dropped corepack). `oxlint 1.87` prints **no summary line**, so the L6 gate asserts the **absence** of output.
- Baseline today: **2016 / 2016**, 38 files, lint 0, typecheck 0.

## 5 · Known gaps and unknowns

| | |
|---|---|
| **The contract declares no failure response.** | `auth.yaml:57-60` declares **`204` only** for `verifyEmail`. The controller's javadoc says the endpoint's "two outcomes are meant to be distinguishable" (204 vs 400), and `AccountExceptionHandler` maps `VerificationTokenRejectedException` to a single response — but **the 400 is absent from the contract**. T48 only needs the success state, so this does not block it; **T49 (R4) is the task that cannot be specified from the contract alone.** Flag now, resolve there. |
| **Success and failure are deliberately distinguishable, and the uniformity rule is narrower than it looks.** | `agents.md` requires identical copy "for bad, expired, and already-used tokens" — i.e. **among failure reasons**, not between success and failure. The controller states the same intent explicitly. A design that made the success state indistinguishable from failure would be a misreading of both. |
| **Where the token is read from is a decision, not a given.** | The link is `?token=…`, so the screen must read a query param — but `useSearchParams` is exactly what T47's AC4 **forbade** in `RegisterSentScreen`. These are opposite requirements for adjacent screens, and the contrast should be stated in whichever file carries it, or a future reader will read one as a mistake. |
| **Whether the success state is a screen or a state of one screen.** | R3 says "the verified success state"; R4 says "one uniform failure message with a neutral resend action" — on the same route. One screen with three states (working, verified, failed) is the shape `CallbackScreen` already uses, and tasks 48 and 49 would then share a file. **I do not know which split the author intends.** This is the single biggest open question for Phase 1/2 and it decides T48's blast radius. |
| **Whether the token should be sent at all before a user gesture.** | An email-scanning proxy or a link-preview fetch will open the URL. Verifying on mount makes any such fetch consume the token. `CallbackScreen` acts on arrival because OAuth requires it; nothing here says this must. **I do not know the author's intent**, and R3's wording ("opens a verification link … shows the verified success state") reads as on-arrival. Raising it rather than assuming. |
| **Minor, cosmetic.** | The synthetic in `emailVerifiedClaimIsNotAccountState.test.ts:461` posts to `'/accounts/verify'`, which is not a contract path (`/accounts/verify-email`). It is a string in a scan test, so nothing behaves differently, but it is a misleading example for the task that was written to help. |

**Phase 0 complete. No design, no requirement extraction — that is Phase 1.**
