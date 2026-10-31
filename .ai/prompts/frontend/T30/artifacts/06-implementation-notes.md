# frontend · T30 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

Six files, **37 cases** (11 controls), nine `describe` blocks.

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The new file alone | **37 / 37**, exit 0 |
| Whole suite | **1435 passed / 1 failed (1436)** — the one is `lintRejectsHandWrittenBackendFetch`, pre-existing and environmental |
| `generatedClientCompiles` | **10 / 10**, unchanged (AC2) |

| File | Lines | Change |
|---|---|---|
| `contracts/api/oidc.yaml` | +89 | `GET /userinfo` + `UserInfoResponse` |
| `libs/ts/api-client/src/generated/oidc.ts` | regenerated | never hand-edited |
| `frontend/src/api/client.ts` | +31 | `OIDC_PUBLIC_OPERATIONS`, `OidcApi`, `createOidcApi` |
| `frontend/src/profile/readProfile.ts` | 98 | new |
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | 979 | new |
| `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | ±1 | an inherited pin, below |

---

## What the writing found that the plan did not

### 1 · My AC3 assertion was wrong about the library, and the honest claim is about shape

The plan's AC3 case asserted `readProfile.ts` does not contain the string `/userinfo`. **It does, and it
must**: `openapi-fetch` takes the schema path as its first argument, and that is exactly what makes the call
typed and checkable against the contract. The assertion was a guess about the library that did not survive
being run.

The claim worth making is the **shape**: the path occurs exactly once, as a quoted literal handed to the
generated client's `GET`, and is never assembled — no concatenation onto it, no template literal, no `new
URL(`. That is the difference between *naming an operation* and *building a request*, and it is the thing L6
actually cares about.

### 2 · The contract assertion failed on the contract's own prose

`expect(userinfo).not.toContain('security: []')` failed — because the `/userinfo` **description explains why
it is not `security: []`** and therefore contains those characters. A substring search cannot tell a
declaration from a sentence about one.

Rewritten line-wise: `text.split('\n').some((line) => line.trim() === 'security: []')`. This is the same
class of error as scanning unstripped source for a forbidden word and finding the comment that explains the
prohibition — and it is worth recording because the file's documentation is deliberately dense, so prose
colliding with assertions is a standing hazard here rather than a one-off.

### 3 · The relative-URL problem, and the idiom that already existed

The first run failed with `TypeError: Failed to parse URL from /userinfo`. Node's `Request` rejects a relative
URL; a browser resolves it. `api/parallel401sTriggerExactlyOneRenewal.test.ts:30` already solves this with an
`installBrowserRequest()` helper — a `Request` subclass resolving against `window.location.href`. Reused
rather than reinvented, and the harness now receives a `Request` object as the real transport does, which also
made the bearer and the `redirect` mode directly observable instead of reconstructed from an init bag.

### 4 · The AC5 claim scan had to be scoped, or it would have been deleted within a month

Plan hazard M2 was about `split`. The sharper version emerged while writing: **the forbidden *claim* names
cannot be scanned tree-wide at all.** `email`, `name` and `sub` are ordinary words this package uses
constantly — `auth.yaml` has an `email` property, `AccountResponse` carries `email` and `emailVerified`, the
catalogue has copy about email verification. A tree-wide scan for them fails everywhere and proves nothing.

So the two instruments compose: the **decode** scan bounds the set of files that touch a token's payload — to
exactly one, asserted — and the **claim** scan runs only over that set. R20 does not forbid naming a claim; it
forbids reading one off a token, and the instrument now says that rather than approximating it.

### 5 · An inherited pin fired, and raising it was the right move

`tokensAreNeverWrittenToAnyStorage.test.tsx:968` asserted exactly **three** `this.name =` occurrences in
production source — the three Error subclasses that name themselves — because a looser `/\.name\s*=/` pattern
would have condemned all three alongside a real `window.name` write. `ReadProfileError` is a fourth.

The count was **raised to four, not loosened**, and the comment says why: this is the pin working as designed.
A new `this.name =` should be somebody's deliberate decision. Precedent: T24 updated 17 inherited assertions
across five files for the same reason.

This is the one existing test file T30 touched, and the Phase 4 brief's "Files NOT to Modify" named existing
test files — so it is declared as **T30-D5** rather than passed over.

### 6 · `redirect: 'manual'` is assertable structurally, and the behaviour needs a stub

Plan hazard M3 held exactly. jsdom models neither redirect modes nor a 3xx `Response` (its constructor rejects
one), so AC12 is proven in two halves: the **option reaching the request** is read off the recorded `Request`,
and the **3xx and followed-200 shapes** are presented by the transport stub with `status` and `redirected`
redefined. The case says so in as many words, because a test that implied jsdom had followed a real redirect
would be claiming something it did not do.

## Decisions worth recording

- **A real `SessionBoundary`, not a stub.** AC4 claims a 401 is handled by the boundary's single-flight
  renewal and not by the reader; a stubbed boundary would make that true by construction. The harness builds
  the real one and counts renewals, so "the boundary renewed once and re-sent with the new token" is measured.
- **`createOidcApi` mirrors `createAuthApi` down to the middleware order**, rather than the two being
  generalised into one factory. Two factories differing only in contract and public-operation set can be
  compared at a glance; two differing in registration order cannot, and that order is load-bearing —
  openapi-fetch runs `onResponse` in reverse registration order.
- **`readProfile` takes `api` and `tokens` as parameters.** The boundary exposes only `renew()` and
  `middleware()`, not `current()`, so the short-circuit needs the token view; taking it as a parameter avoids
  this module owning the token's lifetime and leaves the unwired composition root exactly as unwired (T30-D4).
- **`ReadProfileError` cannot carry detail**, by construction: three fixed messages in a lookup, nothing
  interpolated. A class that *could* hold a response's text would need a rule nobody enforces.
- **Three failures, not one or five.** `no-session`, `unauthorized`, `unavailable`. Everything else collapses
  into `unavailable` on purpose (L4): the visitor learns it did not work, and nothing about why.

## What this does not do

Carried from the frozen brief: no screen consumes the profile (**T30-D3**, `/app/account` is unbuilt); the
end-to-end profile read cannot be demonstrated by anyone until `spec/auth-service/design.md` **O6** closes
(**T30-D1**); `GET /accounts/me` is **T31's** and this file is structured for it (**Kimi F6** — T30 owns the
imports, setup, helpers and `describe('T30: …')` blocks; T31 adds `describe('T31: …')` only).

---

**Phase 6 complete.** Ready for Phase 7 — self-review and mutation score.
