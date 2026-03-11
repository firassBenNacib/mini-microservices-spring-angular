package com.demo.devops.auditservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey currentSecretKey;
  private final SecretKey previousSecretKey;

  public JwtService(
      @Value("${app.jwt.current-secret:${app.jwt.secret:}}") String currentSecret,
      @Value("${app.jwt.previous-secret:}") String previousSecret) {
    this.currentSecretKey = Keys.hmacShaKeyFor(currentSecret.getBytes(StandardCharsets.UTF_8));
    this.previousSecretKey = previousSecret == null || previousSecret.isBlank()
        ? null
        : Keys.hmacShaKeyFor(previousSecret.getBytes(StandardCharsets.UTF_8));
  }

  public Claims parseAccessToken(String token) {
    Optional<Claims> claims = tryParse(token, currentSecretKey);
    if (claims.isEmpty() && previousSecretKey != null) {
      claims = tryParse(token, previousSecretKey);
    }
    Claims parsedClaims = claims.orElseThrow(() -> new JwtException("invalid token"));
    if (!"access".equals(parsedClaims.get("tokenType", String.class))) {
      throw new JwtException("unexpected token type");
    }
    return parsedClaims;
  }

  private Optional<Claims> tryParse(String token, SecretKey key) {
    try {
      return Optional.of(
          Jwts.parserBuilder()
              .setSigningKey(key)
              .build()
              .parseClaimsJws(token)
              .getBody());
    } catch (JwtException ex) {
      return Optional.empty();
    }
  }
}
