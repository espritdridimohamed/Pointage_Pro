package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByCompanyIdOrderByLastNameAsc(Long companyId);

    Optional<Employee> findByMatriculeAndCompanyId(String matricule, Long companyId);

    Optional<Employee> findByRfidUid(String rfidUid);

    boolean existsByMatriculeAndCompanyId(String matricule, Long companyId);

    boolean existsByRfidUid(String rfidUid);
}
