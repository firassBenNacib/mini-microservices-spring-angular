package com.demo.devops.authservice.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.demo.devops.authservice.security.RefreshTokenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class AuthControllerTest {
  private static final String EMAIL = "user@example.com";
  private static final String SECRET = "01234567890123456789012345678901";
  private static final DefaultCsrfToken CSRF_TOKEN =
      new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token");

  private UserRepository userRepository;
  private RefreshTokenService refreshTokenService;
  private AuditClient auditClient;
  private PasswordEncoder passwordEncoder;
  private AuthController controller;
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    refreshTokenService = Mockito.mock(RefreshTokenService.class);
    auditClient = Mockito.mock(AuditClient.class);
    passwordEncoder = Mockito.mock(PasswordEncoder.class);
    jwtService = new JwtService(SECRET, 3600);
    controller =
        new AuthController(
            jwtService,
            refreshTokenService,
            userRepository,
            auditClient,
            passwordEncoder,
            false);
  }

  @Test
  void sessionCreatesCsrfCookieForAnonymousRequests() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session = controller.session(null, request, response, CSRF_TOKEN);
    String setCookieHeader = response.getHeader("Set-Cookie");

    assertFalse(session.authenticated());
    assertNotNull(setCookieHeader);
    assertTrue(setCookieHeader.contains("XSRF-TOKEN="));
  }

  @Test
  void sessionReturnsAuthenticatedUserRole() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            EMAIL,
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    SessionResponse session = controller.session(authentication, request, response, CSRF_TOKEN);

    assertTrue(session.authenticated());
    assertNotNull(session.user());
    assertEquals(EMAIL, session.user().email());
    assertEquals("admin", session.user().role());
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
        controller.login(new LoginRequest(EMAIL, "correct-password"), request, response, CSRF_TOKEN);

    assertTrue(session.authenticated());
    assertEquals(EMAIL, session.user().email());
    assertEquals("admin", session.user().role());

    List<String> cookies = response.getHeaders("Set-Cookie");
    assertEquals(3, cookies.size());
    assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("auth_token=")));
    assertTrue(cookies.stream().anyMatch(cookie -> cookie.contains("refresh_token=")));
    assertEquals(1, cookies.stream().filter(cookie -> cookie.contains("XSRF-TOKEN=")).count());

    verify(auditClient).sendEvent("LOGIN_SUCCESS", EMAIL, "login successful", "auth-service");
    verify(refreshTokenService).createSession(eq(EMAIL), Mockito.anyString(), Mockito.any());
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
        () -> controller.login(new LoginRequest(EMAIL, "wrong-password"), request, response, CSRF_TOKEN));

    verify(auditClient).sendEvent("LOGIN_FAILURE", EMAIL, "invalid password", "auth-service");
    verify(auditClient, never())
        .sendEvent(eq("LOGIN_SUCCESS"), eq(EMAIL), eq("login successful"), eq("auth-service"));
  }

  @Test
  void refreshRotatesSessionWhenRefreshTokenIsValid() {
    UserAccount user = new UserAccount();
    user.setEmail(EMAIL);
    user.setRole("admin");
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    when(refreshTokenService.rotateSession(eq(EMAIL), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new jakarta.servlet.http.Cookie("refresh_token", jwtService.generateRefreshToken(EMAIL, "admin")),
        new jakarta.servlet.http.Cookie("XSRF-TOKEN", "csrf-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session = controller.refresh(request, response, CSRF_TOKEN);

    assertTrue(session.authenticated());
    assertEquals(EMAIL, session.user().email());
    assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(cookie -> cookie.contains("refresh_token=")));
  }

  @Test
  void refreshReturnsAnonymousWhenTheCookieIsMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session = controller.refresh(request, response, CSRF_TOKEN);

    assertFalse(session.authenticated());
    assertEquals(3, response.getHeaders("Set-Cookie").size());
    assertTrue(response.getHeaders("Set-Cookie").stream().allMatch(cookie -> cookie.contains("Max-Age=0")));
    verify(refreshTokenService, never()).rotateSession(any(), any(), any(), any());
  }

  @Test
  void refreshReturnsAnonymousWhenTheUserNoLongerExists() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new jakarta.servlet.http.Cookie("refresh_token", jwtService.generateRefreshToken(EMAIL, "admin")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    SessionResponse session = controller.refresh(request, response, CSRF_TOKEN);

    assertFalse(session.authenticated());
    assertEquals(3, response.getHeaders("Set-Cookie").size());
    assertTrue(response.getHeaders("Set-Cookie").stream().allMatch(cookie -> cookie.contains("Max-Age=0")));
    verify(refreshTokenService, never()).rotateSession(any(), any(), any(), any());
  }

  @Test
  void logoutClearsCookiesAndRevokesRefreshToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new jakarta.servlet.http.Cookie("refresh_token", "refresh-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    controller.logout(request, response);

    verify(refreshTokenService).revokeSession("refresh-cookie");
    assertEquals(3, response.getHeaders("Set-Cookie").size());
    assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(cookie -> cookie.contains("Max-Age=0")));
  }
}
