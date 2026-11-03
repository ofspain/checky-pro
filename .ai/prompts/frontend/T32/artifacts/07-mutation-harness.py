#!/usr/bin/env python3
"""T32 mutation harness - email_verified is not account state.

WHAT THIS IS. Each mutant is a regression this task exists to prevent, applied to real source,
measured against a real baseline, and reverted. A case that cannot tell the mutant from the
original is a case that was never holding anything.

IT REVERTS WITH `git checkout`. COMMIT YOUR TESTS FIRST. Uncommitted test edits are destroyed
silently - that cost 19 cases in T14 and 5 in T15.

WHAT IT ASSERTS ABOUT ITSELF, carried from T31's phase 11 where each of these was paid for:

  * the baseline failures are pinned by FAILING FILE NAME, and the count only WARNS (Kimi A7).
    A count alone absorbs a swap - fix the environmental test on the day an unrelated one breaks
    and the floor still reads 1 while being made of a different test. Refusing on the count made
    T31's harness unrunnable on the very host that found the problem.
  * every anchor must match its file EXACTLY ONCE before a single verdict prints. A stale anchor
    prints BROKEN, BROKEN is not SURVIVED, and the run would still end `survivors: none` with the
    score overstated by exactly the number of anchors that rotted. That was T30's real defect.
  * a BROKEN mutant FAILS the run, like a survivor. Until T31 phase 9 it printed and was invisible
    to the exit code, which is the same defect in its last hiding place.

PATHS ARE REPO-RELATIVE, unlike T30's harness, which kept a separate list and a separate apply path
for contract mutants. Four of T32's eleven mutants edit a contract rather than source, so one
uniform path spelling is simpler than two code paths that can disagree.

    python3 .ai/prompts/frontend/T32/artifacts/07-mutation-harness.py          # all
    python3 .ai/prompts/frontend/T32/artifacts/07-mutation-harness.py MR04     # one
"""

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[5]
FRONTEND = REPO / "frontend"

NONCE = "frontend/src/auth/idTokenNonce.ts"
CLIENT = "frontend/src/api/client.ts"
AUTHORIZE = "frontend/src/auth/authorizeRequest.ts"
READER = "frontend/src/profile/readAccount.ts"
AUTH_YAML = "contracts/api/auth.yaml"
OIDC_YAML = "contracts/api/oidc.yaml"

OWN_TEST = "src/auth/emailVerifiedClaimIsNotAccountState.test.ts"
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1
KNOWN_ENVIRONMENTAL_FAILURES = {
    # This host's oxlint@1.87.0 prints no summary line, so the assertion that parses it cannot pass.
    "src/api/lintRejectsHandWrittenBackendFetch.test.ts",
}

# --- anchors, copied from the real source ----------------------------------------------------------------
PAYLOAD_RETURN = "    return payload as Record<string, unknown>;"
ATOB = "globalThis.atob(base64.padEnd("
SCOPES = "export const REQUESTED_SCOPES = ['openid', 'profile', 'email'] as const;"
AUTH_API = "export function createAuthApi(boundary: SessionBoundary, rateLimit?: RateLimitNoticeState): AuthApi {"
REQUIRED = "      required: [accountUuid, email, emailVerified, status, createdAt]"
STATUS_ENUM = "          enum: [PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED]"
DISCLAIMER = "NOT the access"
ACCOUNT_RETURN = "  return data;"

MUTANTS = [
    # --- MR01-MR02: the claim is read off the decoded token (AC1) --------------------------------------
    # THE SUBTLEST, and the one the task is named for. The file already holds a decoded payload; reading
    # one more property out of it is a two-word edit and the nonce keeps working, so nothing behavioural
    # changes. Only the source scan can see it.
    ("MR01 the decoding file reads email_verified off the payload it already has", NONCE, PAYLOAD_RETURN,
     "    void (payload as Record<string, unknown>).email_verified;\n"
     "    return payload as Record<string, unknown>;"),
    ("MR02 the same read, in the camelCase spelling", NONCE, PAYLOAD_RETURN,
     "    void (payload as Record<string, unknown>).emailVerified;\n"
     "    return payload as Record<string, unknown>;"),

    # --- MR03: a SECOND file decodes, so the bound every other claim rests on breaks (AC1) ------------
    ("MR03 a second production file starts decoding a token", CLIENT, AUTH_API,
     "export function peek(token: string): unknown {\n"
     "  return JSON.parse(globalThis.atob(token.split('.')[1]));\n"
     "}\n\n" + AUTH_API),

    # --- MR04: the channel rule (AC2) -----------------------------------------------------------------
    # A file names the claim and reads nothing from /userinfo. This is the shape a verification banner
    # would take if someone wired it to the wrong source.
    ("MR04 a module names email_verified without reading /userinfo", CLIENT, AUTH_API,
     "export const isVerified = (claims: { email_verified?: boolean }) => claims.email_verified === true;\n\n"
     + AUTH_API),

    # --- MR05: the allowlist row's LOWER bound (AC1) ---------------------------------------------------
    # An upper bound alone lets a row outlive the thing it permits. If the decode goes, the row keeps
    # granting budget nobody uses, and the allowlist stops being evidence.
    ("MR05 the permitted decoder stops decoding, leaving its allowlist row stale", NONCE, ATOB,
     "String(base64.padEnd("),

    # --- MR06: the premise (AC4) -----------------------------------------------------------------------
    # Drop the email scope and the claim's value changes meaning under the task: token-claims.md says it
    # is true IFF that scope was authorized.
    ("MR06 the SPA stops requesting the email scope, changing what the claim reports", AUTHORIZE, SCOPES,
     "export const REQUESTED_SCOPES = ['openid', 'profile'] as const;"),

    # --- MR07-MR09: the contract pins (AC3) ------------------------------------------------------------
    # A pin on a contract line is worth only as much as the proof that the pin fires. T30's MP22/MP23/MP28
    # are the precedent.
    ("MR07 AccountResponse stops REQUIRING emailVerified, so the truthful source becomes optional",
     AUTH_YAML, REQUIRED, "      required: [accountUuid, email, status, createdAt]"),
    ("MR08 the contract's disclaimer is deleted, so nothing records that the claim is not state",
     OIDC_YAML, DISCLAIMER, "ALSO the access"),
    ("MR09 PENDING_VERIFICATION leaves the status enum", AUTH_YAML, STATUS_ENUM,
     "          enum: [ACTIVE, LOCKED, SUSPENDED, DELETED]"),

    # --- MR10: the claim is logged from the one file that can see it (AC1) ----------------------------
    ("MR10 the decoding file LOGS the claim it is not supposed to read", NONCE, PAYLOAD_RETURN,
     "    console.info('email_verified', payload);\n"
     "    return payload as Record<string, unknown>;"),

    # --- MR11: expected INHERITED-ONLY, and that is the point ------------------------------------------
    # Caching verification state is covered by T31's store-emptiness assertion, not by anything of T32's.
    # Recording it here says so out loud: this harness should NOT claim the kill as its own, and if the
    # `own` column ever reads `own` for this row, a T32 case has grown a reach it was not given.
    ("MR11 a verification flag is cached in sessionStorage (T31 owns this kill)", READER, ACCOUNT_RETURN,
     "  globalThis.sessionStorage.setItem('ev', String(data.emailVerified));\n"
     "  return data;"),
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


def apply(path, old, new):
    target = REPO / path
    text = target.read_text()
    if text.count(old) != 1:
        return False
    target.write_text(text.replace(old, new, 1))
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "frontend/src/", "contracts/"], cwd=REPO, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "frontend/src/"], cwd=REPO, check=False)


def preflight():
    """Every anchor in its file, exactly once, before a single verdict is printed."""
    stale = []
    for label, path, old, _new in MUTANTS:
        count = (REPO / path).read_text().count(old)
        if count != 1:
            stale.append(f"  {label.split()[0]:<6} {path} matches its anchor {count} times, expected 1")
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
    print(f"baseline T32 cases:   {own_failed} failed of {own_total}, exit {own_code}\n")

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
        print(f"REFUSING TO RUN: T32's own cases fail {own_failed} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    broken = []
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
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

    print(f"\nsurvivors: {survivors or 'none'}")
    print(f"broken:    {broken or 'none'}")
    return 1 if (survivors or broken) else 0


if __name__ == "__main__":
    sys.exit(main())
