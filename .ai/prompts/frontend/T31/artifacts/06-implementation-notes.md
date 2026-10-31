# frontend · T31 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

Built as planned: `profile/readAccount.ts` and **49 cases** in nine `describe('T31: …')` blocks inside one
outer T31 block.

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| The shared file | **102 / 102** (T30's 53 untouched, T31's 49 added), exit 0 |
| Whole suite | **1501 passed / 1 failed (1502)** — the known environmental `lintRejectsHandWrittenBackendFetch` |

---

## §1 The compile-time pins were proven before being relied on

**G2** froze `Account['status']` as the generated union while **AC8** requires an unrecognised string to fail.
A union is compile-time only, so `ACCOUNT_STATUSES` has to exist as a runtime list — and that list is the one
place T31 restates any part of the schema, so it is the one place that can drift.

It is held in **both** directions, and both were **run before the case that depends on them was written**:

| Mutation | Result |
|---|---|
| Remove `'DELETED'` from the list (a contract status unlisted) | `TS2344: Type '"DELETED"' does not satisfy the constraint 'never'` |
| Add `'ARCHIVED'` (a status the contract lacks) | `TS2322: Type '"ARCHIVED"' is not assignable to type '"ACTIVE" \| "DELETED" \| …'` |

`satisfies` catches the second; `NoUnlistedStatus = AssertNever<Exclude<…>>` catches the first. A third case
compares the list to the YAML itself, which no type can reach. Writing the assertion first and checking later
is how a pin that never fires gets believed — T30's `opaqueredirect` arm was correct and wholly unverified for
three phases.

## §2 Three INHERITED instruments fired on T31's production file, each for a different reason

This is the part worth reading. None was a T31 defect, and none was fixed by loosening a scan.

### §2a T29's state scan — **its stated premise was false from the day it was written**

`ALWAYS_FORBIDDEN_STATE` reported `locked`, `suspended` and `deleted` in `profile/readAccount.ts`. Its own doc
comment explained why that should be impossible:

> *Every one measures ZERO comment-stripped hits in production today, AND no requirement, locked decision or
> later task asks for it. Those are two different tests and a word has to pass both to be here (Kimi F7).*

**The second test did not hold even in T29.** Task 31 was already in `tasks.md`; **R20(b)** commissions reading
the account's status; and the contract's enum is literally
`[PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED]`. So those three words always had a commissioned
future in production **source**. What they have no future in is sign-in **copy**, which is what R30 and L4
actually protect.

**Fixed as a correction to the premise, not to the pattern.** The pattern is unchanged and still global over
copy; the **source** scan now consults a new `PERMITTED_STATE_SOURCE` allowlist with one row naming the file,
the three words, the reason — and a **bound**. Widening the pattern's exceptions would have been the wrong
repair: T29's own header names that error class twice (T26's `localStorage` scan, T28's Kimi I5).

**The allowlist came with its own dirty-proof**, because an exception without one is how a scan rots — T29's
`PERMITTED_STATE_COPY` is kept empty for exactly that reason and says so. The new control asserts three
things: with the row removed the scan reports the file it protects; the row is not stale (the file still names
every word it claims); and the **bound holds** — `readAccount.ts` calls no translator and, structurally, is a
`.ts` file, which cannot contain JSX at all. Naming a state there cannot become showing one.

*A smaller lesson inside that one:* the bound's first draft tested `/<[A-Za-z]/` for JSX and failed on
`Partial<Record<…>>`. A generic parameter is not a JSX element, and a pattern that cannot tell them apart would
have forced the next reader to loosen it. The filename check is both stronger and simpler.

### §2b T27's second-factor scan — **an unbounded `tfa` matching `ReadAccountFailure`**

Four hits in `readAccount.ts`, all the same false positive: the pattern contained a bare `tfa`, and
`ReadAccountFailure` contains `…ntFailure` → `tFa`.

**Left-bounded to `\btfa`.** It still catches `tfa`, `tfaCode` and `tfa_method`; it no longer catches a word
that merely ends in `t` before `Fa`. Renaming T31's type would have hidden the bug rather than fixed it — and
the bug has a future victim: **`PaymentFailure` matches it too**, and the payment tasks are specified to write
exactly that kind of name. This is the same lesson T28 recorded twice about enumerative vocabulary patterns,
now in its unbounded form.

### §2c The token-storage scan — **the pin working as designed**

`ReadAccountError` is a fifth `this.name =`. The count went 4 → 5 and the comment names both additions. This
is a pin doing its job: a new `this.name =` must be somebody's deliberate decision, because the alternative is
a pattern that stops distinguishing a class naming itself from a write to `window.name`.

## §3 What was deliberately NOT carried across from T30

No `redirect: 'manual'`, no 3xx arms, no opaque-redirect arm, and no `response.redirected` check. T30 needed
all of them because a bearer-less `/userinfo` answers `302` to the login page, which `fetch` follows into a
`200` carrying HTML. **Measured here: `/accounts/me` answers a real `401`.** Copying the arms across would
have produced code no case could reach — and the cost of an unreachable arm is exactly what T30's phase 11
demonstrated.

**What DID carry across, for a different reason than T30 had:** the no-token short-circuit. This endpoint's
401 is well behaved, so the boundary could handle a missing session — but a bearer-less call was measured to
answer that 401 **with a `Set-Cookie: JSESSIONID`**. An unauthenticated read has a server-side side effect,
and it also provokes a renewal that cannot succeed. `''` is guarded alongside `null` because
`sessionBoundary.ts:89` attaches the header with `if (token)`, so an empty string sends no `Authorization`
header at all — the exact gap T30 paid a phase 11 finding for.

## §4 Validation is inverted from T30's, and the two readers share no rule

`AccountResponse` requires **all five** fields; `UserInfoResponse` requires only `sub`. So absence is an error
here and the normal answer there, which is why `readAccount.ts` is a separate module rather than an addition
to `readProfile.ts`.

`isUsableAccount` takes `unknown` deliberately: the generated type says the body is an `AccountResponse`, and
the measurements say a body is whatever arrived. Typing the parameter as `Account` would make every check look
redundant to the next reader and invite their removal.

**`emailVerified` is checked for being a boolean, not for being truthy.** Kimi's F5 caught this in the brief's
wording — `false` is a contract-valid, measured-real answer, and one "missing or empty" rule across five
fields of three types would have rejected it. T30's `sub === ''` is the analogue that *is* right: the
difference is the type, not the strictness.

## §5 One control was weaker than it looked, and fixing it found two unswept surfaces

AC10's sentinel control asserted that setting `document.title` makes exactly `['title']` report. It failed:
`title` **and** `head` both report, correctly, because the `<title>` element lives in the head.

Writing it the lazy way — accepting both under a `title` sentinel — would have left **`head` with no sentinel
of its own**, and the five-sentinel draft had also quietly skipped **`url`**. Both now have their own: a
`<meta>` the title cannot account for, and a `history.replaceState` query string. All seven surfaces are
proven, and the case ends by asserting every surface is clean again so a later case cannot inherit a dirty one.

## §6 Deviations

| ID | Deviation |
|---|---|
| **T31-D1** | T30 owns the shared file's import block, and a test file cannot reference a module without importing it. T31 added **four purely additive** import lines — nothing reordered, renamed or removed — so no T30 case can change meaning. Declared rather than absorbed, and rather than contorting T31 into dynamic imports in twenty-odd cases to keep the letter of a rule whose purpose this does not touch. |
| **T31-D2** | `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` (T29's) gained `PERMITTED_STATE_SOURCE`, a corrected premise comment, and one new control. §2a. |
| **T31-D3** | `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` (T27's) had its bare `tfa` left-bounded. §2b. |
| **T31-D4** | `tokensAreNeverWrittenToAnyStorage.test.tsx`'s `this.name =` count raised 4 → 5. §2c. |
| **T31-D5** | **OQ-T31-3 stays open by owner decision**: nothing owns acting on a readable-but-not-ACTIVE account. T31 reads and reports; it invents no route, copy key or redirect. |
| **T31-D6** | `contracts/api/auth.yaml` is **unchanged** by owner decision, so **OQ-T31-1** (no root-level `security`, making every operation nominally public) and **OQ-T31-2** (no 401/403 documented anywhere) remain open. AC1 therefore asserts the operation and its wiring, and AC3 asserts the bearer **behaviourally** through a real boundary — never as a contract property the document does not express. |

## §7 Open questions

None new. **OQ-T31-1**, **OQ-T31-2** and **OQ-T31-3** are open and owner-assigned, recorded verbatim in the
frozen brief. T30's **O6** still blocks T30's half end-to-end; T31 has no such blocker.

---

**Phase 6 complete.** One production module, 49 cases, three inherited instruments corrected rather than
loosened. Phase 7 self-reviews and scores it.
