import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// One config for dev, build and test. The SPA lives under /app (L14) and is reached only through
// the edge, which owns every backend route (L9), so there is no server.proxy here.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
  },
});
