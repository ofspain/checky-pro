# frontend · T24 · Phase 12 — Specification Verification

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Sonnet |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

> **Return target inside allowlist.** A return target on the allowlist is honoured. Verifies
> `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1. — `spec/frontend/tasks.md:36`

**Verdict up front: PASS**, with **five declared deviations**, the first of which is the spec's own task split
rather than a shortfall.

**Measured state.** `tsc --noEmit` exit 0. Suite **1075 passed / 15 todo / 1 failed** by exit code over two
consecutive runs, the failure being the one known environmental case
(`lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary line, which
`.ai/prompts/frontend/T18` recorded and which no task in the package has yet made CI enforce). Mutation: **25 of
25 detected, 24 KILLED by assertion, 1 by collapse, no survivors.** Working tree clean.

**Files.** Exactly the frozen brief's list, verified by diff over `935a125c..HEAD`: two created
(`auth/returnTarget.ts`, `auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx`), seven modified
(`app/routes.tsx`, `auth/CallbackScreen.tsx`, and the five inherited test files). **`spec/` untouched.**
`package.json`, `src/i18n/`, `auth/codeExchange.ts`, `auth/idTokenNonce.ts`, `auth/accessToken.ts`,
`auth/authorizeRequest.ts` and `.oxlintrc.json` are all byte-identical to their pre-T24 state — which is AC11 and
the "NOT to modify" list, verified rather than asserted.

---

## 1 · Traceability — requirements and LOCKED decisions

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R7** — on a matching `state`, validate `nonce`, exchange at `/oauth2/token` with the verifier, hold the token in memory only | Yes (T20/T21/T23 built it; T24 adds the destination at its end) | `CallbackScreen.tsx:194-195` | Yes — `…FallsBackToHome.test.tsx:403` (the honoured path, token held), `:426` (9 rows) | No | No |
| **L1** — allowlisted post-login return target, falling back to the home route | **Yes — this task** | `returnTarget.ts:51-59` (derivation), `:70-72` (membership), `:120-139` (take + fallback), `CallbackScreen.tsx:194-195` | Yes — `:403`, `:426`, `:456`, `:506`, `:687`, `:739` | No | **D-1** (the rejection families are task 25's) |
| **L1** — "never to an arbitrary URL" (`agents.md:21`) | Yes, by **set membership** — no prefix test, no pattern match, no normalisation | `returnTarget.ts:70-72` | Mechanism yes (`:506`, `:739`, `:828`); the seven families are **task 25's** | No | **D-1** |
| **L3** — tokens in memory only | Unchanged, and re-proven for the new key | `returnTarget.ts:91` writes only a route path | Yes — `:956` (no token, no code among the written values; both stores swept) | No | No |
| **L16** — the sessionStorage carve-out, item (a)'s fourth entry: the return target, **single-use and deleted at callback** | **Yes — this task completes item (a)** | `returnTarget.ts:39` (the key), `:120-139` (single-use), `CallbackScreen.tsx:194` (at callback) | Yes — `:522` (read-before-delete as an order), `:540`, `:590`, `:910` | No | **D-4** (a throwing removal leaves it; T24-D9) |
| **L4** — neutral copy; no outcome distinguishes one cause from another | Yes — the fallback has no copy of its own | nothing added to `src/i18n/` | Yes — `:473` (pathname, search, text **and** `historyAction` identical) | No | No |
| **R8** — a discard returns the visitor to sign-in | Unchanged, and now proven not to touch the target | `CallbackScreen.tsx:89-99` | Yes — `:632` (7 fault shapes), `:650` (no target remembered), `:667` (the retry honours it) | No | No |

## 2 · Traceability — acceptance criteria AC1–AC17

| AC | Verified? | Evidence |
|---|---|---|
| **AC1** an allowlisted target is navigated to, with the "would otherwise have stayed" control | Yes | `:403` — the router is measured sitting on `/app/callback` with the exchange in flight, then released; `:426` honours all 9 derived members end to end |
| **AC2** a target outside the allowlist lands on the home route | Yes, in mechanism | `:506`, `:580` — one representative (`/app/home`, T24-D8's own shape). The families are task 25's — **D-1** |
| **AC3** an absent target lands on home by the same path as a rejected one | Yes | `:456`, `:473` |
| **AC4** no discard honours a target, over all failure shapes | Yes | `:632` (7 rows), `:650` |
| **AC5** "never an arbitrary URL": absolute, protocol-relative, `javascript:`, backslash, traversal, encoded traversal, prefix match | **Mechanism yes, enumeration deferred** | `returnTarget.ts:70-72`; the 15 `it.todo` rows at `:1055` — **D-1** |
| **AC6** the allowlist is **derived**; no second list | Yes | `:687` (all 11 route-table entries), `:699` (a path **added** to the table becomes legal), `:739` |
| **AC7** single-use, deleted at callback, deletion cannot precede the read | Yes | `:522` (ordered storage log), `:540`, `:590` |
| **AC8** nothing else enters storage; no token, no PII | Yes | `:956` (four keys exactly; both stores swept for the token and the code), `:985`, `:1002` |
| **AC8** (second clause) a target carrying a query or fragment is not honoured | **Deferred** | `it.todo` rows at `:1068-1069` — **D-1** |
| **AC9** the fallback is indistinguishable from an honoured target | Yes | `:473` — four fields compared, and no catalogue key was added |
| **AC10** a storage read that throws is treated as no target, and never surfaces as an error | Yes | `:890` (end to end), `:867` |
| **AC11** no new catalogue string, no new dependency, no change to the two pure readers or the holder | Yes | verified by diff: `package.json`, `src/i18n/`, `codeExchange.ts`, `idTokenNonce.ts`, `accessToken.ts` all untouched over `935a125c..HEAD` |
| **AC12** every inherited assertion updated with its reason restated | Yes | 17 assertions over five files; the reason sits at `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:64-72` (the renamed `POST_LOGIN_ROUTE` constant the three changed assertions all read), and inline at the others |
| **AC13** suite green by exit code with the one known environmental failure; `tsc --noEmit` exit 0 | Yes | measured twice; see above |
| **AC14** a visitor who left is not navigated, **and the inherited case that says so passes unchanged** | Yes | `:910`; and `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:834-856` **does not appear in T24's diff** — the verification method T24-D7 specified |
| **AC15** `takeReturnTarget()` never throws — a throwing store and a corrupted value | Yes | `:828` (11 rows, each pinning the **return value**), `:867` (an unreachable store), `:806` (the premise measured: `.then(f, g)` does not route `f`'s throw to `g`) |
| **AC16** a `removeItem` that throws still navigates, and the item remains | Yes | `:563` | 
| **AC17** the derivation excludes `:`-bearing paths; adding a plain path makes it legal with no second edit | Yes | `:699`, `:721` (the invented table carries `time/12:30`, `reports/:id`, `:secret`) |

## 3 · Declared deviations

**D-1 · AC5, and AC8's query/fragment clause, are verified by task 25 — which is the spec's own split.**
`tasks.md:36` is "a return target **on** the allowlist is honoured" and `tasks.md:37` is "a return target **off**
the allowlist falls back to the home route". Both cite the same named test. T24 therefore builds the mechanism and
proves the honoured path, and leaves the eleven rejection families as 15 `it.todo` rows with T24's harness already
in place. The one rejection T24 does prove — `/app/home`, the basename-carrying form — is T24-D8's own decision
and belongs to no task-25 family, so **no row was pre-empted**. This is a handoff, not a hole: the predicate they
will exercise is exported for exactly that reason (F6).

**D-2 · The destination renders nothing (T24-D11).** `/app/home` is in the route table with **no element**, and
no task in the 142-task list builds a home screen. The navigation is the whole of the verifiable claim and no test
asserts more. This is the **fifth unowned piece** in the package.

**D-3 · Parameterised routes are not honoured (T24-D5).** `invoices/:id` and `receipts/:id` are excluded, and they
are precisely the two routes notification emails deep-link to. A visitor who clicks an invoice email, signs in, and
should return to that invoice lands on home. Honouring them means pattern matching, and with it traversal and
encoded-separator handling; the price was stated at the Phase 4 gate and the decision was taken there.

**D-4 · A throwing `removeItem` leaves the target behind (T24-D9).** T23-D4's shape: the visitor's outcome does
not change, the next `rememberReturnTarget` overwrites it, and the alternative — failing a completed sign-in over
a storage quirk — is worse.

**D-5 · Nothing in production calls the producer yet.** `rememberReturnTarget` has no production caller; R28's
redirect is **task 41**. So in the running SPA today, *every* sign-in takes the fallback, and the honoured path is
exercised only by tests. That is the brief's scope (T24-D1/D2: T24 builds the mechanism so T41 is a one-line
call), and it is stated here because "a return target on the allowlist is honoured" is true of the code and not
yet observable in the product.

## 4 · The approving principal engineer's answers

**(1) Is the task fully complete?** Yes, for the task as `tasks.md:36` scopes it and the Phase 4 gate froze it.
The mechanism exists, the honoured path is proven end to end over every member of the allowlist, and the five
files that inherited an assertion about where a successful callback ends were all updated with their reasons
restated. What is not here is task 25's rejection table and task 41's producer, both by the spec's own division.

**(2) Does it satisfy every acceptance criterion?** Fifteen of seventeen in full. **AC5** and **AC8**'s second
clause are satisfied in mechanism and deferred in enumeration under **D-1**. No criterion is unmet.

**(3) Does it violate any LOCKED decision?** No. L1 is the decision this task implements; L3 is re-proven for the
new key, with both stores swept; L16's carve-out gains its fourth and final item of clause (a) and nothing else —
AC8's case asserts the store holds exactly four keys during a sign-in and none afterwards; L4 holds because the
fallback has no copy of its own, which `:473` proves by comparing the rendered text. `spec/` was not touched.

**(4) Remaining risks.**

| Risk | Owner |
|---|---|
| The fallback lands on a blank route; no task builds `/app/home` | **unowned** (T24-D11) |
| No production caller, so the honoured path is unexercised in the product | task 41 |
| The storage carve-out is now **two** constants in two modules, and a guard enumerating only `AUTHORIZE_STORAGE_KEYS` would report the return target as a violation of the carve-out that names it | **task 26** (T24-D4) |
| Notification-email deep links are not honoured | declared (T24-D5); a future task if the cost is revisited |
| No real-browser round trip: `oidcRoundTripEndToEnd` has no Playwright suite and no owning task — the largest verification gap in the package | **unowned** |
| The environmental `oxlint` failure is indistinguishable from a real lint regression on this host; CI must pin the suite total **and** that failure's identity | **unowned** (T18 Gap 6) |

Two smaller carry-forwards, unchanged by this task: `contracts/api/token-claims.md` is false on `email`/`name`,
and there is no ID-token contract document.

---

**VERDICT: PASS** — the mechanism L1 asks for is built, derived from the route table, total on read, single-use,
and proven over every allowlist member with controls against vacuity; the two unmet AC clauses are the spec's own
task-25 split, declared as D-1.

---

## Next — Phase 13 — PR preparation.
