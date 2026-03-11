package com.demo.devops.authservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String CURRENT_SECRET = "01234567890123456789012345678901";
  private static final String PREVIOUS_SECRET = "abcdefghijklmnopqrstuvwxyz123456";

  private final JwtService jwtService =
      new JwtService("active-key", CURRENT_SECRET, PREVIOUS_SECRET, 3600, 7200);

  @Test
  void parseAccessTokenFallsBackToThePreviousSecret() {
    Claims claims = jwtService.parseAccessToken(signToken(PREVIOUS_SECRET, "access"));

    assertEquals("user@example.com", claims.getSubject());
    assertEquals("admin", claims.get("role", String.class));
  }

  @Test
  void parseAccessTokenRejectsRefreshTokens() {
    String refreshToken = jwtService.generateRefreshToken("user@example.com", "admin");

    assertThrows(JwtException.class, () -> jwtService.parseAccessToken(refreshToken));
  }

  private String signToken(String secret, String tokenType) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setHeaderParam("kid", "old-key")
        .setSubject("user@example.com")
        .claim("role", "admin")
        .claim("tokenType", tokenType)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(3600)))
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
