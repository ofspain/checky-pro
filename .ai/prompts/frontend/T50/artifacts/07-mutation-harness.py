#!/usr/bin/env python3
"""T50 mutation harness - R5's public, sessionless resend form. TWENTIETH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file.

RUN IT WITH THE LOCAL DOCKER STACK STOPPED (FU-T42-1, diagnosed in T44). One unidentified flake was
seen in the first of four whole-suite runs during phase 6 and not in the three after it; if a row
here disagrees, READ THE NAMED FAILING FILE before believing the count (T49's TR17 lesson).

WHAT THIS TASK OWNS, AND WHY THE ROWS LOOK LIKE THIS. R5 carries three claims and `tasks.md` gives
task 50 two of them: the call is made, and THE FORM NEEDS NO SESSION. Uniformity across server
outcomes is task 51's, on this same suite, so no row here mutates a per-outcome difference - that
would be measuring task 51's cases a task early.

"Needs no session" splits into two claims that fail differently. Reachability is cheap to hold and
cheap to keep: the route is public, and TR15 is the row for it. The REQUEST carrying no bearer is
the one that could rot silently, because every case in this file runs with nothing signed in, so
swapping the client factory would leave them all green - TR06 is that mutant, and the case that
catches it is the only one that seeds a session first.

The rest cluster around what this screen could stop being: THE ACKNOWLEDGEMENT (shown on resolve,
replacing the form), THE RESPONSE (any read of it, the first step towards a reason-specific
message), THE RETRY LINE (D-T50-1, and its clearing), THE ANNOUNCEMENT (an empty-mounted region, a
focus move that happens once and not on arrival), and THE OUTLINE (D-T50-2's single h1).

TWO ROWS EXIST TO PROVE A PIN IN ANOTHER TASK'S FILE HAS TEETH. TR10 restores the frozen design's
SECOND `<Heading>` - the thing D-T50-2 corrected - and TR18 changes `resend.heading` so it no longer
equals `verify.failureAction`, which is the cross-feature sentence the replaced task-49 handoff case
now pins. If TR18 survived, that replacement would be decoration.

ONE ROW IS DECLARED UNFALSIFIABLE. TR20 is task 48's inherited useEffect/useLayoutEffect row: with
one effect that fires on a state change, the two hooks are behaviourally identical here. A KILL
would mean a case had started pinning the hook rather than the behaviour.

THE ROWS:

  TR01  `setStatus('submitting')` deleted  - the retry line then survives into the next attempt, so a
                                            visitor is told the send failed while it is running.
  TR02  the reject arm acknowledges too    - claims a link was sent when the request never left the
                                            browser. The lie D-T50-1 exists to prevent.
  TR03  the reject arm returns to 'idle'   - KIMI'S PHASE 3 DESIGN, which the owner reversed. The
                                            screen goes back to how it looked and says nothing.
  TR04  the handler reads `response.ok`    - acknowledge or not by STATUS. The one branch that could
                                            make this surface reveal an outcome.
  TR05  the server's `message` is rendered - T47's D2 undone: copy with no catalogue key, no t() call
                                            and no owner control without a server deploy.
  TR06  createPublicAuthApi -> createAuthApi - THE SESSIONLESS CLAIM. Every case here runs signed out,
                                            so only the one that seeds a session can see this.
  TR07  the focus effect is deleted        - the acknowledgement replaces the control that held focus,
                                            so focus falls to document.body and nothing is announced.
  TR08  the focus effect fires on arrival  - steals focus from a form the visitor is about to fill.
  TR09  the form stays mounted on ack      - invites a second send against an acknowledgement that
                                            already says a link is on its way.
  TR10  the frozen design's SECOND Heading - D-T50-2's own mutant: two <h1> on one screen, which no
                                            other screen in the bundle has.
  TR11  a second field rides in the body   - the body equality's row; a `toContain` would miss it.
  TR12  the live region is mounted FILLED  - a region that exists only when it has text announces
                                            nothing, which is the trap StatusRegion's contract avoids.
  TR13  the two region lines are swapped   - 'Sending...' for a dropped connection and the retry line
                                            while in flight: both states still render, both wrong.
  TR14  the email field loses its label    - an unnamed control, which L11 forbids.
  TR15  the route element is removed       - REACHABILITY, back to the blank document task 49 left.
  TR16  the attached-screens pin loses the entry - the handoff list stops naming what is attached.
  TR17  the credential table row is deleted - the L2/L5 scan this task had to widen stops being
                                            satisfied, so a form render site is unexplained again.
  TR18  `resend.heading` no longer matches `verify.failureAction` - THE CROSS-FEATURE SENTENCE, held
                                            by the case that replaced task 49's handoff.
  TR19  CANARY: the acknowledgement renders nothing at all.
  TR20  UNFALSIFIABLE useEffect -> useLayoutEffect for focus - no observable difference, inherited
                                            from tasks 48 and 49.
"""
import os
import re
import signal
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"
SCREEN = "frontend/src/features/account/ResendVerificationScreen.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
ATTACHED = "frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx"
CREDENTIALS = "frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx"

REVERT_PATHS = ["frontend/src"]

OWN_TESTS = [
    "src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx",
    "src/features/account/verifyFailureShowsOneUniformMessage.test.tsx",
]

CANARIES = {"TR19"}
UNFALSIFIABLE = {"TR20"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible
KNOWN_ENVIRONMENTAL_FAILURES = set()  # nothing excused, so nothing an excused file could hide

EXPECTED_BASELINE_FAILURES = 0  # of 2113

SETTLE = ".then(() => setStatus('acknowledged'), () => setStatus('unsent'));"
HEADING_BLOCK = """      <Heading ref={headingRef}>
        {acknowledged ? t('resend.acknowledgedHeading') : t('resend.heading')}
      </Heading>"""
ACK_BRANCH = """      {acknowledged ? (
        <Text>{t('resend.acknowledgedText')}</Text>
      ) : ("""
REGION = """      <StatusRegion>
        {status === 'submitting' ? t('resend.submitting') : null}
        {status === 'unsent' ? t('resend.unsent') : null}
      </StatusRegion>"""

MUTANTS = [
    ("TR01 the submitting reset is deleted, so a stale retry line survives into the next attempt",
     SCREEN, "    setStatus('submitting');\n    void createPublicAuthApi()",
     "    void createPublicAuthApi()", "own"),
    ("TR02 the reject arm acknowledges, claiming a link was sent when nothing left the browser",
     SCREEN, SETTLE, ".then(() => setStatus('acknowledged'), () => setStatus('acknowledged'));", "own"),
    ("TR03 the reject arm returns to idle - Kimi's phase 3 design, which the owner reversed",
     SCREEN, SETTLE, ".then(() => setStatus('acknowledged'), () => setStatus('idle'));", "own"),
    ("TR04 the handler reads response.ok, so the status decides what is shown",
     SCREEN, SETTLE,
     ".then(({ response }) => setStatus(response.ok ? 'acknowledged' : 'unsent'), "
     "() => setStatus('unsent'));", "own"),
    # THREE EDITS, BECAUSE A ONE-LINE VERSION OF THIS ROW DOES NOT COMPILE and a mutant that cannot
    # compile measures nothing (T36). Rendering server copy needs the value read, held and then shown, so
    # the row does all three - which is also the honest shape of the regression T47's D2 forbids.
    # AND THE THIRD EDIT ANCHORS ON THE WHOLE BRANCH, not on the one <Text> line: a second element
    # inside a ternary arm needs a FRAGMENT, and the one-line version failed with TS2657 when it was
    # hand-applied here before the run. A JSX mutant must leave valid JSX behind (T48).
    ("TR05 the server's message is read, held and rendered - T47's D2 undone",
     SCREEN, ("  const headingRef = useRef<HTMLHeadingElement>(null);",
              SETTLE,
              ACK_BRANCH),
     ("  const headingRef = useRef<HTMLHeadingElement>(null);\n"
      "  const [serverMessage, setServerMessage] = useState('');",
      ".then(({ data }) => { setServerMessage(data?.message ?? ''); setStatus('acknowledged'); },"
      " () => setStatus('unsent'));",
      """      {acknowledged ? (
        <>
          <Text>{t('resend.acknowledgedText')}</Text>
          <Text>{serverMessage}</Text>
        </>
      ) : ("""), "own"),
    ("TR06 the AUTHENTICATED client factory is used, so the request carries a bearer",
     SCREEN, "createPublicAuthApi()", "createAuthApi()", "own"),
    ("TR07 the focus effect is deleted, so focus falls to the body when the form is replaced",
     SCREEN, "    if (status === 'acknowledged') headingRef.current?.focus();",
     "    if (status === 'never') headingRef.current?.focus();", "own"),
    ("TR08 the focus effect fires on arrival too, stealing focus from the form",
     SCREEN, "    if (status === 'acknowledged') headingRef.current?.focus();",
     "    headingRef.current?.focus();", "own"),
    ("TR09 the form stays mounted after the acknowledgement, inviting a second send",
     SCREEN, ACK_BRANCH,
     """      {acknowledged ? (
        <Text>{t('resend.acknowledgedText')}</Text>
      ) : null}
      {true ? (""", "own"),
    ("TR10 the frozen design's SECOND Heading is restored, giving the screen two h1 (D-T50-2)",
     SCREEN, ACK_BRANCH,
     """      {acknowledged ? (
        <>
          <Heading>{t('resend.acknowledgedHeading')}</Heading>
          <Text>{t('resend.acknowledgedText')}</Text>
        </>
      ) : (""", "own"),
    ("TR11 a second field rides along in the request body",
     SCREEN, "{ body: { email } }", "{ body: { email, locale: 'en' } as { email: string } }", "own"),
    ("TR12 the live region is mounted only when it has text, so it announces nothing",
     SCREEN, REGION,
     """      {status === 'submitting' || status === 'unsent' ? (
        <StatusRegion>
          {status === 'submitting' ? t('resend.submitting') : null}
          {status === 'unsent' ? t('resend.unsent') : null}
        </StatusRegion>
      ) : null}""", "own"),
    ("TR13 the two region lines are swapped: 'Sending' for a failure, the retry line in flight",
     SCREEN, REGION,
     """      <StatusRegion>
        {status === 'unsent' ? t('resend.submitting') : null}
        {status === 'submitting' ? t('resend.unsent') : null}
      </StatusRegion>""", "own"),
    ("TR14 the email field loses its label, leaving the control unnamed",
     SCREEN, "<EmailField label={t('resend.emailLabel')} value={email} onChange={setEmail} />",
     "<EmailField label={''} value={email} onChange={setEmail} />", "own"),
    ("TR15 the route element is removed - back to the blank document task 49 left",
     ROUTES, "        if (path === RESEND_VERIFICATION_PATH)\n"
     "          return { path, element: <ResendVerificationScreen /> };",
     "        if (path === 'never-matches-anything')\n"
     "          return { path, element: <ResendVerificationScreen /> };", "own"),
    ("TR16 the attached-screens handoff list loses the entry",
     ATTACHED, "  'resend-verification',\n  'signed-out',", "  'signed-out',", "own"),
    ("TR17 the credential table row this task added is deleted",
     CREDENTIALS, "    file: 'features/account/ResendVerificationScreen.tsx',",
     "    file: 'features/account/ResendVerificationScreenRENAMED.tsx',", "own"),
    ("TR18 resend.heading no longer matches verify.failureAction, the cross-feature sentence",
     CATALOGUE, "    heading: 'Resend verification email',\n    emailLabel: 'Email address',\n"
     "    submit: 'Send link',",
     "    heading: 'Send a new verification email',\n    emailLabel: 'Email address',\n"
     "    submit: 'Send link',", "own"),
    ("TR19 CANARY the acknowledgement renders nothing at all",
     SCREEN, "        <Text>{t('resend.acknowledgedText')}</Text>", "        <Text>{''}</Text>", "own"),
    ("TR20 UNFALSIFIABLE useEffect -> useLayoutEffect for the focus move",
     SCREEN, ("import { useEffect, useRef, useState, type ReactElement } from 'react';",
              "  useEffect(() => {"),
     ("import { useLayoutEffect, useRef, useState, type ReactElement } from 'react';",
      "  useLayoutEffect(() => {"), "own"),
]


# --- COPY SELF-CHECK -------------------------------------------------------------------------------
# COPY 20 LOST TWO NAMES AND FOUND OUT 40 MINUTES INTO A RUN. This harness is carried forward by hand
# every task, and the header is where it gets spliced: T48 lost `FRONTEND`, T49 lost `os`/`re`/`signal`,
# and this copy lost `FILES` and `KNOWN_ENVIRONMENTAL_FAILURES` - the second time the loss was in the
# CONSTANTS rather than the imports, which is why checking the imports alone was not enough.
#
# So the names the machinery below needs are asserted HERE, before a single mutant is applied, rather
# than being discovered by a NameError after the baseline has been run.
for _required in ("os", "re", "signal", "subprocess", "sys", "Path", "REPO", "FRONTEND", "REVERT_PATHS",
                  "OWN_TESTS", "FILES", "KNOWN_ENVIRONMENTAL_FAILURES", "EXPECTED_BASELINE_FAILURES",
                  "CANARIES", "UNFALSIFIABLE", "MUTANTS"):
    if _required not in dir():
        raise SystemExit(f"the copy lost `{_required}` - restore it before running anything")


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
