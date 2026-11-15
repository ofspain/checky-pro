import { useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { createPublicAuthApi } from '../../api/client';
import { EmailField, Form, Heading, PasswordField, Screen, StatusRegion, SubmitButton, Text } from '../../app/ui';
import { ADVISORY_MAXIMUM_CODE_POINTS, ADVISORY_MINIMUM_CODE_POINTS, countCodePoints } from './passwordLength';

/**
 * The registration screen — R1, and the first screen in `features/`.
 *
 * **A REJECTED SEND RETURNS THE FORM TO ITS IDLE STATE** (phase 8, I7; phase 7, F2). `openapi-fetch`
 * RESOLVES for every HTTP answer, which is why a 202, a 409 and a 500 all reach the success branch - the
 * response-agnostic design R2 and L4 require. It REJECTS when the transport itself throws: offline, DNS
 * failure, a blocked request. The first version of this file had no handler for that, so the rejection
 * escaped unhandled and the live region stayed on "Creating your account…" forever.
 *
 * `'idle'` is the state that needs no new word. It empties the live region, leaves both fields filled and
 * the control usable, and claims nothing: a visitor can press the button again, which is the only thing
 * that can help when the request never left the tab. The alternatives were worse - `'sent'` would report an
 * account that was never requested, and a failure SENTENCE would be copy no owner has approved, while D7
 * froze this screen's key set at six. The silence is the gap, it is carried as OQ-T43-9, and it is
 * strictly better than a screen that hangs.
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
/**
 * The guidance element's id, which the password field points at with `aria-describedby` (task 45).
 *
 * A CONSTANT RATHER THAN `useId()` BECAUSE TWO SIBLINGS MUST AGREE ON IT, and because the screen renders
 * once per route - `SignInScreen` fixes `sign-in-explanation` the same way for the same reason. A
 * generated id would be correct too, but it would have to be threaded through both elements, and the
 * value a test reads would then be an implementation detail rather than a name.
 */
const GUIDANCE_ID = 'register-password-guidance';

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
      .then(() => setStatus('sent'), () => setStatus('idle'));
  }

  // THE BOUNDS COME FROM THE MODULE THAT ENFORCES THEM, never from the sentence. A catalogue string with
  // "12 to 128" typed into it renders identically to this one and goes stale the day a bound moves, which
  // is why the numbers are interpolated and why a case asserts the rendered ones ARE these constants.
  const guidance = t('register.passwordGuidance', {
    min: ADVISORY_MINIMUM_CODE_POINTS,
    max: ADVISORY_MAXIMUM_CODE_POINTS,
  });

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
          aria-describedby={advisory === 'out-of-range' ? GUIDANCE_ID : undefined}
        />
        {/* ONE CONDITION DRIVES BOTH, deliberately. The mark and the sentence that explains it must appear
            and disappear together: a field marked invalid with nothing to read is what task 43 shipped and
            what this task exists to finish, and a sentence with no `aria-describedby` pointing at it is
            the same defect for anyone who cannot see the layout. Between the field and the control so the
            reading order matches the visual one. */}
        {advisory === 'out-of-range' ? <Text id={GUIDANCE_ID}>{guidance}</Text> : null}
        <SubmitButton>{t('register.submit')}</SubmitButton>
      </Form>
      <StatusRegion>
        {status === 'idle' ? null : t(status === 'submitting' ? 'register.submitting' : 'register.success')}
      </StatusRegion>
    </Screen>
  );
}
