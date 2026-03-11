package com.demo.devops.apiservice.security;

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
  private static final String CURRENT_SIGNING_MATERIAL = "01234567890123456789012345678901";
  private static final String PREVIOUS_SIGNING_MATERIAL = "abcdefghijklmnopqrstuvwxyz123456";

  private final JwtService jwtService = new JwtService(CURRENT_SIGNING_MATERIAL, PREVIOUS_SIGNING_MATERIAL);

  @Test
  void parseAccessTokenFallsBackToThePreviousSecret() {
    Claims claims = jwtService.parseAccessToken(signAccessToken(PREVIOUS_SIGNING_MATERIAL));

    assertEquals("user@example.com", claims.getSubject());
  }

  @Test
  void parseAccessTokenRejectsInvalidTokens() {
    assertThrows(JwtException.class, () -> jwtService.parseAccessToken("not-a-jwt"));
  }

  private String signAccessToken(String secret) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("user@example.com")
        .claim("tokenType", "access")
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(3600)))
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
