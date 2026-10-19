package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class BoundaryTest {
  @Test
  void webhookRejectsSsrfRedirectAndAlternateLoopbackNames() {
    for (String url :
        List.of(
            "http://169.254.169.254/events",
            "https://app.calendra.si/events",
            "http://127.0.0.1:8099/events?url=evil",
            "http://127.0.0.1:8099/events#x",
            "http://evil@127.0.0.1:8099/events",
            "http://localhost:8099/events",
            "http://127.0.0.1:8091/events")) assertFalse(Webhooks.allowedTarget(url, 8099));
    assertTrue(Webhooks.allowedTarget("http://127.0.0.1:8099/events", 8099));
  }

  @Test
  void signaturesBindPayloadAndTimestamp() {
    assertNotEquals(
        Webhooks.hmac("secret", "1.event.payload"), Webhooks.hmac("secret", "2.event.payload"));
    assertNotEquals(
        Webhooks.hmac("secret", "1.event.payload"), Webhooks.hmac("other", "1.event.payload"));
  }

  @Test
  void openAiCannotRunWithoutExplicitOptIn() {
    var c = new RuntimeConfigTest().local();
    c.aiProvider = "openai";
    assertThrows(IllegalStateException.class, c::validate);
    c.externalAi = true;
    c.openaiKey = "synthetic-test-key";
    c.openaiModel = "explicit-model";
    c.syntheticOnly = false;
    assertThrows(IllegalStateException.class, c::validate);
  }

  @Test
  void visionOutputRejectsUnknownFieldsAndTypes() {
    var s = new Store(null, new ObjectMapper());
    var vision = new OpenAiVision(s, new RuntimeConfigTest().local());
    assertThrows(
        Exception.class,
        () ->
            vision.parseOutput(
                "{\"records\":[{\"entity\":\"CLIENT\",\"data\":{\"tenant_id\":\"forged\"}}]}"));
  }

  @Test
  void visionOutputRequiresHumanSourceReview() {
    var s = new Store(null, new ObjectMapper());
    var vision = new OpenAiVision(s, new RuntimeConfigTest().local());
    var data = new TreeMap<String, Object>();
    RecordValidator.FIELDS.forEach(f -> data.put(f, ""));
    data.put("display_name", "Synthetic Example");
    var result =
        vision.parseOutput(
            s.json(Map.of("records", List.of(Map.of("entity", "CLIENT", "data", data)))));
    assertEquals(List.of("AI_REVIEW_REQUIRED"), result.getFirst().parserIssues());
  }
}
