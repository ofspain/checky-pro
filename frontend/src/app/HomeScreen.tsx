import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Heading, Screen, Text } from './ui';

/**
 * The landing screen behind the gate: where a completed sign-in arrives (task 174, R28, L1).
 *
 * Until this existed, `/app/home` was a declared route with no element, so the end of a successful sign-in
 * was a blank document. `CallbackScreen` has navigated here since task 17 - `takeReturnTarget()` falls back
 * to this path when nothing was remembered (L1) - which means the most common sign-in in the application
 * ended on an empty page. That is the whole reason this file exists, and it is why the screen is allowed to
 * be this thin: having somewhere to land is the deliverable, and task 42 nests the authenticated shell
 * around it (L12).
 *
 * **THE FOCUS MOVE IS THE FEATURE, not the copy.** A single-page application replaces the document's
 * contents without a document load, so the browser moves nothing: focus stays where the last click left it,
 * or sits on `<body>`, and a screen reader announces nothing at all on arrival. A sighted visitor sees the
 * new screen; a screen-reader user is told that a page they were already on is still there. The effect below
 * is what closes that gap, and `L11` is the rule that requires it.
 *
 * It focuses the HEADING, and the two alternatives were weighed rather than defaulted past. `<main>` - which
 * `Screen` renders and could forward a ref to - announces the landmark and leaves the screen's name to
 * whatever the reader's software decides to read next. A focusable wrapper with `aria-labelledby` announces
 * a label borrowed from this heading, and adds a non-semantic div to do it. The heading announces the
 * screen's name and its level, which is exactly what someone arriving needs to hear, so it wins on the only
 * criterion that matters here.
 *
 * `tabIndex={-1}` is NOT passed from here. It lives inside `Heading`, because `ui/index.ts` rule 2 says an
 * attribute that is a primitive's own contract is set inside and never accepted from a consumer. Phase 3
 * proposed a `tabIndex?: -1` prop and an amendment to that rule; the freeze rejected both (D1). The
 * consequence worth knowing is that EVERY `<h1>` in the SPA is now programmatically focusable, which is
 * deliberate - `-1` is not in the tab order, and task 42's screens get this move without passing anything.
 *
 * The empty dependency array is load-bearing and `?.` is not defensive padding. `mountShell.tsx:25` renders
 * the tree inside `<StrictMode>`, so in development this effect runs twice; `focus()` on an
 * already-focused element is a no-op, which is why running twice is safe and why AC10 mounts the screen in
 * StrictMode and asserts it rather than reasoning about it. The ref is null on the first render and after
 * unmount, so `?.` is the real null case, not decoration.
 *
 * **WHAT THIS SCREEN DELIBERATELY DOES NOT DO.** It reads no token, calls no endpoint, writes no storage,
 * takes no props and renders nothing that depends on who is reading it. That is not minimalism, it is R22
 * and R20: this is the first screen behind the gate, and a screen that showed a name or an account state
 * would have to get it from the access token (R20 forbids reading it) or from `/userinfo` and
 * `/accounts/me`, which belong to task 42. A half-populated greeting is precisely the half-authenticated
 * screen R22 forbids. The import list above is the enforced form of that promise - AC6 asserts it against
 * the source, so adding a fetch or an `auth/` import fails a test rather than a review.
 */
export function HomeScreen() {
  const { t } = useTranslation();
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <Screen>
      <Heading ref={headingRef}>{t('home.heading')}</Heading>
      <Text>{t('home.explanation')}</Text>
    </Screen>
  );
}
