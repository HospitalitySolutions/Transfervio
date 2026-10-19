package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_INTEGRATION", matches = "true")
@Transactional
class LocalDatabaseTest {
  @Autowired Store store;
  @Autowired Scopes scopes;
  @Autowired ObjectStore objects;
  @Autowired JobWorker worker;
  @Autowired RetentionService retention;
  UUID partner, tenant, property, migration;
  String token;

  @BeforeEach
  void seed() {
    partner = UUID.randomUUID();
    tenant = UUID.randomUUID();
    property = UUID.randomUUID();
    migration = UUID.randomUUID();
    token = Store.token();
    store.db.update(
        "insert into partners(id,name,brand_name) values (?,'Synthetic DB test','Synthetic DB"
            + " test')",
        partner);
    store.db.update(
        "insert into tenants(id,partner_id,external_tenant_id,name) values (?,?,'123','Synthetic DB"
            + " test')",
        tenant,
        partner);
    store.db.update(
        "insert into properties(id,tenant_id,partner_id,external_property_id,name,timezone) values"
            + " (?,?,?,'p1','Synthetic property','Europe/Ljubljana')",
        property,
        tenant,
        partner);
    store.db.update(
        "insert into migrations(id,partner_id,tenant_id,property_id) values (?,?,?,?)",
        migration,
        partner,
        tenant,
        property);
    store.db.update(
        "insert into invitations(id,migration_id,token_hash,allowed_entities,expires_at) values"
            + " (?,?,?,'{CLIENT}',now()+interval '1 day')",
        UUID.randomUUID(),
        migration,
        Store.hash(token));
  }

  @Test
  void expiredAndRevokedInvitationsStopResolving() {
    assertEquals(migration, scopes.invitation(token, false).get("id"));
    store.db.update(
        "update invitations set expires_at=now()-interval '1 second' where migration_id=?",
        migration);
    assertThrows(Exception.class, () -> scopes.invitation(token, false));
    store.db.update(
        "update invitations set expires_at=now()+interval '1 day',revoked=true where"
            + " migration_id=?",
        migration);
    assertThrows(Exception.class, () -> scopes.invitation(token, false));
  }

  @Test
  void databaseRejectsCrossPartnerProperty() {
    UUID other = UUID.randomUUID();
    store.db.update(
        "insert into partners(id,name,brand_name) values (?,'Synthetic other','Synthetic other')",
        other);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            store.db.update(
                "insert into migrations(id,partner_id,tenant_id,property_id) values (?,?,?,?)",
                UUID.randomUUID(),
                other,
                tenant,
                property));
  }

  @Test
  void snapshotsAreImmutableInDatabase() {
    store.db.update(
        "insert into approved_snapshots(migration_id,content,sha256) values (?,'{}','synthetic')",
        migration);
    assertThrows(
        Exception.class,
        () ->
            store.db.update(
                "update approved_snapshots set content='{\"tampered\":true}' where migration_id=?",
                migration));
  }

  @Test
  void retentionRemovesExpiredBytesAndKeepsProvenanceMetadata() {
    String key = "synthetic-test/" + UUID.randomUUID();
    UUID upload = UUID.randomUUID();
    byte[] bytes =
        "id,name\nc1,Synthetic Retention".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try {
      objects.put(key, bytes, "text/csv");
      store.db.update(
          "insert into"
              + " uploads(id,migration_id,original_name,content_type,size_bytes,sha256,object_key,created_at)"
              + " values (?,?,'expired.csv','text/csv',?,?,?,now()-interval '8 days')",
          upload,
          migration,
          bytes.length,
          Store.hash(bytes),
          key);
      retention.expireFiles();
      assertNotNull(
          store.one("select deleted_at from uploads where id=?", upload).get("deleted_at"));
      assertThrows(Exception.class, () -> objects.get(key));
    } finally {
      objects.delete(key);
    }
  }

  @Test
  void workerRecoversExpiredLeaseAndPreservesObjects() {
    String key = "synthetic-test/" + UUID.randomUUID();
    byte[] bytes =
        "id,name,email\nc1,Synthetic Recovery,recovery@example.test"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try {
      objects.put(key, bytes, "text/csv");
      assertArrayEquals(bytes, objects.get(key));
      store.db.update(
          "insert into"
              + " uploads(id,migration_id,original_name,content_type,size_bytes,sha256,object_key)"
              + " values (?,?,'recovery.csv','text/csv',?,?,?)",
          UUID.randomUUID(),
          migration,
          bytes.length,
          Store.hash(bytes),
          key);
      store.db.update("update migrations set status='PROCESSING' where id=?", migration);
      store.db.update(
          "insert into jobs(id,migration_id,kind,status,attempts,lease_until) values"
              + " (?,?,'EXTRACT','RUNNING',1,now()-interval '1 minute')",
          UUID.randomUUID(),
          migration);
      worker.work();
      assertEquals(
          "REVIEW", store.one("select status from migrations where id=?", migration).get("status"));
      assertEquals(
          "DONE",
          store.one("select status from jobs where migration_id=?", migration).get("status"));
    } finally {
      objects.delete(key);
    }
  }
}
