import { StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { AppShell } from './AppShell';
import { createAppRouter } from './routes';

/** Mounts the shell into `container`. Throws if it is missing, so a broken page fails loudly
 * instead of rendering nothing. */
export function mountShell(container: HTMLElement | null): Root {
  if (container === null) {
    throw new Error('mountShell: root element #root not found');
  }
  const root = createRoot(container);
  root.render(
    <StrictMode>
      <AppShell>
        <RouterProvider router={createAppRouter()} />
      </AppShell>
    </StrictMode>,
  );
  return root;
}
