<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T43 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/11-test-review.md` · `artifacts/11-mutation-run.txt` |
| **Produces** | `artifacts/12-specification-verification.md` |
| **Verdict** | **PASS** — with four open questions carried to the owner and twelve deviations recorded |

Verified against `spec/frontend/{requirements,design,package,tasks,agents}.md` and
`contracts/api/auth.yaml`, clause by clause, reading the spec **after** the implementation was finished
rather than from memory of the brief.

## The task statement

> **43. Registration accepts 12 characters.** A 12-character password is accepted client-side as advisory.
> Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1. Blocked on 173.

| | |
|---|---|
| *accepted client-side as advisory* | The length table's five in-range and out-of-range rows all **reach the transport**; nothing is disabled; the only consequence of being out of range is `aria-invalid`. Harness TR06 (a blocking check) is killed |
| *verifies `registrationAccepts…`* | `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`, 40 cases. `package.md:182` is the name, matched exactly |
| *blocked on 173* | T173 closed 2026-11-12; this task consumed its layer and added four primitives to it |

## R1, clause by clause

> WHEN a visitor submits the registration form, THEN the SPA SHALL check that the password is 12 to 128
> Unicode code points as advisory guidance only, and SHALL submit to `POST /accounts` through the generated
> client. The server is the authority.

| clause | where | instrument |
|---|---|---|
| *a visitor submits the registration form* | `features/account/RegisterScreen.tsx` via `app/ui/Form.tsx` | AC1: one form, two inputs, one submit control, all inside it |
| *checks 12 to 128* | `features/account/passwordLength.ts` + the screen's submit | the length table; `ADVISORY_MINIMUM_CODE_POINTS` 12 and `ADVISORY_MAXIMUM_CODE_POINTS` 128 asserted as values |
| *Unicode code points* | `countCodePoints` spreads the string | 12 astral code points measured as **24 UTF-16 units through the DOM's own value**; harness TR05 (`.length`) killed by the 7-emoji row |
| *advisory guidance only* | nothing consults the result to decide whether to send | every table row asserts the request went; TR06 and TR23 killed |
| *SHALL submit to `POST /accounts`* | `.POST('/accounts', { body })` | AC5: exactly one request, method, path, body |
| *through the generated client* | `createPublicAuthApi()` → `createAuthClient()` | AC5's source scan; TR07 (hand-written `fetch`) killed |
| *The server is the authority* | no gate, no disable, no transform of what was typed | the 128 upper bound is **not** enforced client-side either; task 44 adds its row |

**The contract agrees, and more strongly than the brief did.** `contracts/api/auth.yaml:600-609`'s
`RegisterAccountRequest` declares `password` as `type: string` with **no `minLength` and no `maxLength`**.
So the 12–128 bound exists in neither the schema nor the generated types: it is advisory *by construction*,
and a client that enforced it would be inventing a constraint the contract does not carry. The same
operation declares **`security: []`** (`auth.yaml:17`), which is the spec-level reason AC5 asserts the
request carries no `Authorization` header and the reason `createPublicAuthApi` exists at all (D4).

## R2 and L4 — enumeration safety

> WHEN the registration request returns, THEN the SPA SHALL show the same "check your email" screen whether
> or not the email already exists, with identical copy and layout, and SHALL NOT add client-side branching
> or delay.

| clause | how |
|---|---|
| *the same screen either way* | AC11 drives **202, 409 and 500** through the same journey and asserts the identical rendered result. The contract declares **202 as the only response** (`auth.yaml:24-32`, *"Always returned regardless of whether the email was already registered (enumeration-safe)"*), so a client that branched on status would branch on something the contract does not define |
| *identical copy* | `register.success` is one catalogue string, asserted not to contain *already/exists/taken* |
| *no client-side branching* | AC11's structural scan forbids `.status`, `data` and `error` in the screen; TR19 killed |
| *no delay* | the same scan forbids `setTimeout`/`setInterval` — which is **L4's timing clause**, not only R2's: *"Copy, redirects, styling, and **timing** are identical … No lockout timers"* |

**One conformance point the reviews did not raise.** `RegistrationAcknowledgement` requires a `message`
field (`auth.yaml:611-616`). The screen **never reads the response body**, so that message is never
rendered — which is required by **L11's *catalogue-only strings***: showing a server-supplied sentence would
put a word on screen that no catalogue owns and no translation covers. The instrument that holds it is
AC11's scan (`data` is forbidden in the screen), and it is worth naming because rendering `message` is the
single most natural thing a careless implementation would do with a 202 body.

## The LOCKED decisions

| | verdict |
|---|---|
| **L2** *interactive login is SAS-hosted* | **Upheld, and narrowed in writing.** T16's credential scan now carries a three-row table (by file, shape and count) whose header states the distinction: L2 governs login **steps**; account **creation** is R1. The SPA collects a password exactly once, to create an account, never to authenticate one. No `one-time-code` and no `current-password`/`new-password` shape is excused anywhere |
| **L3** *tokens in memory only* | No storage write anywhere in the journey, now measured by a **live** spy (the old instance spy saw nothing) |
| **L6** *generated client only* | `createPublicAuthApi` wraps the generated `createAuthClient`; the screen builds no URL and calls no transport |
| **L11** *WCAG AA, keyboard, managed focus, live regions, catalogue-only strings* | Programmatic labels asserted via `input.labels`; every control keyboard-reachable; the live region **pre-mounted and filled** (D1); all six strings from the catalogue, asserted in both directions so a byte-identical literal fails |
| **L12** *phase additivity* | One element attached to a path declared since task 16. No route added, no shell replaced |
| **L13** *confirmed stack* | **Conformance note, not a violation.** TanStack Query and Zustand are listed as confirmed and remain **uninstalled after 43 tasks**; D9 chose local `useState` because a one-shot public mutation has no cache to invalidate and no cross-screen state. L13 confirms a stack, it does not mandate a library per screen — but the gap is now long-standing and belongs to the first screen that needs server state across screens |
| **L14** *route set under `/app`* | `register` was already in `PUBLIC_ROUTE_PATHS`; both declared sets are asserted against hand-written lists. **No ADR was needed because no public route was added** |
| **agents.md** | No spec file modified. No new route. Flat catalogue keys. No restated standing rules |

## Named tests (`package.md` §8)

| test | owner | state |
|---|---|---|
| `registrationAcceptsTwelveAndOneTwentyEightCodePoints` | tasks **43 and 44** | written by this task, **40 cases**, built as a table so task 44 adds a row |
| `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` | task **45** | not written. Task 45 owns the advisory copy, which is why D7 froze six keys and no rejection sentence exists |
| `passwordLengthCountsCodePoints` | task **46** | not written. This task asserts the counter's **totality**, which R1 needs because an advisory check that throws stops being advisory; task 46 owns the counting claim itself |

No named test is left unowned, and nothing in this task's file does another task's work.

## Open questions carried to the owner

| | |
|---|---|
| **OQ-T43-9** | A rejected send returns the form to `'idle'` and says **nothing**. D7 froze six keys, so a failure sentence is an owner's decision. The screen hangs on no state and lies about no outcome, but it explains nothing either |
| **OQ-T43-10** | Should a **public** screen focus its heading on arrival? D5 froze *no focus move* and the guard added in phase 9 now enforces it for all four non-home screens. `HomeScreen` moves focus because it is an arrival from a redirect with no document load; `/app/register` is reached by a link or a typed URL |
| **OQ-T43-11** | **Double submit.** Two clicks send two `POST /accounts`, so two verification emails. R2 forbids branching and delay, which an in-flight guard is not; D1 and D7 are silent. If taken, the shape is `ActionButton`'s — `aria-disabled` plus a ref, never the `disabled` attribute |
| **OQ-T43-7** | No `autoComplete` on either field, so no password manager offers to generate or save. The credential scan treats `new-password` as a login-credential shape with **no permitted row**, making this a deliberate table entry rather than a quiet improvement |

Carried unchanged: **OQ-T43-6**, **OQ-T43-8** (Enter-to-submit is unmeasurable in jsdom), OQ-T42-5 (and
`/app/register` **is** such a public path), OQ-T42-3, OQ-T33-2's authenticated half, OQ-T40-1, OQ-T173-4/5,
OQ-T174-3/5, FU-T42-1, FU-T43-2, FU-T37-2, FU-T39-1, task 38's Q15. **FU-T43-1 is closed.**
