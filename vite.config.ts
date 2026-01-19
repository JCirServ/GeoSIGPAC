
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
  plugins: [react()],
  // Base relativa './' permite que los assets se carguen correctamente
  // bajo cualquier prefijo de ruta que configure el WebViewAssetLoader.
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
    cssCodeSplit: false,
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
      },
      output: {
        // Un solo archivo JS para evitar problemas de latencia en la carga de módulos en Android WebView
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
