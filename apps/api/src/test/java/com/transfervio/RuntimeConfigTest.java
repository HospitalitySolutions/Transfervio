package com.transfervio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RuntimeConfigTest {
  RuntimeConfig local() {
    var c = new RuntimeConfig();
    c.runtime = "local";
    c.endpoint = "http://localhost:9004";
    c.webUrl = "http://localhost:5174";
    c.database = "jdbc:postgresql://localhost:5439/transfervio";
    c.bucket = "local";
    c.accessKey = "local";
    c.secretKey = "synthetic-long-test-secret";
    c.aiProvider = "mock";
    c.destination = "mock";
    return c;
  }

  @Test
  void localStartsWithNoExternalServices() {
    assertDoesNotThrow(() -> local().validate());
  }

  @Test
  void localRejectsRemoteStorage() {
    var c = local();
    c.endpoint = "https://s3.amazonaws.com";
    assertThrows(IllegalStateException.class, c::validate);
  }

  @Test
  void localRejectsRemoteDatabase() {
    var c = local();
    c.database = "jdbc:postgresql://db.example.com:5432/db";
    assertThrows(IllegalStateException.class, c::validate);
  }

  @Test
  void rejectsExternalAiAndWritesByDefault() {
    var c = local();
    c.externalAi = true;
    assertThrows(IllegalStateException.class, c::validate);
    c.externalAi = false;
    c.writeEnabled = true;
    assertThrows(IllegalStateException.class, c::validate);
  }

  @Test
  void cloudHasNoLocalFallback() {
    var c = local();
    c.runtime = "cloud";
    assertThrows(IllegalStateException.class, c::validate);
  }

  @Test
  void loopbackDoesNotAcceptUserInfoTrick() {
    assertFalse(RuntimeConfig.isLoopback("http://localhost@evil.example"));
  }
}
