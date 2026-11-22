/** CSP, no-network guarantee and the About hash. */
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { test, expect, APP_FILE, openApp, createVault, vaultWithNotes } from "./helpers.ts";

const html = readFileSync(APP_FILE, "utf8");

test("index.html is self-contained with a strict CSP", async () => {
  const csp = /<meta http-equiv="Content-Security-Policy" content="([^"]+)">/.exec(html)?.[1] ?? "";
  for (const directive of ["default-src 'none'", "connect-src 'none'", "form-action 'none'", "base-uri 'none'", "object-src 'none'"]) {
    expect(csp).toContain(directive);
  }
  expect(csp).not.toContain("unsafe-inline");
  expect(csp).not.toContain("unsafe-eval");
  expect(html).not.toMatch(/<script[^>]+src=/);
  expect(html).not.toMatch(/<link[^>]+href="(?!data:)/);
  const scripts = html.match(/<script>/g) ?? [];
  expect(scripts.length).toBe(1);
});

test("CSP blocks network access and injected scripts", async ({ page }) => {
  await openApp(page);
  const fetchResult = await page.evaluate(async () => {
    try {
      await fetch("https://example.com/");
      return "allowed";
    } catch {
      return "blocked";
    }
  });
  expect(fetchResult).toBe("blocked");
  const imageLoaded = await page.evaluate(
    () =>
      new Promise<boolean>((resolve) => {
        const img = new Image();
        img.onload = () => resolve(true);
        img.onerror = () => resolve(false);
        img.src = "https://example.com/pixel.png";
      }),
  );
  expect(imageLoaded).toBe(false);
  const injected = await page.evaluate(() => {
    try {
      const s = document.createElement("script");
      s.text = "window.__injected = true";
      document.body.appendChild(s);
    } catch {
      // Trusted Types may throw
    }
    return (window as unknown as { __injected?: boolean }).__injected === true;
  });
  expect(injected).toBe(false);
});

test("About shows the version and the hash of the inline script", async ({ page }) => {
  const script = /<script>([\s\S]*)<\/script>/.exec(html)?.[1] ?? "";
  const hex = createHash("sha256").update(script, "utf8").digest("hex");
  const b64 = createHash("sha256").update(script, "utf8").digest("base64");
  expect(html).toContain(`'sha256-${b64}'`);
  await vaultWithNotes(page, 1);
  await page.getByRole("button", { name: "Settings" }).click();
  await expect(page.getByTestId("script-hash")).toHaveText(`${hex} (CSP sha256-${b64})`);
});

test("no requests during a full session (checked by the requests fixture)", async ({ page }) => {
  await openApp(page);
  await createVault(page);
  await page.getByRole("button", { name: "Add note" }).click();
  await page.getByLabel("Note content").fill("offline only");
  await page.getByRole("button", { name: "Done" }).click();
  await page.getByRole("button", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Back" }).click();
  await page.getByRole("button", { name: "Lock" }).click();
  await expect(page.getByRole("heading", { name: "Unlock vault" })).toBeVisible();
});
