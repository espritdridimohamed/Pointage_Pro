package com.pointagepro.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findTop20ByUserIdOrderByAttemptedAtDesc(Long userId);

    @Transactional
    void deleteByAttemptedAtBefore(LocalDateTime cutoff);
}
