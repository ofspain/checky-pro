<!-- MODEL: Claude Opus 5 — Phase 2 (Task Implementation Brief) -->

# frontend · T32 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## OQ-T32-2 is ANSWERED by the contract, and the contract names this task

Phase 1 left open whether R20's prohibition covers the `/userinfo` copy of `email_verified` as well as the
token's claim, and recommended a reading. **The recommendation was wrong, and `contracts/api/oidc.yaml:191-197`
settles it the other way** — in text that cites T32 by name:

> `email_verified` — `type: boolean`. Whether the email is verified, **as the profile endpoint reports it.
> NOT the access token's `email_verified` claim**, which `contracts/api/token-claims.md` documents as
> reflecting scope authorization rather than verification state (frontend T32).

So the two same-named fields are two different things, and the contract has already said which is which:

| Source | Spelling | Is it verification state? | Available today? |
|---|---|---|---|
| **access token** claim | `email_verified` | **No** — it reports scope authorization | yes, and misleading |
| `GET /userinfo` | `email_verified` | **Yes**, as the profile endpoint reports it | **no** — the live server returns only `sub` (O6) |
| `GET /accounts/me` | `emailVerified` | **Yes** — `AccountResponse`, typed boolean | yes (T31) |

OQ-T32-2 is therefore **closed, by contract, not by my preference**: the prohibition is about the *token*, and
a `/userinfo`-sourced read would be legitimate. My phase 1 recommendation — forbid the name whatever the
channel — would have contradicted a contract that was written with this task in mind. Recorded rather than
quietly dropped, because it changes the shape of AC2's instrument.

## The design

**No production code.** The rule is a prohibition; the truthful source already exists (T31's `readAccount`);
the contract already documents the semantics; and no consumer is commissioned (OQ-T32-1). Writing a module
to "centralise verification state" would invent an unowned abstraction for a UI nobody is building, and L14
forbids inventing routes while L11 forbids inventing copy. T32's whole deliverable is the named test and its
instruments.

**One new file:** `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts`

In `auth/` because the subject is the access token, not the profile read; `.ts` rather than `.tsx` because it
renders nothing — and T31's phase 11 established that a `.ts` extension is a *structural* guarantee against
JSX, which is worth having in a file whose job is scanning.

### The spelling split is the instrument

Measured across the production tree, comment-stripped:

| Spelling | Occurrences in `src/` production code |
|---|---|
| `email_verified` (snake) | **zero** — two mentions exist, both inside comments, which `strip()` removes |
| `emailVerified` (camel) | **one file**, `profile/readAccount.ts:116`, the contract field |

That split is what makes a sharp instrument possible. Snake case is the **token/OIDC** spelling; camel case is
the **contract** spelling. A tree-wide scan for the snake spelling currently has zero legitimate hits, so it
needs no allowlist at all today — only a declared **admission rule** for the day one appears.

### Instruments

| | Instrument | Holds |
|---|---|---|
| **I1** | the decode-bound scan, re-asserted: the files that decode a JWT are exactly `{auth/idTokenNonce.ts}`, and none of them names the claim | AC1 |
| **I2** | **tree-wide**: no production file reads `email_verified` from a token, with an **empty allowlist** carrying the admission rule — a `/userinfo`-sourced read may be added, a token-sourced read never | AC2 |
| **I3** | the positive pin: `AccountResponse.emailVerified` is a required boolean in `auth.yaml`, `PENDING_VERIFICATION` is in `ACCOUNT_STATUSES`, and `readAccount` returns the field unchanged | AC3 |
| **I4** | the semantics pin: `token-claims.md` still documents the claim as scope authorization, and `REQUESTED_SCOPES` is exactly `openid profile email` | AC4 |
| **I5** | the link-flow guard: the scan stays **silent** on a synthetic verification-link module and on a synthetic `/userinfo` read | AC5 |
| **I6** | the surface sweep: no store, console channel or DOM surface holds a verification flag | AC7 |
| **I7** | empty-tree refusal through `refuseEmpty`, and a dirty-proof per instrument | AC6 |

### The hazard this brief has to name: an empty allowlist has a vacuous lower bound

Every allowlist in this package is asserted as **both** an upper and a lower bound — permitted entries must be
proven load-bearing, or the list rots into decoration. **I2's allowlist is empty**, so there is no lower bound
to assert and the usual pattern does not apply. The substitute is I5: prove the scan **fires** on a synthetic
token-sourced read and stays **silent** on a synthetic `/userinfo`-sourced read. Without both halves, I2 is
indistinguishable from a scan that matches nothing, which is the failure mode T22-D14's empty-tree refusal
exists to catch and which Kimi has caught twice in this package already.

## Risks

- **Vacuity (OQ-T32-1), the main one.** No task commissions a signed-in "your email is not verified" surface,
  so AC2 forbids a wrong source for a UI nobody is building. The mitigation is that I2 is a **standing**
  instrument: it fires the day a consumer is written from the wrong source, which is the cheapest moment to
  catch it. It should be stated plainly in the artifact that this is a forward guard, not a present-tense
  proof, so the next reader does not overvalue it.
- **Over-breadth landing on someone else.** Tasks 48–51 build the verification link flow, where a `token`
  query parameter and the word "verify" are entirely legitimate. An instrument that fires on those would be
  an instrument defect in another task's path — exactly what T31 had to repair three times in T27's, T29's and
  T26's scans. I5 exists for this, and the scan must key on **reading a claim off a token**, never on the word
  "verify".
- **The claim is a single underscore away from the legitimate field.** `email_verified` vs `emailVerified`
  differ by one character class. Any pattern must be anchored so one cannot match the other, and the
  dirty-proof must drive both.

## Open questions carried forward

- **OQ-T32-1** — does anything commission a signed-in verification surface? Logged for the owner; T32
  proceeds either way.
- **OQ-T32-2** — **CLOSED** in this phase by `oidc.yaml:191-197`.
- **OQ-T31-4** (inherited, not blocking) — `type-fest`'s `/// <reference lib="esnext"/>` lifts the declared
  ES2022 ceiling for all of `src/`.

**Phase 2 complete.** One new test file, seven instruments, no production code, one open question closed by
the contract and one left for the owner.
