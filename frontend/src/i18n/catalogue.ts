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
  },
  // The callback is a transient screen. It says what is happening, never that sign-in has succeeded - the flow
  // is not finished until the token exchange, which is task 20.
  callback: {
    heading: 'Finishing sign-in',
    working: 'Checking your sign-in…',
  },
} as const;
