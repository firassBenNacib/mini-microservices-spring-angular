package com.demo.devops.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final String currentKid;
  private final SecretKey currentSecretKey;
  private final SecretKey previousSecretKey;
  private final long accessExpirationSeconds;
  private final long refreshExpirationSeconds;

  @Autowired
  public JwtService(
      @Value("${app.jwt.current-kid:active-key}") String currentKid,
      @Value("${app.jwt.current-secret:${app.jwt.secret:}}") String currentSecret,
      @Value("${app.jwt.previous-secret:}") String previousSecret,
      @Value("${app.jwt.access-expiration-seconds:${app.jwt.expiration-seconds:3600}}")
      long accessExpirationSeconds,
      @Value("${app.jwt.refresh-expiration-seconds:604800}") long refreshExpirationSeconds) {
    this.currentKid = currentKid;
    this.currentSecretKey = toKey(currentSecret);
    this.previousSecretKey = previousSecret == null || previousSecret.isBlank() ? null : toKey(previousSecret);
    this.accessExpirationSeconds = accessExpirationSeconds;
    this.refreshExpirationSeconds = refreshExpirationSeconds;
  }

  public JwtService(String currentSecret, long accessExpirationSeconds) {
    this("active-key", currentSecret, "", accessExpirationSeconds, 604800);
  }

  public String generateAccessToken(String email, String role) {
    return generateToken(email, role, "access", accessExpirationSeconds).compact();
  }

  public String generateRefreshToken(String email, String role) {
    return generateToken(email, role, "refresh", refreshExpirationSeconds)
        .setId(UUID.randomUUID().toString())
        .compact();
  }

  public long getAccessExpirationSeconds() {
    return accessExpirationSeconds;
  }

  public long getRefreshExpirationSeconds() {
    return refreshExpirationSeconds;
  }

  public Instant refreshExpiresAt() {
    return Instant.now().plusSeconds(refreshExpirationSeconds);
  }

  public Claims parseAccessToken(String token) {
    return parseToken(token, "access");
  }

  public Claims parseRefreshToken(String token) {
    return parseToken(token, "refresh");
  }

  private io.jsonwebtoken.JwtBuilder generateToken(
      String email,
      String role,
      String tokenType,
      long expirationSeconds) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(expirationSeconds);

    return Jwts.builder()
        .setHeaderParam("kid", currentKid)
        .setSubject(email)
        .claim("role", role)
        .claim("tokenType", tokenType)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiry))
        .signWith(currentSecretKey, SignatureAlgorithm.HS256);
  }

  private Claims parseToken(String token, String expectedType) {
    Optional<Claims> claims = tryParse(token, currentSecretKey);
    if (claims.isEmpty() && previousSecretKey != null) {
      claims = tryParse(token, previousSecretKey);
    }
    Claims parsedClaims = claims.orElseThrow(() -> new JwtException("invalid token"));

    String tokenType = parsedClaims.get("tokenType", String.class);
    if (!expectedType.equals(tokenType)) {
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

  private SecretKey toKey(String secret) {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
