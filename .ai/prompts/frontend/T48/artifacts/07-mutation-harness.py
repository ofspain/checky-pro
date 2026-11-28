#!/usr/bin/env python3
"""T48 mutation harness - the verification screen. EIGHTEENTH COPY of the machinery (OQ-T32-4).

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. It wipes the whole working tree under
the paths in REVERT_PATHS, not just the mutated file.

RUN IT WITH THE LOCAL DOCKER STACK STOPPED (FU-T42-1, diagnosed in T44).

WHAT THIS TASK OWNS. `VerifyScreen` reads one query parameter, POSTs it once, and shows a success
sentence. So the rows below cluster around four things, and three of them are guards rather than
features: the ONE-REQUEST GUARD (`handled`, which StrictMode exists to break), the TOKEN'S
PROVENANCE (it must be the one in the URL, not an empty string the server will reject), the FOCUS
MOVE AND THE ANNOUNCEMENT (which is an ordering property - a live region filled at mount announces
nothing), and the SUCCESS CONDITION itself.

TWO ROWS ARE WRITTEN TO MEASURE A SUSPECTED GAP RATHER THAN TO DIE. TR05 moves the guard ABOVE the
empty-token return, and TR10 narrows `response.ok` to `=== 204`. Phase 6 predicted both might
survive. A row that survives on purpose is only honest if it is declared before the run, so they are
declared here - and if either dies, the prediction was wrong and that is the finding.

THE ROWS:

  TR01  the focus move deleted            - the screen's one unconditional behaviour (AC6, L11).
  TR02  the ref never reaches the heading  - focus has nowhere to go and the effect still runs. The
                                            mutant a case asserting "an effect exists" would miss.
  TR03  the guard set AFTER the request    - StrictMode then sends two POSTs and the token is burned
                                            twice. The regression this screen exists to prevent.
  TR04  the guard deleted entirely         - the same failure, louder.
  TR05  the guard set BEFORE the token check (DECLARED: may survive) - latches on a tokenless
                                            arrival, so a later params change carrying a real token
                                            does nothing. No case re-renders with a new token.
  TR06  the wrong query parameter name     - reads `t`, not `token`. Provenance.
  TR07  the empty-token check dropped      - posts `{ token: '' }`, which the server rejects.
  TR08  the null-token check dropped       - posts a null token on a bare /app/verify.
  TR09  the response ignored entirely      - success shown whatever came back.
  TR10  `response.ok` -> `=== 204` (DECLARED: may survive) - the contract declares only 204, so
                                            nothing in the suite may be able to tell.
  TR11  a bearer attached to the call      - the operation is `security: []` (AC3, L6).
  TR12  the attach branch removed          - the route goes back to having no element.
  TR13  VERIFY_PATH respelled              - the declared set and every membership pin.
  TR14  StatusRegion mounted only on success - the announcement dies while the words stay. The
                                            ordering bug `StatusRegion`'s own header warns about.
  TR15  the two catalogue keys swapped
  TR16  the token written to sessionStorage - L16 lists what may be stored and this is not on it.
  TR17  focus moved only when a token is present - merges the two effects, so a visitor arriving at
                                            a broken link is told nothing.
  TR18  CANARY the PINS A LIMIT case is handed a 204 - its two arms then genuinely differ, so it MUST
                                            fail. If it survives, the limit it pins is unmeasured.
  TR19  CANARY the storage control spies on an INSTANCE - T42's exact bug. The control MUST fail, or
                                            it is not proving the real spy intercepts.
  TR20  UNFALSIFIABLE useEffect -> useLayoutEffect for focus - no observable difference. A KILL here
                                            would mean a case had started pinning the hook.

PHASE 10 ADDED FOUR ROWS, for the brief's security constraints that had no instrument. The brief says the
token is "not stored, not logged, not sent to analytics, not rendered" and only the first had a case.

  TR21  the token rendered in the heading - a credential on screen is shoulder-surfable and lands in every
                                            DOM snapshot and screenshot.
  TR22  the token logged via an ALIAS      - written to EVADE the source scan on purpose. The scan forbids
                                            the literal `console.`; an alias does not contain it, so only a
                                            runtime console watcher can see this. A mutant that died to the
                                            scan would measure the scan, not the new case.
  TR23  the content type overridden        - the contract's call shape. A body the server cannot parse is a
                                            uniform failure the visitor cannot tell from a bad token.
  TR24  repeated tokens joined             - `?token=a&token=b` is a URL anyone can build; `get` takes the
                                            first and `getAll().join()` sends "a,b".

PHASE 11 ADDED ONE ROW. Kimi's F4 observed that the behavioural log case spied on five console methods
while nine exist, so `console.table(token)` would leak a credential while the case passed. The spy list is
now DERIVED from `console` at runtime instead of listed, and the derivation is itself asserted so it cannot
come back empty and make the claim vacuous.

  TR25  the token logged via `console.table` THROUGH AN ALIAS - two evasions in one mutant: the alias dodges
                                            the source scan's literal, and `table` dodged the old spy list.
                                            Against the phase 10 suite it would have survived everything.

ONE PHASE 10 ASSERTION IS UNFALSIFIABLE AND GETS NO ROW: that the request is a POST. openapi-fetch exposes
the verb as the method name and the contract declares no GET for this path, so a mutant changing it cannot
typecheck - and a mutant that cannot compile measures nothing. Declared here rather than dressed up.
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
NAMED = "frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx"

# THIS TASK'S OWN FILES: the suite it wrote. The five files whose NUMBERS it moved are not here - it
# edited counts in them, not claims, so a kill there is inherited rather than earned.
OWN_TESTS = [
    "src/features/account/verifyFromLinkShowsSuccessState.test.tsx",
]

CANARIES = {"TR18", "TR19"}
UNFALSIFIABLE = {"TR20"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

REVERT_PATHS = ["frontend/src/"]

EXPECTED_BASELINE_FAILURES = 0  # of 2042
KNOWN_ENVIRONMENTAL_FAILURES = set()  # nothing excused, so nothing an excused file could hide

# --- anchors, copied from the real source ----------------------------------------------------------------
# Each is CODE, never prose. A mutant whose anchor matches a comment is a mutant that might edit the
# comment instead (T174's preflight refused a run for exactly that).
FOCUS_BODY = "    headingRef.current?.focus();"
HEADING_LINE = "      <Heading ref={headingRef}>{t('verify.heading')}</Heading>"
REGION_LINE = "      <StatusRegion>{status === 'success' ? t('verify.success') : null}</StatusRegion>"
GUARD = "    handled.current = true;"
TOKEN_READ = "    const token = params.get('token');"
EMPTY_CHECK = "    if (token === null || token === '') return;"
POST_LINE = "      .POST('/accounts/verify-email', { body: { token } })"
OK_LINE = "        if (response.ok) setStatus('success');"
FOCUS_EFFECT = """  useEffect(() => {
    headingRef.current?.focus();
  }, []);"""
ATTACH = "        if (path === VERIFY_PATH) return { path, element: <VerifyScreen /> };"
PATH_CONST = "export const VERIFY_PATH = 'verify';"
LIMIT_TRANSPORT = "    const { sent } = installTransport(400);"
PROTOTYPE_SPY = """    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    sessionStorage.setItem('probe', 'value');"""
MUTANTS = [
    # --- the focus move: the screen's one unconditional behaviour ------------------------------------
    ("TR01 the focus move is deleted, so arrival announces nothing",
     SCREEN, FOCUS_BODY, "    void headingRef.current;", "own"),
    ("TR02 the heading never receives the ref, so the effect focuses nothing",
     SCREEN, HEADING_LINE, "      <Heading>{t('verify.heading')}</Heading>", "own"),

    # --- the one-request guard: what StrictMode exists to break --------------------------------------
    # THE ORDER IS THE WHOLE CLAIM. Both invocations read `handled.current` before either writes it,
    # because nothing between them yields - so a guard set after the request is no guard.
    ("TR03 the guard is set AFTER the request, so StrictMode sends two",
     SCREEN, (GUARD, OK_LINE),
     ("", "        if (response.ok) setStatus('success');\n        handled.current = true;"), "own"),
    ("TR04 the guard is deleted, so every re-render re-verifies",
     SCREEN, (GUARD, "    if (handled.current) return;"), ("", ""), "own"),
    # DECLARED: MAY SURVIVE. Latching on a tokenless arrival is only observable if something later
    # supplies a token to the same mounted screen, and no case does.
    ("TR05 the guard is set BEFORE the token check, latching on a tokenless arrival",
     SCREEN, (GUARD, EMPTY_CHECK),
     ("", "    handled.current = true;\n    if (token === null || token === '') return;"), "own"),

    # --- the token's provenance ----------------------------------------------------------------------
    ("TR06 the screen reads the wrong query parameter",
     SCREEN, TOKEN_READ, "    const token = params.get('t');", "own"),
    ("TR07 the empty-token check is dropped, so an empty token is posted",
     SCREEN, EMPTY_CHECK, "    if (token === null) return;", "own"),
    ("TR08 the null-token check is dropped, so a bare /app/verify posts nothing useful",
     SCREEN, EMPTY_CHECK, "    if (token === '') return;", "own"),

    # --- the success condition -----------------------------------------------------------------------
    ("TR09 the response is ignored, so any answer shows success",
     SCREEN, OK_LINE, "        setStatus('success');", "own"),
    # DECLARED: MAY SURVIVE. The contract declares one success response, so a suite that only ever
    # mocks 204 cannot tell these apart. If it dies, something is mocking another 2xx.
    ("TR10 `response.ok` narrowed to `=== 204`",
     SCREEN, OK_LINE, "        if (response.status === 204) setStatus('success');", "own"),

    # --- the call shape ------------------------------------------------------------------------------
    ("TR11 a bearer is attached to a security: [] operation",
     SCREEN, POST_LINE,
     "      .POST('/accounts/verify-email', { body: { token }, headers: { Authorization: 'Bearer x' } })",
     "own"),

    # --- the route -----------------------------------------------------------------------------------
    ("TR12 the attach branch is removed, so /app/verify has no element again",
     ROUTES, ATTACH, "", "own"),
    ("TR13 VERIFY_PATH is respelled, so the declared set changes",
     ROUTES, PATH_CONST, "export const VERIFY_PATH = 'verify-email';", "own"),

    # --- the announcement, which is an ORDERING property ---------------------------------------------
    # The words are identical in both versions; only the moment the region appears differs.
    ("TR14 StatusRegion is mounted only on success, so nothing is announced",
     SCREEN, REGION_LINE,
     "      {status === 'success' ? <StatusRegion>{t('verify.success')}</StatusRegion> : null}", "own"),
    ("TR15 the two catalogue keys are swapped",
     SCREEN, (HEADING_LINE, REGION_LINE),
     ("      <Heading ref={headingRef}>{t('verify.success')}</Heading>",
      "      <StatusRegion>{status === 'success' ? t('verify.heading') : null}</StatusRegion>"), "own"),

    # --- the token is a credential -------------------------------------------------------------------
    ("TR16 the token is written to sessionStorage",
     SCREEN, GUARD, "    handled.current = true;\n    sessionStorage.setItem('verify-token', token);", "own"),
    ("TR17 focus moves only when a token is present, merging the two effects",
     SCREEN, FOCUS_EFFECT,
     """  useEffect(() => {
    if (params.get('token') !== null) headingRef.current?.focus();
  }, [params]);""", "own"),

    # --- canaries: these mutate THIS TASK'S OWN TESTS and MUST fail ----------------------------------
    ("TR18 CANARY the PINS A LIMIT case is handed a 204, so its two arms genuinely differ",
     NAMED, LIMIT_TRANSPORT, "    const { sent } = installTransport(204);", "own"),
    ("TR19 CANARY the storage control spies on an instance, which is T42's exact bug",
     NAMED, PROTOTYPE_SPY,
     """    const setItem = vi.spyOn(sessionStorage, 'setItem');
    sessionStorage.setItem('probe', 'value');""", "own"),

    # --- PHASE 10's rows, for the brief's security constraints that had no instrument ----------------
    ("TR21 the token is rendered into the heading, where anyone can read it",
     SCREEN, HEADING_LINE,
     "      <Heading ref={headingRef}>{t('verify.heading')}{params.get('token')}</Heading>", "own"),
    # THIS MUTANT DELIBERATELY EVADES THE SOURCE SCAN, which is what makes it measure the behavioural case
    # rather than the structural one. The scan forbids the literal `console.`; an ALIAS does not contain it,
    # so only a case that watches the console at runtime can see this. A mutant that died to the scan would
    # have told us nothing about whether the behavioural assertion works.
    ("TR22 the token is logged through an aliased console, evading the source scan",
     SCREEN, GUARD,
     "    handled.current = true;\n    const sink = console;\n    sink.log('verifying', token);", "own"),
    ("TR23 the content type is overridden, so the server cannot parse the body",
     SCREEN, POST_LINE,
     "      .POST('/accounts/verify-email', { body: { token }, headers: { 'Content-Type': 'text/plain' } })",
     "own"),
    ("TR24 every repeated token is joined instead of taking the first",
     SCREEN, TOKEN_READ, "    const token = params.getAll('token').join(',');", "own"),

    # --- PHASE 11's row: the half of the log claim the first spy list could not see ------------------
    # TWO EVASIONS IN ONE MUTANT, and both are deliberate. It logs through an ALIAS, so the source scan's
    # literal `console.` does not match it; and it logs through `table`, which the first version of the
    # behavioural case did not spy on. Against the phase 10 suite this would have survived everything.
    ("TR25 the token is logged via console.table through an alias, which the old spy list missed",
     SCREEN, GUARD,
     "    handled.current = true;\n    const sink = console;\n    sink.table({ token });", "own"),

    # --- unfalsifiable, declared -----------------------------------------------------------------------
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
