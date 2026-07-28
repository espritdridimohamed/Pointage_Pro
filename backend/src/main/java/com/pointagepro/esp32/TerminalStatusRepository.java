package com.pointagepro.esp32;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TerminalStatusRepository extends JpaRepository<TerminalStatus, Long> {
    Optional<TerminalStatus> findByDeviceId(String deviceId);

    @Modifying
    @Transactional
    @Query("UPDATE TerminalStatus t SET t.scansToday = t.scansToday + 1 WHERE t.deviceId = :deviceId")
    void incrementScansToday(String deviceId);

    @Modifying
    @Transactional
    @Query("UPDATE TerminalStatus t SET t.scansToday = 0")
    void resetAllScansToday();
}
