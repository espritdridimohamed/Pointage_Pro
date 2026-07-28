package com.pointagepro.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {

    List<PayrollItem> findByPayrollIdOrderByEmployeeIdAsc(Long payrollId);

    List<PayrollItem> findByPayrollIdAndEmployeeId(Long payrollId, Long employeeId);
}
