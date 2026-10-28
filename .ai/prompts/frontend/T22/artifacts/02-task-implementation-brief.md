# frontend · T22 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

---

## Task

Build `tokensAreNeverWrittenToAnyStorage` (`package.md:157` → R7): prove, package-wide, that no token is written
to `localStorage`, `sessionStorage`, IndexedDB, a cookie, a URL, or a log — and leave an instrument that keeps
proving it.

## Purpose

Tasks 15-21 built every step that could leak a token: the authorize request that writes three items to
sessionStorage, the exchange that receives two tokens, the screen that handles them, the holder that keeps one,
the boundary that puts it on a header and the channel that sends it to sibling tabs. Each of those tasks checked
its own leak surface. **Nobody has checked the package as a whole**, and a leak is invisible by nature: a token in
`localStorage` changes no behaviour until someone reads the storage.

T22 is the sweep. Its product is a test, not a feature.

## Scope

**In**
- One new test file that drives the **real** flow end to end and sweeps every surface named in the task statement.
- A **package-wide source scan** over every production module under `frontend/src`.
- Sweeps over **every failure path**, not only the success path.
- Self-proving instruments: each scan catches a synthetic violation, and no scan passes on empty input.
- The minimal production fix **if** the sweep finds a real leak.

**Out**
- Building the real bundle (**T22-D1**).
- Deleting the three L16 items at callback — **task 23** (`stateSurvivesRedirectAndIsConsumedOnce`).
- The full sessionStorage inventory against both halves of L16, including R29's TTL — **task 26**
  (`sessionStorageHoldsOnlyTheL16Items`).
- Recovery codes (**task 69**), receipts and the Cache Storage runtime (**task 116**, R57's own test).
- The authorization `code` left in the URL after a successful callback (**T22-D3**).
- Wiring the composition root, and silent re-authentication. Still unassigned package-wide; carried from T21.

## Business Rules

| ID | Line |
|---|---|
| **R7** | The access token is held **in memory only** (`requirements.md:23-24`). |
| **R29** | No tokens, passwords, recovery codes, API keys or PII in any storage (`requirements.md:101-103`) — the prohibition half only; the TTL half is task 26's. |
| `agents.md:79` | Tokens are never written to `localStorage`, logs or analytics, and are **never placed in a URL**. |
| `agents.md:80-82` | sessionStorage is the only sanctioned sensitive store, limited to L16; never log a token. |
| `agents.md:85` | Token response bodies are never cached. |

## Locked Decisions

**L3** (scoped) — tokens in memory only; also records that **no `refresh_token` is issued to this client**, so a
stored refresh token cannot exist. **L16** — the sessionStorage carve-out is exactly three PKCE items plus R29's
form input, which means `state`, `nonce` and `code_verifier` are **permitted** and are not what T22 forbids.
**L17** — the Cache Storage surface, already built. **L13** — no new dependency, no state library. **L4** —
neutral copy. **L9** — one origin.

## Frozen decisions for this task

| ID | Decision |
|---|---|
| **T22-D1** | **The sweep does not build the bundle.** `bundleContainsNoSecretEnvKeys` already owns the build-time instrument and is the suite's slowest file; a second full build doubles that cost for a class of leak (one introduced by a dependency) that the source scan and the runtime sweep together cover. Recorded as a limitation in the test's own header, not left implicit. |
| **T22-D2** | **"A token" means the access token, the ID token — whole, each of its three segments, and any decoded claim value — and any `refresh_token` should one ever appear.** Not `state`, `nonce` or `code_verifier`: L16 permits those, so forbidding them would contradict the spec. The definition lives in the test file as a named constant, not in prose. |
| **T22-D3** | **The authorization `code` in the URL is out of scope**, recorded as an observation. It is a one-time credential rather than a token, and SAS put it there — `agents.md:79` forbids the SPA *placing* a token in a URL. Named as a question for task 24, which owns the post-login navigation. |
| **T22-D4** | **"A log" includes a thrown `Error`'s message**, because an uncaught rejection carries one to a console. One scan, closing the only route to a console in a package that makes no `console` call. |
| **T22-D5** | **The sweep reads values, not keys.** Every surface is dumped whole and searched for token values and segments. A key-list check would miss a token written under an innocuous name, which is the shape a real leak takes. |
| **T22-D6** | **Every instrument proves itself.** Each scan is applied to a synthetic violation and must catch it; a scan over empty input must fail rather than pass. The house rule, from `bundleContainsNoSecretEnvKeys`' own header: a scanner never shown to catch a leak has verified nothing. |
| **T22-D7** | **If the sweep finds no leak, nothing in production changes.** Phase 0 measured one storage writer, no `removeItem`, no `clear`, no `console` call. A verification task that ends with no production diff has succeeded, not under-delivered. |

## Dependencies

All built, none new: `buildAuthorizeRequest` / `AUTHORIZE_STORAGE_KEYS` / `redirectUri`
(`auth/authorizeRequest.ts`), `exchangeAuthorizationCode` (`auth/codeExchange.ts`), `CallbackScreen` + the route
graph, `accessToken` / `retainAccessToken` / `clearAccessToken` (`auth/accessToken.ts`), `createSessionBoundary`,
`createAuthApi`, `shareRenewalAcrossTabs`, and `Storage.prototype` for the write spies. Node `fs`/`path` for the
source scan. No contract, no config key, no catalogue key.

## Inputs

A stubbed `fetch` answering `/oauth2/token`; URLs the callback screen arrives at; the real `sessionStorage` and
`localStorage` jsdom provides; the production source tree on disk.

## Outputs

Assertions. No module export, no user-visible behaviour.

## State Changes

**None in production.** Within the test: the three L16 items are written by the real authorize step, and the
holder is cleared between cases.

## Files to Create

`frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts` — the named test. `src/auth/` because every token in
the package originates there; the scan still reads the whole tree.

## Files to Modify

None expected (**T22-D7**). If the sweep finds a leak, the file that leaks, plus nothing else.

## Files NOT to Modify

`spec/**` (standing rule). `api/sessionBoundary.ts`, `api/crossTabRenewal.ts`, `api/client.ts` — built by tasks 5
and 6, and their transient in-flight copies of the token are *required* by R21/R57, not leaks. `auth/accessToken.ts`
and `auth/CallbackScreen.tsx` — T21's, and the subject of the measurement. `auth/authorizeRequest.ts` — task 15's
three writes are L16-sanctioned; the deletion L16 also calls for is **task 23's**. `pwa/*` — R57's, built.
`app/bundleContainsNoSecretEnvKeys.test.ts` — T01's instrument, referenced not extended.

## Acceptance Criteria

AC0-AC14 as extracted in Phase 1, unchanged, with AC0 now frozen as **T22-D2**:

**AC0** the token definition is a named constant in the file · **AC1** no token in either web store after a
successful flow, proven by dumping all keys *and values* · **AC2** sessionStorage holds exactly the three L16
items, each equal to the value the authorize step generated · **AC3** `document.cookie` gains nothing · **AC4** no
IndexedDB database is opened · **AC5** no token in `location.href`, `search`, `hash`, `document.title` or the
rendered DOM · **AC6** no token in any of the four console channels or a thrown `Error`'s message (T22-D4) ·
**AC7** exactly three `setItem` calls in a successful flow, all from the authorize step, none carrying a token; no
`removeItem`, no `clear` · **AC8** every failure path swept: no `state`, mismatched `state`, no `code`, an
authorization error, a refused code, a 5xx, a dropped connection, an unusable response, a bad nonce · **AC9** a
package-wide source scan: no `localStorage`, `indexedDB`, `document.cookie`, `console.*`, and no `setItem` outside
`authorizeRequest.ts`, comments stripped and the stripped body asserted non-empty · **AC10** every instrument
proves itself and refuses empty input (T22-D6) · **AC11** the sweep survives a *throwing* store and reports
honestly rather than passing vacuously · **AC12** a token adopted from a sibling tab through the real
`shareRenewalAcrossTabs` is still in no storage surface · **AC13** no new string, key, dependency, or production
change · **AC14** the suite passes with the one known environmental failure and no other, **by exit code**.

## Required Tests

`tokensAreNeverWrittenToAnyStorage` — the named test, all of the above. Plus the boundary cases Phase 1 implied:
two sign-ins in one tab leaving no residue from the first; a sweep after `clearAccessToken()`; and the case that
makes T22-D5 real — a token written under an innocuous key must be caught by the same sweep, proven with a
synthetic write.

## Constraints

- **Security:** the test must not weaken anything to observe it. Spy on `Storage.prototype`, do not replace the
  store. Never assert against the constant under test.
- **Vacuity:** the single largest risk in this task. A sweep over a flow that never ran passes everything. Every
  block needs a control proving the flow *happened* — a token actually held, three items actually written.
- **Module instance:** if the harness imports the route graph dynamically with `vi.resetModules()` between cases,
  a statically imported module is **a different instance** from the one the screens hold. Measured twice (T20,
  T21). Import after the routes and read that instance, with a control that fails when it is wrong.
- **Comments:** a whole-file scan reads the file's own explanation as the defect. `accessToken.ts:12` names
  `sessionStorage` in order to forbid it. Strip comments, then assert the body survived.
- **jsdom:** no `caches`, no `indexedDB` implementation, no `navigator.locks`; `BroadcastChannel` exists. An
  absence assertion against an API jsdom does not implement is **vacuous** and must be made structurally instead.
- **Thread-safety / transactions:** not applicable (single-threaded, no persistence).
- **Null handling:** touching `sessionStorage` can **throw**, not merely return `null`.
- **No new dependency** (L13). Node built-ins and the existing devDependencies only.

## Open Questions

**No blockers.** The three scoping questions from Phase 1 are frozen above as T22-D1, T22-D3 and T22-D4.

One thing to flag for the author rather than decide here, consistent with T21's §6: **AC4 and the Cache Storage
surface cannot be proven at runtime in this environment.** jsdom implements neither `indexedDB` nor `caches`, so
an assertion that "no database was opened" against a missing API is vacuous by construction. T22 will prove both
structurally — the source names neither API — and say so in the file rather than imply a runtime proof it did not
perform. A real-browser check belongs to whichever task owns end-to-end verification; **no task in `tasks.md`
1-39 is visibly that task.**

---

## Next — Phase 3 — design challenge (Kimi 2.7).
