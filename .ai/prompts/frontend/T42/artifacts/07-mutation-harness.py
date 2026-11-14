#!/usr/bin/env python3
"""T42 mutation harness - the authenticated shell. TWELFTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a
full phase-9 edit in T36 because I ran it on uncommitted test edits.

INHERITED FROM T174's ELEVENTH COPY, UNCHANGED: multi-file and multi-part mutants (`path`, `old`
and `new` may each be a tuple, applied in lockstep); REVERT_PATHS reaching outside `frontend/src/`;
`FAIL`-line pairing for the `in:` list with case names taken from `x` lines only; attribution that
subtracts BASELINE-FAILING CASE NAMES rather than a whole environmental file; and BROKEN detected by
a DROP IN THE TOTAL, because a file that cannot load contributes no tests and nothing measured it.

NEW IN THIS COPY, AFTER ITS FIRST RUN CAME BACK WITH THREE BROKEN ROWS: the expectation table prints
BROKEN rather than crediting it as KILLED/inherited; a JSX mutant anchors on the whole element so the
file still parses; and a mutant on a list that FEEDS `it.each` renames instead of deleting, because
deleting an entry deletes a generated case and the drop in the total is indistinguishable from a file
that never loaded.

FOUR OUTCOMES, AND EVERY ROW PREDICTS ONE:

  `own`           killed, AND with a named failure in a file this task owns or grew.
  `inherited`     killed ONLY outside this task's files. Legitimate here only where phase 6 named
                  the inherited instrument in advance - and this task grew nine of them, so most
                  rows that touch a pinned list come back `own` by definition. That collision is
                  documented rather than relabelled (T174's five disagreements, same cause).
  `unfalsifiable` no observable effect of any kind, in the DOM or in the file; scored apart.
  canary          mutates TEST code to ask whether an instrument is awake; scored apart.

    python3 -I .ai/prompts/frontend/T42/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T42/artifacts/07-mutation-harness.py TE04   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

LAYOUT = "frontend/src/app/AuthenticatedLayout.tsx"
BANNER = "frontend/src/app/ui/Banner.tsx"
ROUTES = "frontend/src/app/routes.tsx"
ONLY = "frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"

# THIS TASK'S OWN FILES: the named suite it extended, and the instruments phase 6 grew. A kill in any of
# them is task 42's, because the case that did the killing is task 42's work - including the nine grown
# ones, which is why a row that reverts a pinned list is reported `own` rather than `inherited`.
OWN_TESTS = [
    "src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx",
    "src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx",
    "src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx",
    "src/app/routesResolveUnderAppPrefixOnly.test.tsx",
    "src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx",
    "src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx",
    "src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx",
    "src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx",
    "src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx",
]

# TE20 mutates TEST code: it plants a file into the render-site list that imports no barrel, which is the
# half of task 174's both-directions derivation a shrinking equality can never exercise. It measures the
# instrument, so it is scored apart.
CANARIES = {"TE20"}

# TE21 is predicted to have no observable effect: the banner's padding is deliberately unpinned. T173's
# REQUIRED_UTILITIES exists to pin colour tokens, type steps and focus rings and explicitly NOT the exact
# class string, because "pinning the whole literal would turn every padding adjustment into a test edit".
# This row is that decision under measurement rather than quoted - and a KILL here would be news.
UNFALSIFIABLE = {"TE21"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/", "frontend/vite.config.ts"]

EXPECTED_BASELINE_FAILURES = 1  # of 1880 after phase 6 added task 42's sixteen cases
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    # USED ONLY FOR THE BASELINE REFUSAL CHECK. Attribution subtracts baseline-failing CASE NAMES, not
    # this file, because 32 of its 33 cases are real - including the repository's only end-to-end build
    # gate, which runs a real `pnpm run build` and is the instrument that holds every type-level claim.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
# Each is CODE, never prose. A mutant whose anchor matches a comment is a mutant that might edit the
# comment instead (T174's preflight refused a run for exactly that).
LAYOUT_ELEMENT = "            element: <AuthenticatedLayout />,"
LISTENER = """    const listening = listenForSignOut(() => {
      signOut();
      setSiblingSignedOut(true);
    });
    return () => listening.close();"""
CLEANUP = "    return () => listening.close();"
REDIRECT = "  if (siblingSignedOut) return <RedirectToSignIn />;"
CONTROL_LINE = "        <ActionButton onClick={endSession}>{t('shell.signOut')}</ActionButton>"
OUTLET = "      <Outlet />"
BANNER_OPEN = "      <Banner>"
HEADER_LINE = ('      <header ref={ref} className="mx-auto flex w-full max-w-prose '
               'items-center justify-end gap-4 px-6 pt-6">')
# TE02 AND TE04 MUTATE THE WHOLE ELEMENT, NOT ITS OPENING TAG. This copy's first run anchored on the
# opening line alone, so `<div ...>` was left closed by `</header>` and the `<section>` was never closed:
# `Banner.tsx` stopped PARSING, every file that imports the barrel failed to LOAD, 530 of 1880 tests ran,
# and the two criteria those rows exist to measure - AC2's landmark and AC9's ancestry - measured nothing.
# A JSX mutant has to leave JSX behind.
BANNER_IMPORT = "import { forwardRef, type ReactNode } from 'react';"
BANNER_BLOCK = HEADER_LINE + "\n        {children}\n      </header>"
SITE = "  'app/AuthenticatedLayout.tsx',"
PRIMITIVE = "  'Banner',"
SIGNOUT_KEY = "    signOut: 'Sign out',"

MUTANTS = [
    # --- AC1: a session renders the shell, which is the half of R28 nothing measured --------------------
    # The whole feature, removed at the route table. A pathless route with children and NO element still
    # renders its children, so the eleven paths keep resolving and the chrome simply vanishes - which is
    # exactly the state the task was commissioned to end, and ten of the eleven would be blank again.
    ("TE01 the layout route loses its element, so no chrome renders anywhere",
     ROUTES, LAYOUT_ELEMENT, "", "own"),
    # The chrome without the landmark. `Banner` is the ninth primitive and this is the row that says the
    # ninth was not decoration: a `<div>` renders identically, reads identically, and is not a banner.
    # Two-part, one file: the import grows `Ref` so the cast compiles, because `Banner.tsx` imports no
    # `React` namespace and the first draft's `React.Ref` would have failed the build gate - a kill in a
    # file this task does not own, which is not what this row claims to measure.
    ("TE02 Banner renders a div instead of a header (the landmark is gone)",
     BANNER, (BANNER_IMPORT, BANNER_BLOCK),
     ("import { forwardRef, type ReactNode, type Ref } from 'react';",
      '      <div ref={ref as unknown as Ref<HTMLDivElement>} className="mx-auto flex w-full max-w-prose '
      'items-center justify-end gap-4 px-6 pt-6">\n        {children}\n      </div>'), "own"),

    # --- AC2: the screen is NESTED, not replaced -------------------------------------------------------
    # The outlet dropped. The chrome still renders, so AC1 passes and only a case that looks for the
    # SCREEN can tell - and `/app/home` would be the chrome and nothing else.
    ("TE03 the outlet is dropped, so the matched screen never renders",
     LAYOUT, OUTLET, "", "own"),
    # The chrome inside the screen's landmark would demote `<header>` to a generic group silently, which is
    # the conditional half of the native mapping. Nothing about the tag changes; only the ancestry does.
    ("TE04 the banner is wrapped in a <section>, which demotes it to a group",
     BANNER, BANNER_BLOCK, "      <section>\n" + BANNER_BLOCK + "\n      </section>", "own"),

    # --- AC3, AC8: the chrome must not take the focus task 174 moved -----------------------------------
    # The hole this task could have fallen into. `Heading` made every `<h1>` focusable at task 174 and the
    # landing screen moves focus to it on arrival; a chrome that focused its own control takes that away
    # from a screen-reader user with nothing failing unless a case asserts `activeElement`.
    ("TE05 the chrome focuses its own control on mount",
     LAYOUT, CLEANUP,
     ("    (document.querySelector('header button') as HTMLButtonElement | null)?.focus();\n" + CLEANUP),
     "own"),
    # A second `<h1>` on every authenticated page, which breaks the document outline and gives a screen
    # reader two page titles. The freeze forbade `Heading` in the layout (D3); this is that rule measured.
    ("TE06 the chrome renders a Heading, so /app/home has two h1",
     LAYOUT, BANNER_OPEN, "      <Banner><Heading>{t('shell.signOut')}</Heading></Banner>\n      <Banner>",
     "own"),

    # --- AC4, AC15: nothing of it reaches a visitor with no session ------------------------------------
    # The layout moved OUTSIDE the gate. Two edits, because the nesting has to be inverted: the chrome -
    # including a control that ends a session - would render for a visitor who has none.
    ("TE07 the layout wraps the gate instead of nesting inside it",
     ROUTES,
     ("        element: <RequireSession />,\n        children: [", LAYOUT_ELEMENT),
     ("        element: <AuthenticatedLayout />,\n        children: [",
      "            element: <RequireSession />,"),
     "own"),

    # --- AC5: no path is added ------------------------------------------------------------------------
    # `path: ''` is the shape both task 40 and task 42 rejected in writing: a string path in the tree
    # changes what the bare `/app/` matches, and the pathless pins stop being able to see the element.
    ("TE08 the layout route takes an empty path",
     ROUTES, LAYOUT_ELEMENT, LAYOUT_ELEMENT + "\n            path: '',", "own"),

    # --- AC6: the one word comes from the catalogue ----------------------------------------------------
    ("TE09 the label is hardcoded in the component",
     LAYOUT, CONTROL_LINE,
     "        <ActionButton onClick={endSession}>Sign out</ActionButton>", "own"),
    # The catalogue entry renamed. The DOM would show nothing where the label was, which is what a missing
    # key looks like (O3) - and T173's key-set pin should see it too.
    ("TE10 the catalogue key is renamed, so the label resolves to nothing",
     CATALOGUE, SIGNOUT_KEY, "    signOutAction: 'Sign out',", "own"),

    # --- AC7: the two pinned lists name what this task added ------------------------------------------
    ("TE11 AuthenticatedLayout.tsx is dropped from the render-site list",
     ONLY, SITE, "", "inherited"),
    # A RENAME, NOT A DELETION. `it.each(PRIMITIVES)` generates one case per primitive, so dropping an
    # entry drops a TEST: the total fell to 1879 and the BROKEN guard - correctly watching for a drop -
    # reported a load failure that had not happened. A rename keeps the list's LENGTH, so all nine
    # generated cases still run and the pin is measured by what it NAMES, not by how long it is.
    ("TE12 Banner is renamed in the primitive list, so the list names a file that does not exist",
     ONLY, PRIMITIVE, "  'Bannerr',", "inherited"),

    # --- AC9: the control is a control, and reachable -------------------------------------------------
    # A link where a button belongs. It looks the same to the eye, announces differently, and activates on
    # a different key - and `TextLink` would navigate rather than act.
    ("TE13 the sign-out control becomes a link",
     LAYOUT, CONTROL_LINE,
     "        <TextLink to=\"/sign-in\">{t('shell.signOut')}</TextLink>", "own"),

    # --- AC10: nothing identity-dependent, and the import list is the contract ------------------------
    ("TE14 the layout reads the access token",
     LAYOUT,
     ("import { signOut } from '../auth/signOut';", REDIRECT),
     ("import { accessToken } from '../auth/accessToken';\nimport { signOut } from '../auth/signOut';",
      "  if (accessToken.current() === null) return <RedirectToSignIn />;\n" + REDIRECT),
     "own"),

    # --- AC11: no storage, no network -----------------------------------------------------------------
    ("TE15 the layout writes storage while rendering",
     LAYOUT, CLEANUP,
     "    globalThis.sessionStorage.setItem('themistra.shell.seen', '1');\n" + CLEANUP, "own"),

    # --- AC12, AC13: the hand-over, and only through endSession() ------------------------------------
    # THE ROW THIS TASK EXISTS FOR. `signOut()` clears this tab and sets the latch and does NOT end the SAS
    # session - so a visitor who clicked sign out stays signed in at the authorization server (R19), and
    # every local assertion still passes. Both functions are already imported, so it is a one-word change.
    ("TE16 the control calls signOut() instead of endSession(), so SAS keeps the session",
     LAYOUT, CONTROL_LINE,
     "        <ActionButton onClick={signOut}>{t('shell.signOut')}</ActionButton>", "own"),
    # Two hand-overs where R19 allows one. A spy that only asserted "assign was called" would pass.
    ("TE17 the control hands over twice",
     LAYOUT, CONTROL_LINE,
     ("        <ActionButton onClick={() => { endSession(); endSession(); }}>"
      "{t('shell.signOut')}</ActionButton>"), "own"),

    # --- AC14: the sibling tab, which is R22's last clause ------------------------------------------
    # The listener removed. Nothing anywhere else installs one - task 39's own suite says so in its header -
    # so this is the difference between propagation as a mechanism and propagation as a journey.
    ("TE18 the cross-tab listener is never installed",
     LAYOUT, LISTENER, "", "own"),
    # The receiving tab redirects but keeps its token: a tab that LOOKS signed out and is not. The latch
    # (`markSignedOut`) is set inside `signOut()`, so dropping the call leaves a sibling's renewal
    # adoptable too - the hazard OQ-T35-3 recorded and task 39 closed.
    ("TE19 the receiving tab redirects without signing out locally",
     LAYOUT, "      signOut();\n      setSiblingSignedOut(true);", "      setSiblingSignedOut(true);",
     "own"),

    # --- a canary and an unfalsifiable row, scored apart --------------------------------------------
    # CONTROL for TE11. The derivation task 174 added claims BOTH directions; dropping an entry tests one,
    # and adding a file that imports nothing tests the other. Without this, an equality that only ever
    # shrinks would look just as healthy.
    ("TE20 CONTROL: a file that does not import the barrel is pinned as a render site",
     ONLY, SITE, SITE + "\n  'app/mountShell.tsx',", "inherited"),
    # UNFALSIFIABLE by design, and the design is T173's: spacing is not pinned anywhere, deliberately, so a
    # padding change should be invisible. A kill would mean some case is pinning a class string it has no
    # business pinning - which is the failure mode this row exists to detect, in either direction.
    ("TE21 the banner's padding changes",
     BANNER, HEADER_LINE, HEADER_LINE.replace("px-6 pt-6", "px-8 pt-10"), "unfalsifiable"),
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
            outcomes[label.split()[0]] = ("BROKEN", expect)
            broken.append(label)
            revert()
            continue
        lost, was_broken, _names, by_own = score(label, base)
        # A BROKEN ROW IS NOT AN OUTCOME. `score` returns (None, True, None, False) for BROKEN, which
        # this line read as "not a survivor, not own" and printed as KILLED/inherited - the exact sin the
        # guard inside `score` exists to prevent, one level up. The kill COUNT already excluded it; the
        # table credited it. Eleven copies carried this and only this one had a BROKEN row to expose it.
        outcomes[label.split()[0]] = (
            "BROKEN" if was_broken else ("SURVIVED" if lost else
                                         ("KILLED/own" if by_own else "KILLED/inherited")), expect)
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
        if outcome == "BROKEN":
            flag = "DISAGREES - nothing ran, so this criterion is UNMEASURED"
        elif expect == "unfalsifiable":
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
