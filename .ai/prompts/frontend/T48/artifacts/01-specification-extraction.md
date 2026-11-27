<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No design, no code. -->

# frontend · T48 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business rules

| ID | One sentence |
|---|---|
| **R3** *(in scope)* | When a visitor opens a verification link and the token is valid, the SPA shows the verified success state. (`requirements.md:14-15`; backend R4) |
| **R4** *(boundary, T49)* | If the verification request fails **for any reason**, the SPA shows **one uniform failure message with a neutral resend action**. (`requirements.md:16-17`) |
| **R5** *(boundary, T50/T51)* | The resend-verification form is public and its acknowledgement is identical whatever the outcome. (`requirements.md:18-20`) |

R3 is one sentence and it is the whole of this task. R4 and R5 are listed because **they land on the same
route and may land in the same file**, which is the open question below — not because they are in scope.

## Locked decisions that constrain this task

| ID | How it binds T48 |
|---|---|
| **L14** *(`design.md:53-56`, 138, 145)* | `/app/verify` is already in the declared public set; this task attaches an element, it does **not** add a path. **The author confirmed the token's shape in writing: `/app/verify?token=<token>` (`design.md:145`)** — so the token is a query parameter, not a path segment, and that is settled spec, not a design choice. |
| **L11** *(`design.md:47`)* | WCAG AA, keyboard operation, **managed focus**, and every user-facing string from the catalogue. The screen is reached by arrival from an email, so focus must be moved deliberately. |
| **L6** *(`design.md:38`)* | The call must go through the generated client. `POST /accounts/verify-email` is already in `AUTH_PUBLIC_OPERATIONS` (`client.ts:8`), so **`createPublicAuthApi()`** is the factory — it carries no bearer and arms no renewal. |
| **L4** *(`design.md:21-26`; `agents.md`)* | Enumeration safety. Note the **scope** carefully: copy must be identical "for bad, expired, and already-used **tokens**" — i.e. among failure reasons. It does **not** require success and failure to be indistinguishable, and `AccountController.java:80-84` says the two outcomes "are meant to be distinguishable". A success state that hid itself would misread this. |
| **L16** *(`design.md:58`)* | sessionStorage may hold only `state`, `nonce`, `code_verifier` and the listed items. **The verification token is not on that list**, so it must not be written there — nor anywhere else. |
| **L9** *(`design.md:46`)* | Single origin; the call goes through the edge path map, as the generated client already does. |
| **L17** *(`agents.md:87-89`)* | The service-worker bypass list is backend prefixes (`/accounts`, `/oauth2/`, …) and **does not include `/app/verify`** — correctly, because a cold arrival at a SPA route must receive the application shell. No action; recorded so nobody "fixes" it. |
| **L3** *(`design.md:13`)* | About **access** tokens, so not directly binding. Recorded because the verification token is a possession credential in a URL and deserves the same instinct: not stored, not logged. |

## Files involved

**Existing, to read or extend**

| File | Why |
|---|---|
| `frontend/src/app/routes.tsx` | `'verify'` at `:54` is declared with no element. Needs a path constant and an attach branch, matching the `REGISTER_SENT_PATH` pattern. |
| `frontend/src/i18n/catalogue.ts` | New keys. **No `verify.*` group is reserved** — checked. |
| `frontend/src/api/client.ts` | Read only. `createPublicAuthApi()` and the operation already exist; nothing here changes. |
| `frontend/src/auth/CallbackScreen.tsx` | Read only — the act-on-arrival template (`useSearchParams`, StrictMode guard, `onScreen` ref). |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 9 → 10, its length pin `:267`, the union `:305`, the catalogue-key pin. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | The attached-screen list `:711` — six screens become seven. |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | Already arrives at `verify?token=abc` (`:134`, `:137`); the attached set grows. |
| `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | The "N of the eight have no element" count at `:292` — **four today, three after this task**. |

**New, expected by the spec**

| File | Authority |
|---|---|
| A verify screen under `frontend/src/features/account/` | `design.md:206` places "register, verify, reset, sessions" in `features/account/`. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | `package.md:107`. |

## Dependencies

| | |
|---|---|
| **Contract** | `contracts/api/auth.yaml:46-60` — `POST /accounts/verify-email`, `operationId: verifyEmail`, **`security: []`**, request `VerifyEmailRequest` = `{ token: string }` **required** (`:618-623`), response **`204` only**. |
| **Client** | `createPublicAuthApi()` (`api/client.ts:61`); the operation is in `AUTH_PUBLIC_OPERATIONS` (`:8`). |
| **Backend** | `AccountController.verifyEmail` (`:85-90`) → `accountService.activateFromVerificationToken(token)`; failures map through `AccountExceptionHandler`'s single `VerificationTokenRejectedException` mapping. |
| **Link producer** | notification-service `TemplateRenderer`, from `themistra.notification.link.base-url` + `/app/verify` (`design.md:145-149`); asserted at `SecretLeakEndToEndIntegrationTest.java:199`. |
| **Primitives** | `Screen`, `Heading`, `Text`, `StatusRegion`, and possibly `Banner` / `TextLink` — all from `app/ui`. |
| **Config keys** | None new. |

## Acceptance criteria

All map to **R3** unless stated.

| AC | Criterion |
|---|---|
| **AC1** | `/app/verify` resolves to an element; a cold arrival with a token renders a non-empty document. |
| **AC2** | The token is read from the **`token` query parameter**, exactly as `design.md:145` specifies. |
| **AC3** | The SPA sends `POST /accounts/verify-email` with `{ token }` as the body, through the generated public client, with **no bearer**. (L6) |
| **AC4** | On the contract's `204`, the screen shows the verified success state. |
| **AC5** | The success state's copy resolves entirely from the catalogue; no server text and no literal. (L11) |
| **AC6** | Focus is moved deliberately on arrival and the success state is announced. (L11) |
| **AC7** | Exactly **one** request per arrival — StrictMode's double effect invocation must not verify twice. A one-time token verified twice is a failure the second time. |
| **AC8** | The token is written to no storage, and appears in no `sessionStorage` key. (L16) |
| **AC9** | `PUBLIC_ROUTE_PATHS` is unchanged; the element is attached by a path constant. (L14) |
| **AC10** | The screen renders through design-system primitives only; `RENDER_SITES` grows deliberately. |
| **AC11** | The declared set, order and membership pins in the four inherited suites stay green. |

## Tests required

**Named (`package.md:107`):** `verifyFromLinkShowsSuccessState` → R3.

**Boundary tests implied by the criteria above**

1. A `204` renders the success state; the request body is asserted to be `{ token: <the token from the URL> }` — not merely that a request was sent.
2. **Exactly one request** across a StrictMode double mount.
3. No `Authorization` header on the request (L6/`security: []`).
4. No storage write, with an intercepting spy on `Storage.prototype` — an instance spy does not intercept in jsdom (T42).
5. Focus lands where L11 requires, asserted through the **real route table**, not a direct mount.
6. The catalogue keys render as literal English and the server's body is not rendered.
7. A token containing URL-hostile characters (`+`, `%`, `/`) survives the round trip — `useSearchParams` decoding is the thing under test.
8. The inherited count and membership pins, moved deliberately.

**Explicitly not tested here:** anything a non-204 does. That is R4 and task 49.

## Open questions

| ID | Question |
|---|---|
| **OQ-T48-1** | **Is the success state its own screen, or one state of a screen shared with T49?** R3 wants a success state and R4 wants "one uniform failure message with a neutral resend action" — **on the same route**. `CallbackScreen` already demonstrates one screen with working/failure states. **This decides T48's blast radius and whether T49 edits T48's file or its own**, so it is the first thing Phase 3 must settle. |
| **OQ-T48-2** | **Should the token be POSTed on arrival at all?** Verifying on mount means any link-preview fetch, email-scanning proxy or security gateway that opens the URL **consumes a one-time token before the human clicks**. R3's wording ("opens a verification link … shows the verified success state") reads as on-arrival, and `CallbackScreen` acts on arrival — but OAuth requires that and nothing here says this does. The alternative is a single confirm control. This is a **product decision with a security consequence**, not a style choice. |
| **OQ-T48-3** | **What is the behaviour when there is no `token` parameter at all, or it is empty?** R3 covers a *valid* token; R4 covers the *request* failing "for any reason". With no token there is **no request to fail**, so this case sits in neither requirement. It is reachable by anyone who opens `/app/verify` directly. I am not inventing a rule for it: whether T48 handles it, T49 absorbs it, or a third task owns it needs an answer. |
| **OQ-T48-4** *(not blocking T48; blocking T49)* | **The contract declares no failure response.** `auth.yaml` gives `verifyEmail` a `204` and nothing else, while the controller and its exception handler clearly produce a 400. T49 cannot be specified from the contract alone. Raised here because it was found here; it should be resolved in T49, or by an `auth.yaml` amendment outside the frontend. |

No `package.md` §11 question (Q1–Q15) governs this task; Q12 concerns R5's resend session divergence, which is T50/T51.

**Phase 1 complete. No design — that is Phase 2's brief and Phase 3's challenge.**
