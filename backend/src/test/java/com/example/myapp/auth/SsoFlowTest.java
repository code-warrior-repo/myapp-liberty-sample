package com.example.myapp.auth;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import com.example.myapp.api.DataController;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringJUnitConfig(SsoFlowTest.Config.class)
@WebAppConfiguration
@org.springframework.test.context.TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:8080")
class SsoFlowTest {
  @Configuration @EnableWebMvc @EnableWebSecurity
  @Import({SecurityConfig.class, DataController.class})
  static class Config {
    @Bean AuthService service() { return mock(AuthService.class); }
    @Bean AuthController controller(AuthService service, SecurityContextRepository repository) {
      return new AuthController(service, repository, "/frontend/", "https://abc.sso.com/");
    }
  }
  @Autowired WebApplicationContext context;
  @Autowired AuthService service;
  MockMvc mvc;
  @BeforeEach void setup() {
    reset(service);
    when(service.login("jsmith", "secret", "app", "test")).thenReturn(
        new AuthenticatedUser("jsmith", "Jane Smith", "app", List.of("USER")));
    mvc = webAppContextSetup(context).apply(springSecurity()).build();
  }
  private MockHttpSession login() throws Exception {
    return (MockHttpSession) mvc.perform(post("/api/sso-login").servletPath("/api/sso-login").header("Origin", "https://abc.sso.com")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("userid", "jsmith").param("token", "secret").param("context", "app").param("env", "test"))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/frontend/"))
        .andReturn().getRequest().getSession(false);
  }
  @Test void loginPersistsIdentityAndExposesCsrf() throws Exception {
    var session = login();
    assertNotNull(session);
    assertEquals(1800, session.getMaxInactiveInterval());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("jsmith"))
        .andExpect(jsonPath("$.fullName").value("Jane Smith"))
        .andExpect(jsonPath("$.permissions[0]").value("USER"))
        .andExpect(jsonPath("$.csrfToken").isNotEmpty())
        .andExpect(jsonPath("$.token").doesNotExist());
  }
  @Test void loginReplacesExistingSession() throws Exception {
    var old = new MockHttpSession();
    var oldId = old.getId();
    var result = mvc.perform(post("/api/sso-login").servletPath("/api/sso-login").header("Origin", "https://abc.sso.com").session(old)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("userid", "jsmith").param("token", "secret").param("context", "app").param("env", "test"))
        .andExpect(status().isSeeOther()).andReturn();
    assertTrue(old.isInvalid());
    assertNotEquals(oldId, result.getRequest().getSession(false).getId());
  }
  @Test void failedLoginRedirectsWithoutSession() throws Exception {
    when(service.login(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new IllegalArgumentException("Invalid"));
    var result = mvc.perform(post("/api/sso-login").servletPath("/api/sso-login").header("Origin", "https://abc.sso.com")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("https://abc.sso.com/"))
        .andReturn();
    assertNull(result.getRequest().getSession(false));
  }
  @Test void upstreamFailureRedirectsToSso() throws Exception {
    when(service.login(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new org.springframework.web.client.RestClientException("Unavailable"));
    mvc.perform(post("/api/sso-login").servletPath("/api/sso-login").header("Origin", "https://abc.sso.com")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("https://abc.sso.com/"));
  }
  @Test void unauthenticatedReadsAndWritesReturn401() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/data").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"test\"}"))
        .andExpect(status().isUnauthorized());
  }
  @Test void writesRequireCsrf() throws Exception {
    var session = login();
    mvc.perform(post("/api/data").session(session).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"test\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/data").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"hello\"}"))
        .andExpect(status().isOk());
  }
  @Test void missingPermissionReturns403() throws Exception {
    when(service.login(anyString(), anyString(), anyString(), anyString())).thenReturn(
        new AuthenticatedUser("jsmith", "Jane Smith", "app", List.of("REPORT_VIEWER")));
    mvc.perform(post("/api/data").session(login()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"test\"}"))
        .andExpect(status().isForbidden());
  }
  @Test void logoutInvalidatesSession() throws Exception {
    var session = login();
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    assertTrue(session.isInvalid());
  }
  @Test void csrfTokenReturnedToFrontendWorksForWrite() throws Exception {
    var session = login();
    String body = mvc.perform(get("/api/auth/me").session(session)).andReturn().getResponse().getContentAsString();
    String token = com.jayway.jsonpath.JsonPath.read(body, "$.csrfToken");
    String header = com.jayway.jsonpath.JsonPath.read(body, "$.csrfHeader");
    mvc.perform(post("/api/data").session(session).header(header, token)
        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"test\"}"))
        .andExpect(status().isOk());
  }
  @Test void rejectsUntrustedFormOrigin() throws Exception {
    mvc.perform(post("/api/sso-login").servletPath("/api/sso-login")
        .header("Origin", "https://untrusted.example")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isSeeOther()).andExpect(redirectedUrl("https://abc.sso.com/"));
    verifyNoInteractions(service);
  }
  @Test void localPreflightSucceedsWithoutSession() throws Exception {
    mvc.perform(options("/api/data").header("Origin", "http://localhost:8080")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }
  @Test void localUnauthenticatedResponseHasCorsHeaders() throws Exception {
    mvc.perform(get("/api/auth/me").header("Origin", "http://localhost:8080"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }
  @Test void localSessionWorksWithCorsAndCsrf() throws Exception {
    var session = login();
    String body = mvc.perform(get("/api/auth/me").session(session)
        .header("Origin", "http://localhost:8080"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String token = com.jayway.jsonpath.JsonPath.read(body, "$.csrfToken");
    mvc.perform(post("/api/data").session(session).header("Origin", "http://localhost:8080")
        .header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
        .content("{\"value\":\"test\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
  }
  @Test void corsDoesNotBypassCsrf() throws Exception {
    mvc.perform(post("/api/data").session(login()).header("Origin", "http://localhost:8080")
        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"test\"}"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
  }
  @Test void untrustedCorsOriginIsRejected() throws Exception {
    mvc.perform(options("/api/data").header("Origin", "http://untrusted.example")
        .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
