package com.transfervio;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(AiFailure.class)
  ResponseEntity<?> ai(AiFailure e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("code", e.code, "message", e.getReason(), "retryable", e.retryable));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "Request rejected" : e.getReason()));
  }

  @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class})
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "Conflicting record or invalid scope"));
  }

  @ExceptionHandler({
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("message", "Invalid request fields"));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> large(Exception e) {
    return ResponseEntity.status(413)
        .body(Map.of("message", "File too large; maximum 10 MB per file"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e) {
    return ResponseEntity.internalServerError()
        .body(Map.of("message", "Operation failed. Retry or contact the local administrator."));
  }
}
