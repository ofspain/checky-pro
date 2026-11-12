#!/usr/bin/env python3
"""T173 mutation harness - the design system.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a
full phase-9 edit in T36 because I ran it on uncommitted test edits.

THE MACHINERY IS T37'S - TENTH COPY, OQ-T32-4 - with two changes this task forced:

  1. A MUTANT MAY SPAN SEVERAL FILES. `path`/`old`/`new` may each be a tuple, applied in
     lockstep. RD04 needs it: the R30 pin's non-emptiness half cannot be falsified by stripping
     ONE primitive's class, because four identical lists are still identical and still non-empty.
     The regression it guards against is "the design system was reverted", and that is five
     files at once. A single-file harness would have scored that row SURVIVED and concluded the
     assertion was dead weight.
  2. REVERT_PATHS REACHES OUTSIDE `frontend/src/`. Two rows mutate `frontend/vite.config.ts`.
     Every previous copy reverted `frontend/src/` alone, so those two mutants would have been
     left in the tree - and every row after them would have been measured against a mutated
     build config, silently.

T173'S NAMED TEST DOES NOT EXIST YET: `package.md` §8 calls it
`screensRenderThroughDesignSystemPrimitivesOnly`, phase 10 writes it, and at phase 7 the only
things measuring this change are OTHER TASKS' INSTRUMENTS - three that phase 6 grew, and several
it did not touch at all. So each row predicts one of four outcomes, and the fourth is new:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `inherited`     killed ONLY outside this task's files, AND THAT IS THE POINT. The primitives
                  took over obligations other tasks already pin - a forwarded ref, a live
                  region, a landmark, a `data-testid`. Phase 6 claimed those obligations
                  survived the move; these rows are that claim under measurement. In every
                  previous copy this outcome printed `INHERITED-ONLY` and was reported as a
                  DISAGREEMENT, which would have been exactly backwards here.
  `phase 10`      nothing can kill it until the named suite exists; a kill here is a
                  DISAGREEMENT worth reading, because an instrument covers more than I thought.
                  AFTER PHASE 10 NO ROW CARRIES THIS LABEL. Eight did, and the named suite killed
                  every one - which is the label doing its job, not failing at it. It was a
                  prediction about a file that did not exist, and the file now exists. Each updated
                  row keeps its ORIGINAL prediction in a trailing comment, because an expectation
                  edited to match an outcome is how a harness quietly stops measuring anything: the
                  edit is defensible only while what it replaced is still readable beside it.
  `unfalsifiable` no observable effect of any kind, in the DOM or in the file; scored apart.

WHY `inherited` IS NOT A LOOPHOLE. It is only legitimate where phase 6 named the inherited
instrument in advance. RD11's kill, for instance, must land in
`rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` - the file `RateLimitNotice.tsx`'s
header cites as the evidence for AC6 - and the run prints the failing case names, so a kill by
something else is visible rather than absorbed.

    python3 -I .ai/prompts/frontend/T173/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T173/artifacts/07-mutation-harness.py RD04   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

UI = "frontend/src/app/ui/"
SCREEN, HEADING, TEXT = UI + "Screen.tsx", UI + "Heading.tsx", UI + "Text.tsx"
STATUS, BUTTON, LINK = UI + "StatusRegion.tsx", UI + "ActionButton.tsx", UI + "TextLink.tsx"
SURFACE, HOLDER = UI + "Surface.tsx", UI + "FocusHolder.tsx"
STYLES = "frontend/src/app/styles.css"
MOUNT = "frontend/src/app/mountShell.tsx"
VITE = "frontend/vite.config.ts"
SHELL = "frontend/src/app/AppShell.tsx"
SIGNIN = "frontend/src/auth/SignInScreen.tsx"
R30 = "frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx"

# THIS TASK'S OWN FILES: the named suite phase 6 wrote, and the five instruments phase 6 GREW. A kill in
# any of them is task 174's, because the case that did the killing is task 174's work. Five, where the
# freeze predicted three - `loginChallenge...` and `failedRenewal...` fired unpredicted, and the first of
# them is the discharged end-to-end pin, so leaving it out would misreport the strongest row in the run.
OWN_TESTS = [
    "src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx",
    "src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx",
    "src/app/routesResolveUnderAppPrefixOnly.test.tsx",
    "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx",
    "src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx",
    "src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx",
]

# RD17 mutates TEST code, so it measures the instrument rather than the implementation. Scored
# apart: the R30 pin's whole sameness half rests on `styleAttributesOf` reporting `style` as well
# as `class`, and nothing in this task's production code can falsify that - the cause of an
# identity failure never reaches the screen, which is the file's own premise. Its two synthetic
# controls are what hold it, so this row asks whether those controls are awake.
# RE19 mutates TEST code: it plants a file that imports nothing into the render-site list, which is the
# half of task 174's new both-directions derivation that a shrinking equality could never exercise. It
# measures the instrument, so it is scored apart.
CANARIES = {"RE19"}

# RE18 is predicted to have no observable effect: running the focus effect on every render still ends with
# focus on the same heading, because `focus()` on the already-focused element is a no-op. Scored apart, and
# a KILL here would be good news - it would mean AC10 is stronger than it claims.
UNFALSIFIABLE = {"RE18"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/", "frontend/vite.config.ts"]

EXPECTED_BASELINE_FAILURES = 1  # of 1862 after phase 9 added I4's order case and I9's three
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    # USED ONLY FOR THE BASELINE REFUSAL CHECK. Attribution no longer subtracts this file, because only ONE
    # of its 33 cases is environmental and the other 32 include the repository's only end-to-end build gate
    # - the one that runs a real `pnpm run build` and asserts today's sources compile. Subtracting the file
    # printed `killed in: []` for RD16's genuine tsc kill, hiding the strongest instrument the harness
    # consults (phase 7 F3, phase 8 I3). What is excluded now is the set of case names that fail AT
    # BASELINE, measured rather than listed, so a mutant that breaks any OTHER case in that file is a kill.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
HOME = "frontend/src/app/HomeScreen.tsx"
HEADING = "frontend/src/app/ui/Heading.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CAT = "frontend/src/i18n/catalogue.ts"
ONLY = "frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx"
RESOLVE = "frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx"
SIGNIN = "frontend/src/auth/SignInScreen.tsx"

EFFECT = ("  useEffect(() => {\n"
          "    headingRef.current?.focus();\n"
          "  }, []);")
H_LINE = ('      <h1 ref={ref} tabIndex={-1} className="text-step-1 font-semibold tracking-tight text-text">')
H_SIG = "export const Heading = forwardRef<HTMLHeadingElement, { children: ReactNode }>(\n  function Heading({ children }, ref) {"
HOME_HEADING = "      <Heading ref={headingRef}>{t('home.heading')}</Heading>"
HOME_TEXT = "      <Text>{t('home.explanation')}</Text>"
HOME_IMPORTS = "import { useEffect, useRef } from 'react';"
ROUTE_ATTACH = "          if (path === HOME_PATH) return { path, element: <HomeScreen /> };"
CAT_HEADING = "    heading: 'Home',"
CAT_EXPL = "    explanation: 'You are signed in. There is nothing here yet.',"
SITE = "  'app/HomeScreen.tsx',"
UNION = "      'the union of the two pinned lists is no longer fourteen').toBe(14);"
RESOLVE_HOME = "  'home',\n] as const;"

# label, file(s), old, new, expectation
MUTANTS = [
    # --- AC2: the focus move, which is the half of the task statement with teeth -------------------------
    # The whole feature, deleted. If this survives anywhere, the task was not built.
    ("RE01 the arrival focus effect is removed entirely",
     HOME, EFFECT, "", "own"),
    # D1's decision, undone at the primitive. The h1 stops being focusable, `focus()` becomes a no-op, and
    # focus stays on <body> - which is EXACTLY the state T27-D1 pinned for eleven tasks, so the discharged
    # end-to-end case must notice too. A row that only this task's suite killed would mean the discharge
    # was decorative.
    ("RE02 Heading stops making its h1 focusable (D1 undone)",
     HEADING, H_LINE,
     '      <h1 ref={ref} className="text-step-1 font-semibold tracking-tight text-text">', "own"),
    # The target moved to the landmark. AC2's second case exists for this one: "not body" is true here, so
    # a suite that only forbade <body> would pass a screen that announces no name.
    ("RE03 focus moves to <main> instead of the heading",
     HOME, "    headingRef.current?.focus();",
     "    headingRef.current?.parentElement?.focus();", "own"),

    # --- AC8: focusable without being a trap -------------------------------------------------------------
    # The one-character difference between "focusable by code" and "inserted into the tab order". It must
    # also reach signInStarts..., whose selector task 174 NARROWED - if the narrowing went too far, that
    # suite is now blind to a real `tabindex="0"` and this row is how that shows up.
    # The anchor is the whole line, not `tabIndex={-1}`: that string also appears in the primitive's own
    # doc comment, and preflight refused the run until it was narrowed. A mutant whose anchor matches prose
    # is a mutant that might edit the prose.
    ("RE04 the focus target is put into the tab order (-1 -> 0)",
     HEADING, H_LINE,
     '      <h1 ref={ref} tabIndex={0} className="text-step-1 font-semibold tracking-tight text-text">',
     "own"),

    # --- AC9: set inside the primitive, not accepted from a consumer -------------------------------------
    # Phase 3's rejected mechanism, implemented. The props type grows the attribute rule 2 forbids.
    ("RE05 tabIndex becomes a prop on Heading (the freeze's rejected design)",
     HEADING, (H_SIG, H_LINE),
     ("export const Heading = forwardRef<HTMLHeadingElement, { children: ReactNode; tabIndex?: -1 }>(\n"
      "  function Heading({ children, tabIndex = -1 }, ref) {",
      '      <h1 ref={ref} tabIndex={tabIndex} className="text-step-1 font-semibold tracking-tight text-text">'),
     "own"),

    # --- AC1: it renders through the design system -------------------------------------------------------
    # A bare element in a screen. This is T173's claim, so the kill should come from BOTH suites - and the
    # `Only` sweep is the one that was blind to this file until task 174 gave it a second derivation.
    ("RE06 the screen writes a bare <span> of its own",
     HOME, HOME_TEXT, HOME_TEXT + "\n      <span />", "own"),

    # --- AC3: the copy is catalogue copy -----------------------------------------------------------------
    ("RE07 the heading is hardcoded in the component",
     HOME, HOME_HEADING, "      <Heading ref={headingRef}>Home</Heading>", "own"),
    # The catalogue VALUE changes. Three suites assert this string: this task's AC3, and the two callback
    # controls that task 174 had to update because a success is no longer silent.
    ("RE08 the catalogue heading becomes something else",
     CAT, CAT_HEADING, "    heading: 'Dashboard',", "own"),
    # A third entry appears. T173's key-set pin is the only thing that can see this.
    ("RE09 an unpinned catalogue key is added to the home group",
     CAT, CAT_EXPL, CAT_EXPL + "\n    greeting: 'Welcome back',", "inherited"),

    # --- AC4: the gate covers this path ------------------------------------------------------------------
    ("RE10 the home route loses its element again",
     ROUTES, ROUTE_ATTACH, "          if (path === HOME_PATH) return { path };", "own"),
    # Every authenticated path gets the landing screen. The screen renders where no task authorized it,
    # and T33's AC11 pin - grown by this task to an EXACT list rather than loosened - is what says so.
    ("RE11 the screen is attached to all eleven authenticated paths",
     ROUTES, ROUTE_ATTACH, "          return { path, element: <HomeScreen /> };", "own"),

    # --- AC5 / AC6: it touches nothing and knows nothing about its reader --------------------------------
    ("RE12 the screen calls an endpoint on mount",
     HOME, "    headingRef.current?.focus();",
     "    void fetch('/accounts/me');\n    headingRef.current?.focus();", "own"),
    ("RE13 the screen imports the access token",
     HOME, HOME_IMPORTS,
     HOME_IMPORTS + "\nimport { accessToken } from '../auth/accessToken';", "own"),
    # Identity-shaped copy, through the catalogue - so the import scan cannot see it and only the DOM scan
    # can. AC6 asserts in both places precisely because either alone has a hole.
    ("RE14 the catalogue greets the reader by implication",
     CAT, CAT_EXPL, "    explanation: 'Welcome back to your account.',", "own"),

    # --- AC7: the instruments this task was obliged to grow ----------------------------------------------
    # The render-site pin reverts. This is the row that measures task 174's OWN fix: before the second
    # derivation existed, a missing HomeScreen.tsx was invisible, because the sweep finds files by
    # lowercase JSX tag and this screen has none.
    ("RE15 HomeScreen.tsx is dropped from the render-site list",
     ONLY, SITE, "", "inherited"),
    ("RE16 the union pin goes back to thirteen",
     ONLY, UNION, "      'the union of the two pinned lists is no longer fourteen').toBe(13);", "inherited"),
    ("RE17 task 16's screen-count pin forgets the fourth screen",
     RESOLVE, RESOLVE_HOME, "] as const;", "inherited"),

    # --- controls and canaries ---------------------------------------------------------------------------
    # CANARY. Not a defect: the effect's dependency array becomes a no-op change in behaviour under a
    # single mount, and AC10's StrictMode case is the only thing that could tell. Predicted UNFALSIFIABLE
    # and recorded as such - running the effect on every render still ends with focus on the same heading,
    # because `focus()` on the focused element is a no-op. The row exists so the prediction is MEASURED
    # rather than asserted, and a surprise kill would mean AC10 is stronger than claimed.
    ("RE18 CANARY: the focus effect runs on every render, not once",
     HOME, "  }, []);", "  });", "unfalsifiable"),
    # CONTROL for RE15. The derivation added in task 174 claims both directions; dropping an entry tests
    # one of them, and ADDING a file that imports nothing tests the other. Without this, an equality that
    # only ever shrinks would look just as healthy.
    ("RE19 CONTROL: a file that does not import the barrel is pinned as a render site",
     ONLY, SITE, SITE + "\n  'app/mountShell.tsx',", "inherited"),

    # --- phase 9: the two findings from the independent review that needed code ---------------------------
    # I4. The landmark checks pass for a screen that explains itself before it names itself, so the order
    # was unmeasured until phase 9. This row is the one that says the new case is not decoration.
    ("RE20 the paragraph is rendered before the heading (I4)",
     HOME, HOME_HEADING + "\n" + HOME_TEXT, HOME_TEXT + "\n" + HOME_HEADING, "own"),
    # I9. THE HOLE THIS TASK OPENED. Before task 174 a `.focus()` on another screen was inert, because no
    # `<h1>` was focusable. `Heading` changed that for every screen at once, so the guard has to be shown
    # working rather than assumed: this plants exactly the regression it was written for.
    # THREE EDITS, because fewer would not compile or would not fire. `SignInScreen` imports no
    # `useEffect`, and its `<Heading>` carries no ref - so a one-line effect would either break `tsc` or
    # call `focus()` on null and survive for a reason that has nothing to do with the guard. A mutant must
    # measure its own description.
    ("RE21 SignInScreen grabs focus on mount, which Heading now makes possible (I9)",
     SIGNIN,
     ("import { useRef, useState } from 'react';",
      "export function SignInScreen() {",
      "      <Heading>{t('signIn.heading')}</Heading>"),
     ("import { useEffect, useRef, useState } from 'react';",
      ("export function SignInScreen() {\n"
       "  const stolen = useRef<HTMLHeadingElement>(null);\n"
       "  useEffect(() => { stolen.current?.focus(); }, []);"),
      "      <Heading ref={stolen}>{t('signIn.heading')}</Heading>"),
     "own"),
]

def as_tuple(value):
    return value if isinstance(value, tuple) else (value,)


def edits_of(path, old, new):
    """One mutant, one or more (path, old, new) triples. A single replacement cannot express a
    REORDER, a move, or - new in this copy - a change that spans five files."""
    paths, olds, news = as_tuple(path), as_tuple(old), as_tuple(new)
    if len(paths) == 1 and len(olds) > 1:
        paths = paths * len(olds)
    assert len(paths) == len(olds) == len(news), "path/old/new must line up"
    return list(zip(paths, olds, news))


def run(files, verbose=False):
    cmd = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    proc = subprocess.run(cmd, cwd=FRONTEND, capture_output=True, text=True)
    failed = total = None
    pairs = []
    names = []
    for line in (proc.stdout + proc.stderr).splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
        # `(?:ts|tsx)` tried `ts` FIRST and nothing anchored the end, so every `.tsx` path was recorded
        # as `.ts` - a file that does not exist. Nine copies printed corrupt file lists (phase 8, I4).
        # ELEVENTH COPY. The tenth collected files from `FAIL` lines and case names from `×` lines, as two
        # independent sets - so `in:` named every file that had ANY failure, including one whose only
        # failing case was already failing at baseline. A `FAIL` line carries both halves itself
        # (`FAIL  <file> > <suite> > <case>`), so they are read from the same line and stay paired.
        match = re.search(r"\bFAIL\b\s+(\S+\.tsx?)\b(.*)$", stripped)
        if match:
            pairs.append((match.group(1), match.group(2).split(" > ")[-1].strip()))
        # NAMES COME FROM `×` LINES ONLY. vitest prints every failing case TWICE - once in the file
        # listing and once in the summary - so taking names from both sources printed each kill twice.
        # The FAIL line is used for the file/case PAIRING above and for nothing else.
        if verbose and stripped.startswith("× "):
            leaf = stripped[2:].split(" > ")[-1]
            names.append(re.sub(r"\s+\d+ms$", "", leaf).strip())
    files_failed = {path for path, _ in pairs}
    return proc.returncode, failed, total, files_failed, names, pairs


def apply(path, old, new):
    for target_path, find, replace in edits_of(path, old, new):
        target = REPO / target_path
        text = target.read_text()
        if text.count(find) != 1:
            return False
        target.write_text(text.replace(find, replace, 1))
    return True


def revert():
    subprocess.run(["git", "checkout", "--", *REVERT_PATHS], cwd=REPO, check=False)
    subprocess.run(["git", "clean", "-fq", "--", *REVERT_PATHS], cwd=REPO, check=False)


def preflight():
    """Every anchor in its file, exactly once, REPLAYED IN MEMORY so a mid-mutant ambiguity is caught
    before any verdict prints. T36 shipped a BROKEN row that the per-anchor check on unmutated text had
    passed, and BROKEN is not a survivor, so the run still ended `survivors: none`."""
    stale = []
    for label, path, old, new, _expect in MUTANTS:
        texts = {}
        for target_path, find, replace in edits_of(path, old, new):
            text = texts.setdefault(target_path, (REPO / target_path).read_text())
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {target_path} matches an anchor {count} times, "
                             "expected 1")
                break
            texts[target_path] = text.replace(find, replace, 1)
    if stale:
        print("REFUSING TO RUN: anchors no longer match the source.\n")
        print("\n".join(stale))
        print("\nEach would print BROKEN, which is NOT a survivor, so the run would still end\n"
              "`survivors: none` and the score would be overstated by exactly this many.")
        return False
    return True


def score(label, base):
    """`base` is (failed, total, cases) from the unmutated run. Two guards phase 7 did not have:

    A MUTANT THAT STOPS A FILE FROM LOADING IS BROKEN, NOT KILLED. RD11 was a syntax error; 23 suites
    failed to PARSE and the raised failure count was credited as a kill, with `broken: none` printed
    underneath (F2, I2). A file that cannot be loaded contributes NO tests, so the total drops - and that
    is the one signal which separates "a case caught it" from "nothing ran". T36 lost the same way.

    ATTRIBUTION IS BY CASE NAME, NOT BY FILE. Subtracting the whole known-environmental FILE hid RD16's
    real kill inside it and printed `killed in: []` (F3, I3). What is subtracted now is the set of case
    names that already fail at baseline, so the other 32 cases in that file - including the only
    end-to-end build gate in the repository - can kill a mutant and be seen doing it."""
    base_failed, base_total, base_cases = base
    code, failed, total, files_failed, names, pairs = run(FILES, verbose=True)
    if failed is None:
        print(f"  BROKEN    no summary     {label}")
        return None, True, None, False
    if total is not None and base_total is not None and total < base_total:
        print(f"  BROKEN    {total} of {base_total} ran  {label}  "
              f"-> {base_total - total} tests never ran: a file did not LOAD, so nothing measured it")
        return None, True, None, False
    new = [name for name in names if name not in base_cases]
    if not new:
        print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
        return label, False, None, False
    _, _, _, _, own_names, _ = run(OWN_TESTS, verbose=True)
    own_new = [name for name in own_names if name not in base_cases]
    by_own = bool(own_new)
    own = "own" if by_own else "inherited"
    print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
    # ELEVENTH COPY: `in:` names only files holding a NEW failure. The tenth printed every file with any
    # failure at all, so a file whose sole failure was the environmental one was reported as a killer.
    print(f"            └ in: {sorted({f for f, case in pairs if case not in base_cases})}")
    for name in (own_new if by_own else new):
        print(f"            └ {name}")
    return None, False, new, by_own


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed, base_cases, _ = run(FILES, verbose=True)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    print(f"baseline failing cases (excluded from every attribution below): {base_cases}\n")
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        print("REFUSING TO RUN: a file outside the known environmental set is failing.\n"
              f"  unexpectedly failing: {sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)}")
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(f"NOTE: baseline is {failed}, not the recorded {EXPECTED_BASELINE_FAILURES}; all are in the\n"
              "known environmental set, so the measured floor is sound.\n")
    base = (failed, total, set(base_cases))

    survivors, broken, outcomes = [], [], {}
    for label, path, old, new, expect in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        lost, was_broken, _names, by_own = score(label, base)
        outcomes[label.split()[0]] = (
            "SURVIVED" if lost else ("KILLED/own" if by_own else "KILLED/inherited"), expect)
        if lost:
            survivors.append(lost)
        if was_broken:
            broken.append(label)
        revert()

    kept = [row for row in MUTANTS if not wanted or wanted in row[0]]
    apart = CANARIES | UNFALSIFIABLE
    regressions = [row for row in kept if row[0].split()[0] not in apart]
    lost_labels = [label for label in survivors + broken if label.split()[0] not in apart]

    print(f"\nregressions: {len(regressions) - len(lost_labels)}/{len(regressions)} killed"
          f"{'' if not broken else f'  ({len(broken)} BROKEN, not counted as killed)'}")
    scored_apart = [label.split()[0] for label in survivors if label.split()[0] in apart]
    if CANARIES:
        print(f"canaries:    {sorted(CANARIES)} - scored apart; a SURVIVING canary means the "
              f"instrument is inert: {[k for k in scored_apart if k in CANARIES] or 'none survived'}")
    if UNFALSIFIABLE:
        print(f"unfalsifiable: {sorted(UNFALSIFIABLE)} - no observable effect to detect")
    print(f"survivors:   {[label.split()[0] for label in survivors] or 'none'}")
    print(f"broken:      {broken or 'none'}")

    # THE PHASE 7 VERDICT. A survivor here is expected for every row phase 10 owns; what matters is a
    # row whose outcome disagrees with its expectation.
    print("\n--- expectation vs outcome ---")
    disagreed = []
    for key, (outcome, expect) in sorted(outcomes.items()):
        if expect == "unfalsifiable":
            flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - it has an observable effect"
        elif expect == "phase 10":
            flag = "as expected" if outcome == "SURVIVED" else f"DISAGREES - already killed ({outcome})"
        elif outcome == "SURVIVED":
            flag = "DISAGREES - nothing detects it"
        elif expect == "inherited" and outcome == "KILLED/own":
            flag = "DISAGREES - this task's own files caught it too (stronger than claimed)"
        elif expect == "own" and outcome == "KILLED/inherited":
            flag = "DISAGREES - killed only OUTSIDE this task's files"
        else:
            flag = "as expected"
        if flag != "as expected":
            disagreed.append(key)
        print(f"  {key:<6} expected {expect:<13} got {outcome:<16} {flag}")
    print(f"\ndisagreements: {disagreed or 'none'}")
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
