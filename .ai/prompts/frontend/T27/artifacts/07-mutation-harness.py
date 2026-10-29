"""T27 mutation harness: every mutant is a SECOND FACTOR the SPA must not have.

!!! THIS SCRIPT EDITS PRODUCTION SOURCE AND REVERTS WITH `git checkout -- src/`. !!!
!!! COMMIT OR STASH EVERYTHING UNDER frontend/src/ BEFORE RUNNING IT.            !!!
Uncommitted edits are destroyed silently. That has cost real work twice in this
package (19 test cases in T14, 5 in T15), which is why the warning is this loud.

    python3 -I .ai/prompts/frontend/T27/artifacts/07-mutation-harness.py [filter]

Run it from the repository root; it locates `frontend/` relative to its own path. It
needs `npx`, and takes a few minutes - each mutant is two vitest runs. An optional
substring argument runs one family, e.g. `MA07`.

WHY THIS LIST LOOKS LIKE THIS. T27 verifies an ABSENCE, so a mutant here is not a
broken behaviour - it is a PLAUSIBLE ADDITION. Every one below is something a
well-meaning developer might write and call progress:

  MA01-MA04  a credential input arrives on the sign-in screen
  MA05-MA07  second-factor logic ships without rendering anything
  MA08-MA10  the callback grows a branch for "MFA was used"
  MA11-MA13  the authorize request negotiates a factor
  MA14-MA16  the copy discloses a second factor (and so breaches R10)
  MA17-MA19  a second hand-over, or one through another mechanism
  MA20-MA22  an MFA-shaped value is stored, logged or forwarded

MA17 IS THE MUTANT KIMI F1 EXISTS FOR: a second navigation through
`location.href` ALONGSIDE the existing `assign`. An `assign`-only spy reports
exactly one call and passes. If MA17 ever survives, the hand-over counter has been
narrowed back to one mechanism.

MA22 IS WRITTEN TO ESCAPE, in T26's tradition: an MFA marker stored under a
computed key behind a condition no test drives. The runtime instruments cannot see
dormant code; only the source scan can. If MA22 survives, the structural half of
AC2/AC3 has been weakened into a behavioural one.

HOW TO READ THE OUTPUT. Each line reports the verdict and, separately, whether
T27's OWN file caught the mutant or only an inherited case did. Those are different
claims and only the second column is T27's. A mutant that makes a run unparseable is
scored BROKEN, not KILLED: read it by hand before calling it detected.

EXPECTED BASELINE on this host: the whole auth+api set runs with 1 pre-existing
failure (`src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`, an oxlint
summary-line difference, unrelated to this task), and T27's own file runs 47/47
with exit 0.
"""

import re
import subprocess
import sys
from pathlib import Path

# Derived from this script's own location: <repo>/.ai/prompts/frontend/T27/artifacts/ -> <repo>/frontend
ROOT = str(Path(__file__).resolve().parents[5] / "frontend")

SIGN_IN = "src/auth/SignInScreen.tsx"
CALLBACK = "src/auth/CallbackScreen.tsx"
AUTHORIZE = "src/auth/authorizeRequest.ts"
CATALOGUE = "src/i18n/catalogue.ts"
EXCHANGE = "src/auth/codeExchange.ts"

# --- anchors, copied from the real source ----------------------------------------------------------------
BUTTON = """      <button
        type="button"
        aria-describedby="sign-in-explanation"
        aria-disabled={status === 'starting'}
        aria-busy={status === 'starting'}
        onClick={() => void startSignIn()}
      >"""
ASSIGN = "      globalThis.location.assign(url);"
START_BODY = "  async function startSignIn() {\n    if (inFlight.current) return;"
PARAMETERS = "  const parameters = new URLSearchParams({\n    response_type: 'code',"
HEADING = "    heading: 'Sign in',"
EXPLANATION = "    explanation: 'Continue to the secure sign-in page to enter your details.',"
WORKING = "    working: 'Checking your sign-in…',"
RETAIN = "      retainAccessToken(response.accessToken);"
NONCE_CHECK = "      if (!idTokenNonceMatchesStoredValue(response.idToken).ok) {"
DESTRUCTURE = "  const { id_token: idToken, access_token: accessToken } = payload as Record<string, unknown>;"

MUTANTS = [
    # --- MA01-MA04: a credential input arrives on the sign-in screen -------------------------------------
    ("MA01 a one-time-code input is rendered beside the button", SIGN_IN, BUTTON,
     '      <input autoComplete="one-time-code" name="code" inputMode="numeric" />\n' + BUTTON),
    ("MA02 a password input is rendered", SIGN_IN, BUTTON,
     '      <input type="password" autoComplete="current-password" name="password" />\n' + BUTTON),
    # Closed properly: the first draft of this mutant left the <form> unclosed, which is a JSX syntax
    # error rather than a plausible addition, and the harness correctly scored it BROKEN rather than
    # KILLED. A mutant has to COMPILE to be evidence of anything.
    ("MA03 the screen grows a form", SIGN_IN, BUTTON,
     "      <form onSubmit={(event) => event.preventDefault()}>\n" + BUTTON
     + "\n      </form>"),
    ("MA04 a recovery-code textarea is rendered", SIGN_IN, BUTTON,
     '      <textarea name="recoveryCode" aria-label="Recovery code" />\n' + BUTTON),

    # --- MA05-MA07: second-factor logic that renders nothing ---------------------------------------------
    ("MA05 a TOTP verifier ships as a function", SIGN_IN, START_BODY,
     "  function verifyTotp(code: string): boolean {\n    return code.length === 6;\n  }\n\n" + START_BODY),
    ("MA06 a recovery-code normaliser ships", SIGN_IN, START_BODY,
     "  const normaliseRecoveryCode = (value: string) => value.replace(/\\s/g, '').toUpperCase();\n\n"
     + START_BODY),
    ("MA07 an authenticator prompt behind a dormant branch", SIGN_IN, ASSIGN,
     "      if (globalThis.navigator.userAgent.includes('ZZZ-NEVER-MATCHES')) {\n"
     "        globalThis.console.log('authenticator required');\n      }\n" + ASSIGN),

    # --- MA08-MA10: the callback branches on how many factors were used ----------------------------------
    ("MA08 the callback navigates elsewhere when amr names two factors", CALLBACK, RETAIN,
     RETAIN + "\n      const amr = JSON.parse(atob(response.idToken.split('.')[1] ?? 'e30'))"
     ".amr as string[] | undefined;\n"
     "      if (amr !== undefined && amr.includes('otp')) {\n"
     "        navigate('/account/mfa', { replace: true });\n        return;\n      }"),
    ("MA09 the callback renders different copy when MFA was used", CALLBACK,
     "      <div role=\"status\" aria-live=\"polite\">{t('callback.working')}</div>",
     "      <div role=\"status\" aria-live=\"polite\">\n"
     "        {globalThis.sessionStorage.getItem('themistra.auth.mfa_used') === 'true'\n"
     "          ? 'Verifying your authenticator code…'\n"
     "          : t('callback.working')}\n      </div>"),
    ("MA10 the callback discards a single-factor sign-in", CALLBACK, NONCE_CHECK,
     "      const claims = JSON.parse(atob(response.idToken.split('.')[1] ?? 'e30')) as "
     "{ amr?: string[] };\n"
     "      if (claims.amr !== undefined && !claims.amr.includes('otp')) {\n"
     "        discard();\n        return;\n      }\n" + NONCE_CHECK),

    # --- MA11-MA13: the authorize request negotiates a factor --------------------------------------------
    ("MA11 an acr_values parameter is added", AUTHORIZE, PARAMETERS,
     PARAMETERS + "\n    acr_values: 'urn:themistra:mfa',"),
    ("MA12 a claims parameter requests an amr claim", AUTHORIZE, PARAMETERS,
     PARAMETERS + '\n    claims: \'{"id_token":{"amr":{"essential":true}}}\','),
    ("MA13 a prompt parameter steers the SAS flow", AUTHORIZE, PARAMETERS,
     PARAMETERS + "\n    prompt: 'login',"),

    # --- MA14-MA16: the copy discloses a second factor (R10) ---------------------------------------------
    ("MA14 the explanation promises an authenticator step", CATALOGUE, EXPLANATION,
     "    explanation: 'Continue to the secure sign-in page, then enter your authenticator code.',"),
    ("MA15 a catalogue entry for an MFA failure is added", CATALOGUE, HEADING,
     HEADING + "\n    mfaRequired: 'Set up two-factor authentication to continue.',"),
    ("MA16 the callback copy names a one-time code", CATALOGUE, WORKING,
     "    working: 'Checking your one-time code…',"),

    # --- MA17-MA19: the hand-over ------------------------------------------------------------------------
    ("MA17 a SECOND hand-over through location.href (the mutant Kimi F1 exists for)", SIGN_IN, ASSIGN,
     "      globalThis.location.href = url;\n" + ASSIGN),
    ("MA18 the hand-over moves to window.open", SIGN_IN, ASSIGN,
     "      globalThis.open(url, '_self');"),
    ("MA19 the inFlight guard is dropped, so a double click hands over twice", SIGN_IN,
     "    if (inFlight.current) return;\n    inFlight.current = true;",
     "    inFlight.current = true;"),

    # --- MA20-MA22: an MFA-shaped value escapes ----------------------------------------------------------
    ("MA20 the amr claim is written to sessionStorage", CALLBACK, RETAIN,
     RETAIN + "\n      globalThis.sessionStorage.setItem('themistra.auth.amr',\n"
     "        String(JSON.parse(atob(response.idToken.split('.')[1] ?? 'e30')).amr));"),
    ("MA21 the whole token response is kept, so MFA fields survive the exchange", EXCHANGE, DESTRUCTURE,
     DESTRUCTURE + "\n  globalThis.sessionStorage.setItem('themistra.auth.token_response',\n"
     "    JSON.stringify(payload));"),
    ("MA22 WRITTEN TO ESCAPE: an mfa marker under a computed key, behind a dead condition", CALLBACK, RETAIN,
     RETAIN + "\n      if (globalThis.navigator.userAgent.includes('ZZZ-NEVER-MATCHES')) {\n"
     "        const key = 'themistra.auth.' + 'mfa' + '_marker';\n"
     "        const method = 'set' + 'Item';\n"
     "        (globalThis.sessionStorage as unknown as Record<string, "
     "(a: string, b: string) => void>)[method](key, 'totp');\n      }"),
]

# Every file whose cases could plausibly notice one of these mutants.
FILES = [
    "src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx",
    "src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx",
    "src/auth/sessionStorageHoldsOnlyTheL16Items.test.tsx",
    "src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx",
    "src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx",
    "src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx",
    "src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx",
    "src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx",
    "src/auth/callbackValidatesNonce.test.tsx",
]

T27_ONLY = ["src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx"]


def run(files):
    proc = subprocess.run(
        ["npx", "-y", "pnpm@12.14.0", "exec", "vitest", "run"] + files,
        cwd=ROOT, capture_output=True, text=True,
    )
    out = proc.stdout + proc.stderr
    m = re.search(r"Tests\s+(?:(\d+) failed \| )?(\d+) passed(?: \| (\d+) todo)?\s+\((\d+)\)", out)
    if not m:
        return None, None, proc.returncode
    return int(m.group(1) or 0), int(m.group(4)), proc.returncode


wanted = sys.argv[1] if len(sys.argv) > 1 else ""
selected = [m for m in MUTANTS if wanted in m[0]]

subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=True)
base_failed, base_total, base_code = run(FILES)
own_base_failed, own_base_total, own_base_code = run(T27_ONLY)
print(f"ROOT = {ROOT}\n")
print(f"baseline all files: {base_failed} failed of {base_total}, exit {base_code}")
print(f"baseline T27 file:  {own_base_failed} failed of {own_base_total}, exit {own_base_code}\n")

survivors = []
for name, path, old, new in selected:
    source = open(f"{ROOT}/{path}").read()
    if old not in source:
        print(f"  !! {name}: pattern not found in {path}")
        survivors.append(f"{name} (NOT APPLIED)")
        continue
    open(f"{ROOT}/{path}", "w").write(source.replace(old, new, 1))
    failed, total, code = run(FILES)
    own_failed, own_total, own_code = run(T27_ONLY)
    subprocess.run(["git", "checkout", "--", path], cwd=ROOT, check=True)
    if failed is None or total is None:
        verdict = "BROKEN"
    elif failed > base_failed or total != base_total or code != base_code:
        verdict = "KILLED"
    else:
        verdict = "SURVIVED"
    alone = ("own" if (own_failed or 0) > (own_base_failed or 0) or own_code != own_base_code
             else "INHERITED-ONLY")
    if verdict != "KILLED":
        survivors.append(f"{name} [{verdict}]")
    print(f"  {verdict:9} {alone:14} {name}  ({failed} failed of {total}, exit {code})")

print("\nsurvivors:", survivors or "none")
subprocess.run(["git", "checkout", "--", "src/"], cwd=ROOT, check=True)
