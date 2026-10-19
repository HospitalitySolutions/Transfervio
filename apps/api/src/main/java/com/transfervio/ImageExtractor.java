package com.transfervio;

import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ImageExtractor {
  final Store store;
  final StructuredParser parser;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  OpenAiVision vision;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  RuntimeConfig config;

  ImageExtractor(Store store, StructuredParser parser) {
    this.store = store;
    this.parser = parser;
  }

  String providerName() {
    return config != null && config.externalAi ? "openai-vision" : "mock-fixture";
  }

  List<StructuredParser.Parsed> extract(byte[] bytes) {
    return extract(bytes, null);
  }

  @SuppressWarnings("unchecked")
  List<StructuredParser.Parsed> extract(byte[] bytes, UUID migration) {
    if (config != null && config.aiProvider.equals("openai"))
      return vision.extract(bytes, migration);
    try {
      var resource = new ClassPathResource("synthetic-images.json");
      if (resource.exists()) {
        var fixtures =
            store.map(
                new String(
                    resource.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
        Object fixture = fixtures.get(Store.hash(bytes));
        if (fixture instanceof Map<?, ?> entry)
          return parser.json(
              store.json(entry.get("records")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw Store.error(400, "Synthetic image manifest could not be read");
    }
    return List.of(
        new StructuredParser.Parsed(
            "CLIENT", new LinkedHashMap<>(), 1, List.of("MOCK_IMAGE_UNRECOGNIZED")));
  }
}
