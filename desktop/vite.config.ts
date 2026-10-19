import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
  },
  envPrefix: ['VITE_', 'TAURI_'],
  build: {
    target: 'esnext',
    minify: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        dock: resolve(__dirname, 'dock.html'),
      },
    },
  },
});

