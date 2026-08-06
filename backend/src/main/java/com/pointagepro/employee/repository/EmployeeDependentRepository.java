package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.EmployeeDependent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDependentRepository extends JpaRepository<EmployeeDependent, Long> {
    List<EmployeeDependent> findByEmployeeId(Long employeeId);
}
