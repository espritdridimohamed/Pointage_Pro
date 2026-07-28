package com.pointagepro.esp32;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScanEventRepository extends JpaRepository<ScanEvent, Long> {

    List<ScanEvent> findTop20ByScannedAtAfterOrderByScannedAtDesc(LocalDateTime after);

    @Query("SELECT COUNT(s) FROM ScanEvent s WHERE s.scannedAt >= :startOfDay")
    int countTodayScans(@Param("startOfDay") LocalDateTime startOfDay);
}
