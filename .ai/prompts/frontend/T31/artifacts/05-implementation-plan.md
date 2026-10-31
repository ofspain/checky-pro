# frontend · T31 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

The prompt's sections are Java-service shaped (entities, repositories, DAO-then-service-then-api ordering).
`frontend` has none of those layers, so each is answered with its real equivalent or marked **not
applicable** — inventing a match would make the plan less useful, not more complete.

---

## Files to create

| File | Contents |
|---|---|
| `frontend/src/profile/readAccount.ts` | The reader. One exported function, one error class, one derived type, one status guard. |

**A separate module, not an addition to `readProfile.ts`.** The frozen brief lists `readProfile.ts` under
*Files NOT to Modify* "beyond what sharing a module would require", and nothing requires sharing: the two
readers have different clients (`createAuthApi` vs `createOidcApi`), different validation rules (all five
fields required vs only `sub`), and different error types by **G3**. A shared module would couple two things
that agree on shape and disagree on every rule.

## Files to modify

| File | Change |
|---|---|
| `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | `describe('T31: …')` blocks **only**, per **G1**. |

Nothing else. No contract, no generated client, no `client.ts`, no `spec/`, no route, no screen.

## Public surface (signatures)

```ts
/** The contract's AccountResponse, derived — union unwidened (G2). */
export type Account = authComponents['schemas']['AccountResponse'];

/** The five the contract declares, as a runtime set — the one place the union is restated, deliberately. */
export const ACCOUNT_STATUSES: readonly Account['status'][];

export type ReadAccountFailure = 'no-session' | 'unauthorized' | 'unavailable';

export class ReadAccountError extends Error {
  readonly failure: ReadAccountFailure;
  constructor(failure: ReadAccountFailure);
}

export function readAccount(
  api: AuthApi,
  tokens: Pick<SessionTokens, 'current'>,
): Promise<Account>;
```

`api` and `tokens` are **parameters** (**G6**), mirroring `readProfile`'s signature so the two read alike even
though they share nothing.

### The one restatement, and why it is unavoidable

**G2** freezes `Account['status']` as the generated union, and **AC8** requires an unrecognised string to
fail. A union is a *compile-time* artifact — it does not exist at runtime, so nothing can be checked against
it without a runtime list. `ACCOUNT_STATUSES` is that list.

This is the only place in T31 that restates part of the schema, and it is **held to the schema by the type
checker**: declaring it as `readonly Account['status'][]` means a status removed from the contract stops
compiling, and `satisfies`-style exhaustiveness pins the other direction. **A test asserts the list is
exactly five and matches the contract's enum**, so the two cannot drift silently. T30's F10 lesson applies —
a hand-written copy that happens to match today is not the property worth having — and this is the narrowest
form of the copy that still makes the check possible.

## Private surface

| Function | Purpose |
|---|---|
| `isAccountStatus(value: unknown)` | Type guard over `ACCOUNT_STATUSES`; narrows `string` to the union. |
| `isUsableAccount(body: unknown)` | The whole of AC7 in one place: the body is a non-null object, `accountUuid`/`email`/`status`/`createdAt` are non-empty strings, `emailVerified` is a **boolean** (so `false` passes), and `status` is recognised. |
| `FAILURE_MESSAGE` | `Record<ReadAccountFailure, string>` — message **by key**, so the error cannot carry detail (**G3**). |

`isUsableAccount` takes `unknown` on purpose. The generated type says the body is an `AccountResponse`; the
measurements say a body is whatever arrived. Typing the parameter as `Account` would make the function's own
checks look redundant to a future reader and invite their removal.

## Entities · Repositories · Services

**Not applicable** — no persistence layer, no ORM, no DI container. The nearest equivalents, all consumed and
none modified:

| Equivalent | Item |
|---|---|
| Generated client | `createAuthApi(boundary, rateLimit?)` → `operations["getOwnAccount"]` |
| Session boundary | `SessionTokens.current()`, `boundary.middleware(AUTH_PUBLIC_OPERATIONS)` |
| Rate-limit notice | `createRateLimitNotice()` — in tests only; T31 constructs no client in production code |

## Reader control flow

1. **No token, no request.** `const token = tokens.current()`; if `null` **or `''`** → `no-session`, nothing
   sent (**G4**, **AC13**). The `''` arm is explicit because `sessionBoundary.ts:89` attaches with
   `if (token)`, so an empty string sends no header and produces the very request this guard prevents.
2. **One call**, `api.GET('/accounts/me')`, inside a `try/catch` that turns **any** throw into `unavailable`
   (**G5**). No `redirect: 'manual'` — this endpoint answers a real 401, and carrying T30's option across
   would be cargo-culting a fix for a problem that does not exist here.
3. **401 or 403 → `unauthorized`**, reached only after the boundary has already run its single renewal and
   re-send (**AC4**).
4. **Not ok → `unavailable`.**
5. **`isUsableAccount` → else `unavailable`** (**AC7**, **AC8**).
6. Return the body unchanged (**AC6**) — no defaulting, no coercion, no normalising.

**No redirect handling.** T30 needed three redirect arms because a bearer-less `/userinfo` answers `302` into
a followed `200 text/html`. Measured here: `/accounts/me` answers `401`. Adding arms for a shape this endpoint
does not produce would be untestable code, and T30's phase 11 showed what untested arms are worth — its
`opaqueredirect` arm was correct and completely unverified for three phases.

## Tests required

All as `describe('T31: …')` blocks in the shared file, each declaring its own constants and helpers
(**G1**).

| Block | Cases | Discharges |
|---|---|---|
| **T31: AC1/AC2** the contract declares it and the client exposes it typed | the path exists with `operationId: getOwnAccount`; `AccountResponse` requires all five; the generated operation is reachable typed; `ACCOUNT_STATUSES` matches the contract's enum exactly | AC1, AC2 |
| **T31: AC3/AC4/AC11** the boundary owns the bearer and the 401 | bearer sent; one renewal then re-send; final 401 → `unauthorized`; 403 → `unauthorized`; `GET /accounts/me` absent from `AUTH_PUBLIC_OPERATIONS` as an upper **and** lower bound; 429 shows the notice; **429 on the re-send**; a 200 leaves the notice alone | AC3, AC4, AC11 |
| **T31: AC5** no token read for `status` or `emailVerified` | the decode scan bounds the set to one, asserted; T31's own claim pattern over only that set; a control per synthetic read; a control proving silence on `response.status` and `AccountResponse.emailVerified` in commissioned code | **AC5** |
| **T31: AC6** the status is read faithfully | **all five statuses** survive unchanged; `emailVerified: false` is returned as `false`; nothing is defaulted or inferred | AC6, F5 |
| **T31: AC7/AC8** an incomplete or out-of-contract body fails | each of five fields missing, `null`, wrong-typed and (where meaningful) empty; a **JSON-`null` body**; an **unrecognised status**; a control that a complete body succeeds | AC7, AC8 |
| **T31: AC9** a failure carries no detail | the `WWW-Authenticate` detail reaches nothing; no cause on any path; a throwing transport; a control that a leaky error **would** be caught | AC9 |
| **T31: AC10** the account never persists or logs | all seven surfaces plus six channels, raw and percent-decoded; a sentinel per surface and per channel; nothing written on the failure paths | AC10 |
| **T31: AC13** no session, no request | `null` token and `''` token both issue nothing, asserted by an empty call list | AC13 |
| **T31: AC12** every instrument shown dirty | T31's own instruments table; the empty-tree refusal; comment-stripping control | AC12 |

### T31's own helpers, declared inside its blocks

| Helper | Why T31's and not T30's |
|---|---|
| `authHarness(answers, options)` | Wires `createAuthApi`, a **real** `createSessionBoundary` and a **real** `createRateLimitNotice`, records `{path, method, auth}` per call. T30's wires `createOidcApi` (**F6**). |
| `T31_FORBIDDEN_TOKEN_CLAIM` | `\bstatus\b` and `\bemailVerified\b`, word-bounded, scanned **only** over the decoding file set (**F9**). |
| `T31_INSTRUMENTS` | T31's own dirty-proof table (**F1**). |

T31 **calls** `tokenDecodes()`, `hitsOf()`, `synthetic()`, `refuseEmpty()`, `surfaces()`, `leaks()` and
`watchConsole()` unchanged.

### Named tests that must stay green

`generatedClientCompiles` (10/10) · `profileIsReadFromUserinfoAndAccountStatusFromMe` (T30's 53 cases
untouched) · `emailVerifiedClaimIsNotAccountState` must remain **provable** for T32 — so T31 constrains
`emailVerified` only as *a boolean faithfully read*, and asserts nothing about it driving UI.

## Execution order

The prompt's "schema → dao → service → api → tests" maps onto this task as:

1. **Contract and generated client — read only, verify nothing is needed.** Confirm `operationId`,
   `AccountResponse`'s five required fields and the generated union. Nothing to migrate or regenerate; this
   step exists to prove that, not to change it.
2. **`readAccount.ts`** — the derived type, `ACCOUNT_STATUSES`, the guards, the error class, the reader.
3. **`tsc --noEmit`** before any test is written. The derived union and the guard are the two things most
   likely to be wrong, and the compiler answers faster than a test run.
4. **The T31 blocks**, in acceptance-criterion order, AC5's scans last — they are the slowest to write and the
   most likely to collide with commissioned code.
5. **Run the file alone, then the whole suite.** Baseline is **1 failed of 1452** (the known environmental
   `lintRejectsHandWrittenBackendFetch`); T31 must not change that count except by adding passes.
6. **Commit the tests.** The harness reverts with `git checkout -- src/`, and uncommitted edits are destroyed
   silently — 19 cases in T14, 5 in T15.
7. **Write and run the mutation harness** with a **baseline guard and an anchor preflight**, both inherited
   from T29 and T30 because both failures happened: a drifting baseline converted two kills into reported
   survivors, and a stale anchor made two mutants print `BROKEN` — which is not `SURVIVED` — so a 26-killed
   run was published as 28 of 28.

## Hazards to plan around

| # | Hazard |
|---|---|
| **H1** | **`response.status` is everywhere.** A tree-wide scan for `status` fails on `readProfile.ts`, `rateLimitNotice.ts` and `sessionBoundary.ts` — code R21 and R23 *require*. The claim scan must run only over the decoding set, and a control must prove it stays silent on the commissioned reads. |
| **H2** | **Node's `Request` rejects relative URLs.** Use the package idiom, `installBrowserRequest()`. |
| **H3** | **`openapi-fetch` rejects when the transport throws** but resolves on an HTTP error status. Hence G5. |
| **H4** | **Registration order is only observable on a re-send.** A 429 on the first call passes under either order; the case must drive **401 → 429**. This is what MP27 caught in T30. |
| **H5** | **A JSON-`null` body must not reach a property access.** It shipped as a defect in T30. |
| **H6** | **`false` is an answer, not an absence.** A single "missing or empty" rule across five fields of three types would reject it. |
| **H7** | **The suite's own prose can fail another task's instrument.** `shellBuildsAndMounts` scans app source for `https?://` **without** stripping comments, so a measured URL written into a comment fails it. Describe URLs; do not quote them. Twice in T30. |
| **H8** | **A test file is one module.** A T31 constant colliding with a T30 name is a redeclaration error, not a scoping accident — prefix T31's module-level names if any are needed, and prefer block scope. |

---

**Phase 5 complete.** One file to create, one to modify, both authorized by the frozen brief. No code written.
