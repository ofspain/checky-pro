<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T32 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | all prior artifacts (00–11) |
| **Produces** | `artifacts/12-specification-verification.md` |

## What the spec asks of this task

`tasks.md:60` — "**email_verified is not account state.** The access-token `email_verified` claim never
drives verification UI. Verifies `emailVerifiedClaimIsNotAccountState`. Cites R20."

`requirements.md:66-67` — R20's third clause: the SPA "**SHALL NOT** read PII or verification state from
the access token."

`agents.md:37-38` (LOCKED, `[ALL]`) — "The requested scopes are `openid profile email`. Account status is
read from `GET /accounts/me`. The access-token `email_verified` claim is never treated as account
verification state."

## Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R20 cl. 3 — verification state never read from the access token | **yes**, as a source-side prohibition | `emailVerifiedClaimIsNotAccountState.test.ts:427` (tree-wide, off-channel), `:338` (decode bound) | 29 cases | no | **D1** (scope extended to any JWT) |
| R20 cl. 3 — the decode channel is bounded | yes | `:338` decoders are exactly `{auth/idTokenNonce.ts}`; `:343` that file reads neither spelling | yes | no | no |
| R20 cl. 3 — the permitted channel is `/userinfo`, by provenance not spelling | yes | `:427` + `readsUserinfo` at `:238`; `oidc.yaml:192-197` | yes | no | **D2** (channel rule is file-level) |
| R20 cl. 1–2 — profile from `/userinfo`, status from `GET /accounts/me` | **T30/T31 own this**; T32 pins the sources positively | `:565` `auth.yaml:661,668`; `:571` `:672`; `:577` the reader passes the field through | yes | no | no |
| `agents.md:37` — scopes are `openid profile email` | yes, pinned as the task's premise | `:649` against `authorizeRequest.ts` `REQUESTED_SCOPES` | yes | no | no |
| `agents.md:38` — the claim is never treated as account verification state | yes | the whole file; `:739` asserts the limitation is disclosed | yes | no | **D1** |
| `tasks.md:60` — "never drives verification UI" | **partially — by construction, not by observation** | `:692` surface sweep as a dirty-proof; no commissioned consumer exists | yes | **see OQ-T32-1** | **D3** |
| `package.md` §8 — `emailVerifiedClaimIsNotAccountState` exists | yes | `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | — | no | no |
| `package.md` §8 — the other three named tests | yes, pre-existing; not duplicated | `profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`, `generatedClientCompiles.test.ts`, `authorizeRequestsExactlyTheDeclaredScopes.test.ts` | — | no | no |
| `contracts/api/token-claims.md` — the claim's semantics | yes, pinned | `:639` | yes | no | no |
| `contracts/api/oidc.yaml` — the permitted field is documented as not the claim | yes, pinned wrap/case-tolerantly | `:619` against `oidc.yaml:195-197` | yes | no | no |

## Deviations

**D1 — the prohibition covers ANY JWT the SPA decodes, not only the access token.** `agents.md:38` says
"the access-token `email_verified` claim". The SPA also decodes an **id_token** (`auth/idTokenNonce.ts`),
and OIDC id_tokens legitimately carry `email_verified`. Nothing in the repo documents what this deployment's
id_token claim means, so T32 fails closed and forbids reading verification state off either. Frozen as G1 in
phase 4, raised as **OQ-T32-3**. This is stricter than the letter of the rule and consistent with its
intent; it cannot mask a defect, only reject a shape nobody has asked for.

**D2 — AC2's channel rule is file-level, not a value flow.** It permits a FILE that calls `/userinfo`, not a
particular value's path through that file. A file that legitimately reads the channel could also read the
claim off a token and pass AC2. AC1's decode bound is what closes that, which is why the two are a pair.
Stated in the file header and in `09-review-resolution.md` (Kimi I7).

**D3 — "never drives verification UI" is proven as a source prohibition, not as an observation of UI.** No
task commissions a signed-in verification surface: tasks 48–51 build the verification **link** flow, where a
visitor arrives with a URL token and no session, and nothing in `tasks.md` reads `PENDING_VERIFICATION` to
tell a signed-in user anything. So the honest claim is "no production module reads the claim", and
"no UI is driven by it" rests on that plus review. Frozen as G4; disclosed in the file and asserted by
`:739` so it cannot be deleted in silence. **OQ-T32-1.**

**D4 — AC3's third clause is held by source inspection, not by invoking `readAccount`.** Kimi's phase 11 A5.
The case reads `readAccount.ts` and requires the field to be validated as a boolean and returned as part of
the body it validated, with no construction, assignment, return of a fresh object, or coercion on any line
that mentions the field. It does **not** call the function. Driving it would duplicate T31's transport
harness to re-prove behaviour T31 owns.

**D5 — the channel detector and the decode bound both fail toward false positives, deliberately.** A call
through a path constant, bracket notation, or a destructured `GET` is not recognised, and a file whose only
`split('.')` sits inside a string literal counts as a decoder. All are measured by controls (`:535`, `:545`)
and by `MR15`, and all are left open: a scan that fails loudly on a legitimate shape can be argued with,
one that passes quietly cannot.

**D6 — no mutant covers a camelCase read outside the decoding file.** A consumer reading `emailVerified`
from `readAccount` is legitimate, so such a mutant would be an intended survivor, and a harness that
reports a survivor it means to keep teaches the reader to ignore the survivor list. Documented instead.

**D7 — the phase 11 A4 repair is measured by controls, not by a mutant.** A mutant can only record a fix
that turns a survivor into a kill; that repair turned a false **positive** into a pass. Recorded in the
harness: it measures an instrument's strength, and only a control measures its precision.

## LOCKED decisions

None violated. `agents.md:37-38` is satisfied and extended (D1). The enumeration-safety block at
`agents.md:40-42` is not engaged — T32 adds no copy, no redirect target and no error styling, and T31's
`identityErrorsIndistinguishableAcrossAccountStates` still passes in the whole-suite run. No file under
`spec/` was modified.

## The approving principal engineer's answers

**(1) Is the task fully complete?** Yes, for what the task commissions. It installs the prohibition R20's
third clause asks for, in both the form the spec states (the access token) and the stricter form the code
requires (any JWT). It does not build a verification surface, because no task commissions one — that is
D3/OQ-T32-1, a scope fact rather than unfinished work.

**(2) Does it satisfy every acceptance criterion?** Yes. AC1 `:338`–`:399`, AC2 `:427`–`:545`, AC3
`:565`–`:619`, AC4 `:639`–`:649`, AC5 by the `CONTROL:` cases, AC6 by the dirty-proofs plus `refuseEmpty`,
AC7 `:692`–`:723` as a dirty-proof, AC8 `:739`. AC3's third clause was uninstrumented until phase 10 found
it; AC2's instrument was rebuilt twice, at phases 9 and 11, after Kimi found it wrong in three ways.

**(3) Does it violate any LOCKED decision?** No.

**(4) Remaining risks?**

- **The channel detector is hand-written and load-bearing.** `literalMask` decides which files AC2 will
  license, and a bug in it licenses files silently. It is now stack-based with nesting and brace counting,
  and exercised in both directions by `:485`, `:500`, `:509`. This is the single place a future reader
  should look first.
- **Scan-helper duplication (OQ-T32-4)** has now been raised by three independent reviews — phase 3,
  phase 8 I4, phase 11 A1. It is not actioned here because moving the helpers would change what **four
  closed tasks'** adversarial scans mean, and a closed instrument is evidence signed off on its exact
  shape. It needs its own task and the owner's decision, not a side effect of T32.
- **A forward guard decays if its consumer never arrives.** If no signed-in verification surface is ever
  built, T32's standing value is the contract pins and the decode bound, not the consumer rule.
- **`agents.md` and the id_token (OQ-T32-3)** remain undocumented; T32's strictness is a choice, not a
  reading of the spec.
- **One environmental suite failure persists**, `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, on
  this host only: `oxlint@1.87.0` prints no summary line for the assertion that parses it. Pre-existing,
  unrelated to T32, pinned by name in the harness baseline so it cannot mask a real failure.

## Gates

| Gate | Result |
|---|---|
| T32 cases | **29 passed** (20 → 26 → 27 → 29) |
| Whole suite | **1537 passed / 1 failed of 1538** — the known environmental case |
| Mutation | **15/15 killed, 0 survivors, 0 broken**, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| Production code | none changed — T32 is test-only (G7) |
| Kimi findings | 9 (phase 3) + 10 (phase 8) + 9 (phase 11) = **28**, all citations verified against source |

**VERDICT: PASS** — R20's third clause is instrumented as a provenance-based prohibition with every
acceptance criterion covered, 15/15 mutants killed, and seven deviations disclosed rather than discovered.
