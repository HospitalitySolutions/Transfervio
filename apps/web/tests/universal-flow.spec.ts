import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = fileURLToPath(new URL("../../../", import.meta.url));
const env = Object.fromEntries(
  readFileSync(path.join(root, ".env.local"), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
test("unfamiliar source mapping and customer-selected export fields", async ({
  page,
  request,
  context,
}) => {
  const api = "http://localhost:" + (env.API_PORT || 8091);
  expect(
    (await (await request.get(api + "/api/runtime")).json()).ai_provider,
  ).toBe("mock");
  await context.route("**/*", (route) =>
    ["localhost", "127.0.0.1"].includes(new URL(route.request().url()).hostname)
      ? route.continue()
      : route.abort(),
  );
  const login = await request.post(api + "/auth/login", {
    data: {
      email: "admin@calendra.test",
      password: env.BOOTSTRAP_ADMIN_PASSWORD,
    },
  });
  expect(login.ok()).toBeTruthy();
  const { token } = await login.json();
  const headers = { Authorization: `Bearer ${token}` };
  try {
    const tenant = await (
      await request.post(api + "/v1/tenants", {
        headers,
        data: {
          external_tenant_id: "browser-universal",
          name: "Synthetic Universal Studio",
        },
      })
    ).json();
    const invitation = await (
      await request.post(api + "/v1/invitations", {
        headers,
        data: {
          tenant_id: tenant.id,
          allowed_entities: ["CLIENT"],
          expires_in_hours: 24,
        },
      })
    ).json();
    await page.goto(invitation.upload_url);
    await page
      .getByLabel("Upload files")
      .setInputFiles({
        name: "synthetic-unfamiliar.csv",
        mimeType: "text/csv",
        buffer: Buffer.from(
          "Roster number,Given label,Mailing point,notes\nui-client-1,Demo,demo@example.test,Private synthetic note",
        ),
      });
    await page
      .getByLabel("Map Roster number", { exact: true })
      .selectOption("source_id");
    await page
      .getByLabel("Map Given label", { exact: true })
      .selectOption("first_name");
    await page
      .getByLabel("Map Mailing point", { exact: true })
      .selectOption("email");
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator("body")).toHaveJSProperty("scrollWidth", 390);
    await page.screenshot({
      path: path.join(root, ".local-data/universal-mapping-mobile.png"),
      fullPage: true,
    });
    await page
      .getByRole("button", { name: "Confirm source mapping", exact: true })
      .click();
    await expect(
      page.getByText("Source column mappings · 0 need confirmation"),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Extract & validate", exact: true })
      .click();
    await expect(
      page.getByText("All included records validated", { exact: true }),
    ).toBeVisible({ timeout: 20000 });
    await page
      .getByText("Choose fields your partner may retrieve", { exact: true })
      .click();
    await page.getByRole("checkbox", { name: "Notes", exact: true }).uncheck();
    await page
      .getByRole("checkbox", { name: /I have reviewed the included records/ })
      .check();
    await page
      .getByRole("button", { name: "Approve migration", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Reviewed. Approved. Ready for the next step.",
      }),
    ).toBeVisible();
    const exported = await request.get(
      api + `/v1/migrations/${invitation.migration_id}/export`,
      { headers },
    );
    expect(exported.ok()).toBeTruthy();
    const result = await exported.json();
    expect(result.records[0].data.first_name).toBe("Demo");
    expect(result.records[0].data).not.toHaveProperty("notes");
    expect(
      (
        await request.get(
          api + `/v1/migrations/${invitation.migration_id}/export?fields=notes`,
          { headers },
        )
      ).status(),
    ).toBe(403);
  } finally {
    await request.post(api + "/v1/logout", { headers });
  }
});
