package com.transfervio;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Safe diagnostics: never persist provider response text or source records in errors. */
public class AiFailure extends ResponseStatusException {
  final String code;
  final boolean retryable;

  AiFailure(String code, String message, boolean retryable) {
    super(HttpStatus.BAD_GATEWAY, message);
    this.code = code;
    this.retryable = retryable;
  }
}
