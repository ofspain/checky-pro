# frontend · T26 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

T26 is the carve-out guard. L16 names an exhaustive list of what sessionStorage may hold; twelve tasks have
since written to that store, each proving its own item. This task asks the opposite question — not "is my item
allowed?" but "is anything there that is not on the list?" — and it is the only task in the package that asks it.

---

## 1. Architecture summary

The service is a React 19.3.1 / TypeScript 7.0.2 SPA under `frontend/`, built by Vite 8.3.5, tested by Vitest
5.0.5 on jsdom 30.1.3, served under the `/app` basename. There is no server, no persistence layer, no outbox: the
"persistence" this task is about is the browser's own four client-side surfaces. Relevant modules:

- **`src/auth/`** — the OIDC client and the whole of this task's subject matter. `authorizeRequest.ts` starts the
  authorization-code flow and writes the PKCE items; `codeExchange.ts` reads one back; `idTokenNonce.ts` reads
  another; `CallbackScreen.tsx` reads the third and orchestrates; `returnTarget.ts` owns the fourth item;
  `accessToken.ts` holds the access token in a module-scoped variable and nowhere else (L3).
- **`src/app/`** — the route table (`routes.tsx`), the shell (`AppShell.tsx`), the composition root
  (`mountShell.tsx`), and `bundleContainsNoSecretEnvKeys.test.ts`, the other package-wide scanner.
- **`src/api/`** — the generated client, the session boundary that attaches the bearer header, and
  `crossTabRenewal.ts`, which shares renewal across tabs over BroadcastChannel / Web Locks (L18).
- **`src/pwa/`** — the service worker and its policies. Workbox `precacheAndRoute(self.__WB_MANIFEST)` plus a
  `NetworkOnly` default; the shell and hashed assets are cached and nothing else (design.md §5, L17).
- **`src/i18n/`** — the catalogue and i18next setup; every assertion about visible copy goes through it.

**Security posture.** Tokens live in memory only (L3). The only sanctioned client-side storage for sensitive data
is sessionStorage, and only for the items `design.md` L16 lists (`agents.md:79-81`). No PII is ever persisted
client-side. Secrets and environment values never enter the bundle.

## 2. Existing code this task touches

Everything this task touches **already exists**. T26 adds one test file and, on today's reading, no production
code.

**The complete production surface that touches a client-side store** (measured — `grep` for
`sessionStorage|localStorage|indexedDB|document.cookie|caches.` across `src/**/*.ts{,x}` excluding tests):

| File | Calls | Item |
|---|---|---|
| `auth/authorizeRequest.ts:81-84` | `setItem` ×3 | L16 (a): `state`, `nonce`, `code_verifier` |
| `auth/authorizeRequest.ts:121-123` | `removeItem` over `Object.values(AUTHORIZE_STORAGE_KEYS)` | the same three, consumed at callback |
| `auth/returnTarget.ts:91` | `setItem` | L16 (a): the post-login return target |
| `auth/returnTarget.ts:122,129` | `getItem`, `removeItem` | the same, read-then-delete at callback |
| `auth/codeExchange.ts:59` | `getItem` | reads the verifier back |
| `auth/idTokenNonce.ts:98` | `getItem` | reads the nonce back |
| `auth/CallbackScreen.tsx:125` | `getItem` | reads the state back |

So there are **exactly two writing modules and exactly four keys**, and both key sets are already published as
named constants rather than literals:

- `AUTHORIZE_STORAGE_KEYS` (`auth/authorizeRequest.ts:35-39`) — `themistra.auth.state`,
  `themistra.auth.nonce`, `themistra.auth.code_verifier`. Its doc comment, written in T15, says it is published
  "so task **26**'s carve-out guard enumerates the same keys, rather than either copying the strings".
- `RETURN_TARGET_KEY` (`auth/returnTarget.ts:39`) — `themistra.auth.return_to`.

**`localStorage` is not called anywhere in production source.** `design.md:193` reserves it for public
preferences (theme, locale); no task has built one yet. `indexedDB`, `document.cookie` and `caches` are likewise
absent from production source — a structural fact T22 already relies on.

**New in this task:** one test file, `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` (the named test from
`package.md:160`; no file of that name exists yet).

## 3. Established patterns to follow

There is no JPA, Flyway, outbox or resource server here; the analogous conventions are the package's own.

- **Two instruments, failing differently (T22-D14).** `tokensAreNeverWrittenToAnyStorage.test.tsx` is the nearest
  neighbour and the template: a **runtime sweep** that spies `Storage.prototype` (alias-proof by construction — a
  write through `globalThis['localStorage']` still calls the spied method) plus a **source scan** over every
  production module with comments stripped. The runtime sweep is primary; the scan is the second net.
- **An allowlist table, not a regex.** `STORAGE_ALLOWLIST` (`tokensAreNeverWrittenToAnyStorage.test.tsx:403-417`)
  maps file → permitted methods → *why*, so a violation names the file and says what to do. It has two rows
  today, one per writing module, and its comment states the rule for the next entrant: a new file that calls a
  storage method adds a row with a reason.
- **Prove the instrument before believing it.** Every sweep block in T22 carries a control that makes the
  instrument report DIRTY, because a leak is invisible by nature and the whole value of a sweep is its ability to
  fail. `productionSources()` refuses an empty tree for the same reason.
- **Constants over literals across module boundaries.** Tests import `AUTHORIZE_STORAGE_KEYS` and
  `RETURN_TARGET_KEY` rather than retyping the strings, so a renamed key breaks compilation rather than silently
  widening a guard.
- **Decisions are recorded as `T<n>-D<m>` in the production or test file that implements them**, with the
  measurement that justified them. `returnTarget.ts` is the clearest example.
- **Flat, derived route and key tables.** Nothing in `src/auth/` reads a route-table name at module scope
  (`returnTarget.ts:42-49`), because the import cycle makes a module-scope read yield `undefined` and fail closed
  silently. A scan enforces it. Any table T26 builds at module scope must be checked against the same hazard.

## 4. Testing conventions

- `npx -y pnpm@12.14.0 exec vitest run …` from `frontend/` is the run recipe on this host; bare `pnpm` is not
  installed. **The exit code is the verdict, not the pass count** — an unhandled rejection prints below the
  all-passed line.
- jsdom, not a browser: `IS_REACT_ACT_ENVIRONMENT = true`, `createRoot` + `act`, `createMemoryRouter` +
  `RouterProvider`, `I18nextProvider` with `createI18n()`. `fetch` is spied per test.
- **jsdom provides neither `indexedDB` nor `caches` nor `cookieStore`** (measured in T22: all three `undefined`).
  An assertion that no database was opened, against an API that does not exist, passes for the wrong reason.
  Those surfaces are proven *structurally*, by the source scan, and T22 says so rather than implying it.
- There is no ArchUnit and no Testcontainers here. The structural equivalents are the source scanners
  (`tokensAreNeverWrittenToAnyStorage`, `bundleContainsNoSecretEnvKeys`, `lintRejectsHandWrittenBackendFetch`) and
  `tsc --noEmit`.
- Known environmental failure on this host, pre-existing and unrelated:
  `lintRejectsHandWrittenBackendFetch.test.ts:118` (this host's `oxlint@1.87.0` prints no summary line).
- Tests must be **committed before any mutation run**, because the harness reverts with `git checkout`.

## 5. Known gaps / unknowns

1. **L16 clause (b) has no implementation and no owning task.** "Non-sensitive mid-task form input for R29, with a
   15-minute TTL" is half the carve-out, and `grep` finds no production code for it. Its named test,
   `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` (`package.md:153`), **is not claimed by any of the 125
   tasks in `tasks.md`** — measured, not inferred. Whether T26's inventory should therefore name clause (b) as a
   permitted-but-absent item, or whether a guard that forbids everything but the four known keys would block a
   future R29 implementation, is a Phase 1/2 question. I do not know which the author intends.
2. **Whether T26 is verification-only.** No production change is anticipated — the four keys are the whole
   surface and both key sets are already exported for this task's use — but I do not know whether the gate wants
   a single published inventory constant (one spelling of the carve-out in the package) or an assertion that
   derives the inventory from the two existing constants.
3. **The relationship to T22's `STORAGE_ALLOWLIST`.** T22 already guards *which file may call which method*; T26
   guards *which keys may exist*. These are different claims, and T22's header explicitly hands over "the full
   L16 inventory and R29's TTL (task 26)". Whether T26 should extend T22's table or stand up its own instrument
   is a Phase 2 decision.
4. **"Nothing else is stored client-side" versus the service-worker cache.** L16's sentence is absolute, while
   `design.md:194` separately permits a precache of the static shell and assets. I read §5 as scoping L16's
   sentence to the per-key stores, with the cache governed by L17 — but the texts are in tension and the reading
   should be confirmed at the gate rather than assumed.
5. **`localStorage` has no production caller at all today**, so a guard over it is a guard over the empty set. It
   is still worth asserting (the point of a sweep is to fail when that changes), but it cannot be demonstrated
   against a real write without a synthetic one.
6. **Carried forward from T24/T25, unchanged and not this task's:** `/app/home` has no element and no owning task
   (T24-D11); `rememberReturnTarget` has no production caller until task 41 (T24-D2);
   notification-email deep links to `invoices/:id` / `receipts/:id` are not honoured (T24-D5).

---

**Phase 0 complete.** No code written, no requirements extracted, no design proposed.
