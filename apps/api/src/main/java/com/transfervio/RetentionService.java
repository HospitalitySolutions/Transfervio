package com.transfervio;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {
  final Store store;
  final ObjectStore objects;
  final Scopes scope;

  @Value("${app.retention-days}")
  int days;

  RetentionService(Store store, ObjectStore objects, Scopes scope) {
    this.store = store;
    this.objects = objects;
    this.scope = scope;
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 30000)
  @Transactional
  public void expireFiles() {
    if (days < 1) throw new IllegalStateException("Retention must be at least one day");
    var files =
        store.rows(
            "select u.id,u.object_key from uploads u join migrations m on m.id=u.migration_id where"
                + " u.deleted_at is null and u.created_at<now()-(?*interval '1 day') and"
                + " m.status<>'PROCESSING' limit 100 for update of m,u skip locked",
            days);
    for (var file : files) {
      try {
        objects.delete(Store.text(file.get("object_key")));
        store.db.update("update uploads set deleted_at=now() where id=?", file.get("id"));
      } catch (Exception ignored) {
      }
    }
    // Covers the crash window between writing object bytes and committing metadata.
    for (String key :
        objects.expiredKeys(java.time.Instant.now().minus(java.time.Duration.ofDays(days))))
      if (store.db.queryForObject(
              "select count(*) from uploads where object_key=?", Integer.class, key)
          == 0) objects.delete(key);
  }

  @Transactional
  public void deleteMigration(UUID partner, UUID id) {
    var m = scope.migration(partner, id, true);
    if ("PROCESSING".equals(m.get("status")))
      throw Store.error(409, "Wait for extraction to finish before deletion");
    for (var f :
        store.rows(
            "select object_key from uploads where migration_id=? and deleted_at is null", id))
      objects.delete(Store.text(f.get("object_key")));
    store.db.update(
        "delete from mock_destination_records where idempotency_key in (select"
            + " r->>'idempotency_key' from approved_snapshots s,"
            + " jsonb_array_elements(s.content->'records') r where s.migration_id=?)",
        id);
    store.db.update("delete from migrations where id=? and partner_id=?", id, partner);
    store.audit(partner, id, "migration.deleted");
  }
}
