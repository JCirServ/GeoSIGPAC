
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
    cssCodeSplit: false, // Mantenemos CSS junto para evitar parpadeos
    chunkSizeWarningLimit: 1000, // Aumentamos el límite de advertencia razonablemente
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
      },
      output: {
        // Habilitamos Code Splitting para Android (mejor rendimiento de carga)
        manualChunks: {
          vendor: ['react', 'react-dom', 'lucide-react'],
          maps: ['jszip', '@google/genai']
        },
        entryFileNames: '[name].js',
        chunkFileNames: '[name]-[hash].js',
        assetFileNames: '[name]-[hash].[ext]'
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, './'),
    },
  },
});
