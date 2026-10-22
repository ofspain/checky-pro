import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { AppShell } from './AppShell';

/** Mounts the shell into `container`. Throws if it is missing, so a broken page fails loudly
 * instead of rendering nothing. */
export function mountShell(container: HTMLElement | null): Root {
  if (container === null) {
    throw new Error('mountShell: root element #root not found');
  }
  const root = createRoot(container);
  root.render(
    <StrictMode>
      <AppShell />
    </StrictMode>,
  );
  return root;
}
