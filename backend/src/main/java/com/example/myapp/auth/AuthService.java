package com.example.myapp.auth;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
  private final TokenValidator validator;
  public AuthService(TokenValidator validator) { this.validator = validator; }
  public AuthenticatedUser login(String userId, String token, String context, String env) {
    return validator.validate(userId, token, context, env);
  }
}
