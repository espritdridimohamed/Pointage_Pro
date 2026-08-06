package com.pointagepro.payroll.repository;

import com.pointagepro.payroll.entity.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollStatusRepository extends JpaRepository<PayrollStatus, Long> {

    Optional<PayrollStatus> findByCode(String code);
}
