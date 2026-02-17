package com.demo.devops.authservice.web;

import com.demo.devops.authservice.client.AuditClient;
import com.demo.devops.authservice.domain.UserAccount;
import com.demo.devops.authservice.dto.LoginRequest;
import com.demo.devops.authservice.dto.LoginResponse;
import com.demo.devops.authservice.dto.StatusResponse;
import com.demo.devops.authservice.repository.UserRepository;
import com.demo.devops.authservice.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final AuditClient auditClient;
  private final PasswordEncoder passwordEncoder;

  public AuthController(
      JwtService jwtService,
      UserRepository userRepository,
      AuditClient auditClient,
      PasswordEncoder passwordEncoder) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
    this.auditClient = auditClient;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
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
    auditClient.sendEvent("LOGIN_SUCCESS", user.getEmail(), "login successful", "auth-service");
    return new LoginResponse(token, jwtService.getExpirationSeconds(),
        new LoginResponse.UserInfo(user.getEmail(), user.getRole()));
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  private static class InvalidCredentialsException extends RuntimeException {}
}
