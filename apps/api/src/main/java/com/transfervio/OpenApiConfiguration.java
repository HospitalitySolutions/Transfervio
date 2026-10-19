package com.transfervio;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenApiCustomizer documentedContracts() {
    return generated -> {
      try (var input = new ClassPathResource("api-contract.json").getInputStream()) {
        var documented = Json31.mapper().readValue(input, OpenAPI.class);
        generated.setOpenapi("3.1.0");
        generated.setInfo(documented.getInfo());
        generated.setServers(documented.getServers());
        generated.setWebhooks(documented.getWebhooks());
        generated.getComponents().getSchemas().putAll(documented.getComponents().getSchemas());
        generated
            .getComponents()
            .setSecuritySchemes(documented.getComponents().getSecuritySchemes());
        // Preserve Spring's live route/request inventory; enrich response and authorization
        // contracts.
        generated
            .getPaths()
            .forEach(
                (path, item) ->
                    item.readOperationsMap()
                        .forEach(
                            (method, operation) -> {
                              var reference = documented.getPaths().get(path);
                              if (reference == null
                                  || reference.readOperationsMap().get(method) == null) return;
                              var contract = reference.readOperationsMap().get(method);
                              operation.setSummary(contract.getSummary());
                              operation.setDescription(contract.getDescription());
                              operation.setSecurity(contract.getSecurity());
                              operation.setResponses(contract.getResponses());
                              operation.setParameters(contract.getParameters());
                            }));
      } catch (Exception e) {
        throw new IllegalStateException("Versioned API contract could not be loaded", e);
      }
    };
  }
}
