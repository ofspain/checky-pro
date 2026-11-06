import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { SIGN_IN_PATH } from '../app/routes';

/**
 * The signed-out landing: where SAS returns the browser after `/connect/logout` (R19, L14).
 *
 * Task 36 points the authorization server here - `post_logout_redirect_uri` is this route, and
 * `design.md:164` records that auth-service registers it for `checky-spa`. Until this screen existed the
 * route was declared with no element, so a successful sign-out landed the visitor on a blank page, which
 * reads as a broken application at exactly the moment they were told nothing had gone wrong.
 *
 * **WHAT THIS SCREEN DELIBERATELY DOES NOT DO, and why the emptiness is the deliverable.** It clears nothing,
 * reads no token, touches no store, sends no request and starts no timer. Clearing here was considered and
 * rejected: it would make a URL into a sign-out affordance, so any cross-site link could sign a visitor out.
 * Redirecting a tab that still holds a token was also rejected, and on a measurement rather than a taste - no
 * authenticated route has an element yet, so `/app/home` renders nothing, and the redirect would move a
 * visitor from a page with words to a blank one. That option becomes available to task 174.
 *
 * **So the copy cannot assert the visitor's state.** This route is reachable by typing it, which means it can
 * be read in a tab that still holds a token, and this screen has no way to tell - nor may it look, because a
 * screen that reported session state would be the status view L4 forbids on a public surface. The catalogue
 * comment for `signedOut` carries the wording argument; the short version is that the heading is a label and
 * the explanation is an invitation, so neither is false in either arrival.
 *
 * `SIGN_IN_PATH` is read INSIDE the component, never at module scope, because this module is imported BY the
 * route table and the binding is still uninitialised while this module's body runs. `routes.tsx:2-5` states
 * the consequence as "if it is ever broken the application fails to start, not a test" - and phase 7 MEASURED
 * that, and it is wrong. A module-scope read here raises no ReferenceError at all: the import yields
 * `undefined` and the way back renders as `href="/app/undefined"`, a dead link on the one screen whose only
 * job is to offer a live one. The same mutation applied to `authorizeRequest.ts`, which is the cycle task
 * 16's comment is actually about, also throws nothing and fails exactly one case - the sign-in suite's own
 * pin. So the discipline is right and the reason given for it was not: what catches a module-scope read is a
 * test, never the runtime, which is why AC13 has one and why it carries a control.
 * `CallbackScreen.tsx:99` has followed the same rule since task 17, and this is that pattern, not a new one.
 *
 * A `Link` and not a button: this is the one navigation in the SPA that should NOT leave the document. Every
 * other hand-over here - the sign-in screen's, task 36's - is deliberately a full-page navigation to SAS. A
 * real anchor also gives middle-click, open-in-new-tab, copy-link and keyboard activation for free, all of
 * which a button wired to `navigate()` would silently remove.
 */
export function SignedOutScreen() {
  const { t } = useTranslation();

  return (
    <main>
      <h1>{t('signedOut.heading')}</h1>
      <p>{t('signedOut.explanation')}</p>
      <Link to={`/${SIGN_IN_PATH}`}>{t('signedOut.action')}</Link>
    </main>
  );
}
