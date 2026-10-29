# frontend · T23 · Phase 9 — Review Resolution

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Human Approval gate |
| **Consumes** | `artifacts/08-independent-review.md` (Kimi 2.7), `artifacts/07-self-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

**Every cited `file:line` in the Phase 8 artifact was checked against source before this gate decided anything.**
Kimi ran static-only (its environment cannot start `vitest`), so the citations were the thing to verify. All nine
findings cite real lines, and the two reviews agree on the substance: I1↔S4, I2↔S5, I3↔S2, I4↔S3, I5↔S1, I6↔S6,
I7↔S7. One factual correction and one premise correction are recorded below.

**Nine accepted, none rejected, and the gate makes no production change.** That is the outcome, not a shortcut:
seven of the nine are verification requirements that belong to the named test (Phase 10), two are paperwork that
belongs here, and the only one proposing a production change — I1's alternative of spelling the three keys at the
call site — is declined in favour of the pin both reviews recommended first.

---

## Resolution log

### I1 · `consumeAuthorizeState()` would delete task 24's key — **ACCEPTED** (High)

Verified: `authorizeRequest.ts:121` iterates `Object.values(AUTHORIZE_STORAGE_KEYS)`; the constant at `:35-39`
holds exactly the three; the doc comment at `:104-105` says the fourth item is task 24's; nothing enforces it.

**Accepted as the pin, not as the rewrite.** Phase 10's named test asserts
`Object.keys(AUTHORIZE_STORAGE_KEYS)` is exactly `['state', 'nonce', 'codeVerifier']`, with a message that tells
task 24 what to decide. **Kimi's stronger alternative — enumerate the three keys by name inside the function — is
declined**, because it trades this trap for the opposite one: T23-D3 chose iteration so that a fourth *single-use*
key cannot be silently left behind, and three hand-written removals are exactly the second spelling of the keys
that T22's source scan exists to forbid. The pin turns a silent inheritance into a failing test, which is what was
actually missing.

**Change made:** none here. Phase 10 requirement **R-I1**, mapped to AC9.

### I2 · The guard swallows every error shape — **ACCEPTED** (Medium)

Verified: `authorizeRequest.ts:122-126`, bare `catch {}`. Both reviews agree the swallowing is right for the
visitor (T23-D4) and that the gap is in the measurement.

**Change made:** none here. Phase 10 requirement **R-I2**: the throwing-store case asserts, with a
`Storage.prototype.removeItem` spy, that **three** attempts were made and all three threw — so "denied" is
distinguishable from "never tried". Mapped to AC5 and AC8.

### I3 · AC14's literal wording is violated by two comment edits — **ACCEPTED** (High, paperwork)

Verified: `codeExchange.ts:95-103` and `idTokenNonce.ts:20-22` were edited; comments only; `tsc` exit 0 and the
three purity cases are the evidence nothing else moved. The edits were required by T23-D8/T23-D9, which say an
inherited statement is updated with its reason restated — and both files stated, in production, that deleting the
three was still task 23's.

**Change made here (this is the gate's own business):**

| **T23-D14** | *(I3/S2)* **AC14 is amended** to: *"no production **behaviour** change outside `authorizeRequest.ts` and `CallbackScreen.tsx`; head-comment corrections in the two pure readers are required by T23-D8/D9 and are not a behaviour change."* The two readers stay pure, which three inherited cases assert. |

### I4 · A T16 test file was changed inside T23's commit — **ACCEPTED, with a factual correction** (Medium)

**Correction:** Kimi says the file "gained `vi.resetModules()` in three places" and cites `:145, 453, 527`. The
three lines are right, but **`:145` was already there** — it is the file's `afterEach`, and it is precisely the
reason the flake existed: the mock's effect depended on a *previous* test having cleared the registry. T23 added
two, at `:453` and `:527`. The distinction matters, because the pre-existing line is the cause, not part of the fix.

**Accepted: the fix stays in T23's commit.** Reverting it restores a one-in-five order-dependent flake to a suite
whose exit code is the gate for every later task, and the fix changes nothing either case asserts (31/31 in
isolation before and after; six consecutive clean full-suite runs since). It is recorded rather than silent:

| **T23-D15** | *(I4/S3)* **An out-of-scope robustness fix is accepted into T23's commit** and named in the PR notes with its measurements: two `vi.resetModules()` calls in the T16 sign-in test's `doMock` helpers. The flake's cause — a statically imported `SignInScreen` plus a registry reset that lived only in `afterEach` — is written at the first site. |

### I5 · Five acceptance criteria are not verified — **ACCEPTED** (High, status)

Verified: no file matches `stateSurvivesRedirectAndIsConsumedOnce*`. AC5, AC6, AC7, AC8 and AC10 are unproven by
the committed suite.

**Change made:** none here. These five are Phase 10's spine, and **Phase 12 may not accept the inverted inherited
assertions as evidence for any of them** — they measure the consequence of the deletion, not its ordering, its
guard, or its arity. Recorded as requirement **R-I5**.

### I6 · A replayed callback clears a held token without a server round trip — **ACCEPTED as a handoff** (Medium)

Verified: `CallbackScreen.tsx:78` (`clearAccessToken()` inside `discard()`), `:122-124` (a second arrival with no
stored state discards). The outcome is unchanged from before T23 — a replay previously re-exchanged, got
`400 invalid_grant` (T20 measured it) and discarded anyway — so this is not a regression; what changed is that the
same end state is now reached locally and instantly. In a real browser it is largely moot, because reopening the
callback URL is a full page load that loses the in-memory token regardless; it bites only on a router-only
navigation back to the callback route.

| **T23-D16** | *(I6/S6)* **Recorded handoff to task 24**, which owns the post-login destination: navigate away from `/app/callback` once the token is held, so the route is not reachable by back/forward navigation while a token is held. T23 changes nothing here — the discard's `clearAccessToken()` is R22's requirement and T17's design. |

### I7 · The success path still ends on the holding screen — **ACCEPTED, already owned** (Low)

Verified: `CallbackScreen.tsx:153-170` holds the token, consumes the three, and does not navigate. Kimi's
attribution is correct — **T22-D9** already records the handoff to task 24 for removing the callback query
(`code`, `state`, `error`, `error_description`) before the post-login screen renders, and task 24 owns the
destination.

**Change made:** none. Named so Phase 12 does not read "the callback flow is complete" into a green T23.

### I8 · The `Object.values` iteration assumes insertion order — **ACCEPTED in disposition, premise corrected** (Low)

Kimi's disposition is right — no production change — but its reason understates the guarantee. **This is specified,
not incidental:** `OrdinaryOwnPropertyKeys` orders integer-index keys ascending and *all other string keys in
property-creation order*, and ES2017 defines `Object.values` on it. `state`, `nonce` and `codeVerifier` are
non-integer string keys, so the order is guaranteed by the language, not "in practice". There is no fragility here
to mitigate.

`expectAuthorizeStateConsumed` (`tokensAreNeverWrittenToAnyStorage.test.tsx:360-368`) therefore asserts the order
because it is **observable and cheap documentation of the one call site's behaviour**, not because it guards
unspecified behaviour. Correctness does not depend on the order at all: the three removals are independent.

### I9 · No direct isolation test for `consumeAuthorizeState()` — **ACCEPTED** (Low)

Verified: the function is exported and exercised only through the screen flow.

**Change made:** none here. Phase 10 requirement **R-I9**: direct cases in the named test — seed three, call, store
empty; call again, nothing thrown and nothing else removed (idempotence); and the guard case from R-I2. These are
the cheapest home for R-I1's key-set pin as well, which is why I1, I2 and I9 converge on one small describe block.

---

## What Phase 10 inherits from this gate

| Req | From | Assertion | AC |
|---|---|---|---|
| **R-I1** | I1/S4 | `Object.keys(AUTHORIZE_STORAGE_KEYS)` is exactly the three, with a message naming task 24's decision | AC9 |
| **R-I2** | I2/S5 | storage-denied: three `removeItem` attempts made, all threw, all three items still present, visitor's outcome unchanged | AC5, AC8 |
| **R-I5** | I5/S1 | AC5, AC6, AC7, AC8, AC10 are proven in the named test; no inherited assertion substitutes for them | all five |
| **R-I9** | I9 | direct cases: consumption, idempotence, the guard | AC5, AC8, AC9 |

Plus, unchanged from the frozen brief: AC6's ordering is **behavioural** (`getItem`/`removeItem` ordering, and a
mutant that moves the call above the exchange must die), AC7 counts **one** deletion under StrictMode and asserts
the final route is not the discard route, and T23-D13's presence control precedes every emptiness assertion.

## Decisions added at this gate

**T23-D14** (AC14 amended to behaviour) · **T23-D15** (the T16 flake fix accepted and named) ·
**T23-D16** (handoff to task 24: navigate off `/app/callback` once the token is held).

## Still unowned by any task, carried forward

The composition root (`mountShell.tsx`) is wired by nothing; silent re-authentication has no task; no task owns a
real-browser end-to-end check, which is the only place IndexedDB, Cache Storage and cookies beyond
`document.cookie` can be proven.
