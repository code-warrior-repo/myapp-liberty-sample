package com.example.myapp.auth;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenValidatorTest {
  HttpServer server;
  TokenValidator validator;
  AtomicReference<String> validationBody = new AtomicReference<>("{\"valid\":true,\"fullName\":\"Jane Smith\"}");
  AtomicReference<String> permissionsBody = new AtomicReference<>("{\"permissions\":[\"USER\"]}");
  AtomicReference<String> validationRequest = new AtomicReference<>();
  AtomicReference<String> permissionsRequest = new AtomicReference<>();
  @BeforeEach void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    endpoint("/validate", validationBody, validationRequest);
    endpoint("/permissions", permissionsBody, permissionsRequest);
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    validator = new TokenValidator(base + "/validate", base + "/permissions", "test");
  }
  void endpoint(String path, AtomicReference<String> body, AtomicReference<String> request) {
    server.createContext(path, exchange -> {
      request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
  }
  @AfterEach void stop() { server.stop(0); }
  @Test void validatesAndFetchesPermissionsWithoutForwardingTokenToPermissions() {
    var user = validator.validate("jsmith", "secret", "app", "test");
    assertEquals("Jane Smith", user.fullName());
    assertEquals("jsmith", user.getName());
    assertTrue(validationRequest.get().contains("\"token\":\"secret\""));
    assertFalse(permissionsRequest.get().contains("secret"));
    assertTrue(permissionsRequest.get().contains("\"userId\":\"jsmith\""));
  }
  @Test void rejectsInvalidTokenBeforePermissionsCall() {
    validationBody.set("{\"valid\":false,\"fullName\":\"Jane Smith\"}");
    assertThrows(IllegalArgumentException.class, () -> validator.validate("jsmith", "secret", "app", "test"));
    assertNull(permissionsRequest.get());
  }
  @Test void rejectsWrongEnvironmentBeforeCallingServices() {
    assertThrows(IllegalArgumentException.class, () -> validator.validate("jsmith", "secret", "app", "prod"));
    assertNull(validationRequest.get());
  }
  @Test void rejectsEmptyPermissions() {
    permissionsBody.set("{\"permissions\":[]}");
    assertThrows(IllegalArgumentException.class, () -> validator.validate("jsmith", "secret", "app", "test"));
  }
  @Test void rejectsBlankPermissions() {
    permissionsBody.set("{\"permissions\":[\"\"]}");
    assertThrows(IllegalArgumentException.class, () -> validator.validate("jsmith", "secret", "app", "test"));
  }
  @Test void rejectsMissingName() {
    validationBody.set("{\"valid\":true}");
    assertThrows(IllegalArgumentException.class, () -> validator.validate("jsmith", "secret", "app", "test"));
  }
}
