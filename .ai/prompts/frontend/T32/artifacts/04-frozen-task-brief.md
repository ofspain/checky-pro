<!-- MODEL: Claude Opus 5 — Phase 4 (Frozen Task Brief) -->

# frontend · T32 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/03-design-challenge.md` (Kimi 2.7) |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

Kimi returned **nine** findings (its own summary says eight; the body has nine). Every cited `file:line` was
read against source before the finding was accepted or rejected.

| | Citation | Verified | Outcome |
|---|---|---|---|
| 1 | the brief's own I2/I5 | **Accurate** | **ACCEPTED — blocker, design changed** |
| 2 | `oidc.yaml:191-197` | **Accurate** | **ACCEPTED — the spelling framing is dropped** |
| 3 | `agents.md:38`, `idTokenNonce.ts` | **Accurate**, and the gap is real | **ACCEPTED — frozen as any-JWT, with a new open question** |
| 4 | the brief's design | **Accurate** | **ACCEPTED as a stated limitation** |
| 5 | `oidc.yaml:194-197` | **Accurate** | **ACCEPTED** |
| 6 | helpers in the T30/T31 file | Accurate on location, **wrong on the convention** | **REJECTED, with evidence** |
| 7 | AC7 | **Accurate** | **ACCEPTED** |
| 8 | AC2 | **Accurate** | **ACCEPTED — AC2 reworded** |
| 9 | the empty allowlist | **Accurate** | **ACCEPTED, resolved by the new design** |

## The blocker was real, and it was mine

Finding 1 is correct and it invalidates the instrument phase 2 proposed. The brief said three things that
cannot all be true: I2 scans the whole tree for the spelling `email_verified`; a `/userinfo`-sourced read is
legitimate; and I5 proves the scan stays silent on a synthetic `/userinfo` read. A `/userinfo` consumer
writes `data.email_verified` — **the same spelling** — because `oidc.yaml:192` names the field that way. So
the scan would flag the legitimate read, and I5 was unimplementable as written.

Finding 2 names the root error precisely: **spelling is not a channel discriminator.** Both the prohibited
token claim and the permitted `/userinfo` field are OIDC artifacts and both spell it `email_verified`. The
camel/snake split I built the instrument on is real but accidental — it distinguishes *which contract*
declares the field, not *where the value came from*. The rule is about **provenance**, so the instrument has
to be about provenance too.

## Frozen decisions

**G1 — the prohibition covers ANY JWT the SPA decodes, not only the access token.**
`agents.md:38` says "the access-token `email_verified` claim", and the named test
`emailVerifiedClaimIsNotAccountState` carries no qualifier. The SPA does hold an id_token and does decode it
(`auth/idTokenNonce.ts`, for `nonce`), and OIDC id_tokens legitimately carry `email_verified`. Decisive
measurement: **`token-claims.md` documents access tokens only** — its three columns are issuance *paths*
(interactive / `client_credentials` / API-key), not artifacts, and its summary line reads "No **access
token** issued by this service ever contains an email address or a name". **Nothing in this repository
documents what the id_token's `email_verified` means here.** An undocumented claim is not a source of truth,
so T32 fails closed, exactly as T31's G2 does for an unrecognised status. See **OQ-T32-3**.

**G2 — the instrument is source-side and channel-discriminating, never spelling-discriminating.**
Kimi's option 2. Two instruments, each implementable and each with both directions proven:

- **I1, the decode bound (the primary claim).** The set of production files that decode a JWT is exactly
  `{auth/idTokenNonce.ts}` — re-asserted here, not inherited from T30 or T31 — and no file in that set names
  `email_verified` or `emailVerified`. This is the implementable form of "never read off a token": a token is
  opaque bytes until something decodes it, so the decode set is where a token-sourced read must originate.
- **I2, the channel rule (tree-wide).** Every production file that names `email_verified` must also read
  `/userinfo` through the generated client. Today **zero** files name it comment-stripped, so the rule holds
  with an empty permitted set — and the rule, not the list, is what the dirty-proofs exercise.

**G3 — AC2 is reworded to what the instrument can actually prove.** Finding 8 is right that
"no component, route, store, hook or copy key is keyed on a token-borne verification flag" overstates a
static scan: a consumer could receive the value through a prop named `isEmailVerified` and no scan would see
it. AC2 now reads: **no production module reads `email_verified` from any source other than `/userinfo`
through the generated client.** The broader consumer claim moves to documentation as the task's stated intent.

**G4 — the limitation is documented in the test file itself, not only in an artifact** (finding 4). The
instrument is a **source-side guard, not a data-flow guard**: it catches a module reading the claim, and it
cannot trace a token-sourced value that has been renamed and passed along. Enforcement is at the point of
token access. Written where the next reader will be standing.

**G5 — AC4 pins `oidc.yaml` primarily** (finding 5). The contract is machine-readable, already parsed by
other tests, and its `/userinfo` `email_verified` description carries the disclaimer and the T32 reference.
`token-claims.md:51` stays as the supporting pin for the "scope authorization" semantics, matched by phrase.
Both are pinned, because if either drifts the task's premise has changed.

**G6 — T32 defines its own scan helpers, as every instrument file in this package does** (finding 6,
**rejected**). The premise is wrong about the convention. Measured:

| File | Local definitions of `strip` / `hitsOf` / `refuseEmpty` / `productionSources` |
|---|---|
| `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | 4 |
| `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | 3 |
| `profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | 4 |

**None is exported anywhere in `src/`.** Self-containment is the established convention and it is deliberate:
these are adversarial instruments, and a shared helper edited for one task silently changes what another
task's scan *means*. T31's phase 11 A2 is the evidence — a licence shared between a task and an instrument
rotted precisely because one side could move without the other. Kimi's option (a), a collectively-owned
`src/testing/scanHelpers.ts`, is a package-wide refactor affecting at least four closed tasks; it is
**outside T32** and is logged as **OQ-T32-4** rather than done here. The duplication cost is real and is
recorded honestly.

**G7 — no production code.** Unchanged from phase 2 and unchallenged by Kimi. One new file:
`frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts`.

## Acceptance criteria, as frozen

| | Criterion | From |
|---|---|---|
| **AC1** | The JWT-decoding file set is exactly `{auth/idTokenNonce.ts}`, re-asserted; no file in it names the claim in either spelling. | R20, G1, G2 |
| **AC2** | No production module names `email_verified` unless that same file reads `/userinfo` through the generated client. | R20, G2, G3 |
| **AC3** | The truthful sources are pinned positively: `AccountResponse.emailVerified` is **required** and `boolean` (`auth.yaml:661,668`), `PENDING_VERIFICATION` is in the status enum (`:672`), and `readAccount` returns the field unchanged. | R20 |
| **AC4** | The semantics are pinned: `oidc.yaml`'s `/userinfo` `email_verified` description still says it is **not** the access token's claim and still cites T32; `token-claims.md` still documents the claim as reflecting scope authorization; `REQUESTED_SCOPES` is exactly `openid profile email`. | G5 |
| **AC5** | The scan is proven in **both** directions: it **fires** on a synthetic module naming the claim with no `/userinfo` read, and stays **silent** on a synthetic module that names it and does read `/userinfo`. It also stays silent on a synthetic verification-**link** module (R3–R5, tasks 48–51), which uses a URL token and never a claim. | G2, finding 9 |
| **AC6** | Every instrument has a dirty-proof and refuses an empty tree (`refuseEmpty`). | package convention |
| **AC7** | The surface sweep is implemented **as a dirty-proof**: a synthetic writer puts `email_verified`, `emailVerified` and `PENDING_VERIFICATION` on each swept surface and the sweep reports every one. No "no hits from code that writes nothing". | L3, L16, finding 7 |
| **AC8** | The source-side limitation (G4) is stated in the test file. | finding 4 |

## Open questions

- **OQ-T32-1** (from phase 1, for the owner) — **no task commissions a signed-in "your email is not verified"
  surface**. T32 is a forward guard and says so plainly.
- **OQ-T32-3** (**new**, from finding 3) — **nothing in this repository documents the id_token's
  `email_verified`.** `token-claims.md` covers access tokens only. T32 fails closed and forbids it, which is
  safe but may be stricter than the auth service intends. A line in `token-claims.md` would settle it.
- **OQ-T32-4** (**new**, from finding 6) — the scan helpers are duplicated across at least four instrument
  files by convention. Whether that convention should be paid down with a shared, collectively-owned module
  is a package-level decision affecting closed tasks.
- **OQ-T32-2** — closed in phase 2 by `oidc.yaml:191-197`.
- **OQ-T31-4** (inherited, not blocking) — `type-fest`'s `/// <reference lib="esnext"/>` lifts the declared
  ES2022 ceiling for all of `src/`.

**Phase 4 complete.** Seven frozen decisions, eight acceptance criteria, one finding rejected with evidence,
two new open questions. The brief Kimi challenged is superseded by this one.
