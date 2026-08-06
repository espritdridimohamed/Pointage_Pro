package com.pointagepro.contract.repository;

import com.pointagepro.contract.entity.SalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {
    List<SalaryHistory> findByEmployeeIdOrderByChangeDateDesc(Long employeeId);
}
