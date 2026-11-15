/** The English catalogue: every user-facing string in the SPA lives here (L11, O3). */
export const en = {
  rateLimit: {
    message: 'Too many requests right now. Wait a moment, then try again.',
    dismiss: 'Dismiss',
  },
  // In the order a visitor meets them, not alphabetical, so a translator reads the screen rather than a list.
  // Deliberately neutral: nothing here discloses whether an account exists or what the sign-in page will ask for
  // (L4), and nothing promises a password step, because which factors are required is the server's to decide (L2).
  signIn: {
    heading: 'Sign in',
    explanation: 'Continue to the secure sign-in page to enter your details.',
    action: 'Continue to sign in',
    starting: 'Taking you to the sign-in page…',
    failed: 'Sign-in could not be started. Try again.',
    // Shown after a callback is discarded (R8). Neutral by requirement: it names no cause, so a missing
    // parameter, an expired attempt and a forged link are indistinguishable to the visitor (L4).
    discarded: 'Your sign-in did not complete. Try again.',
    // Shown when a session that HAD completed is no longer usable and renewal could not recover it (R22,
    // task 33). It is a separate sentence from `discarded` because that one says sign-in did not complete,
    // which here would be false - the visitor signed in, possibly hours ago. It names the EVENT and never a
    // cause: a locked, suspended or deleted account, an expired token and an unreachable server all end a
    // session the same way and all read this same line (L4). "Sign in again." and not "Continue to sign in."
    // because the latter is verbatim the button beside it.
    sessionEnded: 'Your session has ended. Sign in again.',
  },
  // The callback is a transient screen. It says what is happening, never that sign-in has succeeded - the flow
  // is not finished until the token exchange, which is task 20.
  callback: {
    heading: 'Finishing sign-in',
    working: 'Checking your sign-in…',
  },
  // The signed-out landing (task 37), last of the three a visitor meets. NOTHING in the spec package specifies
  // these words - R19 governs the clearing and the end-session call, L14 only that the route exists and is
  // public - so they are derived from L4 and modelled on `signIn.sessionEnded`, which names an EVENT and never
  // a cause. The constraint that shaped them is less obvious than L4: this screen is reachable by typing its
  // URL, so it can be read by a visitor whose tab still holds a token, and it cannot verify otherwise without
  // becoming a status view L4 forbids. So no string here asserts what is true of the visitor's session.
  signedOut: {
    // A page label, not a status report. Not "You have been signed out", not "successfully", and not "of all
    // devices" - each of those is a claim about state this screen cannot check.
    heading: 'Signed out',
    // An INVITATION, which is what carries the burden above: it says nothing about what is currently true, so
    // it is true for someone who has just signed out AND for someone who arrived here holding a token.
    // `signIn.sessionEnded`'s "Your session has ended" was considered and rejected for exactly that reason.
    explanation: 'Sign in again whenever you are ready.',
    // Deliberately NOT `signIn.action`'s "Continue to sign in", which promises the hand-over to the
    // server-hosted flow. This only returns the visitor to the sign-in screen, whose heading it matches.
    action: 'Sign in',
  },
  // The first screen behind the gate (task 174). Two strings, and the shortness is the decision: nothing in
  // the package specifies this copy, so the brief froze the minimum that is not blank and put it here, where
  // the owner can rewrite it without touching a component or a test that asserts behaviour (D7, OQ-T174-1).
  //
  // NOTHING HERE MAY DEPEND ON WHO IS READING IT. No name, no greeting, no account state, no counts - R22
  // forbids a half-authenticated screen, and a screen that greeted a visitor by name would have to get that
  // name from somewhere. The only places it could come from are the access token, which R20 forbids reading,
  // and `/userinfo` or `/accounts/me` - whose READERS are tasks 30's and 31's and have never had a caller
  // (OQ-T33-2); task 42 added the shell's chrome and did not change that. This comment said those two
  // endpoints "are task 42's", which was wrong twice over and is corrected here rather than left to mislead
  // the next reader (T42 deviation). What holds either way is that this screen makes no call at all. So the
  // copy is written for a reader this screen knows nothing about, and AC6 enforces that in both the DOM and
  // the import list.
  home: {
    // A label for the place, not a welcome. 'Welcome back' was considered and rejected: it claims the visitor
    // has been here before, which this screen cannot know, and it is the kind of string that invites a name
    // after it.
    heading: 'Home',
    // Says what is true and what is coming, without promising a date or naming a feature. This is the one
    // string most likely to be replaced the moment there is something to show, which is why it is the only
    // sentence here.
    explanation: 'You are signed in. There is nothing here yet.',
  },
  // The authenticated chrome (task 42). ONE KEY, and the one it is not is the reason this comment exists:
  // phase 3's F8 proposed `shell.brand` with a product name in it. Declined. The package names no brand
  // string for a header, and a product name invented in a component is copy the owner cannot change without
  // a code edit - the thing `home` above was put here to avoid. A brand line, if it is ever wanted, is one
  // key here and one `Text` in the layout.
  register: {
    heading: 'Create your account',
    emailLabel: 'Email address',
    passwordLabel: 'Password',
    submit: 'Create account',
    // The two things the screen says after the submit (task 43, D7). `success` says nothing about whether
    // the address was already registered - R2 and L4 require the same words either way, and task 47's
    // acknowledgement screen is where that claim gets its own suite.
    submitting: 'Creating your account…',
    success: 'Check your email to finish creating your account.',
    /**
     * TASK 45, AND THE FIRST INTERPOLATED STRING IN THIS APPLICATION. Task 43 reserved this key's words for
     * this task and said so here; the reservation is spent, and what replaced it is a TEMPLATE rather than
     * a sentence, for one reason: R1's bound lives in `passwordLength.ts`, and a literal "12 to 128" here
     * would be a second copy of it that drifts the day a bound moves. The screen interpolates
     * `ADVISORY_MINIMUM_CODE_POINTS` and `ADVISORY_MAXIMUM_CODE_POINTS`, and a case asserts the rendered
     * numbers ARE those constants - which is the only thing that can tell this string from one with the
     * numbers typed in, since both render identically.
     *
     * IT NAMES THE RULE AND DOES NOT DIAGNOSE THE INPUT (D-T45-2). "Too short" is a verdict, and R1 makes
     * this check ADVISORY - the server is the authority, so the client has no standing to deliver one. It
     * also means ONE string serves both ends of the range and the screen never branches on which bound was
     * missed.
     *
     * "CHARACTERS", NOT "CODE POINTS", and the imprecision is deliberate. R1 counts code points and
     * `tasks.md` itself says "128 characters"; for every password a visitor is likely to type the two
     * agree. They part only for a grapheme cluster built from several code points - a flag, or a family
     * emoji - where this sentence would understate the allowance. Naming the unit precisely would cost
     * every visitor a term they do not know to save a few the surprise that the server resolves anyway.
     */
    passwordGuidance: 'Use a password of {{min}} to {{max}} characters.',
  },
  shell: {
    // Not invented either: the mirror of `signedOut.action`'s 'Sign in', so the two directions of the same
    // journey are spelled the same way. Not 'Log out' - nothing in this catalogue says 'log'.
    signOut: 'Sign out',
  },
} as const;
