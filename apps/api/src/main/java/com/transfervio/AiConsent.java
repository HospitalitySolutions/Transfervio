package com.transfervio;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConsent {
  final Store store;
  final Scopes scopes;
  final RuntimeConfig config;

  AiConsent(Store store, Scopes scopes, RuntimeConfig config) {
    this.store = store;
    this.scopes = scopes;
    this.config = config;
  }

  boolean fixture(String sha) {
    try (var input =
        new org.springframework.core.io.ClassPathResource("synthetic-inputs.json")
            .getInputStream()) {
      return ((List<?>) store.read(new String(input.readAllBytes(), StandardCharsets.UTF_8)))
          .contains(sha);
    } catch (Exception e) {
      return false;
    }
  }

  void enabled() {
    if (!config.externalAi || !"openai".equals(config.aiProvider))
      throw Store.error(403, "External AI is disabled; no data was sent");
  }

  @Transactional
  public void set(String token, UUID upload, boolean authorized, boolean synthetic) {
    var m = scopes.invitation(token, true);
    scopes.editable(m);
    var file =
        store.one(
            "select sha256 from uploads where id=? and migration_id=? and deleted_at is null",
            upload,
            m.get("id"));
    if (!authorized) {
      store.db.update("delete from ai_authorizations where upload_id=?", upload);
      return;
    }
    enabled();
    if (config.syntheticOnly && !fixture(Store.text(file.get("sha256"))))
      throw Store.error(403, "Synthetic-only mode permits verified fixtures only");
    store.db.update(
        "insert into ai_authorizations(upload_id,sha256,synthetic) values (?,?,?) on"
            + " conflict(upload_id) do update set"
            + " sha256=excluded.sha256,synthetic=excluded.synthetic,consented_at=now()",
        upload,
        file.get("sha256"),
        synthetic);
    store.audit(Store.id(m.get("partner_id")), Store.id(m.get("id")), "ai.upload.authorized");
  }

  void require(UUID migration, UUID upload, String sha) {
    enabled();
    if (migration == null || upload == null)
      throw Store.error(403, "AI requires an authorized upload context");
    if (config.syntheticOnly && !fixture(sha))
      throw Store.error(403, "Synthetic-only mode permits verified fixtures only");
    if (store.db.queryForObject(
            "select count(*) from ai_authorizations a join uploads u on u.id=a.upload_id join"
                + " invitations i on i.migration_id=u.migration_id where u.id=? and"
                + " u.migration_id=? and a.sha256=? and u.sha256=a.sha256 and u.deleted_at is null"
                + " and not i.revoked and i.expires_at>now()",
            Integer.class,
            upload,
            migration,
            sha)
        != 1)
      throw Store.error(
          403, "Authorize this upload for external processing first; no data was sent");
  }
}
