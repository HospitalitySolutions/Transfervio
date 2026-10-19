package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ParsingValidationTest {
  final Store store = new Store(null, new ObjectMapper());
  final StructuredParser parser = new StructuredParser(store);

  @Test
  void englishWorkbookAndSlovenianCsvProduceIdenticalClients() throws Exception {
    var root = java.nio.file.Path.of("../../fixtures/synthetic");
    var csv =
        parser.parse(java.nio.file.Files.readAllBytes(root.resolve("clients-sl.csv")), "text/csv");
    var xlsx =
        parser.parse(
            java.nio.file.Files.readAllBytes(root.resolve("clients-en.xlsx")),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertEquals(5, xlsx.size());
    assertEquals(csv, xlsx);
  }

  @Test
  void slovenianColumnsNormalizeWithoutTemplate() {
    var rows =
        parser.parse(
            "id;Ime;Priimek;E-mail;Telefon\nc1;Demo;Example;DEMO@EXAMPLE.TEST;+386 40 000 001"
                .getBytes(StandardCharsets.UTF_8),
            "text/csv");
    assertEquals("CLIENT", rows.getFirst().entity());
    assertEquals("demo@example.test", rows.getFirst().data().get("email"));
    assertEquals("Demo Example", rows.getFirst().data().get("display_name"));
    assertEquals("+38640000001", rows.getFirst().data().get("phone"));
  }

  @Test
  void preservesSemanticClientIdWithoutGuessingEntity() {
    var r =
        parser
            .parse("client_id,name\nc1,Demo Example".getBytes(StandardCharsets.UTF_8), "text/csv")
            .getFirst();
    assertEquals("CLIENT", r.entity());
    assertEquals("c1", r.data().get("source_id"));
  }

  @Test
  void workbookDoesNotEvaluateFormulas() throws Exception {
    byte[] bytes;
    try (var book = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = book.createSheet();
      sheet.createRow(0).createCell(0).setCellValue("name");
      sheet.createRow(1).createCell(0).setCellFormula("1+1");
      book.write(out);
      bytes = out.toByteArray();
    }
    // Formula-only rows are deliberately ignored: no cached value becomes data.
    assertTrue(
        parser
            .parse(bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .isEmpty());
  }

  @Test
  void rejectsMismatchedMagicAndHtml() {
    assertThrows(Exception.class, () -> parser.detect("not an image".getBytes(), "photo.png"));
    assertThrows(
        Exception.class, () -> parser.detect("<script>bad</script>".getBytes(), "rows.csv"));
  }

  @Test
  void ambiguousDatesAndDstStayForReview() {
    var issues = new ArrayList<RecordValidator.Issue>();
    assertNull(RecordValidator.parseStart("03/04/2026 10:00", "Europe/Ljubljana", issues));
    assertEquals("AMBIGUOUS_DATE", issues.getFirst().code());
    issues.clear();
    assertNull(RecordValidator.parseStart("2026-03-29T02:30", "Europe/Ljubljana", issues));
    issues.clear();
    assertNull(RecordValidator.parseStart("2026-10-25T02:30", "Europe/Ljubljana", issues));
    issues.clear();
    assertNotNull(RecordValidator.parseStart("2026-10-25T02:30+02:00", "Europe/Ljubljana", issues));
    assertTrue(issues.isEmpty());
  }

  @Test
  void neverInventsMissingYearTimezoneDurationOrClient() {
    var data =
        new LinkedHashMap<String, Object>(Map.of("start", "03-04 10:00", "status", "CONFIRMED"));
    var issues =
        new RecordValidator()
            .validate("APPOINTMENT", data, "", true, List.of(), Set.of(), List.of());
    assertTrue(issues.stream().anyMatch(i -> i.code().equals("MISSING_TIMEZONE")));
    assertTrue(issues.stream().anyMatch(i -> i.code().equals("INVALID_DURATION")));
    assertTrue(issues.stream().anyMatch(i -> i.code().equals("UNRESOLVED_CLIENT")));
  }

  @Test
  void unknownScreenshotsReturnReviewIssue() {
    assertEquals(
        "MOCK_IMAGE_UNRECOGNIZED",
        new ImageExtractor(store, parser)
            .extract(new byte[] {1, 2, 3})
            .getFirst()
            .parserIssues()
            .getFirst());
  }

  @Test
  void allTwentyFixturesMatchTheirLabeledFields() throws Exception {
    var fixture = new org.springframework.core.io.ClassPathResource("synthetic-images.json");
    var manifest =
        store.map(new String(fixture.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    assertEquals(20, manifest.size());
    var extractor = new ImageExtractor(store, parser);
    int fields = 0;
    for (var entry : manifest.entrySet()) {
      @SuppressWarnings("unchecked")
      var info = (Map<String, Object>) entry.getValue();
      var path = java.nio.file.Path.of("../../fixtures/synthetic", Store.text(info.get("file")));
      var bytes = java.nio.file.Files.readAllBytes(path);
      assertEquals(entry.getKey(), Store.hash(bytes));
      var expected = parser.json(store.json(info.get("records")).getBytes(StandardCharsets.UTF_8));
      var actual = extractor.extract(bytes);
      assertEquals(expected, actual);
      fields += actual.stream().mapToInt(r -> r.data().size()).sum();
    }
    System.out.println(
        "Mock fixture contract: 20 images, "
            + fields
            + " exact canonical fields; deterministic lookup, NOT OCR accuracy.");
  }
}
