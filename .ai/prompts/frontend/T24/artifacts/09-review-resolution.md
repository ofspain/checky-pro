# frontend · T24 · Phase 9 — Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi 2.7), `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

**Every cited `file:line` in the Phase 8 artifact was checked against source before this gate decided anything.**
Kimi ran static-only again (its environment cannot start `vitest`), so the citations were the thing to verify. All
seven findings cite real lines, and five converge with the self-review: I1↔S1, I2↔S2, I3↔S3, I5↔S5, I7↔S4.

**Seven accepted, none rejected. Three of them change production code**, which is unusual for this gate and is the
honest outcome: two of Kimi's findings (I1, I2) are the two places where the implementation chose a behaviour the
frozen brief had not decided, and both choices were the weaker one. The third change (I4) is accepted in a stronger
form than Kimi proposed — the heuristic is removed rather than documented. The remaining four are a recorded
decision, a Phase 10 scope boundary, and a handoff.

The suite was re-run after the three changes: **1008 passed, 1 failed**, the failure being the one known
environmental case (`lintRejectsHandWrittenBackendFetch.test.ts:118` — this host's `oxlint@1.87.0` prints no summary
line). `tsc --noEmit` exits 0. No inherited assertion moved as a result of these changes, which is itself a signal:
all three are about paths the committed suite does not yet reach, and Phase 10 is where they become observable.

---

## Resolution log

### I1 · A visitor who leaves the callback screen keeps their return target forever — **ACCEPTED, code changed** (High)

Verified: `CallbackScreen.tsx:192` had `if (onScreen.current) navigate(takeReturnTarget(), { replace: true });`,
with the `onScreen` ref cleared in the effect cleanup at `:199-201`. Kimi's reading is exactly right. The guard was
written to answer T24-D7 ("do not move someone who has left"), and it silently acquired a second meaning: do not
*consume* for someone who has left. Those are not the same promise, and L16's is the one that was dropped.

The concrete harm is not abstract. A visitor who abandons the callback mid-exchange has nonetheless completed a
sign-in — the token is held, the three PKCE items are consumed — and the fourth item survives it. The *next*
sign-in in that tab then honours a target from a journey they walked away from.

**Change made** (`CallbackScreen.tsx:190-195`): the take moved above the guard, exactly as recommended.

```tsx
const target = takeReturnTarget();
if (onScreen.current) navigate(target, { replace: true });
```

The comment above it now says which promise each line keeps, so the next reader cannot re-merge them. Kimi's
suggested test — unmount before the exchange resolves, then assert the key is gone after the promise settles — is
Phase 10 requirement **R-I1**.

### I2 · `rememberReturnTarget` will store a value that can never be honoured — **ACCEPTED, code changed** (High)

Verified: `returnTarget.ts:78-85` wrote whatever string it received; `isAllowlistedTarget` was already exported at
`:66-68`. Kimi's distinction is the one the implementation missed: **authority and filtering are not the same
thing.** T24-D3 put the *authority* on the read and that stays true — the route table can change between the write
and the read, so only the read can decide finally. But L3 and L16 are about what may *sit* in storage, not only
about what may be acted on, and the write side was declining to apply a predicate it already had in scope.

**Change made** (`returnTarget.ts:88-89`): one line at the top of the function, same predicate.

```ts
export function rememberReturnTarget(target: string): void {
  if (!isAllowlistedTarget(target)) return;
```

The doc comment now states that a target which could not be honoured is not stored at all and that this is silent,
and records it as **T24-D15**: one rule, checked at both ends, with the read still final. Note the shape this gives
R28's future caller (task 41) — `rememberReturnTarget` is now total *and* lossy, so that task cannot discover
mid-flight that a target it stored was quietly dropped; it will see the fallback, which is the same outcome the read
would have produced anyway. Phase 10 requirement **R-I2**: store a rejected shape, call `takeReturnTarget`, assert
home *because nothing was stored* (the store itself is asserted empty, not just the return value).

### I3 · A discarded callback leaves the target stored, and the decision is unstated — **ACCEPTED as a decision** (Medium)

Verified: `CallbackScreen.tsx:148-197` calls `takeReturnTarget` only in the success handler; every discard path
returns to sign-in without touching the fourth item.

Kimi's read of the behaviour is right and so is its read of the status: correct, but inferred. Recording it as
**T24-D14**:

> **T24-D14.** A discarded callback leaves the return target in storage, deliberately. It is the mirror of T23-D2
> (the three PKCE items are deleted on the consumed path only): the target is consumed by the *navigation that
> honours it*, and a discard performs no navigation, so there is nothing to consume. The visitor retries the
> sign-in and still arrives where they were going — which is the entire point of remembering it. "Single-use" and
> "survives a failed attempt" are not in tension once the use is named: the use is the arrival, not the attempt.

**Change made:** none in code; the decision is recorded here and Phase 10 requirement **R-I3** makes it observable
(a discard leaves the target, and a subsequent successful callback honours it).

### I4 · The parameterized-route exclusion relies on a `:` heuristic — **ACCEPTED, and tightened rather than documented** (Low)

Verified: `returnTarget.ts:51-55` filtered with `!path.includes(':')`; `routes.tsx:43,46` are the two parameterised
routes. Kimi rated this Low on the grounds that no literal-colon route exists today, and recommended documenting
the heuristic.

**The heuristic is real, and it is cheaper to remove than to document.** React Router treats a colon as a dynamic
segment only when the segment *begins* with it — `time/12:30` is a static path in that grammar, and
`path.includes(':')` would have excluded it for a reason that has nothing to do with it. Documenting that would
leave a rule that is right by accident; matching the grammar makes it right on purpose.

**Change made** (`returnTarget.ts:52-58`):

```ts
const parameterised = (path: string) => path.split('/').some((segment) => segment.startsWith(':'));
```

`invoices/:id` and `receipts/:id` are still excluded, for the reason T24-D5 gives. A future literal-colon route
stays legal without anyone revisiting this. Phase 10 requirement **R-I4** is Kimi's suggested pair — `invoices/:id`
is not honoured while `invoices` is — plus a case over the derivation itself, so the grammar rule is asserted and
not merely true.

### I5 · Five acceptance criteria are not verified by the committed suite — **ACCEPTED** (High)

Verified: no file matches `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome*`. The committed suite
proves AC11–AC14 and AC4 structurally; AC1, AC6/AC17, AC15, AC16 and AC7-as-behaviour are unproven.

This is the expected phase boundary, and Kimi's second sentence is the part that binds: **the 17 updated inherited
assertions are not evidence for the allowlist mechanism or the throw-safety contract.** They prove the navigation
happens and where it lands; they say nothing about which targets are legal, and they were edited by this task,
which is precisely why they cannot vouch for it. Phase 12 is barred from counting them toward AC1, AC6, AC15,
AC16 or AC17. Phase 10 requirement **R-I5**: these five are the named test's spine.

### I6 · `takeReturnTarget()` removes before validating, which is correct but worth pinning — **ACCEPTED** (Low)

Verified: `returnTarget.ts:117-124` (now `:128-135`) removes, then validates, with the reason in the comment at
`:122-123` (now `:133-134`). The behaviour is deliberate: a rejected target was still used up by this callback, and
leaving it would hand the next sign-in a target that can neither be honoured nor got rid of.

Kimi is right that a comment is not a constraint. **Change made:** none in code. Phase 10 requirement **R-I6**:
store a non-allowlisted target, assert the return is home *and* the key is gone — one case, two assertions, because
either alone is satisfied by a mutation that breaks the other.

### I7 · T26's carve-out guard must enumerate both constants — **ACCEPTED as a handoff** (Low)

Verified: `authorizeRequest.ts:32-33` publishes `AUTHORIZE_STORAGE_KEYS` so "task 26's carve-out guard enumerates
the same keys"; `returnTarget.ts:39` is the new fourth key. Kimi's inference is correct and its recommendation of
*no code change* is correct for the reason it gives.

**Handoff to task 26, recorded here so it is not inferred from a comment:** the storage carve-out is now **two**
constants in **two** modules —

- `frontend/src/auth/authorizeRequest.ts` → `AUTHORIZE_STORAGE_KEYS` (`state`, `nonce`, `code_verifier`)
- `frontend/src/auth/returnTarget.ts` → `RETURN_TARGET_KEY` (`themistra.auth.return_to`)

— and a guard that enumerates only the first will report the return target as a violation of the carve-out that
names it. **They are deliberately not merged.** T24-D4 separates them because the three PKCE items are deleted for
having been consumed by the exchange and the fourth for having been read by the navigation; putting the fourth key
into `authorizeRequest.ts` would place it inside the module whose `consumeAuthorizeState` must not reach it. The
cost of that separation is this handoff, and it is the cheaper cost.

---

## What changed in production source

| File | Change | Finding |
|---|---|---|
| `auth/CallbackScreen.tsx` | `takeReturnTarget()` hoisted above the `onScreen.current` guard; the comment now separates T24-D7's promise from L16's | I1 |
| `auth/returnTarget.ts` | `rememberReturnTarget` refuses a non-allowlisted target before writing (T24-D15); doc comment states it | I2 |
| `auth/returnTarget.ts` | parameterised-route exclusion matches react-router's grammar (`segment.startsWith(':')`) instead of any colon | I4 |

No test file was touched at this gate. `tsc --noEmit` exits 0; the suite is 1008 passed with the one known
environmental failure.

## Carried into Phase 10

| Requirement | From | AC |
|---|---|---|
| **R-I1** | I1 | unmount before the exchange resolves → no navigation, target consumed anyway |
| **R-I2** | I2 | a rejected target is never stored; the store, not just the return value, is asserted |
| **R-I3** | I3 | a discard leaves the target; the retry honours it (T24-D14) |
| **R-I4** | I4 | `invoices/:id` refused, `invoices` honoured, and the derivation rule asserted |
| **R-I5** | I5 | AC1, AC6/AC17, AC15, AC16, AC7-as-behaviour — the named test's spine |
| **R-I6** | I6 | a rejected target is still consumed: home *and* the key gone |

T25's rejection table stays `it.todo` rows in the same file, per F7.
