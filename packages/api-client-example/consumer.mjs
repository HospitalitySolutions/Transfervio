// Vendor-owned reference consumer. Its only destination is a local synthetic JSON journal.
import {
  readFileSync,
  writeFileSync,
  renameSync,
  mkdirSync,
  existsSync,
} from "node:fs";
import { dirname, resolve } from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";
import { TransfervioClient } from "./client.mjs";

export async function consume(
  client,
  migration,
  journalPath,
  { execute = false, fields = {} } = {},
) {
  const clients = await client.export(migration, "clients", {
      fields: fields.clients,
    }),
    appointments = await client.export(migration, "appointments", {
      fields: fields.appointments,
    });
  if (!execute)
    return {
      dry_run: true,
      clients: clients.length,
      appointments: appointments.length,
      acknowledgments: 0,
    };
  const journal = existsSync(journalPath)
    ? JSON.parse(readFileSync(journalPath, "utf8"))
    : { records: {} };
  const destinations = new Map(),
    receipts = [];
  let created = 0,
    existing = 0;
  for (const record of [...clients, ...appointments]) {
    const prior = journal.records[record.idempotency_key];
    if (prior) {
      destinations.set(record.stable_record_id, prior.id);
      receipts.push({
        record_id: record.stable_record_id,
        outcome: "ALREADY_EXISTS",
        destination_id: prior.id,
      });
      existing++;
      continue;
    }
    const linked = record.relationships?.client_record_id;
    if (record.entity === "APPOINTMENT" && !destinations.has(linked))
      throw Error("Import the linked client before its appointment");
    const id = randomUUID();
    journal.records[record.idempotency_key] = {
      id,
      migration,
      stable_record_id: record.stable_record_id,
      entity: record.entity,
      data: record.data,
      ...(linked ? { destination_client_id: destinations.get(linked) } : {}),
    };
    destinations.set(record.stable_record_id, id);
    receipts.push({
      record_id: record.stable_record_id,
      outcome: "IMPORTED",
      destination_id: id,
    });
    created++;
  }
  mkdirSync(dirname(journalPath), { recursive: true });
  writeFileSync(journalPath + ".tmp", JSON.stringify(journal, null, 2));
  renameSync(journalPath + ".tmp", journalPath);
  // Persist before acknowledging. A crash/retry reuses the same destination identities.
  for (let offset = 0; offset < receipts.length; offset += 1000)
    await client.acknowledge(migration, receipts.slice(offset, offset + 1000));
  return {
    dry_run: false,
    created,
    already_exists: existing,
    acknowledgments: receipts.length,
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  if (!process.env.TRANSFERVIO_API_KEY || !process.env.MIGRATION_ID)
    throw Error(
      "Set TRANSFERVIO_API_KEY and MIGRATION_ID locally; never put them in source or browser JS.",
    );
  const client = new TransfervioClient(
      process.env.TRANSFERVIO_BASE_URL || "http://localhost:8091",
      process.env.TRANSFERVIO_API_KEY,
    ),
    journal = fileURLToPath(
      new URL("../../.local-data/vendor-example.json", import.meta.url),
    );
  console.log(
    JSON.stringify(
      await consume(client, process.env.MIGRATION_ID, journal, {
        execute: process.argv.includes("--execute-synthetic-local"),
      }),
      null,
      2,
    ),
  );
}
