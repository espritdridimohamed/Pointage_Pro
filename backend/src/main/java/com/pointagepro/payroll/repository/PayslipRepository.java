package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @EntityGraph(attributePaths = {"payrollItem.employee", "payrollItem.payroll"})
    @Query("select p from Payslip p where p.payrollItem.payroll.id = :payrollId order by p.id asc")
    List<Payslip> findWithDetailsByPayrollId(@Param("payrollId") Long payrollId);

    @EntityGraph(attributePaths = {"payrollItem.employee", "payrollItem.payroll"})
    @Query("select p from Payslip p where p.id = :id")
    Optional<Payslip> findWithDetailsById(@Param("id") Long id);

    List<Payslip> findByPayrollItemPayrollId(Long payrollId);

    void deleteByPayrollItemPayrollId(Long payrollId);
}
