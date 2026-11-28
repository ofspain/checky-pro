#!/usr/bin/env python3
"""T49 mutation harness - R4's one uniform failure. NINETEENTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file.

RUN IT WITH THE LOCAL DOCKER STACK STOPPED (FU-T42-1, diagnosed in T44).

WHAT THIS TASK OWNS, AND WHY THE ROWS LOOK LIKE THIS. R4 is a UNIFORMITY requirement, and uniformity
is the hardest kind of claim to mutate against, because the obvious mutant - "show a different
message for an expired token" - cannot be written: the backend collapses invalid, expired, used and
deleted-account into ONE fixed 400 before the SPA sees any of them. There is no reason in the
response to branch on.

So the rows cluster around the five ways this screen could stop being uniform or stop being a
failure screen at all: THE FAILURE EXISTING (a non-ok response, a dropped connection, a tokenless
link), THE FLASH (idle rendering failure copy, which would hit every SUCCESSFUL visitor), THE
ACTION (present, neutral, outside the live region), THE BODY (any read of it at all, which is the
first step towards a reason-specific message), and THE CREDENTIAL (a token echoed into the new DOM
this task adds).

ONE ROW IS DECLARED TO SURVIVE AND ONE IS DECLARED UNFALSIFIABLE. TR17 swaps the path constant for
the identical bare literal: behaviourally those are the same string, so no case can tell them apart
while they agree - which is the honest account of what D-T49-1 buys. What catches a DIVERGENCE is
the href assertion, which compares against the constant rather than a literal of its own, and TR18
is the row that proves it. TR20 is task 48's inherited useEffect/useLayoutEffect row.

THE ROWS:

  TR01  the tokenless branch sets nothing  - back to task 48's behaviour: a truncated link renders a
                                            heading and empty space. D-T49-2, the owner's decision.
  TR02  the empty-token case falls through - `token === null` only, so `{ token: '' }` is posted.
  TR03  a non-ok response sets idle        - the failure never appears for ANY HTTP failure. The
                                            single biggest regression this task can suffer.
  TR04  the rejection arm emptied          - back to `() => {}`: a dropped connection shows nothing,
                                            which is the arm task 48 left and R4 calls a reason.
  TR05  the response ignored entirely      - success whatever came back, so a rejected token reads
                                            as verified.
  TR06  `setStatus('idle')` deleted        - THE FLASH, in its reachable form: a stale failure stays
                                            on screen for the whole round trip of a verification
                                            that is about to succeed.
  TR07  idle renders the failure message   - THE FLASH in its direct form, hitting every successful
                                            visitor before their 204 lands.
  TR08  the resend link deleted            - R4 half implemented: a message with nowhere to go.
  TR09  the link moved INSIDE the region   - an interactive control in a `role="status"` region is
                                            re-announced on every change.
  TR10  the message moved OUT of the region - rendered but never announced, which is the defect
                                            `StatusRegion`'s own header exists to prevent.
  TR11  the link also shown on success     - "resend verification" offered to someone just verified.
  TR12  focus moved to the link on failure - interrupts a screen-reader user mid-sentence.
  TR13  the catalogue message names a reason - 'has expired', which is precisely what L4 forbids and
                                            exactly the edit someone writing helpful copy makes.
  TR14  the response body is read          - `response.status === 410` branches, the first step
                                            towards a reason-specific message. Reads, renders
                                            nothing: it must die on the SCAN, not on behaviour.
  TR15  the token rendered in the failure DOM - a credential on screen, in the state this task adds.
  TR16  the token logged via an alias      - evades the literal `console.` ban on purpose, so only
                                            the derived console spy can see it.
  TR17  the path constant -> a bare literal (DECLARED: survives) - identical string, so nothing can
                                            tell them apart while they agree. See above.
  TR18  the path RENAMED in routes.tsx     - the row that gives TR17 its meaning: a screen using the
                                            constant follows the rename and the href assertion still
                                            passes, so this must die in the ROUTE suites and not in
                                            this task's own. Scored as inherited.
  TR19  a canary: the failure message blanked in the catalogue.
  TR20  UNFALSIFIABLE useEffect -> useLayoutEffect for focus - no observable difference, inherited
                                            from task 48. A KILL here would mean a case had started
                                            pinning the hook.
"""

import os
import re
import signal
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"
SCREEN = "frontend/src/features/account/VerifyScreen.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
NAMED = "frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx"
INHERITED = "frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx"

# THIS TASK'S OWN FILES: the suite it wrote, AND task 48's suite, because this task rewrote three
# CLAIMS in it rather than moving a number - the two AC12 cases and `PINS A LIMIT`. The key-set pin in
# the design-system file is not here: that was a list entry, so a kill there is inherited.
OWN_TESTS = [
    "src/features/account/verifyFailureShowsOneUniformMessage.test.tsx",
    "src/features/account/verifyFromLinkShowsSuccessState.test.tsx",
]

CANARIES = {"TR19"}
# TR17 IS HERE BECAUSE A DECLARATION IN THE DOCSTRING IS NOT A DECLARATION. The first run reported it as
# a disagreement, correctly: the prose said "DECLARED: survives" and the row was tagged `own`, so the
# harness expected a kill and said so. Behaviourally the constant and the identical literal ARE the same
# string, which is what makes this unfalsifiable rather than merely unkilled - and TR18, the rename, is
# what measures the difference the constant actually buys.
UNFALSIFIABLE = {"TR17", "TR20"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/"]

EXPECTED_BASELINE_FAILURES = 0  # of 2083
KNOWN_ENVIRONMENTAL_FAILURES = set()  # nothing excused, so nothing an excused file could hide

# --- anchors, copied from the real source ----------------------------------------------------------------
# Each is CODE, never prose. A mutant whose anchor matches a comment is a mutant that might edit the
# comment instead (T174's preflight refused a run for exactly that).
FOCUS_BODY = "    headingRef.current?.focus();"
FOCUS_EFFECT = """  useEffect(() => {
    headingRef.current?.focus();
  }, []);"""
TOKENLESS_BRANCH = """    if (token === null || token === '') {
      setStatus('failure');
      return;
    }"""
GUARD_AND_IDLE = """    handled.current = true;
    setStatus('idle');"""
SETTLE = "        setStatus(response.ok ? 'success' : 'failure');"
REJECT_ARM = """      }, () => {
        setStatus('failure');
      });"""
REGION_BLOCK = """      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
        {status === 'failure' ? t('verify.failureMessage') : null}
      </StatusRegion>"""
LINK_BLOCK = """      {status === 'failure' ? (
        <TextLink to={`/${RESEND_VERIFICATION_PATH}`}>{t('verify.failureAction')}</TextLink>
      ) : null}"""
CATALOGUE_MESSAGE = "    failureMessage: 'This verification link is not valid.',"
PATH_CONST = "export const RESEND_VERIFICATION_PATH = 'resend-verification';"

MUTANTS = [
    # --- the failure existing at all -----------------------------------------------------------------
    ("TR01 a tokenless link sets no failure, back to task 48's blank screen",
     SCREEN, TOKENLESS_BRANCH,
     "    if (token === null || token === '') {\n      return;\n    }", "own"),
    ("TR02 the empty-token case falls through, so `{ token: '' }` is posted",
     SCREEN, TOKENLESS_BRANCH,
     "    if (token === null) {\n      setStatus('failure');\n      return;\n    }", "own"),
    ("TR03 a non-ok response sets idle, so no HTTP failure ever shows the message",
     SCREEN, SETTLE, "        setStatus(response.ok ? 'success' : 'idle');", "own"),
    ("TR04 the rejection arm is emptied, back to task 48's `() => {}`",
     SCREEN, REJECT_ARM, "      }, () => {});", "own"),
    ("TR05 the response is ignored, so a rejected token reads as verified",
     SCREEN, SETTLE, "        setStatus('success');", "own"),

    # --- the flash: the defect this task was most likely to ship --------------------------------------
    ("TR06 `setStatus('idle')` deleted, so a stale failure survives into the next request",
     SCREEN, GUARD_AND_IDLE, "    handled.current = true;", "own"),
    ("TR07 idle renders the failure message, so every successful visitor sees it first",
     SCREEN, REGION_BLOCK,
     """      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
        {status === 'success' ? null : t('verify.failureMessage')}
      </StatusRegion>""", "own"),

    # --- the action ----------------------------------------------------------------------------------
    ("TR08 the resend link is deleted, so R4's action is gone",
     SCREEN, LINK_BLOCK, "      {null}", "own"),
    ("TR09 the link is moved INSIDE the live region, so it is re-announced on every change",
     SCREEN, REGION_BLOCK + "\n" + LINK_BLOCK,
     """      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
        {status === 'failure' ? t('verify.failureMessage') : null}
        {status === 'failure' ? (
          <TextLink to={`/${RESEND_VERIFICATION_PATH}`}>{t('verify.failureAction')}</TextLink>
        ) : null}
      </StatusRegion>""", "own"),
    ("TR10 the failure message is moved OUT of the live region, so it is never announced",
     SCREEN, REGION_BLOCK,
     """      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
      </StatusRegion>
      {status === 'failure' ? t('verify.failureMessage') : null}""", "own"),
    ("TR11 the link is shown on success too, offering a resend to someone just verified",
     SCREEN, LINK_BLOCK,
     """      {status === 'failure' || status === 'success' ? (
        <TextLink to={`/${RESEND_VERIFICATION_PATH}`}>{t('verify.failureAction')}</TextLink>
      ) : null}""", "own"),
    ("TR12 focus is moved to the resend link when the failure lands",
     SCREEN, FOCUS_EFFECT,
     """  useEffect(() => {
    if (status === 'failure') document.querySelector('a')?.focus();
    else headingRef.current?.focus();
  }, [status]);""", "own"),

    # --- the copy, and the structural ban ------------------------------------------------------------
    ("TR13 the catalogue message names a reason, which is exactly what L4 forbids",
     CATALOGUE, CATALOGUE_MESSAGE,
     "    failureMessage: 'This verification link has expired or was already used.',", "own"),
    # READS THE BODY AND RENDERS NOTHING, which is the point: it must die on the SCAN. If it dies only
    # in a behavioural case, the scan is not doing the job it was written for.
    ("TR14 the response body is read, the first step towards a reason-specific message",
     SCREEN, SETTLE,
     "        const gone = response.status === 410;\n"
     "        setStatus(response.ok ? 'success' : gone ? 'failure' : 'failure');", "own"),

    # --- the credential, in the DOM this task adds ---------------------------------------------------
    ("TR15 the token is rendered into the failure message, where anyone can read it",
     SCREEN, REGION_BLOCK,
     """      <StatusRegion>
        {status === 'success' ? t('verify.success') : null}
        {status === 'failure' ? `${t('verify.failureMessage')}${params.get('token') ?? ''}` : null}
      </StatusRegion>""", "own"),
    # EVADES THE SOURCE SCAN ON PURPOSE: `const sink = console;` does not contain the literal
    # `console.`, so only the derived console spy can see this.
    #
    # RE-AIMED AFTER THE FIRST RUN, AND THE FIRST AIM IS THE FINDING. It injected the log into the
    # TOKENLESS branch, where `token` is `null` or `''` BY DEFINITION - so the mutant could not leak a
    # credential even in principle, and the console case, which arrives WITH a token, never ran that
    # branch. It survived 2083 cases and measured nothing about the hole it claimed to record. The
    # instrument was never the problem: re-aimed at the failure settle path, where the real token is in
    # closure scope, it dies in exactly one case.
    ("TR16 the token is logged through an aliased console in the failure path",
     SCREEN, SETTLE,
     "        const sink = console;\n"
     "        if (!response.ok) sink.table({ token });\n"
     "        setStatus(response.ok ? 'success' : 'failure');", "own"),

    # --- declared to survive, and the row that gives it meaning --------------------------------------
    ("TR17 the path constant is replaced by the identical bare literal (DECLARED: survives)",
     SCREEN, "<TextLink to={`/${RESEND_VERIFICATION_PATH}`}>",
     '<TextLink to="/resend-verification">', "unfalsifiable"),
    ("TR18 the path is RENAMED in routes.tsx, which the constant's users must follow",
     ROUTES, PATH_CONST, "export const RESEND_VERIFICATION_PATH = 'resend-verify';", "other"),

    # --- canary --------------------------------------------------------------------------------------
    ("TR19 CANARY the failure message is blanked in the catalogue",
     CATALOGUE, CATALOGUE_MESSAGE, "    failureMessage: '',", "own"),

    # --- unfalsifiable, declared ---------------------------------------------------------------------
    # TWO EDITS, BECAUSE ONE WOULD NOT COMPILE. `useLayoutEffect` has to be imported as well, and a
    # mutant that fails to typecheck measures nothing at all - T36 scored one as a kill before noticing.
    ("TR20 UNFALSIFIABLE useEffect -> useLayoutEffect for the focus move",
     SCREEN,
     ("import { useEffect, useRef, useState, type ReactElement } from 'react';", FOCUS_EFFECT),
     ("import { useEffect, useLayoutEffect, useRef, useState, type ReactElement } from 'react';",
      """  useLayoutEffect(() => {
    headingRef.current?.focus();
  }, []);"""), "unfalsifiable"),
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


def install_signal_reverts():
    """Revert the live mutant when this process is TERMINATED rather than interrupted.

    Phase 11's F1, with its mechanism corrected. A default SIGTERM terminates without unwinding, so
    the per-row `finally` does not run and whichever mutant was live stays on disk - production
    source, modified, in a tree the next `git add` would stage. SIGHUP is here for the same reason:
    a background run outliving its shell.

    `os._exit` ON PURPOSE. Reverting and then letting SystemExit propagate would revert a second time
    through the `finally`, out of a signal handler, while a vitest child is still mid-run. The tree is
    already clean by then, so there is nothing left worth unwinding for, and an immediate exit cannot
    be interrupted again halfway through the revert it is doing.
    """
    def handler(signum, _frame):
        print(f"\n  SIGNAL {signal.Signals(signum).name}: reverting the live mutant before exit.",
              flush=True)
        revert()
        os._exit(128 + signum)

    for sig in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(sig, handler)


def dirty():
    """Uncommitted changes under REVERT_PATHS, as porcelain lines.

    TWO THINGS THIS CATCHES, and task 46 paid for both (phase 7's F3, phase 8's I5).

    A LEFTOVER MUTANT. `revert()` runs after a row, so a process killed between the mutation and the
    revert leaves production source modified - which happened: the host reaped this harness mid-row under
    memory pressure and left `ADVISORY_MINIMUM_CODE_POINTS = 11` in the tree. The next run's baseline guard
    would catch that one only BECAUSE it breaks tests; a leftover from an unfalsifiable row like TR24 or
    TR50 changes nothing observable, would pass every guard, and would be committed by the next `git add`.

    UNCOMMITTED TEST EDITS. `main()`'s first act is `revert()`, which is `git checkout --` plus
    `git clean -fq`: it DESTROYS uncommitted work under those paths without asking. That cost 19 cases in
    T14 and 5 in T15 and has been a standing rule ever since; a rule a tool can enforce should not stay a
    rule people remember.
    """
    out = subprocess.run(["git", "status", "--porcelain", "--", *REVERT_PATHS],
                         cwd=REPO, capture_output=True, text=True, check=False)
    return [line for line in out.stdout.splitlines() if line.strip()]


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
    install_signal_reverts()
    unclean = dirty()
    if unclean:
        print("REFUSING TO RUN: there are uncommitted changes under the paths this harness reverts.\n"
              "  Commit them first - `revert()` is `git checkout --` plus `git clean -fq` and would\n"
              "  destroy them - or, if one of these is a mutant left behind by a killed run, restore it\n"
              "  with `git checkout --` before measuring anything:\n"
              + "\n".join(f"    {line}" for line in unclean))
        return 1
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
        # THE REVERT IS IN A `finally` (phase 7's F3), AND A `finally` IS NOT ENOUGH. Measured, because
        # phase 11's F1 said the revert only runs after every row and that is not true - it is per row,
        # right here. What is true is the conclusion: a default SIGTERM does not unwind the stack, so this
        # `finally` never runs and the live mutant stays on disk. Both signals were measured rather than
        # reasoned about: SIGINT exits -2 with the revert DONE (Ctrl-C raises KeyboardInterrupt, so the
        # phase 9 comment was right about Ctrl-C), SIGTERM exits -15 with the mutant STILL THERE. That is
        # the signal a killed background run gets, which is how this harness has actually been interrupted
        # twice. `install_signal_reverts()` below closes it. SIGKILL still cannot be covered by anything,
        # and `dirty()` is what covers that, on the next run.
        try:
            lost, was_broken, _names, by_own = score(label, base)
        finally:
            revert()
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
