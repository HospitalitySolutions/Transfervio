package com.transfervio;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class RetentionController {
  final RetentionService retention;

  RetentionController(RetentionService retention) {
    this.retention = retention;
  }

  @DeleteMapping("/v1/migrations/{id}")
  void delete(@AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    who.admin();
    retention.deleteMigration(who.partnerId(), id);
  }
}
