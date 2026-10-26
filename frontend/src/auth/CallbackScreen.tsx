import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { SIGN_IN_PATH } from '../app/routes';
import { exchangeAuthorizationCode } from './codeExchange';
import { setSignInNotice } from './signInNotice';

/**
 * Where the browser returns from the Spring Authorization Server flow.
 *
 * T17 owns one half of R8: a callback that arrives with no `state` is discarded, nothing is exchanged, and the
 * visitor is returned to the sign-in start with neutral copy. The other half - a `state` that is present but does
 * not match the stored one - is task 18, and the comparison belongs in the gap marked below.
 *
 * This screen reads nothing from storage and writes nothing to it. The three items T15 stored stay where they
 * are: task 23 owns deleting them, and a discarded attempt leaves a verifier whose challenge was never used,
 * which T15's next request overwrites.
 */
export function CallbackScreen() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  // Set before anything else runs. StrictMode invokes effects twice in development, and this effect navigates and
  // sets module state - both things that must happen once.
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const state = params.get('state');
    const code = params.get('code');

    // R8's antecedent, in T17's half. An empty value counts as missing: it is what a stripped or truncated query
    // produces, and it carries no proof the callback belongs to a request this tab made. A missing `code` is
    // discarded too - beyond R8's literal words, which speak only of `state`, but the alternative is handing the
    // exchange a grant it cannot use and calling that someone else's problem.
    if (state === null || state === '' || code === null || code === '') {
      setSignInNotice('discarded');
      // The leading slash matters: measured, a relative target resolves against the current route and lands on
      // the catch-all, silently. `replace` so a discarded callback is not reachable with the back button.
      navigate(`/${SIGN_IN_PATH}`, { replace: true });
      return;
    }

    // TASK 18 goes here: compare `state` with the stored value and discard on mismatch, the same way.

    void exchangeAuthorizationCode({ code, state });
  }, [params, navigate]);

  return (
    <main>
      <h1>{t('callback.heading')}</h1>
      <div role="status" aria-live="polite">{t('callback.working')}</div>
    </main>
  );
}
