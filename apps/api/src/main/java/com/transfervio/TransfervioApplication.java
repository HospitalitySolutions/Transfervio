package com.transfervio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@EnableScheduling
public class TransfervioApplication {
  public static void main(String[] args) {
    var application = new SpringApplication(TransfervioApplication.class);
    application.addInitializers(new RuntimeSafetyInitializer());
    application.run(args);
  }
}
