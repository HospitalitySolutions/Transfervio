package com.transfervio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Store {
  final JdbcTemplate db;
  final ObjectMapper json;

  public Store(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  List<Map<String, Object>> rows(String sql, Object... args) {
    return db.query(
        sql,
        (rs, n) -> {
          var out = new LinkedHashMap<String, Object>();
          var md = rs.getMetaData();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            if ("jsonb".equals(md.getColumnTypeName(i))) value = read(rs.getString(i));
            else if (value instanceof java.sql.Array a)
              value = Arrays.asList((Object[]) a.getArray());
            else if (value instanceof java.sql.Timestamp t) value = t.toInstant().toString();
            out.put(md.getColumnLabel(i), value);
          }
          return out;
        },
        args);
  }

  Map<String, Object> one(String sql, Object... args) {
    var found = rows(sql, args);
    if (found.isEmpty()) throw error(404, "Resource not found");
    return found.getFirst();
  }

  String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Serialization failed");
    }
  }

  Object read(String value) {
    try {
      return json.readValue(value, Object.class);
    } catch (Exception e) {
      throw error(400, "Invalid JSON");
    }
  }

  Map<String, Object> map(String value) {
    try {
      return json.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw error(400, "Expected a JSON object");
    }
  }

  void audit(UUID partner, UUID migration, String action) {
    db.update(
        "insert into audit_events(partner_id,migration_id,action) values (?,?,?)",
        partner,
        migration,
        action);
  }

  static UUID id(Object value) {
    if (value instanceof UUID u) return u;
    try {
      return UUID.fromString(String.valueOf(value));
    } catch (Exception e) {
      throw error(400, "Invalid identifier");
    }
  }

  static String hash(String value) {
    return hash(value.getBytes(StandardCharsets.UTF_8));
  }

  static String hash(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String token() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static ResponseStatusException error(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }

  static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
