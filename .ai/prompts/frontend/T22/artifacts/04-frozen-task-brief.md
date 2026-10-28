# frontend · T22 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 (human approval gate) |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**This is the only specification the implementation and review phases use.** All six Phase 3 findings are
accepted; two with their mechanism corrected. Decisions T22-D1..D11 are frozen.

---

## 1 · Task and purpose

Build `tokensAreNeverWrittenToAnyStorage` (`package.md:157` → R7): prove, package-wide, that no token reaches
`localStorage`, `sessionStorage`, IndexedDB, a cookie, a URL, or a log — and leave an instrument that keeps
proving it.

Tasks 15-21 each checked their own leak surface. Nobody has checked the package as a whole, and a leak is
invisible by nature: a token in `localStorage` changes no behaviour until something reads it. **The product is a
test.** Phase 0 measured one storage writer in the whole service, no `removeItem`, no `clear`, and no `console`
call, so the expected finding is "no leak" (T22-D7).

## 2 · Disposition of the Phase 3 findings

| Finding | Disposition |
|---|---|
| **F1** the `code` in the URL has no recorded handoff | **Accepted, both halves.** The sweep's URL claim is narrowed to exactly `agents.md:79`'s wording — *the SPA places no token in a URL* — and the `code` becomes a recorded handoff to task 24 rather than a silent exclusion. **T22-D3 amended, T22-D9 added.** |
| **F2** T22-D1 understates the dependency limitation | **Accepted.** The limitation is now three sentences naming what each instrument does and does not cover. **T22-D1 amended.** |
| **F3** `history.state` and `window.name` are unnamed | **Accepted, in scope.** Both are same-origin surfaces that outlive a navigation, both exist in jsdom, and both cost one assertion. **T22-D8.** |
| **F4** `sendBeacon`, `new Image` and friends | **Accepted.** `agents.md:82` forbids sending a token to analytics and Q9 records that there is no analytics at launch — so the scan closes the shape rather than policing a dependency. **T22-D10.** |
| **F5** AC9 omits `removeItem` and `clear` | **Accepted**, with the allowlist pattern T01 already uses: the scan names the files permitted to call each storage method, and **task 23 adds itself to that list** when it builds deletion. **T22-D11.** |
| **F6** the `Bearer ` prefix | **Accepted as a case; its mechanism is wrong.** The sweep searches each surface's dumped text for the token **as a substring** (T22-D5), and `Bearer ACCESS-TOKEN-X` contains `ACCESS-TOKEN-X`, so nothing is missed and no extra search string is needed. What was genuinely absent is the *proof* of that, so a self-proving row writes `Bearer <token>` to storage and asserts the same sweep catches it. **No new search term; one new case.** |

## 3 · Frozen decisions

| ID | Decision |
|---|---|
| **T22-D1** | **The sweep does not build the bundle**, and its limits are stated rather than implied: *the source scan covers this repository's own production code under `frontend/src`; the runtime sweep covers the sign-in → callback → exchange flow it drives, including its failure paths; a leak introduced at runtime by a dependency — an import side effect, an unexercised error path, third-party telemetry inside minified code — is covered by **neither**, and remains `bundleContainsNoSecretEnvKeys`' and dependency review's concern.* Written into the test file's header, in those terms. |
| **T22-D2** | **"A token"** = the access token; the ID token, whole, each of its three segments, and any decoded claim value; and any `refresh_token` should one ever appear (L3 records that none is issued to this client). **Not** `state`, `nonce` or `code_verifier` — L16 permits those in sessionStorage, so forbidding them would contradict the spec. A named constant in the file, not prose. |
| **T22-D3** | *(amended per F1)* **The sweep's URL claim is exactly `agents.md:79`'s**: the SPA places no token in a URL. The authorization `code` SAS put in the callback URL is **not** swept and **not** claimed clean — see T22-D9. |
| **T22-D4** | **"A log" includes a thrown `Error`'s message**, because an uncaught rejection carries one to a console. |
| **T22-D5** | **The sweep reads values, not keys.** Every surface is dumped whole and searched for token values and segments as substrings. A key-list check would miss a token written under an innocuous name, which is the shape a real leak takes. |
| **T22-D6** | **Every instrument proves itself.** Each scan catches a synthetic violation, and a scan over empty input **fails** rather than passes. From `bundleContainsNoSecretEnvKeys`' own header: a scanner never shown to catch a leak has verified nothing. |
| **T22-D7** | **If the sweep finds no leak, nothing in production changes.** A verification task that ends with no production diff has succeeded. |
| **T22-D8** | *(new, F3)* **`history.state` and `window.name` are in scope**, in both the runtime sweep and the source scan. The `window.name` scan must match `window.name` / `globalThis.name` and **not** `this.name =`, which three `Error` subclasses use legitimately (`crossTabRenewal.ts:31,38`, `sessionBoundary.ts:36`). |
| **T22-D9** | *(new, F1)* **Recorded handoff to task 24**, which owns the post-login destination: the callback URL still carries `code` and `state` when the visitor leaves the callback screen, and task 24 should remove the callback query (`code`, `state`, `error`, `error_description`) before the post-login screen renders. T22 asserts the **tokens** are absent from the URL and says in the file that the `code` is out of its scope and whose it is. |
| **T22-D10** | *(new, F4)* **The source scan also forbids the exfiltration shapes**: `navigator.sendBeacon`, `new Image(`, `new Audio(`, `.src =` on a constructed element, and any absolute `http(s)://` address in production source (which T01 already forbids package-wide). Each row self-proving. |
| **T22-D11** | *(new, F5)* **The source scan allowlists storage methods by file**: `setItem` only in `auth/authorizeRequest.ts`; `removeItem` and `clear` **nowhere**, today. The list is a named table with a comment requiring the next task that needs an entry to add itself and say why — **task 23 is the expected next entrant**, when it builds single-use deletion. |

## 4 · Scope

**In:** one new test file; the real flow driven end to end; sweeps of every surface in the task statement plus
T22-D8's two; sweeps over every failure path; a package-wide source scan; self-proving instruments; the minimal
production fix if a leak is found.

**Out:** building the bundle (T22-D1); deleting the L16 items — **task 23**; the full L16 inventory and R29's TTL
— **task 26**; recovery codes — **task 69**; receipts and the Cache Storage runtime — **task 116** and R57's own
built test; the `code` in the URL — **T22-D9, task 24**; wiring the composition root and silent
re-authentication, still unassigned package-wide and carried from T21.

## 5 · Files

**Create:** `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.ts`.

**Modify:** none expected (T22-D7).

**NOT to modify:** `spec/**`; `api/sessionBoundary.ts`, `api/crossTabRenewal.ts`, `api/client.ts` — tasks 5 and 6,
whose transient in-flight copies are *required* by R21 and not leaks; `auth/accessToken.ts`,
`auth/CallbackScreen.tsx` — T21's, and the subject of the measurement; `auth/authorizeRequest.ts` — task 15's
three writes are L16-sanctioned and the deletion L16 also calls for is task 23's; `pwa/*` — R57's;
`app/bundleContainsNoSecretEnvKeys.test.ts` — T01's instrument, referenced not extended.

## 6 · Acceptance criteria

**AC0** the T22-D2 token definition is a named constant in the file · **AC1** after a successful flow, neither web
store contains any token value or segment, proven by dumping every key **and value** · **AC2** sessionStorage
holds **exactly** the three L16 items, each equal to what the authorize step generated · **AC3** `document.cookie`
gains nothing · **AC4** no IndexedDB use — **structurally**, see §7 · **AC5** no token in `location.href`,
`search`, `hash`, `document.title`, the rendered DOM, **`history.state` or `window.name`** (T22-D8) · **AC6** no
token in any of the four console channels or a thrown `Error`'s message (T22-D4) · **AC7** exactly three
`setItem` calls in a successful flow, all from the authorize step, none carrying a token; no `removeItem`, no
`clear` · **AC8** every failure path swept: no `state`, mismatched `state`, no `code`, an authorization error, a
refused code, a 5xx, a dropped connection, an unusable response, a bad nonce · **AC9** the package-wide source
scan: no `localStorage`, `indexedDB`, `document.cookie`, `console.*`, no `window.name` write, none of T22-D10's
exfiltration shapes, and storage methods only in the T22-D11 allowlist — comments stripped, stripped body
asserted non-empty · **AC10** every instrument proves itself and refuses empty input (T22-D6) · **AC11** the sweep
survives a **throwing** store and reports honestly rather than passing vacuously · **AC12** a token adopted from a
sibling tab through the real `shareRenewalAcrossTabs` is in no storage surface afterwards · **AC13** no new
string, catalogue key, dependency or production change · **AC14** the suite passes with the one known
environmental failure and no other, **by exit code** · **AC15 — new (F6).** A synthetic `Bearer <token>` written
to storage is caught by the same sweep, proving the substring search covers a header-shaped leak.

## 7 · What this task cannot prove, and says so

**jsdom implements neither `indexedDB` nor `caches`.** An assertion that "no database was opened" against an API
that does not exist is vacuous by construction. AC4 and the Cache Storage surface are therefore proven
**structurally** — production source names neither API — and the test file states that plainly instead of implying
a runtime proof it did not perform. The same is true of a real cookie jar beyond `document.cookie`.

A real-browser end-to-end check would close this. **No task in `tasks.md` 1-39 is visibly that task**, which is
recorded here for the author alongside the two already carried from T21: nothing wires the composition root, and
silent re-authentication has no task.

## 8 · Constraints

Spy on `Storage.prototype`, never replace the store. Never assert against the constant under test. **Vacuity is
the largest risk**: a sweep over a flow that never ran passes everything, so every block carries a control
proving the flow happened — a token actually held, three items actually written. If the harness imports the route
graph dynamically with `vi.resetModules()`, a statically imported module is **a different instance** from the one
the screens hold (measured in T20 and T21): import after the routes and read that instance, with a control that
fails when it is wrong. Strip comments before scanning, then assert the body survived — `accessToken.ts:12` names
`sessionStorage` in order to forbid it. Touching `sessionStorage` can **throw**, not return `null`. No new
dependency (L13). Node built-ins and existing devDependencies only.

## 9 · No blockers

Every scoping question is frozen above.

---

## Next — Phase 5 — implementation plan.
