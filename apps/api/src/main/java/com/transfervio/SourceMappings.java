package com.transfervio;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceMappings {
  final Store store;
  final Scopes scopes;
  final StructuredParser parser;
  final ObjectStore objects;
  final OpenAiVision ai;

  SourceMappings(
      Store store, Scopes scopes, StructuredParser parser, ObjectStore objects, OpenAiVision ai) {
    this.store = store;
    this.scopes = scopes;
    this.parser = parser;
    this.objects = objects;
    this.ai = ai;
  }

  Map<String, Object> file(UUID migration, UUID upload) {
    return store.one(
        "select * from uploads where id=? and migration_id=? and deleted_at is null",
        upload,
        migration);
  }

  List<StructuredParser.Parsed> read(Map<String, Object> file) {
    return parser.parse(
        objects.get(Store.text(file.get("object_key"))), Store.text(file.get("content_type")));
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> ensure(Map<String, Object> file, List<StructuredParser.Parsed> rows) {
    var tables = new LinkedHashMap<String, List<StructuredParser.Parsed>>();
    rows.forEach(r -> tables.computeIfAbsent(r.table(), k -> new ArrayList<>()).add(r));
    if (tables.size() > 100) throw Store.error(400, "Maximum 100 source tables");
    var out = new ArrayList<Map<String, Object>>();
    for (var entry : tables.entrySet()) {
      var columns = new LinkedHashSet<String>();
      entry.getValue().forEach(r -> columns.addAll(r.sourceFields().keySet()));
      if (columns.size() > 100) throw Store.error(400, "Maximum 100 source columns");
      String entity = entry.getValue().getFirst().entity();
      var mapping = new LinkedHashMap<String, String>();
      var fields = ExportSchema.fields(ExportSchema.plural(entity));
      boolean complete = true;
      for (String column : columns) {
        String field = StructuredParser.ALIASES.getOrDefault(StructuredParser.norm(column), "");
        if (entity.equals("CLIENT") && field.equals("client_source_id")) field = "source_id";
        if (!fields.contains(field)) field = "";
        mapping.put(column, field);
        if (field.isEmpty()) complete = false;
      }
      if (mapping.values().stream().filter(v -> !v.isBlank()).distinct().count()
          != mapping.values().stream().filter(v -> !v.isBlank()).count()) complete = false;
      store.db.update(
          "insert into source_mappings(upload_id,table_name,entity,columns,field_map,approved)"
              + " values (?,?,?,?::jsonb,?::jsonb,?) on conflict do nothing",
          file.get("id"),
          entry.getKey(),
          entity,
          store.json(columns),
          store.json(mapping),
          complete);
      var plan =
          store.one(
              "select * from source_mappings where upload_id=? and table_name=?",
              file.get("id"),
              entry.getKey());
      plan.put("filename", file.get("original_name"));
      plan.put("row_count", entry.getValue().size());
      plan.put(
          "samples",
          entry.getValue().stream()
              .limit(3)
              .map(
                  r -> {
                    var sample = new LinkedHashMap<String, String>();
                    r.sourceFields()
                        .forEach(
                            (k, v) ->
                                sample.put(
                                    k,
                                    Store.text(v)
                                        .substring(0, Math.min(160, Store.text(v).length()))));
                    return sample;
                  })
              .toList());
      out.add(plan);
    }
    return out;
  }

  @Transactional
  public List<Map<String, Object>> plans(String token) {
    var m = scopes.invitation(token, false);
    var out = new ArrayList<Map<String, Object>>();
    for (var file :
        store.rows(
            "select * from uploads where migration_id=? and deleted_at is null and content_type not"
                + " like 'image/%' order by created_at,id",
            m.get("id"))) out.addAll(ensure(file, read(file)));
    return out;
  }

  void requireReady(UUID migration) {
    for (var file :
        store.rows(
            "select * from uploads where migration_id=? and deleted_at is null and content_type not"
                + " like 'image/%'",
            migration))
      for (var plan : ensure(file, read(file)))
        if (!Boolean.TRUE.equals(plan.get("approved")))
          throw Store.error(409, "Review and confirm source column mappings before processing");
  }

  @SuppressWarnings("unchecked")
  List<StructuredParser.Parsed> apply(Map<String, Object> file, byte[] bytes) {
    var rows = parser.parse(bytes, Store.text(file.get("content_type")));
    var plans = ensure(file, rows);
    var out = new ArrayList<StructuredParser.Parsed>();
    for (var row : rows) {
      var plan =
          plans.stream()
              .filter(p -> p.get("table_name").equals(row.table()))
              .findFirst()
              .orElseThrow();
      if (!Boolean.TRUE.equals(plan.get("approved")))
        throw Store.error(409, "Confirm source mapping before processing");
      var mapped = new LinkedHashMap<String, Object>();
      for (var entry : ((Map<String, String>) plan.get("field_map")).entrySet())
        if (!entry.getValue().isBlank())
          mapped.put(entry.getValue(), row.sourceFields().get(entry.getKey()));
      out.add(
          new StructuredParser.Parsed(
              Store.text(plan.get("entity")),
              StructuredParser.canonical(mapped),
              row.row(),
              row.parserIssues(),
              row.sourceFields(),
              row.table()));
    }
    return out;
  }

  public record Mapping(
      String table, String entity, Map<String, String> fields, boolean approved) {}

  @Transactional
  public void save(String token, UUID upload, Mapping request) {
    var m = scopes.invitation(token, true);
    scopes.editable(m);
    var file = file(Store.id(m.get("id")), upload);
    var plan =
        ensure(file, read(file)).stream()
            .filter(p -> p.get("table_name").equals(request.table()))
            .findFirst()
            .orElseThrow(() -> Store.error(404, "Source table not found"));
    validate(request, (List<String>) plan.get("columns"));
    store.db.update(
        "update source_mappings set"
            + " entity=?,field_map=?::jsonb,approved=?,suggested_by='human',updated_at=now() where"
            + " upload_id=? and table_name=?",
        request.entity(),
        store.json(request.fields()),
        request.approved(),
        upload,
        request.table());
    invalidate(m);
  }

  static void validate(Mapping request, List<String> columns) {
    var allowed = ExportSchema.fields(ExportSchema.plural(request.entity()));
    if (request.fields() == null || !request.fields().keySet().equals(new HashSet<>(columns)))
      throw Store.error(400, "Map every source column or explicitly ignore it");
    var targets = new HashSet<String>();
    for (String target : request.fields().values())
      if (target == null
          || (!target.isBlank() && (!allowed.contains(target) || !targets.add(target))))
        throw Store.error(400, "Unknown or duplicate canonical field mapping");
    if (targets.isEmpty()) throw Store.error(400, "Map at least one field");
  }

  void invalidate(Map<String, Object> m) {
    store.db.update(
        "update migrations set status='UPLOADING',revision=revision+1 where id=?", m.get("id"));
    store.db.update("delete from records where migration_id=?", m.get("id"));
  }

  @Transactional
  public Map<String, Object> suggest(String token, UUID upload, String table) {
    var initial = scopes.invitation(token, false);
    store.one("select id from migrations where id=? for no key update", initial.get("id"));
    var m = scopes.invitation(token, false);
    scopes.editable(m);
    var file = file(Store.id(m.get("id")), upload);
    var rows = read(file);
    var plan =
        ensure(file, rows).stream()
            .filter(p -> p.get("table_name").equals(table))
            .findFirst()
            .orElseThrow(() -> Store.error(404, "Source table not found"));
    var sample = boundedSample(rows.stream().filter(r -> r.table().equals(table)).toList());
    var suggestion =
        ai.suggestMapping(
            sample,
            Store.id(m.get("id")),
            upload,
            table,
            Store.text(file.get("sha256")),
            (List<String>) plan.get("columns"));
    var mapping =
        new Mapping(
            table,
            Store.text(suggestion.get("entity")),
            (Map<String, String>) suggestion.get("fields"),
            false);
    validate(mapping, (List<String>) plan.get("columns"));
    store.db.update(
        "update source_mappings set"
            + " entity=?,field_map=?::jsonb,approved=false,suggested_by='openai',updated_at=now()"
            + " where upload_id=? and table_name=?",
        mapping.entity(),
        store.json(mapping.fields()),
        upload,
        table);
    invalidate(m);
    return Map.of(
        "entity",
        mapping.entity(),
        "fields",
        mapping.fields(),
        "approved",
        false,
        "sample_rows",
        sample.size());
  }

  static List<Map<String, String>> boundedSample(List<StructuredParser.Parsed> rows) {
    var out = new ArrayList<Map<String, String>>();
    int budget = 16000;
    for (int i = 0; i < Math.min(12, rows.size()); i++) {
      var row =
          rows.get(
              (int) ((long) i * (rows.size() - 1) / Math.max(1, Math.min(12, rows.size()) - 1)));
      var sample = new LinkedHashMap<String, String>();
      for (var entry : row.sourceFields().entrySet()) {
        String value = Store.text(entry.getValue());
        value = value.substring(0, Math.min(120, value.length()));
        int size = value.length() + entry.getKey().length();
        if (budget < size) return out;
        budget -= size;
        sample.put(entry.getKey(), value);
      }
      out.add(sample);
    }
    return out;
  }
}
