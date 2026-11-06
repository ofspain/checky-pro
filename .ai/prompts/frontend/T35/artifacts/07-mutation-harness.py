#!/usr/bin/env python3
"""T35 mutation harness - sign-out clears memory.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. Uncommitted test edits are destroyed
silently - that cost 19 cases in T14 and 5 in T15.

THE MACHINERY IS T33'S, UNCHANGED, and T33's was T32's: the baseline is pinned by FAILING FILE
NAME with the count only warning (T31 A7), every anchor must match exactly once before a single
verdict prints (T30's real defect), a BROKEN mutant FAILS the run like a survivor (T31 phase 9),
and a mutant may carry SEVERAL edits so that a reorder is not a deletion wearing another name
(T33 NR03). Copied rather than extracted because the four copies live in four closed tasks'
directories; the extraction is OQ-T32-4's.

WHAT A MUTANT HERE CANNOT DO: it can only record a fix that turns a survivor into a kill. Three
of T35's criteria are PRECISION claims - that the scans fire on a planted positive - and a
mutant cannot express those at all. They are measured by the controls in the suite, which is the
same division T32 phase 11 settled: the harness measures an instrument's strength, and only a
control measures its precision.

THE SOLE CANARY IS SO15, and it is unusual enough to say plainly. AC9 pins a HAZARD - a
signed-out tab adopting a sibling's renewal - by asserting the behaviour AS IT IS. So the mutant
that kills it is one that FIXES the hazard, and the kill is the proof that the day task 39
closes OQ-T35-3 the author is told so by a failing case rather than discovering it later. Any
change to the adoption path also breaks task 6's suite, so the kill is coarse by necessity; the
failure count is recorded and the case is verified narrowly by name.

    python3 .ai/prompts/frontend/T35/artifacts/07-mutation-harness.py          # all
    python3 .ai/prompts/frontend/T35/artifacts/07-mutation-harness.py SO04     # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

SIGNOUT = "frontend/src/auth/signOut.ts"
RETURN_TARGET = "frontend/src/auth/returnTarget.ts"
TOKEN = "frontend/src/auth/accessToken.ts"
CROSS_TAB = "frontend/src/api/crossTabRenewal.ts"
SHELL = "frontend/src/app/AppShell.tsx"

OWN_TEST = "src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx"

# A CANARY is not a regression in this task's code - see the note above. Scored apart, because a mutation
# score measures the CURRENT code and a row everyone expects to change state teaches a reader to discount
# the number (Kimi T33 phase 8 I2).
CANARIES = {"SO15"}
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
CLEAR_TOKEN = "  clearAccessToken();"
TAKE_NOTICE = "  takeSignInNotice();"
CONSUME_STATE = "  consumeAuthorizeState();"
CLEAR_TARGET = "  clearReturnTarget();"
SIGNOUT_SIG = "export function signOut(): void {"
TARGET_IMPORT = "import { clearReturnTarget } from './returnTarget';"
HELD_NULL = "  held = null;"
TARGET_GUARD = """  try {
    globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);
  } catch {"""
ADOPT = "        local.adopt(outcome.token);"
SHELL_BODY = "  return <div data-testid=\"app-shell\">{children}</div>;"
SHELL_SIG = "export function AppShell({ children }: { children?: ReactNode }) {"
PWA_FLAG = "let updateIsWaiting = false;"
REAUTH_CLEAR = "    clearAccessToken();"

MUTANTS = [
    # --- AC1, AC4: the token, which is the only one of the four whose position matters ------------------
    ("SO01 the held token is never dropped", SIGNOUT, CLEAR_TOKEN, "  void 0;"),

    # Not "forgets" but "clears a moment later" - the shape a refactor produces, and the reason AC4
    # asserts on the very next statement. Task 36 follows this call with a full-page navigation, which
    # waits for no timer.
    ("SO02 the token is cleared asynchronously, leaving a window where it is still held",
     SIGNOUT, CLEAR_TOKEN, "  globalThis.setTimeout(clearAccessToken, 0);"),

    # A REORDER, not a deletion: two edits, because one replacement cannot express moving a line and the
    # first version of T33's equivalent was a duplicate of its own NR01 wearing another name.
    ("SO03 the token is cleared LAST, after three steps that can each throw",
     SIGNOUT,
     (CLEAR_TOKEN, CLEAR_TARGET),
     ("  void 0;", "  clearReturnTarget();\n  clearAccessToken();")),

    # --- AC3: the residue a deliberate sign-out must not leave -------------------------------------------
    ("SO04 a stale notice survives, so a deliberate leaver is told their session ended",
     SIGNOUT, TAKE_NOTICE, "  void 0;"),
    ("SO05 the three authorize items survive, so a callback can still complete an exchange",
     SIGNOUT, CONSUME_STATE, "  void 0;"),
    ("SO06 the return target survives, so the NEXT sign-in lands where this visitor was",
     SIGNOUT, CLEAR_TARGET, "  void 0;"),

    # --- AC6: the storage traffic, exactly four removals and no read ------------------------------------
    # The precise mistake Kimi phase 3 F3 predicted and the reason clearReturnTarget exists. It still
    # removes the key, so every behavioural case passes; only the operation count catches it.
    ("SO07 the consuming reader is used instead, adding a read and a home-path computation",
     SIGNOUT,
     (TARGET_IMPORT, CLEAR_TARGET),
     ("import { takeReturnTarget } from './returnTarget';", "  takeReturnTarget();")),

    # Leaves the store looking right and wipes the other half of L16's carve-out on the way - R29's
    # mid-task form input, which is task 26's and not sign-out's to destroy.
    ("SO08 the store is cleared wholesale instead of four keys being removed",
     SIGNOUT, CLEAR_TARGET, "  globalThis.sessionStorage.clear();"),

    # --- AC5: nothing to branch on ----------------------------------------------------------------------
    ("SO09 the handler takes the reason it signed out back",
     SIGNOUT, SIGNOUT_SIG, "export function signOut(_reason?: unknown): void {"),

    # --- AC4 again, from the signature side -------------------------------------------------------------
    ("SO10 sign-out becomes asynchronous, so nothing is guaranteed done when it returns",
     SIGNOUT, SIGNOUT_SIG, "export async function signOut(): Promise<void> {"),

    # --- AC12: never throws, whatever the store does ----------------------------------------------------
    ("SO11 the new primitive loses its guard, so a denied store throws out of sign-out",
     RETURN_TARGET, TARGET_GUARD,
     "  globalThis.sessionStorage.removeItem(RETURN_TARGET_KEY);\n  if (false) {"),

    # --- AC2: idempotence, which task 39 will depend on -------------------------------------------------
    ("SO12 clearing twice becomes an error, which breaks the cold-tab and repeat cases",
     TOKEN, HELD_NULL,
     "  if (held === null) throw new Error('already cleared');\n  held = null;"),

    # --- AC7, AC8, AC10: the three scans, each shown to catch a planted change in production ------------
    ("SO13 an unaccounted client-side key appears in production source",
     SHELL, SHELL_BODY,
     "  const smuggled = 'themistra.ui.draft';\n  void smuggled;\n" + SHELL_BODY),
    # MODULE scope, not function scope: AC7's scan is narrowed to top-level declarations (Kimi phase 3 F8),
    # so a mutant planting one inside a function would measure a claim the criterion never made.
    ("SO14 a module-scope mutable appears in a module sign-out does not clear",
     SHELL, SHELL_SIG, "let leaked: number | null = 1;\n" + SHELL_SIG),

    # --- the three instruments phase 9 added, each given a mutant of its own ---------------------------
    # Kimi phase 8 I5. The literal scan cannot see an assembled key, so phase 9 forbade the assembly; this is
    # the mutant that proves the new ban is load-bearing rather than decorative.
    ("SO16 a client-side key is assembled at runtime, where the literal scan cannot see it",
     SHELL, SHELL_SIG,
     "const part = 'auth.draft';\nconst smuggled = `themistra.${part}`;\nvoid smuggled;\n" + SHELL_SIG),

    # Kimi phase 8 I8. A permitted caller that stops calling is a stale allowlist entry, and staleness in an
    # allowlist is the kind of rot that passes forever.
    ("SO17 a permitted caller stops calling its primitive, leaving the allowlist stale",
     "frontend/src/auth/cleanReauth.ts", REAUTH_CLEAR, "    void 0;"),

    # Kimi phase 8 I9, inverted. This is the "resilient" sign-out the finding imagined: each step guarded, so
    # a failure is swallowed and the later steps run anyway. It is a design CHANGE, not a bug fix - it hides
    # the one condition that should surface - and phase 9's case is what records the decision.
    # The anchor is STEP TWO, not step one. Written against step one first, where it would have proved
    # nothing: phase 9's case forces the throw in step two, so guarding step one leaves the throw propagating
    # and the case still passing. A mutant must measure its own description (T33 NR03).
    ("SO18 every step is individually guarded, so a failure is hidden instead of surfacing",
     SIGNOUT, TAKE_NOTICE,
     "  try { takeSignInNotice(); } catch { /* hidden */ }"),

    # Phase 10's audit: excusing a FILE excuses every declaration in it, so AC7 pins the count. This is the
    # third mutable arriving in an already-excused module - the shape that was being waved through silently.
    ("SO19 a third module-scope mutable joins an already-excused module",
     "frontend/src/pwa/registerServiceWorker.ts", PWA_FLAG,
     "let thirdThing: string | null = null;\n" + PWA_FLAG),

    # --- AC9's canary: the mutant that kills it is the one that FIXES the hazard -------------------------
    ("SO15 the cross-tab adoption is removed, so the pinned hazard no longer reproduces",
     CROSS_TAB, ADOPT, "        void outcome.token;"),
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
