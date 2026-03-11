package com.demo.devops.authservice.repository;

import com.demo.devops.authservice.domain.RefreshTokenSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenSession, Long> {
  Optional<RefreshTokenSession>
      findFirstByUserEmailAndTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
          String userEmail,
          String tokenHash,
          Instant now);

  @Modifying
  @Query(
      """
      update RefreshTokenSession session
         set session.revokedAt = :revokedAt
       where session.userEmail = :userEmail
         and session.revokedAt is null
      """)
  void revokeActiveByUserEmail(
      @Param("userEmail") String userEmail,
      @Param("revokedAt") Instant revokedAt);

  @Modifying
  @Query(
      """
      update RefreshTokenSession session
         set session.revokedAt = :revokedAt
       where session.tokenHash = :tokenHash
         and session.revokedAt is null
      """)
  void revokeByTokenHash(
      @Param("tokenHash") String tokenHash,
      @Param("revokedAt") Instant revokedAt);
}
