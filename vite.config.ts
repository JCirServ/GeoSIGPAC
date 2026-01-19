import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
  plugins: [react()],
  // Base relativa indispensable para file:///android_asset/
  base: './', 
  define: {
    'process.env.API_KEY': JSON.stringify(process.env.API_KEY),
    'process.env': {
      API_KEY: process.env.API_KEY
    }
  },
  build: {
    outDir: 'android/app/src/main/assets',
    emptyOutDir: true,
    target: 'es2020',
    cssCodeSplit: false, // Unifica el CSS en un solo archivo
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
      },
      output: {
        // Forzamos un solo bundle para evitar errores de carga de chunks en Android
        manualChunks: undefined,
        inlineDynamicImports: true,
        entryFileNames: '[name].js',
        chunkFileNames: '[name].js',
        assetFileNames: '[name].[ext]'
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './'),
    },
  },
});