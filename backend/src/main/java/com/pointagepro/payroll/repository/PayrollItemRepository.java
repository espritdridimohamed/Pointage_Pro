package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.PayrollItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {

    @EntityGraph(attributePaths = {"employee", "contract"})
    @Query("select i from PayrollItem i where i.payroll.id = :payrollId order by i.id asc")
    List<PayrollItem> findWithDetailsByPayrollId(@Param("payrollId") Long payrollId);

    @EntityGraph(attributePaths = {"employee", "contract", "payroll"})
    @Query("select i from PayrollItem i where i.id = :id")
    Optional<PayrollItem> findWithDetailsById(@Param("id") Long id);

    long countByPayrollId(Long payrollId);

    long countByContractId(Long contractId);

    void deleteByPayrollId(Long payrollId);
}
