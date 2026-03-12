package com.demo.devops.authservice.web;

import com.demo.devops.authservice.client.AuditClient;
import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.dto.LoginRequest;
import com.demo.devops.authservice.dto.SessionResponse;
import com.demo.devops.authservice.dto.StatusResponse;
import com.demo.devops.authservice.repository.UserRepository;
import com.demo.devops.authservice.security.JwtService;
import com.demo.devops.authservice.security.RefreshTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
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
  private static final String REFRESH_COOKIE_NAME = "refresh_token";
  private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final String AUTH_SERVICE_SOURCE = "auth-service";

  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final UserRepository userRepository;
  private final AuditClient auditClient;
  private final PasswordEncoder passwordEncoder;
  private final boolean cookieSecure;

  public AuthController(
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      UserRepository userRepository,
      AuditClient auditClient,
      PasswordEncoder passwordEncoder,
      @Value("${app.cookie.secure:true}") boolean cookieSecure) {
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
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
      HttpServletResponse response,
      CsrfToken csrfToken) {
    if (readCookie(request, CSRF_COOKIE_NAME) == null) {
      addCookie(
          response,
          buildCookie(
              CSRF_COOKIE_NAME,
              resolveCsrfToken(request, csrfToken),
              false,
              jwtService.getRefreshExpirationSeconds()));
    }
    return sessionResponse(authentication);
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public SessionResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response,
      CsrfToken csrfToken) {
    UserAccount user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
    if (user == null) {
      auditClient.sendEvent("LOGIN_FAILURE", request.email(), "user not found", AUTH_SERVICE_SOURCE);
      throw new InvalidCredentialsException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditClient.sendEvent("LOGIN_FAILURE", request.email(), "invalid password", AUTH_SERVICE_SOURCE);
      throw new InvalidCredentialsException();
    }

    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
    String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getRole());
    refreshTokenService.createSession(user.getEmail(), refreshToken, jwtService.refreshExpiresAt());
    writeSessionCookies(response, accessToken, refreshToken, resolveCsrfToken(httpRequest, csrfToken));
    auditClient.sendEvent("LOGIN_SUCCESS", user.getEmail(), "login successful", AUTH_SERVICE_SOURCE);
    return new SessionResponse(
        true,
        jwtService.getAccessExpirationSeconds(),
        new SessionResponse.UserInfo(user.getEmail(), user.getRole()));
  }

  @PostMapping("/refresh")
  public SessionResponse refresh(
      HttpServletRequest request,
      HttpServletResponse response,
      CsrfToken csrfToken) {
    String refreshToken = readCookie(request, REFRESH_COOKIE_NAME);
    if (refreshToken == null || refreshToken.isBlank()) {
      clearSessionCookies(response);
      return new SessionResponse(false, jwtService.getAccessExpirationSeconds(), null);
    }

    try {
      Claims claims = jwtService.parseRefreshToken(refreshToken);
      UserAccount user = userRepository.findByEmailIgnoreCase(claims.getSubject()).orElse(null);
      if (user == null) {
        clearSessionCookies(response);
        return new SessionResponse(false, jwtService.getAccessExpirationSeconds(), null);
      }

      String nextRefreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getRole());
      boolean rotated = refreshTokenService.rotateSession(
          user.getEmail(),
          refreshToken,
          nextRefreshToken,
          jwtService.refreshExpiresAt());
      if (!rotated) {
        clearSessionCookies(response);
        return new SessionResponse(false, jwtService.getAccessExpirationSeconds(), null);
      }

      writeSessionCookies(
          response,
          jwtService.generateAccessToken(user.getEmail(), user.getRole()),
          nextRefreshToken,
          resolveCsrfToken(request, csrfToken));
      return new SessionResponse(
          true,
          jwtService.getAccessExpirationSeconds(),
          new SessionResponse.UserInfo(user.getEmail(), user.getRole()));
    } catch (RuntimeException ex) {
      clearSessionCookies(response);
      return new SessionResponse(false, jwtService.getAccessExpirationSeconds(), null);
    }
  }

  @PostMapping("/logout")
  public StatusResponse logout(HttpServletRequest request, HttpServletResponse response) {
    refreshTokenService.revokeSession(readCookie(request, REFRESH_COOKIE_NAME));
    clearSessionCookies(response);
    return new StatusResponse("ok");
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class InvalidCredentialsException extends RuntimeException {}

  private SessionResponse sessionResponse(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
      return new SessionResponse(false, jwtService.getAccessExpirationSeconds(), null);
    }

    String role = authentication.getAuthorities().stream()
        .findFirst()
        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "").toLowerCase())
        .orElse("user");
    return new SessionResponse(
        true,
        jwtService.getAccessExpirationSeconds(),
        new SessionResponse.UserInfo(authentication.getName(), role));
  }

  private String resolveCsrfToken(HttpServletRequest request, CsrfToken csrfToken) {
    if (csrfToken != null && csrfToken.getToken() != null && !csrfToken.getToken().isBlank()) {
      return csrfToken.getToken();
    }
    String existing = readCookie(request, CSRF_COOKIE_NAME);
    if (existing != null && !existing.isBlank()) {
      return existing;
    }
    return UUID.randomUUID().toString();
  }

  private void writeSessionCookies(
      HttpServletResponse response,
      String accessToken,
      String refreshToken,
      String csrfToken) {
    addCookie(response, buildCookie(AUTH_COOKIE_NAME, accessToken, true, jwtService.getAccessExpirationSeconds()));
    addCookie(response, buildCookie(REFRESH_COOKIE_NAME, refreshToken, true, jwtService.getRefreshExpirationSeconds()));
    addCookie(response, buildCookie(CSRF_COOKIE_NAME, csrfToken, false, jwtService.getRefreshExpirationSeconds()));
  }

  private void clearSessionCookies(HttpServletResponse response) {
    addCookie(response, clearCookie(AUTH_COOKIE_NAME, true));
    addCookie(response, clearCookie(REFRESH_COOKIE_NAME, true));
    addCookie(response, clearCookie(CSRF_COOKIE_NAME, false));
  }

  private ResponseCookie buildCookie(String name, String value, boolean httpOnly, long maxAgeSeconds) {
    return ResponseCookie.from(
            Objects.requireNonNull(name, "cookie name must not be null"),
            Objects.requireNonNull(value, "cookie value must not be null"))
        .httpOnly(httpOnly)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAgeSeconds)
        .build();
  }

  private ResponseCookie clearCookie(String name, boolean httpOnly) {
    return ResponseCookie.from(name, "")
        .httpOnly(httpOnly)
        .secure(cookieSecure)
        .sameSite("Lax")
        .path("/")
        .maxAge(0)
        .build();
  }

  private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
