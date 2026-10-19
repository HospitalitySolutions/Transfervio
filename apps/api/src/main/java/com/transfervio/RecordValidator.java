package com.transfervio;

import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RecordValidator {
  static final Set<String> FIELDS =
      Set.of(
          "source_id",
          "first_name",
          "last_name",
          "display_name",
          "email",
          "phone",
          "notes",
          "language",
          "client_source_id",
          "client_record_id",
          "start",
          "end",
          "timezone",
          "duration_minutes",
          "status",
          "source_service",
          "source_employee",
          "service_id",
          "employee_id");

  public record Issue(String code, String field, String message, String severity) {}

  static Issue issue(String code, String field, String message) {
    return new Issue(code, field, message, "ERROR");
  }

  List<Issue> validate(
      String entity,
      Map<String, Object> data,
      String propertyZone,
      boolean hasProperty,
      List<Map<String, Object>> catalog,
      Set<String> clientIds,
      List<String> parserIssues) {
    var issues = new ArrayList<Issue>();
    for (String code : parserIssues)
      issues.add(
          issue(
              code,
              "source",
              "Review the source: " + code.replace('_', ' ').toLowerCase(Locale.ROOT)));
    if (entity.equals("CLIENT")) {
      if (Store.text(data.get("display_name")).isBlank()
          && Store.text(data.get("first_name")).isBlank()
          && Store.text(data.get("last_name")).isBlank())
        issues.add(issue("MISSING_NAME", "display_name", "Add a client name"));
      String email = Store.text(data.get("email"));
      if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
        issues.add(issue("INVALID_EMAIL", "email", "Check the email address"));
      String phone = Store.text(data.get("phone"));
      if (!phone.isBlank() && !phone.matches("\\+?[0-9]{6,15}"))
        issues.add(issue("INVALID_PHONE", "phone", "Use a phone number with country code"));
      if (email.isBlank() && phone.isBlank() && Store.text(data.get("source_id")).isBlank())
        issues.add(
            issue(
                "MISSING_IDENTITY",
                "source_id",
                "Provide a source ID, email or phone; names alone are ambiguous"));
    } else {
      if (!hasProperty)
        issues.add(
            issue(
                "PROPERTY_REQUIRED",
                "property",
                "Appointments require a property-scoped invitation"));
      if (!clientIds.contains(Store.text(data.get("client_record_id"))))
        issues.add(
            issue("UNRESOLVED_CLIENT", "client_record_id", "Link one client from this migration"));
      String zone = Store.text(data.get("timezone"));
      if (zone.isBlank()) zone = propertyZone;
      data.put("timezone", zone);
      OffsetDateTime start = parseStart(Store.text(data.get("start")), zone, issues);
      if (start != null) data.put("start", start.toString());
      String duration = Store.text(data.get("duration_minutes"));
      if (duration.isBlank() && start != null && !Store.text(data.get("end")).isBlank()) {
        var end = parseStart(Store.text(data.get("end")), zone, issues);
        if (end != null) {
          duration = String.valueOf(Duration.between(start, end).toMinutes());
          data.put("duration_minutes", duration);
        }
      }
      try {
        int minutes = Integer.parseInt(duration);
        if (minutes < 1 || minutes > 1440) throw new NumberFormatException();
      } catch (Exception e) {
        issues.add(
            issue("INVALID_DURATION", "duration_minutes", "Specify duration in minutes (1–1440)"));
      }
      String status = Store.text(data.get("status")).toUpperCase(Locale.ROOT);
      status =
          Map.of("POTRJENO", "CONFIRMED", "REZERVIRANO", "BOOKED", "ODPOVEDANO", "CANCELLED")
              .getOrDefault(status, status);
      data.put("status", status);
      if (!Set.of("BOOKED", "CONFIRMED", "CANCELLED", "COMPLETED", "NO_SHOW").contains(status))
        issues.add(issue("INVALID_STATUS", "status", "Choose a supported appointment status"));
      for (String kind : List.of("SERVICE", "EMPLOYEE")) {
        String field = kind.toLowerCase(Locale.ROOT) + "_id",
            source = "source_" + kind.toLowerCase(Locale.ROOT);
        String id = Store.text(data.get(field));
        if (id.isBlank()) {
          String label = Store.text(data.get(source));
          var matches =
              catalog.stream()
                  .filter(
                      c ->
                          kind.equals(c.get("kind"))
                              && !label.isBlank()
                              && label.equalsIgnoreCase(Store.text(c.get("name"))))
                  .toList();
          if (matches.size() == 1) {
            id = Store.text(matches.getFirst().get("external_id"));
            data.put(field, id);
          }
        }
        final String candidate = id;
        if (catalog.stream()
            .noneMatch(
                c ->
                    kind.equals(c.get("kind"))
                        && candidate.equals(Store.text(c.get("external_id")))))
          issues.add(
              issue(
                  "UNRESOLVED_" + kind,
                  field,
                  "Select a " + kind.toLowerCase(Locale.ROOT) + " from this property's catalog"));
      }
    }
    return issues;
  }

  static OffsetDateTime parseStart(String raw, String zoneName, List<Issue> issues) {
    try {
      if (raw.isBlank()) {
        issues.add(issue("MISSING_START", "start", "Specify a complete date and time"));
        return null;
      }
      if (raw.contains("/")) {
        issues.add(issue("AMBIGUOUS_DATE", "start", "Clarify this date using YYYY-MM-DDTHH:mm"));
        return null;
      }
      if (zoneName.isBlank()) {
        issues.add(issue("MISSING_TIMEZONE", "timezone", "Choose an explicit IANA timezone"));
        return null;
      }
      ZoneId zone = ZoneId.of(zoneName);
      try {
        var offset = OffsetDateTime.parse(raw);
        if (!zone.getRules()
            .getValidOffsets(offset.toLocalDateTime())
            .contains(offset.getOffset())) {
          issues.add(
              issue(
                  "OFFSET_MISMATCH",
                  "start",
                  "Offset does not match the timezone at this local time"));
          return null;
        }
        return offset;
      } catch (DateTimeParseException ignored) {
      }
      LocalDateTime local;
      if (raw.matches("\\d{2}\\.\\d{2}\\.\\d{4} .*"))
        local =
            LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
                    .withResolverStyle(ResolverStyle.STRICT));
      else local = LocalDateTime.parse(raw.replace(' ', 'T'));
      var offsets = zone.getRules().getValidOffsets(local);
      if (offsets.size() != 1) {
        issues.add(
            issue(
                "DST_AMBIGUOUS_OR_MISSING",
                "start",
                "This time is repeated or skipped by daylight saving; specify a valid offset"));
        return null;
      }
      return OffsetDateTime.of(local, offsets.getFirst());
    } catch (Exception e) {
      issues.add(
          issue("INVALID_DATETIME", "start", "Specify date, year, time and a valid timezone"));
      return null;
    }
  }
}
