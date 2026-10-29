# frontend · T26 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T26 — Session storage holds only L16 items |
| **Model** | Claude Opus 5 (acting as the approving principal engineer) |
| **Consumes** | every prior artifact |
| **Produces** | `artifacts/12-specification-verification.md` |

All paths below are relative to `frontend/`; `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` is the named test.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R29** — only non-sensitive form input in sessionStorage, 15-minute TTL, no token / password / recovery code / API key / PII in **any** storage | Verified, not built — the task is a guard | the four permitted keys: `auth/authorizeRequest.ts:36-38`, `auth/returnTarget.ts:39`; the whole writing surface: `authorizeRequest.ts:82-84,121-123`, `returnTarget.ts:91,129` | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` — 53 cases | **The TTL half of R29 is not verified**: clause (b) has no implementation to verify | Declared — D1 below |
| **L16** — the carve-out is exhaustive: (a) three PKCE items + the return target, (b) mid-task form input with a TTL | Verified for (a); (b) absent from the codebase | inventory derived, not written: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:112-148` | pinned at four with an instructive message: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:735-772` | Clause (b) unbuilt and unowned | Declared — D1 |
| **L16** — "nothing else is stored client-side" | Verified for the key-value stores; the SW cache is L17's | three instruments: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:175` (sweep), `:255` (runtime watch), `:678` (namespace scan) | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:773-866`, `:968-1065`, `:1066-1173`, `:1174-1211` | — | Declared — D2 (the surface list is enumerative) |
| **L3** — tokens never in storage | Verified over this file's own flows; T22 owns the package sweep | `auth/accessToken.ts` holds it in a module variable | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1212-1290` | — | Declared — D3 (the nonce exclusion) |
| **`agents.md:79-81`** — sessionStorage is the only sanctioned client-side storage, for the L16 list only; no PII client-side | Verified | `PERMITTED_WRITERS`: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:600-627` | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:968-1012` | — | None |
| **`design.md` §5** — localStorage holds public preferences only | Verified as written in the spec | narrowed claim: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1013-1041` | yes, with a control that a theme preference is **not** flagged | — | None — this was Kimi I2, now aligned |
| **T24-D4** (inherited) — the carve-out is two constants in two modules, unmerged | Honoured | `inventory()` reads both: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:112-148` | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:735-748` | — | None |
| **`package.md:160`** — the named test `sessionStorageHoldsOnlyTheL16Items` | Built | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:715` | 53 cases, exit 0 | — | None |
| **`package.md:153`** — `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` | Not built | — | — | Claimed by **no task** in `tasks.md` (measured, Phase 0) | Out of scope — OQ2 |

## The twelve acceptance criteria

| AC | Verdict | Where |
|---|---|---|
| AC1 — key set is a subset of a **derived** inventory | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:735-772`, `:773-866` |
| AC2 — holds at every intermediate state | met — 4 states + 6 discards, each asserting the **exact** set | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:773-866` |
| AC3 — only the two modules write, and remove, only their own keys; no `clear()` | met, and strengthened by the namespace scan | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:867-899`, `:968-1012`, `:1066-1173` |
| AC4 — no out-of-inventory key is **ever written**, write-then-delete included | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:867-899`, `:1357-1375` |
| AC5 — no token, segment, decoded claim, auth code, password, recovery code or API key as any value or key, in **either** store | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1212-1290` |
| AC6 — `localStorage` empty after every flow; no L16 item in it | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:773-866`, `:1013-1041` |
| AC7 — six surfaces never named; stated as structural | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1174-1211` |
| AC8 — inventory exhaustive and pinned, with an instructive message | met | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:749-761` |
| AC9 — every instrument proven to report DIRTY first | met — 6 controls + 5 indirection cases + 7 namespace cases | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1333-1409`, `:900-967`, `:1066-1173` |
| AC10 — clause (b) recorded as permitted-but-unbuilt, naming its unowned test | met | `CARVE_OUT_MESSAGE`: `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:150-162` |
| AC11 — no PII in any storage | met, with the live/forward-looking split stated | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1212-1290` |
| AC12 — **empty** distinguished from **unreachable** | met, with the measurement that justifies it | `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1291-1332` |

## The four principal questions

**1. Is the task fully complete?** Yes, for the task `tasks.md:38` states. One new file, 53 cases, three
instruments, no production change. What is *not* complete is R29's other half — clause (b) — which no task in the
package owns; that is a package gap this task measured and reported, not work T26 left undone.

**2. Does it satisfy every acceptance criterion?** Yes, all twelve, with three declared deviations below. The
strongest evidence is not the green run: it is **22 of 22 mutants killed, every one by this file alone**,
including the `clear()` mutant that escaped T25's own rejection rows and two mutants written specifically to
escape this file's instruments.

**3. Does it violate any LOCKED decision?** No. L16, L3, L17, L18, `design.md` §5 and `agents.md`'s storage
rule are each either asserted or deliberately left to the task that owns them, and the one place the file does
not assert a permitted thing — the nonce — is excluded **by name** with the reason recorded (D3).

**4. Remaining risks.**

- **The guard will fail the day L16 clause (b) is built.** That is deliberate: L16 says the list is the entire
  carve-out, so growth is a spec event. The failure message names clause (b) and the TTL test no task claims, so
  it reads as an instruction. The risk is that someone silences it instead; the message is the mitigation.
- **The forbidden-surface list is six names long.** A client-side surface nobody has thought of is outside every
  instrument here. `design.md` §5 governs additions (OQ4, with the author).
- **Five needles are forward-looking** — password, recovery code, API key, email, account UUID. No flow in this
  file can produce one, so their absence proves only that the sweep read the surface. Two sentinels make the
  search credible; the cases say which needles are which.
- **A write from inside a permitted writer, under a non-namespace key, on a path no flow drives** is seen by no
  instrument until that path is driven. Measured and stated at the case (`src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx:1013-1041`). It is the narrowest
  residue left after three instruments, and the harness's MA22 is the proof that the dormant family is otherwise
  covered.
- **The suite's single failure is environmental and unrelated**: `api/lintRejectsHandWrittenBackendFetch.test.ts:118`,
  because this host's `oxlint@1.87.0` prints no summary line. T18 Gap 6 still asks CI to pin that failure's
  identity so it cannot mask a real one.

## Declared deviations

**D1 · R29's TTL half is not verified, because it does not exist.** L16 clause (b) — non-sensitive mid-task form
input with a 15-minute TTL — has no implementation in `src/`, and its named test
`expiredSessionMidTaskPreservesOnlyNonSensitiveInput` is claimed by none of the 125 tasks in `tasks.md`
(measured in Phase 0). T26 verifies the inventory and names clause (b) as the expected next entrant. **Owner
action:** assign clause (b) to a task, or record it as deferred.

**D2 · "Nothing else is stored client-side" is verified for the key-value stores, not for the
service-worker cache.** L16's sentence is absolute; `design.md:194` and L17 separately permit a precache of the
shell and hashed assets. The reading taken — §5's per-surface inventory scopes L16's sentence, with the cache
governed by L17 and `serviceWorkerNeverCachesApiResponses` (R57) — is the only one under which the built SPA is
compliant. **Owner action:** confirm (OQ3).

**D3 · The ID token's `nonce` claim is excluded from the decoded-claim sweep, by name.** L16 permits the nonce
in sessionStorage by name and the authorize step puts it there, so searching for it reports a sanctioned item as
a leak — which is exactly what Phase 6's first run did. The exclusion is one claim wide, and a case holds it
there: `sub` is still searched, and a planted `sub` still fails. T22 made the same exclusion independently for
the same reason.

## Final state

| | |
|---|---|
| `tsc --noEmit` | clean |
| `src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx` | **53 passed**, exit 0 |
| Whole suite | **1245 passed / 1 failed** (1246 collected) — the one pre-existing environmental failure |
| Mutation | **22 of 22 killed**, no survivors, **none inherited-only** |
| Harness | `.ai/prompts/frontend/T26/artifacts/07-mutation-harness.py`, committed, runnable from the repo root, with an optional family filter |
| Production code changed | **none** |

---

**VERDICT: PASS** — the carve-out is proven exhaustive by three instruments that were each watched to fail first,
with three declared deviations and one package gap (L16 clause (b)) reported rather than quietly absorbed.
