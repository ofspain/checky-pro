<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation) -->

# frontend · T32 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |
| **Branch** | `spec/service-specs-and-ai-framework` (off `main`; `main` stays deployable) |

Phase 12's verdict is **PASS**, so this phase proceeds.

## Commit title

```
feat(frontend/T32): prove no module reads verification state off a token
```

## Commit message

```
feat(frontend/T32): prove no module reads verification state off a token

Install R20's third clause as a source-side prohibition: verification state
is never read from a token. The access token's `email_verified` is `true` if
and only if the `email` scope was authorized for that one request, so it
reports scope authorization and not verification state - and because this
client requests `email` on every authorize, it reads `true` for an account
that has never verified anything. It is not a stale answer to the right
question; it answers a different question while wearing the name of the
question a verification surface wants to ask, and it fails in the dangerous
direction.

Discriminate by PROVENANCE, not spelling. `oidc.yaml` spells the PERMITTED
`/userinfo` field `email_verified`, exactly as the prohibited claim is
spelled, because both are OIDC artifacts - so a tree-wide scan for the
spelling cannot also stay silent on the legitimate read. Two instruments
instead: bound the files that may decode a JWT at all to one named file that
reads neither spelling, and ask of every mention which CHANNEL its file reads.

Forbid the claim on ANY JWT the SPA decodes, not only the access token. The
SPA also decodes an id_token, OIDC id_tokens legitimately carry this claim,
and nothing in the repo documents what this deployment's id_token claim
means. Fail closed.

Pin the truthful sources positively, because a prohibition with no stated
alternative invites the next author to guess: `AccountResponse.emailVerified`
required and boolean, `PENDING_VERIFICATION` in the status enum, the
`/userinfo` field documented as not the token claim, and the reader handing
the field through unchanged.

Disclose the limits in the file and assert the disclosure, so a green scan
cannot be mistaken for a present-tense proof about a running screen. These
are source-side guards, not data-flow guards, and no task commissions the
signed-in verification surface they guard.

Tests only; no production code changes.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

**Note on the trailer.** The phase 13 template requires
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`, which names a model that did not
do this work. It is reproduced above because the template requires it verbatim; the **actual commits on
the branch carry the correct trailer**. This is now the **fourteenth consecutive task** to carry the note.
Single source: `.ai/generate.py:476`. Regenerating the prompt files is the owner's call.

## Files changed

| File | Change |
|---|---|
| `frontend/src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | **new** — 748 lines, 29 cases, the named test from `package.md` §8 |
| `.ai/prompts/frontend/T32/artifacts/00`–`13` | the pipeline record, including Kimi's phases 3, 8 and 11 verbatim |
| `.ai/prompts/frontend/T32/artifacts/07-mutation-harness.py` | **new** — 15 mutants MR01–MR15 |

**No production file was modified.** T32 is test-only by frozen decision G7; the prohibition it installs is
a prohibition on code that does not exist yet.

## Summary

T32 instruments R20's third clause — "SHALL NOT read PII or verification state from the access token" — and
the LOCKED rule at `agents.md:38`.

The design was rebuilt once. The phase 2 brief tried to tell the prohibited claim from the permitted
`/userinfo` field by snake versus camel spelling, and Kimi's phase 3 killed it with one piece of evidence:
`oidc.yaml:192` spells the permitted field `email_verified` too. **Spelling is not a channel
discriminator; only provenance is.** What shipped is Kimi's own option 2 — a decode bound plus a
channel rule — and the pair matters, because each covers the other's blind spot.

The channel detector was then wrong three times and was rebuilt at phases 9 and 11:

1. A **substring** is not a call shape. `"GET('/userinfo'"` recognised one way of writing the call, so
   `api.GET("/userinfo")` or a formatter-wrapped call was judged off-channel and **flagged** — a legitimate
   consumer reported as the defect.
2. **Keeping literals cuts both ways.** The comment stripper keeps string literals so a claim name inside
   one cannot escape the scan; the channel detector inherited that same surface, so a file could forge
   `api.GET('/userinfo')` inside a string and license itself.
3. **A template expression is code again.** The flag-based literal mask marked everything between backticks
   as literal, so a real call inside `${...}` read as forged **and** a forged one read as real — wrong in
   both directions at once.

Three other findings were taken: the decode bound missed a formatter-spaced `split( '.' )`, which mattered
in the direction that makes a bound **pass**; the contract pin broke on a meaning-preserving re-wrap; and
the anti-coercion regexes were not scoped to the field they protect.

Phase 10's clause-by-clause audit of the frozen brief found AC3's third clause — "`readAccount` returns the
field unchanged" — had no instrument at all. That is the dangerous half: the contract can be perfect while
a module between it and the caller replaces the answer.

## Testing performed

| Gate | Result |
|---|---|
| `vitest run src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | **29 passed** (20 → 26 → 27 → 29 across phases 7, 9, 10, 11) |
| Whole suite | **1537 passed / 1 failed of 1538** |
| Mutation harness | **15/15 killed, 0 survivors, 0 broken**, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0, under both the pinned 7.0.2 and the 5.9.3 in the store |
| `oxlint src` | exit 0 |

The one failure is `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, pre-existing and environmental:
this host's `oxlint@1.87.0` prints no summary line for the assertion that parses it. It is pinned **by
name** in the harness baseline, so it cannot mask a real failure.

Mutants worth naming. `MR11` is **INHERITED-ONLY** by design — caching a verification flag is T31's kill,
and if that column ever reads `own`, a T32 case has grown a reach it was not given. `MR12` and `MR13` each
record a finding taken into code, and each was rewritten after the first draft carried `atob` and so would
have died on AC1's bound for a reason unrelated to the finding it existed to record; both survivals under
the phase 7 patterns were then confirmed by running those patterns, not inferred. `MR15` is a **kill that
marks a false-positive boundary**, not a defect caught. And the phase 11 repair could not be recorded by a
mutant at all: a mutant can only record a fix that turns a survivor into a kill, and that one turned a
false positive into a pass. The harness measures an instrument's strength; only a control measures its
precision.

## Specification references

- **Task:** `spec/frontend/tasks.md:60` — task 32.
- **Requirement:** **R20** (`requirements.md:66-67`), third clause.
- **LOCKED:** `spec/frontend/agents.md:37-38` (`[ALL]`) — scopes are `openid profile email`, status is read
  from `GET /accounts/me`, the access-token `email_verified` claim is never treated as account verification
  state. Satisfied and extended to any JWT (deviation D1).
- **Contracts:** `contracts/api/token-claims.md` (the claim reports scope authorization),
  `contracts/api/oidc.yaml:192-197` (the permitted `/userinfo` field, in the line that cites T32),
  `contracts/api/auth.yaml:661,668,672` (the truthful source).
- **Named test:** `emailVerifiedClaimIsNotAccountState` (`package.md` §8).
- **Deviations:** D1–D7, all in `artifacts/12-specification-verification.md`.

## Open questions carried to the owner

| ID | Question |
|---|---|
| **OQ-T32-1** | No task commissions a signed-in "your email is not verified" surface, so T32 is a forward guard. If none is ever built, its standing value is the contract pins and the decode bound, not the consumer rule. |
| **OQ-T32-3** | Nothing documents this deployment's id_token `email_verified`. T32 fails closed and forbids it. |
| **OQ-T32-4** | Scan-helper duplication, raised independently by **three** reviews (phases 3, 8 I4, 11 A1). Not actioned: moving the helpers would change what four CLOSED tasks' adversarial scans mean. Needs its own task and the owner's decision. |

**T32 COMPLETE** — all fourteen phases. Phase 12 verdict **PASS**.
