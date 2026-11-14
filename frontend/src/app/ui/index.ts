/**
 * The design system's primitive layer — **the only place in this bundle that renders an HTML element.**
 *
 * Task 173 installed the design system O2 resolved on 2026-10-10 (Tailwind CSS plus unstyled accessible
 * primitives) after 27 units had shipped against an unstyled tree. These components are that layer, and
 * they are OURS rather than a vendor's: O2 says "headless/unstyled components (e.g. Radix UI)", and `e.g.` is
 * an example. This paragraph used to say that no screen renders a dialog, dropdown, popover, menu, tooltip,
 * tab set, FORM FIELD or table, so installing such a library would ship unused code. **TASK 43 BUILT THE
 * FORM FIELDS**, and the clause is corrected rather than deleted because the conclusion survives and the
 * reason narrowed: a text field is the one item on that list a library is NOT needed for. Its accessibility
 * is a `<label for>` and an `id` - which `EmailField` and `PasswordField` set inside themselves - and not
 * the focus management, keyboard choreography and ARIA bookkeeping a dialog or a combobox needs. Nothing in
 * this application renders any of the remaining seven. The first screen that needs one (MFA enrollment,
 * R11-R13, is the likely first) should install it with a real consumer, and should start by reading this
 * paragraph.
 *
 * THREE RULES HOLD THE LAYER TOGETHER, and each is enforced by a type rather than by a convention:
 *
 *   1. **No primitive accepts `className` or `style`.** Not optional, not ignored - absent from the type. A
 *      screen that tried to style around the layer fails `tsc`, which is a stronger instrument than any scan.
 *   2. **No primitive accepts the attribute that is its own contract.** `role`, `aria-live`, `tabIndex`,
 *      `type="button"` and the heading level are set inside. An ARIA contract a consumer can override is not
 *      a contract. **TASK 43 ADDED FIVE MORE**, all of them inside: `type="email"`, `type="password"`,
 *      `type="submit"`, the `htmlFor`/`id` pair that associates a label with its control, and `noValidate`.
 *      The field pair is the reason a primitive may render TWO elements for the first time - a control's
 *      label is part of the control, and handing the ids to a screen would hand it the association. The
 *      design-system suite carries that as a named, SIZED exception (three rows), not as a relaxed rule.
 *      What a consumer may still pass is a STATE it computes: `ActionButton`'s `aria-disabled` and
 *      `PasswordField`'s `aria-invalid` are props for the same reason `type` is not.
 *   3. **No primitive carries user-facing copy.** Every word still comes from the catalogue through `t()`,
 *      which the consuming screen calls (L11, agents.md line 97).
 *
 * `screensRenderThroughDesignSystemPrimitivesOnly` is what holds the other half: that no screen reaches past
 * this layer to an element of its own.
 */
export { ActionButton } from './ActionButton';
export { Banner } from './Banner';
export { EmailField } from './EmailField';
export { FocusHolder } from './FocusHolder';
export { Form } from './Form';
export { Heading } from './Heading';
export { PasswordField } from './PasswordField';
export { Screen } from './Screen';
export { StatusRegion } from './StatusRegion';
export { SubmitButton } from './SubmitButton';
export { Surface } from './Surface';
export { Text } from './Text';
export { TextLink } from './TextLink';
