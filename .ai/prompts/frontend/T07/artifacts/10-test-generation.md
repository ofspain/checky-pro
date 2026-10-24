# frontend · T07 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T07 — 429 generic copy |
| **Spec section** | Phase 1 — Foundation |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/09-review-resolution.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/10-test-generation.md` |

Created `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` — **26 cases, all green**. No
production file was touched (`git status`: the test file is the only change).

The frozen brief's 12 required cases are all present; three of them expand into `it.each` tables or a second `it`
where the brief asks for separate assertions, and two cases are the phase 9 additions. Vitest + jsdom, rendering
through `react-dom/client` + `act` as T01 does, with the transport stubbed via `vi.stubGlobal('fetch')` and T05's
browser-style `Request` resolution. (The generic phase text's JUnit/Testcontainers conventions are backend ones; the
frozen brief's Required Tests section governs here, and `agents.md` names Vitest for the SPA.)

## Test manifest

| # | Case (as named in the file) | Frozen case | Verifies |
|---|---|---|---|
| 1 | shows the generic copy for a 429 on a protected operation | 1 | AC1, R23 |
| 2 | shows it for a 429 on the public password-reset operation, the one live SPA 429 today | 2 | AC1, R23, L4 (asserts the operation is in `AUTH_PUBLIC_OPERATIONS` and goes out with no bearer) |
| 3 | shows it for a 429 on the boundary's re-send after a renewal | 3 | AC1, frozen finding 4 (the notice middleware is registered **first**, so it runs `onResponse` last and sees the re-send's 429, which bypasses middleware) |
| 4–10 | shows the same copy, and no server text, for … (7 rows: `Retry-After` 1 / 3600 / absent / an HTTP date; another problem title and detail; an empty body; a non-JSON body) | 4 | AC2, Q12(b). Asserts the **whole** rendered notice equals `message + dismiss` from the catalogue, so no title, detail, header or body text can reach it |
| 11 | handling a 429 calls neither `setTimeout` nor `setInterval` | 5 | AC3. Run with no component mounted, so React's own scheduling cannot mask or fake a timer |
| 12 | renders no digit and no time unit | 5 | AC3, R23. Asserted on `textContent`, per phase 9 — the wrapper's `tabindex="-1"` is markup, not copy, and the case states that explicitly |
| 13 | stays shown across an hour and later 200s, clears only on dismiss, re-shows, and notifies once per burst | 6 | AC4. Fake timers are installed **before** the first 429 so an auto-dismiss would fire; a burst of 3 gives 1 notification; a later 200 does not clear it; the dismiss control is a native `<button type="button">` |
| 14 | keeps keyboard focus on the notice when the dismiss button is activated | 6 (phase 9) | AC4, L11. Focus lands on the wrapper, not `<body>` |
| 15 | leaves focus where it is when the notice is dismissed from elsewhere | 6 (phase 9) | AC4, L11. No focus theft from a mouse dismiss |
| 16 | puts the copy in a `role="status"` region that exists and is empty before the 429 | 7 | AC5, L11, frozen finding 10. Same DOM node before and after, `aria-live="polite"`, the button outside the region |
| 17 | takes both strings from the catalogue, not from the component | 7 | AC5, O3. Rendered under an instance with no resources: the copy disappears, so a hardcoded string cannot pass |
| 18 | hands each 429 to the caller unchanged, body included, and calls the transport once | 8 | AC6. `result.response` is **the same object** the transport produced, `error` is the parsed problem body, `Retry-After` still readable, one transport call |
| 19–22 | shows nothing for a 200 / 400 / 403 / 503 | 9 | AC7 |
| 23 | shows nothing for a 401, renewed or final | 9 | AC7, L12 (a renewed 401 ends 200; a public operation's 401 is final) |
| 24 | leaves `createAuthApi(boundary)` behaving as in T05 when no notice is passed | 10 | AC7, L12. One renewal, one re-send, the 429 passed through — and the copy is lost, which is why wiring must pass the notice (P7-2) |
| 25 | writes nothing to storage or cookies, logs nothing, and leaves the URL alone | 11 | AC8, §5, privacy rules |
| 26 | keeps a throwing subscriber away from the response and the component; its error surfaces uncaught | 12 | Frozen behaviour: synchronous notification, a throw rethrown in a microtask |

Helpers are local to the file, as findings 6 and 8 require: `renderNotice(state, instance?)` (a fresh `createI18n()`
behind `<I18nextProvider>`, `react-dom/client` + `act`), `callThrough()` (flushes React while a call runs),
`installBrowserRequest` / `installTransport` / `fakeSession` copied from T05 rather than imported, and the renewal
case wired through a real `createSessionBoundary`. Nothing in T05's or T06's suites was touched.

## Mutation runs

Each mutant was applied to production code, the suite run, then the file restored with `git checkout --`. **12 of 12
caught**, covering every mutation break the frozen brief lists plus the phase 9 addition.

| Mutant | Caught by |
|---|---|
| M1 the notice middleware registered **after** T05's | case 3 (it then sees only the 401) |
| M2 the copy taken from the problem `title` | 13 cases, including all 7 AC2 rows |
| M3 `Retry-After` interpolated into the copy | 14 cases, including all 7 AC2 rows |
| M4 a `setTimeout` auto-dismiss | cases 11 (timer spy) and 13 (the notice is gone after the hour) |
| M5 no live region | 19 cases |
| M6 notify on every 429 (the `shown === next` guard removed) | case 13 (3 notifications for a burst of 3) |
| M7 a replay on 429 | 6 cases, including 18's single transport call |
| M8 matching `>= 429` | case 22 (503) |
| M9 matching 5xx | 19 cases |
| M10 a hardcoded string instead of the catalogue | case 17 |
| M11 the catalogue resources removed, so a raw key renders | 13 cases |
| M12 the phase 9 wrapper focus fix removed | cases 12 and 14 |

## Verification

- `vitest run` on the new file: **26/26**.
- Whole frontend suite: **124 passed, 1 failed (125)** — the one failure is the pre-existing, environmental
  `lintRejectsHandWrittenBackendFetch` AC4 case recorded in phase 9: this host's `oxlint@1.87.0` prints no summary
  line. It fails identically with T07 stashed, and nothing in this phase touches it.
- `tsc --noEmit` exit 0 (so the typed catalogue keys resolve). `oxlint --no-ignore -c .oxlintrc.json src` exit 0.
- `git status`: the new test file only; no production, spec, or T01–T06 file changed.

## Open Questions

None.
