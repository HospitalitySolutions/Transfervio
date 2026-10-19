package com.transfervio;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

// Runs before DataSource/Flyway creation: unsafe profiles never get a chance to connect.
public class RuntimeSafetyInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext context) {
    validate(context.getEnvironment());
  }

  static void validate(Environment env) {
    String runtime = env.getRequiredProperty("app.runtime"),
        db = env.getRequiredProperty("spring.datasource.url"),
        storage = env.getRequiredProperty("app.storage-endpoint"),
        web = env.getRequiredProperty("app.web-url");
    boolean container = Boolean.parseBoolean(env.getProperty("LOCAL_CONTAINER", "false"));
    if (runtime.equals("local")) {
      boolean
          localDb =
              db.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_-]+"),
          localStorage = RuntimeConfig.isLoopback(storage);
      if (container) {
        localDb = db.matches("jdbc:postgresql://postgres:5432/[a-zA-Z0-9_-]+");
        localStorage = storage.equals("http://minio:9000");
      }
      if (!localDb || !localStorage || !RuntimeConfig.isLoopback(web))
        throw new IllegalStateException(
            "Local profile requires local database, object storage and web URLs");
    } else if (!runtime.equals("cloud")
        || !db.contains("sslmode=verify-full")
        || !storage.startsWith("https://")
        || !web.startsWith("https://"))
      throw new IllegalStateException("Cloud profile is incomplete or lacks verified TLS");
    for (String key :
        java.util.List.of(
            "spring.datasource.username",
            "spring.datasource.password",
            "app.storage-bucket",
            "app.storage-access-key",
            "app.storage-secret-key",
            "app.storage-region",
            "WEBHOOK_SIGNING_MASTER"))
      if (env.getRequiredProperty(key).isBlank())
        throw new IllegalStateException("Missing mandatory runtime setting: " + key);
  }
}
