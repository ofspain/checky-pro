<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). -->

# frontend · T52 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**No design. No implementation.**

---

## 1 · Business rules

| ID | Rule |
|---|---|
| **R14** | *"WHEN a visitor submits any email to the password-reset request form, THEN the SPA SHALL call `POST /accounts/password-reset-request` and SHALL show the same uniform acknowledgement. (backend R12)"* |

**R14 is the whole of the scoped requirement set, and it is two obligations:** the call, and **one**
acknowledgement for **any** email.

**R4 is NOT cited by R14** — and R15, the line immediately below, *does* cite it (*"On any failure it SHALL
show the uniform failure of R4"*). The omission is the subject of **OQ-T52-2**; I am not reading R4 into R14.

## 2 · Locked decisions that constrain this task

Derived from `design.md` §4a, plus `agents.md`, which is authoritative and not restated.

| ID | What it binds here |
|---|---|
| **L4** | **The reason this task exists.** *"Copy, redirects, styling, and **timing** are identical across unknown or missing, locked, suspended, and deleted accounts…, on every **public or self-service surface**."* A public reset form is squarely inside it, and there is **no carve-out** — §4a's only carve-out (Q14/D-033) is the authenticated ADMIN status view |
| **L6** | Generated client only — `libs/ts/api-client`. No hand-written `fetch` (a lint gate enforces this) |
| **L9** | Relative path, single origin, no base URL |
| **L10** | No env key, no secret |
| **L11** | WCAG AA, keyboard operation, **managed focus**, **live regions for async results**, **catalogue-only strings** |
| **L13** | Confirmed stack. TanStack Query and Zustand are *named* in L13 but **not installed**, so local `useState` is the established pattern (D9, every sibling screen) |
| **L14** | The route set. `reset/request` is **already in `PUBLIC_ROUTE_PATHS`**; no ADR needed. See **OQ-T52-1** |
| **L16** | The sessionStorage carve-out is **closed**: `state`, `nonce`, `code_verifier`, the return target, and R29 form input only. **The submitted email may not be stored** — it is in none of those categories, and "no PII is stored in either case" is explicit |

### `agents.md` resolves L4's hardest clause, and it must be quoted exactly

> *"The SPA cannot control server response timing. **The timing requirement applies to the SPA's own
> rendering only.**"*

So L4's *timing* obligation is **testable and bounded**: the screen must not introduce a delay that varies by
outcome. It is **not** a claim about the server. An instrument for this already exists —
`auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` owns a **tree-wide timer scan with a
pinned allow-list** (established T51, D-T51-3).

**L17 does not constrain this task.** Its path list in `agents.md` is **backend prefixes** (`/accounts`,
`/oauth2/`, …), not SPA routes; `/accounts/password-reset-request` is correctly in it as an API route, and
`/app/reset/request` is a navigation that *should* receive the shell. Phase 0 raised this as a possible
dangling reference; **it is not one.**

## 3 · Files involved

### Existing, to extend

| File | Change the spec expects |
|---|---|
| `src/app/routes.tsx` | Promote `'reset/request'` to a named `*_PATH` constant and attach an element, with the house `// TASK 52` comment |
| `src/i18n/catalogue.ts` | A new section — every string on the screen (L11) |
| `src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | **The census gains a row.** A non-reading call must carry `parseAs: 'stream'` or this scan fails (T175) |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | Already asserts the public path set; a promoted constant must stay consistent with it |

### New

| File | |
|---|---|
| `src/features/account/PasswordResetRequestScreen.tsx` | the screen |
| `src/features/account/passwordResetRequestAcknowledgementIsUniform.test.tsx` | the named test (`package.md` §8) |

## 4 · Dependencies

| | |
|---|---|
| **Call** | `POST /accounts/password-reset-request`, `operationId: requestPasswordReset`, **`security: []`** |
| **Request** | `PasswordResetRequest` — `required: [email]`, `format: email`. **One field** |
| **Response** | exactly one `200`, *"Always returned regardless of whether the email matches an account"*, schema `RegistrationAcknowledgement` (`required: [message]`) |
| **Client** | `createPublicAuthApi()` from `src/api/client.ts` |
| **Primitives** | `Screen`, `Heading`, `Text`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion` — all exist; `className`/`style` are absent from their types |
| **i18n** | `useTranslation()` / `t()`; catalogue is English-only |
| **Config keys** | **none** (L9, L10) |

**The `message` the contract requires is deliberately unread**, as registration (T47-D2) and resend both
settled: a server-supplied sentence has no catalogue key, so rendering it would break L11.

## 5 · Acceptance criteria

All trace to **R14** unless marked.

| AC | Criterion |
|---|---|
| **AC1** | Submitting an email calls `POST /accounts/password-reset-request` exactly once, with that email in the body |
| **AC2** | The call is **public** — no `Authorization` header is sent, and the screen works with no session (`security: []`) |
| **AC3** | The acknowledgement after a declared `200` is **non-empty** and comes from the catalogue |
| **AC4** | **The acknowledgement is byte-identical across every outcome the server can return** — the uniformity claim, and the reason the named test exists (R14, L4) |
| **AC5** | The screen renders **nothing the server sent** — the required `message` is never displayed (L11) |
| **AC6** | The submitted email never reaches the URL, `sessionStorage`, `localStorage`, or any log (L4, L16) |
| **AC7** | The call passes **`parseAs: 'stream'`**, and the T175 scan's census is updated (T175, and the reply body is never read) |
| **AC8** | No delay on this screen varies by outcome (**L4's timing clause**, as `agents.md` bounds it) |
| **AC9** | One `<h1>`; focus is managed on the state change; the in-flight state is announced in a live region (L11) |
| **AC10** | The screen renders through design-system primitives only, with no bare elements (L11, T173) |
| **AC11** | `reset/request` resolves under `/app` and only under `/app`, and stays in the public set (L14) |
| **AC12** | Whole suite, `oxlint`, `tsc --noEmit` green |

## 6 · Tests required

| | |
|---|---|
| **Named** | **`passwordResetRequestAcknowledgementIsUniform`** → R14 (`package.md` §8:118) |
| **Boundary — the uniformity set** | The contract declares **one** `200`, so the arms that can differ are what a *proxy or edge* returns, not what the contract allows: a body that is valid JSON, an empty body, a `200` with an **unparseable** body, and other statuses. T51's 15-arm comparison is the established technique |
| **Boundary — provenance** | A screen interpolating literals passed 126/126 in T45 until the mutant was *applied* — the acknowledgement must be shown to come from the catalogue, not from a string that merely matches |
| **Boundary — the email** | Query string, hash, full URL, and both storages |
| **Boundary — the public claim** | No `Authorization` header, and a case that runs with no session at all |
| **Inherited, must not regress** | `routesResolveUnderAppPrefixOnly`, `screensRenderThroughDesignSystemPrimitivesOnly`, the design-system `h1` pins, the timer scan, and **T175's scan** |

## 7 · Open questions

### **OQ-T52-1 — is the acknowledgement a route or an in-place swap?** *(blocks the design, not the extraction)*

**L14's prose and §4c's confirmed list disagree.** L14 enumerates *"password-reset request **and its
acknowledgement**"* — ten items — while §4c lists **nine** paths with **no** reset acknowledgement:

```
sign-in, callback, register, register/sent, verify, resend-verification, reset/request, reset, signed-out
```

**L14 itself says the paths "are listed verbatim in §4c"**, and **Q6 is resolved — *"confirmed as proposed"*
(`package.md:368`)** — so the nine are confirmed and §4c's own "PROPOSED" heading is stale. The two siblings
also diverge: registration uses a separate route, resend swaps **in place** because L14's list gives it no
`/sent` path (**D-T50-2**, where a second `<h1>` would have broken a locked rule).

**Reading:** §4c governs, so the acknowledgement renders **in place**. **Phase 3 decides; I am not deviating
silently.**

### **OQ-T52-2 — R14 names no failure behaviour, and the siblings chose opposite things**

R14 stops at the acknowledgement. R15 cites R4's uniform failure; R14 does not.

| | On a dropped connection |
|---|---|
| `RegisterScreen` | renders **nothing** — **OQ-T43-9** is open against exactly this |
| `ResendVerificationScreen` | a **neutral retry line** — **D-T50-1**, a deliberate reversal, because *"a visitor who presses a button and sees the screen go back to how it looked has been told nothing at all"* |

A transport failure is **orthogonal to L4** — it says nothing about whether the address is registered — so
either choice is enumeration-safe. **This is an owner decision.** The resend precedent is the more recent and
the better argued, and D-T50-1's reasoning applies verbatim.

### **OQ-T52-3 — nothing links to `/app/reset/request`, so the screen is reachable only by typing a URL**

`SignInScreen` has **no** "forgot password" affordance, and no other screen references either reset path. R14
presupposes *"the password-reset request form"*; **the task statement does not mention a link**, and
`tasks.md` gives no later task that adds one. T49 set the opposite precedent by promoting a path **and
linking to it** before anything rendered there. **Whether a link is in T52's scope needs the owner**, and it
bears on the demo path.

---

**Phase 1 complete. Next is Phase 2 — Codebase Investigation.**
