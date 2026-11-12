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
  // and `/userinfo` or `/accounts/me`, which are task 42's and which this screen makes no call to. So the
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
} as const;
