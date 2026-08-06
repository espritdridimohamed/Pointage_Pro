package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.EmployeeContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeContractRepository extends JpaRepository<EmployeeContract, Long> {

    List<EmployeeContract> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    Optional<EmployeeContract> findFirstByEmployeeIdAndStatusCodeOrderByStartDateDesc(Long employeeId, String statusCode);

    long countByLocationId(Long locationId);
}
