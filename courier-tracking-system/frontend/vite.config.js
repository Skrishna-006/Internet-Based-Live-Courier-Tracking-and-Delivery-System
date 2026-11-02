/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Split React + ReactDOM into their own vendor chunk
          'vendor-react': ['react', 'react-dom'],
          // Split React Router into its own vendor chunk
          'vendor-router': ['react-router-dom'],
          // Split Leaflet map libraries (heavy, map-only pages load them)
          'vendor-leaflet': ['leaflet', 'react-leaflet'],
          // Split STOMP WebSocket client
          'vendor-stomp': ['@stomp/stompjs'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
});
