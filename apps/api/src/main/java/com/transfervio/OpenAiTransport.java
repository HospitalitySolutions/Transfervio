package com.transfervio;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTransport {
  final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public record Response(int status, String body) {}

  public Response send(String key, String body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return new Response(response.statusCode(), response.body());
  }
}
