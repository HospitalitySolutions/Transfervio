package com.transfervio;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Versioned allowlist shared by discovery, projections, source mapping and consent scopes. */
@RestController
public class ExportSchema {
  public record Field(
      String id,
      String display_name,
      String data_type,
      boolean nullable,
      boolean required,
      String description,
      String validation) {}

  static final Map<String, List<Field>> ENTITIES = new LinkedHashMap<>();

  static {
    ENTITIES.put(
        "clients",
        List.of(
            f(
                "source_id",
                "Source ID",
                "string",
                false,
                "Original client identifier; may contain source personal data.",
                "One of source_id, email or phone is required."),
            f(
                "first_name",
                "First name",
                "string",
                false,
                "Given name.",
                "At least one name field is required."),
            f(
                "last_name",
                "Last name",
                "string",
                false,
                "Family name.",
                "At least one name field is required."),
            f(
                "display_name",
                "Display name",
                "string",
                false,
                "Full display name.",
                "At least one name field is required."),
            f(
                "email",
                "Email",
                "string",
                false,
                "Email address, normalized to lowercase.",
                "Valid email when present."),
            f(
                "phone",
                "Phone",
                "string",
                false,
                "Telephone number.",
                "6–15 digits, optional leading +."),
            f(
                "language",
                "Language",
                "string",
                false,
                "Source preferred language.",
                "No language invented when absent."),
            f(
                "notes",
                "Notes",
                "string",
                false,
                "User-approved source notes.",
                "Maximum 10,000 characters.")));
    ENTITIES.put(
        "appointments",
        List.of(
            f(
                "source_id",
                "Source ID",
                "string",
                false,
                "Original appointment identifier.",
                "Never a destination identity."),
            f(
                "client_record_id",
                "Client record ID",
                "uuid",
                true,
                "Linked approved CLIENT stable_record_id.",
                "Must refer to an included client in this migration."),
            f(
                "client_source_id",
                "Source client ID",
                "string",
                false,
                "Original client reference.",
                "Resolved against source identities."),
            f(
                "start",
                "Start",
                "datetime",
                true,
                "ISO 8601 date/time with UTC offset.",
                "Complete date and valid timezone offset; ambiguous dates/DST require review."),
            f(
                "end",
                "End",
                "datetime",
                false,
                "Source end, normalized when present.",
                "Must agree with start and duration."),
            f(
                "timezone",
                "Timezone",
                "string",
                true,
                "Explicit source or configured property IANA timezone.",
                "Valid IANA zone."),
            f(
                "duration_minutes",
                "Duration (minutes)",
                "string",
                true,
                "Positive integer encoded as a string for v1 compatibility.",
                "Integer 1–1440."),
            f(
                "status",
                "Status",
                "string",
                true,
                "Normalized source appointment status.",
                "BOOKED, CONFIRMED, CANCELLED, COMPLETED or NO_SHOW."),
            f(
                "source_service",
                "Source service",
                "string",
                false,
                "Source service label for vendor-side resolution.",
                "Destination resolution is optional."),
            f(
                "source_employee",
                "Source employee",
                "string",
                false,
                "Source staff label for vendor-side resolution.",
                "Destination resolution is optional."),
            f(
                "service_id",
                "Service ID",
                "string",
                false,
                "Optional explicitly selected partner catalog reference.",
                "If present, must belong to the property's supplied catalog."),
            f(
                "employee_id",
                "Employee ID",
                "string",
                false,
                "Optional explicitly selected partner catalog reference.",
                "If present, must belong to the property's supplied catalog.")));
  }

  static Field f(
      String id,
      String label,
      String type,
      boolean required,
      String description,
      String validation) {
    return new Field(id, label, type, !required, required, description, validation);
  }

  static String entity(String value) {
    if (!ENTITIES.containsKey(value))
      throw Store.error(400, "Unknown entity; use clients or appointments");
    return value;
  }

  static String plural(String kind) {
    if (kind == null) throw Store.error(400, "Entity is required");
    return switch (kind) {
      case "CLIENT" -> "clients";
      case "APPOINTMENT" -> "appointments";
      default -> throw Store.error(400, "Unsupported entity");
    };
  }

  static Set<String> fields(String entity) {
    return new LinkedHashSet<>(ENTITIES.get(entity(entity)).stream().map(Field::id).toList());
  }

  static Map<String, List<String>> policy(Map<String, List<String>> requested) {
    if (requested == null) return Map.of();
    var result = new LinkedHashMap<String, List<String>>();
    requested.forEach(
        (entity, names) -> {
          var known = fields(entity);
          if (names == null || names.stream().anyMatch(n -> !known.contains(n)))
            throw Store.error(400, "Unknown export field");
          result.put(entity, List.copyOf(new LinkedHashSet<>(names)));
        });
    return result;
  }

  static Set<String> allowed(String entity, Map<String, List<String>>... scopes) {
    var result = fields(entity);
    for (var scope : scopes)
      if (scope != null && scope.containsKey(entity)) result.retainAll(scope.get(entity));
    return result;
  }

  static List<String> selection(String entity, String fields, Set<String> authorized) {
    if (fields == null) return new ArrayList<>(authorized);
    if (fields.isBlank() || fields.length() > 2000)
      throw Store.error(400, "fields must be a nonempty comma-separated list");
    var result = new LinkedHashSet<String>();
    for (String part : fields.split(",", -1)) {
      String name = part.trim();
      if (!fields(entity).contains(name)) throw Store.error(400, "Unknown export field: " + name);
      if (!authorized.contains(name))
        throw Store.error(
            403, "Requested export field is outside the approved authorization scope");
      result.add(name);
    }
    return new ArrayList<>(result);
  }

  @GetMapping("/v1/export-schema")
  Map<String, Object> discover(
      @AuthenticationPrincipal AuthService.Identity who,
      @RequestParam(required = false) String entity) {
    var result = new LinkedHashMap<String, Object>();
    for (String name : entity == null ? ENTITIES.keySet() : Set.of(entity(entity))) {
      var allowed = allowed(name, who.exportFields());
      result.put(name, ENTITIES.get(name).stream().filter(f -> allowed.contains(f.id())).toList());
    }
    return Map.of(
        "schema_version",
        1,
        "entities",
        result,
        "technical_fields",
        List.of("stable_record_id", "entity", "approved", "idempotency_key", "relationships"),
        "compatibility",
        "Additive fields retain v1. Breaking meanings or types require a new major version. Saved"
            + " profiles are reserved for future use; fields selects business data only.");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> project(
      Map<String, Object> record, List<String> selected, boolean explicit) {
    var source = (Map<String, Object>) record.get("data");
    var data = new LinkedHashMap<String, Object>();
    for (String name : selected) {
      Object value = source.get(name);
      if (explicit) data.put(name, value == null || Store.text(value).isBlank() ? null : value);
      else if (source.containsKey(name)) data.put(name, value);
    }
    var out = new LinkedHashMap<String, Object>();
    for (String key : List.of("stable_record_id", "entity", "approved", "idempotency_key"))
      out.put(key, record.get(key));
    out.put("data", data);
    out.put(
        "relationships",
        "APPOINTMENT".equals(record.get("entity"))
            ? Map.of("client_record_id", Store.text(source.get("client_record_id")))
            : Map.of());
    // Source identifiers, filenames and original text are business data, never technical metadata.
    out.put(
        "provenance",
        ((List<Map<String, Object>>) record.getOrDefault("provenance", List.of()))
            .stream()
                .map(
                    p -> {
                      var safe = new LinkedHashMap<String, Object>();
                      for (String key : List.of("upload_id", "sha256", "row", "extractor"))
                        if (p.containsKey(key)) safe.put(key, p.get(key));
                      return safe;
                    })
                .toList());
    return out;
  }
}
