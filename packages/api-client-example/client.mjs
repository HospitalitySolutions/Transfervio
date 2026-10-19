// Server-side reference client. Keep the API key out of browser code.
export class TransfervioClient {
  constructor(baseUrl, apiKey) {
    const url = new URL(baseUrl);
    if (
      !["localhost", "127.0.0.1"].includes(url.hostname) ||
      url.protocol !== "http:"
    )
      throw new Error("This reference client is local-only");
    this.base = baseUrl.replace(/\/$/, "");
    this.key = apiKey;
  }
  async request(path, method = "GET", body) {
    const r = await fetch(this.base + path, {
      method,
      redirect: "error",
      headers: {
        Authorization: `Bearer ${this.key}`,
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!r.ok) throw new Error(`Transfervio returned ${r.status}`);
    const text = await r.text();
    return text ? JSON.parse(text) : undefined;
  }
  async schema(entity) {
    return this.request(
      "/v1/export-schema" +
        (entity ? "?entity=" + encodeURIComponent(entity) : ""),
    );
  }
  async export(migration, entity, { fields, limit = 100 } = {}) {
    const records = [];
    let cursor = 0;
    do {
      const query = new URLSearchParams({
        entity,
        cursor: String(cursor),
        limit: String(limit),
      });
      if (fields !== undefined) query.set("fields", fields.join(","));
      const page = await this.request(
        `/v1/migrations/${encodeURIComponent(migration)}/export?${query}`,
      );
      records.push(...page.records);
      cursor = page.next_cursor;
    } while (cursor !== null);
    return records;
  }
  async poll(cursor = 0) {
    return this.request("/v1/events?cursor=" + encodeURIComponent(cursor));
  }
  async acknowledge(migration, results) {
    return this.request(
      `/v1/migrations/${encodeURIComponent(migration)}/delivery-results`,
      "POST",
      { results },
    );
  }
}
