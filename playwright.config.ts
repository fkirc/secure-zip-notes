import { defineConfig, devices } from "@playwright/test";
import { readFileSync } from "node:fs";

const version = (JSON.parse(readFileSync("package.json", "utf8")) as { version: string }).version;

const ci = !!process.env["CI"];

export default defineConfig({
  testDir: "tests/e2e",
  fullyParallel: true,
  forbidOnly: ci,
  retries: 0,
  reporter: ci ? [["list"], ["html", { open: "never" }]] : "list",
  use: { trace: "off" },
  webServer: {
    command: "node scripts/serve.ts --port 4173",
    url: `http://localhost:4173/secure-zip-notes/v${version}/`,
    reuseExistingServer: !ci,
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] }, testIgnore: /pwa\.spec/ },
    { name: "webkit", use: { ...devices["Desktop Safari"] }, testIgnore: /pwa\.spec/ },
  ],
});
