package com.transfervio;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/invitations/{token}")
public class SourceController {
  final SourceMappings mappings;
  final AiConsent consent;

  SourceController(SourceMappings mappings, AiConsent consent) {
    this.mappings = mappings;
    this.consent = consent;
  }

  record Consent(boolean authorized, boolean synthetic) {}

  record Suggestion(String table) {}

  @GetMapping("/source-mappings")
  List<Map<String, Object>> plans(@PathVariable String token) {
    return mappings.plans(token);
  }

  @PutMapping("/source-mappings/{upload}")
  void save(
      @PathVariable String token,
      @PathVariable UUID upload,
      @RequestBody SourceMappings.Mapping request) {
    mappings.save(token, upload, request);
  }

  @PostMapping("/source-mappings/{upload}/suggest")
  Map<String, Object> suggest(
      @PathVariable String token, @PathVariable UUID upload, @RequestBody Suggestion request) {
    return mappings.suggest(token, upload, request.table());
  }

  @PutMapping("/uploads/{upload}/ai-consent")
  void consent(
      @PathVariable String token, @PathVariable UUID upload, @RequestBody Consent request) {
    consent.set(token, upload, request.authorized(), request.synthetic());
  }
}
