package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class OpenAiPathTest {
  final Store store = new Store(mock(JdbcTemplate.class), new ObjectMapper());
  final RuntimeConfig config = new RuntimeConfigTest().local();
  final OpenAiTransport transport = mock(OpenAiTransport.class);
  final AiConsent consent = mock(AiConsent.class);
  final OpenAiVision ai = new OpenAiVision(store, config, transport, consent);

  byte[] unseenJpeg() throws Exception {
    var picture =
        new java.awt.image.BufferedImage(13, 17, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(picture, "jpeg", bytes);
    return bytes.toByteArray();
  }

  @Test
  void unseenJpegUsesStrictOutputAndRetainsUncertaintyForHumanReview() throws Exception {
    config.openaiKey = "synthetic-key";
    config.openaiModel = "synthetic-model";
    var data = new LinkedHashMap<String, Object>();
    RecordValidator.FIELDS.forEach(f -> data.put(f, ""));
    data.put("first_name", "Synthetic");
    data.put("source_id", "synthetic-1");
    data.put("service_id", "untrusted-destination-id");
    var output =
        Map.of(
            "records",
            List.of(
                Map.of(
                    "entity",
                    "CLIENT",
                    "data",
                    data,
                    "source_region",
                    List.of(0, 0, 1, 1),
                    "uncertain_fields",
                    List.of("first_name"))));
    when(transport.send(anyString(), anyString()))
        .thenReturn(
            new OpenAiTransport.Response(
                200,
                store.json(
                    Map.of(
                        "status",
                        "completed",
                        "output",
                        List.of(
                            Map.of(
                                "content",
                                List.of(
                                    Map.of(
                                        "type", "output_text", "text", store.json(output)))))))));
    var migration = UUID.randomUUID();
    var upload = UUID.randomUUID();
    var bytes = unseenJpeg();
    var result = ai.extract(bytes, migration, upload);
    verify(consent).require(migration, upload, Store.hash(bytes));
    assertEquals("Synthetic", result.getFirst().data().get("first_name"));
    assertFalse(result.getFirst().data().containsKey("service_id"));
    assertEquals(List.of("AI_REVIEW_REQUIRED"), result.getFirst().parserIssues());
    assertEquals(List.of("first_name"), result.getFirst().sourceFields().get("uncertain_fields"));
    var body = ArgumentCaptor.forClass(String.class);
    verify(transport).send(eq("synthetic-key"), body.capture());
    assertEquals(false, store.map(body.getValue()).get("store"));
    assertTrue(body.getValue().contains("\"strict\":true"));
    assertTrue(body.getValue().contains("data:image/jpeg;base64,"));
  }

  @Test
  void missingConsentPreventsAnyProviderCall() throws Exception {
    doThrow(Store.error(403, "Authorize upload first"))
        .when(consent)
        .require(any(), any(), anyString());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> ai.extract(unseenJpeg(), UUID.randomUUID(), UUID.randomUUID()));
    verifyNoInteractions(transport);
  }

  @Test
  void timeoutAndMalformedResponsesHaveDistinctSafeDiagnostics() throws Exception {
    config.openaiKey = "synthetic-key";
    when(transport.send(anyString(), anyString()))
        .thenThrow(new java.net.http.HttpTimeoutException("sensitive diagnostic"));
    var timeout =
        assertThrows(
            AiFailure.class, () -> ai.extract(unseenJpeg(), UUID.randomUUID(), UUID.randomUUID()));
    assertEquals("AI_TIMEOUT", timeout.code);
    assertTrue(timeout.retryable);
    assertFalse(timeout.getReason().contains("sensitive"));
    reset(transport);
    when(transport.send(anyString(), anyString()))
        .thenReturn(new OpenAiTransport.Response(200, "not-json"));
    var invalid =
        assertThrows(
            AiFailure.class, () -> ai.extract(unseenJpeg(), UUID.randomUUID(), UUID.randomUUID()));
    assertEquals("AI_INVALID_SCHEMA", invalid.code);
    assertFalse(invalid.retryable);
  }
}
