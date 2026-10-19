package com.transfervio;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventController {
  final Webhooks hooks;
  final Store store;

  EventController(Webhooks hooks, Store store) {
    this.hooks = hooks;
    this.store = store;
  }

  record Subscription(String target_url) {}

  @PostMapping("/v1/webhook-subscriptions")
  Map<String, Object> subscribe(
      @AuthenticationPrincipal AuthService.Identity who, @RequestBody Subscription req) {
    who.admin();
    return hooks.subscribe(who.partnerId(), req.target_url());
  }

  @GetMapping("/v1/webhook-subscriptions")
  List<Map<String, Object>> subscriptions(@AuthenticationPrincipal AuthService.Identity who) {
    return store.rows(
        "select id,target_url,active,created_at from webhook_subscriptions where partner_id=?",
        who.partnerId());
  }

  @DeleteMapping("/v1/webhook-subscriptions/{id}")
  void remove(@AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    who.admin();
    if (store.db.update(
            "update webhook_subscriptions set active=false where id=? and partner_id=?",
            id,
            who.partnerId())
        == 0) throw Store.error(404, "Subscription not found");
  }

  @GetMapping("/v1/events")
  List<Map<String, Object>> events(
      @AuthenticationPrincipal AuthService.Identity who,
      @RequestParam(defaultValue = "0") int cursor) {
    if (cursor < 0) throw Store.error(400, "Invalid cursor");
    return store.rows(
        "select id,event_type,payload,created_at from outbox_events where partner_id=? order by"
            + " created_at,id limit 100 offset ?",
        who.partnerId(),
        cursor);
  }

  @GetMapping("/v1/webhook-deliveries")
  List<Map<String, Object>> deliveries(@AuthenticationPrincipal AuthService.Identity who) {
    return store.rows(
        "select d.* from webhook_deliveries d join webhook_subscriptions s on"
            + " s.id=d.subscription_id where s.partner_id=? order by d.available_at desc limit 200",
        who.partnerId());
  }
}
