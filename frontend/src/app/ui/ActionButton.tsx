import { forwardRef, type ReactNode } from 'react';

/**
 * The one kind of control this application has today.
 *
 * **`disabled` IS NOT A PROP, AND THAT IS THE WHOLE POINT OF THIS FILE.** `SignInScreen` uses
 * `aria-disabled` instead, because a focused element that becomes `disabled` drops the keyboard user to
 * `<body>` - and on that screen the button is the only control there is, so the user would be stranded with
 * nothing focused and no way back. The real inertness comes from a ref the screen holds, not from the
 * attribute. A primitive that accepted `disabled`, or helpfully translated `aria-disabled` into it, would
 * reintroduce exactly that defect; the type is what prevents it.
 *
 * `type="button"` is set here and is not a prop. This used to read "there is no `<form>` in this bundle, and
 * a button that defaulted to `submit` inside one later would navigate" - TASK 43 BUILT THAT FORM, so the
 * first clause is false and the second is now the live reason. The form's own control is `SubmitButton`,
 * which sets `type="submit"` inside itself for the same rule-2 reason this file sets `button`: a consumer who
 * could pass `type` could turn an action into a navigation, or a submit into a dead control.
 */
export const ActionButton = forwardRef<HTMLButtonElement, {
  children: ReactNode;
  onClick?: () => void;
  'aria-describedby'?: string;
  'aria-disabled'?: boolean;
  'aria-busy'?: boolean;
}>(function ActionButton(
  { children, onClick, 'aria-describedby': describedBy, 'aria-disabled': ariaDisabled,
    'aria-busy': ariaBusy }, ref,
) {
  return (
    <button
      ref={ref}
      type="button"
      aria-describedby={describedBy}
      aria-disabled={ariaDisabled}
      aria-busy={ariaBusy}
      onClick={onClick}
      // One line, deliberately: a className broken across lines puts literal newlines into the DOM's
      // class attribute, and this layer is compared attribute-by-attribute across five account-failure
      // states (R30, task 31's suite). Identical whitespace would still pass, but unreadable diffs are a
      // poor trade for a line length.
      className="self-start rounded-md bg-accent px-4 py-2 text-step-0 font-medium text-text-inverse outline-offset-2 focus-visible:outline-2 focus-visible:outline-focus aria-disabled:opacity-70"
    >
      {children}
    </button>
  );
});
