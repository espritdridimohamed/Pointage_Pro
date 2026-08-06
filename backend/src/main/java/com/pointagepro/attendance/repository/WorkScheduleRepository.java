package com.pointagepro.attendance.repository;

import com.pointagepro.attendance.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    List<WorkSchedule> findByCompanyIdOrderByCodeAsc(Long companyId);

    Optional<WorkSchedule> findByCompanyIdAndCode(Long companyId, String code);

    Optional<WorkSchedule> findFirstByCompanyIdAndIsDefaultTrue(Long companyId);
}
