import { forwardRef, type ReactNode } from 'react';

/**
 * The button that submits a form — `ActionButton`'s sibling, and `ActionButton`'s own comment predicted it.
 *
 * That file says: *"`type="button"` is set here and is not a prop: there is no `<form>` in this bundle, and a
 * button that defaulted to `submit` inside one later would navigate."* Task 43 is the later. The first half of
 * the sentence is now false and has been corrected there; the second half is why this is a separate file
 * rather than a `type` prop on that one. A control's `type` is its contract (rule 2): a consumer who could
 * pass `type` could turn a submit into a dead button, or an action into a navigation.
 *
 * **`type="submit"` IS THE FEATURE, NOT A DETAIL.** It is what makes Enter inside a field submit the form -
 * the implicit submission every keyboard and screen-reader user expects (L11, AC9) - which an `onClick` on a
 * `type="button"` control does not give. It carries no `onClick` for the same reason: the form owns the
 * submit, and a button that both submitted and ran a handler of its own would fire twice.
 *
 * It wears `ActionButton`'s tokens deliberately, so the application has one button appearance rather than
 * two. Both files are pinned to the same token list by the design-system suite, which is the pair pin task
 * 42 learned to ask for: two literals that happen to agree are not the same claim as two that must.
 */
export const SubmitButton = forwardRef<HTMLButtonElement, { children: ReactNode }>(
  function SubmitButton({ children }, ref) {
    return (
      <button
        ref={ref}
        type="submit"
        className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium text-text-inverse outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus"
      >
        {children}
      </button>
    );
  },
);
