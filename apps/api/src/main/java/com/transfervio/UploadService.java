package com.transfervio;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  final Store store;
  final Scopes scope;
  final ObjectStore objects;
  final StructuredParser parser;

  UploadService(Store store, Scopes scope, ObjectStore objects, StructuredParser parser) {
    this.store = store;
    this.scope = scope;
    this.objects = objects;
    this.parser = parser;
  }

  @Transactional
  public List<Map<String, Object>> upload(String token, List<MultipartFile> files) {
    var m = scope.invitation(token, true);
    scope.editable(m);
    UUID migration = Store.id(m.get("id"));
    if (files.isEmpty() || files.size() > 20)
      throw Store.error(400, "Select between 1 and 20 files");
    int existing =
        store.db.queryForObject(
            "select count(*) from uploads where migration_id=?", Integer.class, migration);
    var addedKeys = new ArrayList<String>();
    var result = new ArrayList<Map<String, Object>>();
    try {
      for (var file : files) {
        byte[] bytes = file.getBytes();
        String name =
            Objects.requireNonNullElse(file.getOriginalFilename(), "upload")
                .replaceAll("[\\p{Cntrl}/\\\\]", "_");
        if (name.length() > 180) throw Store.error(400, "Filename too long");
        String type = parser.detect(bytes, name);
        String sha = Store.hash(bytes);
        var duplicate =
            store.rows(
                "select id,original_name from uploads where migration_id=? and sha256=?",
                migration,
                sha);
        if (!duplicate.isEmpty()) {
          result.add(Map.of("id", duplicate.getFirst().get("id"), "duplicate", true));
          continue;
        }
        if (existing++ >= 20) throw Store.error(400, "Maximum 20 unique files per migration");
        UUID id = UUID.randomUUID();
        String key = m.get("partner_id") + "/" + m.get("tenant_id") + "/" + migration + "/" + id;
        objects.put(key, bytes, type);
        addedKeys.add(key);
        store.db.update(
            "insert into"
                + " uploads(id,migration_id,original_name,content_type,size_bytes,sha256,object_key)"
                + " values (?,?,?,?,?,?,?)",
            id,
            migration,
            name,
            type,
            bytes.length,
            sha,
            key);
        result.add(Map.of("id", id, "name", name, "duplicate", false));
      }
      store.db.update(
          "update migrations set status='UPLOADING',revision=revision+1 where id=?", migration);
      store.audit(Store.id(m.get("partner_id")), migration, "files.uploaded");
      return result;
    } catch (Exception e) {
      for (String key : addedKeys) {
        try {
          objects.delete(key);
        } catch (Exception ignored) {
        }
      }
      if (e instanceof RuntimeException r) throw r;
      throw Store.error(400, "Upload failed");
    }
  }

  List<Map<String, Object>> list(UUID migration) {
    return store.rows(
        "select id,original_name,content_type,size_bytes,sha256,created_at,deleted_at from uploads"
            + " where migration_id=? order by created_at,id",
        migration);
  }

  record Original(String name, String contentType, byte[] bytes) {}

  Original original(UUID migration, UUID id) {
    var file =
        store.one(
            "select object_key,content_type,original_name,deleted_at from uploads where id=? and"
                + " migration_id=?",
            id,
            migration);
    if (file.get("deleted_at") != null)
      throw Store.error(410, "Original file retention period ended");
    return new Original(
        Store.text(file.get("original_name")),
        Store.text(file.get("content_type")),
        objects.get(Store.text(file.get("object_key"))));
  }
}
