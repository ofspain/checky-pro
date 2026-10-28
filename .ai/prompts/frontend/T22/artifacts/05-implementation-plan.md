# frontend · T22 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

Task statement: *"No token is written to local storage, session storage, IndexedDB, a cookie, a URL, or a log.
Verifies `tokensAreNeverWrittenToAnyStorage`. Cites R7, L3."*

---

## The shape of this task, and what Phase 6 can honestly do

T22's deliverable is a test, and the pipeline puts tests in Phase 10. T01 — the other pure verification task in
this package — resolved the same tension by writing its production work in Phase 6 and deferring its test file to
Phase 10. T22 has **no production work unless the sweep finds a leak** (T22-D7), which inverts the order: the
measurement has to come first, because it is what decides whether Phase 6 changes anything at all.

So:

| Phase | Work |
|---|---|
| **6** | **Measure.** Run the sweep as throwaway probes — not committed files — over the real flow and the real source tree. Report what each surface actually holds. Make the minimal production fix **if and only if** a probe finds a real leak. |
| **7-9** | Review the measurement and whatever it forced, with the usual gates. |
| **10** | Write the committed `tokensAreNeverWrittenToAnyStorage.test.ts`, then mutate it. |

Phase 6 writing throwaway probes is the established house move: T20 used one to pin the serialized request body
byte-for-byte before committing a parsed assertion, and T21 used one to prove three `signInNotice` assertions were
inert. The difference here is that the probe's *result* is the phase's product.

## Order of work

### 1 · Probe A — the write surface, under the real flow

Drive `buildAuthorizeRequest()` → `CallbackScreen` at `/app/callback?code=…&state=…` → the real
`exchangeAuthorizationCode` against a stubbed `fetch` → `accessToken.current()`, exactly as T21's harness does,
and then dump:

- every key and value of `sessionStorage` and `localStorage`
- `document.cookie`
- `location.href`, `location.search`, `location.hash`
- `document.title`, and `container.innerHTML`
- `history.state` and `window.name` (T22-D8)
- the four `console` channels, spied
- `Storage.prototype.setItem` / `removeItem` / `clear`, spied, with their arguments

**What to report:** the exact contents of each surface, and the call count and arguments of each spy. The control
that makes the dump meaningful is `accessToken.current()` returning the token — a flow that did not reach the
holder proves nothing about where the token went.

### 2 · Probe B — the same, over every failure path

The nine from the frozen AC8, reusing T21's table: no `state`, mismatched `state`, no `code`, an authorization
error, a refused code, a 5xx, a dropped connection, an unusable response, a bad nonce. **What to report:** any
surface that differs from Probe A's, and in particular whether a failure writes anything at all.

### 3 · Probe C — the package-wide source scan

Read every `.ts`/`.tsx` under `frontend/src` except `*.test.ts*`, strip comments, and report each hit for:

| Pattern | Expected |
|---|---|
| `localStorage` | none |
| `indexedDB`, `openDatabase` | none |
| `document.cookie` | none |
| `console.` | none |
| `sessionStorage` | reads in 4 files; `setItem` only in `auth/authorizeRequest.ts` |
| `removeItem`, `.clear()` | none (T22-D11) |
| `window.name` / `globalThis.name` assignment | none — and **not** matching `this.name =`, which three `Error` subclasses use (`crossTabRenewal.ts:31,38`, `sessionBoundary.ts:36`) |
| `sendBeacon`, `new Image(`, `new Audio(`, `.src =` | none (T22-D10) |
| `https?://` | none in our own source (T01 already forbids it) |

**What to report:** the per-pattern hit list, file and line, so Phase 10's allowlist is built from measurement
rather than from memory. Also report the stripped-body byte count per file, since a strip that empties a file
would make its scan vacuous (T22-D6).

### 4 · Probe D — the two surfaces that cannot be measured here

Confirm and record, rather than assert: `typeof indexedDB`, `typeof caches` in jsdom. If either is `undefined`,
AC4 and the Cache Storage surface are structural-only, exactly as §7 of the frozen brief says, and Phase 10 must
say so in the file instead of writing an assertion that passes because the API is missing.

### 5 · Probe E — the cross-tab path (AC12)

Through the real `shareRenewalAcrossTabs` with an injected channel and lock manager — the T21 lesson: with no
options it takes the degraded branch because jsdom has no `navigator.locks`, and the production listener never
runs. Adopt a token through the real listener, then dump every surface again.

### 6 · Production change, if any

Only if a probe finds a leak. The expected diff is **empty** (T22-D7). If one appears, the fix goes in the file
that leaks and nowhere else, and the brief's *NOT to modify* list still binds — a leak inside
`sessionBoundary.ts`'s transient copies is not a leak (R21 requires them), and a missing deletion of the three
L16 items is task 23's, not a leak at all.

## Then Phase 10 builds the file

Planned structure, so Phase 7-9 can review the shape before it is written:

```
tokensAreNeverWrittenToAnyStorage.test.ts
├── header: what this proves, and T22-D1's three sentences on what it does NOT
├── TOKENS: the T22-D2 definition as a named constant (AC0)
├── surfaceDump(): every surface, as one searchable text, + storageEntries()
├── scanSource(): comment-stripped scan with the T22-D11 file allowlist
├── describe('a successful flow leaves nothing behind')      AC1-AC3, AC5, AC7
├── describe('every failure path leaves nothing behind')      AC8  (9-row table)
├── describe('the instruments catch what they are looking for') AC10, AC15
│     — synthetic violations through the SAME functions, empty input refused
├── describe('the package itself names no leak')              AC9, T22-D10, D11
├── describe('what this cannot prove here')                   AC4 + Cache, structural
└── describe('a sibling tab's token is no different')         AC12
```

**Two risks to design against, both measured in this package:**

1. **Vacuity.** Every block carries a control proving the flow ran — a token actually held, three items actually
   written. A sweep over a flow that never happened passes everything.
2. **The module instance.** If the harness imports the route graph dynamically with `vi.resetModules()` between
   cases, a statically imported module is a different instance from the one the screens hold. Import after the
   routes, read that instance, and keep a control that fails when it is wrong.

## What this plan does not do

No production file is planned for change. No new dependency, no catalogue key, no contract. The bundle is not
built (T22-D1). Nothing about tasks 23, 24, 26, 69 or 116 is touched beyond the recorded handoffs.

---

## Next — Phase 6 — the measurement.
