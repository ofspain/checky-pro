<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T32 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Gate** | Human Approval — not a stop under the standing workflow |

## Citation audit, performed before any resolution

Every cited `file:line` was read against source first, per the standing rule. All ten citations are
accurate. One needed a scope correction, in the reviewer's favour and against my own phase 7 framing.

| Finding | Citation | Verdict |
|---|---|---|
| I1 | `:210` `USERINFO_READ`, `:303` `.includes(USERINFO_READ)` | accurate |
| I2 | `:192` `TOKEN_DECODE`, unspaced `split` arm | accurate |
| I3 | `strip()` `:70-110` keeps literals; `offChannel()` `:301-305` | accurate |
| I4 | helper duplication | accurate |
| I5 | `:479-486` four substring checks | accurate |
| I6 | `:368-375` contract phrase pin | accurate |
| I7 | `offChannel()` is file-level | accurate |
| I8 | `NEEDLES` at `:406` | accurate |
| I9 | MR05's ~154 failures | accurate (154 exactly) |
| I10 | MR01/MR02/MR04 coverage | accurate |

**Scope correction to I1.** The review's first example is `api.GET("/userinfo")` in the existing consumer.
That shape cannot arrive silently: T30's AC3, at
`profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:560`, asserts
`source).toContain("api.GET('/userinfo'")` over `profile/readProfile.ts`, which is the one caller in the
tree (`readProfile.ts:125`). So changing *today's* caller to double quotes fails T30, not T32. What I1
actually breaks is a **second** consumer, written in a shape T30 does not pin. That is a smaller blast
radius than the review states and a worse failure mode than I had credited: the instrument would flag a
legitimate reader, which is a false positive landing in a future author's task — exactly the defect T31
had to repair three times in inherited instruments.

## Resolutions

**I1 — Major — TAKEN.** `USERINFO_READ` (a substring) is now `USERINFO_CALL` (a regex),
`\.GET\(\s*['"]/userinfo['"]`: quote-agnostic and whitespace-tolerant. Four call shapes are driven as a
control — single quote, double quote, formatter-wrapped across lines, spaced arguments — and each must be
recognised as on-channel. Under the phase 7 substring the last three were judged off-channel and flagged.

**I3 — Major — TAKEN.** A new `literalMask(code)` computes which offsets sit inside a string or template
literal, and `readsUserinfo` requires the match's `.GET(` to sit outside one. The quoted path is of course
inside a literal — it is the argument — which is why only the match's start is tested. The control reads
the claim off a decoded token while advertising `api.GET('/userinfo')` inside a string, and is caught.
A second control confirms a comment cannot claim the channel either, since comments never reach the scan.

This finding is the sharpest of the ten. `strip` keeps literals deliberately, so that a claim name written
inside one cannot leave the source before a scan sees it — and the channel detector inherited that same
preserved surface from the other side. Comment-spoofing traded for string-spoofing, as the review puts it.

**I2 — Major — TAKEN.** The decode arm is now `split\(\s*['"]\.['"]\s*\)`. The gap mattered in the
direction that makes a bound *pass*: a file decoding with `split( '.' )` was invisible, so AC1's "exactly
one decoding file" still saw one file and the new decoder was never asked whether it reads the claim.
Controls drive `split('.')`, `split( '.' )` and `split(  '.'  )` as recognised, and `split("a.b".charAt(1))`
as **not** recognised, which pins that the arm still does not match ordinary string work — the reason it
was narrow to begin with.

The review's second half of I2 — a decoder named only `decode` — is **answered rather than taken**, with a
case. Widening to `\bdecode\b` would be the wrong fix: measured against today's tree it matches nothing,
it reads as a bound while being one rename from meaning nothing, and `jose`'s real export is already
covered as `decodeJwt`. The new control drives `decode(token)` and asserts it is not recognised, then shows
AC2 catches the file anyway through the other half of the pair. A measured gap, not a hole.

**I6 — Minor — TAKEN.** The pin is now `/\bnot\b\s+the\s+access\s+token/i`, with the `token-claims.md` and
`T32` citations unchanged. The substring `NOT the access` sat on one line only by accident of where
`oidc.yaml:195` happens to wrap; re-wrapping the description, which changes no meaning, would have failed
it. MR08 still dies — it writes `ALSO the access`, and `\bnot\b` is absent from the stanza.

**I7 — Minor — TAKEN.** A paragraph in the file header now says that AC2's channel rule is file-level: it
permits a FILE that calls `/userinfo`, not a particular value's flow. It also says why AC1 and AC2 are a
**pair** rather than alternatives — AC2 asks which channel a mentioning file reads, AC1 keeps the token
channel down to one named file that reads neither spelling. The review is right that this was only implied.

**I5 — Minor — ACCEPTED AS STATED, documented in place.** AC8 is a disclosure guard, not a behavioural
one; any rewrite keeping the four substrings passes. The comment now says so, and says what it *does*
prove: the limitation cannot be deleted in silence. That paragraph is the only thing standing between a
later reader and the belief that a green scan here means no UI is driven by the claim.

**I8, I9, I10 — NO ACTION**, as the review itself concludes for each. I9's record: MR05 is a structural pin
on the allowlist's lower bound, not a precision kill; its 154 failures say only that something fired.
I10's limitation — no mutant covers a camelCase read outside the decoding file, because a consumer reading
`emailVerified` from `readAccount` is legitimate and such a mutant would be an intended survivor — stays
stated in the header and in `07-self-review.md` S2.

**I4 — Minor/Major — ESCALATED, NOT ACTIONED. Stays OQ-T32-4.** The review's argument is the one I had not
made: a helper *not* updated when it should be creates drift, which is the mirror of the hazard the
duplication avoids. Both are real. What decides it for T32 is blast radius — moving `strip`,
`refuseEmpty`, `productionSources` and the surface sweep into `src/testing/scanHelpers.ts` would change
what **four closed tasks'** adversarial scans mean, and a closed task's instrument is evidence that was
signed off on its exact shape. That is the owner's call, and T32 is not the place to make it unilaterally.
Measured for the record: T29/T30/T31 define 4, 3 and 4 local copies respectively, and none is exported
anywhere in `src/`.

## Mutation score after the repair

Two new mutants record the two findings taken into code. Each isolates exactly one hole, because a mutant
that dies for the wrong reason is a false proof:

| Mutant | Phase 7 instrument | Phase 9 instrument |
|---|---|---|
| MR12 forges the channel in a string literal, reads the claim off an ambient object | **SURVIVED** — `"GET('/userinfo'" in code` was `True` | **KILLED** by the quote-aware mask |
| MR13 a second file decodes with `split( '.' )` only | **SURVIVED** — the old arm did not match | **KILLED** by the widened arm |

Both survivals were confirmed by running the phase 7 patterns against the injected code directly, not
inferred. MR12 reads the claim off an ambient object rather than by decoding, and MR13 carries no `atob`,
precisely so that neither dies on AC1's bound for a reason unrelated to the finding it records.

## Gates

| Gate | Result |
|---|---|
| T32 cases | **26 passed** (was 20) |
| Whole suite | **1534 passed / 1 failed of 1535** — the known `oxlint` environmental case |
| Mutation | **13/13 killed, 0 survivors, 0 broken**, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |
| Production code | none changed; T32 remains a test-only task (G7) |

## Carried to the owner

- **OQ-T32-1** — no task commissions a signed-in "your email is not verified" surface, so T32 is a forward
  guard. If none is ever built, its standing value is the contract pins and the decode bound.
- **OQ-T32-3** — nothing in the repo documents the id_token's `email_verified`; T32 fails closed on it.
- **OQ-T32-4** — scan-helper duplication, now with the review's counter-argument recorded above.

**Phase 9 complete.**
