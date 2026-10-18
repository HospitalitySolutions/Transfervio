import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: Number(process.env.WEB_PORT || 5174), strictPort: true,
    proxy: Object.fromEntries(['/api', '/v1', '/public', '/auth', '/actuator'].map(path => [path, `http://127.0.0.1:${process.env.API_PORT || 8091}`])) },
});
