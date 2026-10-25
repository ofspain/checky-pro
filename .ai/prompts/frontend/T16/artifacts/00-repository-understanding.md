# frontend · T16 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T16 — No password field |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 16):**
> **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.

---

## This is the first screen

Fifteen tasks have built machinery. **None of them renders anything.** `routes.tsx` declares twenty-one route
paths as bare `{ path }` objects with no `element`, so `/app/sign-in` serves a blank shell today. T16 attaches the
first element, and it is the first task whose output is *looked at* rather than asserted.

Two consequences shape everything below. First, T16 is the task that must finally provide the
`<I18nextProvider>` tree that T07's `RateLimitNotice` has been waiting for — its own doc comment says so. Second,
T01's shell test asserts the shell renders **nothing at all**, so the way the first element is attached decides
whether a frozen test from task 1 goes red.

---

## Architecture as it stands

`frontend/` is a pnpm-workspace package, `@themistra/frontend`, served in development by Vite on port 5173 —
behind the Caddy edge, which is **the single origin** (L9). There is no backend in this package; the SPA talks to
four Spring Boot services through the edge path map.

| Module | What lives there | Built by |
|---|---|---|
| `src/app/` | the shell, the route table, the mount entry, `RateLimitNotice` | T01, T07 |
| `src/api/` | the generated-client wrapper, session boundary, cross-tab renewal, the rate-limit notice store | T05–T08 |
| `src/auth/` | `pkce.ts` (T14), `authorizeRequest.ts` (T15) | T14, T15 |
| `src/i18n/` | `createI18n()` and the English catalogue | T07 |
| `src/pwa/` | worker source, navigation denylist, API request policy, registration | T09–T12 |

**Persistence:** none server-side; client-side state is constrained by **L16**, which is an exhaustive carve-out —
`sessionStorage` may hold `state`, `nonce`, `code_verifier` and the post-login return target, plus non-sensitive
mid-task form input with a 15-minute TTL, and **nothing else**. **L3** keeps access tokens in memory only.

**Events/outbox:** not applicable to this package.

**Security posture:** public OIDC client with PKCE, no client secret in the bundle (L1); interactive login —
password, TOTP, recovery codes — runs **inside the SAS-hosted flow**, never in the SPA (L2, `agents.md:23-24`).
That is the whole reason T16 exists as a named task: R6 makes "no password field" a verifiable property rather
than an implementation detail.

---

## Existing code this task touches

### Already exists, and T16 consumes it unchanged

| File | What T16 needs from it |
|---|---|
| `src/auth/authorizeRequest.ts` | `buildAuthorizeRequest(): Promise<{url, state, nonce}>` — T15, 50 cases. The whole request shape is settled: eight parameters, `S256`, exactly `openid profile email`, the three L16 items persisted. T16 calls it and navigates. |
| `src/app/routes.tsx:9` | `'sign-in'` is already in `PUBLIC_ROUTE_PATHS`. T16 attaches an element; it adds no path. |
| `src/app/AppShell.tsx` | the root `<div data-testid="app-shell">` every provider mounts inside. |
| `src/app/mountShell.tsx` | `StrictMode > AppShell > RouterProvider`. The provider tree T16 extends. |
| `src/i18n/i18n.ts` | `createI18n()` — one instance per tab, reaching components **only** through `<I18nextProvider>`; `initReactI18next` is deliberately unused, so there is no global instance to fall back on. |
| `src/i18n/catalogue.ts` | `en`, currently two keys under `rateLimit`. T16 adds the sign-in copy. |
| `src/app/RateLimitNotice.tsx` | the pattern for an accessible component here: `useTranslation()`, a `role="status" aria-live="polite"` region, explicit focus handling on unmount. |

### New in T16

- a sign-in screen component under `src/app/` (or `src/auth/` — phase 2 decides);
- the `<I18nextProvider>` wiring in the provider tree;
- `element` on the `sign-in` route;
- catalogue keys for the heading, the explanation and the action;
- the test `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`.

### What T16 must not touch

T01–T15 files beyond the four named above. In particular **`src/auth/authorizeRequest.ts` and `src/auth/pkce.ts`
are closed** — T15 reopened `pkce.ts` under an explicitly locked decision (D-2) and that is not a precedent to
reuse casually.

---

## Established patterns to follow

**Components.** Function components, no class components, no component library, no CSS framework —
`RateLimitNotice` is the only precedent and it renders plain elements with no `className` at all. React 19.3.1.

**No hardcoded copy, ever.** `agents.md:96`: "Every user-facing string is in the catalogue. No hardcoded copy in
components." The catalogue is `as const` and its type is threaded into i18next's `CustomTypeOptions`, so a key
that does not exist is a **type error**, not a runtime miss.

**Accessibility is a locked requirement, not a nicety** (L11, `agents.md:95`): keyboard-operable, managed focus
across redirect and MFA steps, async results announced through a live region, WCAG AA contrast.

**Testing.** Vitest 5.0.5 + jsdom 30.1.3. **There is no `@testing-library/react` in this package** — component
tests use `createRoot` + `act()` directly, with
`(globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true`, as
`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx:7-17` does. T16 follows that rather than adding a
dependency; no task since T01 has added one.

**Test naming.** One file per named test from `package.md` §8, named after the behaviour:
`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx`.

**Configuration.** Nothing is read from the environment. The owner decision of 2026-10-24 (recorded in
`.ai/prompts/frontend/FIRST-UI-READINESS.md`) is that OIDC configuration is **derived at runtime** — origin from
`location`, `client_id` the constant `checky-spa`, redirect URI from the route table. T15 implements exactly that
and reads no key. `design.md` §4c's four `VITE_*` keys end up unused.

---

## The constraint that decides T16's shape

**T01 asserts the shell renders nothing:**

```ts
// shellBuildsAndMounts.test.tsx:103-109
const shell = container.querySelector('[data-testid="app-shell"]');
expect(shell?.innerHTML).toBe(''); // no text, not even whitespace
```

A screen that rendered inside `AppShell` unconditionally would turn that red. It does not, and the reason is
narrow: T01 mounts at jsdom's default location, which is outside the `/app` basename, so the router matches no
route and renders nothing. The readiness note records this as probed on 2026-10-24 with T01 still 7/7, and the
probe reverted. **I will re-measure it rather than trust the note**, because it is the single assertion most
likely to break and it belongs to a do-not-modify file.

**T01 also scans every production source file** (`productionSourceFiles()` → `readdir(src, {recursive: true})`,
excluding `*.test.*`) for `import.meta.env` and for `https?://`. The second is a trap for a UI task: a comment
citing a documentation URL, or an `xmlns` on an inline SVG, would fail a task-1 test. T16's source may contain no
absolute address of any kind, in code or in comments.

---

## Testing conventions

Unit and component tests only; no Testcontainers in this package, no fixed `Clock` pattern (that is the Java
services'), no ArchUnit. The nearest equivalent to an architecture test is the family of source-scanning
assertions T01 and T13–T15 use — reading real files and asserting over their text — and T16 inherits two of them.

**Measured baseline on this host, before T16:**

| | |
|---|---|
| Whole suite | **529 pass / 531 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch` — **and they are two different environmental faults, which I have been conflating.** AC4 fails because this host's `oxlint@1.87.0` prints no summary line, so the assertion `expected '' to contain 'Found 0 warnings and 0 errors'` cannot pass. AC2 fails with `spawn pnpm ENOENT`, because bare `pnpm` is not on this host's PATH (`npx -y pnpm@12.14.0` is the working recipe). Both pre-existing, both unrelated to T16. |

---

## Known gaps and unknowns

1. **Where the screen's file belongs — `src/app/` or a new `src/screens/`.** `design.md` §6 has a planned file
   map; I have not yet read it closely enough to say what it names for screens. Phase 1 resolves this from §6
   rather than by preference.
2. **What the screen says.** R6 fixes what it must *not* render. Nothing I have read specifies the heading or
   body copy, and `agents.md` forbids inventing it in the component — it must be catalogue keys, but the catalogue
   is mine to extend. Phase 1 or 2 must decide whether the copy is a design decision needing the owner.
3. **Whether the sign-in action is a button or an automatic redirect.** R6 says "WHEN a visitor chooses sign-in" —
   which implies a deliberate choice, so a button. But `/app/sign-in` is also the route a session-expiry redirect
   would land on, and auto-redirecting would be hostile there. Phase 2 must settle it; I do not know which the
   owner intends.
4. **Focus management across a full-page redirect.** L11 requires managed focus "across redirect and MFA steps".
   A full-page navigation to SAS destroys the document, so there is nothing to restore on the way out; on the way
   *back* the callback route is task 17's. What L11 asks of T16 specifically is not obvious to me.
5. **Whether T16 should render `RateLimitNotice`.** T07's phase 13 note assigns the provider wiring to task 16,
   and the component becomes visible "through the same wiring". Whether T16 must actually *mount* it, or only make
   mounting possible, I do not know. It has no notice state to show on a public screen with no API calls.
6. **`design.md` §4c still says auth T19 is "not built" and "Blocked on Q1"**, while L5 in the same file records
   it as built. Pre-existing spec drift, irrelevant to T16, noted so it is not rediscovered.

---

## Next

Phase 1 — specification extraction: R6, the locked decisions that bear on a screen (L1, L2, L11, L14, L16), the
`design.md` §6 file map, and the open questions above turned into either a resolved decision or an explicit OQ.
