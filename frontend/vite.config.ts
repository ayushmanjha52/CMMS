import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev the API is proxied, so the browser sees one origin — same as production, where
// Spring Boot serves this bundle. That keeps the SameSite=Strict refresh cookie working.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.API_URL ?? 'http://localhost:8080',
    },
  },
});
