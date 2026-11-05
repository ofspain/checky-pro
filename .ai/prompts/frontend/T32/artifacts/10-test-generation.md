<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation) -->

# frontend · T32 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` |
| **Requirement** | R20 (third clause) |

T32 is a test-only task (G7, frozen phase 4), so this phase has no production code to cover. Its work was
to audit the suite against the frozen brief clause by clause and close whatever the earlier phases had
left uninstrumented. **It found one gap**, in AC3.

## What this phase added

**AC3's third clause had no instrument.** The clause reads: `AccountResponse.emailVerified` is required and
`boolean`, `PENDING_VERIFICATION` is in the status enum, **and `readAccount` returns the field unchanged**.
Phases 6–9 pinned the two contract halves and never pinned the third. That is the dangerous half: the
contract can be perfect while a module between it and the caller replaces the answer, which is this task's
failure direction in its purest form — unverified reported as verified.

It is pinned at **source**, not driven: `readAccount.ts` must validate the field as a boolean
(`typeof account.emailVerified === 'boolean'`) and must `return data;`, with no construction
(`emailVerified:`), assignment (`emailVerified =`), or coercion (`Boolean(`, `!!`) anywhere in the module.
Driving it instead would mean duplicating T31's transport harness to re-prove transport behaviour T31
owns — a far larger duplication than the scan helpers already logged as OQ-T32-4.

**`MR14`** was added so the new case is proven to fire rather than asserted to: it rewrites the return as
`{ ...data, emailVerified: true }` and dies as T32's **own** kill.

One defect was found by running, and it is worth recording because it is the same class of error twice in
one task: the forbidden-token list was written as substrings, and `emailVerified =` is a **prefix of the
`=== 'boolean'` guard the same case requires**. The case failed on the very code it was written to protect.
Now regexes, with `/emailVerified\s*=(?!=)/`. T32 has now hit a self-collision in a scan twice — once in
phase 7 (`document.title` deleted by `head.innerHTML`), once here.

## Test manifest

One file, `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts`, **27 cases**. AC5 (both-direction
proof) and AC6 (dirty-proof plus empty-tree refusal) are cross-cutting by design and are satisfied by the
`CONTROL:` cases inside the other blocks rather than by a block of their own.

| # | Case | Verifies |
|---|---|---|
| 1 | is exactly one file, and it is the nonce reader | AC1 · R20 · G1, G2 |
| 2 | reads NEITHER spelling of the claim, in the one file that decodes | AC1 · R20 |
| 3 | still DECODES, which is the allowlist row lower bound | AC1 · AC6 |
| 4 | CONTROL: the scan FIRES when a decoding file reads the claim, in either spelling | AC5 · AC6 |
| 5 | CONTROL: the two spellings do not match each other | AC5 |
| 6 | CONTROL: a scan that reads nothing is refused, not reported clean | AC6 · T22-D14 |
| 7 | CONTROL: a formatter-spaced split is still recognised as a decode | AC1 · phase 8 I2 |
| 8 | CONTROL: a decoder named only `decode` is NOT recognised — a measured gap | AC1 limit · phase 8 I2 |
| 9 | no production file names the claim off the `/userinfo` channel | AC2 · R20 · G2, G3 |
| 10 | and today nothing names it at all, the stronger statement while true | AC2 |
| 11 | CONTROL: the rule FIRES on a mention with no `/userinfo` read | AC5 |
| 12 | CONTROL: the rule is SILENT on a mention in a file that does read `/userinfo` | AC5 · the admission rule |
| 13 | CONTROL: the rule is SILENT on the verification LINK flow | AC5 · R3–R5, tasks 48–51 |
| 14 | CONTROL: the channel is recognised in every call shape a formatter can produce | AC2 · phase 8 I1 |
| 15 | CONTROL: a file cannot claim the channel with a STRING containing the call | AC2 · phase 8 I3 |
| 16 | CONTROL: a comment cannot claim the channel either | AC2 · phase 8 I3 |
| 17 | CONTROL: a call through a path CONSTANT is not recognised — the limit of a source scan | AC2 limit |
| 18 | `AccountResponse` requires `emailVerified` as a boolean | AC3 · `auth.yaml:661,668` |
| 19 | `PENDING_VERIFICATION` is in the contract enum AND in the runtime list T31 checks against | AC3 · `auth.yaml:672` |
| 20 | **the reader hands the truthful field through UNCHANGED** | **AC3 third clause — new in this phase** |
| 21 | the `/userinfo` field is documented as NOT the token claim, in the line citing this task | AC3, AC4 · G5 |
| 22 | `token-claims.md` still documents the claim as reflecting scope authorization | AC4 |
| 23 | the SPA still requests the `email` scope, which makes the claim read true for us | AC4 |
| 24 | reports a flag written to EVERY one of the seven surfaces | AC7 · L3, L16 |
| 25 | reports a flag logged to EVERY one of the six console channels | AC7 |
| 26 | and the surfaces are clean when nothing has written to them | AC7 · AC6 |
| 27 | says in its own header that it is a source-side guard and a forward guard | AC8 · G4 |

**Every acceptance criterion is covered:** AC1 (1–3, 7–8), AC2 (9–10, 14–17), AC3 (18–21), AC4 (21–23),
AC5 (4–5, 11–13), AC6 (3, 6, 26), AC7 (24–26), AC8 (27).

## Named tests (`package.md` §8)

| Named test | File | Owner |
|---|---|---|
| `emailVerifiedClaimIsNotAccountState` | `src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | **T32** |
| `profileIsReadFromUserinfoAndAccountStatusFromMe` | `src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | T30, extended by T31 |
| `generatedClientCompiles` | `src/api/generatedClientCompiles.test.ts` | earlier task |
| `authorizeRequestsExactlyTheDeclaredScopes` | `src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | earlier task |

The three inherited files are **not** duplicated here. T32 reads `REQUESTED_SCOPES` from
`authorizeRequest.ts` directly (case 23) rather than re-proving the scope test, and reads
`ACCOUNT_STATUSES` from `profile/accountStatus.ts` (case 19) rather than re-proving T31's list — the same
module T31 created so that a list could be licensed as a FILE.

## Testing conventions

`agents.md`'s JUnit, Testcontainers and fixed-`Clock` conventions are the **backend** conventions and do
not apply: this is the SPA package, whose convention is Vitest with jsdom. No container is needed and no
clock is read — every instrument in this file is a source or contract scan, except the AC7 surface sweep,
which writes to jsdom surfaces and resets them in `afterEach` (including the URL, which must reset there
and not at the end of the dirtying case — a case that fails before its own cleanup otherwise leaks
`?email_verified=true` into the next one, which is what happened the first time this file ran).

## Gates

| Gate | Result |
|---|---|
| T32 cases | **27 passed** (20 at phase 7 → 26 at phase 9 → 27 here) |
| Whole suite | **1535 passed / 1 failed of 1536** — the known `oxlint` environmental case |
| Mutation | **14/14 killed, 0 survivors, 0 broken**, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| Production code | none changed |

**Phase 10 complete.**
