<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production code changed. -->

# frontend · T47 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-run.txt` |

**No production file was changed in this phase.**

```
baseline whole suite: 0 failed of 2016, exit 0
regressions: 16/16 killed
canaries:    ['TR16', 'TR17'] - none survived
survivors:   ['TR15']
broken:      none
disagreements: ['TR14']
```

---

## How this phase chose what to write

**The harness was already 14/14 before this phase, so "find the surviving mutants" was not available as a
method.** A suite that kills everything aimed at it has only proved that the things aimed at it were aimed
well. So the question became the other one: **which criteria are written down somewhere and measured
nowhere?** Five were, and two of them were open questions whose deferral no instrument could see.

This is the opposite of T46's phase 10, which added rows for a surviving mutant. Recording the difference
because the method follows from the score, not from habit.

## The five cases added

| Case | Criterion | Why nothing held it before |
|---|---|---|
| `renders IDENTICALLY on a cold arrival and at the end of a submit` | AC2 + AC8 | Every other identical case compares **two journeys**, which differ only in what the server answered. This compares a journey with **no journey at all** — the strongest available form of "the screen has no inputs". A screen that took a prop, router state or a storage flag from the submit renders differently for a visitor who pasted the URL, and nothing in the suite could tell. |
| `is identical for a reply that is not JSON at all` | AC2 + AC4 | Both probe replies are well-formed JSON objects and the contract makes `message` **required**, so a client that parsed the body passed every row above and would fall over on an empty 202 — which is what a no-content 202 looks like on the wire, and what `JSON.parse` throws on. |
| `renders exactly one heading, so the document outline has no gap` | L11 | `Heading` has **no `level` prop**, and its own header says the first use of one would be a second `<h1>` or an orphan `<h2>`. Nothing counted headings. |
| `OPEN QUESTION PINNED - the acknowledgement offers nowhere to go` | D3, **OQ-T47-4** | The freeze deferred the sign-in link. **A deferral nobody can see is indistinguishable from an oversight**, so the absence is now an assertion: adding a `TextLink` breaks this case and names the question instead of letting a quiet edit answer it. |
| `OPEN QUESTION PINNED - a double submit sends TWO requests` | **OQ-T43-11** | Measured, not assumed, and it is the unhappy answer: two presses before the first reply send **two POSTs** — two verification emails for one registration — with no guard in task 43 or task 47. Pinned at today's behaviour so the question cannot be closed silently in either direction. It also pins the half that **is** safe: one destination, once, with the history entry replaced rather than stacked. |

## The two harness rows added

| Row | Outcome | Purpose |
|---|---|---|
| **TR18** the acknowledgement grows a `TextLink` | `KILLED/own`, 5 cases | Proves the OQ-T47-4 pin bites. It reuses an **existing** catalogue key deliberately, so the kill comes from the control assertion rather than from the key-set pin noticing a new word — **a mutant must isolate the hole it records.** |
| **TR19** a second `<h1>` appears | `KILLED/own`, 6 cases | Proves the outline pin bites — the failure `Heading`'s missing `level` prop exists to prevent. |

## Full coverage map

| AC / rule | Cases |
|---|---|
| **AC1** | `renders a non-empty acknowledgement at /app/register/sent` |
| **AC2** | byte-identical for two different bodies · identical for an undeclared 409 · identical for a non-JSON body · **identical for cold arrival vs submit** · the probes are asserted to differ |
| **AC3** | lands on `REGISTER_SENT_PATH` · **REPLACES rather than pushes**, flag and consequence · the three re-aimed AC11 rows |
| **AC4** | source scan (no `fetch`, `.status`, `data`, `error`, router hooks, storage, props) · exactly one request · **what was typed is what left the tab** |
| **AC5** | source scan (`setTimeout`, `setInterval`) |
| **AC6** | both keys as literal English · the catalogue sentence and **not** the server's message · no untranslated key reaches the DOM |
| **AC7** | focus via the real table · at the end of the real journey · under StrictMode · no tab stop · `<body>` control |
| **AC8** | cold arrival with no submit and no router state · **and it renders the same thing** |
| **AC9** | `Storage.prototype` spy on set/get/remove, keyed by key name · intercept control |
| **AC10** | `PUBLIC_ROUTE_PATHS` strings unchanged — every inherited membership pin |
| **AC11** | no lowercase tag · no props · barrel import · `RENDER_SITES` 9 |
| **AC12** | all 22 re-aimed sites green; the three AC11 rows kill TR11 |
| **L11** | focus (AC7) · catalogue-only (AC6) · **one heading, no orphan subheading** |
| **D3 / OQ-T47-4** | **the no-control pin** |
| **OQ-T43-11** | **the double-submit pin** |

## Not added, and why

- **No case for `TR15`** (useEffect → useLayoutEffect). It has no observable effect; a case that killed it
  would be pinning the hook rather than the behaviour, which is what `unfalsifiable` means.
- **No shared test-util module** (FU-T43-2). Out of scope, and now recorded a fifth time — Phase 9's I9 was
  the first copy of a helper between suites that carried a hole rather than just duplication.

**Phase 10 complete.** Next is **Phase 11 — Test Review (Kimi 2.7)**. STOP HERE.
