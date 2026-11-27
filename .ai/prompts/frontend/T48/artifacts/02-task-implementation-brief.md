<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief). No design, no code. -->

# frontend · T48 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

**T48 — Verify success state.** A valid token shows the verified success state. Verifies
`verifyFromLinkShowsSuccessState`. Cites **R3**. (`spec/frontend/tasks.md:82`)

## Purpose

`/app/verify` has been a declared public route with no element since task 16, so the link in every
verification email the platform has ever sent lands on a blank document. This task gives that link a
destination for the case where the token is good.

## Scope

**In**

- An element on `/app/verify`, attached by a path constant.
- Reading the token from the `token` query parameter.
- One `POST /accounts/verify-email` through the generated public client.
- The success state for the contract's `204`: catalogue copy, managed focus, announced.
- Moving the inherited count and membership pins that a new render site necessarily moves.

**Out**

- **Everything a non-`204` does** — R4, task 49. No uniform failure message, no resend action.
- The resend form and its acknowledgement — R5, tasks 50 and 51.
- `/app/reset`, which takes a token the same way — R14, tasks 52 onward.
- Any change to `auth.yaml`, to notification-service, or to the link the email carries.
- Any change to `PUBLIC_ROUTE_PATHS` itself; the path is already declared.

## Business rules

| ID | |
|---|---|
| **R3** | When a visitor opens a verification link and the token is valid, the SPA shows the verified success state. |

## Locked decisions

| ID | |
|---|---|
| **L14** | `/app/verify` is in the declared public set; the token arrives as `?token=<token>`, author-confirmed at `design.md:145`. Attach by constant, never a literal. |
| **L11** | WCAG AA, keyboard operation, managed focus, every user-facing string from the catalogue. |
| **L6** | Generated client only; the operation is already in `AUTH_PUBLIC_OPERATIONS`, so the factory is `createPublicAuthApi()` — no bearer, no renewal. |
| **L4** | Enumeration-safe copy applies **among failure reasons** (bad, expired, used tokens), not between success and failure, which `AccountController.java:80-84` states are meant to be distinguishable. |
| **L16** | The verification token is not among the permitted sessionStorage items, so it is stored nowhere. |
| **L9** | Single origin, through the edge path map, as the generated client already does. |
| **L17** | `/app/verify` is correctly absent from the service-worker bypass list; a cold arrival must receive the shell. No action. |
| **L3** | Access tokens in memory only. Not binding here; recorded because the verification token is a credential in a URL. |

## Dependencies

| | |
|---|---|
| Contract | `contracts/api/auth.yaml:46-60` — `POST /accounts/verify-email`, `security: []`, body `VerifyEmailRequest` = `{ token: string }` required (`:618-623`), response **`204` only**. |
| Client | `createPublicAuthApi()` — `frontend/src/api/client.ts:61`; operation listed at `:8`. |
| Router | `useSearchParams` from `react-router`; the act-on-arrival template is `frontend/src/auth/CallbackScreen.tsx`. |
| Primitives | `frontend/src/app/ui` barrel only. |
| Baseline | **2016 / 2016**, 38 files, `lint` 0, `typecheck` 0, as of T47's close. |

## Inputs

- The `token` query parameter of `/app/verify`, as produced by notification-service's `TemplateRenderer`.
- Nothing else. No props, no session, no stored state, no route params.

## Outputs

- One `POST /accounts/verify-email` with `{ token }`.
- A rendered success state, from the catalogue.

## State changes

**None persisted.** No storage write, no cookie, no token retained. The only state is React state local to
the screen for the duration of the arrival.

## Files to create

| | |
|---|---|
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | The named suite (`package.md:107`). |
| A verify screen under `frontend/src/features/account/` | `design.md:206` places verify in this module. **Its name and whether it is one file or shared with task 49 depends on OQ-T48-1 and is not decided here.** |

## Files to modify

| | |
|---|---|
| `frontend/src/app/routes.tsx` | A path constant for `verify`; the attach branch. |
| `frontend/src/i18n/catalogue.ts` | New keys; no `verify.*` group is reserved today. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 9 → 10; its length pin (`:267`); the union pin `toBe(22)` (`:305`); the catalogue-key pin. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | The attached-element case at `:711` — six named screens become seven. |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` (`:18`) gains `verify`, **in route order, not task order** — the list's own comment says a task-ordered entry fails while nothing is wrong. |
| `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | The count at `:292`: four of the eight have no element today, **three** after this task. |

## Files NOT to modify

- `spec/**`, `contracts/**`, and every backend service.
- `frontend/src/api/client.ts` — the operation and the factory already exist.
- `frontend/src/auth/CallbackScreen.tsx` — read as a template; not edited.
- The T43/T44/T45/T47 registration suites. **This is a prediction, not a guarantee:** T47 broke the same
  promise when attaching an element made thirteen cases in a closed suite crash, and that cost a
  ratification. If a pin in a closed suite moves, say so and have it ratified rather than quietly editing it.

## Acceptance criteria

| AC | Criterion |
|---|---|
| **AC1** | `/app/verify` resolves to an element; a cold arrival carrying a token renders a non-empty document. |
| **AC2** | The token is read from the `token` query parameter. |
| **AC3** | One `POST /accounts/verify-email` is sent, body `{ token }`, through the generated public client, **with no `Authorization` header**. |
| **AC4** | On `204`, the verified success state is shown. |
| **AC5** | Every user-facing string resolves from the catalogue; no literal and nothing from the response. |
| **AC6** | Focus is moved deliberately on arrival and the outcome is announced. |
| **AC7** | **Exactly one request per arrival**, including under StrictMode's double effect invocation — a one-time token verified twice fails the second time. |
| **AC8** | The token is written to no storage and appears in no sessionStorage key. |
| **AC9** | `PUBLIC_ROUTE_PATHS` is unchanged and the element is attached by a path constant. |
| **AC10** | The screen renders through `app/ui` primitives only; `RENDER_SITES` grows deliberately. |
| **AC11** | The four inherited suites' count, order and membership pins stay green. |

## Required tests

**Named:** `verifyFromLinkShowsSuccessState` → R3.

1. A `204` renders the success state, driven through the **real route table** from `/app/verify?token=…`.
2. The request body equals `{ token: <the token in the URL> }` — read inside the handler, since a body can
   be consumed once. Asserting only that *a* request was sent would pass for an empty token.
3. Exactly one request across a StrictMode double mount.
4. No `Authorization` header.
5. No storage write, spying on `Storage.prototype` — an instance spy does not intercept in jsdom (T42).
6. Focus where L11 requires, with a control proving the assertion can fail.
7. The catalogue keys render as literal English; the response body does not reach the DOM.
8. A token containing `+`, `%` and `/` survives the round trip, which tests the query decoding rather than
   the screen.
9. The inherited pins, moved deliberately.

## Constraints

| | |
|---|---|
| **Security** | The token is a bearer-equivalent possession credential in a URL. It is not stored (L16), not logged, and not put in any analytics or error payload. The call carries no session (`security: []`), so `createPublicAuthApi` is required — `createAuthApi` would attach a bearer the visitor does not have and arm renewal on a 401. |
| **Idempotency** | The token is one-time. A second POST for the same arrival is a defect, not a retry — hence AC7, and hence no automatic retry anywhere in this task. |
| **Module boundaries** | Screens render through the `app/ui` barrel only; backend calls go through the generated client only (L6), enforced by lint and by an end-to-end gate. |
| **Null handling** | A missing or empty `token` parameter is **not specified by R3 or R4** — see OQ-T48-3. Do not invent behaviour for it; it must be answered before implementation. |
| **Performance / thread-safety / transactions** | Not applicable to a browser client. No persistence, so no transaction boundary. |

## Open questions

| ID | Blocker |
|---|---|
| **OQ-T48-1** | **Is the success state its own screen, or one state of a screen shared with task 49?** R3's success and R4's uniform failure land on the **same route**, and `CallbackScreen` already demonstrates one screen with several states. This decides whether task 49 extends this task's file or writes its own, and therefore this task's file list. **Phase 3 must settle it first.** |
| **OQ-T48-2** | **Should the token be POSTed on arrival, or behind one confirm control?** On-arrival means any link previewer, email-scanning proxy or security gateway that opens the URL **consumes a one-time token before the human clicks**, and the human then sees task 49's failure. R3 reads as on-arrival and `CallbackScreen` acts on arrival, but OAuth compels that and nothing here does. A product decision with a security consequence. |
| **OQ-T48-3** | **What happens with no `token` parameter, or an empty one?** R3 covers a valid token; R4 covers the request failing. With no token there is no request, so neither requirement reaches it — and `/app/verify` is openable directly by anyone. |

OQ-T48-4 (the contract declares no failure response for `verifyEmail`) is recorded in Phase 1 and is **not a
blocker for this task**; it blocks task 49.

**Phase 2 complete. Next is Phase 3 — Design Challenge (Kimi 2.7).**
