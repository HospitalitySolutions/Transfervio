import { createLogger, defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxy failures must not persist public invitation capabilities in dev logs.
const logger = createLogger();
for (const level of ["info", "warn", "warnOnce", "error"] as const) {
  const original = logger[level].bind(logger);
  logger[level] = (message, options) =>
    original(
      message.replace(
        /((?:\/public)?\/invitations\/|\/i\/)[A-Za-z0-9_-]{32,}/g,
        "$1[redacted]",
      ),
      options,
    );
}
export default defineConfig({
  customLogger: logger,
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: Number(process.env.WEB_PORT || 5174),
    strictPort: true,
    proxy: Object.fromEntries(
      ["/api", "/v1", "/public", "/auth", "/actuator"].map((path) => [
        path,
        `http://127.0.0.1:${process.env.API_PORT || 8091}`,
      ]),
    ),
  },
});
