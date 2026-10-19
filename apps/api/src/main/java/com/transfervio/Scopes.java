package com.transfervio;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class Scopes {
  final Store store;

  Scopes(Store store) {
    this.store = store;
  }

  Map<String, Object> tenant(UUID partner, UUID tenant) {
    return store.one("select * from tenants where id=? and partner_id=?", tenant, partner);
  }

  Map<String, Object> property(UUID partner, UUID tenant, UUID property) {
    return store.one(
        "select * from properties where id=? and tenant_id=? and partner_id=?",
        property,
        tenant,
        partner);
  }

  Map<String, Object> migration(UUID partner, UUID migration, boolean lock) {
    return store.one(
        "select * from migrations where id=? and partner_id=?" + (lock ? " for update" : ""),
        migration,
        partner);
  }

  Map<String, Object> invitation(String raw, boolean lock) {
    return store.one(
        "select m.*,i.id invitation_id,i.allowed_entities,i.expires_at from invitations i join"
            + " migrations m on m.id=i.migration_id join partners p on p.id=m.partner_id where"
            + " i.token_hash=? and not i.revoked and i.expires_at>now() and p.active"
            + (lock ? " for update of m,i" : ""),
        Store.hash(raw));
  }

  void editable(Map<String, Object> m) {
    if (!Set.of("UPLOADING", "REVIEW", "FAILED").contains(Store.text(m.get("status"))))
      throw Store.error(409, "Migration cannot be changed in this phase");
  }
}
