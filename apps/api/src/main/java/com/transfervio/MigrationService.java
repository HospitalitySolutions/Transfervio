package com.transfervio;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationService {
  final Store store;
  final Scopes scope;
  final ObjectStore objects;
  final StructuredParser parser;
  final ImageExtractor images;
  final RecordValidator validator;
  final UploadService uploads;
  final SourceMappings sourceMappings;
  final AiConsent aiConsent;
  final RuntimeConfig config;

  MigrationService(
      Store store,
      Scopes scope,
      ObjectStore objects,
      StructuredParser parser,
      ImageExtractor images,
      RecordValidator validator,
      UploadService uploads,
      SourceMappings sourceMappings,
      AiConsent aiConsent,
      RuntimeConfig config) {
    this.store = store;
    this.scope = scope;
    this.objects = objects;
    this.parser = parser;
    this.images = images;
    this.validator = validator;
    this.uploads = uploads;
    this.sourceMappings = sourceMappings;
    this.aiConsent = aiConsent;
    this.config = config;
  }

  @Transactional
  public void process(String token) {
    var m = scope.invitation(token, true);
    scope.editable(m);
    UUID id = Store.id(m.get("id"));
    sourceMappings.requireReady(id);
    if (config.externalAi)
      for (var file :
          store.rows(
              "select id,sha256 from uploads where migration_id=? and deleted_at is null and"
                  + " content_type like 'image/%'",
              id)) aiConsent.require(id, Store.id(file.get("id")), Store.text(file.get("sha256")));
    if (store.db.queryForObject(
            "select count(*) from uploads where migration_id=? and deleted_at is null",
            Integer.class,
            id)
        == 0) throw Store.error(400, "Upload files before extraction");
    store.db.update("update migrations set status='PROCESSING',revision=revision+1 where id=?", id);
    store.db.update(
        "insert into jobs(id,migration_id,kind) values (?,?,'EXTRACT') on"
            + " conflict(migration_id,kind) do update set"
            + " status='PENDING',attempts=0,available_at=now(),lease_until=null,error_code=null",
        UUID.randomUUID(),
        id);
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public void extract(UUID migration) {
    var m = store.one("select * from migrations where id=? for no key update", migration);
    if (!"PROCESSING".equals(m.get("status"))) return;
    var all = new LinkedHashMap<UUID, Map<String, Object>>();
    var clientIndex = new HashMap<String, Set<UUID>>();
    var files =
        store.rows(
            "select * from uploads where migration_id=? and deleted_at is null order by"
                + " created_at,id",
            migration);
    var allowed =
        (List<String>)
            store
                .one("select allowed_entities from invitations where migration_id=?", migration)
                .get("allowed_entities");
    int total = 0;
    for (var file : files) {
      byte[] bytes = objects.get(Store.text(file.get("object_key")));
      String type = Store.text(file.get("content_type"));
      var parsed =
          type.startsWith("image/")
              ? images.extract(bytes, migration, Store.id(file.get("id")))
              : sourceMappings.apply(file, bytes);
      for (var row : parsed) {
        if (++total > 10000) throw Store.error(400, "Maximum 10,000 records per migration");
        var data = new LinkedHashMap<>(row.data());
        var parserIssues = new ArrayList<>(row.parserIssues());
        if (!allowed.contains(row.entity())) parserIssues.add("ENTITY_NOT_ALLOWED");
        String sourceIdentity = Store.text(data.get("source_id"));
        String identity =
            row.entity().equals("CLIENT")
                ? identityKeys(data).stream()
                    .sorted()
                    .findFirst()
                    .orElse(file.get("sha256") + ":" + row.row())
                : !sourceIdentity.isBlank() ? "id:" + sourceIdentity : store.json(data);
        UUID id =
            UUID.nameUUIDFromBytes(
                (migration + ":" + row.entity() + ":" + identity).getBytes(StandardCharsets.UTF_8));
        if (row.entity().equals("CLIENT")) {
          var matches = new HashSet<UUID>();
          identityKeys(data).forEach(k -> matches.addAll(clientIndex.getOrDefault(k, Set.of())));
          if (matches.size() == 1) id = matches.iterator().next();
          else if (matches.size() > 1) parserIssues.add("AMBIGUOUS_DUPLICATE");
          for (String key : identityKeys(data))
            clientIndex.computeIfAbsent(key, k -> new HashSet<>()).add(id);
        }
        var provenance = new LinkedHashMap<String, Object>();
        provenance.put("upload_id", file.get("id"));
        provenance.put("filename", file.get("original_name"));
        provenance.put("sha256", file.get("sha256"));
        provenance.put("row", row.row());
        provenance.put("table", row.table());
        if (type.startsWith("image/"))
          for (String field : List.of("source_region", "uncertain_fields"))
            if (row.sourceFields().containsKey(field))
              provenance.put(field, row.sourceFields().get(field));
        provenance.put("source_id", sourceIdentity);
        provenance.put(
            "extractor", type.startsWith("image/") ? images.providerName() : "deterministic");
        provenance.put("parser_issues", parserIssues);
        if (all.containsKey(id)) {
          var existing = all.get(id);
          var prior = (Map<String, Object>) existing.get("data");
          for (var field : data.entrySet()) {
            if (Store.text(field.getValue()).isBlank()) continue;
            if (!Store.text(prior.get(field.getKey())).isBlank()
                && !Store.text(prior.get(field.getKey())).equals(Store.text(field.getValue()))
                && !field.getKey().equals("source_id")) parserIssues.add("DUPLICATE_CONFLICT");
            else prior.put(field.getKey(), field.getValue());
          }
          provenance.put("duplicate_match", true);
          ((List<Object>) existing.get("provenance")).add(provenance);
        } else {
          var record = new LinkedHashMap<String, Object>();
          record.put("id", id);
          record.put("entity", row.entity());
          record.put("data", data);
          record.put("provenance", new ArrayList<>(List.of(provenance)));
          all.put(id, record);
        }
      }
    }
    store.db.update("delete from records where migration_id=?", migration);
    for (var r : all.values())
      store.db.update(
          "insert into records(id,migration_id,entity,data,provenance) values"
              + " (?,?,?,?::jsonb,?::jsonb)",
          r.get("id"),
          migration,
          r.get("entity"),
          store.json(r.get("data")),
          store.json(r.get("provenance")));
    revalidate(m);
    store.db.update(
        "update migrations set status='REVIEW',revision=revision+1 where id=?", migration);
    store.audit(Store.id(m.get("partner_id")), migration, "extraction.completed");
  }

  static Set<String> identityKeys(Map<String, Object> data) {
    var keys = new HashSet<String>();
    for (String field : List.of("source_id", "email", "phone")) {
      String value = Store.text(data.get(field)).toLowerCase(Locale.ROOT);
      if (!value.isBlank()) keys.add(field + ":" + value);
    }
    return keys;
  }

  @SuppressWarnings("unchecked")
  void revalidate(Map<String, Object> m) {
    UUID migration = Store.id(m.get("id"));
    var records =
        store.rows("select * from records where migration_id=? order by entity desc,id", migration);
    String zone =
        m.get("property_id") == null
            ? ""
            : Store.text(
                scope
                    .property(
                        Store.id(m.get("partner_id")),
                        Store.id(m.get("tenant_id")),
                        Store.id(m.get("property_id")))
                    .get("timezone"));
    var catalog =
        store.rows(
            "select kind,external_id,name from catalog_entries where partner_id=? and tenant_id=?"
                + " and property_id=?",
            m.get("partner_id"),
            m.get("tenant_id"),
            m.get("property_id"));
    var clientIds = new HashSet<String>();
    var references = new HashMap<String, Set<String>>();
    for (var r : records)
      if (r.get("entity").equals("CLIENT") && !Boolean.TRUE.equals(r.get("excluded"))) {
        String id = Store.text(r.get("id"));
        clientIds.add(id);
        var data = (Map<String, Object>) r.get("data");
        for (String key : identityKeys(data))
          references.computeIfAbsent(key, k -> new HashSet<>()).add(id);
        for (var p : (List<Map<String, Object>>) r.get("provenance"))
          if (!Store.text(p.get("source_id")).isBlank())
            references
                .computeIfAbsent(
                    "source_id:" + Store.text(p.get("source_id")).toLowerCase(Locale.ROOT),
                    k -> new HashSet<>())
                .add(id);
      }
    var issueMap = new HashMap<UUID, List<RecordValidator.Issue>>();
    @SuppressWarnings("unchecked")
    var allowed =
        (List<String>)
            store
                .one("select allowed_entities from invitations where migration_id=?", migration)
                .get("allowed_entities");
    for (var r : records) {
      var data = (Map<String, Object>) r.get("data");
      String entity = Store.text(r.get("entity"));
      UUID id = Store.id(r.get("id"));
      if (entity.equals("APPOINTMENT") && Store.text(data.get("client_record_id")).isBlank()) {
        var candidates = new HashSet<String>();
        for (String key :
            List.of(
                "source_id:" + Store.text(data.get("client_source_id")).toLowerCase(Locale.ROOT),
                "email:" + Store.text(data.get("email")).toLowerCase(Locale.ROOT),
                "phone:" + Store.text(data.get("phone"))))
          candidates.addAll(references.getOrDefault(key, Set.of()));
        if (candidates.size() == 1) data.put("client_record_id", candidates.iterator().next());
      }
      var parserIssues = new ArrayList<String>();
      for (var p : (List<Map<String, Object>>) r.get("provenance"))
        if (!Boolean.TRUE.equals(p.get("reviewed")))
          parserIssues.addAll((List<String>) p.getOrDefault("parser_issues", List.of()));
      var issues =
          validator.validate(
              entity,
              data,
              zone,
              m.get("property_id") != null,
              catalog,
              clientIds,
              parserIssues.stream().distinct().toList());
      if (!allowed.contains(entity))
        issues.add(
            RecordValidator.issue(
                "ENTITY_NOT_ALLOWED",
                "entity",
                "This invitation does not permit this entity; exclude the record"));
      if (entity.equals("CLIENT")
          && identityKeys(data).stream()
              .anyMatch(k -> references.getOrDefault(k, Set.of()).size() > 1))
        issues.add(
            RecordValidator.issue(
                "DUPLICATE_CLIENT",
                "source_id",
                "Another included client shares this semantic identity; correct or exclude a"
                    + " duplicate"));
      if (Boolean.TRUE.equals(r.get("excluded"))) issues.clear();
      issueMap.put(id, issues);
    }
    // Find overlapping staff bookings without inventing scheduling resolutions.
    var scheduled =
        records.stream()
            .filter(
                r ->
                    r.get("entity").equals("APPOINTMENT")
                        && !Boolean.TRUE.equals(r.get("excluded")))
            .toList();
    var byEmployee = new HashMap<String, List<Map<String, Object>>>();
    for (var r : scheduled) {
      var d = (Map<String, Object>) r.get("data");
      if (!Store.text(d.get("employee_id")).isBlank() && !"CANCELLED".equals(d.get("status")))
        byEmployee.computeIfAbsent(Store.text(d.get("employee_id")), k -> new ArrayList<>()).add(r);
    }
    for (var group : byEmployee.values()) {
      record Slot(Map<String, Object> record, java.time.Instant start, java.time.Instant end) {}
      var slots = new ArrayList<Slot>();
      for (var r : group)
        try {
          var d = (Map<String, Object>) r.get("data");
          var start = java.time.OffsetDateTime.parse(Store.text(d.get("start"))).toInstant();
          slots.add(
              new Slot(
                  r,
                  start,
                  start.plusSeconds(
                      Integer.parseInt(Store.text(d.get("duration_minutes"))) * 60L)));
        } catch (Exception ignored) {
        }
      slots.sort(Comparator.comparing(Slot::start));
      Slot longest = null;
      for (var slot : slots) {
        if (longest != null && slot.start().isBefore(longest.end())) {
          for (var r : List.of(slot.record(), longest.record()))
            issueMap
                .get(Store.id(r.get("id")))
                .add(
                    RecordValidator.issue(
                        "OVERLAPPING_APPOINTMENT",
                        "start",
                        "Overlapping appointment for the same employee; review or exclude"));
        }
        if (longest == null || slot.end().isAfter(longest.end())) longest = slot;
      }
    }
    for (var r : records)
      store.db.update(
          "update records set data=?::jsonb,issues=?::jsonb where id=? and migration_id=?",
          store.json(r.get("data")),
          store.json(issueMap.get(Store.id(r.get("id")))),
          r.get("id"),
          migration);
  }

  Map<String, Object> preview(Map<String, Object> m) {
    UUID id = Store.id(m.get("id"));
    var result = new LinkedHashMap<String, Object>();
    result.put("migration", m);
    result.put("files", uploads.list(id));
    result.put(
        "ai_usage",
        store.rows(
            "select model,purpose,count(*) responses,sum(input_tokens)"
                + " input_tokens,sum(output_tokens) output_tokens,sum(estimated_cost_usd)"
                + " estimated_cost_usd from ai_usage where migration_id=? group by model,purpose",
            id));
    result.put(
        "records",
        store.rows(
            "select id,entity,data,provenance,issues,excluded from records where migration_id=?"
                + " order by entity desc,id",
            id));
    result.put(
        "delivery_results",
        store.rows("select * from delivery_results where migration_id=? order by updated_at", id));
    result.put(
        "jobs",
        store.rows(
            "select kind,status,attempts,error_code,error_message from jobs where migration_id=?",
            id));
    return result;
  }

  public record Correction(
      UUID record_id, Map<String, String> fields, Boolean excluded, boolean acknowledge_source) {}

  public record Corrections(int revision, List<Correction> records) {}

  @SuppressWarnings("unchecked")
  @Transactional
  public void correct(String token, Corrections request) {
    var m = scope.invitation(token, true);
    if (!m.get("status").equals("REVIEW")) throw Store.error(409, "Wait for review before editing");
    if (((Number) m.get("revision")).intValue() != request.revision())
      throw Store.error(409, "Review changed; refresh before editing");
    if (request.records() == null || request.records().size() > 500)
      throw Store.error(400, "Maximum 500 corrections");
    for (var patch : request.records()) {
      var r =
          store.one(
              "select * from records where id=? and migration_id=?",
              patch.record_id(),
              m.get("id"));
      var data = (Map<String, Object>) r.get("data");
      if (patch.fields() != null)
        for (var e : patch.fields().entrySet()) {
          if (!RecordValidator.FIELDS.contains(e.getKey())
              || e.getValue() == null
              || e.getValue().length() > 10000) throw Store.error(400, "Invalid editable field");
          data.put(e.getKey(), e.getValue().trim());
        }
      var provenance = (List<Map<String, Object>>) r.get("provenance");
      if (patch.acknowledge_source()) provenance.forEach(p -> p.put("reviewed", true));
      store.db.update(
          "update records set data=?::jsonb,excluded=?,provenance=?::jsonb where id=? and"
              + " migration_id=?",
          store.json(data),
          patch.excluded() == null ? r.get("excluded") : patch.excluded(),
          store.json(provenance),
          patch.record_id(),
          m.get("id"));
    }
    revalidate(m);
    store.db.update("update migrations set revision=revision+1 where id=?", m.get("id"));
    store.audit(Store.id(m.get("partner_id")), Store.id(m.get("id")), "review.corrected");
  }

  @Transactional
  public Map<String, Object> approve(String token, int revision) {
    return approve(token, revision, null);
  }

  @SuppressWarnings("unchecked")
  @Transactional
  public Map<String, Object> approve(
      String token, int revision, Map<String, List<String>> requestedFields) {
    var m = scope.invitation(token, true);
    UUID id = Store.id(m.get("id"));
    if (Set.of("READY", "PARTIAL", "IMPORTED").contains(m.get("status")))
      return store.one("select sha256,created_at from approved_snapshots where migration_id=?", id);
    if (!m.get("status").equals("REVIEW") || ((Number) m.get("revision")).intValue() != revision)
      throw Store.error(409, "Review changed; refresh and review before approving");
    revalidate(m);
    var invitationFields = (Map<String, List<String>>) m.get("export_fields");
    var sharing = ExportSchema.policy(requestedFields == null ? invitationFields : requestedFields);
    var approvedFields = new LinkedHashMap<String, List<String>>();
    for (String entity : ExportSchema.ENTITIES.keySet()) {
      var permitted = ExportSchema.allowed(entity, invitationFields);
      if (sharing.containsKey(entity) && !permitted.containsAll(sharing.get(entity)))
        throw Store.error(403, "Approval cannot expand invitation sharing scope");
      approvedFields.put(
          entity, new ArrayList<>(ExportSchema.allowed(entity, invitationFields, sharing)));
    }
    var records =
        store.rows(
            "select id,entity,data,provenance from records where migration_id=? and not excluded"
                + " order by entity desc,id",
            id);
    if (records.isEmpty()) throw Store.error(400, "Include at least one record");
    if (store.db.queryForObject(
            "select count(*) from records where migration_id=? and not excluded and"
                + " jsonb_array_length(issues)>0",
            Integer.class,
            id)
        > 0) throw Store.error(409, "Resolve validation issues before approval");
    for (var r : records) {
      r.put("stable_record_id", r.remove("id"));
      r.put("approved", true);
      r.put(
          "idempotency_key",
          Store.hash(
              m.get("partner_id")
                  + ":"
                  + m.get("tenant_id")
                  + ":"
                  + m.get("property_id")
                  + ":"
                  + id
                  + ":"
                  + r.get("stable_record_id")));
    }
    var content = new LinkedHashMap<String, Object>();
    content.put("schema_version", 1);
    content.put("export_fields", approvedFields);
    for (String field : List.of("partner_id", "tenant_id", "property_id"))
      content.put(field, m.get(field));
    content.put("migration_id", id);
    content.put("records", records);
    String json = store.json(content), sha = Store.hash(json);
    store.db.update(
        "insert into approved_snapshots(migration_id,content,sha256) values (?,?::jsonb,?)",
        id,
        json,
        sha);
    store.db.update(
        "update migrations set status='READY',approved_at=now(),revision=revision+1 where id=?",
        id);
    UUID event = UUID.randomUUID();
    store.db.update(
        "insert into outbox_events(id,migration_id,partner_id,event_type,payload) values"
            + " (?,?,?,'migration.ready',?::jsonb)",
        event,
        id,
        m.get("partner_id"),
        store.json(
            Map.of(
                "event_id",
                event,
                "type",
                "migration.ready",
                "migration_id",
                id,
                "export_url",
                "/v1/migrations/" + id + "/export",
                "snapshot_sha256",
                sha)));
    store.audit(Store.id(m.get("partner_id")), id, "migration.approved");
    return Map.of("sha256", sha, "status", "READY");
  }
}
