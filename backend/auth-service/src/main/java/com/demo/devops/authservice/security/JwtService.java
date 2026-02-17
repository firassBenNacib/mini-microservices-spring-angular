package com.demo.devops.authservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey secretKey;
  private final long expirationSeconds;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationSeconds = expirationSeconds;
  }

  public String generateToken(String email, String role) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(expirationSeconds);

    return Jwts.builder()
        .setSubject(email)
        .claim("role", role)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expiry))
        .signWith(secretKey, SignatureAlgorithm.HS256)
        .compact();
  }

  public long getExpirationSeconds() {
    return expirationSeconds;
  }
}
