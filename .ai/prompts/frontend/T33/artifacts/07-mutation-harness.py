#!/usr/bin/env python3
"""T33 mutation harness - clean re-auth on failed renewal.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. Uncommitted test edits are destroyed
silently - that cost 19 cases in T14 and 5 in T15.

THE MACHINERY IS T32'S, UNCHANGED, and that is deliberate: the baseline is pinned by FAILING FILE
NAME with the count only warning (T31 A7), every anchor must match exactly once before a single
verdict prints (T30's real defect), and a BROKEN mutant FAILS the run like a survivor (T31 phase
9). No contract paths this time - every T33 mutant edits `frontend/src/`.

WHAT A MUTANT HERE CANNOT DO, carried from T32 phase 11: it can only record a fix that turns a
survivor into a kill. T33 has one documented limit of the false-POSITIVE kind - a tab outside the
SPA remembers no return target - and that is measured by a control, not by a row below. The
harness measures an instrument's strength; only a control measures its precision.

    python3 .ai/prompts/frontend/T33/artifacts/07-mutation-harness.py          # all
    python3 .ai/prompts/frontend/T33/artifacts/07-mutation-harness.py NR04     # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

REAUTH = "frontend/src/auth/cleanReauth.ts"
NOTICE = "frontend/src/auth/signInNotice.ts"
CATALOGUE = "frontend/src/i18n/catalogue.ts"
SCREEN = "frontend/src/auth/SignInScreen.tsx"
ROUTES = "frontend/src/app/routes.tsx"
CLIENT = "frontend/src/api/client.ts"

OWN_TEST = "src/auth/failedRenewalTriggersCleanReauthWithoutHalfSession.test.tsx"

# A CANARY is not a regression in this task's code. It is a change elsewhere in the repository that will one
# day be made deliberately, and on that day T33's claim stops being true for free. It must be killed today,
# so a survivor still fails the run - but it is scored SEPARATELY, because a mutation score is a measure of
# the current code and a row everyone expects to change state would teach a reader to discount the number.
# Kimi phase 8 I2 asked for this split and was right to.
CANARIES = {"NR17", "NR24"}
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
CLEAR = "    clearAccessToken();"
REMEMBER = "    if (here !== null) rememberReturnTarget(here);"
SET_NOTICE = "    setSignInNotice('session-ended');"
NAVIGATE = "    navigate(`/${SIGN_IN_PATH}`);"
HANDLER_BODY = "  return () => {"
HANDLER_SIG = "export function createRenewalFailureHandler(navigate: (to: string) => void): () => void {"
NOTICE_MAP = "  'session-ended': 'signIn.sessionEnded',"
SENTENCE = "    sessionEnded: 'Your session has ended. Sign in again.',"
SCREEN_NOTICE = "t(NOTICE_COPY_KEY[notice])"
AUTH_ROUTES = "      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path })),"
SIGN_IN_ROUTE = "        if (path === SIGN_IN_PATH) return { path, element: <SignInScreen /> };"
IN_APP_PREFIX = "  return path.startsWith(`${base}/`) ? path.slice(base.length) : null;"
CLIENT_API = "export function createAuthApi(boundary: SessionBoundary, rateLimit?: RateLimitNoticeState): AuthApi {"
CLIENT_IMPORT = "import type { SessionBoundary } from './sessionBoundary';"

MUTANTS = [
    # --- NR01-NR02: the token (AC1). The first clause of "no half-session" is a tab holding a token ----
    ("NR01 the held token is never dropped", REAUTH, CLEAR, "    void 0;"),

    # Not "forgets to clear" but "clears a moment later", which is the shape a refactor produces and the
    # one a synchronous assertion is needed to catch. In that window the tab holds a token it cannot use.
    ("NR02 the token is cleared asynchronously, leaving a window where it is still held",
     REAUTH, CLEAR, "    globalThis.setTimeout(clearAccessToken, 0);"),

    # The ORDER, which the module says is load-bearing. TWO edits, because one replacement can only
    # delete a line and deletion is already NR01: this moves the clear BELOW the navigation, so every
    # ordinary case still passes - the token does get cleared - and only the throwing-navigate case can
    # tell the mutant from the original. That is the point of the order, so that is the only case that
    # should be able to see it.
    ("NR03 the token is cleared LAST, after the navigation that can throw",
     REAUTH, (CLEAR + "\n", NAVIGATE), ("", NAVIGATE + "\n" + CLEAR)),

    # --- NR04-NR08: the copy (AC5), which is where L4 is kept or lost -------------------------------
    ("NR04 no notice is set, so the screen says nothing about why the visitor is here",
     REAUTH, SET_NOTICE, "    void 0;"),
    ("NR05 the notice reuses `discarded`, whose sentence says sign-in did not complete",
     REAUTH, SET_NOTICE, "    setSignInNotice('discarded');"),
    ("NR06 the map points the new notice at the discarded sentence",
     NOTICE, NOTICE_MAP, "  'session-ended': 'signIn.discarded',"),
    ("NR07 the sentence becomes a duplicate of the discarded one",
     CATALOGUE, SENTENCE, "    sessionEnded: 'Your sign-in did not complete. Try again.',"),
    # L4 in one line: a sentence that names the cause tells an anonymous prober what happened.
    ("NR08 the sentence names the cause", CATALOGUE, SENTENCE,
     "    sessionEnded: 'Your session token expired and could not be renewed. Sign in again.',"),

    # --- NR09: the screen (AC5). The notice is invisible unless the screen chooses by it -------------
    ("NR09 the screen renders the discarded sentence for every notice, as it did before this task",
     SCREEN, SCREEN_NOTICE, "t('signIn.discarded')"),

    # --- NR10-NR12: the destination (AC4/AC10) ------------------------------------------------------
    ("NR10 the visitor is sent to the signed-out landing, which states something false",
     REAUTH, NAVIGATE, "    navigate('/signed-out');"),
    ("NR11 the path is spelled again as a literal instead of read from the route table",
     REAUTH, NAVIGATE, "    navigate('/sign-in');"),
    # T16 measured that a module-scope read of a route constant yields `undefined` rather than throwing,
    # so this mutant fails CLOSED and silently - the worst available failure mode.
    ("NR12 a route constant is read at module scope", REAUTH, HANDLER_SIG,
     "const DESTINATION = `/${SIGN_IN_PATH}`;\nvoid DESTINATION;\n" + HANDLER_SIG),

    # --- NR13-NR14: the failure reason (AC9), which L4 requires to be unreachable -------------------
    ("NR13 the handler takes the failure reason back", REAUTH, HANDLER_BODY,
     "  return (reason: unknown) => {\n    void reason;"),
    ("NR14 the copy is chosen by the failure reason", REAUTH, HANDLER_BODY,
     "  return (reason: unknown) => {\n"
     "    if (reason instanceof Error) setSignInNotice('discarded');"),

    # --- NR15-NR16: the return target (AC6) ---------------------------------------------------------
    ("NR15 where the visitor was is forgotten", REAUTH, REMEMBER, "    void here;"),
    # Bypassing the allowlist is the dangerous half: an unhonourable target then SITS in storage, which
    # L16 is about as much as it is about what gets acted on (T24-D15).
    ("NR16 the target is written straight to storage, bypassing the allowlist", REAUTH, REMEMBER,
     "    if (here !== null) globalThis.sessionStorage.setItem('themistra.auth.return_to', here);"),

    # --- NR19-NR23: the five holes Kimi's phase 11 found in the instruments themselves -------------
    # Each SURVIVED the suite as it stood at phase 10 and is killed by the phase 11 repair. A finding taken
    # into code is only recorded honestly by a mutant that dies for the new reason.

    # F3: the sweep's needles were narrower than the cause-word list, so a logged cause walked past both it
    # and the catalogue check - the catalogue sentence being unchanged.
    ("NR19 the cause is logged to the console, in words the sweep did not watch for",
     REAUTH, SET_NOTICE,
     "    console.warn('session renewal failed: the access token expired');\n" + SET_NOTICE),

    # F4: the order, below the step that only the new throwing-remember case can see. Two edits, because
    # one replacement can only delete - and deletion is NR01.
    ("NR20 the token is cleared after the return target is remembered",
     REAUTH, (CLEAR + "\n", REMEMBER), ("", REMEMBER + "\n" + CLEAR)),

    # F5: the full-page bypass. It discards the in-memory notice this task just set, so the visitor arrives
    # at sign-in with nothing to read - a defect no behavioural case in this file could see.
    ("NR21 the injected navigate is bypassed with a full-page location assignment",
     REAUTH, NAVIGATE, "    globalThis.location.assign(`${APP_BASENAME}/${SIGN_IN_PATH}`);"),

    # F7: the basename boundary. `/applications/x` becomes INSIDE the SPA, and its return target points out
    # of it - which `rememberReturnTarget`'s allowlist then rejects, so the only visible trace is the wrong
    # path being offered. Only a direct test of the normalisation can see this.
    ("NR22 the basename prefix test drops the separator, claiming /applications for the SPA",
     REAUTH, IN_APP_PREFIX,
     "  return path.startsWith(base) ? path.slice(base.length) : null;"),

    # F9: a queue instead of a replacement, which would show the visitor the older and now-wrong sentence.
    ("NR23 a notice already pending is left in place instead of replaced",
     REAUTH, SET_NOTICE,
     "    if (takeSignInNotice() === null) setSignInNotice('session-ended');\n"
     "    else setSignInNotice('discarded');"),

    # --- NR18: the destination is tied to the screen that can start re-auth (AC10) -----------------
    # Phase 10 found that nothing tied them, so this is what proves the case it got actually fires. The
    # route still resolves and still renders something, which is why the weaker case could not see it.
    ("NR18 the sign-in route carries a different screen, so the destination cannot start re-auth",
     ROUTES, SIGN_IN_ROUTE,
     "        if (path === SIGN_IN_PATH) return { path, element: <CallbackScreen /> };"),

    # --- NR24: the wiring arrives (AC12) - a CANARY, like NR17 --------------------------------------
    # Not a defect. It is the day the SPA is wired up, and the case it kills is the handover note telling
    # that author what to pass as `onRenewalFailed`. Scored apart for the same reason NR17 is.
    # Two edits, so the mutant is SYNTACTICALLY VALID and the module still imports: a bare reference to an
    # unimported name would throw at import time and fail the whole suite, which is a kill for the wrong
    # reason. `wired` is never called, so nothing executes - AC12 reads source, not behaviour.
    ("NR24 a production module starts constructing the boundary, so the wiring has arrived",
     CLIENT, (CLIENT_IMPORT, CLIENT_API),
     (CLIENT_IMPORT + "\nimport { createRenewalFailureHandler } from '../auth/cleanReauth';",
      "const wired = (nav: (to: string) => void) => createRenewalFailureHandler(nav);\n"
      "void wired;\n" + CLIENT_API)),

    # --- NR17: the environmental assumption behind "no half-authenticated screen" (AC11) ------------
    # Not a defect in this task's code at all. It is the day the repository stops making T33's claim
    # true for free, and the case it kills says so and names T41.
    ("NR17 an authenticated route gains a screen, so nothing stops a half-authenticated render",
     ROUTES, AUTH_ROUTES,
     "      ...AUTHENTICATED_ROUTE_PATHS.map((path) => ({ path, element: <SignInScreen /> })),"),
]


def run(files, name_filter=None):
    command = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    if name_filter:
        command += ["-t", name_filter]
    proc = subprocess.run(command, cwd=FRONTEND, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    failed = total = None
    files_failed = set()
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
        match = re.search(r"\bFAIL\b\s+(\S+\.(?:ts|tsx))", stripped)
        if match:
            files_failed.add(match.group(1))
    return proc.returncode, failed, total, files_failed


def edits_of(old, new):
    """One mutant, one or more replacements.

    A single replacement cannot express a REORDER - it can only delete or rewrite in place - and NR03
    needs one: moving the clear below the navigation is a different regression from removing it, and the
    first version of that row deleted the line and called itself a reorder, which made it a duplicate of
    NR01 wearing another name. A mutant that measures something other than its own description is worse
    than a missing mutant, because the score counts it.
    """
    return list(zip(old, new)) if isinstance(old, tuple) else [(old, new)]


def apply(path, old, new):
    target = REPO / path
    text = target.read_text()
    for find, replace in edits_of(old, new):
        if text.count(find) != 1:
            return False
        text = text.replace(find, replace, 1)
    target.write_text(text)
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "frontend/src/"], cwd=REPO, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "frontend/src/"], cwd=REPO, check=False)


def preflight():
    """Every anchor in its file, exactly once, before a single verdict is printed."""
    stale = []
    for label, path, old, new in MUTANTS:
        text = (REPO / path).read_text()
        for find, _replace in edits_of(old, new):
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {path} matches an anchor {count} times, expected 1")
    if stale:
        print("REFUSING TO RUN: anchors no longer match the source.\n")
        print("\n".join(stale))
        print(
            "\nThe source moved and these mutants did not follow. Each would print BROKEN, which is\n"
            "NOT a survivor, so the run would still end `survivors: none` and the score would be\n"
            "overstated by exactly this many. Re-copy the anchors and run again."
        )
        return False
    return True


def score(label, base_failed):
    code, failed, total = run(FILES)[:3]
    if failed is None:
        print(f"  BROKEN    -              {label}")
        return None, True
    if failed > base_failed:
        own_failed = run([OWN_TEST])[1]
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        return None, False
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return f"{label} [SURVIVED]", False


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    own_code, own_failed, own_total, _ = run([OWN_TEST])
    print(f"baseline T33 cases:   {own_failed} failed of {own_total}, exit {own_code}\n")

    # The NAMES are the real guard, so they are checked first and the count only warns.
    if not files_failed <= KNOWN_ENVIRONMENTAL_FAILURES:
        unexpected = sorted(files_failed - KNOWN_ENVIRONMENTAL_FAILURES)
        print(
            "REFUSING TO RUN: a file outside the known environmental set is failing.\n"
            f"  unexpectedly failing: {unexpected}\n"
            "A floor made of a different test measures every verdict below against the wrong\n"
            "thing. Fix the tree, or update KNOWN_ENVIRONMENTAL_FAILURES deliberately."
        )
        return 1
    if failed != EXPECTED_BASELINE_FAILURES:
        print(
            f"NOTE: the baseline is {failed} failures, not the recorded {EXPECTED_BASELINE_FAILURES}, but\n"
            "every one of them is in the known environmental set, so the measured floor is sound.\n"
        )
    if own_failed != 0:
        print(f"REFUSING TO RUN: T33's own cases fail {own_failed} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    broken = []
    regressions = canaries = 0
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if label.split()[0] in CANARIES:
            canaries += 1
        else:
            regressions += 1
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        survived, was_broken = score(label, base_failed)
        if survived:
            survivors.append(survived)
        if was_broken:
            broken.append(label)
        revert()

    kept = [label for label, *_ in MUTANTS if not wanted or wanted in label]
    scored = [label for label in kept if label.split()[0] not in CANARIES]
    print(f"\nregressions: {len(scored) - len([s for s in survivors if s.split()[0] not in CANARIES])}"
          f"/{regressions} killed")
    if canaries:
        print(f"canaries:    {canaries} - a change elsewhere that must still be caught today, scored apart")
    print(f"survivors:   {survivors or 'none'}")
    print(f"broken:      {broken or 'none'}")
    return 1 if (survivors or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
