# frontend · T29 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md` |

Final verification of T29 against `spec/frontend/requirements.md` (R10, R30), `design.md` (L4, L11, L16),
`tasks.md` task 29 and `agents.md`.

**T29 is verification-only. No production file is touched**, which is itself the finding: the uniformity R30
demands was already structural — `CallbackScreen.tsx:88-98` funnels all five account states through a single
`discard()` closure — and T29's job was to prove it and to pin it against the day someone adds a branch.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R10** — one uniform failure message for any SAS login failure | Yes, pre-existing | `CallbackScreen.tsx:88-98` (the single `discard()`); `catalogue.ts` `signIn.discarded` | Yes — AC1, AC10 (cases 4, 6, 49, 50) | No | — |
| **R10** — the privileged-unenrolled clause specifically | Yes, pre-existing | SAS-side; mapped onto the generic copy | **Yes, by T28** — `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`, 47 cases, landed 2026-10-29 | No | **T29-D1** — the live SAS `/login` page is unreachable from a jsdom suite |
| **R10** — the SPA never says enrollment is required | Yes | no enrollment vocabulary in any module or catalogue string | Yes — AC7, AC8 (cases 27, 34); T28 owns the enrollment vocabulary | No | — |
| **R30** — copy identical across the account states | Yes | one closure, one catalogue key | Yes — AC1, AC2 (cases 4, 7) | No | — |
| **R30** — redirect target identical | Yes | `CallbackScreen.tsx:97` | Yes — AC1, AC2, AC5 (cases 4, 7, 21) | No | — |
| **R30** — visual treatment identical | Yes, vacuously today | no `class`, no `style` on any failure node | Yes — AC4 (cases 17–19) **and** the pinned DOM skeleton (case 4) | No | **T29-D3** — a pin for task 173, not a claim styling will never exist |
| **R30** — the three token states | **No — out of scope** | composition root mounts neither `sessionBoundary` nor `rateLimitNotice` | **task 59** owns `identityErrorsIdenticalAcrossTokenStates` | Yes, by assignment | **T29-D2** |
| **L4** — no copy distinguishes one failure from another | Yes | `catalogue.ts` — six `signIn.*` keys, two of them failure sentences | Yes — AC7–AC10 (cases 27–51) | No | — |
| **L11** — keyboard operation and a live region | Yes | `SignInScreen.tsx:58-76` | Yes — `liveShape` and `focus` pinned absolutely (case 4) | No | — |
| **L16** — the three stored items are read back and cleared | Yes, pre-existing | `CallbackScreen.tsx` | Yes — `storedKeys` field, AC2 shell control (case 10) | No | — |
| **`agents.md`** enumeration rule | Yes | no account-state or role gate in any production module | Yes — AC9 (cases 43–48) | No | — |

R30 names four groups — "unknown or missing, locked, suspended, and deleted". T29 drives **five** fixtures,
splitting "unknown or missing" in two (Kimi F1). That is stricter than the requirement, deliberately: the two
reach `AccountUserDetailsService` by different routes and collapse to the same refusal, and proving they are
indistinguishable is worth more than assuming it.

## Acceptance criteria

All eleven discharged. Cases are numbered as in the Phase 10 manifest, extended by Phase 11.

| AC | Cases | Verdict |
|---|---|---|
| AC1 — every state ends in the one discard closure | 4–6, **+2 new** | **PASS** |
| AC2 — outcome equivalence across the five | 1–3, 7–10 | **PASS** |
| AC3 — no state word, description or URI survives | 11–16 | **PASS** |
| AC4 — visual treatment identical and pinned | 17–19, **+ the skeleton pin** | **PASS** |
| AC5 — rendering not timed by cause | 20–22 | **PASS** |
| AC6 — no unpermitted timer | 23–26 | **PASS** |
| AC7 — no production module names a state | 27–33 | **PASS** |
| AC8 — no catalogue string names a state | 34–42 | **PASS** |
| AC9 — nothing gates on a state or a role | 43–48, **+1 new** | **PASS** |
| AC10 — two sentences, exactly six keys | 49–51 | **PASS** |
| AC11 — every instrument proven to report DIRTY | 52–53, **17 rows** | **PASS** |

## Measured verification

| | |
|---|---|
| `tsc --noEmit` | exit 0 |
| `oxlint src` | clean |
| The file alone | **56 / 56**, exit 0 |
| Whole suite | **1398 passed / 1 failed (1399)** |
| The one failure | `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — pre-existing, environmental, unrelated |
| Mutation | **28 mutants, 28 killed, no survivors**, all by T29's own file |
| Production diff | **empty** |

The file is **1921 lines, 56 cases, 32 of them controls, thirteen `describe` blocks**.

The environmental failure is the one Kimi's ninth gap asks about. It is **not a T29 regression**: this host's
`oxlint@1.87.0` prints no summary line, so the lint-assertion test cannot parse a result. It predates T29 by
many tasks and is recorded in every manifest since. No product change is possible or warranted; the fix is a
runner concern.

## Phase 11 disposition

Kimi returned nine gaps under a heading that says eight. Every cited claim was checked against source first.

| Gap | Disposition |
|---|---|
| R10's privileged refusal copy "has no executable evidence **until task 28 lands**" | **REJECTED on fact.** T28 landed 2026-10-29 — `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`, 78 KB, 47 cases, 29/29 mutants, closed PASS. Kimi reviewed it in this package two phases ago. The clause has evidence. |
| R30's token-state half absent | **CONFIRMED, already declared.** T29-D2, task 59. No change. |
| Focus management not asserted | **ACCEPTED.** `focus` is now a struct field **and** pinned absolutely to `body`. |
| Live-region attributes not compared | **ACCEPTED.** `liveRegions` was a bare count; `liveShape` records `role`/`aria-live` and is pinned to `['status/polite']`. |
| DOM structural equivalence partial | **ACCEPTED.** `skeleton` records tags, nesting and ARIA with text excluded, pinned to `SIGN_IN_SKELETON`. |
| The AC9 scan misses destructured comparisons | **ACCEPTED — and it is a hole Kimi's own I1 fix opened.** Keying on the receiver meant `const { state } = account` had no receiver left. The new alternative keys on the state **literal**. Still not a bare `state ===`: `CallbackScreen` compares the OIDC `state` parameter, and a control pins that the scan stays silent on both shapes it writes. |
| No callback carrying both `code` and `error` | **ACCEPTED.** `CallbackScreen.tsx:81-85` already reasons about this in a comment — "a URL carrying both is malformed" — but reasoning in a comment is not a test, and the ordering that enforces it is a refactor away from reversing. |
| Minimal OAuth error responses | **ACCEPTED.** `error_description` and `error_uri` are optional in RFC 6749 §4.1.2.1; every fixture sent both, so the file rested on a response shape no standard guarantees. |
| The environmental failure is not isolated | **NOTED, no change.** Already documented in Phases 9, 10 and here. A runner concern, not a product one. |

**What accepting four of them exposed is worth more than any of them.** The three new fields were recorded in
the struct and asserted nowhere, which catches a **divergence** and nothing else. The five states cannot
diverge at the sign-in screen — each arrives through the one `discard()` closure, and the screen cannot see
which state refused the visitor — so the only expressible mutation is a **uniform** one, and struct equality
lets every uniform change through. `MC23`–`MC25` prove it: before the absolute pins were added they were
unkillable; after, `MC24` and `MC25` each fail exactly two cases, and both are pins. This is Kimi I7's lesson
and T28's MB29 for the third time in this package: **a field catches five that differ; only an absolute
assertion catches five that moved together.**

## The approving principal engineer's four questions

**1 · Is the task fully complete?** Yes, for the axis `tasks.md` assigns it. T29 owns the sign-in/callback
axis of `identityErrorsIndistinguishableAcrossAccountStates`; tasks 58 and 136 also cite the named test, and
the frozen split stands — T29 owns this axis, T58 extends the file, T136 is the gate. Precedent: T24 created
`returnTargetOutsideAllowlistFallsBackToHome` at 67 cases and T25 grew it to 184.

**2 · Does it satisfy every acceptance criterion?** Yes — eleven of eleven, each with at least one control
proving the instrument reports DIRTY, and 28 mutants killed.

**3 · Does it violate any LOCKED decision?** No. L4 is the requirement it verifies. L11 is now pinned rather
than merely observed. L16's stored items are a struct field. No spec file was modified.

**4 · Remaining risks?**

- **The vocabulary is enumerative, and always will be.** Every tier has a per-word control, and T27's and
  T28's patterns were diffed against each other to find the `two-factor`/`multi-factor` hole, but a phrasing
  nobody listed passes. This is inherent to absence testing, not a defect in this file.
- **`SIGN_IN_SKELETON` will fail the day task 173 lands**, by design. It is a pin, and task 173's author is
  meant to change it deliberately rather than discover the structure drifted.
- **R30 is not discharged by T29 alone** — the token-state half is task 59's.
- **R10's privileged clause rests on T28**, whose own deviation T28-D1 stands: the refusal copy is SAS's,
  rendered on a page no jsdom suite can reach. `oidcRoundTripEndToEnd` (task 134) remains unowned and is the
  largest verification gap in the package.
- **OQ1 stands and is not T29's**: the live SAS `/login` renders no `mfaCode` field while the backend
  requires it in the same POST.

## Declared deviations

| ID | Deviation |
|---|---|
| **T29-D1** | The five states are driven as adversarial fixtures at the SPA boundary, not through SAS. SAS's own `/login` is unreachable from this suite. |
| **T29-D2** | The three token states are out of scope — task 59 owns them, and the composition root mounts neither `sessionBoundary` nor `rateLimitNotice`. |
| **T29-D3** | The visual-treatment claim is a pin for task 173, not a claim that styling will never exist. |
| **T29-D4** | The Phase 8 fixes were applied during Phase 9 rather than Phase 10, so Phase 10 is a manifest rather than an implementation. |

## Carry-forward (not T29's)

The phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer — **ten consecutive tasks** now; the fix
belongs in `.ai/generate.py`. `oidcRoundTripEndToEnd` (task 134) has no suite and no owning task. The
composition root `mountShell.tsx` is unowned. L16 clause (b) is unbuilt and unowned.
`contracts/api/token-claims.md` is wrong on `email`/`name` and silent on `amr`/`acr`.

---

**PASS** — eleven acceptance criteria discharged, 56 cases, 28/28 mutants killed against a guarded baseline,
no production change, and four declared deviations all assigned to named owners.
