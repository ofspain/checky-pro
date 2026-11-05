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
} as const;
