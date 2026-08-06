package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.AttendanceEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    @EntityGraph(attributePaths = {"employee", "eventType"})
    List<AttendanceEvent> findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(Long employeeId,
                                                                                LocalDateTime from,
                                                                                LocalDateTime to);

    Optional<AttendanceEvent> findFirstByTerminalIdAndExternalRef(Long terminalId, String externalRef);

    boolean existsByTerminalIdAndExternalRef(Long terminalId, String externalRef);

    Optional<AttendanceEvent> findFirstByEmployeeIdAndTerminalIdAndEventTypeCodeAndEventTimeBetween(
            Long employeeId, Long terminalId, String eventTypeCode,
            LocalDateTime from, LocalDateTime to);

    Optional<AttendanceEvent> findFirstByEmployeeIdAndTerminalIdAndEventTimeBetween(
            Long employeeId, Long terminalId, LocalDateTime from, LocalDateTime to);

    Optional<AttendanceEvent> findFirstByEmployeeIdAndEventTimeLessThanOrderByEventTimeDesc(
            Long employeeId, LocalDateTime eventTime);
}
