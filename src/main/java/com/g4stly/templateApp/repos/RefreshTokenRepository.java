package com.g4stly.templateApp.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.g4stly.templateApp.models.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserIdAndUserType(Long userId, String userType);

    List<RefreshToken> findByUserIdAndUserTypeAndIsRevokedFalse(Long userId, String userType);

    @Modifying
    @Transactional
    void deleteByToken(String token);

    @Modifying
    @Transactional
    void deleteByExpiryDateBefore(LocalDateTime date);

    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId AND rt.userType = :userType AND rt.isRevoked = false")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("userType") String userType);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.isRevoked = true OR rt.expiryDate < :date")
    int cleanupRevokedAndExpired(@Param("date") LocalDateTime date);

    long countByUserIdAndUserType(Long userId, String userType);

    long countByIsRevokedFalseAndExpiryDateAfter(LocalDateTime now);

    // Admin management queries
    List<RefreshToken> findByUserType(String userType);

    List<RefreshToken> findByIsRevoked(Boolean isRevoked);

    List<RefreshToken> findByIpAddress(String ipAddress);

    @Query("SELECT rt FROM RefreshToken rt WHERE " +
           "(:userType IS NULL OR rt.userType = :userType) AND " +
           "(:userId IS NULL OR rt.userId = :userId) AND " +
           "(:isRevoked IS NULL OR rt.isRevoked = :isRevoked) AND " +
           "(:ipAddress IS NULL OR rt.ipAddress = :ipAddress) " +
           "ORDER BY rt.createdAt DESC")
    List<RefreshToken> findWithFilters(
            @Param("userType") String userType,
            @Param("userId") Long userId,
            @Param("isRevoked") Boolean isRevoked,
            @Param("ipAddress") String ipAddress);

    @Query("SELECT rt.userType, COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = false AND rt.expiryDate > :now GROUP BY rt.userType")
    List<Object[]> countActiveTokensByUserType(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = false AND rt.expiryDate > :now")
    long countAllActiveTokens(@Param("now") LocalDateTime now);
}
