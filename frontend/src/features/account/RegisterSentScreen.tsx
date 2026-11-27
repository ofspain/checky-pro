import { useEffect, useRef, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { Heading, Screen, Text } from '../../app/ui';

/**
 * The registration acknowledgement — R2, and the screen that finally fills `register/sent`.
 *
 * `register/sent` has been a declared public path with no element since task 16. Task 43 built the form that
 * leads here and deliberately did NOT navigate, for a reason it wrote down in its own suite: navigating would
 * have handed the visitor "a declared path with no element". That was a conditional decision, and the
 * condition was this file not existing. It exists, so task 43's `does not navigate` case is inverted here
 * rather than overruled — a closed task named the circumstance under which its own choice should flip.
 *
 * **IDENTICAL IS THE WHOLE REQUIREMENT, AND IT IS A PROPERTY OF HAVING NOTHING TO BE IDENTICAL ABOUT.** R2
 * and L4 say a new address and an already-registered one get the same acknowledgement. The server already
 * guarantees its half: `POST /accounts` answers 202 either way and discloses nothing. The client's half is
 * kept by a screen with NO INPUTS — no props, no router state, no search params, no storage read, no call of
 * its own. There is no value in scope that could differ between the two cases, so identical output is not a
 * behaviour this screen implements and a suite checks; it is the only thing it can do. A screen that took
 * even one prop from the submit would need a case per value; this one needs a case that it takes none.
 *
 * **THE CONTRACT'S `message` IS REQUIRED, AND DELIBERATELY UNREAD** (D2). `RegistrationAcknowledgement` is
 * `required: [message]` with `message: string` (`contracts/api/auth.yaml`), so the server always sends a
 * sentence and this client always ignores it. That is not an oversight to be tidied later: L11 locks every
 * user-facing string to the catalogue, and a server-supplied sentence has no catalogue key, which means no
 * `t()` call, no D7 case measuring it, and no way for the owner to change the words without a server deploy.
 * Rendering it would also move R2's guarantee off the client — the identical-copy claim would become the
 * server's to keep, and the named suite could no longer prove it. **Do not "fix" this by rendering the body.**
 * If server-controlled copy is ever wanted, the field is already there and the change is one line here.
 *
 * **THE SENTENCE IS `register.success`, BORROWED FROM THE FORM'S OWN GROUP**, and the borrowing is the point.
 * Task 43 reserved those words for this screen in the catalogue itself. Phase 3 proposed a fresh
 * `registerSent.explanation` instead; the freeze rejected it, because the new pair would have left
 * `register.success` rendered nowhere while two pins still named it — one of them pinning its exact words —
 * and because a second sentence with the same job is a second thing to keep in step with R2.
 *
 * **THE FOCUS MOVE IS THE ANNOUNCEMENT, which is why there is no `StatusRegion` here.** This screen is
 * arrived at by a client-side navigation, so the browser moves nothing: focus would stay on the submit button
 * that has just been unmounted, and a screen-reader user would be told nothing at all. Moving focus to the
 * heading announces the screen by name, which is what someone who just submitted a form needs to hear. A live
 * region in addition would announce the same words a second time. It is the SECOND screen to move focus, not
 * the first — `HomeScreen` has done it since task 174, and is also a navigation arrival — so the I9 guard's
 * four-screen loop keeps its reason and gains a named exception rather than a fifth entry.
 *
 * The empty dependency array and the `?.` carry the same weight they do in `HomeScreen`: `mountShell.tsx`
 * renders inside `<StrictMode>`, so this effect runs twice in development and `focus()` on an already-focused
 * element is a no-op, which is why running twice is safe and why a case asserts it under StrictMode instead of
 * reasoning about it. The ref is null on the first render and after unmount, so `?.` is the real null case.
 *
 * `tabIndex` is NOT passed from here. It lives inside `Heading` (`app/ui/index.ts` rule 2, task 174 D1).
 */
export function RegisterSentScreen(): ReactElement {
  const { t } = useTranslation();
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <Screen>
      <Heading ref={headingRef}>{t('registerSent.heading')}</Heading>
      <Text>{t('register.success')}</Text>
    </Screen>
  );
}
