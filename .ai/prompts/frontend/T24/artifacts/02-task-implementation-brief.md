# frontend · T24 · Phase 2 — Task Implementation Brief

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

**Two owner decisions were taken before this brief was written** (Phase 1's OQ-1 and OQ-2), and they set its shape:
**T24 builds the whole mechanism** — capture, storage, allowlist, navigation — so T41 is later left a one-line
call; and **the allowlist is derived from the route table and matched as exact paths**, not as patterns.

---

## Task

Honour an allowlisted post-login return target at the end of a successful callback, and fall back to the home
route for anything else. Build the mechanism the spec assigned to no task: a capture helper, L16's named stored
item, one allowlist check, and the navigation `CallbackScreen` currently does not perform.

## Purpose

After a successful sign-in the visitor is left standing on `/app/callback`. L1 says the SPA honours an
**allowlisted** post-login return target and otherwise falls back to the **home route**; `agents.md:22` adds the
clause that makes it a security control rather than a convenience — **"never to an arbitrary URL"**. An open
redirect at the end of an OIDC flow is the classic way a phishing chain ends: the victim really did sign in, so the
page they land on inherits all the trust of a completed login.

## Scope

**In.** A new module owning the return target: the storage key, a capture function (`rememberReturnTarget`), and a
single read-validate-delete function (`takeReturnTarget`) that returns a destination that is always safe to
navigate to. The allowlist, derived from `routes.tsx`. One navigation added to `CallbackScreen`'s successful path.
The named test. The inherited assertions in T22 and T23 that a fifth storage writer and a fourth stored item make
false.

**Out.** The **producer**: nothing calls `rememberReturnTarget` in production yet — R28/T41's redirect is its
caller and is a later task (**T24-D2**). Query strings and fragments in a target (**T24-D6**). The two
parameterised authenticated routes (**T24-D5**, and read its consequence — it is product-visible). Sign-out's
`post_logout_redirect_uri`, which is server-allowlisted and task 36's. Silent re-authentication. A screen at
`/app/home`.

## Business Rules

- **R7** — the callback validates `nonce`, exchanges the code, holds the token in memory. T24's navigation is the
  last step of that path and changes none of it.
- **R8** — a missing or mismatched `state` discards to the sign-in start; **no discard may honour a target**.
- **R28** — while unauthenticated, only the public routes are served and everything else redirects to sign-in.
  Not built here; it is the future caller of the capture.
- **R29** — only non-sensitive values in sessionStorage. A bare route path is not PII; a query string could be.
- **R30** — an identity-screen outcome does not vary its copy or treatment by cause.

## Locked Decisions

- **L1** — allowlisted post-login return target, falling back to the home route. **The primary authority.**
- **`agents.md:21-22`** — the same rule, plus "never to an arbitrary URL".
- **L16** — sessionStorage may hold `state`, `nonce`, `code_verifier` **and the post-login return target**,
  single-use and deleted at callback. The fourth item comes into existence in this task.
- **L14** — all routes under `/app`, paths listed verbatim in `design.md` §4c; a new public route needs an ADR.
- **L4, L30** — neutral, cause-independent outcomes. **L11** — catalogue-only strings. **L3** — tokens in memory
  only. **L12** — additive to the Phase 1 shell.

## Dependencies

`app/routes.tsx` — `APP_BASENAME`, `AUTHENTICATED_ROUTE_PATHS`, `PUBLIC_ROUTE_PATHS`, `SIGN_IN_PATH`,
`CALLBACK_PATH`. `auth/CallbackScreen.tsx` — the successful path, and `useNavigate`. `auth/authorizeRequest.ts` —
`AUTHORIZE_STORAGE_KEYS` and `consumeAuthorizeState()` (T23), for the boundary between the two deletions.
`auth/signInNotice.ts` — the existing single-read `set`/`take` pattern this module follows. No new dependency, no
config key, no contract, no catalogue key.

## Inputs

A candidate return target: a string a caller hands `rememberReturnTarget`, and later the raw string read back out
of storage. **Both are untrusted.** Same-origin code wrote it, but a stale value, a hand-edited store, or a future
caller passing a full URL are all reachable, so the read is the trust boundary.

## Outputs

`takeReturnTarget()` returns a **router-relative path that is always allowlisted** — the stored target when it is
legal, and the home path otherwise. It never returns `null`, so no caller has to decide what to do with a rejected
target: there is one destination type, and therefore no second place to get the fallback wrong.

## State Changes

One new `sessionStorage` key, written by `rememberReturnTarget` and removed by `takeReturnTarget` (read and delete
in one call, so it cannot be deleted before it is read). It is **L16's fourth item**, single-use and deleted at
callback exactly as L16 says. No other state changes; the token holder, the three PKCE items and all in-memory
state are untouched.

## Files to Create

- `frontend/src/auth/returnTarget.ts` — the key, `rememberReturnTarget`, `takeReturnTarget`, the derived
  allowlist, the one validator.
- `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — the named test. **Shared with T25**;
  this brief partitions the cases (see Required Tests).

## Files to Modify

- `frontend/src/auth/CallbackScreen.tsx` — navigate to `takeReturnTarget()` after `consumeAuthorizeState()`, with
  `{ replace: true }`, so the callback URL is not reachable with the back button.
- `frontend/src/app/routes.tsx` — export `HOME_PATH = 'home'` and use it in `AUTHENTICATED_ROUTE_PATHS`, the same
  pattern `SIGN_IN_PATH` and `CALLBACK_PATH` already follow, so the home route has one spelling.
- `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` — a `STORAGE_ALLOWLIST` row for
  `auth/returnTarget.ts` (`setItem`, `removeItem`) with its reason, and the "touches session storage in only the
  four files that read it" case becomes five.
- `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` — the key-set pin stays at three keys and
  **its message is rewritten** to record what T24 actually decided and why (T24-D4).

## Files NOT to Modify

`spec/**`; `auth/codeExchange.ts` and `auth/idTokenNonce.ts` (pure readers — T23-D1, three tests assert it);
`auth/accessToken.ts`; `auth/authorizeRequest.ts` (its three keys and `consumeAuthorizeState` stay as T23 left
them — **T24-D4**); `api/*`; `pwa/*`; `.oxlintrc.json` (T22-D12).

## Acceptance Criteria

**AC1** an allowlisted target is navigated to, the control being that the visitor would otherwise have stayed on
`/app/callback` · **AC2** a target outside the allowlist lands on the **home route** · **AC3** an absent target
lands on the home route by the same path as a rejected one · **AC4** no discard honours a target, over all five
failure shapes · **AC5** "never an arbitrary URL": an absolute URL, a protocol-relative `//host`, `javascript:`, a
backslash path, a traversal, an encoded traversal, and a prefix match (`/app/homeward`) each fall back · **AC6**
the allowlist is **derived** from the route table — adding a path there makes it legal with no second edit, and no
second list exists · **AC7** the target is single-use and deleted at callback, and the deletion cannot precede the
read · **AC8** nothing else enters storage; no token, no PII; a target carrying a query or fragment is not
honoured (T24-D6) · **AC9** the fallback is indistinguishable from an honoured target in copy and treatment — no
"your destination was rejected" anywhere · **AC10** a storage read that **throws** is treated as no target and
never reaches the visitor as an error · **AC11** no new catalogue string, no new dependency, no change to the two
pure readers or the holder · **AC12** every inherited assertion updated with its reason restated · **AC13** suite
green **by exit code** with the one known environmental failure; `tsc --noEmit` exit 0.

## Required Tests

Named test `returnTargetOutsideAllowlistFallsBackToHome` (`package.md:158` → R7), **partitioned** so T25 inherits
work rather than a finished file:

- **T24's half (this task).** The mechanism and the honoured path: an allowlisted target is navigated to; an absent
  target goes home; the target is deleted after use and readable before; a throwing store is treated as no target;
  the allowlist is derived from the route table; `rememberReturnTarget` round-trips through `takeReturnTarget`; no
  discard honours a target (five shapes); the store is left with nothing extra.
- **T25's half (next task).** The rejection families, each with its own row: absolute, protocol-relative,
  `javascript:`, backslash, traversal, encoded traversal, prefix match, a public route, a nonexistent route, a
  parameterised route (T24-D5's limit), and a target carrying a query or fragment (T24-D6).

T24 writes the file, the harness and its own cases, and leaves the rejection table as a named, empty-but-stated
section — it does **not** pre-empt T25's rows.

**Controls, because the house risk here is a test that passes against no navigation at all:** every "landed at X"
case asserts the destination **and** that the other destination was reachable from the same harness; the honoured
case proves the visitor would otherwise have been left on `/app/callback`; and the derivation case proves a path
added to the table becomes legal, not merely that today's list matches today's list.

## Constraints

**Security is the whole task.** The read is the trust boundary and the validator is called there, once —
validating on write as well would be two code paths for one decision and the write is not where the risk is. Set
membership, not prefix matching and not pattern matching: a prefix check is how `/app/homeward` and
`/app/home.evil.com` get honoured. **Null handling:** `takeReturnTarget()` never returns null or empty; its
contract is a destination, not an option. **Storage:** every touch guarded, because touching `sessionStorage`
*throws* in a context with site data blocked and there is no error boundary — an uncaught throw would blank the
page at the end of a sign-in. **Module boundaries:** the new module owns its key and its deletion; the two pure
readers stay pure; `consumeAuthorizeState` keeps owning exactly the three PKCE items. **No transactions, no
threads:** one tab, one effect, and the existing `handled` ref already makes the callback single-invocation.
**Performance:** irrelevant — one string comparison against a set of ten.

## Open Questions

**No blockers.** Phase 1's OQ-1 and OQ-2 were decided by the owner. Three consequences are recorded as decisions
for the Phase 4 gate rather than questions:

- **T24-D5, and the owner should read this one.** Exact-path matching excludes `invoices/:id` and `receipts/:id`.
  Those are exactly the two routes **notification emails link to** (`design.md` §4c, note of 2026-10-19:
  `invoice.created` → `/app/invoices/:id`, `receipt.issued` → `/app/receipts/:id`). So an unauthenticated visitor
  who clicks an invoice email, signs in, and would have been returned to that invoice **will land on home
  instead**. The price of honouring them is pattern matching for two routes plus the traversal and
  encoded-separator cases that come with it. Recorded as a **declared limit and a handoff**, not an oversight.
- **T24-D6.** A target carrying a query or fragment is not honoured. The only planned caller stores a pathname, so
  nothing needs it today; honouring one means deciding what may appear in a query, which is a decision with no
  current consumer.
- **T24-D4.** The return target is deleted by the module that owns its reader, not by `consumeAuthorizeState`.
  Both are "single-use and deleted at callback" per L16, but the three PKCE items are deleted because the
  **exchange** consumed them and the target because the **navigation** read it — different consumers, different
  moments. T23's key-set pin therefore stays at three, and its message is rewritten to say this rather than to ask.

## Next — Phase 3 — design challenge (Kimi 2.7).
