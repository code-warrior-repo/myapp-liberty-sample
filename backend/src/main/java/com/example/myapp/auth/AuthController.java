package com.example.myapp.auth;

import java.net.URI;
import java.util.Map;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService service;
  private final SecurityContextRepository repository;
  private final URI frontendUrl, ssoUrl;
  public AuthController(AuthService service, SecurityContextRepository repository,
      @Value("${app.frontend-url}") String frontendUrl, @Value("${app.sso-url}") String ssoUrl) {
    this.service = service;
    this.repository = repository;
    this.frontendUrl = URI.create(frontendUrl);
    this.ssoUrl = URI.create(ssoUrl);
  }

  @PostMapping(value = "/sso-login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<Void> login(@RequestParam(defaultValue = "") String userid,
      @RequestParam(defaultValue = "") String token, @RequestParam(defaultValue = "") String context,
      @RequestParam(defaultValue = "") String env, HttpServletRequest req, HttpServletResponse res) {
    // Only the configured SSO origin may submit this CSRF-exempt browser form.
    String expectedOrigin = ssoUrl.getScheme() + "://" + ssoUrl.getRawAuthority();
    if (!expectedOrigin.equals(req.getHeader("Origin"))) return redirect(ssoUrl);
    // Discard any previous identity, including when the new login fails.
    var oldSession = req.getSession(false);
    if (oldSession != null) oldSession.invalidate();
    SecurityContextHolder.clearContext();
    try {
      var user = service.login(userid, token, context, env);
      var session = req.getSession(true); // Fresh ID prevents session fixation.
      session.setMaxInactiveInterval(30 * 60);
      var authentication = new UsernamePasswordAuthenticationToken(user, null,
          user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
      var securityContext = SecurityContextHolder.createEmptyContext();
      securityContext.setAuthentication(authentication);
      SecurityContextHolder.setContext(securityContext);
      repository.saveContext(securityContext, req, res);
      return redirect(frontendUrl);
    } catch (IllegalArgumentException | RestClientException ex) {
      // Do not log tokens, upstream response bodies, or put credentials in redirects.
      var session = req.getSession(false);
      if (session != null) session.invalidate();
      SecurityContextHolder.clearContext();
      return redirect(ssoUrl);
    }
  }

  @GetMapping("/auth/me")
  public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user, CsrfToken csrf) {
    return Map.of("userId", user.userId(), "fullName", user.fullName(), "context", user.context(),
        "permissions", user.permissions(), "csrfToken", csrf.getToken(), "csrfHeader", csrf.getHeaderName());
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest req) {
    var session = req.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }
  private ResponseEntity<Void> redirect(URI destination) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(destination)
        .cacheControl(CacheControl.noStore()).build();
  }
}
