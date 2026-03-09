package com.demo.devops.authservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.devops.authservice.client.AuditClient;
import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.dto.LoginRequest;
import com.demo.devops.authservice.dto.SessionResponse;
import com.demo.devops.authservice.repository.UserRepository;
import com.demo.devops.authservice.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthControllerTest {
  private static final String EMAIL = "user@example.com";

  private UserRepository userRepository;
  private AuditClient auditClient;
  private PasswordEncoder passwordEncoder;
  private AuthController controller;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    auditClient = Mockito.mock(AuditClient.class);
    passwordEncoder = Mockito.mock(PasswordEncoder.class);
    controller =
        new AuthController(
            new JwtService("01234567890123456789012345678901", 3600),
            userRepository,
            auditClient,
            passwordEncoder,
            false);
  }

  @Test
  void sessionCreatesCsrfCookieForAnonymousRequests() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session = controller.session(null, request, response);
    String setCookieHeader = response.getHeader("Set-Cookie");

    assertFalse(session.authenticated());
    assertNotNull(setCookieHeader);
    assertTrue(setCookieHeader.contains("XSRF-TOKEN="));
  }

  @Test
  void loginWritesSessionCookiesAndAuditsSuccess() {
    UserAccount user = new UserAccount();
    user.setEmail(EMAIL);
    user.setPasswordHash("hashed-password");
    user.setRole("admin");

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session =
        controller.login(new LoginRequest(EMAIL, "correct-password"), request, response);

    assertTrue(session.authenticated());
    assertEquals(EMAIL, session.user().email());
    assertEquals("admin", session.user().role());

    List<String> cookies = response.getHeaders("Set-Cookie");
    assertEquals(3, cookies.size());
    assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("auth_token=")));
    assertEquals(2, cookies.stream().filter(cookie -> cookie.contains("XSRF-TOKEN=")).count());

    verify(auditClient).sendEvent("LOGIN_SUCCESS", EMAIL, "login successful", "auth-service");
  }

  @Test
  void loginAuditsFailureWhenPasswordIsInvalid() {
    UserAccount user = new UserAccount();
    user.setEmail(EMAIL);
    user.setPasswordHash("hashed-password");
    user.setRole("user");

    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThrows(
        RuntimeException.class,
        () -> controller.login(new LoginRequest(EMAIL, "wrong-password"), request, response));

    verify(auditClient).sendEvent("LOGIN_FAILURE", EMAIL, "invalid password", "auth-service");
    verify(auditClient, never())
        .sendEvent(eq("LOGIN_SUCCESS"), eq(EMAIL), eq("login successful"), eq("auth-service"));
  }
}
