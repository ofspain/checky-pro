#!/usr/bin/env python3
"""T47 mutation harness - the registration acknowledgement. SEVENTEENTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file.

RUN IT WITH THE LOCAL DOCKER STACK STOPPED (FU-T42-1, diagnosed in T44).

THE BASELINE IS 2010/2010 AND `KNOWN_ENVIRONMENTAL_FAILURES` IS EMPTY FOR THE FIRST TIME IN SIXTEEN
COPIES. Every previous copy excused `lintRejectsHandWrittenBackendFetch.test.ts`, whose assertion parsed
an oxlint summary line that oxlint 1.87.0 stopped printing. That was fixed as FU-T47-1 before this phase
(the case now asserts the ABSENCE of output), so there is nothing to excuse and nothing for an excused
file to hide. T173's warning - that a `KNOWN_ENVIRONMENTAL_FAILURES` keyed by FILE hides a real kill in
the only end-to-end build gate - no longer has a file to apply to.

WHAT THIS TASK OWNS. `RegisterSentScreen` renders two catalogue strings and moves focus; `RegisterScreen`
navigates to it. Almost every row below is therefore about one of three things: the FOCUS MOVE (which is
the only behaviour the screen has), the IDENTICAL OUTPUT (which is a property of the screen having no
inputs), and the NAVIGATION (which is the one thing that changed in a closed task's screen).

THE ROWS:

  TR01  no heading at all                 - the screen's only named element. AC1, AC6 and all of AC7.
  TR02  the focus move deleted            - the task's one behaviour. If this survives, L11 is unheld.
  TR03  the ref never reaches the heading  - focus has nowhere to go, and the effect still runs. The
                                            mutant a case asserting "an effect exists" would miss.
  TR04  the sentence HARDCODED in the screen - BYTE-IDENTICAL ON SCREEN. Only a source scan sees it, and
                                            the one that does is the assertion this task MOVED out of
                                            task 43's suite rather than deleting. This row is why that
                                            move had to be a move.
  TR05  the two keys swapped              - the heading becomes the sentence and the sentence the heading.
                                            Both keys still render, so a case that only counted them
                                            passes.
  TR06  the heading REPORTS               - 'Account created', which discloses exactly what R2 and L4
                                            exist to withhold. The enumeration row.
  TR07  the sentence distinguishes        - 'Your new account was created.' The catalogue scan for
                                            already/exists/taken/new account is what holds this.
  TR08  the submit goes nowhere           - the acknowledgement is never reached. The task undone from
                                            the form's end rather than the screen's.
  TR09  the navigation is RELATIVE        - no leading slash, so it lands on /register/register/sent.
                                            The failure mode the plan predicted and chose against.
  TR10  `replace: true` dropped           - PREDICTED SURVIVOR IN PHASE 7, AND IT SURVIVED ALL 2010 CASES.
                                            That is what turned the plan's argument for `replace` into a
                                            finding (F1). Phase 9 added the case, so the expectation moved
                                            from `phase 10` to `own` - and the case asserts the CONSEQUENCE
                                            (Back must not reach a form that would resubmit) as well as the
                                            flag, because a flag assertion pins today's spelling.
  TR11  the screen READS THE STATUS       - navigates only on 202. THE R2 ROW: the three re-aimed AC11
                                            rows (202, 409, 500) are what must catch it, and if they do
                                            not, re-aiming them instead of deleting them bought nothing.
  TR12  it navigates on FAILURE too       - a transport error reports an account that was never
                                            requested. Task 43's reject case is what sees this.
  TR13  the route gets no element         - the condition is made unreachable rather than deleted, so the
                                            import stays used and `tsc` stays quiet: a mutant that fails
                                            the build gate measures the build gate.
  TR14  the path's spelling changes       - 'register/done'. Expected INHERITED: the declared-set pins
                                            have guarded `PUBLIC_ROUTE_PATHS` since task 16.
  TR15  UNFALSIFIABLE useLayoutEffect     - focus still moves, before paint instead of after. No case can
                                            tell, and a KILL would mean a case is pinning the hook.
  TR16  CANARY the two probes made IDENTICAL - the suite mocks a new address and an already-registered one
                                            with DIFFERENT bodies, because identical mocks make an
                                            identical-output claim vacuous (trap 1). It SURVIVED in phase 7:
                                            trap 1 was defended by a comment and not by an instrument.
                                            Phase 9 added the inequality assertion, so this now expects
                                            `own`. A canary that dies is the canary working.
  TR17  CANARY the held transport answers - `holdTransportInFlight` resolves immediately, which is phase
                                            6's own decision inverted. Thirty-five re-aimed cases in two
                                            closed suites depend on it; if this does not kill loudly, the
                                            decision was not load-bearing and the cases are not measuring
                                            the form at all.

FOUR OUTCOMES, AND EVERY ROW PREDICTS ONE: `own` (killed with a named failure in a file this task wrote
or grew), `inherited` (killed only elsewhere), `phase 10` (expected to survive now and be closed there),
`unfalsifiable` (no observable effect).
"""
import os
import re
import signal
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

SENT = "frontend/src/features/account/RegisterSentScreen.tsx"
SCREEN = "frontend/src/features/account/RegisterScreen.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
NAMED = "frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx"
ACCEPTS = "frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx"

# THIS TASK'S OWN FILES: the suite it wrote, and the two closed suites whose assertions it re-aimed. The
# re-aimed files are here for the reason T46 gave for task 44's - this task wrote those assertions, in
# their current form, so a kill there is honestly `own` rather than borrowed.
OWN_TESTS = [
    "src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx",
    "src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx",
    "src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx",
]

CANARIES = {"TR16", "TR17"}
UNFALSIFIABLE = {"TR15"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/"]

EXPECTED_BASELINE_FAILURES = 0  # of 2010 - the first copy with a clean baseline
KNOWN_ENVIRONMENTAL_FAILURES = set()  # FU-T47-1 is fixed; nothing is excused and nothing can hide

# --- anchors, copied from the real source ----------------------------------------------------------------
# Each is CODE, never prose. A mutant whose anchor matches a comment is a mutant that might edit the
# comment instead (T174's preflight refused a run for exactly that).
FOCUS_BODY = "    headingRef.current?.focus();"
USE_EFFECT = "  useEffect(() => {"
HEADING_LINE = "      <Heading ref={headingRef}>{t('registerSent.heading')}</Heading>"
TEXT_LINE = "      <Text>{t('register.success')}</Text>"
SEND = ("      .then(() => navigate(`/${REGISTER_SENT_PATH}`, { replace: true }), "
        "() => setStatus('idle'));")
SENT_GROUP = """  registerSent: {
    heading: 'Check your email',
  },"""
SUCCESS_LINE = "    success: 'Check your email to finish creating your account.',"
ATTACH = "        if (path === REGISTER_SENT_PATH) return { path, element: <RegisterSentScreen /> };"
PATH_CONST = "export const REGISTER_SENT_PATH = 'register/sent';"
NEW_MOCK = ("const NEW_ADDRESS: Reply = { status: 202, "
            "body: JSON.stringify({ message: 'Verification sent to a new address.' }) };")
HOLD_BODY = "  return installTransport(() => new Promise<Reply>(() => undefined));"

MUTANTS = [
    # --- the screen's two elements -------------------------------------------------------------------
    ("TR01 the acknowledgement renders no heading, so nothing names the screen",
     SENT, HEADING_LINE, "", "own"),
    ("TR02 the focus move is deleted, so arrival announces nothing",
     SENT, FOCUS_BODY, "    void headingRef.current;", "own"),
    ("TR03 the heading never receives the ref, so the effect focuses nothing",
     SENT, HEADING_LINE, "      <Heading>{t('registerSent.heading')}</Heading>", "own"),
    ("TR04 the sentence is hardcoded in the screen - BYTE-IDENTICAL on screen",
     SENT, TEXT_LINE, "      <Text>Check your email to finish creating your account.</Text>", "own"),
    ("TR05 the two catalogue keys are swapped",
     SENT, (HEADING_LINE, TEXT_LINE),
     ("      <Heading ref={headingRef}>{t('register.success')}</Heading>",
      "      <Text>{t('registerSent.heading')}</Text>"), "own"),
    # --- the words ------------------------------------------------------------------------------------
    ("TR06 the heading REPORTS instead of instructing, disclosing what R2 withholds",
     CATALOGUE, SENT_GROUP, "  registerSent: {\n    heading: 'Account created',\n  },", "own"),
    ("TR07 the sentence distinguishes a new account from an existing one",
     CATALOGUE, SUCCESS_LINE,
     "    success: 'Your new account was created. Check your email.',", "own"),
    # --- the navigation -------------------------------------------------------------------------------
    ("TR08 the submit goes nowhere, so the acknowledgement is never reached",
     SCREEN, SEND, "      .then(() => undefined, () => setStatus('idle'));", "own"),
    ("TR09 the navigation is relative, so it lands on /register/register/sent",
     SCREEN, SEND,
     "      .then(() => navigate(`${REGISTER_SENT_PATH}`, { replace: true }), "
     "() => setStatus('idle'));", "own"),
    ("TR10 `replace: true` is dropped, so Back returns to a form that would resubmit",
     SCREEN, SEND,
     "      .then(() => navigate(`/${REGISTER_SENT_PATH}`), () => setStatus('idle'));", "own"),
    ("TR11 the screen READS THE STATUS and navigates only on a 202",
     SCREEN, SEND,
     "      .then((reply) => { if (reply.response.status === 202) "
     "navigate(`/${REGISTER_SENT_PATH}`, { replace: true }); }, () => setStatus('idle'));", "own"),
    ("TR12 it navigates on failure too, reporting an account nobody requested",
     SCREEN, SEND,
     "      .then(() => navigate(`/${REGISTER_SENT_PATH}`, { replace: true }), "
     "() => navigate(`/${REGISTER_SENT_PATH}`, { replace: true }));", "own"),
    # --- the route ------------------------------------------------------------------------------------
    ("TR13 the route's condition is made unreachable, so the path gets no element",
     ROUTES, ATTACH,
     "        if (path === 'register/nowhere') return { path, element: <RegisterSentScreen /> };",
     "own"),
    ("TR14 the path's spelling changes, so the declared set no longer holds it",
     ROUTES, PATH_CONST, "export const REGISTER_SENT_PATH = 'register/done';", "inherited"),
    # --- the control rows -----------------------------------------------------------------------------
    # TWO EDITS, because the import must come with it: a mutant that fails `tsc` measures the build gate
    # and not the suite, which is the trap T46's TR01 recorded.
    ("TR15 useEffect becomes useLayoutEffect - the focus still moves",
     SENT, ("import { useEffect, useRef, type ReactElement } from 'react';", USE_EFFECT),
     ("import { useLayoutEffect, useRef, type ReactElement } from 'react';",
      "  useLayoutEffect(() => {"), "unfalsifiable"),
    ("TR16 CANARY the two probe replies are made IDENTICAL, so trap 1 is disarmed",
     NAMED, NEW_MOCK,
     "const NEW_ADDRESS: Reply = { status: 202, "
     "body: JSON.stringify({ message: 'This address is already registered; no action taken.' }) };",
     "own"),
    ("TR17 CANARY the held transport answers at once, inverting phase 6's own decision",
     ACCEPTS, HOLD_BODY, "  return installTransport(() => ACCEPTED);", "own"),
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
