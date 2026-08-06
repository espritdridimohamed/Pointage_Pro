package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.PayrollItemComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollItemComponentRepository extends JpaRepository<PayrollItemComponent, Long> {

    List<PayrollItemComponent> findByPayrollItemIdInOrderByPayrollItemIdAscSortOrderAsc(List<Long> itemIds);

    List<PayrollItemComponent> findByPayrollItemIdOrderBySortOrderAsc(Long payrollItemId);

    void deleteByPayrollItemIdIn(List<Long> itemIds);
}
