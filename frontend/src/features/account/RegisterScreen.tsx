import { useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { createPublicAuthApi } from '../../api/client';
import { EmailField, Form, Heading, PasswordField, Screen, StatusRegion, SubmitButton } from '../../app/ui';
import { ADVISORY_MAXIMUM_CODE_POINTS, ADVISORY_MINIMUM_CODE_POINTS, countCodePoints } from './passwordLength';

/**
 * The registration screen — R1, and the first screen in `features/`.
 *
 * **WHERE THIS FILE LIVES, AND WHY NOT `auth/`.** `design.md:206` lays out `src/features/account/` for
 * "register, verify, reset, sessions", and nothing had created `features/` in 42 tasks. Registration is an
 * account operation, not part of the OIDC flow: it holds no token, starts no session, and is forbidden from
 * importing `auth/` at all. The three screens in `auth/` are there because they ARE the flow (L1-L3).
 *
 * **THE CHECK IS ADVISORY, WHICH MEANS IT MARKS AND DOES NOT BLOCK** (R1: "as advisory guidance only ... The
 * server is the authority"). On submit the password's code points are counted; outside 12 to 128 the field is
 * marked `aria-invalid` and **the request is sent anyway**. Nothing is disabled, nothing is prevented, and no
 * sentence explains the mark yet - task 45 owns the guidance copy and should choose its own words. A state
 * with no sentence is incomplete on purpose; a check with no consumer would have been worse, and the freeze's
 * own prediction that removing the check would be unobservable (D17) was the symptom of that shape.
 *
 * **IT ANNOUNCES ITS RESULT AND DOES NOT NAVIGATE** (D1, OQ-T43-5). `POST /accounts` always answers 202, and
 * task 47 owns both the acknowledgement screen and `register/sent` - which is a declared path with no element
 * today, so navigating there would hand the visitor a blank document. Instead the `StatusRegion` is mounted
 * from the start and filled afterwards, which is the order that gets a live region announced at all, and
 * which `StatusRegion`'s own header makes the rule.
 *
 * **NOTHING BRANCHES ON THE RESPONSE** (R2, L4). The handler awaits the call and reports success; it does not
 * read the body, does not look at the status, does not retry and adds no delay. Registration is
 * enumeration-safe at the server by design - the same 202 whether or not the address was already registered -
 * and a client that distinguished outcomes would reintroduce exactly the difference R2 removes.
 *
 * It holds `email`, `password` and a status in local `useState` (D9). L13's TanStack Query and Zustand are
 * still uninstalled after 42 tasks, and a one-shot public mutation has no cache to invalidate and no state to
 * share across screens - which is what both exist for. The first screen that needs server state across
 * screens should revisit L13 with a real consumer, the way task 43 revisited the design system's invitation.
 */
export function RegisterScreen(): ReactElement {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [status, setStatus] = useState<'idle' | 'submitting' | 'sent'>('idle');
  const [advisory, setAdvisory] = useState<'unchecked' | 'in-range' | 'out-of-range'>('unchecked');

  function submit(): void {
    const length = countCodePoints(password);
    setAdvisory(length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS
      ? 'in-range' : 'out-of-range');
    setStatus('submitting');
    // The advisory result is deliberately NOT consulted here: R1 sends the form either way.
    void createPublicAuthApi().POST('/accounts', { body: { email, password } })
      .then(() => setStatus('sent'));
  }

  return (
    <Screen>
      <Heading>{t('register.heading')}</Heading>
      <Form onSubmit={submit}>
        <EmailField label={t('register.emailLabel')} value={email} onChange={setEmail} />
        <PasswordField
          label={t('register.passwordLabel')}
          value={password}
          onChange={setPassword}
          aria-invalid={advisory === 'out-of-range' ? true : undefined}
        />
        <SubmitButton>{t('register.submit')}</SubmitButton>
      </Form>
      <StatusRegion>
        {status === 'idle' ? null : t(status === 'submitting' ? 'register.submitting' : 'register.success')}
      </StatusRegion>
    </Screen>
  );
}
