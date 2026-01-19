
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

// Fix: Define __dirname for ESM environments as it is not available by default
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
  plugins: [react()],
  // Importante: base relativa para que el WebView encuentre los archivos con file:///android_asset/
  base: './', 
  build: {
    // Redirigimos la salida directamente a los assets de Android
    outDir: 'android/app/src/main/assets',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        // Fix: __dirname reference
        main: resolve(__dirname, 'index.html'),
      },
      output: {
        // Mantenemos nombres simples para evitar problemas de rutas en Android antiguo
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]'
      }
    }
  },
  resolve: {
    alias: {
      // Fix: __dirname reference
      '@': resolve(__dirname, './'),
    },
  },
});
