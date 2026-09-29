package com.example.myapp.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TokenValidator {
  private final RestClient client;
  private final String validationUrl, permissionsUrl, environment;

  public TokenValidator(@Value("${app.validation-url}") String validationUrl,
      @Value("${app.permissions-url}") String permissionsUrl,
      @Value("${app.environment}") String environment) {
    this.validationUrl = validationUrl;
    this.permissionsUrl = permissionsUrl;
    this.environment = environment;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(5000);
    client = RestClient.builder().requestFactory(factory).build();
  }

  // Adapt these DTOs when the real service contracts are supplied.
  public record ValidationRequest(String userid, String token, String context, String env) {}
  public record ValidationResponse(boolean valid, String fullName) {}
  public record PermissionsRequest(String userId, String context) {}
  public record PermissionsResponse(List<String> permissions) {}

  public AuthenticatedUser validate(String userId, String token, String context, String env) {
    if (blank(userId) || blank(token) || blank(context) || blank(env)
        || !environment.equals(env) || blank(validationUrl) || blank(permissionsUrl)) {
      throw new IllegalArgumentException("Invalid login configuration or fields");
    }
    var validation = client.post().uri(validationUrl).contentType(MediaType.APPLICATION_JSON)
        .body(new ValidationRequest(userId, token, context, env)).retrieve().body(ValidationResponse.class);
    if (validation == null || !validation.valid() || blank(validation.fullName())) {
      throw new IllegalArgumentException("Invalid identity");
    }
    var result = client.post().uri(permissionsUrl).contentType(MediaType.APPLICATION_JSON)
        .body(new PermissionsRequest(userId, context)).retrieve().body(PermissionsResponse.class);
    if (result == null || result.permissions() == null || result.permissions().isEmpty()
        || result.permissions().stream().anyMatch(TokenValidator::blank)) {
      throw new IllegalArgumentException("Missing permissions");
    }
    return new AuthenticatedUser(userId, validation.fullName(), context,
        result.permissions().stream().distinct().toList());
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
}
