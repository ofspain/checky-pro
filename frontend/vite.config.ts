import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

// One config for dev, build and test. The SPA lives under /app (L14) and is reached only through
// the edge, which owns every backend route (L9), so there is no server.proxy here.
export default defineConfig({
  base: '/app/',
  plugins: [
    react(),
    // injectManifest, not generateSW: the worker is written by hand in src/pwa so it can import the
    // navigation denylist (T09) and the request policy (T11) instead of restating them (L17, R57).
    // No manifest is emitted - an installable PWA is not this task. Registration is hand-written in
    // src/main.tsx, so the plugin injects none.
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src/pwa',
      filename: 'serviceWorker.ts',
      injectRegister: null,
      manifest: false,
    }),
  ],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
  },
});
