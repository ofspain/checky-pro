# frontend · T27 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md` |

**Task (verbatim, `spec/frontend/tasks.md` task 27):** *SAS MFA challenge resumes at callback. After the
challenge completes, the SPA resumes at the callback. Verifies
`loginChallengeRunsInsideSasFlowAndResumesAtCallback`. Cites R9.*

---

## Traceability matrix

| Requirement | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R9** — the challenge runs inside the SAS flow; the SPA resumes at the callback | Yes, by the SPA containing no factor and resuming symmetrically | `SignInScreen.tsx:38` (one hand-over), `CallbackScreen.tsx:66-205` (resumption), `codeExchange.ts:76` (every non-token field discarded) | `…ResumesAtCallback.test.tsx:697, 967, 1093, 1220, 1547` | No | No |
| **R10** — uniform failure copy; never says enrollment is required | Yes, not contradicted | `CallbackScreen.tsx:89-99` (one `discard` closure for all five modes), `catalogue.ts:18` | `:1258` (refusal ≡ mismatched state; catalogue scan) | No | No |
| **R57 / L17** — `/login` never cached, never given the shell | Yes, cited not re-proved | `navigationDenylist.ts:17-29` | `:1352` | No | No |
| **L2** — interactive login is SAS-hosted; password/TOTP/recovery run in the SAS flow | Yes — no credential surface exists in the bundle | measured: 24 production modules, zero credential shapes | `:790` (source scan, manifest, identifier scan + 7 controls) | No | No |
| **L5** — SAS refuses an unenrolled privileged account with the same error | Yes, no enrollment-during-login path exists | `CallbackScreen.tsx:89-99` | `:1258` | No | No |
| **L4** — enumeration-safe UI, no copy distinguishing failures | Yes | `catalogue.ts:8-9, 18` | `:1258` (two faults → one outcome) | No | No |
| **L3** — tokens in memory only; nothing MFA-shaped stored | Yes | `accessToken.ts`, `authorizeRequest.ts:35` | `:1093` (sweep incl. response body + decoded claims) | No | No |
| **L14** — resumption needs no session beyond the three PKCE items | Yes | `authorizeRequest.ts:35`, `codeExchange.ts:59` | `:1220` | No | No |
| **L16** — the sessionStorage carve-out is exhaustive | Yes, cited to T26 | `authorizeRequest.ts:35`, `returnTarget.ts:39` | `:756` (store does not grow on a second click) | No | No |
| **L1 / R6, R7** — PKCE, validated `state`/`nonce` | Yes, cited to T16–T23 | `authorizeRequest.ts:85-94`, `idTokenNonce.ts:119` | `:967` (the wrong-`nonce` blindness control) | No | No |
| **L11** — managed focus across redirect and MFA steps | **Partly.** The announcement is real; focus is not managed | `CallbackScreen.tsx:210` (live region), `SignInScreen.tsx:54-56, 67-69` | `:1395` | **Yes — focus** | **T27-D1** |

### Acceptance criteria

All eleven ACs from the frozen brief are implemented and exercised. Ten `describe` blocks cover them, because
AC2 and AC3 share one — both are claims about structural absence over the same scanned tree, and splitting
them would have duplicated the walker. *(Earlier artifacts said "eleven blocks, one per AC"; the count is ten.
No AC is uncovered — the mapping is in the block titles above.)*

| AC | Block | Verdict |
|---|---|---|
| AC1 hand-over, one navigation by any mechanism | `:697` | PASS — MA17 killed by this file |
| AC2 no credential input anywhere | `:790` | PASS |
| AC3 no second-factor logic or package | `:790` | PASS — source, imports **and** manifest |
| AC4 callback is step-count-agnostic | `:967` | PASS — three fixtures, deep-equal `Outcome` |
| AC5 resumption needs only the three PKCE items | `:1220` | PASS |
| AC6 a privileged refusal is an ordinary discard | `:1258` | PASS — incl. the catalogue scan |
| AC7 no MFA-shaped value on any surface | `:1093` | PASS — incl. response body and decoded claims |
| AC8 `/login` denylisted | `:1352` | PASS |
| AC9 announced at resumption; focus managed | `:1395` | **PASS with deviation T27-D1** |
| AC10 exactly the eight authorize parameters | `:1547` | PASS — MA11/12/13 killed |
| AC11 every instrument proven to report DIRTY | `:1603` + controls | PASS |

---

## The approving principal engineer's four questions

**1. Is the task fully complete?** Yes. One new file,
`frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`, 1679 lines, **50 cases**.
Verified: `git diff 06261068..HEAD -- frontend/src` reports **one file changed, 1679 insertions** — so no
production module was touched at any point in this task, as the frozen brief anticipated.

**2. Does it satisfy every acceptance criterion?** Yes, with AC9 satisfied in the restated form the Phase 4
gate froze: the announcement is asserted, the focus obligation is **pinned** with its owners named. The
restatement was itself the product of measurement, not convenience — AC9 as first drafted required behaviour
no production module performs.

**3. Does it violate any LOCKED decision?** No. It adds no credential surface, no second-factor logic, no
MFA-specific production code, and no MFA-shaped copy. It asserts no claim that would make a future breach of
R10 or L4 pass. L11 is **not violated** — it is incompletely satisfied by the existing application, which this
task measures and records rather than papers over.

**4. Remaining risks.**
- **The largest is unchanged and not this task's:** no real browser round trip exists.
  `oidcRoundTripEndToEnd` has no suite and no owning task. Everything here is jsdom plus structure, declared in
  the file's own header rather than implied.
- **Enumerative lists.** The forbidden-package and MFA-identifier lists cannot prove the absence of a library
  or a term nobody thought of. Stated beside each list.
- **AC4's `text` field is empty on both sides** of the success comparison, because `/app/home` has no element.
  The other four `Outcome` fields carry the claim and the rendered-text half is earned in the AC9 block
  instead. Recorded in the file and in self-review S5 as the first thing a reviewer should attack.
- **OQ1 is a live product gap, not a test gap:** the SAS login page has no `mfaCode` field, so a real browser
  cannot complete an MFA sign-in today. Confirmed again on 2026-10-29 against the running local stack — the
  live form carries `username`, `password` and `_csrf` only. Owner action outstanding.

## Deviations

- **T27-D1 — focus is pinned, not managed.** L11 requires managed focus across the redirect. Measured: neither
  screen moves focus, and the completed resumption renders nothing, holds zero live regions and leaves
  `document.activeElement` on `<body>`, because `/app/home` is registered with no element. Pinned with an
  instruction to delete the pin when it is closed. **Owners:** task 135
  (`phase1ScreensPassAccessibilityChecks`) and **task 174** (home screen, added 2026-10-29).
- **T27-D2 — Phase 8 and Phase 10 were not produced as separate artifacts.** Kimi delivered one review,
  labelled Phase 11. T27's deliverable is a single test file, so phases 6/10 and 8/11 collapse onto one
  object and one review discharges both. Phase 8 was **not** self-produced. Full record in Phase 9.
- **T27-D3 — the file spells the eight authorize parameter names as literals**, against the package's usual
  "no literal" rule. Justified at length in the file header and AC10: the derived form is T16:223's and is
  structurally incapable of catching an addition, and the names are RFC 6749 §4.1.1 / OIDC Core §3.1.2.1 wire
  spellings that T15 cannot rename.

## Verification performed

| | |
|---|---|
| `tsc --noEmit` | clean |
| `oxlint` on the new file | clean |
| The file alone | **50 / 50**, exit 0 |
| Whole suite | **1295 passed / 1 failed (1296)** — the failure is the known environmental `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` |
| Mutation | **22 / 22 killed, survivors: none** (21 by this file; MA18 inherited by design) |
| Production source | **unchanged** — one file changed in T27's commits, and it is the test |
| Working tree | clean after every harness run |

---

**VERDICT: PASS** — R9's two halves are verified as absence and symmetry over the measured architecture, all
eleven acceptance criteria hold, no LOCKED decision is violated, and the one unmet obligation (L11's managed
focus) is recorded as T27-D1 with named owners rather than quietly satisfied.
