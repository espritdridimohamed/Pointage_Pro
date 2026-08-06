package com.pointagepro.employee.repository;

import com.pointagepro.employee.entity.EmployeeBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, Long> {
    List<EmployeeBankAccount> findByEmployeeIdOrderByValidFromDesc(Long employeeId);

    Optional<EmployeeBankAccount> findByEmployeeIdAndIsDefaultTrue(Long employeeId);
}
