import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { buildAuthorizeRequest } from './authorizeRequest';

/**
 * The sign-in screen: the first screen in the application, and the one that starts the OIDC authorization code
 * flow with PKCE (R6).
 *
 * It renders no password field, and no credential input of any kind. That is not a styling choice or an omission
 * to be filled in later - interactive login runs inside the Spring Authorization Server flow, so the password,
 * TOTP and recovery-code steps belong to the server and never to this bundle (L2, agents.md). A visitor who
 * chooses to sign in is handed to that flow by a full-page navigation.
 *
 * T15 owns everything about the request itself - the eight parameters, S256, exactly the three registered scopes,
 * and writing the three items the callback will read back. This screen's own job is narrow: offer the choice, and
 * send the visitor to the URL T15 built.
 */
export function SignInScreen() {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'starting' | 'failed'>('idle');
  // Synchronous, where `disabled` would not be: setStatus only schedules a re-render, so two clicks dispatched in
  // one task would both pass a state-based guard, build two requests, overwrite each other's stored verifier and
  // fire two navigations. A ref is set before anything awaits.
  const inFlight = useRef(false);

  async function startSignIn() {
    if (inFlight.current) return;
    inFlight.current = true;
    setStatus('starting');
    try {
      // `state` and `nonce` come back too, and are deliberately dropped: buildAuthorizeRequest has already put all
      // three items in session storage, and storing them again here could only diverge from what it wrote (L16).
      const { url } = await buildAuthorizeRequest();
      globalThis.location.assign(url);
      // No reset on success. The document is being replaced; re-enabling would invite a second navigation while
      // the browser is still committing the first.
    } catch {
      // The rejection is discarded, not logged. Its text can name the PKCE verifier contract, and this is the one
      // screen where the no-logging habit is worth keeping absolute rather than reasoned about case by case
      // (agents.md). The visitor gets the same neutral sentence whatever went wrong.
      inFlight.current = false;
      setStatus('failed');
    }
  }

  return (
    <main>
      <h1>{t('signIn.heading')}</h1>
      <p>{t('signIn.explanation')}</p>
      {/* aria-disabled rather than disabled: a focused element that becomes disabled drops the keyboard user to
          <body>, and this is the only control on the screen. The ref above is what actually makes it inert. */}
      <button type="button" aria-disabled={status === 'starting'} onClick={() => void startSignIn()}>
        {t('signIn.action')}
      </button>
      {/* Always rendered, so a screen reader announces the content when it appears rather than the region itself
          arriving with text already in it. Focus is not moved here: the button the visitor pressed is still there
          and still focused, and it is the control they need to retry. */}
      <div role="status" aria-live="polite">
        {status === 'starting' ? t('signIn.starting') : null}
        {status === 'failed' ? t('signIn.failed') : null}
      </div>
    </main>
  );
}
