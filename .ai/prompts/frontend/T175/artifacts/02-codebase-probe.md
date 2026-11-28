<!-- MODEL: Claude Opus 5 — Phase 2 (Codebase Probe). Everything below was MEASURED. Working tree clean. -->

# frontend · T175 · Phase 2 — Codebase Probe

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-codebase-probe.md` |

**No production file is left changed.** Every number below comes from a run.

---

## 1 · The hypothesis holds, including the row that could have sunk it

Phase 1 said `parseAs: 'stream'` **should** keep `response.ok` readable, and flagged one row as fatal if it
did not: **a dropped connection must still render the failure**, because R4's *"any reason"* genuinely covers
it. Measured, with the option applied to `VerifyScreen`:

```
MUST: 204 no body -> SUCCESS   got=success  OK
MUST: 400 -> FAILURE           got=failure  OK
MUST: 410 -> FAILURE           got=failure  OK
MUST: 500 -> FAILURE           got=failure  OK
DEFECT: 200 HTML -> SUCCESS    got=success  OK
DEFECT: 200 trunc -> SUCCESS   got=success  OK
MUST: dropped -> FAILURE       got=failure  OK     ← the row that could have sunk it
```

**Seven for seven.** `openapi-fetch` still resolves with `response` — only `data` becomes the stream — so
`response.ok` keeps distinguishing a genuine `4xx`/`5xx`, R4 is preserved rather than redefined, and the two
defect rows now reach the success state R3 demands. `tsc --noEmit` clean.

**So the fix is the same one line on both screens, and the Phase 3 question is narrower than Phase 1
feared** — no third state, no new copy, no new catalogue key.

## 2 · `RegisterScreen` measured, and R2's violation is plainer than expected

```
202 valid JSON (declared)  formStillThere=n  text="Check your emailCheck your email to finish"
202 no body                formStillThere=n  text="Check your emailCheck your email to finish"
202 HTML body              formStillThere=Y  text="Create your accountEmail addressPasswordCr"   ← ■
202 truncated JSON         formStillThere=Y  text="Create your accountEmail addressPasswordCr"   ← ■
```

**The form just sits there.** No acknowledgement, no error, no navigation — the visitor presses the button and
the screen is unchanged. R2 requires *"the same 'check your email' screen … with identical copy and layout"*,
and **showing no screen is not showing that screen.**

It is also the **worse failure mode of the two**, in one respect T51's screen did not have: the account **was
created** and the verification email **was sent**, so a visitor who concludes nothing happened and submits
again hits a second registration for an address that already exists.

## 3 · The sweep — six call sites, and only two remain to fix

Phase 0 asked for every generated-client call classified by what its reject arm does, rather than fixing the
two screens `tasks.md` happens to name.

| Site | Reads the body? | Verdict |
|---|---|---|
| `ResendVerificationScreen.tsx:98` | No | **Fixed by T51** |
| `VerifyScreen.tsx:100` | `response` only, never `data` | **This task** |
| `RegisterScreen.tsx:95` | No | **This task** |
| `codeExchange.ts:114` | **Yes** — `const { data } = await …POST('/oauth2/token')` | **Correct as-is.** A token response that does not parse means **no token**, which is a real failure. |
| `readProfile.ts:125` | **Yes** | **Correct as-is.** |
| `readAccount.ts:157` | **Yes** | **Correct as-is.** |

**The defect is exactly bounded: it affects only the three call sites that do not read the body**, and one is
already fixed. The three readers legitimately need a parsed body, so for them a parse failure *is* a failure —
**which is why the fix is not "add `parseAs` everywhere"** and why a house-rule scan (OQ-T175-4) has to
distinguish readers from non-readers rather than ban the pattern.

## 4 · The repair is invisible to the existing suite — **again**

**Whole suite with both fixes applied: 2137 / 2137. Nothing moved.**

This is the second task running where the repair's blast radius is zero, and the reason is the same: the
contract declares `204` for `verifyEmail` and `202` for `registerAccount`, **every fixture answers the way the
contract says**, and the defect only exists for replies the contract does not describe.

So T175 brings the only instruments that will ever hold this, and — per T51's Phase 5 — **the cases must fail
before the fix or they measure nothing.** Measured target states for the three new arms:

| Arm | Before | After |
|---|---|---|
| `VerifyScreen`, `200` + HTML | *"This verification link is not valid."* | *"Your email has been verified."* |
| `VerifyScreen`, `200` + truncated JSON | *"This verification link is not valid."* | *"Your email has been verified."* |
| `RegisterScreen`, `202` + HTML | the form, unchanged | the acknowledgement |

## 5 · What Phase 3 should still decide

| ID | |
|---|---|
| **OQ-T175-1** | **Narrowed by measurement.** No third state is needed — `response.ok` survives, so R3 and R4 both hold with one option and no new copy. The question is now only whether the owner agrees the R3-over-R4 reading is right. |
| **OQ-T175-2** | Both screens in one unit. **`tasks.md` names both**, the mechanism is one, and the sweep shows the set is closed at two. |
| **OQ-T175-3** | **No third caller to decide about** — the sweep found three readers and all three are correct as-is. |
| **OQ-T175-4** | **The most valuable question, and now better posed.** A scan cannot simply require `parseAs` — three legitimate readers must not have it. It would have to say: *a call whose settle handler does not read `data` must pass `parseAs: 'stream'`*. That is checkable from source, and without it the fourth non-reading screen reintroduces this. |

## 6 · Probe honesty

The `RegisterScreen` probe printed a `navigated=` column that read `n` on **every** row, including the two
that plainly acknowledged — the suite uses a memory router, so `window.location` never moves. **It was a
constant, not evidence**, exactly the shape Phase 9 of T51 recorded one day earlier. The `text=` column is
what carried the finding, and it was there because the lesson was already written down.

---

**Phase 2 complete. Next is Phase 3 — Design Challenge (Kimi 2.7). STOP HERE.**
