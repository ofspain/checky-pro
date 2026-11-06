#!/usr/bin/env python3
"""T36 mutation harness - sign-out ends the SAS session.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. Uncommitted test edits are destroyed
silently - that cost 19 cases in T14 and 5 in T15.

THE MACHINERY IS T35'S, UNCHANGED, and T35's was T33's and T32's: the baseline is pinned by
FAILING FILE NAME with the count only warning (T31 A7), every anchor must match exactly once
before a single verdict prints (T30's real defect), a BROKEN mutant FAILS the run like a
survivor (T31 phase 9), and a mutant may carry SEVERAL edits so that a reorder is not a deletion
wearing another name (T33 NR03). Copied rather than extracted because the five copies live in
five closed tasks' directories; the extraction is OQ-T32-4's, now nine reviews across four tasks.

WHAT A MUTANT HERE CANNOT DO: it can only record a fix that turns a survivor into a kill. Several
of T36's criteria are PRECISION claims - that a scan fires on a planted positive, that a watcher
installed too late sees nothing - and a mutant cannot express those at all. They are measured by
the controls in the suite. That division was settled in T32 phase 11: the harness measures an
instrument's strength, and only a control measures its precision.

NO CANARIES. T35 had one, SO15, because its AC9 pins a hazard by asserting the behaviour as it
is, so the mutant that kills it is one that FIXES the hazard. T36 adds no such case: AC13 pins
the non-idempotence of endSession() as it is, but a mutant that made it idempotent would be a
LATCH, which AC15 forbids - so the thing a canary would record is already forbidden by a
criterion, and a row that asserts a forbidden fix would teach a reader to discount the score.
AC12 and AC13 are ordinary regressions here.

    python3 .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py          # all
    python3 .ai/prompts/frontend/T36/artifacts/07-mutation-harness.py ES04     # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

END_SESSION = "frontend/src/auth/endSession.ts"
ID_TOKEN = "frontend/src/auth/idToken.ts"
SIGNOUT = "frontend/src/auth/signOut.ts"
CALLBACK = "frontend/src/auth/CallbackScreen.tsx"
DENYLIST = "frontend/src/pwa/navigationDenylist.ts"
SHELL = "frontend/src/app/AppShell.tsx"

OWN_TEST = "src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx"

CANARIES = set()
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
FIRST_IMPORT = "import { APP_BASENAME } from '../app/routes';"
HANDOVER = "  globalThis.location.assign(endSessionUrl(hint));"
TAKE_HINT = "  const hint = takeIdToken();"
CALL_SIGNOUT = "  signOut();"
END_SESSION_SIG = "export function endSession(): void {"
HINT_GUARD = "  if (hint !== null) parameters.set('id_token_hint', hint);"
POST_LOGOUT_SET = "  parameters.set('post_logout_redirect_uri', postLogoutRedirectUri());"
DESLASH = "  const base = APP_BASENAME.endsWith('/') ? APP_BASENAME.slice(0, -1) : APP_BASENAME;"
DERIVED_URI = "  return `${globalThis.location.origin}${base}/${SIGNED_OUT_PATH}`;"
PATH_CONST = "export const END_SESSION_PATH = '/connect/logout';"
SIGNED_OUT_CONST = "const SIGNED_OUT_PATH = 'signed-out';"
URL_RETURN = "  return `${globalThis.location.origin}${END_SESSION_PATH}?${parameters.toString()}`;"
TAKE_ID_BODY = """export function takeIdToken(): string | null {
  const token = held;
  held = null;
  return token;
}"""
RETAIN_ID = "export function retainIdToken(token: string): void {"
SIGNOUT_TAKE_ID = "  takeIdToken();"
CALLBACK_RETAIN = "      retainIdToken(response.idToken);"
CALLBACK_NONCE = "      if (!idTokenNonceMatchesStoredValue(response.idToken).ok) {"
DENYLIST_ENTRY = "  '/connect/logout',"
SHELL_BODY = "  return <div data-testid=\"app-shell\">{children}</div>;"
SHELL_FIRST_IMPORT = "import type { ReactNode } from 'react';"

MUTANTS = [
    # --- AC1: the clearing completes before the browser leaves -------------------------------------------
    # THE ORDER MUTANT, and the reason AC1 has a throwing-navigation case as well as a state case. Two
    # edits, because moving a line is not deleting one - T33's NR03 was a duplicate of its own NR01 until
    # that was understood.
    ("ES01 the browser is handed over BEFORE the clearing, so a tab navigates away holding a token",
     END_SESSION,
     (CALL_SIGNOUT, HANDOVER),
     ("  void 0;", "  globalThis.location.assign(endSessionUrl(hint));\n  signOut();")),

    ("ES02 the clearing half is skipped entirely, so the SAS session ends and the tab keeps its token",
     END_SESSION, CALL_SIGNOUT, "  void 0;"),

    # Not "forgets" but "clears a moment later" - the shape a refactor produces. A full-page navigation
    # waits for no timer, so the token is still held when the browser leaves.
    ("ES03 the clearing is deferred to a timer the navigation does not wait for",
     END_SESSION, CALL_SIGNOUT, "  globalThis.setTimeout(signOut, 0);"),

    # --- AC2: exactly one hand-over, across four mechanisms ----------------------------------------------
    ("ES04 no hand-over at all: the tab clears locally and the SAS cookie survives",
     END_SESSION, HANDOVER, "  void 0;"),

    # The mutant the four-mechanism counter exists for, and the one Kimi's F5 would have narrowed away:
    # an ADDITION beside the existing assign, which an assign-only spy reports as exactly one call.
    ("ES05 a SECOND hand-over is fired through window.open beside the assign",
     END_SESSION, HANDOVER,
     "  globalThis.location.assign(endSessionUrl(hint));\n  globalThis.open(endSessionUrl(hint));"),

    ("ES06 the hand-over moves to the href setter, which an assign-only watcher cannot see",
     END_SESSION, HANDOVER, "  globalThis.location.href = endSessionUrl(hint);"),

    # --- AC3: the URL ------------------------------------------------------------------------------------
    ("ES07 the end-session path is misspelled, so the navigation reaches no endpoint",
     END_SESSION, PATH_CONST, "export const END_SESSION_PATH = '/connect/logoff';"),

    ("ES08 an absolute host is spelled, baking a deployment into the bundle (L9, R58)",
     END_SESSION, URL_RETURN,
     "  return `https://auth.checky.example${END_SESSION_PATH}?${parameters.toString()}`;"),

    # --- AC4: post_logout_redirect_uri, derived ----------------------------------------------------------
    # THE COINCIDENCE MUTANT. While APP_BASENAME is '/app' this produces a byte-identical URL, so every
    # assertion against the real route table passes. Only the moved-basename case can see it - task 22
    # recorded that hole and task 36 inherits it exactly.
    ("ES09 the basename is hardcoded instead of read from the route table",
     END_SESSION, DESLASH, "  const base = '/app';"),

    ("ES10 the de-slash is dropped, so a future '/app/' basename produces '/app//signed-out'",
     END_SESSION, DESLASH, "  const base = APP_BASENAME;"),

    ("ES11 the post-logout target names a route the table does not declare",
     END_SESSION, SIGNED_OUT_CONST, "const SIGNED_OUT_PATH = 'logged-out';"),

    ("ES12 the post-logout parameter is omitted, so SAS has no allowlisted target to return to",
     END_SESSION, POST_LOGOUT_SET, "  void 0;"),

    # --- AC5, AC12: the hint -----------------------------------------------------------------------------
    ("ES13 the hint is omitted even when a token is held, so the wrong session may end",
     END_SESSION, HINT_GUARD, "  void 0;"),

    # THE `?? ''` MUTANT. URLSearchParams serialises the empty string without complaint, so the parameter
    # is PRESENT and empty - asking SAS to end the session identified by ''. Every behavioural case that
    # only checks `get('id_token_hint')` against a held token still passes.
    ("ES14 an absent token becomes an EMPTY hint rather than no hint",
     END_SESSION, HINT_GUARD, "  parameters.set('id_token_hint', hint ?? '');"),

    ("ES15 a tab with no ID token skips the hand-over, leaving the SAS cookie alive",
     END_SESSION,
     (TAKE_HINT, HANDOVER),
     ("  const hint = takeIdToken();\n  if (hint === null) { signOut(); return; }",
      "  globalThis.location.assign(endSessionUrl(hint));")),

    # The access token in the URL - a bearer credential in a browser history entry, a referrer and a
    # server log. The single worst thing this module could do.
    # The first version of this mutant appended an import AFTER a statement, which is invalid TS: the T36
    # test file failed to collect and the run reported `2 failed of 1579` - 75 cases short, killed by
    # INHERITED failures only. The harness's own label caught it. A mutant that cannot compile measures
    # nothing about the suite, so the import is placed where an import goes.
    ("ES16 the access token is sent as the hint instead of the ID token",
     END_SESSION,
     (FIRST_IMPORT, TAKE_HINT),
     ("import { accessToken } from './accessToken';\nimport { APP_BASENAME } from '../app/routes';",
      "  const hint = accessToken.current();")),

    # --- AC6: exactly two parameters ---------------------------------------------------------------------
    ("ES17 a third parameter is added, widening what the hand-over discloses",
     END_SESSION, URL_RETURN,
     "  parameters.set('client_id', 'checky-spa');\n"
     "  return `${globalThis.location.origin}${END_SESSION_PATH}?${parameters.toString()}`;"),

    # --- AC9, AC15: idToken.ts ---------------------------------------------------------------------------
    # The hole AC9 was written to close. `take` that does not clear leaves the token held after sign-out,
    # and endSession's own URL is still correct - so only a case that calls signOut() alone, or asks for
    # the token twice, can see it.
    ("ES18 takeIdToken returns the token without clearing it",
     ID_TOKEN, TAKE_ID_BODY,
     "export function takeIdToken(): string | null {\n  return held;\n}"),

    ("ES19 signOut no longer clears the ID token, so only the endSession path drops it",
     SIGNOUT, SIGNOUT_TAKE_ID, "  void 0;"),

    # A PLAIN READER, which is the structural hazard AC15 forbids: it reopens the read-before-clear window
    # by making it possible to obtain the token without consuming it.
    ("ES20 idToken.ts grows a plain reader beside the consuming one",
     ID_TOKEN, RETAIN_ID,
     "export function currentIdToken(): string | null {\n  return held;\n}\n\nexport function retainIdToken(token: string): void {"),

    # A SECOND module-scope mutable in a file task 35's AC7 lists as CLEARED. This is the exact gap phase 2
    # found in T35's AC7 - counts pinned only for EXCUSED files - and the mutant that proves AC9 closed it.
    ("ES21 a second module-scope mutable is added to a cleared file, cleared by nothing",
     ID_TOKEN, RETAIN_ID,
     "let lastSeen: string | null = null;\n\nexport function retainIdToken(token: string): void {"),

    # --- AC14: the callback ------------------------------------------------------------------------------
    # Hoisted above the nonce check: the token is retained for a login this tab did not start, and it looks
    # perfectly valid afterwards - only its nonce was wrong. Behaviourally invisible, as task 19 recorded.
    ("ES22 the ID token is retained BEFORE the nonce check",
     CALLBACK,
     (CALLBACK_RETAIN, CALLBACK_NONCE),
     ("      void 0;", "      retainIdToken(response.idToken);\n      if (!idTokenNonceMatchesStoredValue(response.idToken).ok) {")),

    ("ES23 the callback never retains the ID token, so every sign-out omits the hint",
     CALLBACK, CALLBACK_RETAIN, "      void 0;"),

    # --- AC10, AC16: the two spellings -------------------------------------------------------------------
    ("ES24 a third production module names the end-session path",
     SHELL, SHELL_BODY,
     "  const out = '/connect/logout';\n  void out;\n" + SHELL_BODY),

    # R57. Without the denylist entry the service worker answers the navigation from its fallback and hands
    # index.html to a browser on its way to the end-session endpoint, and nobody can sign out.
    ("ES25 the end-session path is dropped from the service-worker navigation denylist",
     DENYLIST, DENYLIST_ENTRY, "  "),

    # --- AC7: a full-page navigation ---------------------------------------------------------------------
    ("ES26 the hand-over becomes a router navigation, which cannot leave the SPA",
     END_SESSION, HANDOVER, "  globalThis.history.pushState({}, '', endSessionUrl(hint));"),

    # --- phase 9, Kimi I3: the bypass the LITERAL scan cannot see -----------------------------------------
    # A third module imports the exported constant and navigates to it. No literal `/connect/logout` appears
    # in that module, so ES24's scan passes it. This is the mutant the import-route scan was added for, and
    # before that scan existed it would have SURVIVED.
    ("ES27 a third module imports END_SESSION_PATH and reaches the endpoint through the variable",
     SHELL,
     (SHELL_FIRST_IMPORT, SHELL_BODY),
     ("import { END_SESSION_PATH } from '../auth/endSession';\n" + SHELL_FIRST_IMPORT,
      "  globalThis.location.assign(END_SESSION_PATH);\n" + SHELL_BODY)),

    # --- phase 9, Kimi I7: a surface neither task 35 nor task 36 swept ------------------------------------
    # `window.name` survives a cross-origin navigation, so a token written there outlives the very hand-over
    # this module performs. The URL is still correct, so every behavioural case passes.
    ("ES28 the ID token is written to window.name, which survives the navigation",
     END_SESSION, HANDOVER,
     "  window.name = hint ?? '';\n  globalThis.location.assign(endSessionUrl(hint));"),
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
    print(f"baseline T35 cases:   {own_failed} failed of {own_total}, exit {own_code}\n")

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
        print(f"REFUSING TO RUN: T35's own cases fail {own_failed} on a clean tree.")
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
