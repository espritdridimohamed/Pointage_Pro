package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.EmployeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeStatusRepository extends JpaRepository<EmployeeStatus, Long> {
    Optional<EmployeeStatus> findByCode(String code);
}
