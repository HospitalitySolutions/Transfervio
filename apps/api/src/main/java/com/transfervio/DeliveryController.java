package com.transfervio;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/migrations/{id}")
public class DeliveryController {
  final DeliveryService delivery;

  DeliveryController(DeliveryService delivery) {
    this.delivery = delivery;
  }

  record MockRequest(Set<UUID> fail_record_ids) {}

  record Receipts(List<DeliveryService.Receipt> results) {}

  @GetMapping("/export")
  Map<String, Object> export(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "clients") String entity,
      @RequestParam(defaultValue = "0") int cursor,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String fields) {
    return delivery.export(who.partnerId(), id, entity, cursor, limit, fields, who.exportFields());
  }

  @GetMapping("/dry-run")
  Map<String, Object> dryRun(
      @AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    who.admin();
    return delivery.dryRun(who.partnerId(), id);
  }

  @PostMapping("/delivery-results")
  void receipts(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @RequestBody Receipts receipts) {
    delivery.acknowledge(who.partnerId(), id, receipts.results());
  }

  @PostMapping("/mock-delivery")
  Map<String, Object> mock(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @RequestBody(required = false) MockRequest request) {
    who.admin();
    return delivery.deliverMock(
        who.partnerId(),
        id,
        request == null || request.fail_record_ids() == null
            ? Set.of()
            : request.fail_record_ids());
  }
}
