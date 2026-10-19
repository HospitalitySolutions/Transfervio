import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../../../", import.meta.url));
const env = Object.fromEntries(
  readFileSync(path.join(root, ".env.local"), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
test("partner invitation, branded review, correction, approval and local delivery", async ({
  page,
  context,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await context.route("**/*", (route) => {
    const url = new URL(route.request().url());
    if (!["localhost", "127.0.0.1"].includes(url.hostname))
      return route.abort();
    return route.continue();
  });
  await page.goto("/");
  await page
    .getByLabel("Password", { exact: true })
    .fill(env.BOOTSTRAP_ADMIN_PASSWORD);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Every move, in one place." }),
  ).toBeVisible();
  await page.screenshot({
    path: path.join(root, ".local-data/dashboard.png"),
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "New invitation", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("combobox", { name: "Customer", exact: true })
    .selectOption({ label: "Synthetic Studio" });
  await dialog
    .getByRole("combobox", { name: "Location", exact: true })
    .selectOption({ label: "Ljubljana · Demo" });
  await dialog
    .getByRole("button", { name: "Create invitation", exact: true })
    .click();
  await expect(
    dialog.getByRole("heading", { name: "Your invitation is ready." }),
  ).toBeVisible();
  const url = await dialog.getByLabel("Upload link").inputValue();
  await dialog.getByRole("button", { name: "Close dialog" }).click();
  const portal = await context.newPage();
  portal.on("pageerror", (e) => errors.push(e.message));
  await portal.goto(url);
  await expect(
    portal.getByRole("heading", { name: "Your next chapter starts here." }),
  ).toBeVisible();
  const landing = await (
    await portal.request.get(
      new URL(url).origin +
        "/public/invitations/" +
        new URL(url).pathname.split("/").at(-1),
    )
  ).json();
  await expect(portal.locator(".wordmark")).toContainText(
    landing.brand.brand_name,
  );
  await portal
    .getByLabel("Upload files")
    .setInputFiles(
      ["clients-sl.csv", "screenshot-09.png", "screenshot-17.png"].map((f) =>
        path.join(root, "fixtures/synthetic", f),
      ),
    );
  await expect(
    portal.getByText("screenshot-17.png", { exact: true }),
  ).toBeVisible();
  await portal
    .getByRole("button", { name: "Extract & validate", exact: true })
    .click();
  await expect(
    portal.getByRole("heading", {
      name: "Your data, ready for a closer look.",
    }),
  ).toBeVisible({ timeout: 20000 });
  await portal.getByRole("button", { name: /Appointments/ }).click();
  await portal.getByRole("checkbox", { name: "Needs attention" }).check();
  await portal
    .getByRole("button", { name: "Review", exact: true })
    .first()
    .click();
  const edit = portal.getByRole("dialog", { name: "Review appointment" });
  await edit.getByLabel("Start", { exact: true }).fill("2026-11-10T10:00");
  await edit.getByLabel("Duration minutes", { exact: true }).fill("60");
  await edit.getByRole("button", { name: "Save & revalidate" }).click();
  await expect(edit).toBeHidden();
  await portal.getByRole("checkbox", { name: "Needs attention" }).uncheck();
  await expect(
    portal.getByText("All included records validated", { exact: true }),
  ).toBeVisible();
  await portal.screenshot({
    path: path.join(root, ".local-data/portal-review.png"),
    fullPage: true,
  });
  await portal
    .getByRole("checkbox", { name: /I have reviewed the included records/ })
    .check();
  await portal
    .getByRole("button", { name: "Approve migration", exact: true })
    .click();
  await expect(
    portal.getByRole("heading", {
      name: "Reviewed. Approved. Ready for the next step.",
    }),
  ).toBeVisible();
  await page.reload();
  await page
    .getByRole("button", { name: "Open migration for Synthetic Studio" })
    .first()
    .click();
  const migration = page.getByRole("dialog");
  await migration.getByRole("button", { name: "Preview delivery" }).click();
  await expect(
    migration.getByRole("heading", { name: "Dry-run · 9 records" }),
  ).toBeVisible();
  await migration
    .getByRole("button", { name: "Deliver to local mock" })
    .click();
  await expect(migration.getByText("9 confirmed · 0 failed")).toBeVisible();
  await migration.getByRole("button", { name: "Close dialog" }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
  await page.screenshot({
    path: path.join(root, ".local-data/dashboard-mobile.png"),
    fullPage: true,
  });
  await portal.setViewportSize({ width: 390, height: 844 });
  await portal.screenshot({
    path: path.join(root, ".local-data/portal-mobile.png"),
    fullPage: true,
  });
  expect(errors).toEqual([]);
});
