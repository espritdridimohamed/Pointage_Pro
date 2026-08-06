package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.EmployeeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    List<EmployeeAssignment> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeAssignment> findFirstByEmployeeIdAndValidToIsNullOrderByValidFromDesc(Long employeeId);

    long countByDepartmentId(Long departmentId);

    long countByPositionId(Long positionId);

    long countByLocationId(Long locationId);
}
