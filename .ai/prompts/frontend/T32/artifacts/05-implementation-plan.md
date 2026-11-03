<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan) -->

# frontend · T32 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

One new file, no production code (G7):
**`frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts`**

`.ts`, not `.tsx`: it renders nothing, and T31's phase 11 established the extension as a *structural*
guarantee against JSX, which is worth having in a file whose whole job is scanning source.

## Carried helpers (G6 — defined locally, as every instrument file in this package does)

| Helper | Carried from | Why it is not simplified |
|---|---|---|
| `strip(source)` | T29 → T30 → T31 | Comments removed, **string and template literals KEPT**. The two-regex version this replaced was a real defect Kimi found by reading: deleting from the first `//` to end of line turns `'https://id.example/help'` into `'https:` and a claim name sitting in a literal leaves the source before the scan sees it. |
| `productionFiles()` / `productionSources()` | T30 | `.ts`/`.tsx` under `src/`, excluding `*.test.*`; paths relative to `src/`. |
| `refuseEmpty(items, refusal)` | T22-D14 | An empty tree is **refused**, never reported clean. |
| `hitsOf(targets, pattern)` | T29 | `{ file, hit }` per match, lowercased. |
| `surfaces()` / `watchConsole()` | T30/T31 | the seven surfaces and six console channels, for AC7. |

Each is copied with its reason-comment intact. A helper arriving without the reason it has its shape is how
the next task silently simplifies it back into the defect it was written to avoid.

## The two scans

### AC1 — the decode bound (source-side, primary)

```ts
const TOKEN_DECODE = '\\batob\\b|\\bjwt[Dd]ecode\\b|\\bdecodeJwt\\b|split\\([\'"]\\.[\'"]\\)';
const CLAIM_EITHER_SPELLING = '\\bemail_verified\\b|\\bemailVerified\\b';
```

Three claims, in order:

1. the files whose comment-stripped source matches `TOKEN_DECODE` are **exactly** `['auth/idTokenNonce.ts']`
   — re-asserted here, not inherited from T30 or T31, because every claim below is only as sound as this
   bound;
2. that file names **neither** spelling of the claim;
3. it still *does* decode (the lower bound) — it contains all five forms `DECODE_FORMS` lists, so a row that
   stopped decoding cannot leave budget behind.

`TOKEN_DECODE` is narrow on purpose and the reason is carried from T30's M2: the obvious pattern would be the
five decode forms, but `split` alone matches ordinary string work everywhere and `JSON.parse` matches every
response body in the tree. Breadth is applied only to the **named** file's accounting, where it is safe.

**Both spellings, deliberately** (G1, G2). Inside a decoding file the channel is already known to be a token,
so spelling carries no information there and excluding one would be a gap for free.

### AC2 — the channel rule (tree-wide)

```ts
/** A file may name `email_verified` only if it reads /userinfo through the generated client. */
const USERINFO_READ = "GET('/userinfo'";
```

Every production file whose stripped source matches `email_verified` must also match `USERINFO_READ`.
Measured today: **zero** files match the first, so the rule holds over an empty permitted set.

This is Kimi's option 2 and it replaces the spelling-split instrument the design challenge killed. The rule
is about **provenance**: `oidc.yaml:192` spells the permitted `/userinfo` field `email_verified`, the same as
the prohibited claim, so only the channel can tell them apart. What the dirty-proofs exercise is the **rule**,
not a list — which is the answer to finding 9's "an empty allowlist is decorative".

`readProfile.ts` (T30) is the file that reads `/userinfo` today (`:125`, `api.GET('/userinfo', …)`), so the
rule already has a real file it would permit; it simply does not name the field yet, because the live server
returns only `sub` (O6).

### AC5 — both directions, and the link flow

| Synthetic module | Expected |
|---|---|
| names `email_verified`, no `/userinfo` read | **flagged** |
| names `email_verified` **and** reads `/userinfo` | **silent** |
| a verification **link** module: reads a `token` query param, posts `/accounts/verify`, never names the claim | **silent** |

The third is the guard that keeps this instrument off tasks 48–51's path. T31 had to repair three inherited
instruments that fired on its own commissioned code, and the lesson is to write the false-positive proof
before the scan ships, not after someone else trips on it.

## AC3 — the positive pin

From `contracts/api/auth.yaml`, parsed as YAML rather than grepped:

- `AccountResponse.required` **contains** `emailVerified` (`:661`) and the property is `type: boolean`
  (`:668-669`);
- the status enum contains `PENDING_VERIFICATION` (`:672`);
- `ACCOUNT_STATUSES` (T31) contains it too, so the runtime list and the contract agree;
- `readAccount` returns the field unchanged — asserted through T31's module, not restated.

A prohibition with no stated alternative invites the next author to guess. This is the half that says where
verification state *does* come from.

## AC4 — the semantics pin

| Pinned | Where | Phrase |
|---|---|---|
| the `/userinfo` field is **not** the token claim | `oidc.yaml` `UserInfoResponse.email_verified.description` | contains `NOT the access` and `token-claims.md`, and cites `T32` |
| the claim reports scope authorization | `contracts/api/token-claims.md` | contains `scope authorization` within the `email_verified` row |
| the SPA's scopes | `auth/authorizeRequest.ts` | `REQUESTED_SCOPES` is exactly `['openid','profile','email']` |

`oidc.yaml` is primary (G5): machine-readable, already parsed by other tests, and it is the line that cites
this task. If any of the three drifts, T32's premise has changed and the task should be re-read rather than
patched — the assertion message says so.

## AC7 — the sweep, as a dirty-proof

A synthetic writer puts `email_verified`, `emailVerified` and `PENDING_VERIFICATION` on **each** of the seven
surfaces and into each of the six console channels, and the sweep must report every one. Finding 7 is right
that a "no hits" report from code that writes nothing is indistinguishable from a skipped test — and this
package has been bitten by exactly that, in T30's `document.title` case where `head` and `url` had no
sentinel of their own.

## AC8 — the limitation, in the file

A header block stating that the instrument is a **source-side guard, not a data-flow guard**: it catches a
module reading the claim, and cannot trace a token-sourced value renamed to `isEmailVerified` and passed
along. Enforcement is at the point of token access. Written where the next reader will be standing (G4).

## Mutation harness

`artifacts/07-mutation-harness.py`, carrying every guard the T31 harness ended with: baseline pinned by
failing **file name** with the count as a warning (Kimi A7), anchors matched **exactly once** before any
verdict prints, `BROKEN` failing the run alongside a survivor, and `-t` filtering for the `own` column.

Planned mutants, each a regression this task exists to prevent:

| | Mutant |
|---|---|
| MR01 | `idTokenNonce.ts` reads `email_verified` off the decoded payload — **the subtlest**, and the one the whole task is named for |
| MR02 | the same with the camel spelling |
| MR03 | a **second** file starts decoding a token, breaking AC1's bound |
| MR04 | a module names `email_verified` with no `/userinfo` read (AC2) |
| MR05 | the decode set's lower bound: `idTokenNonce.ts` stops decoding |
| MR06 | `REQUESTED_SCOPES` loses `email`, so the claim's meaning changes under the task |
| MR07 | `AccountResponse.emailVerified` becomes optional in the contract |
| MR08 | the contract's disclaimer is deleted from `oidc.yaml` |
| MR09 | `PENDING_VERIFICATION` leaves the status enum |
| MR10 | a verification flag is cached in `sessionStorage` |
| MR11 | the claim is logged |

MR06–MR09 mutate **contracts**, as T30's MP22/MP23/MP28 did. A pin on a contract line is worth only as much
as the proof that the pin fires.

## Risks carried into implementation

- **Vacuity (OQ-T32-1).** Stated in the file, not hidden: a forward guard with no present consumer.
- **The camel spelling is one character class from the legitimate field.** `email_verified` vs
  `emailVerified` must not match each other; both dirty-proofs drive both spellings.
- **`JSON.parse` is everywhere.** `TOKEN_DECODE` must stay narrow or AC1's bound collapses into noise.

**Phase 5 complete.** One file, two scans, eight criteria, eleven mutants planned.
