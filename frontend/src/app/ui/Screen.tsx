import { forwardRef, type ReactNode } from 'react';

/**
 * The `<main>` landmark, which is every screen's outermost element.
 *
 * FOUR CONSUMERS since task 174: the sign-in, callback, signed-out and home screens. The landmark is what a screen-reader user
 * navigates to, so it is set here and cannot be overridden - `publicRouteSetIsExactlyTheDeclaredSet.test.tsx`
 * pins "one landmark holding a level-one heading, a paragraph and the way back, in that order", and this
 * primitive is what keeps the first word of that sentence true.
 *
 * It nests nowhere: `Surface` is the shell's `<div>` and sits ABOVE the router, so there is exactly one
 * `<main>` in the document at any time. A second one would be invalid and would split the landmark.
 */
export const Screen = forwardRef<HTMLElement, { children: ReactNode }>(
  function Screen({ children }, ref) {
    return (
      <main ref={ref} className="mx-auto flex max-w-prose flex-col gap-4 px-6 py-12">
        {children}
      </main>
    );
  },
);
