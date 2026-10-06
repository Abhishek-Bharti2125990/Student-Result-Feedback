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
                // Must match `server.port` in the backend's application.yml.
                // If it does not, the proxy silently forwards to whatever else
                // owns that port - on a machine running Jenkins on 8080 that is
                // Jenkins, which answers a login POST with its own 403 "No
                // valid crumb was included in the request" and never reaches
                // Spring Security at all.
                target: process.env.VITE_API_TARGET ?? 'http://localhost:8081',
                changeOrigin: true,
            },
        },
    },
})
