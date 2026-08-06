package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.EmployeeTaxProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeTaxProfileRepository extends JpaRepository<EmployeeTaxProfile, Long> {

    List<EmployeeTaxProfile> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeTaxProfile> findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeTaxProfile> findFirstByEmployeeIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
            Long employeeId, LocalDate date);
}
