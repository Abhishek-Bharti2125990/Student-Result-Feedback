import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        react(),
        tailwindcss(),
    ],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        port: 5173,
        proxy: {
            // The API is called with relative URLs so that dev and production
            // behave the same. Proxying here also means the browser never makes
            // a cross-origin request, so CORS cannot break a demo.
            '/api': {
                target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
})
