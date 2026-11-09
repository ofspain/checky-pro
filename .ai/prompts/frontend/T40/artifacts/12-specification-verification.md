<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification) -->

# frontend · T40 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | all prior artifacts |
| **Produces** | `artifacts/12-specification-verification.md`, `artifacts/12-mutation-run.txt` |

## Final gates

| | |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| named suite | **49 / 49** |
| `vitest run` | **1744 / 1745** — the one known host-environmental `lintRejectsHandWrittenBackendFetch` case |
| harness | **17 mutants · 16/16 regressions killed · 1 unfalsifiable scored apart · 0 broken · 0 survivors · 0 disagreements**, every kill `KILLED/own` |

---

## 1 · Traceability matrix

| Requirement / decision | Implemented? | Evidence (`file:line`) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R28** first clause — unauthenticated visitors are served **only** §4c's public routes | **yes** | `auth/RequireSession.tsx:35-42` · `app/routes.tsx:74` | `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:900` (eleven blocked), `:943` (served with a session), `:954` (**the AC3 control**), `:961` (nine public served) | no | no |
| **R28** second clause — redirect anything else to the sign-in start | **no, by design** | `RequireSession.tsx:17-22` names task 41 | `:981` asserts `historyAction` stays `POP` on a blocked path, so the redirect **cannot** have been taken here | **task 41** | no — scoped out at phase 2 and frozen at phase 4 |
| **R19** the signed-out landing is one of the nine, and its screen exists | yes (task 37) | `routes.tsx:35` · `SignedOutScreen.tsx` | `:178` (task 37's) and `:961` now covers it as one of the nine | no | no |
| **L14** §4c verbatim: nine public, eleven authenticated, "everything else requires a session" | **yes** | `routes.tsx:27-52` (arrays unchanged) · `:74` (the split) | `routesResolveUnderAppPrefixOnly.test.tsx:141-152` (both arrays literal), `:159` (tree shape incl. the gate's level), `publicRouteSet…:1000` (the gate's children are **exactly** the authenticated array) | no | no |
| **L1–L3** the guard asks the session owner; no new surface, no new storage | **yes** | `RequireSession.tsx:41` — `Boolean(accessToken.current())`, the module's only outward reach | `:1053` (50-pattern sweep), `:1062` (its control), `:1086` (it **does** read the session owner) | no | no |
| **L11** a blocked route renders no copy; text comes only from the catalogue | **yes** | `RequireSession.tsx:42` returns `null` | `:900` — `container.innerHTML === ''`, mounted without the shell so the container **is** the outlet's subtree | no | no |
| **L12, L13** `app/` owns the router; the shell is the mount root | **yes** | `routes.tsx:74` — the gate is a route, not a wrapper around the shell | `:1000` (structure), `routesResolveUnderAppPrefixOnly:159` (shape) | no | no |
| **L16** nothing added to the `sessionStorage` carve-out | **yes** | no storage call exists in the guard | `:1053` forbids `sessionStorage`, `localStorage`, `document.cookie`, `indexedDB` with paired probes | no | no |
| **L15** part of the auth gate that must precede payment surfaces | **yes** | `routes.tsx:74` gates `invoices`, `invoices/:id`, `payments`, `receipts/:id` among the eleven | `:900` iterates **all eleven**, so the four payment paths are each measured | no | no |
| **design.md:203** guards live in `auth/` | **yes** | `auth/RequireSession.tsx` | `:1091` — no `from '../app/`, no dynamic `import('../app/`, and `app/` names it | no | no |

## 2 · Acceptance criteria

All thirteen, from the frozen brief. AC12 and AC13 were added at phase 4 from Kimi's phase-3 findings.

| | criterion | verdict | what holds it |
|---|---|---|---|
| **AC1** | the nine public paths resolve and render without a session | **PASS** | `:961` all nine · `:1104` trailing slash · `:1113` query string, query preserved · `:967` the catch-all |
| **AC2** | none of the eleven serves content without a session | **PASS** | `:900` all eleven · `:915` under `StrictMode` · `:921` an empty-string token is not a session · `:1098` with a query string and a trailing slash |
| **AC3** | **AC2 turns red when the guard is removed** | **PASS** | `:954` — same table, same eleven paths, same probes, a pass-through gate, and the content arrives. RS01 confirms the direction: deleting the gate's question kills five cases |
| **AC4** | with a session, an authenticated path is served | **PASS** | `:943` all eleven · `:935` under `StrictMode` with a session (phase 11 F5) |
| **AC5** | the sets are disjoint and exactly §4c's, counts **derived** | **PASS** | `routesResolveUnderAppPrefixOnly:149` · and `withProbes` at `:216` derives its own probe count from the two arrays rather than writing a number |
| **AC6** | the guard lives in `auth/`; `app/` imports it, never the reverse | **PASS** | `:1091`, both directions, static and dynamic imports |
| **AC7** | the guard stores, writes, sends, navigates, holds state and reads routes — **nothing** | **PASS** | `:1053` fifty patterns · `:1062` the control: each fires against **the real source plus its probe**, the real source trips **none**, the length is pinned, and no two rows share a pattern |
| **AC8** | a blocked route renders **no text at all** | **PASS** | `:900` asserts `innerHTML`, not `textContent` — RS05 (an empty `<div>`) survived 1725 tests at phase 7 and dies here |
| **AC9** | adding a path to one list without the other fails a test | **PASS** | `routesResolveUnderAppPrefixOnly:141-152` (both arrays literal) **and** `:1000` (the gate's children are exactly one of them) |
| **AC10** | a sessionless visit to a public path performs **no navigation** | **PASS** | `:971` all nine, `historyAction === 'POP'` · `:981` the same on a **blocked** path, which is what keeps task 41's clause out |
| **AC11** | sibling instruments are **grown, not bypassed** | **PASS** | `routesResolveUnderAppPrefixOnly:159` names the gate's level explicitly; `:175` keeps its three with the filter's new load-bearing role written above it. RS10 and RS17 both die there |
| **AC12** | the guard's reach is exact — one pathless route, children exactly the eleven, public nine and catch-all **siblings** | **PASS** | `:1000`. RS12 — which changes **no behaviour at all** — dies on this and on the tree shape |
| **AC13** | **no element hides where the count pins cannot look** | **PASS** | `:1016` plus its control at `:1033`. RS14, measured at phase 9, **survived all 1725 tests** before this case existed |

## 3 · Deviations

| | | |
|---|---|---|
| **D1** | Phase 6 modified **three** files, not the two planned. Task 33's AC11 walker and task 25's chain control both fired; phase 5's risk table predicted neither. Both **grown to admit new state** under the closed-task rule, both recorded. | accepted |
| **D2** | The gate's condition is named (`const hasSession = …`) rather than inlined, because `Boolean(...)` in a condition position is a cast oxlint strips as redundant. The frozen expression is unchanged. | accepted |
| **D3** | ~~No explicit return type~~ — **closed at phase 9**: `RequireSession(): ReactElement | null` at `RequireSession.tsx:35`. | closed |
| **D4** | Phase 9 edited two **comments** in task 37's suite that T40 had made false (*"currently untestable"*, *"an auth gate that does not exist yet"*). Permitted under the closed-task rule — the statements had become false — and both say what changed rather than being deleted. | accepted |
| **D5** | The harness's `expectation` field was **redefined** between phase 7 and phase 10, from *where* a kill would come from to *killed by a case this task owns*. The phase-7 and phase-9 runs are kept so the earlier predictions and their five better-than-predicted outcomes remain readable. | accepted |
| **D6** | Phase 10's AC7 control was weaker than T37's, and phase 11's F2 is what surfaced it. Bringing it up found **two** more gaps F2 had not named: the probe was tested in isolation rather than against the real source, and the real source was never checked for already tripping a row. | accepted, fixed |
| **D7** | **Caught at this phase, not by a review:** task 37's describes in the shared file are numbered AC1–AC13 and so are task 40's, against a different frozen brief — so the file held two AC10s, two AC12s and two AC13s, and a failure naming "AC12" would not have said whose. Every T40 block is now prefixed `T40`. This is the file-level twin of the collision T39's matrix caught in its own brief. | accepted, fixed |

## 4 · Phase 11 findings — verdicts

**Two of the fourteen are false against source**, and both describe the state **before phase 9 fixed them**.
Checked, as every Kimi citation in this task has been:

| | | |
|---|---|---|
| **F3** | *"`RequireSession` still has no explicit return-type annotation… Phase 10 did not add the annotation."* | **REJECTED — false.** `RequireSession.tsx:1` is `import type { ReactElement } from 'react';` and `:35` is `export function RequireSession(): ReactElement \| null {`. Phase 10 did not add it because **phase 9 did**, from the same finding (I2/F8). |
| **F14** | *"The `routes.tsx` import-cycle comment still does not mention `RequireSession`."* | **REJECTED — false.** `routes.tsx:6-7`: *"The three imports below it are not part of that cycle (phase 8, I13): the two screens and the T40 guard…"* Added at phase 9 from I13. |
| **F1** | `GUARD_FORBIDDEN` misses `adopt`, `renew`, and the cross-tab primitives | **ACCEPTED, fixed.** Eight rows added. The point is exact: the sweep forbade the standalone **writers** and said nothing about methods on the object the guard already imports, so `accessToken.adopt('x')` — which delegates to `retainAccessToken` — would have passed. The cross-tab rows matter more: a render that set the sign-out latch would decide whether a **sibling tab** may adopt a renewal |
| **F2** | no duplicate-pattern check in the control | **ACCEPTED, fixed — and it was worse than the finding.** See **D6** |
| **F4** | no blocked-path case with a query string or trailing slash | **ACCEPTED, fixed** at `:1098`. The justification is honest: those two are an *inference* from a source scan, and a route-reading regression is exactly what breaks them while the bare-path cases stay green |
| **F5** | no `StrictMode` case **with** a session | **ACCEPTED, fixed** at `:935` |
| **F7** | `document.title`, `window.name`, `history.state` missing | **ACCEPTED, fixed**, for parity with T37's privacy sweep |
| **F10** | a dynamic `import('../app/…')` walks past the static-import pattern | **ACCEPTED, fixed**: two rows added |
| **F6** | the gate is dormant in production | **ACCEPTED, no action** — see §6 |
| **F8, F9, F11, F12, F13** | the key-coverage assertion, `historyAction`, catch-all with a session, the probe's text, the file header | **ACCEPTED as written, no action** |

Running total across the three Kimi gates: **18 + 15 + 14 = 47 findings**, of which **three were refuted by
measurement or source** (phase 8's I9, phase 11's F3 and F14) and four remedies were refused at phase 4 with
the measurement that refuted them.

## 5 · The principal engineer's four questions

**(1) Is the task fully complete?** **Yes, for the half it owns.** R28's first clause has a mechanism, that
mechanism is exact — one pathless gate over exactly the eleven — and every claim is measured against a
control rather than against the absence of a screen. R28's **second** clause is task 41's, scoped out at
phase 2, frozen at phase 4, and now *pinned shut* by `:981`: a blocked path must not navigate, so T40 cannot
quietly take task 41's work.

**(2) Does it satisfy every acceptance criterion?** **Yes, all thirteen** — §2, each with a named case, no
PARTIAL. Two of them (AC12, AC13) came from the phase-3 challenge, and AC13 is the one the task would have
shipped without: the mutant it owns survived all 1725 tests until phase 10 wrote it.

**(3) Does it violate any LOCKED decision?** **No.** L14's arrays are byte-identical. L1–L3 are honoured by
a 50-pattern sweep whose every row fires against the real source. L11's "no copy" is `null`, asserted as an
empty subtree rather than empty text. L16 adds no storage key. L12/L13 keep the router in `app/` and the
shell as the mount root — the gate is a route, not a wrapper. `design.md:203`'s placement is met and scanned
in both directions.

**(4) Remaining risks.**

| | |
|---|---|
| **The gate is dormant** (F6, OQ-T36-5) | No authenticated screen exists to be gated — task 42, blocked on 173/174 — so R28's first clause is verified **by probes**, not by a real screen. This is the tenth consecutive task in that position. The handoff pins are what will make it visible: task 42 cannot attach a screen without `routesWithElements`, T37's AC9 and `pathTree` all firing |
| **OQ-T40-1, the read is not reactive** | A token arriving with **no navigation** leaves a mounted blocked route blocked. Nothing in `src/` does that, and closing it needs a subscription on the session owner this task may not add. Documented in the guard's header, where a reader of the guard finds it |
| **A held token is not a usable account** | A SUSPENDED account keeps a working token; only `/accounts/me` can say otherwise (task 31). The gate claims exactly what it checks, and its header says so |
| **RS04 is unfalsifiable** | `null` and `<></>` both render nothing. Scored apart, never as a kill or a loss |
| **Spec drift, unchanged** | `design.md:137` still reads *"PROPOSED … pending author confirmation (Q6)"* while `package.md:368` records Q6 resolved on 2026-10-10. `spec/` is not modified by this task — **owner's** |

## 6 · Verdict

**PASS** — R28's first clause is implemented as one exact pathless gate and measured against a control, all
thirteen acceptance criteria hold with named cases, 16/16 regressions die by this task's own cases, and the
only open items are the owner's (spec drift) or a later task's (the dormant gate, task 42).
