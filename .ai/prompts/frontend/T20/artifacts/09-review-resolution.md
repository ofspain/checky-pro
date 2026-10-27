# frontend · T20 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Seven independent-review findings and eight self-review findings, all decided. **Every citation in Phase 8 was
checked against the source and every one is accurate.** One code change was applied. One finding is rejected on a
concrete ground, and Phase 8's framing of five others is corrected.

---

## Two corrections to Phase 8 before the ledger

**1 · "The implementation is not complete" misreads the pipeline, not the code.** Phase 8's summary says to "fix
I1–I5 before treating Phase 6 as complete". I1 through I5 are all *missing tests*, and this pipeline puts test
generation in **Phase 10**, explicitly: Phase 6's own prompt says "Do NOT write tests here (that is Phase 10)".
Phase 6's notes flagged exactly these items as Phase 10's, by name, under *Deviations* — the named test, the AC22
form-encoding case, the AC23 drift case and the `ALLOWED` entry. So all five are **accepted as facts and scheduled
where the pipeline puts them**; none is a Phase 6 omission, and Phase 6 is not reopened.

**2 · The suite does run here.** Phase 8 notes that `vite-plugin-pwa` is not installed in its environment so
`vitest` cannot start, which makes its review static-only — honestly disclosed, and it is why the findings are all
about what is absent rather than what behaves wrongly. For the record: `frontend/node_modules/vite-plugin-pwa` is
present on this host, and Phase 6 ran the suite to **818 passed / 1 failed of 819**, the one failure being the
known environmental `oxlint` case. Phase 8's verification gap is environmental and is not evidence about the code.

---

## Independent review (Phase 8)

| # | Finding | Decision |
|---|---|---|
| I1 | The named test file does not exist | **Accepted as scheduled — Phase 10.** |
| I2 | AC22 form-encoding case missing | **Accepted as scheduled — Phase 10.** |
| I3 | AC23 `oidc.ts` drift case missing | **Accepted as scheduled — Phase 10.** |
| I4 | `createOidcClient` missing from the lint `ALLOWED` table | **Accepted as scheduled — Phase 10.** |
| I5 | AC6, the absence of a client credential, is unproven | **Accepted as scheduled — Phase 10.** |
| I6 | `usableTokens` reaches its cast with an array | **REJECTED** — reason below. |
| I7 | The latch's rationale no longer covers the async window | **Accepted — applied now.** |

**I1–I5 · accepted, Phase 10.** They are correct and they are the next phase's entire job. Each is already written
into the frozen brief as an AC, so Phase 12 will verify them one by one: the named test (AC1–AC13, AC20, AC21),
the form-encoding case (AC22), the drift case (AC23), the `ALLOWED` entry (Phase 4 ledger, finding 2) and the
no-credential assertion (AC6). I5 deserves the emphasis Phase 8 gives it: for a client registered with
`ClientAuthenticationMethod.NONE`, the absence of a credential **is** the security story, and a passing probe that
never looked for one is not coverage. It is the first thing Phase 10 writes.

**I6 · rejected, and for a better reason than the self-review gave.** Phase 8 asks for `Array.isArray(payload)` in
the first guard of `usableTokens` (`codeExchange.ts:75`) to make the type-level claim match the runtime one,
calling it a one-line tightening that "costs nothing in coverage". It costs something precise: **the line cannot
be killed by any input.** Every array that reaches the cast already returns `null` at the next two guards, because
`id_token` and `access_token` are `undefined` on an array — so removing `Array.isArray(payload) ||` changes the
function's output for no value whatsoever. A mutation run would report that mutant as a survivor, and this package
has held 19 of 19 killed through T19. Adding a line that no test can kill, to a file whose discipline is that every
line is falsifiable, is a step backwards dressed as tidiness. The misleading cast is a real observation, and the
honest answer to it is the one Phase 10 already plans: a 200 whose body is an array, asserting the rejection — so
the behaviour is pinned rather than argued, and nothing inert is added to carry the claim.

**I7 · accepted, applied.** The recommendation is in two halves and both are right.

- **The comment**, applied now. `CallbackScreen.tsx:42-44` explained the empty dependency list in terms of there
  being no second set of parameters to react to, which was the whole story while the seam was a stub. Four lines
  were added recording that the latch is set **before** the exchange starts and therefore closes the entire window
  the request is in flight for, so nothing that re-runs the effect in that window can redeem the code twice — and
  that a replayed code is refused anyway (measured: `400 invalid_grant`), but the refusal would land as a discard
  on a sign-in that had already succeeded. No behaviour changed; the `handled` latch and the two-argument `.then`
  are untouched.
- **The unmount-in-flight case**, scheduled for Phase 10: unmount the screen while the exchange is pending, then
  assert no crash, no navigation and no token leakage. Phase 8 rates its own confidence Low and that is right —
  `navigate` from a disposed router is a warning rather than a security fault — but it is an untested path through
  code that now performs I/O, and the existing StrictMode case (`callbackValidatesNonce.test.tsx:727-737`) covers
  double invocation, not unmount.

## Self-review (Phase 7)

| # | Finding | Decision |
|---|---|---|
| F1 | AC4's "exactly five" rests on a test, not the type | **Accepted** — Phase 10 asserts the serialized body. |
| F2 | The replaced T18 case couples that suite to T20-D3 | **Left as written.** Phase 8 reviewed the file and did not object; no change. |
| F3 | `expires_in` dropped; task 21 needs it | **Accepted as a handoff** — carried to Phase 13. |
| F4 | The OIDC client must never carry renewal middleware | **Accepted as a constraint on task 21** — carried to Phase 13. |
| F5 | AC19 needs no new test | **Accepted** — Phase 10 cites `serviceWorkerNeverCachesApiResponses.test.ts:169-175`. |
| F6 | No credential absence proven | **Accepted** — same as I5, Phase 10. |
| F7 | Contract documents 200 and 400 only | **No change** — the code is indifferent to status by design (T20-D9). |
| F8 | An array reaches the cast | **Superseded by I6's rejection**, which gives the stronger reason. |

## The one change applied

| File | Change | Finding |
|---|---|---|
| `frontend/src/auth/CallbackScreen.tsx` | Four comment lines on the latch and the in-flight window. No behaviour change. | I7 |

Nothing else was touched. No refactoring, no optimization, no public API change, no rename.

## Verification after the change

- `tsc --noEmit` clean.
- `oxlint` exit **0**.
- Suite **818 passed / 1 failed of 819** — the known environmental `oxlint` AC4 case and no other, and no `Errors`
  line, so no unhandled rejection.

## Open questions

**None new.** OQ-2 and OQ-3 stay deferred with the owners Phase 4 named; F3 and F4 are handoffs to task 21 and are
recorded for Phase 13 rather than being questions.

---

## Next — Phase 10 — test generation. It owes I1, I2, I3, I4, I5, the array-body case and the unmount-in-flight case.
