<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review) -->

# frontend · T173 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |
| **Produces** | `artifacts/08-independent-review.md` |

## Verification performed

I attempted to run the verification commands independently. `pnpm install` could not complete because this session's host cannot reach the npm registry: every fetch for the Tailwind 4 packages (`tailwindcss`, `@tailwindcss/vite`, and their `oxide`/`node`/`jiti` dependencies) timed out after retries. The existing `node_modules` symlinks do not include the Tailwind packages, so `tsc`, `oxlint`, `vitest`, and the build cannot run without them.

Consequently I could not reproduce the self-review's numbers. The rest of this review is based on a close reading of the changed source, the harness, and the run output.

| Command | Attempted result |
|---|---|
| `npx pnpm@12.14.0 install --frozen-lockfile` | **Network timeout** fetching Tailwind packages |
| `tsc --noEmit` / `oxlint src` / `vitest run` | **Could not run** (missing Tailwind deps) |
| Manual contrast recomputation | **Confirmed** the four corrected ratios in `styles.css` |

No production or test code was changed during this review.

---

## Findings

### I1 — The four corrected contrast ratios are arithmetically right
· **Severity:** **Minor/Major** (verification of self-review F1)
· **Evidence:** `frontend/src/app/styles.css:32-51` lists `--color-surface: #fbfbfd`, `--color-text: #15161a`, `--color-muted: #4b4f58`, `--color-accent: #1b4ed8`, `--color-text-inverse: #ffffff`, `--color-focus: #0b5bd3`.
· **Assessment:** I recomputed the WCAG relative-luminance ratios independently with Python:
  - text on surface: **17.49:1** (file says 17.5)
  - muted on surface: **7.94:1** (file says 7.9)
  - text-inverse on accent: **6.71:1** (file says 6.7)
  - focus on surface: **5.89:1** (file says 5.9)
  All three required thresholds (4.5:1, 4.5:1, 3:1) are cleared. The early fix committed as `c6155cf5` is correct, and the file header now honestly states that the ratios are computed rather than estimated.
· **Recommendation:** None. This finding confirms self-review F1.

### I2 — RD11 is indeed a broken mutant, and the harness's "broken" detection is missing
· **Severity:** **Major** (verification of self-review F2)
· **Evidence:** `artifacts/07-mutation-harness.py:191-195` defines RD11 by replacing `(<div ...>{children}</div>)` with `({children === null ? null : (<div ...>{children}</div>)})`.
· **Assessment:** I traced the substitution by hand. The resulting component body is `return ( {children === null ? null : (...)} )`. In JSX, `return (` begins a parenthesised JavaScript expression; the next `{` is parsed as an object-literal opener, not a JSX expression. That makes the file unparseable. Vitest's failure count rises not because a test detected the live-region regression, but because the parser rejected the file. The harness currently has no guard that compares the total test count against the baseline total, so it reports the mutant as killed.
· **Recommendation:** Agree with self-review F2: add a `score()` guard that treats a run whose total test count is below baseline as `BROKEN`, and rewrite RD11 to wrap the return value in a valid conditional.

### I3 — The environmental-failure exclusion is keyed by file, masking real kills
· **Severity:** **Major** (verification of self-review F3)
· **Evidence:** `artifacts/07-mutation-harness.py:91-95` sets `KNOWN_ENVIRONMENTAL_FAILURES = {"src/api/lintRejectsHandWrittenBackendFetch.test.ts"}`.
· **Assessment:** That file contains 33 cases; only the host's `oxlint` summary-line case is environmental. The other cases, including the end-to-end build gate, are real instruments. When RD16 kills via the build gate, the harness subtracts the whole file from attribution and prints `killed in: []`, making the strongest instrument invisible. I verified this by reading the file: its build case runs the real production build and asserts today's sources compile cleanly. This is the only end-to-end build gate in the repository.
· **Recommendation:** Agree with self-review F3: narrow the set to the one case name, and attribute by case rather than by file.

### I4 — The FAIL-line regex truncates `.tsx` to `.ts`
· **Severity:** **Minor/Major** (verification of self-review F4)
· **Evidence:** `artifacts/07-mutation-harness.py:269` uses `re.search(r"\bFAIL\b\s+(\S+\.(?:ts|tsx))", stripped)`.
· **Assessment:** I independently confirmed the bug. Alternation `ts|tsx` tries `ts` first and succeeds on the `ts` prefix of `tsx`, leaving the trailing `x` outside the capture. `re.search` then returns the substring ending at `ts`, so every `.tsx` failure is recorded as a `.ts` file. This corrupts every file list the harness prints and weakens the baseline guard, which compares truncated names against the environmental set.
· **Recommendation:** Agree with self-review F4: change the pattern to `(\S+\.tsx?)\b`.

### I5 — RD15 (wrapped className) survives because the R30 regex anchors only the start
· **Severity:** **Minor/Major** (verification of self-review F6)
· **Evidence:** `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1448` asserts `toMatch(/^[a-z0-9]+\[class=/)` for every attribute reported by `styleAttributesOf`.
· **Assessment:** I verified that a className broken across lines puts a literal newline into the rendered `class` attribute. Because the R30 test renders the same broken class in all five account states, the five attribute strings remain identical; the non-emptiness check passes; and the regex only checks the prefix `tag[class=`, so the trailing newline is ignored. The mutant survives. This contradicts phase 6's D4 claim that it would be "very visible to the R30 pin".
· **Recommendation:** Agree with self-review F6: add a source-scan row forbidding a `className` literal that contains a newline, and correct D4 in phase 9.

### I6 — The R30 pin was grown correctly for T173
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:1418-1450`.
· **Assessment:** The test now asserts (a) the five account states have identical styling, (b) at least one element carries styling, and (c) every styling attribute is a `class`, not an inline `style`. This is the right way to turn a "no styling" pin into a "no cause-keyed styling" pin when the design system lands. The synthetic controls below it are also correct.
· **Recommendation:** None. This is confirmation that the closed-task instrument was grown rather than bypassed.

### I7 — The `Only` sweep's target count of 13 is ambiguous
· **Severity:** **Minor/Major** (verification of self-review F7)
· **Evidence:** `.ai/prompts/frontend/T173/artifacts/04-freeze-task.md:185`: *"After this task there must be thirteen, and every one must appear in exactly one of the two lists."*
· **Assessment:** There are 8 primitives and 5 render sites, totalling 13. A source sweep that looks for bare lowercase JSX tags in the render sites will find 0 after T173, because every render site uses primitives. So a test that asserts the sweep finds 13 files will fail. The intended claim is almost certainly: the union of the primitive list and the render-site list is 13, and their intersection is empty. The brief's wording fits both readings.
· **Recommendation:** Agree with self-review F7: phase 10 should assert the union (13) and the empty intersection separately, not the length of the derived tag-bearing set.

### I8 — `AppShell` and `RateLimitNotice` comments in older tests are now slightly stale
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx:261` says *"`AppShell.tsx` is a bare `<div>` that task 16's suite wraps"*. `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:68` says *"No `AppShell`: it is a bare div task 16's suite wraps."*
· **Assessment:** After T173, `AppShell` renders a `Surface` primitive, which in turn renders a `<div>`. The outer element is no longer "bare" — it carries Tailwind utility classes. The tests still pass because they do not assert the absence of classes on the shell, but the comments are now misleading.
· **Recommendation:** Update those two comments to say `Surface` primitive rather than bare `<div>`. This is a doc change only.

### I9 — `RateLimitNotice` has no production consumer, so `FocusHolder`'s layout classes are not exercised in production
· **Severity:** **Minor** (verification of self-review F8)
· **Evidence:** `frontend/src/app/RateLimitNotice.tsx:11-12` says *"Don't mount it in `AppShell` until the first screen adds that provider tree."* Nothing in production imports or renders it.
· **Assessment:** The focus-management behaviour is tested in `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` and the ref forwarding is tested, but the layout classes (`flex flex-col gap-2`) only appear in a test-only tree. A regression in those classes would not be visible until a later task mounts the notice.
· **Recommendation:** None for T173. Record as a note for whoever mounts the notice.

### I10 — The `Surface` primitive correctly keeps `data-testid` as a prop
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/Surface.tsx:14-17` accepts `'data-testid'` and forwards it.
· **Assessment:** This satisfies AC11 (the shell's `data-testid` is unchanged) while keeping the primitive reusable. The decision matches the phase-3 challenge's F5.
· **Recommendation:** None.

### I11 — `FocusHolder` children being required is justified by its single consumer
· **Severity:** **Minor** (verification of self-review and phase-3 F11)
· **Evidence:** `frontend/src/app/ui/FocusHolder.tsx:14-17` comment says children is required because `RateLimitNotice` always renders a `StatusRegion` inside it.
· **Assessment:** I verified `RateLimitNotice.tsx:40-52`: the `FocusHolder` wraps `<StatusRegion>` (always rendered) and a conditional `<ActionButton>`. So children is indeed never empty. The phase-3 suggestion to make it optional was based on an incorrect premise.
· **Recommendation:** None.

### I12 — The `bundleContainsNoSecretEnvKeys` allowlist now admits `https://tailwindcss.com`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/bundleContainsNoSecretEnvKeys.test.ts:76-84`.
· **Assessment:** The admission is justified as Tailwind 4's license banner in the emitted CSS. This is a new category for the allowlist (a stylesheet comment, not a thrown error message). RD05 and RD06 are killed because removing the stylesheet or the plugin removes the banner, breaking the exact-set assertion.
· **Recommendation:** None. This is a clean way to turn an allowlist into a pin that also requires.

### I13 — `shellBuildsAndMounts.test.tsx` does not yet assert a CSS asset is emitted
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/app/shellBuildsAndMounts.test.tsx:54-91` builds for production but only checks JS script sources, service worker, and asset contents.
· **Assessment:** T173 AC1 requires the stylesheet to reach built output as an emitted asset, and AC14 (per the brief) is the build-asset check. The existing test does not assert a `.css` file exists in `outDir/assets`. This is expected to be phase 10's work, but it is worth flagging.
· **Recommendation:** Phase 10 should add an assertion that the build emits at least one `.css` asset in `outDir/assets` and that its content contains the Tailwind license banner.

### I14 — The primitive layer is well-typed and follows its own three rules
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/index.ts:12-23` and each primitive file.
· **Assessment:** None of the eight primitives accepts `className` or `style`; ARIA contracts (role, aria-live, tabIndex, type="button", h1) are set internally; and no primitive contains user-facing copy. The types enforce these rules at compile time.
· **Recommendation:** None.

### I15 — `ActionButton`'s long single-line className is the right trade-off
· **Severity:** **Minor**
· **Evidence:** `frontend/src/app/ui/ActionButton.tsx:34-38`.
· **Assessment:** The comment explains that breaking the className across lines would put literal newlines into the DOM's class attribute. The R30 pin compares attribute strings, so consistent formatting matters. The one-line choice is defensible.
· **Recommendation:** None.

### I16 — The network failure is a session environmental issue, not a T173 defect
· **Severity:** **Informational**
· **Evidence:** Every `pnpm` fetch for Tailwind packages timed out with `os error 60` (connection timeout).
· **Assessment:** The self-review's verification numbers cannot be independently confirmed in this session because the dependency layer cannot be installed. All other verification is by static reading.
· **Recommendation:** None.

---

## Summary

T173's Phase 7 self-review is internally consistent and the implementation reads correctly. My independent checks confirm the four most important technical claims: the corrected contrast ratios (I1), RD11's broken status (I2), the environmental-failure set being too coarse (I3), and the `.tsx` truncation bug (I4). I also confirm the R30 pin was grown correctly (I6), the wrapped-className gap (I5), and the ambiguity in the "thirteen" count (I7). The only new observation beyond the self-review is I8 (stale comments in two older tests that still describe `AppShell` as a bare `<div>`). Because this session cannot reach the npm registry, I could not reproduce the harness numbers; that should not be read as a discrepancy with the self-review.

**Phase 8 complete.** Sixteen findings returned; no code or contract changed.
