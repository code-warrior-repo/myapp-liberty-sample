package com.example.myapp.auth;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
  @Bean CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
        .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
    configuration.setAllowCredentials(true);
    configuration.validateAllowCredentials();
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
    configuration.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return request -> {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      // SSO is a top-level form navigation, not a frontend fetch. Its Origin is
      // checked by AuthController; do not apply the frontend CORS allowlist to it.
      if ("/api/sso-login".equals(path)) return null;
      return source.getCorsConfiguration(request);
    };
  }
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository,
      CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    return http
        .cors(c -> c.configurationSource(corsConfigurationSource))
        .csrf(c -> c.ignoringRequestMatchers(r -> "POST".equals(r.getMethod())
            && "/api/sso-login".equals(r.getServletPath())))
        .securityContext(c -> c.securityContextRepository(repository).requireExplicitSave(true))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
        .authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.POST,
            "/api/sso-login").permitAll().anyRequest().authenticated())
        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, res, ex) -> res.setStatus(401))
            .accessDeniedHandler((req, res, ex) -> {
              var session = req.getSession(false);
              var context = session == null ? null : session.getAttribute(
                  HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
              boolean authenticated = context instanceof org.springframework.security.core.context.SecurityContext c
                  && c.getAuthentication() != null && c.getAuthentication().isAuthenticated();
              res.setStatus(authenticated ? 403 : 401);
            }))
        .build();
  }
}
