import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
  // The Android WebView serves these files from a local virtual HTTPS origin.
  base: './',
  publicDir: 'public',
  build: {
    outDir: resolve(currentDir, '../app/src/main/assets/hwpviewer'),
    emptyOutDir: true,
    sourcemap: false,
    target: 'es2022',
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
});
