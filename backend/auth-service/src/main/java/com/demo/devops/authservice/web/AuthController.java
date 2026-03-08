package com.demo.devops.authservice.web;

import com.demo.devops.authservice.client.AuditClient;
import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.dto.LoginRequest;
import com.demo.devops.authservice.dto.SessionResponse;
import com.demo.devops.authservice.dto.StatusResponse;
import com.demo.devops.authservice.repository.UserRepository;
import com.demo.devops.authservice.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private static final String AUTH_COOKIE_NAME = "auth_token";
  private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final AuditClient auditClient;
  private final PasswordEncoder passwordEncoder;
  private final boolean cookieSecure;

  public AuthController(
      JwtService jwtService,
      UserRepository userRepository,
      AuditClient auditClient,
      PasswordEncoder passwordEncoder,
      @Value("${app.cookie.secure:true}") boolean cookieSecure) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.auditClient = auditClient;
    this.passwordEncoder = passwordEncoder;
    this.cookieSecure = cookieSecure;
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @GetMapping("/session")
  public SessionResponse session(
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    ensureCsrfCookie(request, response);
    return sessionResponse(authentication);
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public SessionResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    UserAccount user = userRepository.findByEmailIgnoreCase(request.email())
        .orElse(null);
    if (user == null) {
      auditClient.sendEvent("LOGIN_FAILURE", request.email(), "user not found", "auth-service");
      throw new InvalidCredentialsException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditClient.sendEvent("LOGIN_FAILURE", request.email(), "invalid password", "auth-service");
      throw new InvalidCredentialsException();
    }

    String token = jwtService.generateToken(user.getEmail(), user.getRole());
    String csrfToken = ensureCsrfCookie(httpRequest, response);
    writeSessionCookies(response, token, csrfToken);
    auditClient.sendEvent("LOGIN_SUCCESS", user.getEmail(), "login successful", "auth-service");
    return new SessionResponse(
        true,
        jwtService.getExpirationSeconds(),
        new SessionResponse.UserInfo(user.getEmail(), user.getRole()));
  }

  @PostMapping("/logout")
  public StatusResponse logout(HttpServletResponse response) {
    clearSessionCookies(response);
    return new StatusResponse("ok");
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class InvalidCredentialsException extends RuntimeException {}

  private SessionResponse sessionResponse(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
      return new SessionResponse(false, jwtService.getExpirationSeconds(), null);
    }

    String role = authentication.getAuthorities().stream()
        .findFirst()
        .map((authority) -> authority.getAuthority().replaceFirst("^ROLE_", "").toLowerCase())
        .orElse("user");
    return new SessionResponse(
        true,
        jwtService.getExpirationSeconds(),
        new SessionResponse.UserInfo(authentication.getName(), role));
  }

  private String ensureCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
    String existing = readCookie(request, CSRF_COOKIE_NAME);
    if (existing != null && !existing.isBlank()) {
      return existing;
    }

    String csrfToken = UUID.randomUUID().toString();
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        buildCookie(CSRF_COOKIE_NAME, csrfToken, false, jwtService.getExpirationSeconds()).toString());
    return csrfToken;
  }

  private void writeSessionCookies(HttpServletResponse response, String token, String csrfToken) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        buildCookie(AUTH_COOKIE_NAME, token, true, jwtService.getExpirationSeconds()).toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        buildCookie(CSRF_COOKIE_NAME, csrfToken, false, jwtService.getExpirationSeconds()).toString());
  }

  private void clearSessionCookies(HttpServletResponse response) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(AUTH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(CSRF_COOKIE_NAME, "")
            .httpOnly(false)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build()
            .toString());
  }

  private ResponseCookie buildCookie(String name, String value, boolean httpOnly, long maxAgeSeconds) {
    return ResponseCookie.from(name, value)
        .httpOnly(httpOnly)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofSeconds(maxAgeSeconds))
        .build();
  }

  private String readCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
