package com.transfervio;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.boot.actuate.health.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import java.net.URI;

@Configuration
public class RuntimeConfig {
    @Value("${app.runtime}") String runtime;
    @Value("${app.storage-endpoint}") String endpoint;
    @Value("${app.storage-bucket}") String bucket;
    @Value("${app.storage-access-key}") String accessKey;
    @Value("${app.storage-secret-key}") String secretKey;
    @Value("${app.storage-region}") String region;
    @Value("${app.web-url}") String webUrl;
    @Value("${spring.datasource.url}") String database;
    @Value("${app.ai-provider}") String aiProvider;
    @Value("${app.external-ai-enabled}") boolean externalAi;
    @Value("${app.destination-mode}") String destination;
    @Value("${app.import-write-enabled}") boolean writeEnabled;

    @PostConstruct void validate() {
        if (!aiProvider.equals("mock") || externalAi) throw new IllegalStateException("External AI is not enabled in this foundation");
        if (!destination.equals("mock") && !destination.equals("calendra")) throw new IllegalStateException("Unknown destination");
        if (writeEnabled) throw new IllegalStateException("Remote destination writes require a separately reviewed integration");
        if (accessKey.isBlank() || secretKey.length() < 16 || bucket.isBlank()) throw new IllegalStateException("Storage configuration is incomplete");
        if (runtime.equals("local")) {
            if (!isLoopback(endpoint) || !isLoopback(webUrl) || !database.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[a-zA-Z0-9_-]+"))
                throw new IllegalStateException("Local profile requires loopback database, storage and browser URLs");
        } else if (!runtime.equals("cloud") || !endpoint.startsWith("https://") || !webUrl.startsWith("https://") || !database.contains("sslmode=verify-full")) {
            throw new IllegalStateException("Cloud requires HTTPS storage/web and verified database TLS");
        }
    }
    static boolean isLoopback(String value) {
        try { var uri = URI.create(value); return uri.getUserInfo() == null && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())); }
        catch (Exception e) { return false; }
    }
    @Bean MinioClient minioClient() throws Exception {
        var client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).region(region).build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            if (!runtime.equals("local")) throw new IllegalStateException("Cloud bucket must be provisioned separately");
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        return client;
    }
    @Bean HealthIndicator storageHealth(MinioClient client) {
        return () -> { try { return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()) ? Health.up().build() : Health.down().build(); }
            catch (Exception e) { return Health.down().build(); } };
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        // The application authenticates scoped bearer capabilities. Cookies are never credentials.
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/api/runtime", "/v3/api-docs/**", "/swagger-ui/**").permitAll().anyRequest().denyAll())
            .headers(h -> h.frameOptions(f -> f.deny())).build();
    }
}
