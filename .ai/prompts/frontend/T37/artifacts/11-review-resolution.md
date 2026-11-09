<!-- MODEL: Claude Opus 5 — Phase 11 (Review Resolution) -->

# frontend · T37 · Phase 11 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/11-test-review.md` |
| **Produces** | `artifacts/11-review-resolution.md` |

Thirteen findings. **Seven accepted and implemented**, **one rejected for the third time**, **one
accepted as a comment**, **four recorded with no action**. Every citation checked against source first —
and F1's citation was right while its *severity* was understated.

## Score after resolution

```
regressions: 35/35 killed     (29 → 35; SL31-SL36 added, SL25 rewritten)
survivors:   none
broken:      none
```

| Gate | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **29 passed (29)** |
| `vitest run` | **1691 passed / 1 failed of 1692** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

## F1 — accepted, and worse than the finding said

Kimi's citation is exact: `accessToken.ts:45` exports `accessToken` as a `LocalSession` **object** with
`current()`, `adopt()` and `renew()`. The sweep's entry was `/\baccessToken\s*\(/`.

So the pattern forbade **`accessToken(`, which is not a legal call anywhere in this codebase**, and
allowed every call that is. It was not an incomplete pattern; it was a pattern aimed at nothing.

**And it takes my own SL25 with it.** SL25 planted `accessToken() !== null` — the impossible shape. Its
kill proved two worthless things: that the scan matched a mutant no regression could produce, and that an
impossible call crashes at runtime. The rewrite tells the story in the numbers:

| | failures |
|---|---|
| SL25 as written in phase 9 (`accessToken()`) | **15** — the scan, plus a thirteen-case runtime-crash cascade |
| SL25 rewritten (`accessToken.current()`) | **2** — the scan and its control, and nothing else |

A legal read breaks nothing, which is exactly why it needed a source scan and exactly why the old mutant's
large kill count was meaningless. This is the **fourth** time in this task that a kill count said one
thing and the named failures said another.

The identifier itself is forbidden now (`/\baccessToken\b/`), which covers all three methods and anything
added later. `clearAccessToken` and `retainAccessToken` keep their own rows — neither contains a
lower-case `accessToken`.

**SL31 is F1's other half, and the worse one.** `adopt()` is not a read: a public page reachable by typing
its URL would be **writing** to the tab's session. It dies on AC2, AC8 and the sweep.

## Accepted

| | Finding | What it measured |
|---|---|---|
| **F2** | The import scan assumed single quotes | Accepted — a scan for a token read must not rest on formatter discipline. Quote-agnostic, with a deliberately **double-quoted** probe. **SL36** plants it. |
| **F3** | `useReducer`, `useMemo`, `useCallback`, `useContext` unforbidden | Accepted. This screen is deliberately static, so any hook beyond `useTranslation` is a deviation. **`useId` left out on purpose** — it is genuinely inert, and forbidding it would make this a style rule rather than a claim about behaviour. **SL35** plants `useContext`. |
| **F4** | The structure case checked tags, not semantics | Accepted. `<main role="region">` is no longer a main landmark and `<h1 aria-level="2">` is no longer level one, and **neither changes a tag name**. **SL32** and **SL33** plant them; SL32 is caught twice, since `role=` is also in AC5's scan. |
| **F8** | Nothing pinned the `to` prop to the constant | Accepted — and Kimi's "low priority" was wrong. **SL34** reads `SIGN_IN_PATH` inside the component (so AC13's scan passes) and hardcodes `to="/sign-in"`. The rendered href is **byte-identical**, so AC5's comparison passes too. The new F8 case is the **only** thing that catches it. |
| **F11** | The count control could not see a duplicated row | Accepted. A duplicate would mean one surface swept twice and another not at all, with the length still looking right. |

The sweep is now **26 rows**, each with a probe, with the count and the distinctness of the patterns both
pinned.

## F5 — the file name · rejected for the third time, with the fallback taken

Raised in phase 3 (F6), phase 8 (I4) and again here. The reason has not changed: `tasks.md:65` and `:71`
name the **same** test — `publicRouteSetIsExactlyTheDeclaredSet` — for task 37 and task 40. Renaming for
T37 either breaks the convention that a test file is named after its named test, or produces two files for
one named test and leaves task 40 deleting one.

Kimi's own fallback is reasonable and is now taken: the explanation is repeated **in the `describe` block**,
not only in the file header, so a reader who opens the suite rather than its top still sees it.

## F7 — the numbering gap is real, and left open deliberately

There is no SL24; the label was skipped when phase 9's six were written. **Not renumbered**, because
artifacts 09 and 10 record narrow verifications by mutant name, and renumbering would make every one of
those citations point at a mutant that no longer has that name. The harness says so where the gap is.
Thirty-five mutants, SL01–SL36.

## Recorded, no action

| | |
|---|---|
| **F6** | `APP_BASENAME` spelled literally in `endSession.ts` — not observed, and Kimi's own recommendation is "none unless a future mutant demonstrates it". |
| **F9** | The runtime case stubs `fetch` and not `XMLHttpRequest`; the source scan is the real guard and covers it. Kimi agrees. |
| **F10** | Attributes encoding a token — out of scope for the criteria, and `className`/`style` are not rendered at all. |
| **F12** | Nothing calls `endSession()`. **OQ-T36-5**, eighth consecutive dormant task; no test in this task can close it. |
| **F13** | `routes.tsx:5`'s false consequence. **FU-T37-2**, task 16's closed file. |

## What the two Kimi gates changed, in one line

Phase 8 found that **rendered-output comparison cannot see a read**; phase 11 found that **the scan written
to fix that was aimed at a call shape that cannot exist**. Both were real, and the second one invalidated a
mutant of mine that had been counted as a kill.

## Phase 12

Specification verification.
