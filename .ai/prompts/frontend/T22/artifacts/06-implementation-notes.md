# frontend · T22 · Phase 6 — Implementation Notes (the measurement)

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

Date: 2026-10-28. Five throwaway probes, as planned. **No production file changed** (T22-D7), and the probes were
not committed — they were run, read, and deleted; the kept copy lives in the session scratchpad.

---

## What changed

| File | Change |
|---|---|
| — | **Nothing.** No leak was found, so there is nothing to fix. |

## 1 · The headline: no leak, anywhere

Driven through the **real** `buildAuthorizeRequest` → `CallbackScreen` → `exchangeAuthorizationCode` →
`accessToken`, with only `fetch` stubbed:

| Surface | Success flow | All nine failure paths | Sibling-tab adoption |
|---|---|---|---|
| `sessionStorage` | the three L16 items, and nothing else | the three L16 items | empty |
| `localStorage` | empty | empty | empty |
| `document.cookie` | `""` | `""` | `""` |
| `document.title` | `""` | `""` | `""` |
| rendered DOM | no token, no ID token | no token | n/a |
| `history.state` | `null` | `null` | `null` |
| `window.name` | `""` | `""` | `""` |
| console (6 channels) | 0 calls | 0 calls | 0 calls |
| `removeItem` / `clear` | 0 calls | 0 calls | 0 calls |

Searched as substrings (T22-D5): the access token, the ID token whole, **and each of its three segments
separately**. Every one absent from every surface. The control that makes this mean something:
`accessToken.current()` returned the token in the success case and in the sibling-tab case, so the flow really
reached the holder.

## 2 · The source scan, measured — this is Phase 10's allowlist

**23 production files** under `frontend/src` (excluding `*.test.ts*`), comments stripped:

| Pattern | Hits |
|---|---|
| `localStorage` | **0** |
| `indexedDB` / `openDatabase` | **0** |
| `document.cookie` | **0** |
| `console.` | **0** |
| `removeItem` | **0** |
| `.clear()` | **0** |
| `(window\|globalThis).name =` | **0** |
| `sendBeacon` | **0** |
| `new Image(` / `new Audio(` | **0** |
| `.src =` | **0** |
| `https?://` | **0** |
| `caches.` | **0** |
| `sessionStorage` | **4**, all reads: `auth/CallbackScreen.tsx`, `auth/authorizeRequest.ts`, `auth/codeExchange.ts`, `auth/idTokenNonce.ts` |
| `setItem` | **3**, all in `auth/authorizeRequest.ts`, one per L16 item |

**T22-D8's discrimination works.** The `window.name` pattern finds 0 while `this.name =` finds 3 —
`crossTabRenewal.ts:28,35` and `sessionBoundary.ts:26`, all `Error` subclasses naming themselves. A scan written
as `/\.name\s*=/` would have failed on three legitimate lines.

Stripped-body sizes were recorded for all 23 files; the smallest is `auth/signInNotice.ts` at 287 bytes from 1173
raw, and `auth/accessToken.ts` strips from 5321 to **572** — that file is 89% comment. No file strips to empty, so
no file's scan is vacuous (T22-D6).

## 3 · Three vacuity traps the probe found in itself — the real product of this phase

A sweep that reports "clean" is worth exactly as much as its ability to report "dirty". Writing the probe the
obvious way produced three assertions that would have passed against a leak.

### Trap 1 · The `setItem` spy was installed too late

The probe spied `Storage.prototype.setItem` **after** calling `buildAuthorizeRequest()`, and reported
`setItem calls: []` — zero, for a flow that demonstrably wrote three items (they are in the dump two lines
above). AC7 says *exactly three `setItem` calls, all from the authorize step*. Written in this order, AC7 would
assert `0` and pass, and it would keep passing if a fourth call appeared.

**For Phase 10:** the spy must predate the authorize step, and AC7's case must assert **three** with their
arguments, not an absence.

### Trap 2 · `location.href` never moves, so the URL sweep is vacuous

Every dump reports `href: http://localhost:3000/` — the document's own URL, untouched. `createMemoryRouter` keeps
its location in router state and **never writes `window.location` or `history`**. So an assertion that
`location.href` contains no token passes against a document that never navigated at all: not even the
authorization `code` is there, although the screen was driven to `/app/callback?code=…&state=…`.

This is the sharpest finding of the phase. "No token in a URL" is AC5's half that comes straight from
`agents.md:79`, and the obvious instrument for it is inert under this harness.

**For Phase 10:** the URL surface is `router.state.location` — `pathname`, `search`, `hash` — which is where the
SPA's own navigation actually lands, plus `history.state`. The case needs a control proving the router's location
*did* carry the callback query, so that "no token there" is a measurement rather than an empty box.

### Trap 3 · `title`, `history.state` and `window.name` are empty boxes

All three come back `""`, `null` and `""` — not because the SPA cleaned them, but because jsdom starts them empty
and nothing in the package writes any of them. An absence assertion against a surface nothing ever touches is
true by construction.

**For Phase 10:** each of the three needs a self-proof — a sentinel value written into the surface and found by
the same sweep function — so the case proves the *instrument reads that surface*, and the clean result is then
meaningful. This is T22-D6 applied per surface rather than once per scan.

## 4 · Confirmed: two surfaces cannot be proven here

```
indexedDB = undefined   caches = undefined   cookieStore = undefined
BroadcastChannel = function   navigator.locks = undefined
```

Exactly as §7 of the frozen brief states. AC4 and the Cache Storage surface are **structural only**: the source
names neither API, and no runtime assertion is possible. Phase 10 must say this in the file rather than write
`expect(indexedDB.databases()).toEqual([])`-shaped code that passes because the API is absent.

`navigator.locks = undefined` also re-confirms the T21 lesson: `shareRenewalAcrossTabs` with no options takes the
degraded branch. Probe E injected a channel and a lock manager, registered **1** listener, and adopted a token
through the production path.

## 5 · Acceptance criteria evidence from this phase

| AC | Status after the measurement |
|---|---|
| AC1, AC2, AC3, AC5 (storage/cookie/DOM half), AC6, AC7, AC8, AC12 | **measured clean**, pending Phase 10's committed assertions and the three trap fixes |
| AC4, Cache Storage | **structural only**, confirmed by §4 |
| AC9 | **measured**, hit lists in §2 — Phase 10's allowlist is these numbers |
| AC5 (URL half) | **not yet provable** as written; Trap 2 settles how |
| AC0, AC10, AC11, AC13, AC14, AC15 | Phase 10 |

## 6 · Open questions for the gate

**None blocking.** One judgement to record: the three traps mean Phase 10's file is doing more work than "write
down what Phase 6 measured". The sweep has to prove it can *see* each surface before its clean result counts, and
that doubles the case count for AC5 and AC7. That is the right cost — a sweep nobody can falsify is not evidence.

---

## Next — Phase 7 — self-review.
