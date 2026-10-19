package com.transfervio;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MigrationController {
  final Scopes scope;
  final UploadService uploads;
  final MigrationService migrations;

  MigrationController(Scopes scope, UploadService uploads, MigrationService migrations) {
    this.scope = scope;
    this.uploads = uploads;
    this.migrations = migrations;
  }

  record Approval(int revision, Map<String, List<String>> export_fields) {}

  @PostMapping(value = "/public/invitations/{token}/uploads", consumes = "multipart/form-data")
  List<Map<String, Object>> upload(
      @PathVariable String token, @RequestParam("files") List<MultipartFile> files) {
    return uploads.upload(token, files);
  }

  @GetMapping("/public/invitations/{token}/uploads/{id}")
  org.springframework.http.ResponseEntity<byte[]> original(
      @PathVariable String token, @PathVariable UUID id) {
    var m = scope.invitation(token, false);
    var file = uploads.original(Store.id(m.get("id")), id);
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
        .header(
            "Content-Disposition",
            org.springframework.http.ContentDisposition.attachment()
                .filename(file.name(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(file.bytes());
  }

  @PostMapping("/public/invitations/{token}/process")
  void process(@PathVariable String token) {
    migrations.process(token);
  }

  @GetMapping("/public/invitations/{token}/preview")
  Map<String, Object> preview(@PathVariable String token) {
    return migrations.preview(scope.invitation(token, false));
  }

  @PatchMapping("/public/invitations/{token}/mappings")
  void mappings(@PathVariable String token, @RequestBody MigrationService.Corrections changes) {
    migrations.correct(token, changes);
  }

  @PostMapping("/public/invitations/{token}/approve")
  Map<String, Object> approve(@PathVariable String token, @RequestBody Approval request) {
    return migrations.approve(token, request.revision(), request.export_fields());
  }

  @GetMapping("/v1/migrations/{id}")
  Map<String, Object> detail(
      @AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    var preview = migrations.preview(scope.migration(who.partnerId(), id, false));
    if (who.kind().equals("API_KEY"))
      return Map.of(
          "migration",
          preview.get("migration"),
          "jobs",
          preview.get("jobs"),
          "delivery_results",
          preview.get("delivery_results"));
    return preview;
  }
}
