package com.demo.devops.authservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.devops.authservice.domain.RefreshTokenSession;
import com.demo.devops.authservice.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RefreshTokenServiceTest {
  private RefreshTokenRepository refreshTokenRepository;
  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
    refreshTokenService = new RefreshTokenService(refreshTokenRepository);
  }

  @Test
  void createSessionRevokesExistingTokensAndStoresTheHashedRefreshToken() {
    Instant expiresAt = Instant.parse("2026-03-18T00:00:00Z");
    ArgumentCaptor<RefreshTokenSession> sessionCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);

    refreshTokenService.createSession("user@example.com", "refresh-token", expiresAt);

    verify(refreshTokenRepository).revokeActiveByUserEmail(eq("user@example.com"), any(Instant.class));
    verify(refreshTokenRepository).save(sessionCaptor.capture());

    RefreshTokenSession session = sessionCaptor.getValue();
    assertEquals("user@example.com", session.getUserEmail());
    assertEquals(expiresAt, session.getExpiresAt());
    assertEquals(hash("refresh-token"), session.getTokenHash());
    assertEquals(null, session.getRevokedAt());
  }

  @Test
  void rotateSessionReturnsFalseWhenTheCurrentRefreshTokenIsMissing() {
    when(refreshTokenRepository.findFirstByUserEmailAndTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            eq("user@example.com"),
            eq(hash("current-token")),
            any(Instant.class)))
        .thenReturn(Optional.empty());

    boolean rotated = refreshTokenService.rotateSession(
        "user@example.com",
        "current-token",
        "next-token",
        Instant.parse("2026-03-18T00:00:00Z"));

    assertFalse(rotated);
    verify(refreshTokenRepository, never()).save(any(RefreshTokenSession.class));
  }

  @Test
  void rotateSessionRevokesTheCurrentRecordAndStoresTheReplacementToken() {
    RefreshTokenSession current = new RefreshTokenSession();
    current.setUserEmail("user@example.com");
    current.setTokenHash(hash("current-token"));
    current.setExpiresAt(Instant.parse("2026-03-17T00:00:00Z"));
    when(refreshTokenRepository.findFirstByUserEmailAndTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            eq("user@example.com"),
            eq(hash("current-token")),
            any(Instant.class)))
        .thenReturn(Optional.of(current));

    ArgumentCaptor<RefreshTokenSession> sessionCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);

    boolean rotated = refreshTokenService.rotateSession(
        "user@example.com",
        "current-token",
        "next-token",
        Instant.parse("2026-03-18T00:00:00Z"));

    assertTrue(rotated);
    verify(refreshTokenRepository, Mockito.times(2)).save(sessionCaptor.capture());
    assertNotNull(sessionCaptor.getAllValues().get(0).getRevokedAt());
    assertEquals("user@example.com", sessionCaptor.getAllValues().get(1).getUserEmail());
    assertEquals(hash("next-token"), sessionCaptor.getAllValues().get(1).getTokenHash());
  }

  @Test
  void revokeSessionSkipsBlankTokens() {
    refreshTokenService.revokeSession("   ");

    verify(refreshTokenRepository, never()).revokeByTokenHash(any(), any());
  }

  @Test
  void revokeSessionHashesTheRefreshTokenBeforeRevokingIt() {
    refreshTokenService.revokeSession("refresh-token");

    verify(refreshTokenRepository).revokeByTokenHash(eq(hash("refresh-token")), any(Instant.class));
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
