import { forwardRef, type ReactNode } from 'react';
import { Link } from 'react-router';

/**
 * An in-app link, which is react-router's `<Link>` and therefore a client-side navigation.
 *
 * `href` IS NOT A PROP. Every link in this bundle today goes to a route inside `/app` - the signed-out
 * screen's way back to sign-in is the only one - and `to` is what keeps the basename handling with the router
 * instead of with the caller. An external link is a different thing with different obligations (`rel`,
 * `target`, and a visible indication that the visitor is leaving), so it gets its own primitive when something
 * actually needs one.
 */
export const TextLink = forwardRef<HTMLAnchorElement, { children: ReactNode; to: string }>(
  function TextLink({ children, to }, ref) {
    return (
      <Link
        ref={ref}
        to={to}
        className="self-start text-step-0 font-medium text-accent underline underline-offset-4 outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus"
      >
        {children}
      </Link>
    );
  },
);
