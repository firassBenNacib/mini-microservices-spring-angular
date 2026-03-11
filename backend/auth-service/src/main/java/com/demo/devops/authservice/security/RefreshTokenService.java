package com.demo.devops.authservice.security;

import com.demo.devops.authservice.domain.RefreshTokenSession;
import com.demo.devops.authservice.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
  private final RefreshTokenRepository refreshTokenRepository;

  public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional
  public void createSession(String userEmail, String refreshToken, Instant expiresAt) {
    refreshTokenRepository.revokeActiveByUserEmail(userEmail, Instant.now());
    refreshTokenRepository.save(buildRecord(userEmail, refreshToken, expiresAt));
  }

  @Transactional
  public boolean rotateSession(
      String userEmail,
      String currentRefreshToken,
      String nextRefreshToken,
      Instant expiresAt) {
    Optional<RefreshTokenSession> existing = refreshTokenRepository
        .findFirstByUserEmailAndTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            userEmail,
            hashToken(currentRefreshToken),
            Instant.now());

    if (existing.isEmpty()) {
      return false;
    }

    RefreshTokenSession current = existing.get();
    current.setRevokedAt(Instant.now());
    refreshTokenRepository.save(current);
    refreshTokenRepository.save(buildRecord(userEmail, nextRefreshToken, expiresAt));
    return true;
  }

  @Transactional
  public void revokeSession(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }
    refreshTokenRepository.revokeByTokenHash(hashToken(refreshToken), Instant.now());
  }

  private RefreshTokenSession buildRecord(String userEmail, String refreshToken, Instant expiresAt) {
    RefreshTokenSession session = new RefreshTokenSession();
    session.setUserEmail(userEmail);
    session.setTokenHash(hashToken(refreshToken));
    session.setExpiresAt(expiresAt);
    return session;
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
