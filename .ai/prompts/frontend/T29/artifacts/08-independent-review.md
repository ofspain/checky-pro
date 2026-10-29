# frontend · T29 · Phase 8 — Independent Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent static review of the T29 test file and self-review. The file is exceptionally thorough: it addresses every Phase 3 finding explicitly, keeps the vacuity argument in the header, and closes the timing clause with both behavioural and structural instruments. Mutation score is 18/18 killed with no survivors, no production change, and the self-review is honest about which instruments are proven by controls rather than mutants. The gaps below are real but narrow: a few common gate shapes the gating scan misses, an incomplete redirect-timing assertion under the fake clock, a comment-stripper that can corrupt string literals, and some vocabulary/key-name coverage holes.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed source.

---

## I1 · The gating scan misses several natural account-state gate shapes

**Severity.** Medium.

**Evidence.**
- `ACCOUNT_STATE_GATE` (lines 286-291) keys on names such as `accountState`, `accountLocked`, `isSuspended`, `isDeleted`, role comparisons and `roles.includes`.
- It does **not** match any of the following, all of which a future developer could write to branch on account state:
  - `account.state === 'LOCKED'` or `user.state === 'SUSPENDED'`
  - `account.status === 'LOCKED'`
  - `user.isLocked` or `account.locked`
  - `switch (accountState) { case 'LOCKED': ... }`
  - enum comparison `accountState === AccountState.Locked`

**Why it matters.**
The scan is intended to prove that nothing gates a failed sign-in on an account state. The self-review (S3) celebrates that an escape mutant spelling `account_state` is caught, but `account.state` is at least as natural a property name and is not caught. A `switch` on state is the most obvious way to add state-specific UX and would evade the regex entirely. The existing control at line 1387-1398 only exercises `===` comparisons on the names already in the pattern.

**Recommended change.**
Add the missing shapes to `ACCOUNT_STATE_GATE` and exercise them in the control:
- `\.state\s*[=!]==|\.status\s*[=!]==`
- `isLocked|\.locked\b|isSuspended|isDeleted` (the last two exist; `isLocked` and `\.locked\b` do not)
- `case\s+['"]LOCKED['"]|case\s+['"]SUSPENDED['"]|case\s+['"]DELETED['"]`
- `AccountState\.(?:Locked|Suspended|Deleted)`

Add one synthetic offender for each new shape and assert the scan flags them.

**Confidence.** Medium — pattern coverage.

---

## I2 · AC5 asserts the redirect-at-zero timing for only one of the five states

**Severity.** Medium.

**Evidence.**
- AC5's first case (lines 1111-1131) asserts the sentence is rendered at clock zero for all five account states.
- AC5's second case (lines 1133-1138) asserts the redirect is at clock zero **only for `ACCOUNT_STATES[2]` (`locked`)**.

**Why it matters.**
If a future bug introduced a `setTimeout(navigate, ...)` keyed on error code for `unknown`, `missing`, `suspended` or `deleted`, the sentence would still render at zero (because `discard()` is called synchronously), the sentence test would pass, and the redirect test would not run for the affected state. The real-clock AC2 struct comparison might catch it if the delays differ across states, but a uniform delay on the four non-locked states would leave all five outcomes structurally identical on a real clock as well (all would be measured before the deferred navigation completed).

**Recommended change.**
Extend the second AC5 case to loop over all five `ACCOUNT_STATES` and assert each lands on `/app/sign-in` with `REPLACE` at clock zero. Alternatively, fold the destination assertion into the first case so every state is checked for both sentence and redirect at zero.

**Confidence.** Medium — timing coverage.

---

## I3 · `strip()` corrupts string literals that contain `//` or `/* */`, which can hide forbidden words

**Severity.** Medium.

**Evidence.**
- `strip()` at line 308-310 is:
  ```ts
  function strip(source: string): string {
    return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  }
  ```
- It removes `//...` to end-of-line and `/*...*/` everywhere, including inside string literals.
- Example: the literal `"https://id.example/help/locked-account"` becomes `"https:` after the `//...` is stripped, removing the word `locked`.
- The AC7 control at line 1278 only checks that `deleted` is removed from a *comment*, not that a string literal is preserved.

**Why it matters.**
The source scans (AC6 timer scan, AC7 state scan, AC9 gating scan) all run on comment-stripped code. If a production string literal contains a URL or any `//`/`/* */` sequence surrounding a forbidden word, the stripper can delete the forbidden word and the scan reports clean. For T29 specifically, a help URL string carrying a state word would be the most likely shape of this leak, and URL strings are exactly where `//` appears.

**Recommended change.**
Replace the naive regex stripper with one that preserves string literals, or pre-tokenise the source to remove only comments outside strings. As a smaller fix, add a control that feeds `strip()` a string literal containing a forbidden word and a `//`/`/* */` sequence, and asserts the forbidden word survives. If the current stripper fails the control, fix it.

**Confidence.** Medium — instrument correctness.

---

## I4 · The state-word vocabulary omits common account-state synonyms

**Severity.** Low.

**Evidence.**
- `ALWAYS_FORBIDDEN_STATE` (line 191-192) is: `locked|lockout|suspended|deleted|deactivat|inactive|no\s*such|banned|terminated|archived|dormant`.
- Common account-state phrasings not covered: `compromised`, `breached`, `flagged`, `under review`, `restricted`, `limited`, `shadow` (as in shadow-banned), `nonexistent`, `unrecognized`.

**Why it matters.**
The self-review acknowledges the vocabulary is enumerative and that a phrasing nobody listed passes. The list is defensible for the current code base, but several of the omitted words are plausible future account-state labels. Adding them now with controls prevents a later task from accidentally introducing enumeration-friendly copy.

**Recommended change.**
Add `compromised|breached|flagged|restricted|limited|shadow|nonexistent|unrecognized` to `ALWAYS_FORBIDDEN_STATE`. Add one control phrasing for each, and keep the existing legitimate-word controls (`unknown`, `missing`, `pending`, `closed`, `disabled`, `not found`) passing.

**Confidence.** Low — future-proofing.

---

## I5 · Catalogue keys can name account-state concepts without being flagged

**Severity.** Low.

**Evidence.**
- `FORBIDDEN_STATE_KEY` (line 230-231) is: `locked|lockout|suspended|deleted|deactivat|inactive|banned|terminated|archived|dormant`.
- It does not include concept names like `accountState`, `accountStatus`, `accountLockedReason`, `lockoutReason`, etc.
- A catalogue key like `signIn.accountState` or `callback.accountStatus` with an innocent value (e.g., "Your sign-in did not complete.") would not be flagged by `forbiddenStateEntries` because the key does not match `FORBIDDEN_STATE_KEY` and the value is innocent.

**Why it matters.**
AC8's purpose is to ensure no user-facing string names an account state. A key name that encodes an account-state concept is a strong signal that state-specific copy exists or will exist, even if today's value is uniform. The current key pattern only catches keys that literally spell a forbidden state word.

**Recommended change.**
Add `accountState|account_status|accountStatus|lockoutReason|lockedReason|suspensionReason|deletionReason` to `FORBIDDEN_STATE_KEY`, or create a separate concept-key pattern. Add a control showing that `signIn.accountState` with an innocent value is flagged by its key name, while `invoice.status` (a non-sign-in surface) is permitted because the value is commissioned.

**Confidence.** Low — key-name coverage.

---

## I6 · The role/account-state gate pattern misses some array-method and property-name variants

**Severity.** Low.

**Evidence.**
- `ACCOUNT_STATE_GATE` matches `roles.includes('MERCHANT'|'ADMIN')` but not:
  - `roles.some((r) => r === 'MERCHANT')`
  - `roles.find((r) => r === 'MERCHANT')`
  - `account.roleName === 'MERCHANT'`
  - `user.permissions.includes('MERCHANT')`
  - `account.type === 'MERCHANT'`

**Why it matters.**
The gating scan is enumerative by design. These variants are less common than the ones already covered, but they are natural ways to express the same gate. The existing control only exercises `roles.includes` for the array case and `account.role ===` for the property case.

**Recommended change.**
Extend the pattern with `\.some\s*\(|\.find\s*\(|roleName|\.type\s*[=!]==|permissions\.includes` and add synthetic offenders for each. Alternatively, document that the scan relies on the project's naming convention (`role`, `roles`) and that deviations must be caught by code review.

**Confidence.** Low — pattern completeness.

---

## I7 · The `search === ''` absolute assertion is load-bearing but the comment about it is not itself tested

**Severity.** Low.

**Evidence.**
- AC1 (lines 869-878) asserts `outcome.search === ''` and `outcome.hash === ''` absolutely for each state.
- The comment cites T28's MB29 as the mutant that proved the distinction.
- There is no dedicated control that shows the struct comparison alone would pass if all five states kept their query string.

**Why it matters.**
The absolute assertion is correct and important, but a future maintainer could delete it thinking the struct comparison is enough. A control would pin the difference.

**Recommended change.**
Add a control that constructs two outcomes with identical non-empty `search` strings and shows the struct comparison passes while the absolute assertion fails. Keep it small: it only needs to demonstrate the vacuity the absolute assertion closes.

**Confidence.** Low — assertion pinning.

---

## Summary

I1–I3 are the most consequential: they are gaps in the instruments that do the heavy lifting (gating scan, fake-clock redirect timing, and source stripping). I4–I7 are coverage and documentation strengtheners. The file is already very strong — 18/18 mutants killed, no survivors — and these findings are polish rather than defects. No production change is required.
