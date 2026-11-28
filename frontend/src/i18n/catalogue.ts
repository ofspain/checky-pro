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
    // THE TWO THINGS THE SCREEN SAID AFTER THE SUBMIT - and after task 47 only `submitting` is still one of
    // them. `success` is rendered by `RegisterSentScreen` now, which the form navigates to once the call
    // settles, so the words a visitor reads at the end of a registration live one screen away from the form.
    // The key keeps its name and its group: renaming it would churn four pins for no behavioural gain, and
    // this group is where its reservation was written.
    //
    // THAT RESERVATION IS SPENT, AND IT WAS KEPT RATHER THAN REPLACED. Task 43 wrote that "task 47's
    // acknowledgement screen is where that claim gets its own suite"; task 47's phase 3 then proposed a
    // second pair of keys with new wording instead, which would have left this string rendered nowhere while
    // two pins in `screensRenderThroughDesignSystemPrimitivesOnly` still named it - one of them pinning these
    // exact words. The freeze reused it, so this sentence is the one the named suite proves identical for a
    // new address and an already-registered one.
    //
    // It says nothing about whether the address was already registered, which is R2 and L4: the same words
    // either way, because the server answers 202 either way.
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
  // The acknowledgement (task 47), and ONE KEY rather than two. The sentence under this heading is
  // `register.success`, reused from the group above rather than rewritten here: task 43 reserved those words
  // for this screen in writing, and a second sentence would have left the first rendered nowhere.
  //
  // 'Check your email' IS AN INSTRUCTION AND NOT A REPORT, which is the whole reason it can be identical for
  // both cases. A heading that reported - 'Account created', or 'Already registered' - would disclose exactly
  // what the server withholds: R2 and L4 exist so a visitor who submits someone else's address learns nothing
  // about whether it has an account. This names what to do next and nothing else.
  //
  // IT REPEATS THE FIRST FOUR WORDS OF THE SENTENCE BELOW IT, DELIBERATELY. Focus moves to this heading on
  // arrival (L11), so it is the first and sometimes the only thing a screen-reader user hears before acting -
  // which means the essential instruction has to be complete inside it, not completed by the paragraph after
  // it. The sentence then supplies the reason. A heading trimmed to avoid the overlap - 'Almost there' - would
  // announce the arrival and leave the instruction to a line the visitor may never reach.
  registerSent: {
    heading: 'Check your email',
  },
  // THE VERIFICATION SCREEN - R3, task 48, and the destination of every verification email the platform has
  // ever sent. `/app/verify` has been a declared route with no element since task 16, so until now that link
  // landed on a blank document.
  //
  // `heading` IS SHOWN IN EVERY STATE, including while the request is in flight and when the link carried no
  // token at all, because the screen has only one heading and it names the screen rather than the outcome.
  // That it still reads as an instruction after the email is verified is OQ-T48-5, raised at the freeze and
  // the owner's to settle - focus lands here (L11), so it is the first thing announced.
  //
  // `success` IS ANNOUNCED RATHER THAN MERELY RENDERED, and that is a property of HOW it is rendered, not of
  // the words. It goes inside a `StatusRegion` that is mounted empty on arrival and filled when the 204
  // lands; that primitive's own header explains why the order matters - a live region mounted at the same
  // moment its text arrives is a new element with content already in it, and a screen reader announces
  // nothing. There is deliberately NO `verifying` key (D3): R3 covers the success state only, and the region
  // has to be mounted and empty anyway for the announcement to work, so its absence costs nothing
  // structurally.
  //
  // TASK 49 ADDED THE FAILURE PAIR, and the sentence above - "no failure key lives here" - was task 48
  // declining to invent R4's copy, not a claim that none would ever exist.
  //
  // **THE MESSAGE NAMES NO REASON, WHICH IS THE WHOLE REQUIREMENT.** L4 demands identical copy across bad,
  // expired and used tokens and across unknown, locked, suspended and deleted accounts, so 'expired',
  // 'already used', 'not found' and 'already verified' are all forbidden here - each would let a visitor
  // distinguish two outcomes by reading one sentence. 'This verification link is not valid' states the
  // outcome without diagnosing it. The backend already collapses every reason into one fixed 400
  // (`AccountExceptionHandler:46-52`), so this copy is the client half of a uniformity the server enforces.
  //
  // **FLAT, NOT NESTED, AND THAT WAS MEASURED.** Phase 3 designed these as `verify.failure.message` and
  // `verify.failure.action`. Phase 5's probe found that shape CRASHES the copy scan in
  // `screensRenderThroughDesignSystemPrimitivesOnly` - `sentence.replace is not a function` - because it
  // flattens this catalogue exactly one level and assumes every value is a string, and a `failure` object
  // would have been the first second-level group in the file. Flat keys keep the grouping legible, keep the
  // reuse path open, and leave the instrument working (D-T49-4).
  //
  // `failureMessage` is the half task 54 reuses: R15's own text says a failed reset "SHALL show the uniform
  // failure of R4". `failureAction` is this screen's, because a reset failure does not offer to resend a
  // verification email.
  verify: {
    heading: 'Verify email',
    success: 'Your email has been verified.',
    failureMessage: 'This verification link is not valid.',
    failureAction: 'Resend verification email',
  },
  shell: {
    // Not invented either: the mirror of `signedOut.action`'s 'Sign in', so the two directions of the same
    // journey are spelled the same way. Not 'Log out' - nothing in this catalogue says 'log'.
    signOut: 'Sign out',
  },
} as const;
