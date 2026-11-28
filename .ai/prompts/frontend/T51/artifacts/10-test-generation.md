<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production file changed. -->

# frontend · T51 · Phase 10 — Test Manifest

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-run.txt` |

**No production file was changed in this phase.**

```
baseline whole suite: 0 failed of 2137, exit 0
regressions: 6/6 killed   canaries: ['TR28'] none survived
survivors: none   broken: none   disagreements: none
```

---

## Two of Kimi's four additions cannot fail, and the reason is temporal

**I10** asks whether the live region announces the same thing **mid-flight** across arms. **I11** asks whether
the **request** is identical across arms. Both are settled by sequence rather than by care:

**the arms differ only in the reply, and the request is built — and the in-flight render has already
happened — before any reply exists.** At the moment either is observable, the outcome has not been decided, so
no mutant on this screen can make either vary by outcome. Writing them would have added two cases that cannot
fail.

*"Identical by construction"* is normally the phrase that precedes a vacuous case in this project, so being
exact matters: **it is not that the screen is careful to treat the arms alike — it is that nothing about the
arm is knowable yet.** Only post-settle state can vary by outcome, so only post-settle state earns a cross-arm
comparison. T50's *"mounts the live region EMPTY and fills it only while submitting"* owns the in-flight claim
for one arm, which is the right number for it.

### What can vary is the request a **retry** sends

By then one outcome has been seen and the screen has state. That is the half of **I11** with content, and it
is the case that got written: fail once, retry, and assert the second request's body, path, method and absent
`Authorization` — with the first attempt's failure asserted **first**, so the case cannot pass by never
retrying at all.

**TR29** kills it, along with T50's retry case.

## I8 — the obstacle Kimi named does not exist at this seam

Kimi declined a redirect arm *"because `fetch` follows redirects automatically"*, recommending it *"if a
practical stub can be built."*

**The transport is stubbed, so nothing follows anything** — the screen simply sees a `3xx` resolve. Checked
before writing rather than assumed: `new Response(body, { status: 302 })` constructs, as do `301` and `304`.

Two arms added — **`302` with a body and a `Location`**, because an edge redirecting a `POST` is a real
deployment shape, and **`304` with no body**. Both render the uniform acknowledgement. **Fifteen arms.**

## I9 — the count is now pinned instead of implied

`serverWords` is optional, so AC10's *"the server's own words never reach the DOM"* ran only for the arms that
had words. **The count was implied by the data and stated nowhere** — the shape of a claim that quietly
erodes, because the next person to add a wordless arm drops AC10's coverage and nothing says so.

Two changes. The plain-text arm now carries `'no account matched: nothing queued'` instead of `'OK'`, since a
proxy answering in plain text is exactly the kind of thing that would say **why**, and why is what must not
render. And a case **names the five arms that genuinely cannot carry words** — two empty bodies, `204`, `503`,
`304` — and asserts the other **ten** do.

**TR30 isolates it exactly**: adding a `418` wordless arm fails that one case and nothing else. It ran against
**2138** tests rather than 2137, because the mutant adds an arm and an arm is a case — the count itself
confirming the mutant did what its label says.

**TR30 mutates this task's own suite**, which is right for a coverage pin: the property is one *of the arm
list*, so the only way to falsify it is to change that list.

## The six rows

| Row | Kills | |
|---|---|---|
| **TR24** `parseAs` removed | 5 | **D-T51-2's two cases are two of the five** |
| **TR25** `parseAs: 'json'` | 5 | The same hole, the spelling a refactor picks |
| **TR26** email interpolated | 18 | Real, **non-isolating** (Phase 7 F2, Kimi I6) |
| **TR27** inert timer | 2, inherited | Retired D-T51-3 |
| **TR29** retry body differs | 2 | Mine plus T50's retry case |
| **TR30** a wordless arm added | **1** | Perfectly isolating |
| **TR28** CANARY | 21 | The acknowledgement instruments are live |

Both new rows were **hand-applied and verified to compile and kill before the run** — and TR30's target path
needed fixing first, because `OWN_TESTS` entries are vitest-relative while a mutation target needs the
`frontend/` prefix. **Checked against the filesystem rather than trusted.**

## Coverage after this phase

| AC | |
|---|---|
| **AC1–AC3** | 15 arms, each comparing `innerHTML` to a **captured, guarded** baseline |
| **AC4, AC9** | No arm renders `unsent`; the **whole location** per arm |
| **AC5** | A rejecting transport, from both sides |
| **AC6** | A different address renders identical markup |
| **AC7, AC8** | Focus and the live region, **collected and compared as whole lists** |
| **AC10** | Ten arms hold it, **and the number is now a case** |
| **AC11** | The response-read scan; the timer clause **named to its tree-wide owner** |
| **AC12, AC13** | No catalogue key; 2137/2137, lint 0, typecheck 0 |

## Still not done, and stated

| | |
|---|---|
| **OQ-T51-6** | **A measured bug on another screen** — `VerifyScreen` tells a visitor whose verification *succeeded* that their link is not valid, whenever the reply's body does not parse. Phase 9 measured it and wrote the table into that screen's source. **Not fixed here**: it needs a decision about what R4's *"fails for any reason"* means when the failure is the body. |
| **FU-T43-2** | `teardownCurrent` second copy, `type` fourth. |
| **OQ-T51-7, OQ-T51-8** | Unchanged. |

**Phase 10 complete. Next is Phase 11 — Test Review (Kimi 2.7). STOP HERE.**
