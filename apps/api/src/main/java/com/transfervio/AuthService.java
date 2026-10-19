package com.transfervio;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  final Store store;
  final RuntimeConfig config;
  final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

  public record Identity(UUID partnerId, UUID credentialId, String kind) {
    void admin() {
      if (!kind.equals("SESSION")) throw Store.error(403, "Partner administrator session required");
    }
  }

  public AuthService(Store store, RuntimeConfig config) {
    this.store = store;
    this.config = config;
  }

  @org.springframework.beans.factory.annotation.Value("${app.bootstrap-password}")
  String bootstrapPassword;

  @PostConstruct
  void bootstrap() {
    if (!config.runtime.equals("local")) return;
    if (bootstrapPassword.length() < 24)
      throw new IllegalStateException("Set a random local bootstrap password (24+ characters)");
    for (var name : List.of("Calendra", "Lime Demo")) {
      String email = name.equals("Calendra") ? "admin@calendra.test" : "admin@lime.test";
      if (store.db.queryForObject(
              "select count(*) from partner_users where email=?", Integer.class, email)
          == 0) {
        UUID partner = UUID.randomUUID();
        store.db.update(
            "insert into partners(id,name,brand_name) values (?,?,?)", partner, name, name);
        store.db.update(
            "insert into partner_users(id,partner_id,email,password_hash) values (?,?,?,?)",
            UUID.randomUUID(),
            partner,
            email,
            passwords.encode(bootstrapPassword));
      }
    }
  }

  Identity authenticate(String raw) {
    var rows =
        store.rows(
            "select c.id,c.partner_id,c.kind from credentials c join partners p on"
                + " p.id=c.partner_id where c.token_hash=? and not c.revoked and p.active and"
                + " (c.expires_at is null or c.expires_at>now())",
            Store.hash(raw));
    if (rows.isEmpty()) throw Store.error(401, "Invalid or expired credentials");
    var row = rows.getFirst();
    return new Identity(
        Store.id(row.get("partner_id")), Store.id(row.get("id")), Store.text(row.get("kind")));
  }

  @Transactional
  Map<String, Object> login(String email, String password) {
    var rows =
        store.rows(
            "select u.* from partner_users u join partners p on p.id=u.partner_id where u.email=?"
                + " and u.active and p.active",
            email.toLowerCase(Locale.ROOT));
    if (rows.isEmpty()
        || !passwords.matches(password, Store.text(rows.getFirst().get("password_hash"))))
      throw Store.error(401, "Incorrect email or password");
    return issue(Store.id(rows.getFirst().get("partner_id")), "Partner session", "SESSION");
  }

  Map<String, Object> issue(UUID partner, String label, String kind) {
    String raw = Store.token();
    UUID id = UUID.randomUUID();
    Instant expires = Instant.now().plusSeconds(kind.equals("SESSION") ? 28800 : 7776000);
    store.db.update(
        "insert into credentials(id,partner_id,token_hash,label,kind,expires_at) values"
            + " (?,?,?,?,?,?)",
        id,
        partner,
        Store.hash(raw),
        label,
        kind,
        java.sql.Timestamp.from(expires));
    return Map.of("id", id, "token", raw, "expires_at", expires.toString());
  }
}
