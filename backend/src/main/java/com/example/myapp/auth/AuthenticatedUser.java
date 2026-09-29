package com.example.myapp.auth;
import java.io.Serializable;
import java.util.List;
public record AuthenticatedUser(String userId, String fullName, String context,
    List<String> permissions) implements Serializable, java.security.Principal {
  @Override public String getName() { return userId; }
  public AuthenticatedUser { permissions = List.copyOf(permissions); }
}
