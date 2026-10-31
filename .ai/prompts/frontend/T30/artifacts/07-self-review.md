# frontend · T30 · Phase 7 — Self-Review and Mutation Score

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T30 — Profile from userinfo |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

**23 mutants, 23 killed, no survivors**, all by T30's own file — but two survived the first run, and what they
turned out to be is the most useful thing in this phase.

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **38 / 38**, exit 0 |
| Whole suite | **1436 passed / 1 failed (1437)** — the known `lintRejectsHandWrittenBackendFetch` environmental failure |
| Mutation | **23 / 23 killed**, against a guarded baseline of `1 failed of 1437` |
| `generatedClientCompiles` | 10 / 10 |

---

## S1 · The first run had two survivors, and only one was an escape

This is the distinction T28's MB27 taught and it came up again immediately.

### MP21 was a real escape

*A 403 is reported as `unavailable` instead of `unauthorized`.* **It changed nothing**, because no case drove
a 403 at all. The reader had always handled it — `response.status === 401 || response.status === 403` — and
nothing held it to that, so the branch was free to rot.

Closed with a case. The distinction is worth keeping rather than collapsing into one failure: a **401** means
the credential was not accepted, a **403** means it was and the account may not have this. A caller that
cannot tell them apart cannot choose between re-authenticating and telling the visitor their account lacks
something.

### MP09 was inert, and scoring it either way would have been wrong

*The `WWW-Authenticate` header is carried into the error.* It survived — and **not because the leak went
undetected.** `ReadProfileError`'s constructor looks its message up by key:

```ts
super(FAILURE_MESSAGE[failure]);
```

so `new ReadProfileError(`unauthorized${header}`)` finds nothing in the table and the message becomes
`undefined`. The header never reaches anywhere. **The leak was prevented by construction rather than by a
test**, which makes the mutant incapable of the harm it names.

Calling that a KILL would credit a test that did no work; calling it an ESCAPE would report a hole that does
not exist. Both answers are wrong, so two things changed:

1. **The property is now pinned** — AC8 asserts the class cannot be *talked into* carrying detail, which is a
   stronger claim than its merely not doing so today.
2. **MP09 is re-aimed** at the shape that can actually leak — a plain `Error` carrying the header, which is
   exactly what a reader reaching for a useful message would write. Re-aimed, it is killed by six cases.

## S2 · The measurements did more for this task than the review did

Kimi's phase 3 was good and its Blocker was real. But three of the four most consequential design decisions
came from calling the running server, not from reading or reviewing:

| Measured | What it changed |
|---|---|
| `/userinfo` → `200 {"sub"}` with `scope: ["openid","profile","email"]` granted | AC9 treats a `sub`-only body as a **success**. A reader written against the aspirational shape would be broken against the only server that exists, and would have looked correct in a suite fed only a full fixture |
| Bearer-less → `302 → /login`, followed into `200 text/html` | AC12 and `redirect: 'manual'` exist. The design had the boundary handle a missing session via its 401 path; **there is no 401** |
| Malformed bearer → `401`, **empty body**, detail in `WWW-Authenticate` | AC8 covers headers. A requirement written about bodies would have missed it entirely |

MP13 and MP14 are the mutants that show the second row was not a theoretical concern: both remove a guard and
both are killed, so both were defects that would otherwise have shipped — and both are invisible to any test
that only ever drives a tab holding a token.

## S3 · Where this file is weakest

- **The forbidden-claim vocabulary is enumerative**, as every absence scan in this package is. `email`,
  `name`, `sub` and the four OIDC extras are listed; a claim nobody listed passes. Mitigated by the decode
  scan bounding the search to one file, which makes the list's completeness much less load-bearing than it
  would be tree-wide — but not eliminated.
- **The decode pattern is narrow by choice and could be evaded.** `atob`, a `jwtDecode`-style helper, or
  indexing segment 1 of a split. A decode written as `token.split('.').at(1)` or via `Buffer.from` would pass.
  Widening it was measured to fail on commissioned code (Phase 6 §4), so this is a deliberate trade rather
  than an oversight, and it is the first thing a later reviewer should push on.
- **AC12's behavioural half rests on a stub.** jsdom models neither redirect modes nor a 3xx `Response`, so
  the followed-redirect shape is presented by the transport rather than performed. The case says so. Only a
  real browser — `oidcRoundTripEndToEnd`, task 134, unowned — could do better.
- **`SIGN_IN`-style absolute pinning is absent for the contract.** AC1 reads the YAML as text and checks
  stanzas, because no YAML parser is available in this environment. A structural change that kept the same
  lines in the same stanzas would pass.
- **Nothing consumes the profile** (T30-D3), so the reader's ergonomics are unproven by use.

## S4 · What I got wrong while writing, recorded rather than smoothed over

- **The AC3 assertion was wrong about `openapi-fetch`.** It asserted `readProfile.ts` does not contain
  `/userinfo`; the schema path is the library's first argument and must be there. Rewritten as a claim about
  shape.
- **The contract assertion failed on the contract's own prose**, because the `/userinfo` description explains
  why it is *not* `security: []`. Rewritten line-wise. In a package that documents this densely, prose
  colliding with assertions is a standing hazard, not a one-off.
- **Phase 0 guessed a cause and the guess was wrong** — it read discovery's `scopes_supported: ["openid"]` as
  meaning the scopes were not granted. A real token refuted it. Phase 1 corrected it in writing.
- **A carry-forward note I had repeated for several tasks was wrong on both counts** (`token-claims.md`
  silent on `amr`/`acr`, wrong on email/name). Corrected in Phase 0 §G3.

## S5 · The mutant families

| Family | Mutants | Probes | Result |
|---|---|---|---|
| **A token is read instead of `/userinfo`** | MP01–MP05 | **AC5, R20(c)** | 5 / 5 |
| The reader builds its own request | MP06–MP08 | AC3, L6 | 3 / 3 |
| A failure leaks what it was told | MP09–MP11 | AC8 | 3 / 3 |
| **No session mistaken for a profile** | MP12–MP15 | **AC12** | 4 / 4 |
| The sub-only live shape | MP16–MP17 | AC9 | 2 / 2 |
| The profile survives the read | MP18–MP19 | AC6, AC7 | 2 / 2 |
| The bearer and the 401 | MP20–MP21 | AC4 | 2 / 2 |
| The contract stops saying what it says | MP22–MP23 | AC1 | 2 / 2 |

Two deserve naming beyond MP09 and MP21:

- **MP01 is the subtlest.** It reads `sub` rather than a PII claim — the shape Kimi's F4 warned about, and the
  one a reviewer skims past because a UUID "isn't really PII". It is on the forbidden list precisely so this
  dies, and Kimi's proposed carve-out for it was refused because measurement showed the carve-out described
  machinery that does not exist.
- **MP05 is written to escape**, in T26's through T29's tradition: the decode sits in a module nothing
  imports, so no runtime case can reach it. Only a whole-tree source scan sees dormant code, which is why AC5
  scans the tree and why the empty-tree refusal is not decoration.

## S6 · The harness

T29's baseline guard was carried in **from the start** rather than added after being burned: the floor is
asserted at `EXPECTED_BASELINE_FAILURES = 1`, T30's own file is asserted green, the harness refuses to run
against a wrong floor, and it exits non-zero on any survivor. MP22 and MP23 mutate `contracts/api/oidc.yaml`
rather than `src/`, so they are applied and reverted separately — the revert covers both trees.

---

**Phase 7 complete.** 38 cases, 23/23 mutants, no production regression. Ready for **Phase 8 — Kimi 2.7's
independent review.**
