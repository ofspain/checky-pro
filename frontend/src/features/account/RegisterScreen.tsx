import { useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { createPublicAuthApi } from '../../api/client';
import { REGISTER_SENT_PATH } from '../../app/routes';
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
 * **IT NAVIGATES NOW, AND TASK 43 IS WHAT SAYS IT MAY** (task 47; was D1, OQ-T43-5). This paragraph read "IT
 * ANNOUNCES ITS RESULT AND DOES NOT NAVIGATE" for four tasks, and the reason it gave was conditional: task 47
 * "owns both the acknowledgement screen and `register/sent` - which is a declared path with no element today,
 * so navigating there would hand the visitor a blank document". The condition was the path having no element.
 * `RegisterSentScreen` is that element, so the decision is spent rather than reversed, and task 43's
 * `does not navigate` case is INVERTED in its own file rather than deleted - a case whose premise a later task
 * was always going to retire.
 *
 * The navigation is ABSOLUTE and REPLACES. Absolute because a relative `navigate('register/sent')` resolves
 * against this route and would land on `/register/register/sent`; `CallbackScreen` writes `/${SIGN_IN_PATH}`
 * for the same reason. `replace` because Back from the acknowledgement must not return to a filled form whose
 * resubmission posts the same address again - pushing would turn the browser's Back button into a retry
 * control nobody designed.
 *
 * **THE `StatusRegion` STAYS, AND IT NOW SAYS ONE THING RATHER THAN TWO.** It is still mounted from the start
 * and filled afterwards, which is the order that gets a live region announced at all and which
 * `StatusRegion`'s own header makes the rule. What it no longer holds is the success sentence: that is
 * `register.success`, rendered by `RegisterSentScreen` one navigation away. The in-flight word is the only
 * thing this screen still announces, and the live region is the only thing that can announce it, because a
 * visitor who cannot see the control needs to know the press was heard.
 *
 * **NOTHING BRANCHES ON THE RESPONSE** (R2, L4), and that is still exactly true after the navigation landed.
 * The handler awaits the call and leaves; it does not read the body, does not look at the status, does not
 * retry and adds no delay. **A navigation taken on SETTLE is not a branch on the RESPONSE** - the same single
 * destination is reached for a 202, a 409 and a 500, which is what the three rows of task 43's AC11 assert and
 * why those rows were re-aimed at the destination rather than retired. Registration is enumeration-safe at the
 * server by design - the same 202 whether or not the address was already registered - and a client that
 * distinguished outcomes would reintroduce exactly the difference R2 removes.
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
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [status, setStatus] = useState<'idle' | 'submitting'>('idle');
  const [advisory, setAdvisory] = useState<'unchecked' | 'in-range' | 'out-of-range'>('unchecked');

  function submit(): void {
    const length = countCodePoints(password);
    setAdvisory(length >= ADVISORY_MINIMUM_CODE_POINTS && length <= ADVISORY_MAXIMUM_CODE_POINTS
      ? 'in-range' : 'out-of-range');
    setStatus('submitting');
    // The advisory result is deliberately NOT consulted here: R1 sends the form either way.
    // `parseAs: 'stream'` IS TASK 175'S FIX, AND R2 IS WHY. Without it a `202` whose body is not valid
    // JSON made `openapi-fetch` reject, the arm below returned the form to idle, and the visitor saw
    // NOTHING HAPPEN - for a registration the server had accepted and emailed. R2 requires "the same
    // 'check your email' screen ... with identical copy and layout", and showing no screen is not showing
    // it. This screen reads nothing from the reply, so there is nothing the parse was for.
    void createPublicAuthApi().POST('/accounts', { body: { email, password }, parseAs: 'stream' })
      .then(() => navigate(`/${REGISTER_SENT_PATH}`, { replace: true }), () => setStatus('idle'));
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
      {/* ONE WORD NOW, NOT TWO. The success sentence moved to `RegisterSentScreen`; what is left is the
          in-flight word, and the region still renders unconditionally so the SAME node is filled rather
          than replaced - a region swapped between renders is a new one to the accessibility tree, and a
          case in task 43's suite asserts that identity. */}
      <StatusRegion>{status === 'submitting' ? t('register.submitting') : null}</StatusRegion>
    </Screen>
  );
}
