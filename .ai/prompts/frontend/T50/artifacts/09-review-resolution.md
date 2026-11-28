<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T50 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Status** | **COMPLETE** — no owner decision was required at this gate. |

**No code changed in this phase**, so the Phase 7 harness result stands as the record:

```
18/18 regressions killed · canary dead · survivors ['TR20'] (declared) · 0 broken · 0 disagreements
```

Kimi re-ran the named suite, `tsc`, `oxlint`, the whole suite **and the harness** and reproduced every number.

---

## Nothing needed ratifying, and that is worth stating

**Both deviations were ratified at Phase 4, before implementation**, so this gate had no decision to make.
`D-T50-1` (the `unsent` retry line, reversing Kimi's Phase 3) and `D-T50-2` (one `<Heading>`, forced by
measurement) were each put to the owner with the measurement attached, and Kimi's **I1 and I2 confirm both are
implemented as ratified and held by cases**.

**This is the first task in four where Phase 9 had no deviation outstanding** — and the contrast is the point:
T47, T48 and T49 each reached this gate with deviations discovered *during* implementation. Ratifying at Phase
4 cost one question and removed a whole class of late surprise.

## Kimi's fourteen findings — disposition

**Eleven positive or observational, one Medium, two Minor.** Every file:line citation was checked before being
accepted.

| Finding | Severity | Disposition |
|---|---|---|
| **I1** D-T50-1 implemented as ratified | Positive | Noted |
| **I2** D-T50-2 implemented as ratified | Positive | Noted |
| **I3** the three Phase 7 gaps are real | **Medium** | **ACCEPTED** — *"Address in Phase 10 as planned."* |
| **I4** the bare no-session case is vacuous without the seeded one | Observation | **ACCEPTED** — it confirms F6 rather than finding something new |
| **I5** the response-body scan is comprehensive | Positive | Noted |
| **I6** the T49 handoff replacement is strong | Positive | Noted — TR18 is the mutant that proves it |
| **I7** the storage spy is correctly built | Positive | Noted |
| **I8** the acknowledgement renders in place | Positive | Noted |
| **I9** focus and announcement split correctly | Positive | Noted |
| **I10** the `\bresponse\b` ban is broad but safe | Minor | **ACCEPTED as stated.** A `responseTime` helper would slip past and a `response` binding would not, which is the right way round for what the scan defends |
| **I11** no case asserts the email is absent from the path | Minor | **ACCEPTED** — and it belongs with I3's URL case in Phase 10 |
| **I12** catalogue keys are flat | Positive | Noted |
| **I13** the seven pins are all updated | Positive | **ACCEPTED with two corrections — below** |
| **I14** OQ-T50-6 documented | Observation | Noted |

### I13's conclusion outruns its own evidence

The finding lists seven pins, marks two of them **`need to verify`**, and then concludes *"All seven pins
appear updated from reading the test files."* **Two entries say the check was not done and the verdict says it
was.**

**Both were verified here, and both are correct:**

| Pin | Evidence |
|---|---|
| `routesResolveUnderAppPrefixOnly.test.tsx:37` | `'resend-verification',` present in `SCREENS_ATTACHED_SO_FAR`, sixth, after `'verify'` |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:721, :768` | `describe('AC9 exactly eight screens are attached'` and `RESEND_VERIFICATION_PATH` in the attached list, imported at `:43` |

So the verdict was **right by luck rather than by evidence**, and a green whole suite is not the same proof: it
shows the pins and the code agree, not that a particular pin names this screen. Recorded because the next
reader would take a `Positive` at face value.

### I13 is also missing three of the files this task touched

Its list names seven. **The task touched twelve files** — two created and ten modified:

```
created   features/account/ResendVerificationScreen.tsx
created   features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx
modified  app/routes.tsx · i18n/catalogue.ts
modified  app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx
modified  app/routesResolveUnderAppPrefixOnly.test.tsx
modified  app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx
modified  auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx
modified  features/account/verifyFailureShowsOneUniformMessage.test.tsx
modified  app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx            <- not in I13
modified  auth/unauthenticatedRoutesRedirectToSignIn.test.tsx                  <- not in I13
modified  features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx  <- not in I13
```

**The three it cannot see are the second-order pins**, and the reason is structural rather than careless:
**they are recorded in `06-implementation-notes.md`, and Phase 8 consumes `07-self-review.md` alone.**

## FU-T50-1 · The generator fix I shipped this morning was incomplete

`.ai/generate.py` was changed today so phases 6, 9, 10 and 11 each receive the earlier artifact they need
(FU-T49-1). **Phase 8 still consumes its predecessor alone**, and this task is the evidence that it needs one
more: an independent **code** review reading only a self-review is reviewing a summary of the change rather
than the record of it, and it missed a quarter of the files as a result.

**Recommended:** Phase 8 should also consume `artifacts/06-implementation-notes.md`.

**Not applied here, deliberately.** Regenerating rewrites the prompts of every task in every service,
including the Phase 11 prompt **this task has not used yet** — changing an instrument mid-measurement. The
clean moment is a task boundary, and this is recorded so it happens at one rather than being forgotten.

## Final state

| | |
|---|---|
| Suite | **2113 / 2113**, 41 files |
| Named suite | **27 cases** |
| `lint` / `typecheck` | 0 / 0 |
| Harness | **18/18**, canary dead, 0 broken, **0 disagreements** |
| Deviations | **D-T50-1, D-T50-2 — both ratified at Phase 4** |

## Carried forward

| ID | |
|---|---|
| **OQ-T50-7** | **Phase 10's**: the email in no URL (I3, I11), the email in no console with a **derived and asserted** method list, and the failure→retry→**success** journey driven end to end. |
| **OQ-T50-5** | **T51's**: the `unsent` line appears for a transport failure **and for nothing else**. |
| **OQ-T50-6** | A `500` resolve says *"Check your email."* Deliberate and documented. |
| **FU-T50-1** | Phase 8 should consume the implementation notes. |
| **FU-T43-2** | Still no shared test-util module; `type` is copied a fourth time. |
| **R-T48-1** | Unchanged. |

**Phase 9 complete. Next is Phase 10 — Test Generation.**
