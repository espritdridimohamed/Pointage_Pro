package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.WorkScheduleLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleLineRepository extends JpaRepository<WorkScheduleLine, Long> {

    List<WorkScheduleLine> findByScheduleId(Long scheduleId);

    Optional<WorkScheduleLine> findByScheduleIdAndWeekday(Long scheduleId, Integer weekday);
}
