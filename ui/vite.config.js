import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/alert-console/',
  build: {
    outDir: '../src/main/resources/static/alert-console',
    emptyOutDir: true,
    assetsDir: 'assets'
  },
  server: {
    port: 5173,
    proxy: {
      '/alert-console/api': {
        target: 'http://localhost:10009',
        changeOrigin: true
      }
    }
  }
})
