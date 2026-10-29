# frontend · T29 · Phase 3 — Design Challenge

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 task implementation brief. T29 is verification-only: it must prove that every SPA-observable sign-in failure looks and behaves identically, regardless of the account state that caused it. The brief is strong on timing and structural absence, but the representation of the five account states and the scoping of the timer scan need sharper definition.

---

## F1 · The five account states are not enumerated in the Required Tests

**Severity.** Medium.

**Evidence.**
- The Purpose section names "unknown or missing, locked, suspended and deleted accounts" — four states.
- R30 says "the five account states".
- Required Test 1 says "the observable failure causes, each driven to its sentence and destination, compared as one struct", but does not list the five states.

**Why it matters.** Without an explicit list, a future implementation could omit one state and the test would still pass if it compared whatever causes it did drive. The test must enumerate the states and assert each produces the same outcome.

**Recommended brief amendment.** Add the explicit list to Required Test 1: `unknown`, `missing`, `locked`, `suspended`, `deleted`. For each, specify the SPA-observable trigger (e.g., `invalid_grant` response with a distinguishing claim or `error=` callback parameter), and assert the outcome struct is identical.

**Confidence.** Medium — a vacuity risk.

---

## F2 · How each account state reaches the SPA is not specified

**Severity.** Medium.

**Evidence.**
- The backend refuses all five states with the same `BadCredentialsException("Bad credentials")`.
- The SPA does not call an account-state API, so the state must be inferred from the failure response.
- The brief does not say what fixture represents each state.

**Why it matters.** If all five states look identical at the SPA boundary, the test's only claim is that the SPA is indifferent to indistinguishable inputs. The test should make that explicit, or it should inject the tiniest possible differences at the SPA boundary (e.g., different `error` codes, different `error_description` fragments) and assert the SPA still treats them identically. Otherwise the comparison is between identical inputs, which proves nothing.

**Recommended brief amendment.** For each of the five states, define the fixture. Option A: all five are represented by the same `invalid_grant` response, and the test asserts the SPA cannot distinguish them. Option B: each state is represented by a different `error` parameter or response body field, and the test asserts the SPA still produces the same outcome. Either is defensible, but the choice must be stated.

**Confidence.** Medium — design clarity.

---

## F3 · The observable rendering moment is not defined

**Severity.** Medium.

**Evidence.**
- AC5 says "the sentence is present **without advancing the clock**, for every cause".
- It does not say whether "present" means in `document.body.textContent`, in a live region, or after a specific render phase.

**Why it matters.** A `setTimeout(..., 0)` would make the sentence present after a zero-millisecond advance, but absent at time 0. The test's interpretation of "without advancing" determines whether such a delay is caught. The brief should define the exact assertion.

**Recommended brief amendment.** Specify the assertion: after the failure cause is applied and `act`/`turn` have flushed, with the fake clock at 0, `document.body.textContent` contains `en.signIn.discarded` and the live region is populated.

**Confidence.** Medium — timing precision.

---

## F4 · The "identity path" file set is not defined

**Severity.** Medium.

**Evidence.**
- AC5 requires a timer scan "over the identity path".
- The Constraints section says timers exist in three `api/` modules and must be permitted.
- The brief does not list which files constitute the identity path.

**Why it matters.** A scan scoped to the wrong files either misses a timer in an identity module or falsely fails a permitted timer in a non-identity module. The test needs an explicit file list or glob.

**Recommended brief amendment.** Define the identity path as the files involved in sign-in and callback rendering: `auth/SignInScreen.tsx`, `auth/CallbackScreen.tsx`, `auth/signInNotice.ts`, `auth/authorizeRequest.ts`, `app/routes.tsx`, and any file they import that participates in failure rendering. Document that timers in `api/crossTabRenewal.ts`, `api/sessionBoundary.ts` and `api/rateLimitNotice.ts` are permitted.

**Confidence.** Medium — scan correctness.

---

## F5 · A utility module with a timer imported by the identity path could escape the scan

**Severity.** Low.

**Evidence.**
- The timer scan is scoped to identity path files.
- A utility under `utils/` or `hooks/` with a `setTimeout` imported by `SignInScreen.tsx` would not be in the identity path file list unless imports are traced.

**Why it matters.** The structural claim is that no timer stands between a failure and its sentence. If the timer lives in a helper imported by the identity path, a file-scoped scan misses it.

**Recommended brief amendment.** Either trace imports (heavy) or scan all production files and maintain an allowlist of permitted timers with reasons, as T28 does for permitted hits. The three `api/` timers would be allowlisted, and any new timer would need explicit permission.

**Confidence.** Low — coverage completion.

---

## F6 · AC4's "no styling exists" claim has no control

**Severity.** Low.

**Evidence.**
- AC4 says visual treatment is identical and "trivially: no styling exists".
- There is no test that proves a `className` or `style` difference would be caught.

**Why it matters.** The day styling lands (task 173), this file should fail if different failures receive different classes. A control now would document the intent and make the transition easier.

**Recommended brief amendment.** Add a case that asserts all rendered failure nodes have no `className` and no `style` attribute, with a synthetic control that a `className="locked"` fails. This is a pin, not a claim that styling will never exist.

**Confidence.** Low — future-proofing.

---

## F7 · The account-state vocabulary list could be broader

**Severity.** Low.

**Evidence.**
- The Constraints section lists safe-to-forbid words: `locked`, `lockout`, `suspended`, `deleted`, `deactivat`, `inactive`, `no such`.
- It omits `banned`, `terminated`, `archived`, `expired`, `revoked`, `invalid`, `disabled` (already excluded), `shadow`, `dormant`.

**Why it matters.** A future account state might use one of these words. The scan is enumerative, so omissions are expected, but common synonyms should be included to reduce the chance of a future breach.

**Recommended brief amendment.** Add `banned|terminated|archived|expired|revoked|dormant` to the account-state pattern, with controls showing each fires and that `aria-disabled` and TypeScript `unknown` do not.

**Confidence.** Low — coverage.

---

## F8 · R30's "three token states" are not addressed

**Severity.** Low.

**Evidence.**
- R30 says copy, redirect and treatment are identical "across the five account states and the three token states".
- The brief focuses on account states and says the 401/renewal and 429 surfaces are out because they have no production path.
- It does not say what the three token states are or how they will be verified.

**Why it matters.** T29 owns the sign-in/callback axis. Token-state failures might surface through `createSessionBoundary` or `createRateLimitNotice`, which are currently uncalled. The brief should explicitly defer token states to task 58 or task 136, or define how they are represented on the identity path today.

**Recommended brief amendment.** State that the three token states are out of scope for T29 because the composition root does not mount `sessionBoundary` or `rateLimitNotice`, and cite the task that will own them.

**Confidence.** Low — scope clarity.

---

## F9 · No control proves the fake-timer case would catch a delay

**Severity.** Low.

**Evidence.**
- Required Test 4 says the sentence is present without advancing the clock.
- The Constraints section says fake timers are a vacuity risk and the control is "a synthetic delayed render that the same case catches".
- The Required Tests do not explicitly include this control.

**Why it matters.** Without a control, a case that simply asserts the sentence is present at time 0 would pass even if the code had no timers and the test was not actually using fake timers correctly.

**Recommended brief amendment.** Add Required Test 4b: a synthetic component that renders the sentence inside a `setTimeout(..., 100)` is shown to fail the "present at time 0" assertion, proving the fake timer is active and the assertion can catch a delay.

**Confidence.** Low — instrument verification.

---

## Summary

F1–F4 are the most important because they define what the test is actually comparing and where it looks. F5–F9 are smaller completeness and future-proofing items. No production change is anticipated; all recommendations are additions to the test plan.
