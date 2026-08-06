package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.EmployeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, Long> {

    List<EmployeeSchedule> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeSchedule> findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeSchedule> findFirstByEmployeeIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
            Long employeeId, LocalDate date);

    long countByScheduleId(Long scheduleId);
}
