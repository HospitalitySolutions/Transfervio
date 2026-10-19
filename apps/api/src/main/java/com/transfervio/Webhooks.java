package com.transfervio;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class Webhooks {
  final Store store;
  final RuntimeConfig config;

  @Value("${WEBHOOK_SIGNING_MASTER}")
  String master;

  @Value("${WEBHOOK_RECEIVER_PORT:8099}")
  int port;

  Webhooks(Store store, RuntimeConfig config) {
    this.store = store;
    this.config = config;
  }

  @jakarta.annotation.PostConstruct
  void validate() {
    if (master.length() < 32)
      throw new IllegalStateException("Webhook signing master must be at least 32 characters");
  }

  static boolean allowedTarget(String url, int port) {
    try {
      var uri = URI.create(url);
      return "http".equals(uri.getScheme())
          && "127.0.0.1".equals(uri.getHost())
          && uri.getPort() == port
          && "/events".equals(uri.getRawPath())
          && uri.getRawQuery() == null
          && uri.getFragment() == null
          && uri.getUserInfo() == null;
    } catch (Exception e) {
      return false;
    }
  }

  static String hmac(String key, String data) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Signing failed");
    }
  }

  String secret(UUID subscription) {
    return hmac(master, "transfervio-webhook:" + subscription);
  }

  Map<String, Object> subscribe(UUID partner, String url) {
    if (!config.runtime.equals("local") || !allowedTarget(url, port))
      throw Store.error(
          400, "Only the configured loopback webhook receiver is supported in this MVP");
    UUID id = UUID.randomUUID();
    store.db.update(
        "insert into webhook_subscriptions(id,partner_id,target_url) values (?,?,?)",
        id,
        partner,
        url);
    return Map.of("id", id, "signing_secret", secret(id), "target_url", url);
  }

  @Scheduled(fixedDelay = 3000, initialDelay = 5000)
  public void deliver() {
    store.db.update(
        "insert into webhook_deliveries(event_id,subscription_id) select e.id,s.id from"
            + " outbox_events e join webhook_subscriptions s on s.partner_id=e.partner_id and"
            + " s.active and s.created_at<=e.created_at on conflict do nothing");
    var jobs =
        store.rows(
            "update webhook_deliveries set"
                + " status='RUNNING',attempts=attempts+1,lease_until=now()+interval '30 seconds'"
                + " where (event_id,subscription_id)=(select event_id,subscription_id from"
                + " webhook_deliveries where attempts<5 and ((status='PENDING' and"
                + " available_at<=now()) or (status='RUNNING' and lease_until<now())) order by"
                + " available_at for update skip locked limit 1) returning *");
    if (jobs.isEmpty()) return;
    var job = jobs.getFirst();
    int code = 0;
    try {
      var subscription =
          store.one(
              "select * from webhook_subscriptions where id=? and active",
              job.get("subscription_id"));
      String url = Store.text(subscription.get("target_url"));
      if (!config.runtime.equals("local") || !allowedTarget(url, port))
        throw new IllegalArgumentException("Target not allowed");
      var event = store.one("select payload from outbox_events where id=?", job.get("event_id"));
      String body = store.json(event.get("payload")),
          timestamp = String.valueOf(Instant.now().getEpochSecond());
      String signature =
          hmac(
              secret(Store.id(job.get("subscription_id"))),
              timestamp + "." + job.get("event_id") + "." + body);
      var request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .header("X-Transfervio-Event", Store.text(job.get("event_id")))
              .header("X-Transfervio-Timestamp", timestamp)
              .header("X-Transfervio-Signature", "sha256=" + signature)
              .header("X-Transfervio-Subscription", Store.text(job.get("subscription_id")))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      code =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(3))
              .followRedirects(HttpClient.Redirect.NEVER)
              .build()
              .send(request, HttpResponse.BodyHandlers.discarding())
              .statusCode();
    } catch (Exception ignored) {
    }
    boolean success = code >= 200 && code < 300;
    String status =
        success ? "DONE" : ((Number) job.get("attempts")).intValue() >= 5 ? "FAILED" : "PENDING";
    store.db.update(
        "update webhook_deliveries set"
            + " status=?,lease_until=null,last_http_status=?,available_at=now()+(attempts*attempts*interval"
            + " '3 seconds') where event_id=? and subscription_id=?",
        status,
        code,
        job.get("event_id"),
        job.get("subscription_id"));
  }
}
