<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T31 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx -t "T31:"` | **50/50 passed** |
| `vitest run src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | **103/103 passed** (T30's 53 untouched, T31's 50 added) |
| `vitest run src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | **155/155 passed** |
| `oxlint src` | **exit 0, no output** |
| `tsc --noEmit` | **exit 1** — pre-existing error in `src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`, see I1 |

No production or test code was changed during this review.

---

## Findings

### I1 — `tsc --noEmit` is not green on current HEAD, contradicting the self-review gate
· **Severity:** **Major** (process / reproducibility)
· **Evidence:**
  ```
  src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx(718,28): error TS2550:
    Property 'findLastIndex' does not exist on type 'string[]'. Do you need to change
    your target library? Try changing the `lib` compiler option to 'es2023' or later.
  src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx(718,43): error TS7006:
    Parameter 'event' implicitly has an 'any' type.
  ```
  The file is owned by T23/T24 (`fd04091b`, `f5b380c1`, `6b5b4afe`); T31 never touched it. `frontend/tsconfig.json` sets `"target": "ES2022"` and `"lib": ["ES2022", ...]`, and `findLastIndex` is ES2023.
· **Assessment:** The failure is **not caused by T31**, but the self-review artifact reports `tsc --noEmit | exit 0`. That claim is not reproducible on the current checkout. Either the self-review ran against a different `node_modules` state (e.g. TypeScript 5.x while `package.json` now specifies 7.0.2), or the gate was recorded incorrectly. A red static-analysis gate undermines the rest of the scorecard.
· **Recommendation:** Re-run `tsc --noEmit` from a clean install on the committed `package.json`/`pnpm-lock.yaml`, decide whether to add `"ES2023"` to `lib` or to rewrite the `findLastIndex` call, and update the Phase 7 artifact if the gate cannot be made green. Do not let a pre-existing failure hide behind T31's green test counts.

### I2 — `PERMITTED_STATE_SOURCE` bound proves structure, not the safety property it claims
· **Severity:** **Major**
· **Evidence:** `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:250-651`. The allowlist row for `profile/readAccount.ts` permits `locked`, `suspended`, `deleted` because the module "validates and returns" and "renders nothing and calls no translator". The test enforces the row with two checks: `row.file.endsWith('.ts')` and no `\bt\(|useTranslation` in the source. It does **not** check that the words are used only inside `ACCOUNT_STATUSES` / `isAccountStatus`, nor does it forbid them appearing in a string literal or error message inside the same file.
· **Assessment:** The bound is weaker than the `why` field advertises. A future edit could add `throw new ReadAccountError('suspended')` or `console.info('account suspended')` in `readAccount.ts` and the T29 test would still pass, because the structural checks (`.ts` file, no translator) would hold while the semantic property (state words do not reach a visitor) would not. The self-review's S4 correctly identifies this as residual risk, but ranks it Minor; given that the file is explicitly allowlisted to name states, a regression path that keeps the file `.ts`-only is realistic.
· **Recommendation:** Tighten the bound. Either (a) assert that each permitted word appears **only** inside `ACCOUNT_STATUSES` and `isAccountStatus` (e.g. by scanning the file for the allowed contexts), or (b) move the runtime status list to a dedicated `accountStatus.ts` module and permit only that module, leaving `readAccount.ts` forbidden. A structural bound on a file that is allowed to name sensitive states is not enough.

### I3 — `\btfa` narrowing was made in a T27-owned file and should be T27-verified
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx:172-177`. T31 changed the MFA source scan pattern from bare `tfa` to `\btfa` to stop it matching `ReadAccountFailure`. The self-review's S4 documents the rationale and checks `tfa`, `tfaCode`, `tfa_method`.
· **Assessment:** The fix is sound for T31's immediate need, but it is a cross-task edit to T27's instrument. `\btfa` no longer matches a state word that merely ends in `t` before `Fa`; that is the intent, but it also means a legitimate identifier such as `paymentTfa` or `twoTfa` would no longer be flagged. The self-review acknowledges this is "someone else's" pattern, and T27's controls passing today is not a guarantee for future identifiers.
· **Recommendation:** Treat this as a T27 finding to be re-run in T27's next review phase. Add a T27-specific control that proves every second-factor spelling T27 intends to catch is still caught after the left-bound change, not only the three T31 checked.

### I4 — AC10 leak sweep does not include the `emailVerified` value
· **Severity:** **Minor**
· **Evidence:** `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:1431-1436` defines `ACCOUNT_VALUES` as `[SUSPENDED.accountUuid, SUSPENDED.email, SUSPENDED.createdAt, SUSPENDED.status]`. The boolean `emailVerified` (both `true` and the measured-critical `false`) is not in the needle list. The first AC10 case checks `leaks(surfaces(), ACCOUNT_VALUES)` and `watch.lines`.
· **Assessment:** A `console.log(account.emailVerified)` would be caught by `watchConsole`, but a write to `sessionStorage.setItem('ev', String(account.emailVerified))` would stringify to `"true"` or `"false"` and would **not** be matched by `ACCOUNT_VALUES`. The second AC10 case checks that `sessionStorage`/`localStorage` keys are empty on failure paths, but not on the success path where the account is actually held.
· **Recommendation:** Add `String(SUSPENDED.emailVerified)` (or both `true` and `false` explicitly) to `ACCOUNT_VALUES`, or add a dedicated case that stores a `false` account and sweeps for `"false"` on all surfaces. The current sweep proves string fields do not leak; it does not prove the same for the boolean verification flag.

### I5 — No-session guard misses whitespace-only tokens
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/profile/readAccount.ts:157-158` rejects `null` and `''` only. `frontend/src/api/sessionBoundary.ts:89` attaches the bearer with `if (token)`, so a token value of `'   '` (truthy) is sent as `Authorization: Bearer    `.
· **Assessment:** The guard's stated purpose is to avoid both a `Set-Cookie: JSESSIONID` side effect and a doomed renewal. A whitespace-only token triggers exactly those: the server answers 401, the boundary tries one renewal, and the reader finally returns `unauthorized`. No test covers this shape. It is the same class of "empty-ish token" gap that T30 paid a phase 11 finding for with `''`.
· **Recommendation:** Either reject whitespace-only tokens in `readAccount` (`token.trim() === ''`) or trim/normalise in `sessionBoundary` before deciding whether to attach the header. Add a test case proving a whitespace token issues no request and no renewal.

### I6 — Mutation harness baseline is coupled to an environmental failure count
· **Severity:** **Minor**
· **Evidence:** `.ai/prompts/frontend/T31/artifacts/07-mutation-harness.py:75` hard-codes `EXPECTED_BASELINE_FAILURES = 1`, tied to `lintRejectsHandWrittenBackendFetch` failing on this host.
· **Assessment:** The harness correctly refuses to run if the baseline drifts, which prevents the T30 score inflation bug. But the coupling means a fix to the environmental test, or a new environmental failure, stops the harness from running rather than adjusting. This is acceptable as long as the harness is maintained by hand, but it is a manual pin that can rot.
· **Recommendation:** Consider deriving the expected baseline from an explicit allowlist of known environmental failures (e.g. by test name) rather than a raw count, so an unrelated new failure is not silently absorbed into the baseline.

### I7 — `ReadAccountError` messages are internal, but the inversion test checks only `catalogue.ts`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:1947-1958` asserts that none of the three messages appears in `src/i18n/catalogue.ts`.
· **Assessment:** The test correctly enforces that the messages are not catalogue keys. It does not prove they are never rendered by any screen — that obligation is left to T32/T33 consumers. This is deliberate, but it means the "internal" property is held at the message source only, not at the UI boundary.
· **Recommendation:** No change needed for T31, but document in the artifact that AC9's "internal message" finding is a source-level pin; UI consumers of `failure` must be reviewed separately.

### I8 — `isUsableAccount` accepts `createdAt` and `email` as any non-empty string
· **Severity:** **Informational**
· **Evidence:** `frontend/src/profile/readAccount.ts:127-137` checks `accountUuid`, `email`, `createdAt` for non-empty string and `emailVerified` for boolean. It does not validate UUID shape, email shape, or ISO-8601 date-time shape.
· **Assessment:** This is within the scope the brief gave T31 ("missing or empty"). The generated client types the fields, so downstream consumers may assume validity. A malicious or malformed server response could carry an empty-looking `email` of `" "` or an invalid UUID and still pass.
· **Recommendation:** Consider tightening validation in a follow-up if any downstream screen treats these fields as trustworthy. Not required for T31's mandate.

### I9 — `ACCOUNT_STATUSES` and `NoUnlistedStatus` are exported validation machinery
· **Severity:** **Informational**
· **Evidence:** `frontend/src/profile/readAccount.ts:40-65` exports `Account`, `ACCOUNT_STATUSES`, and `NoUnlistedStatus`.
· **Assessment:** Exporting the runtime list is necessary because tests need to compare it to the YAML enum. `NoUnlistedStatus` is only a compile-time assertion and need not be exported. Leaving it public increases the module's API surface without benefit.
· **Recommendation:** Remove the `export` keyword from `NoUnlistedStatus`; keep `Account` and `ACCOUNT_STATUSES` exported as needed by tests and consumers.

---

## What the implementation gets right

- **AC5 is held without editing T30's forbidden-claim constant.** T31 defines its own `T31_FORBIDDEN_TOKEN_CLAIM` and reuses T30's decode-scan helpers, resolving the ownership problem flagged in Phase 3.
- **AC7 handles the boolean `false` case correctly.** The guard checks `typeof account.emailVerified === 'boolean'`, not truthiness.
- **AC9 was measured against `/accounts/me` specifically**, not inherited from T30's `/userinfo` measurement; the hard-coded `WWW-Authenticate` string is documented as measured on auth-service.
- **The no-session guard includes `''`** and explains why, with a control proving a token does issue the request.
- **The mutation harness has baseline and anchor guards**, learning from T29's baseline drift and T30's stale-anchor score inflation.
- **No T30 test block was modified** in the shared file; T31's 50 cases are additive.

---

## Summary

T31's implementation is strong and the test suite is dense. The two highest-priority items are **I1** (the non-reproducible `tsc` gate) and **I2** (the weak `PERMITTED_STATE_SOURCE` bound). Neither is a behavioural bug in `readAccount`, but each is a place where a future change could regress a security-relevant property while the existing tests stay green. I3, I4 and I5 are concrete gaps that should be closed before the task is considered fully hardened.

**Phase 8 complete.**
