package com.example.myapp.auth;

import org.springframework.context.annotation.*;
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
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository)
      throws Exception {
    return http
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
