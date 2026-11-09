/**
 * The design system's primitive layer — **the only place in this bundle that renders an HTML element.**
 *
 * Task 173 installed the design system O2 resolved on 2026-10-10 (Tailwind CSS plus unstyled accessible
 * primitives) after 27 units had shipped against an unstyled tree. These eight components are that layer, and
 * they are OURS rather than a vendor's: O2 says "headless/unstyled components (e.g. Radix UI)", and `e.g.` is
 * an example. No screen in this application renders a dialog, dropdown, popover, menu, tooltip, tab set, form
 * field or table - the composite controls such a library exists to make accessible - so installing one now
 * would ship unused code. The first screen that needs one (MFA enrollment, R11-R13, is the likely first)
 * should install it with a real consumer, and should start by reading this paragraph.
 *
 * THREE RULES HOLD THE LAYER TOGETHER, and each is enforced by a type rather than by a convention:
 *
 *   1. **No primitive accepts `className` or `style`.** Not optional, not ignored - absent from the type. A
 *      screen that tried to style around the layer fails `tsc`, which is a stronger instrument than any scan.
 *   2. **No primitive accepts the attribute that is its own contract.** `role`, `aria-live`, `tabIndex`,
 *      `type="button"` and the heading level are set inside. An ARIA contract a consumer can override is not
 *      a contract.
 *   3. **No primitive carries user-facing copy.** Every word still comes from the catalogue through `t()`,
 *      which the consuming screen calls (L11, agents.md line 97).
 *
 * `screensRenderThroughDesignSystemPrimitivesOnly` is what holds the other half: that no screen reaches past
 * this layer to an element of its own.
 */
export { ActionButton } from './ActionButton';
export { FocusHolder } from './FocusHolder';
export { Heading } from './Heading';
export { Screen } from './Screen';
export { StatusRegion } from './StatusRegion';
export { Surface } from './Surface';
export { Text } from './Text';
export { TextLink } from './TextLink';
