import { useEffect, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router';
import { listenForSignOut } from '../api/crossTabSignOut';
import { endSession } from '../auth/endSession';
import { RedirectToSignIn } from '../auth/RedirectToSignIn';
import { signOut } from '../auth/signOut';
import { ActionButton, Banner } from './ui';

/**
 * The authenticated layout — task 42, and R28's positive clause: *"a session renders the shell and the
 * authenticated routes."*
 *
 * **WHAT IT IS FOR, GIVEN THAT A SHELL ALREADY EXISTED.** `AppShell` wraps every route, public or not, and
 * has since task 1 - so the task statement was already literally true and the unit would have been vacuous
 * read that way. What did not exist was anything BETWEEN the gate and the eleven authenticated paths: ten of
 * them render an empty document, `/app/home` hung directly off the gate, and tasks 43 onward had nothing to
 * nest in while L12 forbids them from replacing the Phase 1 shell to make one. This is that layer.
 *
 * **IT CARRIES NO NAVIGATION, DELIBERATELY** (phase 3 F1, D1). Ten of the eleven paths render nothing, and a
 * link to a dead end is worse than no link. The first task to own a destination worth linking to adds a
 * link; the route shape means it will not have to restructure anything to do it.
 *
 * **IT HOLDS THE FIRST CALLER THE SIGN-OUT PATH HAS EVER HAD** (owner decision, OQ-T42-2, closed
 * 2026-11-14). `signOut()` (task 35), `endSession()` (task 36), the signed-out landing (task 37) and
 * cross-tab propagation (task 39) were all built and unreachable - `endSession()` had no production caller
 * at all, which is OQ-T36-5, carried for seven consecutive tasks. Both halves are wired here:
 *
 *   - the BUTTON calls `endSession()` and nothing else. It does not call `signOut()`, does not take the id
 *     token, does not broadcast and does not build a URL, because `endSession()` composes all four and
 *     **the order is its criterion** (`auth/endSession.ts:89-96`). R57 is satisfied inside it too: the
 *     end-session navigation is served by SAS, never by this shell.
 *   - the LISTENER is this tab's answer when a SIBLING signs out. Its callback calls `signOut()` and not
 *     `endSession()`, which `api/crossTabSignOut.ts:130-133` prescribes: *"Signing out here must NOT
 *     broadcast, or two tabs would ping-pong - which is why `signOut()` sets the latch and `endSession()`
 *     does the broadcasting"*. The SAS session is ended once, by the tab the visitor acted in.
 *
 * **WHY A RECEIVING TAB RENDERS `RedirectToSignIn` RATHER THAN NAVIGATING** (D11). The gate is a read at
 * render, not a subscription (OQ-T40-1), and it is this component's PARENT - so no state held here can make
 * it re-ask its question, and `RequireSession.tsx` is not this task's to edit. Leaving the visitor on the
 * screen was not an option either: R22 forbids showing a screen to someone who no longer has a session. So
 * the flag swaps the outlet for the redirect, and the destination, the return target and the
 * replace-vs-push decision all stay with the component task 41 built to own them. A `useNavigate` here
 * would have been a second copy of three decisions that already have a home.
 *
 * **THE HANDLER IS A CALL, NOT A REFERENCE** (phase 8 I4, phase 7 F3). `onClick={endSession}` type-checks
 * today, because `ActionButton` declares `onClick?: () => void` and `endSession(): void` takes nothing, and
 * React would simply hand it a `MouseEvent` that the signature drops. It stops being harmless the moment
 * `endSession` grows an OPTIONAL first parameter - `endSession(hint?: string)` is still assignable to
 * `() => void`, so `tsc` would stay silent while the click event arrived as the hint. The closure costs one
 * allocation per render and removes the whole class of bug; AC13's source case pins the shape.
 *
 * It renders no `Screen` and no `Heading`: `Screen` is the `<main>` landmark and nests nowhere, and the
 * matched screen below already renders the page's only `<h1>` (phase 3 F3). It takes no focus, holds no ref
 * and renders no non-negative `tabIndex` (F4) - task 174 made every `<h1>` focusable and wrote the guard
 * that would catch this one taking focus.
 */
export function AuthenticatedLayout(): ReactElement {
  const { t } = useTranslation();
  // ONE-WAY, AND SAFE ONLY BECAUSE THE REDIRECT UNMOUNTS THIS LAYOUT (phase 8 I10, phase 7 F7). Nothing
  // clears the latch: `RedirectToSignIn` navigates, the tree remounts under the gate with no session, and
  // this component is gone. A later flow that re-mounted the layout WITHOUT a tree remount - signing in
  // again in place - would arrive with the latch still set and redirect straight back out. Whoever builds
  // that clears it here; today there is no such path, and a reset with no caller is a reset nothing holds.
  const [siblingSignedOut, setSiblingSignedOut] = useState(false);

  useEffect(() => {
    // `signOut()` first, so this tab is clean before anything re-renders, and the flag second. The listener
    // is closed on unmount: `listenForSignOut` hands back `{ close() }`, it is idempotent, and it closes
    // only a channel it opened itself.
    const listening = listenForSignOut(() => {
      signOut();
      setSiblingSignedOut(true);
    });
    return () => listening.close();
  }, []);

  if (siblingSignedOut) return <RedirectToSignIn />;

  return (
    <>
      <Banner>
        <ActionButton onClick={() => endSession()}>{t('shell.signOut')}</ActionButton>
      </Banner>
      <Outlet />
    </>
  );
}
