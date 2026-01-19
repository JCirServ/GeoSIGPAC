
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default defineConfig({
  plugins: [react()],
  // Base relativa crítica para que file:///android_asset/ funcione correctamente
  base: './', 
  define: {
    // Inyecta la API KEY y previene errores de "process is not defined" en el navegador/WebView
    'process.env.API_KEY': JSON.stringify(process.env.API_KEY),
    'process.env': {
      API_KEY: process.env.API_KEY
    }
  },
  build: {
    outDir: 'android/app/src/main/assets',
    emptyOutDir: true,
    target: 'es2020', // Asegura compatibilidad con WebViews modernos
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
      },
      output: {
        // Estructura de archivos plana para evitar problemas de resolución de rutas en Android
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]'
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './'),
    },
  },
});
