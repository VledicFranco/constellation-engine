import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  root: '.',
  publicDir: false,
  plugins: [react()],
  build: {
    outDir: '../modules/http-api/src/main/resources/dashboard',
    emptyOutDir: false,
    rollupOptions: {
      input: {
        main: 'index.html',
      },
      output: {
        entryFileNames: 'static/js/[name].js',
        chunkFileNames: 'static/js/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          if (assetInfo.name?.endsWith('.css')) {
            return 'static/css/[name][extname]';
          }
          return 'static/assets/[name]-[hash][extname]';
        },
        manualChunks: {
          react: ['react', 'react-dom'],
          zustand: ['zustand'],
          monaco: ['monaco-editor'],
        },
      },
    },
    sourcemap: true,
    minify: 'esbuild',
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/lsp': {
        target: 'ws://localhost:8080',
        ws: true,
      },
      '/pipelines': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/dashboard': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
});
