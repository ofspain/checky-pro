import { forwardRef, type ReactNode } from 'react';

/**
 * A paragraph of body copy.
 *
 * `id` IS A PROP FOR ONE REASON: `aria-describedby`. The sign-in screen's explanation carries
 * `id="sign-in-explanation"` so the button can point at it - a keyboard user tabbing straight to the control
 * would otherwise never hear that they are about to leave for a server-hosted page. Dropping the `id` here
 * would silently sever that association, which is the kind of regression that looks like tidying.
 */
export const Text = forwardRef<HTMLParagraphElement, { children: ReactNode; id?: string }>(
  function Text({ children, id }, ref) {
    return (
      <p ref={ref} id={id} className="text-step-0 leading-relaxed text-text">
        {children}
      </p>
    );
  },
);
