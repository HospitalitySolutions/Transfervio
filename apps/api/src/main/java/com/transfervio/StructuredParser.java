package com.transfervio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class StructuredParser {
  public record Parsed(
      String entity, Map<String, Object> data, int row, List<String> parserIssues) {}

  final Store store;

  StructuredParser(Store store) {
    this.store = store;
  }

  static final int MAX_ROWS = 10000;
  static final Map<String, String> ALIASES = new HashMap<>();

  static {
    alias(
        "source_id",
        "id",
        "client id",
        "customer id",
        "identifikator",
        "sifra",
        "appointment id",
        "booking id");
    alias("first_name", "first name", "firstname", "given name", "ime");
    alias("last_name", "last name", "lastname", "surname", "priimek");
    alias(
        "display_name",
        "name",
        "full name",
        "customer name",
        "client name",
        "ime in priimek",
        "naziv");
    alias("email", "email address", "e-mail", "e posta", "eposta");
    alias("phone", "phone number", "mobile", "telephone", "telefon", "gsm");
    alias("notes", "note", "opombe");
    alias("language", "preferred language", "jezik");
    alias("client_source_id", "client_id", "customer_id", "stranka id", "client ref");
    alias("start", "start time", "starts_at", "start_at", "appointment date", "datum", "zacetek");
    alias("end", "end time", "ends_at", "konec");
    alias("duration_minutes", "duration", "minutes", "trajanje");
    alias("timezone", "time zone", "casovni pas");
    alias("status", "booking status", "stanje");
    alias("source_service", "service", "service name", "storitev");
    alias("source_employee", "employee", "staff", "consultant", "izvajalec", "zaposleni");
    alias("service_id", "destination service id");
    alias("employee_id", "destination employee id");
    alias("client_record_id", "stable client id");
  }

  static void alias(String canonical, String... names) {
    ALIASES.put(norm(canonical), canonical);
    for (String n : names) ALIASES.put(norm(n), canonical);
  }

  static String norm(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
  }

  static Map<String, Object> canonical(Map<String, ?> source) {
    var out = new LinkedHashMap<String, Object>();
    source.forEach(
        (k, v) -> {
          String key = ALIASES.get(norm(k));
          if (key != null) {
            String value = Store.text(v);
            if (out.containsKey(key)
                && !Store.text(out.get(key)).isBlank()
                && !value.isBlank()
                && !out.get(key).equals(value))
              throw Store.error(400, "Conflicting columns map to the same field");
            if (!value.isBlank() || !out.containsKey(key)) out.put(key, value);
          }
        });
    if (!out.containsKey("display_name"))
      out.put(
          "display_name",
          (Store.text(out.get("first_name")) + " " + Store.text(out.get("last_name"))).trim());
    if (out.containsKey("email"))
      out.put("email", Store.text(out.get("email")).toLowerCase(Locale.ROOT));
    if (out.containsKey("phone"))
      out.put("phone", Store.text(out.get("phone")).replaceAll("[ ()-]", ""));
    return out;
  }

  String detect(byte[] bytes, String name) {
    String ext = name.toLowerCase(Locale.ROOT).replaceAll("^.*\\.", "");
    if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
      throw Store.error(400, "Empty file or size over 10 MB");
    if (ext.equals("png")
        && bytes.length > 8
        && Arrays.equals(
            Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
      checkImage(bytes);
      return "image/png";
    }
    if (Set.of("jpg", "jpeg").contains(ext)
        && bytes.length > 3
        && bytes[0] == (byte) 255
        && bytes[1] == (byte) 216
        && bytes[2] == (byte) 255) {
      checkImage(bytes);
      return "image/jpeg";
    }
    if (ext.equals("xlsx") && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K')
      return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    if (Set.of("csv", "json").contains(ext)) {
      try {
        StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
      } catch (Exception e) {
        throw Store.error(400, "Text files must be UTF-8");
      }
      String text = new String(bytes, StandardCharsets.UTF_8).stripLeading();
      if (text.indexOf('\0') >= 0 || text.startsWith("<"))
        throw Store.error(400, "Invalid text file content");
      if (ext.equals("json") && !(text.startsWith("{") || text.startsWith("[")))
        throw Store.error(400, "Invalid JSON file");
      return ext.equals("csv") ? "text/csv" : "application/json";
    }
    throw Store.error(
        415, "Supported files: CSV, XLSX, JSON, PNG and JPG; extension must match content");
  }

  List<Parsed> parse(byte[] bytes, String type) {
    try {
      if (type.equals("text/csv")) return csv(bytes);
      if (type.equals("application/json")) return json(bytes);
      if (type.contains("spreadsheetml")) return xlsx(bytes);
      throw Store.error(415, "Use the image extractor for screenshots");
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw Store.error(400, "File cannot be parsed safely; check its format");
    }
  }

  static void checkImage(byte[] bytes) {
    try (var input =
        javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = javax.imageio.ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException();
      var reader = readers.next();
      try {
        reader.setInput(input);
        long width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || width * height > 25_000_000L) throw new IOException();
      } finally {
        reader.dispose();
      }
    } catch (Exception e) {
      throw Store.error(400, "Invalid image or image exceeds 25 megapixels");
    }
  }

  List<Parsed> csv(byte[] bytes) throws IOException {
    String text = new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
    String header = text.lines().findFirst().orElse("");
    char delimiter =
        header.chars().filter(c -> c == ';').count() > header.chars().filter(c -> c == ',').count()
            ? ';'
            : ',';
    var format =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(delimiter)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .get();
    var result = new ArrayList<Parsed>();
    try (var parser = CSVParser.parse(text, format)) {
      for (var row : parser) {
        add(result, row.toMap(), (int) row.getRecordNumber() + 1, null, List.of());
      }
    }
    return result;
  }

  List<Parsed> xlsx(byte[] bytes) throws IOException {
    var result = new ArrayList<Parsed>();
    org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(20 * 1024 * 1024);
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      if (book.isMacroEnabled()) throw Store.error(400, "Macro-enabled workbooks are not accepted");
      DataFormatter formatter = new DataFormatter(Locale.ROOT);
      for (var sheet : book) {
        if (sheet.getLastRowNum() > MAX_ROWS)
          throw Store.error(400, "Maximum 10,000 rows per file");
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) continue;
        if (header.getLastCellNum() > 100) throw Store.error(400, "Maximum 100 columns");
        for (int n = header.getRowNum() + 1; n <= sheet.getLastRowNum(); n++) {
          Row row = sheet.getRow(n);
          if (row == null) continue;
          var map = new LinkedHashMap<String, Object>();
          var issues = new ArrayList<String>();
          for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            String value = "";
            if (cell != null && cell.getCellType() == CellType.FORMULA)
              issues.add("FORMULA_NOT_EVALUATED");
            else if (cell != null
                && cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell))
              value = cell.getLocalDateTimeCellValue().toString();
            else if (cell != null) value = formatter.formatCellValue(cell);
            map.put(formatter.formatCellValue(header.getCell(c)), value);
          }
          if (map.values().stream().anyMatch(v -> !Store.text(v).isBlank()))
            add(result, map, n + 1, null, issues);
        }
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  List<Parsed> json(byte[] bytes) {
    Object root = store.read(new String(bytes, StandardCharsets.UTF_8));
    var result = new ArrayList<Parsed>();
    if (root instanceof List<?> list) {
      int row = 0;
      for (var item : list) {
        if (!(item instanceof Map<?, ?>)) throw Store.error(400, "Expected records");
        add(result, (Map<String, Object>) item, ++row, null, List.of());
      }
    } else if (root instanceof Map<?, ?> map) {
      for (var entry : Map.of("clients", "CLIENT", "appointments", "APPOINTMENT").entrySet()) {
        if (map.get(entry.getKey()) instanceof List<?> list) {
          int row = 0;
          for (var item : list) {
            if (!(item instanceof Map<?, ?>)) throw Store.error(400, "Expected records");
            add(result, (Map<String, Object>) item, ++row, entry.getValue(), List.of());
          }
        }
      }
    }
    if (result.isEmpty())
      throw Store.error(400, "Expected an array of records or clients/appointments arrays");
    return result;
  }

  void add(List<Parsed> out, Map<String, ?> raw, int row, String type, List<String> issues) {
    if (out.size() >= MAX_ROWS) throw Store.error(400, "Maximum 10,000 rows per file");
    if (raw.size() > 100 || raw.values().stream().anyMatch(v -> Store.text(v).length() > 10000))
      throw Store.error(400, "Too many columns or oversized cell");
    var data = canonical(raw);
    if (type == null)
      type =
          data.containsKey("start")
                  || data.containsKey("source_service")
                  || data.containsKey("duration_minutes")
              ? "APPOINTMENT"
              : "CLIENT";
    if (type.equals("CLIENT")
        && data.containsKey("client_source_id")
        && !data.containsKey("source_id")) data.put("source_id", data.remove("client_source_id"));
    out.add(new Parsed(type, data, row, new ArrayList<>(issues)));
  }
}
