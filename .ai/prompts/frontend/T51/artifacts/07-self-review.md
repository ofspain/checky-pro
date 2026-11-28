<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). -->

# frontend · T51 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

```
baseline whole suite: 0 failed of 2133, exit 0
regressions: 4/4 killed   canaries: ['TR28'] none survived
survivors: none   broken: none   disagreements: none
```

---

## F1 · TR24 and TR25 die by **five** cases, and two of them are the deviation

```
TR24 parseAs is removed …  (5 failed of 2133)
  └ renders markup identical to the declared reply for: 200 with an HTML body, as an edge error page sends
  └ renders markup identical to the declared reply for: 200 with a plain-text body
  └ renders markup identical to the declared reply for: 200 with JSON truncated mid-document
  └ ends with focus on the same element for every outcome
  └ ends with the live region saying the same thing for every outcome
```

**The focus case and the live-region case are two of the five kills**, and they are there only because of
**D-T51-2**, which Kimi's design did not contain. Had the design shipped unamended, this defect would be held
by three instruments instead of five — and more importantly, the **focus channel would have been permanently
unmeasured**, because `innerHTML` does not carry `document.activeElement`.

**Reading which cases die, not how many, is what makes that visible.** The row would have scored a kill either
way.

## F2 · TR26 has a real kill and **does not isolate the case it was written for**

```
TR26 the acknowledgement interpolates the submitted address  (18 failed of 2133)
```

Eighteen cases, and **four of them are T50's**, written before this task existed. My differing-email case is
the eighteenth name in the list.

So the row demonstrates the **suite's** strength and not that case's. It is kept because the mutant is a real
regression that must die, and recorded here as **non-isolating**, because T36's rule is that *a mutant must
isolate the hole it records* and this one does not.

### And the attempt to make it isolating found a BROKEN mutant I nearly adopted

The one leak shape T50's `textContent` equality cannot see is an **attribute**. So I tried
`<Text data-sent-to={email}>`:

| Step | Result |
|---|---|
| `tsc --noEmit` | **clean** — so I believed the axis was live |
| Cases killed | **exactly one**, and it was T50's **source scan** |
| Why | The scan forbids `\bdata\b`, aimed at `response.data`. **`data-sent-to` contains `data`.** |
| Why nothing else fired | **No primitive spreads props.** `Text` destructures `{ children, id }` and forwards nothing, so the attribute compiled, rendered nothing, and changed no DOM. |

**A mutant that compiles, changes nothing, and is "killed" by a pattern aimed at something else is exactly the
BROKEN-scored-as-KILLED trap this project has hit three times.** I caught it only by reading *which* case
died and finding the kill made no sense.

**The useful fact underneath:** the design system prevents attribute leaks **structurally** — not by its
types, which allow `data-*`, but by never spreading props. So the differing-email case's only live axis is
text, which T50 already holds, and the case stands as a statement of the claim rather than as a unique
instrument.

## F3 · **D-T51-3 was a duplicate, and I corrected one false claim with another**

| Where | Claim | Truth |
|---|---|---|
| **Phase 1** | *"The screen has no timer, which an existing source scan already forbids."* | Right that one exists, **wrong about where** — named no file, and I was picturing T47's. |
| **Phase 4** | *"L4's fourth clause has no instrument at all here"*, so D-T51-3 adds two patterns. | **Also wrong.** |
| **Measured** | `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1576` owns *"finds no timer outside the three permitted modules"* and *"the permitted rows describe exactly the timers that exist, with no slack"* | A tree-wide scan with a **pinned allow-list**. |

**Mine was two patterns over one file; the real one covers every production module and asserts its own
allow-list has no slack.** D-T51-3's patterns are **removed**, and the owning cases are named by their exact
assertions where the patterns were — T173's rule: *decline the duplicate, but name the owner so a rename
breaks a test.*

### The harness found this, and only because the control was built to isolate

TR27 was written as D-T51-3's control. A first version that changed the status killed **23 cases** and would
have proved nothing — the scan's kill buried among 22 behavioural ones. The **behaviourally inert** version
(`() => undefined`) killed **3 cases in 2 files**, and the second file was one **nothing in this task had ever
mentioned**.

After the removal it kills **2**, in that file alone — the count dropping by exactly the duplicate I had
added, which is the measurement that confirms it was one. TR27 is retagged **`inherited`** and kept, so the
tree-wide instrument stays visible from here.

## F4 · What the five harness rows establish

| Row | Kills | |
|---|---|---|
| **TR24** `parseAs` removed | 5, all mine | The fix, and **D-T51-2's two cases are two of the five** |
| **TR25** `parseAs: 'json'` | 5, all mine | The same hole by the spelling a refactor picks |
| **TR26** email interpolated | 18 | Real, **non-isolating** (F2) |
| **TR27** inert timer | 2, inherited | Retired D-T51-3 (F3) |
| **TR28** CANARY | 19 | The acknowledgement instruments are live |

## F5 · The cases failed before the fix — the one thing this task could not do without

```
× 200 with an HTML body …   × 200 with a plain-text body   × 200 with JSON truncated mid-document
× ends with focus on the same element …   × ends with the live region saying the same thing …
  Tests  5 failed | 42 passed (47)
```

The hole was invisible to 2116 tests, so **a case that passed before the fix would have measured nothing** —
and that is precisely what happened one task ago to a case written for *"the email in no URL."*

## F6 · The location assertion was unkillable, and is not now

D-T51-4 asserted the **pathname**. Any navigation that changes a pathname renders a different screen, so the
markup comparison fires first and **no mutant can kill the pathname assertion alone** — an instrument nobody
could ever see fail. A `navigate('?sent=1')` leaves the pathname untouched, so it now captures
**pathname + search + hash**.

**Still honest about it:** no row in this harness isolates it either. It is a named instrument for a clause
`innerHTML` otherwise holds incidentally, and the naming is for maintenance.

## F7 · Gaps for Phase 10

| | |
|---|---|
| **No arm drives a REDIRECT status** | `3xx` is absent from the thirteen. `openapi-fetch` follows redirects via `fetch`, so the arm would need a `302` the stub does not follow — **I do not know** what it renders, and *not knowing* is the reason to write it. |
| **`serverWords` is optional, and four arms omit it** | Four arms (`null`, `''`, plain text `OK`, `503`) have no distinctive sentence, so **AC10 is held by nine arms, not thirteen**. Defensible — there are no words to look for — but the count should be stated rather than implied. |
| **The region case asserts the END state only** | A region that announced something different *mid-flight* per outcome would pass. T50 holds the in-flight line for one arm. |
| **No case asserts the request is identical across arms** | Uniformity of what the SPA *renders* is proven; uniformity of what it *sends* on a retry after each arm is not. |

## F8 · Carried open items

**OQ-T51-6** (`RegisterScreen`/`VerifyScreen` read `response.ok`, so a `SyntaxError` reaches them by a
different path) — **the highest-value open item in this task**, and out of scope by `tasks.md`.
**OQ-T51-7**, **OQ-T51-8**, **FU-T43-2** unchanged.

---

**Phase 7 complete. Next is Phase 8 — Independent Review (Kimi 2.7). STOP HERE.**
