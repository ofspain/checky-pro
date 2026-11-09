#!/usr/bin/env python3
"""T39 mutation harness - sign-out propagates to every tab.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout -- frontend/src/`. COMMIT YOUR TESTS FIRST. That wipes the whole
tree, not just the mutated file - it cost 19 cases in T14, 5 in T15, and a full phase-9 edit in
T36 because I ran it on an uncommitted test file.

THE MACHINERY IS T37'S - seventh copy, OQ-T32-4 - with one addition this task needed:

  NARROW VERIFICATION IS BUILT IN, not a separate script. Every killed mutant prints the NAMES of
  the cases that failed, from the three files this task owns or grew. T37 learned four times over
  that a kill count and the named failures can disagree, and it learned it by writing a second
  script afterwards. Here the names come out of the same run.

  AND THE REASON IT HAD TO. This suite contains AC9c, a case that asserts a DEFECT as it is
  (OQ-T39-6, a stale sibling success overwriting a fresh token). A mutant that happened to close
  that hazard would turn AC9c red, and a harness counting failures would score that as a kill -
  recording a FIX as a regression caught. So every row's named failures are checked against
  AC9c, and a row whose kill rests on it alone is reported as MISATTRIBUTED, not as killed.

    python3 -I .ai/prompts/frontend/T39/artifacts/07-mutation-harness.py        # all
    python3 -I .ai/prompts/frontend/T39/artifacts/07-mutation-harness.py XT05   # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

SIGNOUT_PROP = "frontend/src/api/crossTabSignOut.ts"
RENEWAL = "frontend/src/api/crossTabRenewal.ts"
SIGN_OUT = "frontend/src/auth/signOut.ts"
ACCESS_TOKEN = "frontend/src/auth/accessToken.ts"
END_SESSION = "frontend/src/auth/endSession.ts"

# The files that measure this task: the one it added, and the three inherited suites it grew. Task 21's
# was added in phase 10 - without it XT27's kill printed `INHERITED-ONLY` with no named case, because the
# case that kills it is the one phase 10 added to the suite that owns renewal's counting contract.
OWN_TESTS = [
    "src/api/signOutPropagatesToAllTabs.test.ts",
    "src/auth/signOutClearsMemoryAndEndsSasSession.test.tsx",
    "src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx",
    "src/api/siblingTabsShareOneRenewal.test.ts",
]

# AC9c. A failure here is a hazard pin going green-to-red, which is a FIX, not a caught regression.
HAZARD_PIN = "KNOWN HAZARD: a stale success from before the sign-out is adopted after a new sign-in"

CANARIES = set()

# SCORED APART, and not as a kill or a loss. XT18 removes `close()`'s idempotency guard; a double
# `removeEventListener` and a double `BroadcastChannel.close()` are both no-ops by specification, so the
# guard's removal has NO observable effect and no test can have one. Phase 9 declined to give `close()` an
# observable side effect for a test's convenience, and phase 10 reworded the two assertions that claimed
# idempotency to the claim they actually hold. A row that cannot be killed without changing production is a
# limitation of the code's observability, not a gap in the suite, and counting it either way would lie.
UNFALSIFIABLE = {"XT18"}

FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
LISTEN_ADD = "  channel.addEventListener('message', onMessage);"
ON_MESSAGE = "    if (isSignOut(event.data)) onSignOut();"
PARSE = ("  return typeof data === 'object' && data !== null\n"
         "    && (data as { type?: unknown }).type === SIGN_OUT_TYPE;")
POST_INJECTED = "    channel.postMessage({ type: SIGN_OUT_TYPE });"
POST_OWN = "    own.postMessage({ type: SIGN_OUT_TYPE });"
BC_GUARD_POST = "  if (typeof BroadcastChannel !== 'function') return;"
BC_GUARD_LISTEN = "  if (!injected && typeof BroadcastChannel !== 'function') return { close: () => undefined };"
MAKE_OWN = "  const own = new BroadcastChannel(SIGN_OUT_CHANNEL);"
MAKE_CHANNEL = "  const channel = injected ?? new BroadcastChannel(SIGN_OUT_CHANNEL);"
REMOVE_LISTENER = "      channel.removeEventListener('message', onMessage);"
CLOSED_GUARD = "      if (closed) return;"
CLOSE_OWN = "      if (!injected) channel.close();"
LATCH_SET = "  signedOut = true;"
LATCH_DECL = "let signedOut = false;"
TYPE_DECL = "export const SIGN_OUT_TYPE = 'session:signed-out';"
SWALLOW = """  } catch {
    // Dropped on purpose, for the reason in this function's header: a sign-out that cannot tell the other
    // tabs must still finish its own. There is nowhere to report it to - the next statement in the caller
    // navigates away - and `console` is a forbidden surface here (L3).
  } finally {"""

SO_IMPORT = "import { markSignedOut } from '../api/crossTabSignOut';"
LATCH_CALL = "  markSignedOut();"
CLEAR_TOKEN = "  clearAccessToken();"
RELEASE = "  markSignedIn();\n  held = token;"

GUARD = "      if (hasSignedOut()) return;"
OUTCOME_COUNT = "    outcomeCount += 1;"

CHANNEL_DECL = "export const SIGN_OUT_CHANNEL = 'themistra.session.signout';"
CLEAR_HELD = "  held = null;"

ES_SIGN_OUT = "  signOut();"
ES_BROADCAST = "  broadcastSignOut();"
ES_ASSIGN = "  globalThis.location.assign(endSessionUrl(hint));"

MUTANTS = [
    # --- AC1: a sign-out in one tab reaches every sibling -----------------------------------------------
    # The whole requirement, removed. Measured during phase 7's self-review: six cases die, and three of
    # them are NEGATIVE cases that pass with a deaf listener - which is finding F3, not a kill.
    ("XT01 the listener is deaf: nothing is ever delivered to a sibling tab",
     SIGNOUT_PROP, LISTEN_ADD, "  void onMessage;"),

    # The production path specifically. Every AC1/AC2/AC11/AC12 case injects a channel, so a broadcast
    # that silently stops working on the REAL channel is invisible to all of them - only AC9d, in task
    # 36's suite, posts through `endSession()` and watches the real channel name.
    ("XT02 the no-argument broadcast posts nothing, so only the injected path still works",
     SIGNOUT_PROP, POST_OWN, "    void own;"),

    # F5's hazard. BEFORE phase 9's fix this killed 22 cases, because the throw propagated into
    # `endSession()` and took the whole hand-over down. With the swallow in place the post fails silently
    # instead, so what this row now measures is that the swallow did not swallow the FEATURE: no message
    # reaches the channel and AC9d says so. A narrow kill where there was a cascade.
    ("XT03 the channel is closed before the post, so the broadcast silently delivers nothing",
     SIGNOUT_PROP, (MAKE_OWN, POST_OWN),
     ("  const own = new BroadcastChannel(SIGN_OUT_CHANNEL);\n  own.close();",
      "    own.postMessage({ type: SIGN_OUT_TYPE });")),

    # --- AC2: the receiving tab does not echo -----------------------------------------------------------
    # The design's one structural guarantee, inverted: if `signOut()` broadcasts, then a tab that signs out
    # because a sibling told it to tells the sibling back, and two tabs ping-pong without bound.
    # MEASURED WRONG ONCE, and kept as the correction. The first version inserted the call and not the
    # IMPORT, so it was a `ReferenceError: broadcastSignOut is not defined` at `signOut.ts:74` - 46 failures,
    # 45 of them a crash cascade. The intended case did fire, because a source scan matches an identifier
    # whether or not it resolves, so the row would have scored as killed while measuring a module that
    # cannot run. T37's SL25 was wrong in exactly this way. A mutant must measure its own description.
    ("XT04 signOut() broadcasts, so every receiving tab echoes and two tabs ping-pong",
     SIGN_OUT, (SO_IMPORT, LATCH_CALL),
     ("import { broadcastSignOut, markSignedOut } from '../api/crossTabSignOut';",
      "  markSignedOut();\n  broadcastSignOut();")),

    ("XT05 the listener re-posts what it received, on the same channel",
     SIGNOUT_PROP, ON_MESSAGE,
     "    if (isSignOut(event.data)) {\n      onSignOut();\n      channel.postMessage(event.data);\n    }"),

    # --- AC3: the SAS session is ended once, by the acting tab (R19) ------------------------------------
    # N tabs navigating to `/connect/logout` for one intent. The receiving tab must clear LOCALLY; the
    # hand-over belongs to the tab the visitor acted in.
    ("XT06 every receiving tab navigates to the SAS end-session endpoint itself",
     SIGNOUT_PROP, ON_MESSAGE,
     "    if (isSignOut(event.data)) {\n      onSignOut();\n"
     "      globalThis.location.assign('/connect/logout');\n    }"),

    # --- AC4: the message carries nothing ---------------------------------------------------------------
    ("XT07 the message grows a timestamp, which is a correlation value on a surface (L3)",
     SIGNOUT_PROP, (POST_INJECTED, POST_OWN),
     ("    channel.postMessage({ type: SIGN_OUT_TYPE, at: Date.now() });",
      "    own.postMessage({ type: SIGN_OUT_TYPE, at: Date.now() });")),

    ("XT08 the message grows a per-tab identifier to de-duplicate with",
     SIGNOUT_PROP, (POST_INJECTED, POST_OWN),
     ("    channel.postMessage({ type: SIGN_OUT_TYPE, tab: crypto.randomUUID() });",
      "    own.postMessage({ type: SIGN_OUT_TYPE, tab: crypto.randomUUID() });")),

    # --- AC5: propagation does not depend on Web Locks --------------------------------------------------
    # The reason there are two channels. `crossTabRenewal.ts:78-86` listens only when Web Locks exist;
    # copying that shape here degrades a sibling tab's sign-out into a sibling tab left signed in.
    ("XT09 propagation copies renewal's shape and needs Web Locks to listen at all",
     SIGNOUT_PROP, MAKE_CHANNEL,
     "  if (!navigator.locks) return { close: () => undefined };\n"
     "  const channel = injected ?? new BroadcastChannel(SIGN_OUT_CHANNEL);"),

    # --- AC6: no BroadcastChannel at all is a no-op, not a throw ----------------------------------------
    ("XT10 broadcasting throws where BroadcastChannel does not exist",
     SIGNOUT_PROP, BC_GUARD_POST, "  void 0;"),

    ("XT11 listening throws where BroadcastChannel does not exist",
     SIGNOUT_PROP, BC_GUARD_LISTEN, "  void injected;"),

    # --- AC10 / L16: nothing is stored on any path ------------------------------------------------------
    # The obvious wrong fix: make the latch survive a reload by storing it. It would also make a sign-out
    # permanent for that origin until something cleared the key.
    ("XT12 the latch is persisted to sessionStorage, so sign-out leaves a stored trace",
     SIGNOUT_PROP, LATCH_SET,
     "  signedOut = true;\n  globalThis.sessionStorage.setItem('themistra.session.signedout', '1');"),

    # --- AC11: only a sign-out is acted on --------------------------------------------------------------
    # Measured in phase 7: AC11's first case is the ONLY case in the whole suite that kills this.
    ("XT13 anything arriving on the channel signs the tab out",
     SIGNOUT_PROP, PARSE, "  void data;\n  return true;"),

    # The strict half of Kimi phase 3 F8, which phase 4 rejected. If this is killed, the rejection is
    # pinned by a measurement rather than by an argument: an old tab must still act on a future
    # `{ type, reason }` sign-out, because an ignored sign-out is a tab that stays signed in.
    ("XT14 the parser refuses a sign-out carrying an unknown extra property (F8's strict half)",
     SIGNOUT_PROP, PARSE,
     "  return typeof data === 'object' && data !== null\n"
     "    && Object.keys(data as object).length === 1\n"
     "    && (data as { type?: unknown }).type === SIGN_OUT_TYPE;"),

    ("XT15 the type is matched case-insensitively, so a near-miss message signs the tab out",
     SIGNOUT_PROP, PARSE,
     "  return typeof data === 'object' && data !== null\n"
     "    && String((data as { type?: unknown }).type).toLowerCase() === SIGN_OUT_TYPE;"),

    # --- AC12: close() stops the listening --------------------------------------------------------------
    ("XT16 close() leaves the message handler attached",
     SIGNOUT_PROP, REMOVE_LISTENER, "      void onMessage;"),

    ("XT17 close() closes a channel it did not create, breaking its owner's next post",
     SIGNOUT_PROP, CLOSE_OWN, "      channel.close();"),

    # Idempotency, which AC12 and AC6 both claim. Kept whatever the verdict: a double
    # `removeEventListener` and a double `close()` are both harmless by specification, so if nothing
    # fails this row records that the two "is idempotent" assertions are unfalsifiable rather than wrong.
    ("XT18 close() loses its idempotency guard",
     SIGNOUT_PROP, CLOSED_GUARD, "      void closed;"),

    # --- AC13: the layering that makes the callback necessary -------------------------------------------
    # `api/` importing from `auth/` inverts the direction the whole directory depends on, and it is the
    # shortcut a reader would reach for first: why pass the action in when you could import it?
    ("XT19 the propagation module imports signOut from auth/ and calls it directly",
     SIGNOUT_PROP, (ON_MESSAGE, CHANNEL_DECL),
     ("    if (isSignOut(event.data)) { onSignOut(); signOutLocally(); }",
      "import { signOut as signOutLocally } from '../auth/signOut';\n" + CHANNEL_DECL)),

    # --- AC16: the module holds no channel of its own ---------------------------------------------------
    ("XT20 the module holds one channel at module scope, which nothing ever closes",
     SIGNOUT_PROP, LATCH_DECL,
     "const shared = new BroadcastChannel(SIGN_OUT_CHANNEL);\nvoid shared;\nlet signedOut = false;"),

    # --- the latch, in signOut.ts -----------------------------------------------------------------------
    # The task's own line, removed. Task 36's derived SIGN_OUT_SET should also notice: six becomes five.
    ("XT21 signOut() no longer sets the latch, so OQ-T35-3 reopens",
     SIGN_OUT, LATCH_CALL, "  void 0;"),

    # A REORDER, not a deletion - two edits, because a single replacement can only remove. The position is
    # the one thing signOut.ts's comment argues for, and the argument is about a THROW window: with the
    # latch second, a throw from the clear leaves a tab that lost its token and can still be handed one.
    ("XT22 the latch is set AFTER the clear, so a throw mid-sign-out leaves it unset",
     SIGN_OUT, (LATCH_CALL, CLEAR_TOKEN),
     ("  void 0;", "  clearAccessToken();\n  markSignedOut();")),

    # --- the release, in accessToken.ts -----------------------------------------------------------------
    ("XT23 the latch is never released, so a tab that signs in again can never adopt a renewal",
     ACCESS_TOKEN, RELEASE, "  held = token;"),

    # The release moved off the single assignment point - which `accessToken.ts:82` calls "the only place a
    # non-null token is assigned" - and onto the clear. A sign-out would then release the latch it just set,
    # which is OQ-T35-3 restored by a different route: the clear happens inside `signOut()` itself.
    ("XT24 the latch is released by clearAccessToken() instead of by the sign-in",
     ACCESS_TOKEN, (RELEASE, CLEAR_HELD),
     ("  held = token;", "  markSignedIn();\n  held = null;")),

    # --- the guard, in crossTabRenewal.ts ---------------------------------------------------------------
    # The headline regression: the hazard OQ-T35-3 itself, restored.
    ("XT25 the renewal listener adopts without asking whether this tab signed out",
     RENEWAL, GUARD, "      void hasSignedOut;"),

    ("XT26 the guard is inverted: only a signed-out tab adopts",
     RENEWAL, GUARD, "      if (!hasSignedOut()) return;"),

    # The placement the comment argues for at length: the outcome must still be COUNTED and its waiters
    # still woken, because this task changes what a signed-out tab adopts and not how renewal settles.
    # Kept whatever the verdict - if it survives, that paragraph is a claim no case can falsify.
    ("XT27 the guard moves above the count and the waiter wake, changing how renewal settles",
     RENEWAL, (GUARD, OUTCOME_COUNT),
     ("      void hasSignedOut;", "    if (hasSignedOut()) return;\n    outcomeCount += 1;")),

    # --- the broadcast, in endSession.ts ----------------------------------------------------------------
    ("XT28 endSession() signs this tab out and tells no other tab",
     END_SESSION, ES_BROADCAST, "  void 0;"),

    # The EDITS ARE ORDERED remove-then-insert, and that is not a style choice: the first version inserted
    # `broadcastSignOut();` above `signOut();` first, which left TWO occurrences of the line the second edit
    # anchors on, so `apply` refused and the row printed BROKEN. Preflight could not have caught it - it
    # reads the unmutated file - which is why preflight now replays each mutant's edits in memory.
    ("XT29 the broadcast runs BEFORE this tab has signed itself out",
     END_SESSION, (ES_BROADCAST, ES_SIGN_OUT),
     ("  void 0;", "  broadcastSignOut();\n  signOut();")),

    ("XT30 the broadcast runs AFTER the navigation that may already have unloaded the page",
     END_SESSION, (ES_BROADCAST, ES_ASSIGN),
     ("  void 0;", "  globalThis.location.assign(endSessionUrl(hint));\n  broadcastSignOut();")),

    # --- phase 9: the two rows the review's resolution needed -------------------------------------------
    # The state the production path was in before F5/I5 was accepted, reconstructed in full: a post that
    # throws AND no catch to drop it. This is what "fire-and-forget" meant when it was only advertised -
    # the clear has happened, the siblings have not been told, and `location.assign` is never reached, so
    # the visitor stays signed in at the authorization server. Two edits, because the swallow has to go too.
    ("XT31 the broadcast throws and nothing drops it, so a failed notification strands the sign-out",
     SIGNOUT_PROP, (MAKE_OWN, SWALLOW),
     ("  const own = new BroadcastChannel(SIGN_OUT_CHANNEL);\n  own.close();", "  } finally {")),

    # THE MEASUREMENT THAT REJECTS Kimi phase 8 I10, which asked for a scan asserting `SIGN_OUT_TYPE` is a
    # string and not an object. It is already enforced, by task 35's `declarations: 1` - and that is not a
    # deduction, it is how the object form was caught while phase 6 was being written. If this row ever
    # survives, I10 was right and should be reopened.
    ("XT32 the message becomes an object constant, which I10 says nothing would catch",
     SIGNOUT_PROP, TYPE_DECL,
     "export const SIGN_OUT_MESSAGE = { type: 'session:signed-out' } as const;\n"
     "export const SIGN_OUT_TYPE = SIGN_OUT_MESSAGE.type;"),
]


def run(files, verbose=False):
    command = ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files]
    if verbose:
        command += ["--reporter=verbose"]
    proc = subprocess.run(command, cwd=FRONTEND, capture_output=True, text=True)
    out = proc.stdout + proc.stderr
    failed = total = None
    files_failed = set()
    names = []
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
        # `× file > describe > ... > case name 12ms` - the last segment, with the duration trimmed.
        if verbose and stripped.startswith("× "):
            leaf = stripped[2:].split(" > ")[-1]
            names.append(re.sub(r"\s+\d+ms$", "", leaf).strip())
    return proc.returncode, failed, total, files_failed, names


def edits_of(old, new):
    """One mutant, one or more replacements.

    A single replacement cannot express a REORDER - it can only delete or rewrite in place - and XT22,
    XT27, XT29 and XT30 all need one. T36 learned this the hard way: the first version of a reorder row
    deleted a line and called itself a reorder, which made it a duplicate of a deletion wearing another
    name. A mutant that measures something other than its own description is worse than a missing mutant,
    because the score counts it.
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
        # REPLAYED IN MEMORY, not checked against the original. XT29's second anchor was unique in the file
        # and ambiguous after XT29's own first edit, so a per-anchor check on the unmutated text passed it
        # and `apply` refused mid-run. BROKEN is not a survivor, so the run still ended `survivors: none`
        # and only the explicit broken-row arithmetic kept the score honest. Caught here now instead.
        text = (REPO / path).read_text()
        for find, replace in edits_of(old, new):
            count = text.count(find)
            if count != 1:
                stale.append(f"  {label.split()[0]:<6} {path} matches an anchor {count} times, expected 1")
                break
            text = text.replace(find, replace, 1)
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
        return None, True, None
    if failed > base_failed:
        _, own_failed, _, _, names = run(OWN_TESTS, verbose=True)
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        for name in names:
            print(f"            └ {name}")
        # A kill that rests on the hazard pin alone is a FIX scored as a regression caught.
        if names and all(name == HAZARD_PIN for name in names):
            print(f"  MISATTRIBUTED - the only named failure is AC9c, which pins OQ-T39-6 as it IS")
            return f"{label} [MISATTRIBUTED to AC9c]", False, names
        return None, False, names
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return f"{label} [SURVIVED]", False, None


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"REPO = {REPO}\n")
    revert()
    if not preflight():
        return 1

    code, failed, total, files_failed, _ = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    own_code, own_failed, own_total, _, _ = run(OWN_TESTS)
    print(f"baseline own cases:   {own_failed} failed of {own_total}, exit {own_code}\n")

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
        print(f"REFUSING TO RUN: this task's own cases fail {own_failed} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    broken = []
    regressions = canaries = unfalsifiable = 0
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if label.split()[0] in UNFALSIFIABLE:
            unfalsifiable += 1
        elif label.split()[0] in CANARIES:
            canaries += 1
        else:
            regressions += 1
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            broken.append(label)
            revert()
            continue
        lost, was_broken, _names = score(label, base_failed)
        if lost:
            survivors.append(lost)
        if was_broken:
            broken.append(label)
        revert()

    kept = [label for label, *_ in MUTANTS if not wanted or wanted in label]
    apart = CANARIES | UNFALSIFIABLE
    scored = [label for label in kept if label.split()[0] not in apart]
    # A BROKEN mutant is NOT a kill, and the inherited tally counted it as one. The number a reader
    # quotes must not be inflated by a mutant that never ran. Found in T36's phase 10.
    lost_labels = [label for label in survivors + broken if label.split()[0] not in apart]
    print(f"\nregressions: {len(scored) - len(lost_labels)}/{regressions} killed"
          f"{'' if not broken else f'  ({len(broken)} BROKEN, not counted as killed)'}")
    if canaries:
        print(f"canaries:    {canaries} - a change elsewhere that must still be caught today, scored apart")
    if unfalsifiable:
        print(f"unfalsifiable: {unfalsifiable} - no observable effect to detect; see UNFALSIFIABLE above")
    print(f"survivors:   {survivors or 'none'}")
    print(f"broken:      {broken or 'none'}")
    unexpected = [label for label in survivors if label.split()[0] not in apart]
    return 1 if (unexpected or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
