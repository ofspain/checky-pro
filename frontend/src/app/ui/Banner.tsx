import { forwardRef, type ReactNode } from 'react';

/**
 * The `<header>` banner landmark — the application's own chrome, above whatever screen is matched.
 *
 * **THE NINTH PRIMITIVE, AND THE NINTH IS WHAT THIS FILE HAS TO JUSTIFY.** `index.ts`'s pinned list said
 * eight and its comment said "a ninth must be added deliberately" - an invitation with a cost, not a
 * prohibition. Task 42 pays it because of a contradiction it could not otherwise resolve (its phase 3, F2):
 * the authenticated layout must hold a sign-out control, a control a keyboard user has to be able to FIND,
 * and finding it means a landmark. Rule 2 of this layer says `role` is set INSIDE a primitive and never
 * accepted from a consumer - so a landmark can exist only inside one. The alternative to this file was not a
 * cheaper landmark; it was no landmark. The two other ways out were both worse: `Surface` is a `<div>` and
 * would have given the chrome no semantics at all, and letting the layout render one lowercase tag of its
 * own would have been the first exception to §6e, whose entire value is that it has none.
 *
 * **IT SETS NO `role`, AND THAT IS NOT AN OVERSIGHT.** `<header>` maps to the `banner` role natively, and
 * redundant ARIA on an element that already carries the semantics is the worse of the two spellings. But the
 * native mapping is CONDITIONAL: a `<header>` inside `article`, `aside`, `main`, `nav` or `section` is a
 * generic group instead, silently. So the condition is part of this primitive's contract rather than an
 * assumption about how it is used, and task 42's suite asserts the ancestry rather than the tag - a later
 * task that nests the layout inside a screen would demote this landmark without changing one line here.
 *
 * It carries no copy (rule 3) and accepts no `className` or `style` (rule 1). The width, gutter and centring
 * mirror `Screen`'s so the chrome and the screen below it share one content column - and that is a
 * COINCIDENCE OF TWO LITERALS, not an enforced relationship (phase 8 I9, phase 7 F4). Change `Screen` to a
 * wider column and the banner stays at `max-w-prose`, every case passes, and the control sits off the
 * content edge. Each primitive owns its own width, so neither pin is wrong; what is missing is a pin on the
 * pair, and task 42's phase 10 adds one.
 *
 * **IT HAS NO ACCESSIBLE NAME, DELIBERATELY** (phase 8 I7). A landmark's name is what distinguishes it from
 * its siblings, and there is exactly one banner in the document (`Screen` is the one `<main>`, and nothing
 * else here is a landmark), so a screen reader announcing "banner" is unambiguous. Naming it would mean
 * copy - the application's name - which is a catalogue key and an owner's decision, and D1 froze this chrome
 * as a sign-out control and nothing else. The task that adds navigation to it should revisit this.
 */
export const Banner = forwardRef<HTMLElement, { children: ReactNode }>(
  function Banner({ children }, ref) {
    return (
      <header ref={ref} className="mx-auto flex w-full max-w-prose items-center justify-end gap-4 px-6 pt-6">
        {children}
      </header>
    );
  },
);
