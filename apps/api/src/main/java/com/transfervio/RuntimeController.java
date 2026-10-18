package com.transfervio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RuntimeController {
    private final JdbcTemplate db;
    private final RuntimeConfig config;
    public RuntimeController(JdbcTemplate db, RuntimeConfig config) { this.db = db; this.config = config; }
    @GetMapping("/api/runtime") Map<String,Object> runtime() {
        return Map.of("product", "Transfervio", "runtime", config.runtime, "ai_provider", config.aiProvider,
            "destination_mode", config.destination, "import_write_enabled", config.writeEnabled,
            "first_started_at", db.queryForObject("select first_started_at::text from runtime_checkpoint where id=1", String.class));
    }
}
