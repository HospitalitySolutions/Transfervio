package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class UniversalContractTest {
  final Store store = new Store(null, new ObjectMapper());

  @Test
  void explicitProjectionKeepsIdentityButNeverLeaksProvenancePii() {
    var row = new LinkedHashMap<String, Object>();
    row.put("stable_record_id", UUID.randomUUID());
    row.put("entity", "APPOINTMENT");
    row.put("approved", true);
    row.put("idempotency_key", "stable-key");
    row.put(
        "data",
        Map.of(
            "start",
            "2026-11-01T10:00+01:00",
            "client_record_id",
            "opaque-client",
            "notes",
            "private",
            "source_id",
            "private-source"));
    row.put(
        "provenance",
        List.of(
            Map.of(
                "upload_id",
                "opaque-upload",
                "filename",
                "private-client-name.png",
                "source_id",
                "secret-source",
                "row",
                1)));
    var projected = ExportSchema.project(row, List.of("start", "end"), true);
    assertEquals(row.get("stable_record_id"), projected.get("stable_record_id"));
    assertEquals(Map.of("client_record_id", "opaque-client"), projected.get("relationships"));
    var data = (Map<?, ?>) projected.get("data");
    assertTrue(data.containsKey("end"));
    assertNull(data.get("end"));
    assertFalse(data.containsKey("notes"));
    assertFalse(store.json(projected).contains("private"));
    assertFalse(store.json(projected).contains("secret-source"));
    assertEquals("private", ((Map<?, ?>) row.get("data")).get("notes"));
  }

  @Test
  void fieldAuthorizationIntersectsScopesAndRejectsUnknown() {
    var allowed =
        ExportSchema.allowed(
            "clients",
            Map.of("clients", List.of("email", "phone")),
            Map.of("clients", List.of("email", "notes")));
    assertEquals(Set.of("email"), allowed);
    assertEquals(List.of("email"), ExportSchema.selection("clients", "email,email", allowed));
    assertEquals(
        403,
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> ExportSchema.selection("clients", "phone", allowed))
            .getStatusCode()
            .value());
    assertEquals(
        400,
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> ExportSchema.selection("clients", "tenant_id", allowed))
            .getStatusCode()
            .value());
    assertThrows(Exception.class, () -> ExportSchema.selection("clients", "", allowed));
    assertTrue(ExportSchema.allowed("clients", Map.of("clients", List.of())).isEmpty());
  }

  @Test
  void representativeSamplesAreBoundedAndIncludeLastRow() {
    var rows = new ArrayList<StructuredParser.Parsed>();
    for (int n = 0; n < 10000; n++)
      rows.add(
          new StructuredParser.Parsed(
              "CLIENT",
              Map.of(),
              n,
              List.of(),
              Map.of("unfamiliar", "person-" + n, "note", "x".repeat(2000)),
              "records"));
    var sample = SourceMappings.boundedSample(rows);
    assertEquals(12, sample.size());
    assertEquals("person-9999", sample.getLast().get("unfamiliar"));
    assertTrue(sample.stream().allMatch(r -> r.get("note").length() <= 120));
    assertTrue(store.json(sample).length() < 18000);
  }

  @Test
  void conflictingAliasesRemainAvailableForManualMapping() {
    var rows =
        new StructuredParser(store)
            .parse(
                "id,name,email,e-mail\nc1,Synthetic,first@example.test,second@example.test"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "text/csv");
    assertEquals("first@example.test", rows.getFirst().sourceFields().get("email"));
    assertEquals("second@example.test", rows.getFirst().sourceFields().get("e-mail"));
    assertThrows(Exception.class, () -> StructuredParser.canonical(rows.getFirst().sourceFields()));
  }

  @Test
  void mappingCannotSmuggleScopeOrCollapseColumns() {
    assertEquals(
        400,
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () ->
                    SourceMappings.validate(
                        new SourceMappings.Mapping("records", null, Map.of("a", "email"), true),
                        List.of("a")))
            .getStatusCode()
            .value());
    assertThrows(
        Exception.class,
        () ->
            SourceMappings.validate(
                new SourceMappings.Mapping("records", "CLIENT", Map.of("a", "tenant_id"), true),
                List.of("a")));
    assertThrows(
        Exception.class,
        () ->
            SourceMappings.validate(
                new SourceMappings.Mapping(
                    "records", "CLIENT", Map.of("a", "email", "b", "email"), true),
                List.of("a", "b")));
    assertDoesNotThrow(
        () ->
            SourceMappings.validate(
                new SourceMappings.Mapping(
                    "records", "CLIENT", Map.of("a", "email", "b", ""), true),
                List.of("a", "b")));
  }

  @Test
  void providerFailuresAreActionableAndDoNotEchoProviderText() {
    var ai = new OpenAiVision(store, new RuntimeConfigTest().local());
    assertEquals("AI_AUTHENTICATION", ai.providerFailure(401, "secret-key").code);
    assertEquals("AI_MODEL", ai.providerFailure(404, "private-source").code);
    assertEquals(
        "AI_QUOTA", ai.providerFailure(429, "{\"error\":{\"code\":\"insufficient_quota\"}}").code);
    assertTrue(ai.providerFailure(429, "{}").retryable);
    assertFalse(ai.providerFailure(401, "{}").retryable);
    assertFalse(
        ai.providerFailure(400, "customer-name-and-key").getReason().contains("customer-name"));
  }

  @Test
  void knownJsonTablesDoNotHideUnfamiliarTables() {
    var rows =
        new StructuredParser(store)
            .parse(
                store
                    .json(
                        Map.of(
                            "clients", List.of(Map.of("id", "known-1", "name", "Synthetic")),
                            "attendees",
                                List.of(
                                    Map.of(
                                        "person", Map.of("ref", "unknown-1", "given", "Example")))))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "application/json");
    assertEquals(2, rows.size());
    assertEquals(
        Set.of("clients", "attendees"),
        new HashSet<>(rows.stream().map(StructuredParser.Parsed::table).toList()));
    assertEquals(
        "unknown-1",
        rows.stream()
            .filter(r -> r.table().equals("attendees"))
            .findFirst()
            .orElseThrow()
            .sourceFields()
            .get("person.ref"));
  }

  @Test
  void arbitraryInputRequiresBothRuntimeOptInAndFileConsent() {
    var config = new RuntimeConfigTest().local();
    var consent = new AiConsent(store, new Scopes(store), config);
    assertEquals(
        403,
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> consent.require(UUID.randomUUID(), UUID.randomUUID(), "unseen"))
            .getStatusCode()
            .value());
    config.externalAi = true;
    config.aiProvider = "openai";
    assertThrows(
        Exception.class, () -> consent.require(UUID.randomUUID(), UUID.randomUUID(), "unseen"));
  }

  @Test
  void vendorCatalogResolutionIsOptionalButForgedReferencesAreRejected() {
    var data =
        new LinkedHashMap<String, Object>(
            Map.of(
                "client_record_id",
                "client",
                "start",
                "2026-11-02T10:00",
                "timezone",
                "Europe/Ljubljana",
                "duration_minutes",
                "60",
                "status",
                "CONFIRMED",
                "source_service",
                "Vendor resolves this",
                "source_employee",
                "Source person"));
    var validator = new RecordValidator();
    assertTrue(
        validator
            .validate(
                "APPOINTMENT",
                data,
                "Europe/Ljubljana",
                true,
                List.of(),
                Set.of("client"),
                List.of())
            .isEmpty());
    data.put("service_id", "foreign-service");
    assertTrue(
        validator
            .validate(
                "APPOINTMENT",
                data,
                "Europe/Ljubljana",
                true,
                List.of(),
                Set.of("client"),
                List.of())
            .stream()
            .anyMatch(i -> i.code().equals("UNRESOLVED_SERVICE")));
  }
}
