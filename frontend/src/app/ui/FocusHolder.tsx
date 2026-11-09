import { forwardRef, type ReactNode } from 'react';

/**
 * A container that can receive focus programmatically but is not in the tab order.
 *
 * ONE CONSUMER, AND IT EXISTS FOR ONE DEFECT: `app/RateLimitNotice.tsx` dismisses itself, which unmounts the
 * button the visitor just pressed. Without somewhere to put focus, the browser drops it to `<body>` and a
 * keyboard user loses their place entirely - the failure L11 names when it says "managed focus". The notice
 * holds a ref to this element and focuses it as the button goes away.
 *
 * `tabIndex={-1}` is set here and is not a prop: focusable-but-not-tabbable is the entire contract, and a
 * consumer that could set `0` would insert a tab stop that holds nothing.
 *
 * `children` is REQUIRED (phase 3, F11 proposed making it optional, and the premise was wrong): the notice's
 * status region is rendered unconditionally inside this holder - only the button is conditional - so there is
 * no state in which this wraps nothing. A focus holder with no content is a bug the type can refuse.
 */
export const FocusHolder = forwardRef<HTMLDivElement, { children: ReactNode }>(
  function FocusHolder({ children }, ref) {
    return (
      <div ref={ref} tabIndex={-1} className="flex flex-col gap-2 outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus">
        {children}
      </div>
    );
  },
);
