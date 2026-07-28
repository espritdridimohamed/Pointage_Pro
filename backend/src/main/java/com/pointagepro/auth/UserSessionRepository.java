package com.pointagepro.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(Long userId);
    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<UserSession> findByTokenHashAndRevokedFalse(String tokenHash);
    UserSession findTopByTokenHashOrderByCreatedAtDesc(String tokenHash);

    @Transactional
    void deleteByUserIdAndDeviceInfoAndRevokedFalse(Long userId, String deviceInfo);

    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
