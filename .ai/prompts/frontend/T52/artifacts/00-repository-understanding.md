<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). -->

# frontend · T52 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T52 — Reset request is uniform (`R14`) |
| **Named test** | `passwordResetRequestAcknowledgementIsUniform` → R14 (`package.md` §8) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**No code written. No design.**

---

## 1 · Architecture summary

A Vite + React 19 SPA, served under the `/app` basename, talking to the auth service through **one generated
typed client** (`@themistra/api-client`, `openapi-fetch`) over **relative paths only** — single origin, no base
URL, no env key (L9, L10). State is local `useState`; L13's TanStack Query and Zustand are **not** installed.

| Area | Where | Notes |
|---|---|---|
| Shell, router | `src/app/` | `routes.tsx` builds the real table from two path lists |
| Design system | `src/app/ui/` | **8 primitives**, Tailwind 4 CSS-first. `className` and `style` are **absent from the types** (T173) |
| Session, OIDC | `src/auth/` | SAS authorization-code + PKCE; `accessToken.ts` is the only token reader |
| Generated client | `src/api/client.ts` | `createAuthApi`, `createPublicAuthApi`, `createOidcApi`; package exports `createAuthClient`, `createOidcClient` |
| Copy | `src/i18n/catalogue.ts` | **every** user-facing string, English-only (L11) |
| Account screens | `src/features/account/` | register, register/sent, verify, resend-verification |

Persistence, outbox and resource-server security are **backend concerns** — not present here. The only
client-side storage is L16's sessionStorage carve-out.

## 2 · Existing code this task touches

### Already there — and further along than I expected

**`'reset/request'` and `'reset'` are ALREADY in `PUBLIC_ROUTE_PATHS`** (`routes.tsx:81-82`), as **bare string
literals** rather than named constants, with **no element attached**.

This is exactly the state T49 left `resend-verification` in and T50 picked up: *the path is declared and
public, and renders an empty document.* The difference is that T49 **linked to** its path before anything
rendered there; **nothing in the bundle links to either reset path** (checked: the only `reset` hit in
`SignInScreen.tsx` is a comment about re-enabling a button).

| Thing | State |
|---|---|
| `PUBLIC_ROUTE_PATHS` entry | **exists**, as `'reset/request'` |
| A named `*_PATH` constant | **absent** — the other eight are promoted, these two are not |
| An element on the route | **absent** |
| Catalogue keys | **absent** — the only `reset` hits are two comments about **task 54** reusing `verify`'s `failureMessage` |
| `POST /accounts/password-reset-request` in the generated client | **present** |
| Anything linking to the screen | **nothing** |

### The contract is structurally identical to resend-verification

```yaml
/accounts/password-reset-request:
  post:
    operationId: requestPasswordReset
    security: []                 # public
    responses:
      '200':
        description: Always returned regardless of whether the email matches an account.
        schema: RegistrationAcknowledgement       # required: [message]
```

Same `security: []`, same single `200`, **the same "always returned regardless" sentence** that T50/T51 read as
the whole of R5's uniformity, and the same `required: [message]` body that registration and resend both
deliberately **ignore** (T47-D2, and resend's header).

### The nearest siblings, and they chose **differently** from each other

| | Acknowledgement | On a dropped connection |
|---|---|---|
| `RegisterScreen` → `RegisterSentScreen` | a **separate route**, `/app/register/sent` | renders **nothing** — `setStatus('idle')`; **OQ-T43-9** open |
| `ResendVerificationScreen` | **in place**, one `<Heading>` whose text changes (**D-T50-2**, forced by measurement: a second `<h1>` would break a locked a11y rule) | a **neutral retry line** (**D-T50-1**, a reversal) |

### A new, direct dependency landed one task ago

**T175's scan (`src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts`) will fail on this task's
new call** unless it passes `parseAs: 'stream'` **and** the scan's census table gains the row. This screen will
not read the reply body — nothing in R14 needs it — so it is a non-reader by the scan's rule. **This is the
scan's first customer, and it is working as designed rather than being an obstacle.**

## 3 · Established patterns to follow

| | |
|---|---|
| **Copy** | Every string via `t()` from `catalogue.ts` (L11). A server-supplied `message` has no key, so it cannot be rendered |
| **Primitives only** | `Screen`, `Heading`, `Text`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion` — a suite pins that screens use no bare elements |
| **One `<h1>` per screen** | All seven existing screens; the design-system suite pins `h1` length 1 for three of them |
| **The client** | `createPublicAuthApi()` for an unauthenticated call; **`parseAs: 'stream'` on any call that does not read the body** (T51, T175) |
| **Announcement** | focus move for a state replacement, `StatusRegion` for in-flight — *not interchangeable* (T50) |
| **Route attachment** | a `// TASK n` house comment beside each element in `routes.tsx` |

## 4 · Testing conventions

Vitest + jsdom, **no Testcontainers and no `Clock`** here. Cases mount the **real route table** through a
memory router at an `/app` location, so every case goes through `routes.tsx`. `fetch` is stubbed per case;
`Request` needs a browser-style relative-URL shim (T05). `router.state.location.pathname` **carries the
basename**; `useLocation().pathname` does not. Source **scans** are a first-class instrument and must assert
their own census. Every task runs a mutation harness whose rows must die.

## 5 · Known gaps and unknowns

### **G1 — L14's prose and §4c's verbatim list disagree, and §4c wins by L14's own words**

L14 says *"The route paths are listed verbatim in §4c"*, then enumerates **ten** things including
*"password-reset request **and its acknowledgement**"*. §4c's confirmed list has **nine** paths and **no
acknowledgement path for reset**:

```
/app/sign-in, /app/callback, /app/register, /app/register/sent, /app/verify,
/app/resend-verification, /app/reset/request, /app/reset, /app/signed-out
```

**Q6 is resolved** — *"confirmed as proposed (2026-10-10)"* (`package.md:368`) — so the nine paths are
confirmed and §4c's own "PROPOSED pending Q6" heading is stale. I am **not** resolving this here: it decides
whether the acknowledgement is a route or an in-place swap, which is Phase 1/3/4's business.

### **G2 — R14 specifies no failure behaviour at all**

R14 ends at *"SHALL show the same uniform acknowledgement."* R15, one line below, **does** say *"On any failure
it SHALL show the uniform failure of R4"* — and R14 does not. The two siblings chose **opposite** things
(§2), and one of those choices is an **open question against its own screen** (OQ-T43-9). **I do not know
what R14 wants on a dropped connection.**

### **G3 — nothing links to `/app/reset/request`**

The screen would be reachable only by typing the URL. R14 presupposes *"the password-reset request form"*
exists; the task statement says nothing about a link, and **`SignInScreen` has no "forgot password" affordance**.
Whether adding one is in scope is a Phase 1 question. It bears on the demo path, which currently reaches
sign-in from the landing page.

### **G4 — the token-bearing sibling is a different task**

`/app/reset?token=…` (R15, task 53) is the confirmation screen; the 2026-10-19 note fixes the token as a query
parameter, as `/app/verify` already does. **Out of T52's scope**, but it shares the `reset` path prefix, so the
route shapes interact.

### **G5 — two catalogue comments already name task 54**

`catalogue.ts:174-175` records that **task 54** reuses `verify`'s `failureMessage` and that a reset failure
offers no resend. Written before this task, it is evidence about a *later* one, and I have not assumed it binds
T52.

---

**Phase 0 complete. Next is Phase 1 — Specification Extraction.**
