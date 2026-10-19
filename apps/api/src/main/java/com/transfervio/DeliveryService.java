package com.transfervio;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {
  final Store store;
  final Scopes scope;
  final RuntimeConfig config;

  DeliveryService(Store store, Scopes scope, RuntimeConfig config) {
    this.store = store;
    this.scope = scope;
    this.config = config;
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> snapshot(UUID partner, UUID id) {
    scope.migration(partner, id, false);
    var rows = store.rows("select content from approved_snapshots where migration_id=?", id);
    if (rows.isEmpty()) throw Store.error(409, "Approve the migration before export or delivery");
    return (Map<String, Object>) rows.getFirst().get("content");
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> records(Map<String, Object> snapshot) {
    return (List<Map<String, Object>>) snapshot.get("records");
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> export(
      UUID partner,
      UUID id,
      String entity,
      int cursor,
      int limit,
      String fields,
      Map<String, List<String>> credentialFields) {
    if (cursor < 0
        || limit < 1
        || limit > 200
        || !Set.of("clients", "appointments").contains(entity))
      throw Store.error(400, "Invalid pagination or entity");
    var snapshot = snapshot(partner, id);
    var authorized =
        ExportSchema.allowed(
            entity,
            credentialFields,
            (Map<String, List<String>>) snapshot.getOrDefault("export_fields", Map.of()));
    var selected = ExportSchema.selection(entity, fields, authorized);
    String kind = entity.equals("clients") ? "CLIENT" : "APPOINTMENT";
    var all = records(snapshot).stream().filter(r -> kind.equals(r.get("entity"))).toList();
    if (cursor > all.size()) throw Store.error(400, "Cursor outside export");
    int end = Math.min(all.size(), cursor + limit);
    var result = new LinkedHashMap<String, Object>();
    result.put("schema_version", 1);
    result.put("migration_id", id);
    result.put("tenant_id", snapshot.get("tenant_id"));
    result.put("property_id", snapshot.get("property_id"));
    result.put(
        "records",
        all.subList(cursor, end).stream()
            .map(r -> ExportSchema.project(r, selected, fields != null))
            .toList());
    result.put("next_cursor", end < all.size() ? end : null);
    result.put("total", all.size());
    return result;
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> dryRun(UUID partner, UUID id) {
    var snapshot = snapshot(partner, id);
    var proposed = new ArrayList<Map<String, Object>>();
    for (var row : records(snapshot)) {
      var existing =
          store.rows(
              "select id from mock_destination_records where idempotency_key=?",
              row.get("idempotency_key"));
      var plan = new LinkedHashMap<String, Object>();
      plan.put("record_id", row.get("stable_record_id"));
      plan.put("entity", row.get("entity"));
      plan.put("action", existing.isEmpty() ? "CREATE" : "ALREADY_EXISTS");
      plan.put("data", row.get("data"));
      plan.put("idempotency_key", row.get("idempotency_key"));
      proposed.add(plan);
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("migration_id", id);
    result.put("destination", "local-mock");
    result.put("dry_run", true);
    result.put("writes_enabled", false);
    result.put("tenant_id", snapshot.get("tenant_id"));
    result.put("property_id", snapshot.get("property_id"));
    result.put("proposed_changes", proposed);
    result.put("notifications", 0);
    result.put("billing_operations", 0);
    result.put(
        "note",
        "Local mock preview only. Vendors map canonical records and perform imports in their own"
            + " systems.");
    return result;
  }

  public record Receipt(UUID record_id, String outcome, String destination_id, String error_code) {}

  @Transactional
  public void acknowledge(UUID partner, UUID migration, List<Receipt> receipts) {
    scope.migration(partner, migration, true);
    var snapshot = snapshot(partner, migration);
    if (receipts == null || receipts.isEmpty() || receipts.size() > 1000)
      throw Store.error(400, "Provide 1–1000 receipts");
    if (receipts.stream().anyMatch(r -> r == null || r.record_id() == null || r.outcome() == null))
      throw Store.error(400, "Each receipt requires a record ID and outcome");
    var byId = new HashMap<UUID, Map<String, Object>>();
    records(snapshot).forEach(r -> byId.put(Store.id(r.get("stable_record_id")), r));
    var sorted = new ArrayList<>(receipts);
    sorted.sort(
        Comparator.comparing(
            r ->
                "CLIENT".equals(byId.getOrDefault(r.record_id(), Map.of()).get("entity")) ? 0 : 1));
    for (var receipt : sorted) {
      var row = byId.get(receipt.record_id());
      if (row == null) throw Store.error(400, "Receipt record is outside this approved snapshot");
      if (!Set.of("IMPORTED", "ALREADY_EXISTS", "UPDATED", "FAILED").contains(receipt.outcome())
          || Store.text(receipt.destination_id()).length() > 200
          || Store.text(receipt.error_code()).length() > 100)
        throw Store.error(400, "Invalid delivery receipt");
      boolean success = !receipt.outcome().equals("FAILED");
      if (success && Store.text(receipt.destination_id()).isBlank())
        throw Store.error(400, "Successful receipts require a destination ID");
      if (success && row.get("entity").equals("APPOINTMENT")) {
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) row.get("data");
        if (store.db.queryForObject(
                "select count(*) from delivery_results where migration_id=? and record_id=? and"
                    + " outcome<>'FAILED'",
                Integer.class,
                migration,
                Store.id(data.get("client_record_id")))
            == 0) throw Store.error(409, "Acknowledge the linked client before its appointment");
      }
      var prior =
          store.rows(
              "select * from delivery_results where migration_id=? and record_id=?",
              migration,
              receipt.record_id());
      if (!prior.isEmpty() && !"FAILED".equals(prior.getFirst().get("outcome"))) {
        if (success
            && !Objects.equals(prior.getFirst().get("destination_id"), receipt.destination_id()))
          throw Store.error(409, "Destination identity cannot change on retry");
        continue; // Confirmed success is monotonic: retries never downgrade it.
      }
      String error =
          receipt.error_code() == null ? null : receipt.error_code().replaceAll("[^A-Z0-9_]", "_");
      store.db.update(
          "insert into delivery_results(migration_id,record_id,outcome,destination_id,error_code)"
              + " values (?,?,?,?,?) on conflict(migration_id,record_id) do update set"
              + " outcome=excluded.outcome,destination_id=excluded.destination_id,error_code=excluded.error_code,attempt=delivery_results.attempt+1,updated_at=now()",
          migration,
          receipt.record_id(),
          receipt.outcome(),
          receipt.destination_id(),
          error);
    }
    int successes =
        store.db.queryForObject(
            "select count(*) from delivery_results where migration_id=? and outcome<>'FAILED'",
            Integer.class,
            migration);
    store.db.update(
        "update migrations set status=? where id=?",
        successes == byId.size() ? "IMPORTED" : "PARTIAL",
        migration);
    store.audit(partner, migration, "delivery.acknowledged");
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Map<String, Object> deliverMock(UUID partner, UUID migration, Set<UUID> failRecords) {
    if (!config.destination.equals("mock"))
      throw Store.error(409, "Mock import is available only in DESTINATION_MODE=mock");
    scope.migration(partner, migration, true);
    var snapshot = snapshot(partner, migration);
    var receipts = new ArrayList<Receipt>();
    var destinations = new HashMap<String, String>();
    for (var row : records(snapshot)) {
      UUID record = Store.id(row.get("stable_record_id"));
      String kind = Store.text(row.get("entity"));
      var data = new LinkedHashMap<>((Map<String, Object>) row.get("data"));
      var existing =
          store.rows(
              "select id from mock_destination_records where idempotency_key=?",
              row.get("idempotency_key"));
      if (!existing.isEmpty()) {
        String id = Store.text(existing.getFirst().get("id"));
        destinations.put(record.toString(), id);
        receipts.add(new Receipt(record, "ALREADY_EXISTS", id, null));
        continue;
      }
      if (failRecords.contains(record)) {
        receipts.add(new Receipt(record, "FAILED", null, "SYNTHETIC_RETRY_TEST"));
        continue;
      }
      if (kind.equals("APPOINTMENT")) {
        String client = destinations.get(Store.text(data.get("client_record_id")));
        if (client == null) {
          receipts.add(new Receipt(record, "FAILED", null, "CLIENT_NOT_IMPORTED"));
          continue;
        }
        data.put("destination_client_id", client);
      }
      UUID destination = UUID.randomUUID();
      store.db.update(
          "insert into"
              + " mock_destination_records(id,idempotency_key,partner_id,tenant_id,property_id,entity,data)"
              + " values (?,?,?,?,?,?,?::jsonb)",
          destination,
          row.get("idempotency_key"),
          partner,
          Store.id(snapshot.get("tenant_id")),
          snapshot.get("property_id") == null ? null : Store.id(snapshot.get("property_id")),
          kind,
          store.json(data));
      destinations.put(record.toString(), destination.toString());
      receipts.add(new Receipt(record, "IMPORTED", destination.toString(), null));
    }
    acknowledge(partner, migration, receipts);
    return Map.of(
        "receipts",
        receipts,
        "destination",
        "local-mock",
        "notifications",
        0,
        "billing_operations",
        0);
  }
}
