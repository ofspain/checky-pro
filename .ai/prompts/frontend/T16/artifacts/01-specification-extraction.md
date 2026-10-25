# frontend · T16 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 16):**
> **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

---

## The scoped requirement

**R6** (`requirements.md:21-22`), verbatim:

> WHEN a visitor chooses sign-in, THEN the SPA SHALL start the OIDC authorization code flow with PKCE and SHALL NOT
> render a password field. (backend R14; `agents.md` identity rules)

Three obligations, and the third is the one the task is named for:

1. **"WHEN a visitor chooses sign-in"** — a deliberate act by the visitor. This resolves phase 0's unknown 3: the
   screen offers an action the visitor takes, **not** an automatic redirect on arrival. Two independent reasons
   confirm it. R28 redirects every non-public route to the sign-in start, so an auto-redirecting sign-in screen
   would bounce an expired session straight back into SAS with no way to stop; and R8 returns a discarded callback
   "to the sign-in start with neutral copy", which cannot be read if arriving there immediately leaves.
2. **"SHALL start the OIDC authorization code flow with PKCE"** — T15 already built the request. T16 calls
   `buildAuthorizeRequest()` and performs the full-page navigation.
3. **"SHALL NOT render a password field"** — the verifiable property. The SPA never collects a credential; SAS
   does (L2).

---

## Locked decisions in scope

| | Bearing on T16 |
|---|---|
| **L1** Public OIDC client with PKCE | T15 satisfies the request shape. T16 must not add a client secret, a second client id, or any parameter of its own. |
| **L2** Interactive login is SAS-hosted | **The reason R6 exists.** Password, TOTP and recovery-code steps run inside SAS (`agents.md:23-24`: "The SPA renders no password form"). T16 renders no credential input of any kind — not a password, not an email, not a username. |
| **L9** Single origin | The authorize URL is this page's own origin. T16 navigates to a same-origin path; it names no host. |
| **L10** Secrets stay out of the bundle | T16 reads no environment key. Enforced for free by T01's `src/**` scan. |
| **L11** Accessibility and i18n on every screen | **The binding constraint on this task's shape.** Keyboard-operable, managed focus, async results through a live region, WCAG AA, catalogue-only strings. This is the first screen, so it is the first time L11 is testable at all. |
| **L14** SPA route set under `/app` | `sign-in` is already declared public (`routes.tsx:9`). T16 attaches an element and adds no path. |
| **L16** The only `sessionStorage` carve-out | T15 writes the three items. **T16 writes nothing** — it must not add a key, and the post-login return target belongs to tasks 24–25. |
| **L12** Phase additivity | T16 extends the shell's provider tree; it does not replace the shell, the routing or the auth model. |
| **L13** Confirmed stack | React + React Router + Vitest, all present. **No new dependency** — in particular no component library, no CSS framework, and no `@testing-library/react`. |

---

## Where the files go, from the file map

`design.md` §6 settles phase 0's unknown 1 without recourse to preference:

```
│   ├── app/                  shell, providers, router under /app   (L13, L14)
│   ├── auth/                 OIDC client, session owner, guards    (L1–L3, L16, L18)
```

- The **sign-in screen** is the OIDC client's entry point → **`src/auth/`**, beside `pkce.ts` and
  `authorizeRequest.ts`.
- The **`<I18nextProvider>` wiring** is a provider → **`src/app/`**, in `mountShell.tsx`.

`features/account/` covers register, verify, reset and sessions — not sign-in, which is the OIDC flow itself.
No new directory is needed.

---

## Scope boundary, from the task list

The adjacent tasks claim everything T16 might otherwise absorb, so the boundary is read off `tasks.md` rather
than judged:

| Not T16 | Owner |
|---|---|
| Callback handling, `state` comparison, discarding | **17, 18** |
| `nonce` validation, code exchange, token in memory | **19, 20, 21, 22** |
| Deleting the three L16 items at callback | **23** |
| The post-login return target and its allowlist | **24, 25** |
| `sessionStorage` holds only L16 items | **26** |
| The SAS MFA challenge resuming at callback | **27** |
| **The uniform sign-in failure message**, including the unenrolled-privileged case | **28, 29** |
| Redirecting non-public routes to the sign-in start | **41** (R28) |

This resolves a question phase 0 did not ask. R8 and R10 both describe copy shown *on* the sign-in start — a
discarded callback returning "with neutral copy", and one uniform failure message for every SAS failure — and it
would be easy to build that here. **It is tasks 28 and 29.** T16 renders the screen and starts the flow; the
failure surface arrives later and will mount into it.

What *is* T16's own: the failure of **its own** async step. `buildAuthorizeRequest()` returns a promise and can
reject — T15 made it reject rather than build a half request, and T15's own suite proves storage is left empty
when it does. L11 requires async results to be announced through a live region, so T16 owns announcing *that*
outcome. It is not a SAS failure and does not use tasks 28–29's copy.

---

## Acceptance criteria

- **AC1** — the sign-in screen renders **no password input**: no `input[type=password]`, and no credential input
  of any kind. Asserted over the rendered DOM, not over the source.
- **AC2** — choosing sign-in calls `buildAuthorizeRequest()` and navigates the whole page to the URL it returns.
  The navigation is a real assignment to `location`, not a router navigation, because the destination is outside
  the SPA.
- **AC3** — the screen renders nothing until the visitor acts: no `buildAuthorizeRequest()` call, no
  `sessionStorage` write, and no navigation on mount. (R6's "chooses".)
- **AC4** — the three L16 items are in `sessionStorage` after the action, written by T15 and not by T16, and T16
  adds no fourth key.
- **AC5** — every string comes from the catalogue. No literal user-facing text in the component.
- **AC6** — keyboard operable: the action is a real `<button>`, reachable and activatable by keyboard, with an
  accessible name from the catalogue.
- **AC7** — the screen has one `<h1>`, and the document has a live region for async results (L11).
- **AC8** — if `buildAuthorizeRequest()` rejects, the screen stays, announces a catalogue message in the live
  region, does not navigate, and leaves `sessionStorage` empty. The action can be retried.
- **AC9** — **T01 stays green, untouched.** The shell still renders nothing when mounted outside `/app`, and no
  production source file names `import.meta.env` or an absolute address — including in comments and in any
  inline SVG.
- **AC10** — no new dependency, no change to `package.json`, no change to any T01–T15 file except the four this
  task must extend: `routes.tsx` (an `element`), `mountShell.tsx` (the provider), `catalogue.ts` (keys), and
  nothing in `src/auth/pkce.ts` or `src/auth/authorizeRequest.ts`.
- **AC11** — measured suite delta: the count immediately before T16 plus T16's new cases, with the two known
  environmental `lintRejectsHandWrittenBackendFetch` failures and no others.
- **AC12** — non-vacuity: every assertion that could pass for the wrong reason is fed a deliberately wrong input
  through the same matcher. This is the fifth-time-lucky rule from T12–T15 — an assertion compared against the
  constant it is testing proves nothing.

---

## Open questions

**OQ-1 — the screen's copy is not specified anywhere, and I may not invent it in the component.**
`agents.md:96` requires catalogue-only strings, which settles *where* the copy lives but not *what it says*. R6
fixes only what must not be rendered. Nothing in `requirements.md`, `design.md` or `package.md` gives a heading or
a body sentence for the sign-in screen.

I will write plain, neutral English into the catalogue — a heading, one sentence explaining that sign-in continues
on the secure sign-in page, and a button label — and flag it for the owner. The copy is a product decision, not an
engineering one; the catalogue is the right place for it to be changed without touching a component, which is
precisely what L11 and `agents.md:96` are for. **This does not gate the task**: the structure, the behaviour and
every acceptance criterion are independent of the wording, and the owner can reword it in one file.

**OQ-2 — does T16 mount `RateLimitNotice`?** (Phase 0, unknown 5.) T07's phase-13 note assigns the provider
wiring to task 16 and says the component becomes visible "through the same wiring". My reading: T16 owes the
*wiring*, not the mounting. The sign-in screen makes no API call, so it has no 429 to show, and mounting a notice
with no state to display would be dead markup on the first screen anyone sees. **Decision: wire the provider, do
not mount the notice.** Recorded as an OQ because it interprets another task's note rather than a requirement.

**OQ-3 — what L11's "managed focus across redirect" asks of T16.** (Phase 0, unknown 4.) A full-page navigation
destroys the document, so there is no focus to restore on the way out, and the return journey lands on the
callback route (task 17). What T16 can do, and will: give the screen a focusable heading region and move focus to
the live region when an async failure is announced, so a keyboard user is not left on a button whose action
silently did nothing. Returning focus *after* a redirect is not implementable within one document and is not
claimed.

**Carried, gating nothing:** OQ-1 from T13/T15 (T01's `import.meta.env` ban versus `design.md` §4c's four keys —
T16 needs no key either, so the ban and the allowlist still push the same way); T12's OQ-1 (a refresh cannot
promote a waiting worker); `design.md` §4c saying auth T19 is "not built" while L5 in the same file records it as
built.

---

## Next

Phase 2 — implementation brief: the component's shape, the provider tree change, how the navigation is performed
and spied, and the catalogue keys.
