import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  workers: 1,
  timeout: 90000,
  reporter: "list",
  use: {
    baseURL: process.env.BROWSER_BASE_URL || "http://localhost:5174",
    channel: "chrome",
    headless: true,
    viewport: { width: 1440, height: 1000 },
  },
  outputDir: "../../.local-data/browser-tests",
});
