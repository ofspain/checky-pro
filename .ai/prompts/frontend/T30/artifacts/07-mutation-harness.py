#!/usr/bin/env python3
"""T30's mutation harness: profileIsReadFromUserinfoAndAccountStatusFromMe.

    python3 -I .ai/prompts/frontend/T30/artifacts/07-mutation-harness.py [FILTER]

IT REVERTS WITH `git checkout -- src/`. COMMIT YOUR TESTS FIRST. Uncommitted test
edits are destroyed silently - that cost 19 cases in T14 and 5 in T15.

WHAT THE FAMILIES PROBE

  MP01-MP05  a token is read INSTEAD of /userinfo        (AC5 - the security clause)
  MP06-MP08  the reader builds its own request                      (AC3, L6)
  MP09-MP11  a failure leaks what it was told                       (AC8)
  MP12-MP15  no session is mistaken for a profile                   (AC12)
  MP16-MP17  the sub-only live shape is rejected or faked           (AC9)
  MP18-MP19  the profile survives the read                          (AC6, AC7)
  MP20-MP21  the bearer and whose job the 401 is                    (AC4)
  MP22-MP23  the contract stops saying what it says                 (AC1)

MP01-MP05 ARE THIS TASK'S REASON TO EXIST. R20's third clause is the only one an
implementation can break invisibly: reading `email` or `email_verified` off the access
token produces a plausible-looking value, no error, and no behavioural difference a
runtime test could see. Every one of these five mutants is caught by a SOURCE SCAN or
by nothing. If any survives, AC5 has stopped being verification.

MP01 is the subtlest: it reads `sub` rather than a PII claim, which is the shape Kimi's
F4 warned about - a cheap substitute for calling /userinfo that a reviewer skims past
because a UUID "isn't really PII". The claim list includes `sub` precisely so this dies.

MP05 IS WRITTEN TO ESCAPE, in T26's through T29's tradition: the decode sits in a module
nothing imports yet, so no runtime case can reach it. A whole-tree source scan is the
only instrument that sees dormant code - which is why AC5 scans the tree rather than a
file list, and why the empty-tree refusal matters.

MP12-MP15 ARE THE ONES THE MEASUREMENT BOUGHT. Before `/userinfo` was called against
the running server, the design had the session boundary handle a missing session via its
401 path. Measured: a bearer-less call answers 302 to /login, which `fetch` follows into
200 text/html. There is no 401. MP13 removes the short-circuit and MP14 removes
`redirect: 'manual'`; both are defects that would have shipped, and both are invisible
to any test that only ever drives a tab holding a token.

HOW TO READ THE OUTPUT. Each line reports the verdict and, separately, whether T30's OWN
file caught the mutant or only an inherited case did. Those are different claims and only
the second column is T30's. A mutant that makes a run unparseable is scored BROKEN, not
KILLED: a mutant has to COMPILE to be evidence of anything.

THE BASELINE IS ASSERTED, NOT ASSUMED. T29 learned this the hard way: one run read 3
failures instead of 1, and because every verdict is `failed > base_failed`, the two
mutants producing exactly 3 failures were reported as SURVIVORS. They were not. A floor
that can drift silently turns a false negative into a published mutation score, so this
harness refuses to run rather than report, and exits non-zero on any survivor.

EXPECTED BASELINE on this host: the whole suite runs 1436 passed / 1 failed - the one is
the pre-existing `src/api/lintRejectsHandWrittenBackendFetch.test.ts`, an oxlint
summary-line quirk of this machine, unrelated to T30 and failing before and after it.
T30's own file runs 38 / 38, exit 0.
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5] / "frontend"
CONTRACT = Path(__file__).resolve().parents[5] / "contracts/api/oidc.yaml"

READER = "src/profile/readProfile.ts"
CLIENT = "src/api/client.ts"
NONCE = "src/auth/idTokenNonce.ts"

T30_ONLY = ["src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx"]
FILES = []  # empty = the whole suite, so inherited kills anywhere are visible

EXPECTED_BASELINE_FAILURES = 1

# --- anchors, copied from the real source ----------------------------------------------------------------
NO_SESSION = "  if (tokens.current() === null) throw new ReadProfileError('no-session');"
GET_CALL = """  const { data, response } = await api.GET('/userinfo', {"""
REDIRECT_OPT = "    redirect: 'manual',"
REDIRECT_GUARD = """  if (response.redirected || (response.status >= 300 && response.status < 400) || response.type === 'opaqueredirect') {
    throw new ReadProfileError('no-session');
  }"""
UNAUTHORIZED = "  if (response.status === 401 || response.status === 403) throw new ReadProfileError('unauthorized');"
UNAVAILABLE = """  if (!response.ok || data === undefined || typeof data.sub !== 'string' || data.sub === '') {
    throw new ReadProfileError('unavailable');
  }"""
RETURN = "  return data;"
PUBLIC_OPS = "export const OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(['POST /oauth2/token']);"

MUTANTS = [
    # --- MP01-MP05: a token is read INSTEAD of /userinfo (AC5) -------------------------------------------
    ("MP01 SUBTLEST: sub is taken off the access token instead of calling /userinfo", READER, RETURN,
     "  const claims = JSON.parse(atob((tokens.current() ?? '').split('.')[1])) as { sub: string };\n"
     "  return { ...data, sub: claims.sub };"),
    ("MP02 email is read off the access token", READER, RETURN,
     "  const claims = JSON.parse(atob((tokens.current() ?? '').split('.')[1])) as { email?: string };\n"
     "  return { ...data, email: claims.email };"),
    ("MP03 email_verified - VERIFICATION STATE - is read off the access token", READER, RETURN,
     "  const claims = JSON.parse(atob((tokens.current() ?? '').split('.')[1])) as"
     " { email_verified?: boolean };\n"
     "  return { ...data, email_verified: claims.email_verified };"),
    ("MP04 a decode is added to the ALLOWLISTED file, which now also reads a claim", NONCE,
     "    return payload as Record<string, unknown>;",
     "    void (payload as { email?: string }).email;\n"
     "    return payload as Record<string, unknown>;"),
    # Dormant on purpose: nothing imports it, so only a whole-tree source scan can see it.
    ("MP05 ESCAPE: a decode in a module nothing imports yet", None, None, None),

    # --- MP06-MP08: the reader builds its own request (AC3, L6) ------------------------------------------
    ("MP06 the reader calls fetch directly", READER, GET_CALL,
     "  const raw = await globalThis.fetch('/userinfo');\n"
     "  const { data, response } = { data: (await raw.json()) as Profile, response: raw };\n"
     "  void ({"),
    ("MP07 the path is assembled rather than named", READER, GET_CALL,
     """  const path = `/${'userinfo'}` as '/userinfo';
  const { data, response } = await api.GET(path, {"""),
    ("MP08 /userinfo is made PUBLIC, so it carries no bearer and never renews", CLIENT, PUBLIC_OPS,
     "export const OIDC_PUBLIC_OPERATIONS: ReadonlySet<string> = new Set(["
     "'POST /oauth2/token', 'GET /userinfo']);"),

    # --- MP09-MP11: a failure leaks what it was told (AC8) -----------------------------------------------
    # RE-AIMED after the first run. The original threw
    # `new ReadProfileError(`unauthorized${detail}`)` and SURVIVED - and that survival was NOT an escape.
    # `ReadProfileError` looks its message up by key, so a key with a header glued on finds nothing and the
    # message becomes `undefined`: the leak is prevented by construction, and the mutant was INERT. T28's
    # MB27 is the same lesson. A mutant that cannot do the harm it names proves nothing either way, so this
    # one now throws the error type a leaky reader would actually reach for.
    ("MP09 the header is carried into a plain Error, which CAN hold it", READER, UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    throw new Error(`Profile read refused: ${response.headers.get('www-authenticate') ?? ''}`);\n"
     "  }"),
    ("MP10 the response body's detail reaches the error message", READER, UNAVAILABLE,
     """  if (!response.ok || data === undefined || typeof data.sub !== 'string' || data.sub === '') {
    const body = JSON.stringify(data ?? {});
    throw new ReadProfileError(`unavailable ${body}` as 'unavailable');
  }"""),
    ("MP11 a failed read is logged", READER, UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    console.warn('profile read refused', response.headers.get('www-authenticate'));\n"
     "    throw new ReadProfileError('unauthorized');\n"
     "  }"),

    # --- MP12-MP15: no session is mistaken for a profile (AC12) -----------------------------------------
    ("MP12 the no-session short-circuit becomes a renewal attempt instead", READER, NO_SESSION,
     "  if (tokens.current() === null) await tokens.renew();"),
    ("MP13 the short-circuit is removed, so a bearer-less request is sent", READER, NO_SESSION, ""),
    ("MP14 redirect: 'manual' is dropped, so a login page arrives as a success", READER, REDIRECT_OPT, ""),
    ("MP15 the 3xx guard is removed, so a redirect is read as a profile", READER, REDIRECT_GUARD, ""),

    # --- MP16-MP17: the sub-only live shape (AC9) --------------------------------------------------------
    ("MP16 a sub-only response is treated as a FAILURE, breaking the live server", READER, UNAVAILABLE,
     """  if (!response.ok || data === undefined || typeof data.sub !== 'string' || data.sub === ''
      || data.email === undefined) {
    throw new ReadProfileError('unavailable');
  }"""),
    ("MP17 the missing claims are FILLED IN rather than left absent", READER, RETURN,
     "  return { ...data, email: data.email ?? 'unknown@example.invalid', name: data.name ?? 'Unknown' };"),

    # --- MP18-MP19: the profile survives the read (AC6, AC7) --------------------------------------------
    ("MP18 the profile is cached in sessionStorage", READER, RETURN,
     "  globalThis.sessionStorage.setItem('themistra.profile', JSON.stringify(data));\n"
     "  return data;"),
    ("MP19 the profile is written to window.name", READER, RETURN,
     "  window.name = JSON.stringify(data);\n  return data;"),

    # --- MP20-MP21: the bearer and the 401 (AC4) --------------------------------------------------------
    ("MP20 the reader handles the 401 itself, bypassing the boundary's single flight", READER, UNAUTHORIZED,
     "  if (response.status === 401 || response.status === 403) {\n"
     "    await tokens.renew();\n"
     "    throw new ReadProfileError('unauthorized');\n"
     "  }"),
    ("MP21 a 403 is reported as unavailable, losing the unauthorized answer", READER, UNAUTHORIZED,
     "  if (response.status === 401) throw new ReadProfileError('unauthorized');"),
]

# MP22-MP23 mutate the CONTRACT rather than source, so they are applied separately.
CONTRACT_MUTANTS = [
    ("MP22 /userinfo is declared security: [], contradicting the client", "      operationId: getUserInfo",
     "      security: []\n      operationId: getUserInfo"),
    ("MP23 UserInfoResponse requires email too, which the live server never sends",
     "      required: [sub]", "      required: [sub, email]"),
]


def run(files):
    proc = subprocess.run(
        ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run", *files],
        cwd=ROOT, capture_output=True, text=True,
    )
    out = proc.stdout + proc.stderr
    failed = total = None
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Tests "):
            body = stripped[len("Tests "):]
            failed = int(body.split("failed")[0].strip()) if "failed" in body else 0
            if "(" in body:
                total = int(body.split("(")[-1].rstrip(")"))
    return proc.returncode, failed, total


ESCAPE_MODULE = ROOT / "src/profile/profileClaims.ts"
ESCAPE_SOURCE = """// MP05: dormant on purpose. Nothing imports this, so no runtime case can reach it.
export function claimsOf(token: string): { email?: string } {
  return JSON.parse(atob(token.split('.')[1])) as { email?: string };
}
"""


def apply(path, old, new):
    if path is None:  # MP05: a whole new module
        ESCAPE_MODULE.write_text(ESCAPE_SOURCE)
        return True
    target = ROOT / path
    text = target.read_text()
    if text.count(old) != 1:
        return False
    target.write_text(text.replace(old, new, 1))
    return True


def apply_contract(old, new):
    text = CONTRACT.read_text()
    if text.count(old) != 1:
        return False
    CONTRACT.write_text(text.replace(old, new, 1))
    return True


def revert():
    subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=False)
    subprocess.run(["git", "clean", "-fq", "--", "src/"], cwd=ROOT, check=False)
    subprocess.run(["git", "checkout", "--", str(CONTRACT)], cwd=CONTRACT.parent, check=False)


def score(label, base_failed):
    code, failed, total = run(FILES)
    if failed is None:
        print(f"  BROKEN    -              {label}")
        return None
    if failed > base_failed:
        _, own_failed, _ = run(T30_ONLY)
        own = "own" if (own_failed or 0) > 0 else "INHERITED-ONLY"
        print(f"  KILLED    {own:<14} {label}  ({failed} failed of {total}, exit {code})")
        return None
    print(f"  SURVIVED  -              {label}  ({failed} failed of {total}, exit {code})")
    return f"{label} [SURVIVED]"


def main():
    wanted = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"ROOT = {ROOT}\n")
    code, failed, total = run(FILES)
    print(f"baseline whole suite: {failed} failed of {total}, exit {code}")
    code30, failed30, total30 = run(T30_ONLY)
    print(f"baseline T30 file:    {failed30} failed of {total30}, exit {code30}\n")
    if failed != EXPECTED_BASELINE_FAILURES:
        print(
            f"REFUSING TO RUN: baseline is {failed} failures, expected "
            f"{EXPECTED_BASELINE_FAILURES}. Every verdict below would be measured against a\n"
            "wrong floor, and a mutant producing exactly this many failures would be reported\n"
            "as a survivor. Check `git status` and re-run."
        )
        return 1
    if failed30 != 0:
        print(f"REFUSING TO RUN: T30's own file fails {failed30} of {total30} on a clean tree.")
        return 1
    base_failed = failed

    survivors = []
    for label, path, old, new in MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply(path, old, new):
            print(f"  BROKEN    anchor missed  {label}")
            revert()
            continue
        survived = score(label, base_failed)
        if survived:
            survivors.append(survived)
        revert()

    for label, old, new in CONTRACT_MUTANTS:
        if wanted and wanted not in label:
            continue
        if not apply_contract(old, new):
            print(f"  BROKEN    anchor missed  {label}")
            revert()
            continue
        survived = score(label, base_failed)
        if survived:
            survivors.append(survived)
        revert()

    print(f"\nsurvivors: {survivors or 'none'}")
    return 1 if survivors else 0


if __name__ == "__main__":
    sys.exit(main())
